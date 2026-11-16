package com.cnhsk.learning.aigen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cnhsk.learning.ai.AiUnavailableException;
import com.cnhsk.learning.ai.ClaudeClient;
import com.cnhsk.learning.ai.StubClaudeClient;
import com.cnhsk.shared.settings.SettingKey;
import com.cnhsk.shared.settings.SettingsService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=3")
@ExtendWith(OutputCaptureExtension.class)
class AiGenerationServiceIntegrationTest extends AbstractIntegrationTest {
    private static final Instant NOW = WeakKnowledgeSelectorIntegrationTest.NOW;
    private static final String TOKEN_KEY = "ai:tokens:202611";
    @Autowired AiGenerationService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean AiQuestionValidator validator;
    @Autowired StringRedisTemplate redis;
    @MockitoSpyBean StubClaudeClient provider;
    @MockitoSpyBean AiGenerateConfig config;
    @MockitoSpyBean WeakKnowledgeSelector selector;
    @MockitoSpyBean AiGenerationStore store;
    @MockitoBean SettingsService settings;
    @MockitoBean Clock clock;
    WeakKnowledgeSelectorIntegrationTest.Fixtures fixtures;
    long admin;
    int calls;

    @BeforeEach
    void prepare() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.millis()).thenReturn(NOW.toEpochMilli());
        fixtures = new WeakKnowledgeSelectorIntegrationTest.Fixtures(jdbc);
        admin = fixtures.user();
        jdbc.update("INSERT INTO auth.user_roles(user_id,role_code) VALUES (?,'ADMIN')", admin);
        doReturn(Math.toIntExact(admin)).when(config).systemUserId();
        doReturn(5).when(config).batchSize();
        doReturn(3).when(config).maxBatchesPerRun();
        doReturn(10).when(config).minBank();
        doReturn(14).when(config).windowDays();
        doReturn(20).when(config).minWrong();
        doReturn("fixture-model").when(config).getGenerateModel();
        doReturn(Duration.ofSeconds(60)).when(config).getTimeout();
        when(settings.getInt(SettingKey.AI_MONTHLY_TOKEN_BUDGET)).thenReturn(100000);
        redis.delete(TOKEN_KEY);
        calls = provider.callCount();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return invocation.callRealMethod();
        }).when(provider).complete(anyString(), anyList(), anyList(), anyInt(), any());
    }

    @AfterEach
    void cleanupOwnedRows() {
        if (fixtures != null) {
            for (long user : fixtures.users) {
                List<Long> generated = jdbc.queryForList("SELECT q.id FROM learning.questions q "
                        + "JOIN learning.service_requests r ON r.id=q.created_by_request_id WHERE r.user_id=?", Long.class, user);
                for (long id : generated) {
                    jdbc.update("DELETE FROM learning.question_knowledge_points WHERE question_id=?", id);
                    jdbc.update("DELETE FROM learning.questions WHERE id=?", id);
                }
                jdbc.update("DELETE FROM auth.user_roles WHERE user_id=?", user);
            }
            fixtures.close();
        }
        redis.delete(TOKEN_KEY);
        assertThat(config.getCron()).isEqualTo("-");
    }

    @Test
    void fiveDraftsAndRequestCompleteAtomicallyWithSnakeCaseOptionsAndSafeCounters(CapturedOutput output) throws Exception {
        long node = selectOne();
        String canary = "private-response-" + UUID.randomUUID();
        provider.enqueue(new ClaudeClient.Completion(response(canary, 5), 111, 222));

        var result = service.runOnce();

        assertThat(result.outcome()).isEqualTo(AiGenerationService.Outcome.EXECUTED);
        assertThat(result.candidates()).isEqualTo(1);
        assertThat(result.batches()).isEqualTo(1);
        assertThat(result.completed()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(result.questions()).isEqualTo(5);
        assertThat(result.inputTokens()).isEqualTo(111);
        assertThat(result.outputTokens()).isEqualTo(222);
        assertThat(provider.callCount() - calls).isEqualTo(1);
        Map<String, Object> request = onlyRequest();
        assertThat(request.get("status")).isEqualTo("COMPLETED");
        assertThat(request.get("kind")).isEqualTo("AI_GENERATE");
        assertThat(request.get("user_id")).isEqualTo(admin);
        assertThat(((Timestamp) request.get("completed_at")).toInstant()).isEqualTo(NOW);
        JsonNode payload = mapper.readTree(request.get("payload").toString());
        assertThat(payload.path("kid").asText()).isEqualTo(fixtures.kid(node));
        assertThat(payload.path("knowledgePointId").asLong()).isEqualTo(node);
        assertThat(payload.path("model").asText()).isEqualTo("fixture-model");
        assertThat(request.get("result")).isNotNull();
        var drafts = jdbc.queryForList("SELECT * FROM learning.questions WHERE created_by_request_id=? ORDER BY id", request.get("id"));
        assertThat(drafts).hasSize(5);
        for (var draft : drafts) {
            assertThat(draft.get("qtype")).isEqualTo("MULTIPLE_CHOICE");
            assertThat(draft.get("source")).isEqualTo("AI");
            assertThat(draft.get("status")).isEqualTo("PENDING_REVIEW");
            assertThat(draft.get("correct_key")).isEqualTo("A");
            assertThat(draft.get("hsk_level")).isEqualTo(1);
            assertThat(draft.get("difficulty")).isEqualTo(2);
            assertThat(draft.get("reusable")).isEqualTo(true);
            assertThat(draft.get("code")).isNull();
            JsonNode options = mapper.readTree(draft.get("options").toString());
            assertThat(options.get(0).has("is_correct")).isTrue();
            assertThat(options.get(0).has("isCorrect")).isFalse();
            assertThat(jdbc.queryForMap("SELECT knowledge_point_id,role FROM learning.question_knowledge_points WHERE question_id=?", draft.get("id")))
                    .containsEntry("knowledge_point_id", node).containsEntry("role", "PRIMARY");
        }
        assertThat(redis.opsForValue().get(TOKEN_KEY)).isEqualTo("333");
        assertThat(output.getAll()).contains("candidates=1", "batches=1", "completed=1", "questions=5")
                .doesNotContain(canary);
    }

    @Test
    void providerRunsOutsideAnAmbientCallerTransactionWithPoolThree() {
        selectOne();
        provider.enqueue(new ClaudeClient.Completion(response("ambient", 1), 1, 2));
        var result = new TransactionTemplate(transactions).execute(status -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            var run = service.runOnce();
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return run;
        });
        assertThat(result.questions()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{\"questions\":[]}", "{\"questions\":[null]}"})
    void malformedAndZeroAcceptedResponsesFailWithoutDraftsButStillCountCompletionTokens(String response) {
        selectOne();
        provider.enqueue(new ClaudeClient.Completion(response, 7, 9));
        var result = service.runOnce();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.questions()).isZero();
        assertThat(result.inputTokens()).isEqualTo(7);
        assertThat(result.outputTokens()).isEqualTo(9);
        assertFailed(response.equals("{") ? "INVALID_OUTPUT" : "NO_VALID_QUESTION", 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TIMEOUT", "UPSTREAM_ERROR", "NOT_CONFIGURED"})
    void providerFailureIncrementsRetryExactlyOnceAndReleasesGuard(String reason) {
        selectOne();
        provider.enqueueFailure(AiUnavailableException.Reason.valueOf(reason));
        var result = service.runOnce();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(provider.callCount() - calls).isEqualTo(1);
        assertFailed("PROVIDER_ERROR", 1);
        doReturn(List.of()).when(selector).selectForGeneration();
        assertThat(service.runOnce().outcome()).isEqualTo(AiGenerationService.Outcome.EXECUTED);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10})
    void exhaustedBudgetStopsBeforeProviderAndAllLaterCandidates(int budget) {
        selectNodes(2);
        when(settings.getInt(SettingKey.AI_MONTHLY_TOKEN_BUDGET)).thenReturn(budget);
        if (budget > 0) redis.opsForValue().set(TOKEN_KEY, Integer.toString(budget));
        assertThat(service.runOnce().failed()).isEqualTo(1);
        assertFailed("BUDGET_EXCEEDED", 0);
        assertThat(provider.callCount()).isEqualTo(calls);
        assertThat(requests()).hasSize(1);
    }

    @Test
    void budgetReachedBetweenBatchesStopsAfterTheCurrentFailedClaim() {
        selectNodes(3);
        when(settings.getInt(SettingKey.AI_MONTHLY_TOKEN_BUDGET)).thenReturn(10);
        provider.enqueue(new ClaudeClient.Completion(response("first", 1), 6, 4));
        var result = service.runOnce();
        assertThat(result.batches()).isEqualTo(2);
        assertThat(result.completed()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.questions()).isEqualTo(1);
        assertThat(provider.callCount() - calls).isEqualTo(1);
        assertThat(requests()).extracting(row -> row.get("status")).containsExactly("COMPLETED", "FAILED");
    }

    @Test
    void multipleCandidatesRunSequentiallyOnlyUpToMaximum() {
        selectNodes(3);
        doReturn(2).when(config).maxBatchesPerRun();
        provider.enqueue(new ClaudeClient.Completion(response("first", 1), 1, 2));
        provider.enqueue(new ClaudeClient.Completion(response("second", 1), 3, 4));
        var result = service.runOnce();
        assertThat(result.batches()).isEqualTo(2);
        assertThat(result.completed()).isEqualTo(2);
        assertThat(result.questions()).isEqualTo(2);
        assertThat(result.inputTokens()).isEqualTo(4);
        assertThat(result.outputTokens()).isEqualTo(6);
        assertThat(provider.callCount() - calls).isEqualTo(2);
    }

    @Test
    void pendingRequestForSystemUserStopsAsBusyWithoutProvider() {
        long node = selectOne();
        fixtures.run(admin, "AI_GENERATE", "PENDING", fixtures.payload(node), NOW);
        assertThat(service.runOnce().outcome()).isEqualTo(AiGenerationService.Outcome.BUSY);
        assertThat(provider.callCount()).isEqualTo(calls);
        assertThat(requests()).hasSize(1);
    }

    @Test
    void changedEligibilityBeforeClaimSkipsProviderAndDoesNotCreateRequest() {
        long node = selectOne();
        jdbc.update("UPDATE learning.knowledge_points SET can_do=NULL WHERE id=?", node);
        assertThat(service.runOnce().batches()).isZero();
        assertThat(provider.callCount()).isEqualTo(calls);
        assertThat(requests()).isEmpty();
    }

    @Test
    void finalizeRevalidatesOriginalIndicesAfterConcurrentDuplicateAndKeepsSibling() throws Exception {
        long node = selectOne();
        var body = mapper.createObjectNode();
        body.putArray("questions").addNull().add(question("racing duplicate")).add(question("valid sibling"));
        provider.enqueue(new ClaudeClient.Completion(body.toString(), 8, 9));
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        doAnswer(invocation -> { entered.countDown(); await(release); return invocation.callRealMethod(); })
                .when(provider).complete(anyString(), anyList(), anyList(), anyInt(), any());
        var executor = Executors.newSingleThreadExecutor();
        try {
            var run = executor.submit(service::runOnce);
            await(entered);
            long duplicate = fixtures.question(node, "MANUAL", "ARCHIVED", null);
            jdbc.update("UPDATE learning.questions SET stem='racing duplicate' WHERE id=?", duplicate);
            release.countDown();
            assertThat(run.get(25, TimeUnit.SECONDS).questions()).isEqualTo(1);
            JsonNode result = mapper.readTree(onlyRequest().get("result").toString());
            assertThat(result.toString()).contains("DUPLICATE_STEM");
            assertThat(result.findValues("index")).extracting(JsonNode::asInt).contains(0, 1);
            assertThat(jdbc.queryForList("SELECT stem FROM learning.questions WHERE created_by_request_id=?", String.class,
                    onlyRequest().get("id"))).containsExactly("valid sibling");
        } finally {
            release.countDown(); executor.shutdownNow();
            assertThat(executor.awaitTermination(25, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void contextChangedWhileProviderRunsFailsInsteadOfSavingUnderOldPrompt() {
        long node = selectOne();
        provider.enqueue(new ClaudeClient.Completion(response("old context", 1), 1, 2));
        doAnswer(invocation -> {
            jdbc.update("UPDATE learning.knowledge_points SET code=overlay(code placing '2' from 3 for 1),hsk_level=2 WHERE id=?", node);
            return invocation.callRealMethod();
        }).when(provider).complete(anyString(), anyList(), anyList(), anyInt(), any());
        assertThat(service.runOnce().failed()).isEqualTo(1);
        assertFailed("CONTEXT_UNAVAILABLE", 0);
    }

    @Test
    void staleRequestCannotBeOverwrittenByFinalize() {
        selectOne();
        provider.enqueue(new ClaudeClient.Completion(response("stale outcome", 1), 1, 2));
        doAnswer(invocation -> {
            jdbc.update("UPDATE learning.service_requests SET status='FAILED',error_message='STALE',completed_at=? WHERE user_id=? AND status='RUNNING'",
                    Timestamp.from(NOW), admin);
            return invocation.callRealMethod();
        }).when(provider).complete(anyString(), anyList(), anyList(), anyInt(), any());
        assertThat(service.runOnce().questions()).isZero();
        assertFailed("STALE", 0);
    }

    @Test
    void failureAfterFirstQuestionAndLabelRollsBackEveryDraftAndUsesANewFailureTransaction(CapturedOutput output) {
        selectOne();
        provider.enqueue(new ClaudeClient.Completion(response("rollback-private-canary", 2), 3, 4));
        injectCommitFailure();

        var result = service.runOnce();

        assertThat(result.questions()).isZero();
        assertThat(result.completed()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.inputTokens()).isEqualTo(3);
        assertThat(result.outputTokens()).isEqualTo(4);
        assertFailed("DB_ERROR", 0);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.question_knowledge_points p JOIN learning.questions q "
                + "ON q.id=p.question_id WHERE q.created_by_request_id=?", Long.class, onlyRequest().get("id"))).isZero();
        assertThat(output.getAll()).doesNotContain("rollback-private-canary", "private persistence failure");
        assertThat(provider.callCount() - calls).isEqualTo(1);
    }

    @Test
    void unavailableFailureWriteLeavesRunningForTheNextStaleCleanup() {
        selectOne();
        provider.enqueue(new ClaudeClient.Completion(response("unavailable", 2), 3, 4));
        injectCommitFailure();
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("private database unavailable"))
                .when(store).fail(any(), eq("DB_ERROR"), eq(false));

        var result = service.runOnce();

        assertThat(result.questions()).isZero();
        assertThat(result.failed()).isZero();
        assertThat(onlyRequest().get("status")).isEqualTo("RUNNING");
        when(clock.instant()).thenReturn(NOW.plusSeconds(601));
        when(clock.millis()).thenReturn(NOW.plusSeconds(601).toEpochMilli());
        doCallRealMethod().when(selector).selectForGeneration();
        selector.selectForGeneration();
        assertFailed("STALE", 0);
    }

    @Test
    void lostGuardSessionStopsBeforeFinalizeWithoutReacquiringOrCallingLaterProviders() {
        selectNodes(2);
        provider.enqueue(new ClaudeClient.Completion(response("lost session", 1), 3, 4));
        doAnswer(invocation -> {
            Integer backend = jdbc.queryForObject("SELECT pid FROM pg_locks WHERE locktype='advisory' AND granted "
                    + "AND classid=CAST((hashtextextended('ai-generate:job',0) >> 32) & 4294967295 AS oid) "
                    + "AND objid=CAST(hashtextextended('ai-generate:job',0) & 4294967295 AS oid) AND objsubid=1", Integer.class);
            assertThat(jdbc.queryForObject("SELECT pg_terminate_backend(?)", Boolean.class, backend)).isTrue();
            return invocation.callRealMethod();
        }).when(provider).complete(anyString(), anyList(), anyList(), anyInt(), any());

        var result = service.runOnce();

        assertThat(result.batches()).isEqualTo(1);
        assertThat(result.questions()).isZero();
        assertThat(provider.callCount() - calls).isEqualTo(1);
        assertFailed("DB_ERROR", 0);
        verify(store, never()).finish(any(), any(), any(), any());
        assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
    }

    private void injectCommitFailure() {
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) {
                    throw new org.springframework.dao.DataIntegrityViolationException("private persistence failure");
                }
            });
            return result;
        }).when(validator).validate(anyString(), any());
    }

    private long selectOne() { return selectNodes(1).get(0); }
    private List<Long> selectNodes(int count) {
        List<Long> nodes = new ArrayList<>();
        List<WeakKnowledgeSelector.Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long node = fixtures.node(); nodes.add(node);
            candidates.add(new WeakKnowledgeSelector.Candidate(node, fixtures.kid(node), 20, 0, 0));
        }
        doReturn(candidates).when(selector).selectForGeneration();
        return nodes;
    }
    private List<Map<String, Object>> requests() {
        return jdbc.queryForList("SELECT * FROM learning.service_requests WHERE user_id=? ORDER BY id", admin);
    }
    private Map<String, Object> onlyRequest() { assertThat(requests()).hasSize(1); return requests().get(0); }
    private void assertFailed(String reason, int retry) {
        var request = onlyRequest();
        assertThat(request.get("status")).isEqualTo("FAILED");
        assertThat(request.get("error_message")).isEqualTo(reason);
        assertThat(request.get("retry_count")).isEqualTo(retry);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.questions WHERE created_by_request_id=?", Long.class, request.get("id"))).isZero();
    }
    private String response(String prefix, int count) {
        ObjectNode root = mapper.createObjectNode(); var questions = root.putArray("questions");
        for (int i = 0; i < count; i++) questions.add(question(prefix + " " + i));
        return root.toString();
    }
    private ObjectNode question(String stem) {
        ObjectNode question = mapper.createObjectNode().put("stem", stem).put("explanation", "Self-authored explanation").put("difficulty", 2);
        var options = question.putArray("options");
        for (String key : List.of("A", "B", "C", "D"))
            options.addObject().put("key", key).put("text", "Choice " + key).put("isCorrect", key.equals("A"));
        return question;
    }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(25, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }
}
