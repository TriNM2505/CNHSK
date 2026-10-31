package com.cnhsk.learning.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.cnhsk.learning.practice.PracticeService.StartPracticeCommand;
import com.cnhsk.learning.practice.PracticeSourceHandler.Answer;
import com.cnhsk.learning.practice.entity.Attempt;
import com.cnhsk.learning.practice.entity.AttemptItem;
import com.cnhsk.learning.practice.entity.AttemptItemRepository;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.learning.practice.web.PracticeController;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.parser.OpenAPIV3Parser;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@Import(PracticeFinishIntegrationTest.EventConfiguration.class)
class PracticeAbandonIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private PracticeService practice;
    @Autowired private AttemptRepository attempts;
    @Autowired private AttemptItemRepository items;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PracticeFinishIntegrationTest.EventProbe probe;
    @MockitoBean private Clock clock;
    @MockitoBean(name = "topicTestHandler") private PracticeSourceHandler handler;
    private long owner;

    @BeforeEach
    void setup() throws Exception {
        when(clock.instant()).thenReturn(PracticeServiceTest.NOW);
        when(handler.source()).thenReturn(PracticeSource.TOPIC_STUDY);
        when(handler.enrichMeta(any(), any())).thenReturn(Map.of());
        probe.clear();
        owner = createUser();
    }

    @Test
    void FR008_AT16b_abandonKeepsThreeRecordedCardsAndDoesNotScoreWriteStudyOrPublishEvents() throws Exception {
        long id = start(PracticeSource.TOPIC_STUDY);
        for (int no = 1; no <= 3; no++) {
            practice.answer(owner, id, new Answer(no, "LEARNED", 1000, 0, false, false, null));
        }
        List<Long> evidenceIds = items.findByAttemptIdOrderByOrderIndexAsc(id).stream().map(AttemptItem::getId).toList();
        JsonNode snapshot = attempts.findById(id).orElseThrow().getServedItems().deepCopy();
        probe.clear();
        MockHttpServletResponse response = abandon(id, owner);
        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(response.getContentAsString()).isEmpty();
        Attempt saved = attempts.findById(id).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("ABANDONED");
        assertThat(saved.getScore()).isNull();
        assertThat(saved.getMaxScore()).isNull();
        assertThat(saved.getSubmittedAt()).isNull();
        assertThat(saved.getDurationMs()).isNull();
        assertThat(saved.getServedItems()).isEqualTo(snapshot);
        assertThat(items.findByAttemptIdOrderByOrderIndexAsc(id)).extracting(AttemptItem::getId)
                .containsExactlyElementsOf(evidenceIds);
        assertNoCompletionEffects();
        assertError(abandon(id, owner), 409, "SESSION_ALREADY_SUBMITTED");
        assertThat(items.findByAttemptIdOrderByOrderIndexAsc(id)).hasSize(3);
    }

    @Test
    void FR008_abandonPreservesDeferredDraftAndItsTimestampWithoutGradingIt() throws Exception {
        long id = start(PracticeSource.TOPIC_TEST);
        practice.answer(owner, id, new Answer(1, "B", 1200, 0, false, false, null));
        Attempt before = attempts.findById(id).orElseThrow();
        JsonNode draft = before.getDraftAnswers().deepCopy();
        assertThat(abandon(id, owner).getStatus()).isEqualTo(204);
        Attempt saved = attempts.findById(id).orElseThrow();
        assertThat(saved.getDraftAnswers()).isEqualTo(draft);
        assertThat(saved.getDraftSavedAt()).isEqualTo(before.getDraftSavedAt());
        assertThat(saved.getServedItems()).isEqualTo(before.getServedItems());
        assertThat(items.findByAttemptIdOrderByOrderIndexAsc(id)).isEmpty();
        assertNoCompletionEffects();
    }

    @Test
    void FR001_missingAndForeignExpiredSessionsReturnIdentical403WithoutMutatingEitherOwner() throws Exception {
        long id = start(PracticeSource.TOPIC_STUDY);
        Attempt attempt = attempts.findById(id).orElseThrow();
        attempt.setExpiresAt(PracticeServiceTest.NOW.minusSeconds(1));
        attempts.saveAndFlush(attempt);
        long other = createUser();
        MockHttpServletResponse missing = abandon(Long.MAX_VALUE, other);
        MockHttpServletResponse foreign = abandon(id, other);
        assertError(missing, 403, "ATTEMPT_NOT_OWNED");
        assertError(foreign, 403, "ATTEMPT_NOT_OWNED");
        assertThat(mapper.readTree(missing.getContentAsString())).isEqualTo(mapper.readTree(foreign.getContentAsString()));
        assertThat(attempts.findById(id).orElseThrow().getStatus()).isEqualTo("IN_PROGRESS");
        assertNoCompletionEffects();
    }

    @ParameterizedTest
    @EnumSource(value = PracticeSource.class, names = {"TOPIC_STUDY", "TOPIC_PRACTICE", "TOPIC_TEST"})
    void FR008_expiredAbandonCommitsExpiredBefore422AndKeepsRecordedOrDraftEvidence(PracticeSource source) throws Exception {
        long id = start(source);
        practice.answer(owner, id, new Answer(1, source == PracticeSource.TOPIC_STUDY ? "KNOWN" : "A",
                1000, 0, false, false, null));
        Attempt before = attempts.findById(id).orElseThrow();
        JsonNode snapshot = before.getServedItems().deepCopy();
        JsonNode draft = before.getDraftAnswers();
        List<Long> evidenceIds = items.findByAttemptIdOrderByOrderIndexAsc(id).stream().map(AttemptItem::getId).toList();
        before.setExpiresAt(PracticeServiceTest.NOW.minusSeconds(1));
        attempts.saveAndFlush(before);
        probe.clear();
        assertError(abandon(id, owner), 422, "SESSION_EXPIRED");
        assertError(abandon(id, owner), 422, "SESSION_EXPIRED");
        Attempt saved = attempts.findById(id).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("EXPIRED");
        assertThat(saved.getServedItems()).isEqualTo(snapshot);
        assertThat(saved.getDraftAnswers()).isEqualTo(draft);
        assertThat(items.findByAttemptIdOrderByOrderIndexAsc(id)).extracting(AttemptItem::getId)
                .containsExactlyElementsOf(evidenceIds);
        assertNoCompletionEffects();
    }

    @Test
    void FR008_exactExpiryBoundaryAllowsAbandon() throws Exception {
        long id = start(PracticeSource.TOPIC_STUDY);
        Attempt attempt = attempts.findById(id).orElseThrow();
        attempt.setExpiresAt(PracticeServiceTest.NOW);
        attempts.saveAndFlush(attempt);
        assertThat(abandon(id, owner).getStatus()).isEqualTo(204);
        assertThat(attempts.findById(id).orElseThrow().getStatus()).isEqualTo("ABANDONED");
        assertNoCompletionEffects();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBMITTED", "ABANDONED"})
    void FR008_terminalSessionsRejectAbandonWithoutChangingExistingResults(String terminal) throws Exception {
        long id = start(PracticeSource.TOPIC_TEST);
        Attempt attempt = attempts.findById(id).orElseThrow();
        attempt.setStatus(terminal);
        attempt.setExpiresAt(PracticeServiceTest.NOW.minusSeconds(1));
        if ("SUBMITTED".equals(terminal)) {
            attempt.setSubmittedAt(PracticeServiceTest.NOW);
            attempt.setScore(2L);
            attempt.setMaxScore(3L);
        }
        attempts.saveAndFlush(attempt);
        assertError(abandon(id, owner), 409, "SESSION_ALREADY_SUBMITTED");
        Attempt saved = attempts.findById(id).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(terminal);
        assertThat(saved.getScore()).isEqualTo(attempt.getScore());
        assertThat(saved.getMaxScore()).isEqualTo(attempt.getMaxScore());
        assertThat(saved.getSubmittedAt()).isEqualTo(attempt.getSubmittedAt());
        assertNoCompletionEffects();
    }

    @Test
    void FR008_abandonAndFinishRaceProducesOneTransitionAndConsistentCompletionEvidence() throws Exception {
        long id = start(PracticeSource.TOPIC_TEST);
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var abandonResult = executor.submit(() -> {
                ready.countDown();
                assertThat(go.await(10, TimeUnit.SECONDS)).isTrue();
                return abandon(id, owner).getStatus();
            });
            var finishResult = executor.submit(() -> {
                ready.countDown();
                assertThat(go.await(10, TimeUnit.SECONDS)).isTrue();
                return mvc.perform(post("/api/practice/sessions/{id}/finish", id)
                        .header("Authorization", bearer(owner, "LEARNER"))).andReturn().getResponse().getStatus();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            int abandoned = abandonResult.get(30, TimeUnit.SECONDS);
            int finished = finishResult.get(30, TimeUnit.SECONDS);
            if (abandoned == 204) {
                assertThat(finished).isEqualTo(409);
                assertThat(attempts.findById(id).orElseThrow().getStatus()).isEqualTo("ABANDONED");
                assertThat(items.findByAttemptIdOrderByOrderIndexAsc(id)).isEmpty();
                assertNoCompletionEffects();
            } else {
                assertThat(abandoned).isEqualTo(409);
                assertThat(finished).isEqualTo(200);
                assertThat(attempts.findById(id).orElseThrow().getStatus()).isEqualTo("SUBMITTED");
                assertThat(jdbc.queryForObject("select count(*) from learning.study_events where user_id=?", Long.class, owner))
                        .isEqualTo(1);
                assertThat(probe.finished).hasSize(1);
            }
        } finally {
            go.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void FR008_twoConcurrentAbandonsReturn204And409WithoutAnyCompletionEffects() throws Exception {
        long id = start(PracticeSource.TOPIC_STUDY);
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                ready.countDown();
                assertThat(go.await(10, TimeUnit.SECONDS)).isTrue();
                return abandon(id, owner).getStatus();
            });
            var second = executor.submit(() -> {
                ready.countDown();
                assertThat(go.await(10, TimeUnit.SECONDS)).isTrue();
                return abandon(id, owner).getStatus();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(204, 409);
        } finally {
            go.countDown();
            executor.shutdownNow();
        }
        assertNoCompletionEffects();
    }

    @Test
    void FR001_abandonRequiresLearnerAndCookieCsrfButBearerDoesNotNeedCsrf() throws Exception {
        long id = start(PracticeSource.TOPIC_STUDY);
        assertError(mvc.perform(post("/api/practice/sessions/{id}/abandon", id)).andReturn().getResponse(),
                401, "UNAUTHENTICATED");
        for (String role : List.of("ADMIN", "TEACHER", "MODERATOR", "ACCOUNTANT", "CONTENT_EDITOR")) {
            assertError(mvc.perform(post("/api/practice/sessions/{id}/abandon", id)
                    .header("Authorization", bearer(owner, role))).andReturn().getResponse(), 403, "FORBIDDEN");
        }
        Cookie access = new Cookie("access_token", token(owner, "LEARNER"));
        assertError(mvc.perform(post("/api/practice/sessions/{id}/abandon", id).cookie(access)).andReturn().getResponse(),
                403, "CSRF_TOKEN_INVALID");
        var csrf = mvc.perform(get("/api/auth/csrf").cookie(access)).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        assertError(mvc.perform(post("/api/practice/sessions/{id}/abandon", id).cookie(access, csrf)
                        .header("X-XSRF-TOKEN", "invalid")).andReturn().getResponse(), 403, "CSRF_TOKEN_INVALID");
        mvc.perform(post("/api/practice/sessions/{id}/abandon", id).cookie(access, csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())).andExpect(status().isNoContent());
        assertThat(abandon(start(PracticeSource.TOPIC_STUDY), owner).getStatus()).isEqualTo(204);
    }

    @Test
    void FR001_invalidPathUsesValidationContractAndLeavesSessionUntouched() throws Exception {
        long id = start(PracticeSource.TOPIC_STUDY);
        for (String invalid : List.of("not-a-number", "999999999999999999999999")) {
            assertError(mvc.perform(post("/api/practice/sessions/{id}/abandon", invalid)
                    .header("Authorization", bearer(owner, "LEARNER"))).andReturn().getResponse(), 422, "VALIDATION_FAILED");
        }
        assertThat(attempts.findById(id).orElseThrow().getStatus()).isEqualTo("IN_PROGRESS");
        assertNoCompletionEffects();
    }

    @Test
    void FR001_openApiAbandonStatusesMatchControllerWithoutAddingErrorCodes() throws Exception {
        var parsed = new OpenAPIV3Parser().readContents(Files.readString(
                Path.of("specs/003-vocab-topics/contracts/openapi-practice-vocab.yaml")));
        assertThat(parsed.getMessages()).isEmpty();
        var operation = parsed.getOpenAPI().getPaths().get("/api/practice/sessions/{id}/abandon").getPost();
        assertThat(operation.getResponses().keySet()).containsExactlyInAnyOrder("204", "401", "403", "409", "422");
        assertThat(operation.getDescription()).contains("ATTEMPT_NOT_OWNED", "SESSION_EXPIRED", "SESSION_ALREADY_SUBMITTED");
        var annotations = PracticeController.class.getMethod("abandon", String.class).getAnnotation(ApiResponses.class);
        assertThat(Arrays.stream(annotations.value()).map(ApiResponse::responseCode).toList())
                .containsExactlyInAnyOrderElementsOf(operation.getResponses().keySet());
    }

    private long createUser() throws Exception {
        String password = "Aa1" + UUID.randomUUID();
        return mapper.readTree(mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content(mapper.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.test",
                                "password", password, "confirmPassword", password,
                                "displayName", "Abandon test user", "acceptTerms", true))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("userId").asLong();
    }

    private long start(PracticeSource source) {
        List<ServedItem> served = java.util.stream.IntStream.rangeClosed(1, 3).mapToObj(no ->
                source == PracticeSource.TOPIC_STUDY ? PracticeServiceTest.card(no) : PracticeServiceTest.choice(no)).toList();
        return practice.start(new StartPracticeCommand(owner, source, 1L, null, served, null, null, null)).sessionId();
    }

    private MockHttpServletResponse abandon(long id, long user) throws Exception {
        return mvc.perform(post("/api/practice/sessions/{id}/abandon", id)
                .header("Authorization", bearer(user, "LEARNER")).header("X-Request-Id", "abandon-test"))
                .andReturn().getResponse();
    }

    private String bearer(long user, String role) {
        return "Bearer " + token(user, role);
    }

    private String token(long user, String role) {
        return jwt.issueAccessToken(user, List.of(role), false, UUID.randomUUID());
    }

    private void assertNoCompletionEffects() {
        assertThat(jdbc.queryForObject("select count(*) from learning.study_events where user_id=?", Long.class, owner))
                .isZero();
        assertThat(probe.graded).isEmpty();
        assertThat(probe.finished).isEmpty();
        verify(handler, never()).onFinish(any());
        verify(handler, never()).isSuspicious(any());
    }

    private void assertError(MockHttpServletResponse response, int expectedStatus, String code) throws Exception {
        assertThat(response.getStatus()).isEqualTo(expectedStatus);
        JsonNode error = mapper.readTree(response.getContentAsString());
        assertThat(error.path("error_code").asText()).isEqualTo(code);
        assertThat(error.path("message").asText()).isNotBlank();
        assertThat(error.path("request_id").asText()).isNotBlank();
        assertThat(error.has("stackTrace")).isFalse();
    }
}
