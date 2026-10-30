package com.cnhsk.learning.pronunciation;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import com.cnhsk.learning.content.entity.PronStage;
import com.cnhsk.learning.content.entity.PronUnit;
import com.cnhsk.learning.content.repository.PronStageRepository;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler.FinishContext;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.shared.security.JwtService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
class PronStageHandlerIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-30T06:00:00Z");
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:18-alpine")).withDatabaseName("pronunciation_finish_test")
            .withUsername("pronunciation_finish_owner").withPassword(UUID.randomUUID().toString());
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
    private static final GenericContainer<?> MAILPIT = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.27"))
            .withExposedPorts(1025, 8025);

    static {
        POSTGRES.start();
        REDIS.start();
        MAILPIT.start();
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
        registry.add("test.mailpit.base-url", () -> "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025));
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private PronStageRepository stages;
    @Autowired private AttemptRepository attempts;
    @Autowired private PracticeService practice;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @MockitoBean private Clock clock;
    @MockitoSpyBean private PronStageHandler handler;
    private long owner;

    @BeforeEach
    void setup() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        // The class owns this database; answer and finish transactions commit normally.
        jdbc.update("DELETE FROM learning.pron_stages WHERE stage_index BETWEEN 1 AND 8");
        owner = createUser();
    }

    @Test
    void FR010_AT08_incompleteFinishRollsBackAndAudioFailedStillCountsAsAnswered() throws Exception {
        PronStage first = stage(1, 10);
        stage(2, 10);
        JsonNode session = start(owner, 1);
        long id = session.path("sessionId").asLong();
        for (int index = 0; index < 4; index++) {
            JsonNode item = session.path("items").get(index);
            answer(owner, id, item, index < 2, index >= 2, 1000);
        }
        first.setUnits(units(1));
        stages.saveAndFlush(first);

        var response = finish(owner, id);

        assertError(response, 422, "STAGE_INCOMPLETE");
        assertThat(mapper.readTree(response.getContentAsString()).path("remaining").asInt()).isEqualTo(6);
        var stored = attempts.findById(id).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(stored.getSubmittedAt()).isNull();
        assertThat(stored.getScore()).isNull();
        assertThat(stored.getMaxScore()).isNull();
        assertThat(itemCount(id)).isEqualTo(4);
        assertThat(progressCount(owner)).isZero();
        assertThat(eventCount(owner)).isZero();
        for (int index = 4; index < 10; index++) {
            answer(owner, id, session.path("items").get(index), true, false, 1000);
        }
        assertThat(result(finish(owner, id)).path("extra").path("accuracy").decimalValue())
                .isEqualByComparingTo("100.00");
    }

    @Test
    void FR011_AT09_oneAudioFailureAndNineCorrectAnswersProduceOneHundredPercent() throws Exception {
        PronStage first = stage(1, 10);
        stage(2, 10);

        JsonNode finish = complete(1, 9, 1, 0);

        assertThat(finish.path("extra").path("accuracy").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(finish.path("score").asInt()).isEqualTo(9);
        assertThat(finish.path("maxScore").asInt()).isEqualTo(9);
        assertThat(finish.path("suspicious").asBoolean()).isFalse();
        assertThat(progress(first).get("percent")).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    void FR012_FR013_AT10_passingStoresBestAndOpensExactlyOneNextStageInTheResponse() throws Exception {
        PronStage first = stage(1, 10);
        PronStage second = stage(2, 10);
        PronStage third = stage(3, 10);

        JsonNode finish = complete(1, 9, 0, 0);
        JsonNode extra = finish.path("extra");

        assertThat(extra.fieldNames()).toIterable().containsExactlyInAnyOrder("stageIndex", "accuracy", "bestAccuracy",
                "passPercent", "passed", "unlockedNextStage", "nextStageIndex", "needed", "completedAllStages", "noScore");
        assertThat(extra.path("accuracy").decimalValue()).isEqualByComparingTo("90.00");
        assertThat(extra.path("bestAccuracy").decimalValue()).isEqualByComparingTo("90.00");
        assertThat(extra.path("needed").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(extra.path("passed").asBoolean()).isTrue();
        assertThat(extra.path("unlockedNextStage").asBoolean()).isTrue();
        assertThat(extra.path("nextStageIndex").asInt()).isEqualTo(2);
        assertThat(extra.path("completedAllStages").asBoolean()).isFalse();
        assertThat(extra.path("noScore").asBoolean()).isFalse();
        assertThat(progress(first).get("state")).isEqualTo("COMPLETED");
        assertThat(progress(second).get("state")).isEqualTo("AVAILABLE");
        assertThat(progress(second).get("percent")).isNull();
        assertThat(unlockedAt(first)).isEqualTo(NOW);
        assertThat(unlockedAt(second)).isEqualTo(NOW);
        assertThat(progressCount(owner, third.getId())).isZero();
        assertThat(eventCount(owner)).isEqualTo(1);
    }

    @Test
    void FR012_AT11_failedRetryKeepsTheCompletedStageBestAndOriginalUnlockTime() throws Exception {
        PronStage first = stage(1, 10);
        PronStage second = stage(2, 10);
        complete(1, 9, 0, 0);
        Instant firstUnlock = unlockedAt(first);
        Instant nextUnlock = unlockedAt(second);
        when(clock.instant()).thenReturn(NOW.plusSeconds(30));

        JsonNode extra = complete(1, 7, 0, 0).path("extra");

        assertThat(extra.path("accuracy").decimalValue()).isEqualByComparingTo("70.00");
        assertThat(extra.path("bestAccuracy").decimalValue()).isEqualByComparingTo("90.00");
        assertThat(extra.path("passed").asBoolean()).isFalse();
        assertThat(extra.path("needed").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(extra.path("nextStageIndex").asInt()).isEqualTo(2);
        assertThat(extra.path("unlockedNextStage").asBoolean()).isFalse();
        assertThat(extra.path("completedAllStages").asBoolean()).isFalse();
        assertThat(progress(first).get("percent")).isEqualTo(new BigDecimal("90.00"));
        assertThat(progress(first).get("state")).isEqualTo("COMPLETED");
        assertThat(unlockedAt(first)).isEqualTo(firstUnlock);
        assertThat(unlockedAt(second)).isEqualTo(nextUnlock);
        assertThat(jdbc.queryForObject("SELECT updated_at FROM learning.user_progress WHERE user_id = ? "
                        + "AND target_type = 'PRON_STAGE' AND target_id = ?", OffsetDateTime.class, owner, first.getId()).toInstant())
                .isEqualTo(NOW.plusSeconds(30));
        JsonNode summary = list();
        assertThat(summary.get(0).path("state").asText()).isEqualTo("COMPLETED");
        assertThat(summary.get(1).path("state").asText()).isEqualTo("AVAILABLE");
    }

    @Test
    void FR013_AT12_passingAgainDoesNotReinsertOrResetTheNextStage() throws Exception {
        stage(1, 10);
        PronStage second = stage(2, 10);
        complete(1, 9, 0, 0);
        Map<String, Object> before = progress(second);
        when(clock.instant()).thenReturn(NOW.plusSeconds(40));

        JsonNode extra = complete(1, 10, 0, 0).path("extra");

        assertThat(extra.path("unlockedNextStage").asBoolean()).isFalse();
        assertThat(extra.path("bestAccuracy").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(progress(second)).isEqualTo(before);
        assertThat(progressCount(owner, second.getId())).isEqualTo(1);
    }

    @Test
    void FR013_AT13_twoRealFinishThreadsCreateOneSuccessorAndKeepTheHigherAccuracy() throws Exception {
        PronStage first = stage(1, 10);
        PronStage second = stage(2, 10);
        PronStage third = stage(3, 10);
        JsonNode initial = start(owner, 1);
        long firstId = initial.path("sessionId").asLong();
        var snapshot = attempts.findById(firstId).orElseThrow().getServedItems().path("items");
        List<ServedItem> served = mapper.convertValue(snapshot, new TypeReference<List<ServedItem>>() { });
        long secondId = practice.start(new PracticeService.StartPracticeCommand(owner, PracticeSource.PRON_STAGE,
                first.getId(), null, served, null, null, null)).sessionId();
        JsonNode secondSession = mapper.valueToTree(practice.view(owner, secondId));
        answerAll(firstId, initial, 9, 0, 0);
        answerAll(secondId, secondSession, 10, 0, 0);
        CyclicBarrier beforeProgressWrite = new CyclicBarrier(2);
        PronStageHandler target = AopTestUtils.getUltimateTargetObject(handler);
        doAnswer(invocation -> {
            beforeProgressWrite.await(10, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(target).onFinish(any(FinishContext.class));
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var left = pool.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return result(finish(owner, firstId));
            });
            var right = pool.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return result(finish(owner, secondId));
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<JsonNode> results = List.of(left.get(20, TimeUnit.SECONDS), right.get(20, TimeUnit.SECONDS));
            assertThat(results.stream().filter(value -> value.path("extra").path("unlockedNextStage").asBoolean()).count())
                    .isEqualTo(1);
            assertThat(attempts.findById(firstId).orElseThrow().getStatus()).isEqualTo("SUBMITTED");
            assertThat(attempts.findById(secondId).orElseThrow().getStatus()).isEqualTo("SUBMITTED");
            assertThat(progress(first).get("percent")).isEqualTo(new BigDecimal("100.00"));
            assertThat(progress(first).get("state")).isEqualTo("COMPLETED");
            assertThat(progressCount(owner, second.getId())).isEqualTo(1);
            assertThat(progressCount(owner, third.getId())).isZero();
            assertThat(progressCount(owner)).isEqualTo(2);
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void FR014_AT14_perfectStageOneDoesNotUnlockStageThree() throws Exception {
        stage(1, 10);
        PronStage second = stage(2, 10);
        PronStage third = stage(3, 10);

        complete(1, 10, 0, 0);

        assertThat(progressCount(owner, second.getId())).isEqualTo(1);
        assertThat(progressCount(owner, third.getId())).isZero();
        assertThat(list().get(2).path("state").asText()).isEqualTo("LOCKED");
    }

    @Test
    void FR015_AT15_passingStageEightCompletesThePathWithoutCreatingAnotherProgressRow() throws Exception {
        PronStage last = stage(8, 10);
        seed(owner, last, null, "AVAILABLE");

        JsonNode extra = complete(8, 8, 0, 0).path("extra");

        assertThat(extra.path("accuracy").decimalValue()).isEqualByComparingTo("80.00");
        assertThat(extra.path("passed").asBoolean()).isTrue();
        assertThat(extra.path("completedAllStages").asBoolean()).isTrue();
        assertThat(extra.path("nextStageIndex").isNull()).isTrue();
        assertThat(extra.path("unlockedNextStage").asBoolean()).isFalse();
        assertThat(progressCount(owner)).isEqualTo(1);
        assertThat(progress(last).get("state")).isEqualTo("COMPLETED");
    }

    @Test
    void FR015_completionUsesTheLargestImportedStageRatherThanHardcodingEight() throws Exception {
        PronStage last = stage(3, 10);
        seed(owner, last, null, "AVAILABLE");

        assertThat(complete(3, 8, 0, 0).path("extra").path("completedAllStages").asBoolean()).isTrue();
        assertThat(progressCount(owner)).isEqualTo(1);
    }

    @Test
    void FR016_AT18_failedAttemptNeedsTenPercentAndDoesNotOpenTheSuccessor() throws Exception {
        stage(1, 10);
        PronStage second = stage(2, 10);

        JsonNode extra = complete(1, 7, 0, 0).path("extra");

        assertThat(extra.path("passed").asBoolean()).isFalse();
        assertThat(extra.path("needed").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(extra.path("nextStageIndex").asInt()).isEqualTo(2);
        assertThat(progressCount(owner, second.getId())).isZero();
    }

    @Test
    void FR012_AT19_progressFailureAfterBothWritesRollsBackFinishAndCanBeRetried() throws Exception {
        stage(1, 10);
        stage(2, 10);
        JsonNode session = start(owner, 1);
        long id = session.path("sessionId").asLong();
        answerAll(id, session, 9, 0, 0);
        PronStageHandler target = AopTestUtils.getUltimateTargetObject(handler);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            assertThat(progressCount(owner)).isEqualTo(2);
            throw new DataIntegrityViolationException("Simulated progress persistence failure");
        }).when(target).onFinish(any(FinishContext.class));

        var response = finish(owner, id);

        assertError(response, 500, "INTERNAL_ERROR");
        assertThat(response.getContentAsString()).doesNotContain("Simulated", "persistence", "Exception");
        assertThat(attempts.findById(id).orElseThrow().getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(attempts.findById(id).orElseThrow().getSubmittedAt()).isNull();
        assertThat(progressCount(owner)).isZero();
        assertThat(eventCount(owner)).isZero();
        assertThat(itemCount(id)).isEqualTo(10);
        doCallRealMethod().when(target).onFinish(any(FinishContext.class));
        assertThat(result(finish(owner, id)).path("extra").path("unlockedNextStage").asBoolean()).isTrue();
        assertThat(eventCount(owner)).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"3,0,false", "4,0,true", "0,3,true", "0,2,false"})
    void FR011_AT20_audioSuspicionIsStrictAndIsOrCombinedWithTheEngineFlag(
            int audioFailed, int fast, boolean suspicious) throws Exception {
        stage(1, 10);
        stage(2, 10);

        JsonNode finish = complete(1, 10 - audioFailed, audioFailed, fast);

        assertThat(finish.path("suspicious").asBoolean()).isEqualTo(suspicious);
        assertThat(attempts.findById(finish.path("sessionId").asLong()).orElseThrow().isSuspicious()).isEqualTo(suspicious);
    }

    @Test
    void FR011_noScoreWithoutPriorProgressReturnsAllNullFieldsAndWritesNoProgress() throws Exception {
        stage(1, 10);
        stage(2, 10);

        JsonNode finish = complete(1, 0, 10, 0);
        JsonNode extra = finish.path("extra");

        assertNoScore(extra);
        assertThat(extra.has("bestAccuracy")).isTrue();
        assertThat(extra.path("bestAccuracy").isNull()).isTrue();
        assertThat(extra.path("nextStageIndex").asInt()).isEqualTo(2);
        assertThat(progressCount(owner)).isZero();
        assertThat(finish.path("suspicious").asBoolean()).isTrue();
    }

    @Test
    void FR011_noScorePreservesAllExistingProgressIncludingBestAndTimestamps() throws Exception {
        stage(1, 10);
        stage(2, 10);
        complete(1, 9, 0, 0);
        List<Map<String, Object>> before = allProgress();
        when(clock.instant()).thenReturn(NOW.plusSeconds(50));

        JsonNode extra = complete(1, 0, 10, 0).path("extra");

        assertNoScore(extra);
        assertThat(extra.path("bestAccuracy").decimalValue()).isEqualByComparingTo("90.00");
        assertThat(allProgress()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 7})
    void FR015_lastStageDoesNotCompleteAllStagesOnNoScoreOrAFailedRetry(int correct) throws Exception {
        PronStage last = stage(8, 10);
        seed(owner, last, null, "AVAILABLE");
        complete(8, 9, 0, 0);

        JsonNode extra = complete(8, correct, correct == 0 ? 10 : 0, 0).path("extra");

        assertThat(extra.path("passed").asBoolean()).isFalse();
        assertThat(extra.path("completedAllStages").asBoolean()).isFalse();
        assertThat(extra.path("bestAccuracy").decimalValue()).isEqualByComparingTo("90.00");
        assertThat(extra.path("nextStageIndex").isNull()).isTrue();
        assertThat(progress(last).get("state")).isEqualTo("COMPLETED");
    }

    @ParameterizedTest
    @CsvSource({"1,32,3.13,76.87", "2,3,66.67,13.33"})
    void FR011_roundingUsesScaleTwoHalfUpInPersistedProgress(int correct, int total, String accuracy, String needed) throws Exception {
        PronStage first = stage(1, total);

        JsonNode extra = complete(1, correct, 0, 0).path("extra");

        assertThat(extra.path("accuracy").decimalValue()).isEqualByComparingTo(accuracy);
        assertThat(extra.path("needed").decimalValue()).isEqualByComparingTo(needed);
        assertThat(progress(first).get("percent")).isEqualTo(new BigDecimal(accuracy));
    }

    @Test
    void FR012_exactCustomThresholdPassesAndUnrelatedProgressIsUntouched() throws Exception {
        PronStage first = stage(1, 40);
        first.setPassPercent(new BigDecimal("82.50"));
        stages.saveAndFlush(first);
        stage(2, 10);
        long other = createUser();
        seed(other, first, new BigDecimal("55.00"), "AVAILABLE");
        jdbc.update("INSERT INTO learning.user_progress (user_id, target_type, target_id, percent) VALUES (?, 'TOPIC', ?, ?)",
                owner, first.getId(), new BigDecimal("33.00"));
        List<Map<String, Object>> before = jdbc.queryForList("SELECT * FROM learning.user_progress "
                + "WHERE user_id = ? OR target_type = 'TOPIC' ORDER BY user_id, target_type, target_id", other);

        JsonNode extra = complete(1, 33, 0, 0).path("extra");

        assertThat(extra.path("accuracy").decimalValue()).isEqualByComparingTo("82.50");
        assertThat(extra.path("passed").asBoolean()).isTrue();
        assertThat(extra.path("needed").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForList("SELECT * FROM learning.user_progress WHERE user_id = ? OR target_type = 'TOPIC' "
                + "ORDER BY user_id, target_type, target_id", other)).isEqualTo(before);
    }

    @Test
    void FR004_AT16_passingBeforeTheNextStageIsImportedMakesItAvailableAfterImport() throws Exception {
        stage(1, 10);
        JsonNode initial = complete(1, 9, 0, 0);
        assertThat(initial.path("extra").path("nextStageIndex").isNull()).isTrue();

        PronStage second = stage(2, 10);

        assertThat(list().get(1).path("state").asText()).isEqualTo("AVAILABLE");
        assertThat(progressCount(owner, second.getId())).isZero();
    }

    @Test
    void FR004_AT17_increasingTheThresholdAfterUnlockNeverRelocksTheNextStage() throws Exception {
        PronStage first = stage(1, 10);
        stage(2, 10);
        complete(1, 9, 0, 0);
        first.setPassPercent(new BigDecimal("95.00"));
        stages.saveAndFlush(first);

        JsonNode summary = list();

        assertThat(summary.get(0).path("state").asText()).isEqualTo("AVAILABLE");
        assertThat(summary.get(1).path("state").asText()).isEqualTo("AVAILABLE");
    }

    @Test
    void FR010_finishPreservesOwnershipRoleAndCookieCsrfChecks() throws Exception {
        stage(1, 1);
        JsonNode session = start(owner, 1);
        long id = session.path("sessionId").asLong();
        answerAll(id, session, 1, 0, 0);
        long other = createUser();
        assertError(finish(other, id), 403, "ATTEMPT_NOT_OWNED");
        assertError(finish(other, Long.MAX_VALUE), 403, "ATTEMPT_NOT_OWNED");
        mvc.perform(post("/api/practice/sessions/{id}/finish", id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/practice/sessions/{id}/finish", id).header("Authorization", bearer(owner, "ADMIN")))
                .andExpect(status().isForbidden());
        Cookie access = new Cookie("access_token", token(owner, "LEARNER"));
        assertError(mvc.perform(post("/api/practice/sessions/{id}/finish", id).cookie(access))
                .andReturn().getResponse(), 403, "CSRF_TOKEN_INVALID");
        Cookie csrf = mvc.perform(get("/api/auth/csrf").cookie(access)).andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        mvc.perform(post("/api/practice/sessions/{id}/finish", id).cookie(access, csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())).andExpect(status().isOk());
        assertThat(progressCount(other)).isZero();
    }

    @Test
    void FR016_NFRP02_finishIncludingSuccessorInsertHasLocalP95BelowThreeHundredMilliseconds() throws Exception {
        stage(1, 10);
        PronStage second = stage(2, 10);
        complete(1, 10, 0, 0);
        List<Long> durations = new ArrayList<>();
        for (int sample = 0; sample < 20; sample++) {
            jdbc.update("DELETE FROM learning.user_progress WHERE user_id = ? AND target_type = 'PRON_STAGE' AND target_id = ?",
                    owner, second.getId());
            JsonNode session = start(owner, 1);
            long id = session.path("sessionId").asLong();
            answerAll(id, session, 10, 0, 0);
            long started = System.nanoTime();
            var response = finish(owner, id);
            durations.add(System.nanoTime() - started);
            assertThat(result(response).path("extra").path("unlockedNextStage").asBoolean()).isTrue();
        }
        durations.sort(Long::compareTo);
        long p95 = durations.get(18);
        System.out.println("FR016_NFRP02 finish p95Ms=" + BigDecimal.valueOf(p95, 6).toPlainString());
        assertThat(p95).isLessThan(Duration.ofMillis(300).toNanos());
    }

    private PronStage stage(int index, int total) {
        PronStage stage = new PronStage((short) index, "Tầng " + index);
        stage.setUnits(units(total));
        return stages.saveAndFlush(stage);
    }

    private List<PronUnit> units(int total) {
        return IntStream.rangeClosed(1, total).mapToObj(no -> new PronUnit("U" + no,
                "https://example.test/" + no + ".mp3", "Chọn âm " + no,
                List.of(new PronUnit.Option("A", "z" + no), new PronUnit.Option("B", "zh" + no),
                        new PronUnit.Option("C", "s" + no), new PronUnit.Option("D", "sh" + no)),
                "C", "Giải thích tự soạn " + no)).toList();
    }

    private JsonNode start(long userId, int stageIndex) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/pronunciation/stages/{stageIndex}/sessions", stageIndex)
                        .header("Authorization", bearer(userId, "LEARNER"))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode complete(int stageIndex, int correct, int audioFailed, int fast) throws Exception {
        JsonNode session = start(owner, stageIndex);
        long id = session.path("sessionId").asLong();
        answerAll(id, session, correct, audioFailed, fast);
        return result(finish(owner, id));
    }

    private void answerAll(long id, JsonNode session, int correct, int audioFailed, int fast) throws Exception {
        int total = session.path("items").size();
        for (int index = 0; index < total; index++) {
            answer(owner, id, session.path("items").get(index), index < correct,
                    index >= total - audioFailed, index < fast ? 1 : 1000);
        }
    }

    private void answer(long userId, long id, JsonNode item, boolean correct, boolean audioFailed, int duration) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("no", item.path("no").asInt());
        body.put("durationMs", duration);
        body.put("replayCount", 1);
        if (audioFailed) {
            body.put("audioFailed", true);
        } else {
            String expected = "s" + item.path("prompt").path("text").asText().substring("Chọn âm ".length());
            for (JsonNode option : item.path("options")) {
                if (option.path("text").asText().equals(expected) == correct) {
                    body.put("answer", option.path("key").asText());
                    break;
                }
            }
        }
        mvc.perform(post("/api/practice/sessions/{id}/answers", id).header("Authorization", bearer(userId, "LEARNER"))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))).andExpect(status().isOk());
    }

    private MockHttpServletResponse finish(long userId, long id) throws Exception {
        return mvc.perform(post("/api/practice/sessions/{id}/finish", id).header("Authorization", bearer(userId, "LEARNER")))
                .andReturn().getResponse();
    }

    private JsonNode result(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(200);
        return mapper.readTree(response.getContentAsString());
    }

    private JsonNode list() throws Exception {
        return mapper.readTree(mvc.perform(get("/api/pronunciation/stages").header("Authorization", bearer(owner, "LEARNER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void seed(long userId, PronStage stage, BigDecimal accuracy, String state) {
        jdbc.update("INSERT INTO learning.user_progress (user_id, target_type, target_id, percent, state, unlocked_at) "
                        + "VALUES (?, 'PRON_STAGE', ?, ?, ?, ?)", userId, stage.getId(), accuracy, state,
                OffsetDateTime.ofInstant(NOW.minusSeconds(3600), ZoneOffset.UTC));
    }

    private Map<String, Object> progress(PronStage stage) {
        return jdbc.queryForMap("SELECT * FROM learning.user_progress WHERE user_id = ? AND target_type = 'PRON_STAGE' AND target_id = ?",
                owner, stage.getId());
    }

    private List<Map<String, Object>> allProgress() {
        return jdbc.queryForList("SELECT * FROM learning.user_progress WHERE user_id = ? ORDER BY target_type, target_id", owner);
    }

    private Instant unlockedAt(PronStage stage) {
        return jdbc.queryForObject("SELECT unlocked_at FROM learning.user_progress WHERE user_id = ? "
                + "AND target_type = 'PRON_STAGE' AND target_id = ?", OffsetDateTime.class, owner, stage.getId()).toInstant();
    }

    private int progressCount(long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM learning.user_progress WHERE user_id = ?", Integer.class, userId);
    }

    private int progressCount(long userId, long stageId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM learning.user_progress WHERE user_id = ? "
                + "AND target_type = 'PRON_STAGE' AND target_id = ?", Integer.class, userId, stageId);
    }

    private int itemCount(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM learning.attempt_items WHERE attempt_id = ?", Integer.class, id);
    }

    private int eventCount(long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM learning.study_events WHERE user_id = ?", Integer.class, userId);
    }

    private void assertNoScore(JsonNode extra) {
        assertThat(extra.has("accuracy")).isTrue();
        assertThat(extra.path("accuracy").isNull()).isTrue();
        assertThat(extra.has("needed")).isTrue();
        assertThat(extra.path("needed").isNull()).isTrue();
        assertThat(extra.path("passed").asBoolean()).isFalse();
        assertThat(extra.path("unlockedNextStage").asBoolean()).isFalse();
        assertThat(extra.path("completedAllStages").asBoolean()).isFalse();
        assertThat(extra.path("noScore").asBoolean()).isTrue();
    }

    private void assertError(MockHttpServletResponse response, int status, String code) throws Exception {
        assertThat(response.getStatus()).isEqualTo(status);
        JsonNode error = mapper.readTree(response.getContentAsString());
        assertThat(error.path("error_code").asText()).isEqualTo(code);
        assertThat(error.path("request_id").asText()).isNotBlank();
        assertThat(error.path("message").asText()).isNotBlank();
        assertThat(error.toString()).doesNotContain("stackTrace", "\"trace\"", "@example.test", "password");
    }

    private String bearer(long userId, String role) {
        return "Bearer " + token(userId, role);
    }

    private String token(long userId, String role) {
        return jwt.issueAccessToken(userId, List.of(role), false, UUID.randomUUID());
    }

    private long createUser() throws Exception {
        String password = "Aa1" + UUID.randomUUID();
        return mapper.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.test",
                                "password", password, "confirmPassword", password,
                                "displayName", "Pronunciation finish test", "acceptTerms", true))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("userId").asLong();
    }
}
