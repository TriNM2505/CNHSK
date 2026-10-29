package com.cnhsk.learning.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cnhsk.learning.practice.PracticeService.StartPracticeCommand;
import com.cnhsk.learning.practice.PracticeSourceHandler.Answer;
import com.cnhsk.learning.practice.entity.Attempt;
import com.cnhsk.learning.practice.entity.AttemptItem;
import com.cnhsk.learning.practice.entity.AttemptItemRepository;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class PracticeControllerIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = PracticeServiceTest.NOW;
    @Autowired private MockMvc mvc;
    @Autowired private PracticeService service;
    @Autowired private AttemptRepository attempts;
    @Autowired private AttemptItemRepository items;
    @Autowired private JwtService jwt;
    @Autowired private ObjectMapper mapper;
    @MockitoBean private Clock clock;
    private long owner;
    private long other;

    @BeforeEach
    void setup() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        owner = createUser();
        other = createUser();
    }

    @Test
    void FR010_missingAndOtherUsersSessionReturnTheSameForbiddenBody() throws Exception {
        long id = start(PracticeSource.TOPIC_STUDY, 1L).sessionId();
        String missing = mvc.perform(get("/api/practice/sessions/{id}", Long.MAX_VALUE)
                        .header("Authorization", bearer(other, "LEARNER")).header("X-Request-Id", "same-request"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("ATTEMPT_NOT_OWNED"))
                .andExpect(header().string("X-Request-Id", "same-request"))
                .andReturn().getResponse().getContentAsString();
        String denied = mvc.perform(get("/api/practice/sessions/{id}", id)
                        .header("Authorization", bearer(other, "LEARNER")).header("X-Request-Id", "same-request"))
                .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(denied)).isEqualTo(mapper.readTree(missing));
        assertThat(mapper.readTree(denied).fieldNames()).toIterable()
                .containsExactlyInAnyOrder("error_code", "message", "request_id");
    }

    @Test
    void FR010_onlyLearnersCanReadSessionsAndExistingAuthenticationStillWorks() throws Exception {
        long id = start(PracticeSource.TOPIC_STUDY, 1L).sessionId();
        mvc.perform(get("/api/practice/sessions/{id}", id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/practice/sessions/active").param("source", "TOPIC_STUDY"))
                .andExpect(status().isUnauthorized());
        for (String role : List.of("ADMIN", "TEACHER", "CONTENT_EDITOR", "MODERATOR", "ACCOUNTANT")) {
            mvc.perform(get("/api/practice/sessions/{id}", id).header("Authorization", bearer(owner, role)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/practice/sessions/active").param("source", "TOPIC_STUDY")
                            .header("Authorization", bearer(owner, role)))
                    .andExpect(status().isForbidden());
        }
        // Learning remains available to users who have not verified their email.
        mvc.perform(get("/api/practice/sessions/{id}", id).header("Authorization", bearer(owner, "LEARNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value(id));
        mvc.perform(get("/api/practice/sessions/{id}", id)
                        .cookie(new jakarta.servlet.http.Cookie("access_token", token(owner, "LEARNER"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(owner, "LEARNER")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/practice/sessions/{id}/answers", id).header("Authorization", bearer(owner, "LEARNER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void FR010_invalidPathAndQueryValuesUseTheValidationErrorContract() throws Exception {
        for (String path : List.of("/api/practice/sessions/not-a-number", "/api/practice/sessions/9999999999999999999999",
                "/api/practice/sessions/active", "/api/practice/sessions/active?source=UNKNOWN",
                "/api/practice/sessions/active?source=", "/api/practice/sessions/active?source=TOPIC_STUDY&refId=no")) {
            mvc.perform(get(path).header("Authorization", bearer(owner, "LEARNER")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.details[0].field").isNotEmpty())
                    .andExpect(jsonPath("$.request_id").isNotEmpty());
        }
    }

    @Test
    void FR010_activeWithoutRefIdSearchesAcrossRefsAndBreaksTiesById() throws Exception {
        long first = start(PracticeSource.TOPIC_STUDY, 1L).sessionId();
        long latest = start(PracticeSource.TOPIC_STUDY, 2L).sessionId();
        startFor(other, PracticeSource.TOPIC_STUDY, 2L);
        start(PracticeSource.TOPIC_PRACTICE, 2L);
        long submitted = start(PracticeSource.TOPIC_STUDY, 3L).sessionId();
        changeStatus(submitted, "SUBMITTED");
        long expired = start(PracticeSource.TOPIC_STUDY, 4L).sessionId();
        Attempt old = attempts.findById(expired).orElseThrow();
        old.setExpiresAt(NOW.minusSeconds(1));
        attempts.saveAndFlush(old);
        assertActive(null, latest);
        assertActive("1", first);
        assertActive("2", latest);
        mvc.perform(get("/api/practice/sessions/active").param("source", "TOPIC_STUDY").param("refId", "99")
                        .header("Authorization", bearer(owner, "LEARNER")))
                .andExpect(status().isNoContent()).andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty());
    }

    @Test
    void FR010_activeSortsByServedTimeBeforeIdAndAcceptsExactExpiryBoundary() throws Exception {
        long newerTime = start(PracticeSource.TOPIC_STUDY, null).sessionId();
        when(clock.instant()).thenReturn(NOW.minusSeconds(10));
        start(PracticeSource.TOPIC_STUDY, null);
        when(clock.instant()).thenReturn(NOW);
        assertActive(null, newerTime);
        Attempt boundary = attempts.findById(newerTime).orElseThrow();
        boundary.setExpiresAt(NOW);
        attempts.saveAndFlush(boundary);
        assertActive(null, newerTime);
        mvc.perform(get("/api/practice/sessions/{id}", newerTime).header("Authorization", bearer(owner, "LEARNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void FR010_lazyExpiryCommitsAndPreservesPreviouslyRecordedEvidence() throws Exception {
        var view = service.start(new StartPracticeCommand(owner, PracticeSource.TOPIC_STUDY, 1L, null,
                List.of(PracticeServiceTest.card(1)), List.of(new Answer(1, "LEARNED", 1000, 0, false, false, null)),
                null, null));
        when(clock.instant()).thenReturn(view.expiresAt().plusMillis(1));
        mvc.perform(get("/api/practice/sessions/{id}", view.sessionId())
                        .header("Authorization", bearer(owner, "LEARNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.answered[0].answer").value("LEARNED"));
        assertThat(attempts.findById(view.sessionId()).orElseThrow().getStatus()).isEqualTo("EXPIRED");
        assertThat(items.findByAttemptIdOrderByOrderIndexAsc(view.sessionId())).hasSize(1);
        assertThat(service.findActive(owner, PracticeSource.TOPIC_STUDY, 1L)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBMITTED", "ABANDONED", "EXPIRED"})
    void FR010_ownerCanRestoreTerminalSessionsWithoutChangingTheirStatus(String terminal) throws Exception {
        long id = start(PracticeSource.TOPIC_STUDY, 1L).sessionId();
        changeStatus(id, terminal);
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofDays(2)));
        mvc.perform(get("/api/practice/sessions/{id}", id).header("Authorization", bearer(owner, "LEARNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(terminal));
        Attempt restored = attempts.findById(id).orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(terminal);
        assertThat(restored.getSubmittedAt()).isEqualTo("SUBMITTED".equals(terminal) ? NOW : null);
    }

    @Test
    void FR003_immediateRestoresOnlyGradedAnswersAndNeverServedAcceptOrExplanation() throws Exception {
        long id = start(PracticeSource.TOPIC_PRACTICE, 1L).sessionId();
        record(id, 1, "B", false, Map.of());
        JsonNode json = read(id);
        assertThat(json.path("answered").get(0).path("answer").asText()).isEqualTo("B");
        assertThat(json.path("answered").get(0).path("correctAnswer").asText()).isEqualTo("A");
        assertThat(json.path("answered").get(0).path("counted").asBoolean()).isTrue();
        assertThat(json.path("items").toString()).doesNotContain("\"answer\"", "\"accept\"", "explanation");
        assertThat(json.toString()).doesNotContain("secret-pinyin", "secret-explanation");
    }

    @Test
    void FR003_skippedAndAudioFailedEvidenceDoesNotCountOrExposeAnUngradedAnswer() throws Exception {
        for (String reason : List.of("skipped", "audioFailed")) {
            long id = start(PracticeSource.TOPIC_PRACTICE, 1L).sessionId();
            record(id, 1, null, null, Map.of(reason, true));
            JsonNode state = read(id).path("answered").get(0);
            assertThat(state.path("counted").asBoolean()).isFalse();
            assertThat(state.has("correctAnswer")).isFalse();
        }
    }

    @Test
    void FR003_deferredRestorationNeverRevealsCorrectAnswerAcceptOrExplanationBeforeFinish() throws Exception {
        long id = start(PracticeSource.TOPIC_TEST, 1L).sessionId();
        Attempt attempt = attempts.findById(id).orElseThrow();
        attempt.setDraftAnswers(mapper.valueToTree(Map.of("1", Map.of("answer", "B", "durationMs", 1000),
                "2", Map.of("answer", "user-pinyin"), "3", Map.of("skipped", true))));
        attempts.saveAndFlush(attempt);
        // Even stored graded evidence must not make an IN_PROGRESS deferred session reveal results.
        record(id, 2, "user-pinyin", true, Map.of());
        JsonNode view = read(id);
        assertThat(view.path("answered")).hasSize(3);
        assertThat(view.path("answered").get(0).path("answer").asText()).isEqualTo("B");
        assertThat(view.path("answered").get(1).path("answer").asText()).isEqualTo("user-pinyin");
        assertThat(view.path("answered").get(2).path("answer").isNull()).isTrue();
        for (JsonNode state : view.path("answered")) {
            assertThat(state.path("correct").isNull()).isTrue();
        }
        assertThat(view.toString()).doesNotContain("correctAnswer", "\"accept\"", "explanation",
                "secret-pinyin", "secret-explanation");
        assertActive("1", id);
    }

    @Test
    void FR002_startCommitsSnapshotAndPreRecordedItemsTogetherAndInvalidEvidenceRollsBack() {
        long before = attempts.count();
        assertThatThrownBy(() -> service.start(new StartPracticeCommand(owner, PracticeSource.TOPIC_STUDY, 1L, null,
                List.of(PracticeServiceTest.card(1), PracticeServiceTest.card(2)),
                List.of(new Answer(1, "LEARNED", 1000, 0, false, false, null),
                        new Answer(2, "KNOWN", -1, 0, false, false, null)), null, Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(attempts.count()).isEqualTo(before);
        var view = service.start(new StartPracticeCommand(owner, PracticeSource.TOPIC_STUDY, 1L, null,
                List.of(PracticeServiceTest.card(1)), List.of(new Answer(1, "KNOWN", 1200, 0, false, false, null)),
                null, Map.of("label", "safe")));
        assertThat(attempts.findById(view.sessionId()).orElseThrow().getServedItems().path("meta").path("label").asText())
                .isEqualTo("safe");
        assertThat(items.findByAttemptIdOrderByOrderIndexAsc(view.sessionId())).hasSize(1);
        assertThat(service.view(owner, view.sessionId()).answered().get(0).answer()).isEqualTo("KNOWN");
    }

    private long createUser() throws Exception {
        // Create identities through the public auth endpoint; all persistence is in Testcontainers.
        String password = "Aa1" + UUID.randomUUID();
        String body = mapper.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.test",
                "password", password, "confirmPassword", password,
                "displayName", "Practice test user", "acceptTerms", true));
        return mapper.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("userId").asLong();
    }

    private PracticeService.SessionView start(PracticeSource source, Long refId) {
        return startFor(owner, source, refId);
    }

    private PracticeService.SessionView startFor(long userId, PracticeSource source, Long refId) {
        return service.start(new StartPracticeCommand(userId, source, refId, null,
                source == PracticeSource.TOPIC_STUDY ? List.of(PracticeServiceTest.card(1))
                        : List.of(PracticeServiceTest.choice(1), PracticeServiceTest.choice(2)),
                null, null, null));
    }

    private void changeStatus(long id, String status) {
        Attempt attempt = attempts.findById(id).orElseThrow();
        attempt.setStatus(status);
        if ("SUBMITTED".equals(status)) {
            attempt.setSubmittedAt(clock.instant());
        }
        attempts.saveAndFlush(attempt);
    }

    private void record(long id, int no, String answer, Boolean correct, Map<String, Object> meta) {
        AttemptItem item = new AttemptItem(id, no, "MEANING_CHOICE", "VOCAB");
        item.setAnswer(answer);
        item.setCorrect(correct);
        item.setMeta(meta);
        items.saveAndFlush(item);
    }

    private JsonNode read(long id) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/practice/sessions/{id}", id)
                        .header("Authorization", bearer(owner, "LEARNER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void assertActive(String refId, long expectedId) throws Exception {
        String source = attempts.findById(expectedId).orElseThrow().getRefType();
        var request = get("/api/practice/sessions/active").param("source", source)
                .header("Authorization", bearer(owner, "LEARNER"));
        if (refId != null) {
            request.param("refId", refId);
        }
        mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value(expectedId));
    }

    private String bearer(long userId, String role) {
        return "Bearer " + token(userId, role);
    }

    private String token(long userId, String role) {
        return jwt.issueAccessToken(userId, List.of(role), false, UUID.randomUUID());
    }
}
