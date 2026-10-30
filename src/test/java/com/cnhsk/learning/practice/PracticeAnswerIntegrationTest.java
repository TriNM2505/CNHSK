package com.cnhsk.learning.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.security.RateLimiter;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.parser.OpenAPIV3Parser;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@AutoConfigureMockMvc
class PracticeAnswerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private PracticeService practice;
    @Autowired private AttemptRepository attempts;
    @Autowired private AttemptItemRepository items;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private StringRedisTemplate redis;
    @MockitoBean private Clock clock;
    @MockitoBean private PracticeSourceHandler handler;
    @MockitoSpyBean private RateLimiter counters;
    private long owner;

    @BeforeEach
    void setup() throws Exception {
        when(clock.instant()).thenReturn(PracticeServiceTest.NOW);
        when(handler.source()).thenReturn(PracticeSource.TOPIC_PRACTICE);
        when(handler.enrichMeta(any(), any())).thenReturn(Map.of("sourceChecked", true));
        owner = createUser();
    }

    @Test
    void FR004_AT02_missingAndUnownedSessionsHaveIdenticalForbiddenErrors() throws Exception {
        long id = start(PracticeSource.TOPIC_PRACTICE, List.of(PracticeServiceTest.choice(1)));
        long other = createUser();
        String missing = submit(Long.MAX_VALUE, other, body(1, "A")).getContentAsString();
        var denied = submit(id, other, body(1, "A"));
        assertThat(denied.getStatus()).isEqualTo(403);
        assertThat(mapper.readTree(missing)).isEqualTo(mapper.readTree(denied.getContentAsString()));
        assertThat(mapper.readTree(missing).path("error_code").asText()).isEqualTo("ATTEMPT_NOT_OWNED");
        assertNoEvidence(id);
    }

    @Test
    void FR004_AT03_expiredAnswerReturns422AndCommitsExpiredWithoutErasingEvidence() throws Exception {
        long id = start(PracticeSource.TOPIC_PRACTICE,
                List.of(PracticeServiceTest.choice(1), PracticeServiceTest.choice(2)));
        assertThat(submit(id, owner, body(1, "A")).getStatus()).isEqualTo(200);
        Attempt attempt = attempts.findById(id).orElseThrow();
        // PostgreSQL stores microseconds, so a one-nanosecond offset can round back to NOW.
        attempt.setExpiresAt(PracticeServiceTest.NOW.minusSeconds(1));
        attempts.saveAndFlush(attempt);
        assertThat(attempts.findById(id).orElseThrow().getExpiresAt()).isBefore(PracticeServiceTest.NOW);
        assertError(submit(id, owner, body(2, "A")), 422, "SESSION_EXPIRED");
        assertThat(attempts.findById(id).orElseThrow().getStatus()).isEqualTo("EXPIRED");
        assertThat(items.findByAttemptIdOrderByOrderIndexAsc(id)).hasSize(1);
        assertError(submit(id, owner, body(2, "A")), 422, "SESSION_EXPIRED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBMITTED", "ABANDONED"})
    void FR004_terminalSessionsRejectAnswersWithoutChanges(String terminal) throws Exception {
        long id = start(PracticeSource.TOPIC_PRACTICE, List.of(PracticeServiceTest.choice(1)));
        Attempt attempt = attempts.findById(id).orElseThrow();
        attempt.setStatus(terminal);
        if ("SUBMITTED".equals(terminal)) {
            attempt.setSubmittedAt(PracticeServiceTest.NOW);
        }
        attempts.saveAndFlush(attempt);
        assertError(submit(id, owner, body(1, "A")), 409, "SESSION_ALREADY_SUBMITTED");
        assertThat(attempts.findById(id).orElseThrow().getStatus()).isEqualTo(terminal);
        assertNoEvidence(id);
    }

    @Test
    void FR004_exactExpiryBoundaryStillAcceptsAnswers() throws Exception {
        long id = start(PracticeSource.TOPIC_PRACTICE, List.of(PracticeServiceTest.choice(1)));
        Attempt attempt = attempts.findById(id).orElseThrow();
        attempt.setExpiresAt(PracticeServiceTest.NOW);
        attempts.saveAndFlush(attempt);
        assertThat(submit(id, owner, body(1, "A")).getStatus()).isEqualTo(200);
    }

    @Test
    void FR004_AT04_AT06_rejectsUnservedNumbersAndForeignOptions() throws Exception {
        long id = start(PracticeSource.TOPIC_PRACTICE, List.of(PracticeServiceTest.choice(1)));
        assertError(submit(id, owner, body(99, "A")), 400, "ITEM_NOT_IN_SESSION");
        assertError(submit(id, owner, body(1, "Z")), 400, "OPTION_NOT_IN_ITEM");
        assertNoEvidence(id);
    }

    @ParameterizedTest
    @EnumSource(value = PracticeSource.class, names = {"TOPIC_PRACTICE", "TOPIC_STUDY"})
    void FR004_AT05_concurrentAnswersWriteExactlyOneRowAndReturn409(PracticeSource source) throws Exception {
        long id = start(source, List.of(source == PracticeSource.TOPIC_STUDY
                ? PracticeServiceTest.card(1) : PracticeServiceTest.choice(1)));
        Map<String, Object> request = body(1, source == PracticeSource.TOPIC_STUDY ? "LEARNED" : "A");
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<MockHttpServletResponse>>();
            for (int index = 0; index < 2; index++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!go.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent test did not start.");
                    }
                    return submit(id, owner, request);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            var responses = List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
            assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(200, 409);
            assertError(responses.stream().filter(response -> response.getStatus() == 409).findFirst().orElseThrow(),
                    409, "ANSWER_ALREADY_SUBMITTED");
        } finally {
            go.countDown();
            pool.shutdownNow();
        }
        assertThat(items.findByAttemptIdOrderByOrderIndexAsc(id)).hasSize(1);
        assertError(submit(id, owner, request), 409, "ANSWER_ALREADY_SUBMITTED");
    }

    @Test
    void FR005_AT07_immediateGradesOnServerAndKeepsSuspiciousEvidenceAndSourceMetadata() throws Exception {
        long id = start(PracticeSource.TOPIC_PRACTICE,
                List.of(PracticeServiceTest.choice(1), PracticeServiceTest.choice(2)));
        var quick = body(1, "A");
        quick.put("durationMs", 300);
        quick.put("clientMeta", Map.of("correct", false, "score", 999, "suspicious", false));
        JsonNode result = json(submit(id, owner, quick));
        assertThat(result.path("correct").asBoolean()).isTrue();
        assertThat(result.path("correctAnswer").asText()).isEqualTo("A");
        assertThat(result.path("counted").asBoolean()).isTrue();
        assertThat(result.path("explanation").asText()).isEqualTo("secret-explanation");
        AttemptItem recorded = items.findByAttemptIdOrderByOrderIndexAsc(id).get(0);
        assertThat(recorded.getCorrect()).isTrue();
        assertThat(recorded.getMeta()).containsEntry("suspicious", true).containsEntry("sourceChecked", true)
                .containsEntry("replayCount", 0).doesNotContainKeys("score", "correct");
        assertThat(json(submit(id, owner, body(2, "B"))).path("correct").asBoolean()).isFalse();
    }

    @Test
    void FR004_AT08_AT09_validatesReplayAudioAndDoesNotCountAudioFailures() throws Exception {
        long id = start(PracticeSource.TOPIC_PRACTICE, List.of(audio(1), PracticeServiceTest.choice(2)));
        var request = body(1, "A");
        request.put("replayCount", 4);
        assertError(submit(id, owner, request), 400, "TOO_MANY_REPLAYS");
        var noAudio = body(2, null);
        noAudio.put("audioFailed", true);
        assertError(submit(id, owner, noAudio), 400, "AUDIO_NOT_IN_ITEM");
        request.put("replayCount", 3);
        request.put("audioFailed", true);
        request.remove("answer");
        JsonNode result = json(submit(id, owner, request));
        assertThat(result.path("counted").asBoolean()).isFalse();
        assertThat(result.has("correctAnswer")).isFalse();
        AttemptItem recorded = items.findByAttemptIdOrderByOrderIndexAsc(id).get(0);
        assertThat(recorded.getCorrect()).isNull();
        assertThat(recorded.getMeta()).containsEntry("audioFailed", true).containsEntry("replayCount", 3);
    }

    @Test
    void FR004_AT10_skipIsUncountedAndForbiddenForSourcesThatDisallowIt() throws Exception {
        long id = start(PracticeSource.TOPIC_PRACTICE, List.of(PracticeServiceTest.choice(1)));
        var request = body(1, null);
        request.put("skipped", true);
        assertThat(json(submit(id, owner, request)).path("counted").asBoolean()).isFalse();
        AttemptItem recorded = items.findByAttemptIdOrderByOrderIndexAsc(id).get(0);
        assertThat(recorded.getCorrect()).isNull();
        assertThat(recorded.getMeta()).containsEntry("skipped", true);
        long blocked = start(PracticeSource.PRON_STAGE, List.of(PracticeServiceTest.choice(1)));
        assertError(submit(blocked, owner, request), 400, "SKIP_NOT_ALLOWED");
        assertNoEvidence(blocked);
    }

    @Test
    void FR006_AT12_replacesDraftWithCompleteEvidenceAndNeverExposesResults() throws Exception {
        long id = start(PracticeSource.TOPIC_TEST, List.of(audio(1), PracticeServiceTest.choice(2)));
        var request = body(1, null);
        request.put("audioFailed", true);
        request.put("replayCount", 2);
        assertDeferred(submit(id, owner, request));
        JsonNode draft = attempts.findById(id).orElseThrow().getDraftAnswers().path("1");
        assertThat(draft.path("audioFailed").asBoolean()).isTrue();
        assertThat(draft.path("replayCount").asInt()).isEqualTo(2);
        request = body(1, "B");
        request.put("replayCount", 3);
        assertDeferred(submit(id, owner, request));
        Attempt saved = attempts.findById(id).orElseThrow();
        draft = saved.getDraftAnswers().path("1");
        assertThat(draft.path("answer").asText()).isEqualTo("B");
        assertThat(draft.path("audioFailed").asBoolean()).isFalse();
        assertThat(draft.path("replayCount").asInt()).isEqualTo(3);
        assertThat(saved.getDraftSavedAt()).isEqualTo(PracticeServiceTest.NOW);
        var skip = body(2, null);
        skip.put("skipped", true);
        assertDeferred(submit(id, owner, skip));
        assertThat(attempts.findById(id).orElseThrow().getDraftAnswers().path("2").path("skipped").asBoolean()).isTrue();
        assertThat(items.findByAttemptIdOrderByOrderIndexAsc(id)).isEmpty();
        String view = mvc.perform(get("/api/practice/sessions/{id}", id)
                        .header("Authorization", bearer(owner, "LEARNER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(view).doesNotContain("\"correct\"", "correctAnswer", "\"accept\"", "explanation", "secret-pinyin");
        assertThat(mapper.readTree(view).path("answered").get(0).path("answer").asText()).isEqualTo("B");
    }

    @ParameterizedTest
    @EnumSource(value = PracticeSource.class, names = {"TOPIC_STUDY", "TOPIC_TEST"})
    void FR007_nonGradableItemsAreAlwaysRecordedOnceWithoutGrading(PracticeSource source) throws Exception {
        long id = start(source, List.of(PracticeServiceTest.card(1)));
        JsonNode result = json(submit(id, owner, body(1, "KNOWN")));
        assertThat(result.path("recorded").asBoolean()).isTrue();
        assertThat(result.fieldNames()).toIterable().containsExactlyInAnyOrder("no", "recorded");
        AttemptItem recorded = items.findByAttemptIdOrderByOrderIndexAsc(id).get(0);
        assertThat(recorded.getCorrect()).isNull();
        assertThat(recorded.getAnswer()).isEqualTo("KNOWN");
        assertError(submit(id, owner, body(1, "LEARNED")), 409, "ANSWER_ALREADY_SUBMITTED");
    }

    @ParameterizedTest
    @EnumSource(value = PracticeSource.class, names = {"TOPIC_PRACTICE", "TOPIC_TEST"})
    void FR004_sourceHookRejectionRollsBackEveryAnswerAndDraftWrite(PracticeSource source) throws Exception {
        when(handler.source()).thenReturn(source);
        long id = start(source, List.of(PracticeServiceTest.choice(1)));
        doAnswer(invocation -> {
            Attempt attempt = attempts.findById(id).orElseThrow();
            attempt.setDraftAnswers(mapper.valueToTree(Map.of("1", Map.of("answer", "partial"))));
            AttemptItem partial = new AttemptItem(id, 1, "MEANING_CHOICE", "VOCAB");
            partial.setAnswer("partial");
            items.saveAndFlush(partial);
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Nguồn từ chối câu trả lời.", Map.of());
        }).when(handler).validateAnswer(any(), any());
        assertError(submit(id, owner, body(1, "A")), 422, "VALIDATION_FAILED");
        assertNoEvidence(id);
    }

    @Test
    void FR006_enrichmentFailurePreservesPreviousDraftAndRollsBackReplacement() throws Exception {
        when(handler.source()).thenReturn(PracticeSource.TOPIC_TEST);
        long id = start(PracticeSource.TOPIC_TEST, List.of(PracticeServiceTest.choice(1)));
        assertDeferred(submit(id, owner, body(1, "A")));
        doThrow(new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Không lưu nháp.", Map.of())).when(handler).enrichMeta(any(), any());
        assertError(submit(id, owner, body(1, "B")), 422, "VALIDATION_FAILED");
        assertThat(attempts.findById(id).orElseThrow().getDraftAnswers().path("1").path("answer").asText()).isEqualTo("A");
        assertThat(items.findByAttemptIdOrderByOrderIndexAsc(id)).isEmpty();
    }

    @Test
    void FR004_rejectsInvalidDtoEvidenceAndMutuallyExclusiveFlagsBeforeWriting() throws Exception {
        long id = start(PracticeSource.TOPIC_TEST, List.of(audio(1)));
        for (String invalid : List.of("{}", "{", "null", "{\"no\":1,\"durationMs\":\"invalid\"}",
                "{\"no\":1,\"durationMs\":-1}", "{\"no\":0,\"durationMs\":1000}",
                "{\"no\":1,\"durationMs\":3600001}", "{\"no\":1,\"durationMs\":1000,\"replayCount\":-1}",
                "{\"no\":1,\"durationMs\":1000}", "{\"no\":1,\"durationMs\":1000,\"skipped\":true,\"audioFailed\":true}")) {
            assertError(mvc.perform(post("/api/practice/sessions/{id}/answers", id)
                            .header("Authorization", bearer(owner, "LEARNER"))
                            .contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andReturn().getResponse(), 422, "VALIDATION_FAILED");
        }
        var tooLong = body(1, "A".repeat(101));
        assertError(submit(id, owner, tooLong), 422, "VALIDATION_FAILED");
        assertNoEvidence(id);
    }

    @Test
    void FR004_serviceAlsoValidatesEvidenceForInternalApiCallers() {
        long id = start(PracticeSource.TOPIC_PRACTICE, List.of(PracticeServiceTest.choice(1)));
        List<Answer> invalid = new ArrayList<>();
        invalid.add(null);
        invalid.add(new Answer(0, "A", 1000, 0, false, false, null));
        invalid.add(new Answer(1, "A", -1, 0, false, false, null));
        invalid.add(new Answer(1, "A", 3_600_001, 0, false, false, null));
        invalid.add(new Answer(1, "A", 1000, -1, false, false, null));
        invalid.add(new Answer(1, null, 1000, 0, false, false, null));
        invalid.add(new Answer(1, "A".repeat(101), 1000, 0, false, false, null));
        invalid.add(new Answer(1, null, 1000, 0, true, true, null));
        for (Answer answer : invalid) {
            assertThatThrownBy(() -> practice.answer(owner, id, answer)).isInstanceOfSatisfying(ApiException.class,
                    error -> assertThat(error.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        }
        assertNoEvidence(id);
    }

    @Test
    void FR004_answersRequireLearnerRoleAndCookieCsrfWhileBearerNeedsNoCsrf() throws Exception {
        long id = start(PracticeSource.TOPIC_PRACTICE, List.of(PracticeServiceTest.choice(1)));
        String body = mapper.writeValueAsString(body(1, "A"));
        mvc.perform(post("/api/practice/sessions/{id}/answers", id).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        for (String role : List.of("ADMIN", "TEACHER", "MODERATOR", "CONTENT_EDITOR", "ACCOUNTANT")) {
            assertError(submit(id, owner, role, body(1, "A")), 403, "FORBIDDEN");
        }
        Cookie access = new Cookie("access_token", token(owner, "LEARNER"));
        mvc.perform(post("/api/practice/sessions/{id}/answers", id).cookie(access)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        var csrf = mvc.perform(get("/api/auth/csrf").cookie(access)).andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        mvc.perform(post("/api/practice/sessions/{id}/answers", id).cookie(access, csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        long bearerId = start(PracticeSource.TOPIC_PRACTICE, List.of(PracticeServiceTest.choice(1)));
        assertThat(submit(bearerId, owner, body(1, "A")).getStatus()).isEqualTo(200);
    }

    @Test
    void FR004_AT11_sixHundredIsAllowedAnd601Returns429WithRetryUntilNextHour() throws Exception {
        long id = start(PracticeSource.TOPIC_TEST, List.of(PracticeServiceTest.choice(1)));
        String actualHour = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZoneOffset.UTC)
                .format(PracticeServiceTest.NOW);
        String key = "practice:ans:" + owner + ":" + actualHour;
        redis.opsForValue().set(key, "599", Duration.ofMinutes(30));
        assertDeferred(submit(id, owner, body(1, "A")));
        var response = submit(id, owner, body(1, "B"));
        assertError(response, 429, "RATE_LIMITED");
        long retry = (Duration.between(PracticeServiceTest.NOW,
                PracticeServiceTest.NOW.truncatedTo(java.time.temporal.ChronoUnit.HOURS).plusSeconds(3600))
                .toMillis() + 999) / 1000;
        assertThat(mapper.readTree(response.getContentAsString()).path("retryAfterSeconds").asLong()).isEqualTo(retry);
        assertThat(attempts.findById(id).orElseThrow().getDraftAnswers().path("1").path("answer").asText()).isEqualTo("A");
        assertThat(redis.opsForValue().get(key)).isEqualTo("601");
    }

    @Test
    void FR004_AT11_redisCounterFailureStillAllowsAnswerWithoutExposingExceptionText() throws Exception {
        long id = start(PracticeSource.TOPIC_PRACTICE, List.of(PracticeServiceTest.choice(1)));
        doThrow(new IllegalStateException("private Redis connection detail"))
                .when(counters).increment(anyString(), any(Duration.class));
        assertThat(submit(id, owner, body(1, "A")).getStatus()).isEqualTo(200);
        assertThat(items.findByAttemptIdOrderByOrderIndexAsc(id)).hasSize(1);
    }

    @Test
    void FR004_AT11_stoppedRedisUsesTheSameFailOpenBehaviorAsStarts() {
        try (var isolated = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379)) {
            isolated.start();
            var config = new RedisStandaloneConfiguration(isolated.getHost(), isolated.getMappedPort(6379));
            var client = LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(300))
                    .shutdownTimeout(Duration.ZERO).build();
            var factory = new LettuceConnectionFactory(config, client);
            factory.afterPropertiesSet();
            factory.start();
            try {
                var limiter = new PracticeRateLimiter(new RateLimiter(new StringRedisTemplate(factory)),
                        Clock.fixed(PracticeServiceTest.NOW, ZoneOffset.UTC));
                limiter.checkAnswer(owner);
                isolated.stop();
                assertThatCode(() -> limiter.checkAnswer(owner)).doesNotThrowAnyException();
                assertThatCode(() -> limiter.checkStart(owner)).doesNotThrowAnyException();
            } finally {
                factory.destroy();
            }
        }
    }

    @Test
    void FR005_FR011_AT18_pinyinIsGradedByTheServerUsingAcceptedForms() throws Exception {
        var pinyin = new ServedItem(1, ItemType.PINYIN_INPUT, Skill.VOCAB, null,
                PracticeServiceTest.choice(1).prompt(), null, null, List.of("wo3men5"), "Giải thích",
                new ServedItem.Rules(800, 0, false));
        long id = start(PracticeSource.TOPIC_PRACTICE, List.of(pinyin));
        assertThat(json(submit(id, owner, body(1, "wǒmen"))).path("correct").asBoolean()).isTrue();
        assertThat(items.findByAttemptIdOrderByOrderIndexAsc(id).get(0).getCorrect()).isTrue();
    }

    @Test
    void FR004_openApiContainsExactlyApprovedAnswersStatusesAndEightErrorsWithoutChangingSharedSchema() throws Exception {
        String yaml = Files.readString(Path.of("specs/003-vocab-topics/contracts/openapi-practice-vocab.yaml"));
        var parsed = new OpenAPIV3Parser().readContents(yaml);
        assertThat(parsed.getMessages()).isEmpty();
        var api = parsed.getOpenAPI();
        var operation = api.getPaths().get("/api/practice/sessions/{id}/answers").getPost();
        assertThat(operation.getResponses().keySet()).containsExactlyInAnyOrder("200", "400", "401", "403", "409", "422", "429");
        Map<String, Integer> expected = Map.of("SESSION_EXPIRED", 422, "SESSION_ALREADY_SUBMITTED", 409,
                "ITEM_NOT_IN_SESSION", 400, "ANSWER_ALREADY_SUBMITTED", 409, "OPTION_NOT_IN_ITEM", 400,
                "TOO_MANY_REPLAYS", 400, "AUDIO_NOT_IN_ITEM", 400, "SKIP_NOT_ALLOWED", 400);
        expected.forEach((code, status) -> assertThat(operation.getDescription()).contains(status + " " + code + ":"));
        JsonNode schema = new ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory()).readTree(yaml)
                .at("/components/schemas/ErrorResponse/properties/error_code");
        assertThat(schema.path("type").asText()).isEqualTo("string");
        assertThat(schema.has("enum")).isFalse();
        assertThat(schema.size()).isEqualTo(1);
    }

    private long createUser() throws Exception {
        String password = "Aa1" + UUID.randomUUID();
        return mapper.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.test",
                                "password", password, "confirmPassword", password,
                                "displayName", "Answer test user", "acceptTerms", true))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("userId").asLong();
    }

    private long start(PracticeSource source, List<ServedItem> served) {
        return practice.start(new StartPracticeCommand(owner, source, 1L, null, served, null, null, null)).sessionId();
    }

    private Map<String, Object> body(int no, String answer) {
        var result = new LinkedHashMap<String, Object>();
        result.put("no", no);
        result.put("answer", answer);
        result.put("durationMs", 1000);
        return result;
    }

    private MockHttpServletResponse submit(long id, long user, Map<String, Object> body) throws Exception {
        return submit(id, user, "LEARNER", body);
    }

    private MockHttpServletResponse submit(long id, long user, String role, Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/practice/sessions/{id}/answers", id).header("Authorization", bearer(user, role))
                        .header("X-Request-Id", "answer-test").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andReturn().getResponse();
    }

    private String bearer(long user, String role) {
        return "Bearer " + token(user, role);
    }

    private String token(long user, String role) {
        return jwt.issueAccessToken(user, List.of(role), false, UUID.randomUUID());
    }

    private JsonNode json(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(200);
        return mapper.readTree(response.getContentAsString());
    }

    private void assertDeferred(MockHttpServletResponse response) throws Exception {
        JsonNode result = json(response);
        assertThat(result.fieldNames()).toIterable().containsExactlyInAnyOrder("no", "accepted");
        assertThat(result.path("accepted").asBoolean()).isTrue();
        assertThat(response.getContentAsString()).doesNotContain("\"correct\"", "correctAnswer", "\"accept\"", "explanation");
    }

    private void assertError(MockHttpServletResponse response, int expectedStatus, String code) throws Exception {
        assertThat(response.getStatus()).isEqualTo(expectedStatus);
        JsonNode error = mapper.readTree(response.getContentAsString());
        assertThat(error.path("error_code").asText()).isEqualTo(code);
        assertThat(error.path("message").asText()).isNotBlank();
        assertThat(error.path("request_id").asText()).isNotBlank();
        assertThat(error.has("stackTrace")).isFalse();
    }

    private void assertNoEvidence(long id) {
        assertThat(items.findByAttemptIdOrderByOrderIndexAsc(id)).isEmpty();
        assertThat(attempts.findById(id).orElseThrow().getDraftAnswers()).isNull();
    }

    private ServedItem audio(int no) {
        ServedItem choice = PracticeServiceTest.choice(no);
        return new ServedItem(no, ItemType.AUDIO_WORD_CHOICE, Skill.LISTENING, null, choice.prompt(),
                choice.options(), choice.answer(), choice.accept(), choice.explanation(), new ServedItem.Rules(800, 3, true));
    }
}
