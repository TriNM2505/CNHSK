package com.cnhsk.learning.aigen;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.ai.AiUnavailableException;
import com.cnhsk.learning.ai.ClaudeClient;
import com.cnhsk.learning.ai.StubClaudeClient;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.AuthenticatedUser;
import com.cnhsk.shared.settings.SettingKey;
import com.cnhsk.shared.settings.SettingsService;
import com.cnhsk.support.AbstractIntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=3")
class AiRunTriggerIntegrationTest extends AbstractIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");
    private static final AtomicLong MILLIS = new AtomicLong(NOW.toEpochMilli());
    private static final String TOKEN_KEY = "ai:tokens:202611";
    private static final List<SettingKey> KEYS = List.of(SettingKey.AI_GENERATE_SYSTEM_USER_ID,
            SettingKey.AI_GENERATE_BATCH_SIZE, SettingKey.AI_GENERATE_MAX_BATCHES_PER_RUN, SettingKey.AI_MONTHLY_TOKEN_BUDGET,
            SettingKey.AI_GENERATE_WINDOW_DAYS, SettingKey.AI_GENERATE_MIN_WRONG, SettingKey.AI_GENERATE_DAILY_CAP);
    @Autowired AiGenerationService service;
    @Autowired AiGenerationGuard guard;
    @Autowired AiGenerateConfig config;
    @Autowired AuthApi auth;
    @Autowired SettingsService settings;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactions;
    @Autowired StringRedisTemplate redis;
    @MockitoBean Clock clock;
    @MockitoSpyBean StubClaudeClient provider;
    private WeakKnowledgeSelectorIntegrationTest.Fixtures fixtures;
    private long admin;
    private int calls;
    private final List<Map<String, Object>> previousSettings = new ArrayList<>();
    private final List<WorkExecutor> workers = new ArrayList<>();

    @BeforeEach
    void setup() {
        reset(provider); previousSettings.clear(); workers.clear();
        advanceClock();
        fixtures = new WeakKnowledgeSelectorIntegrationTest.Fixtures(jdbc);
        admin = fixtures.user();
        jdbc.update("INSERT INTO auth.user_roles(user_id,role_code) VALUES (?,'ADMIN')", admin);
        for (SettingKey key : KEYS) previousSettings.addAll(jdbc.queryForList("SELECT * FROM shared.app_settings WHERE key=?", key.key()));
        setting(SettingKey.AI_GENERATE_SYSTEM_USER_ID, Math.toIntExact(admin));
        setting(SettingKey.AI_GENERATE_BATCH_SIZE, 1);
        setting(SettingKey.AI_GENERATE_MAX_BATCHES_PER_RUN, 1);
        setting(SettingKey.AI_MONTHLY_TOKEN_BUDGET, 100000);
        // Fixture wrong answers are a day older than this clock; the cap must not see other classes' questions.
        setting(SettingKey.AI_GENERATE_WINDOW_DAYS, 14);
        setting(SettingKey.AI_GENERATE_MIN_WRONG, 20);
        setting(SettingKey.AI_GENERATE_DAILY_CAP, 500);
        advanceClock(); redis.delete(TOKEN_KEY); calls = provider.callCount();
        assertThat(settings).isNotNull();
        assertThat(config.systemUserId()).isEqualTo(admin);
        assertThat(config.getCron()).isEqualTo("-");
    }

    @AfterEach
    void cleanup() throws Exception {
        SecurityContextHolder.clearContext();
        for (WorkExecutor worker : workers) worker.close();
        reset(provider);
        assertThat(lockCount()).isZero();
        for (SettingKey key : KEYS) jdbc.update("DELETE FROM shared.app_settings WHERE key=?", key.key());
        for (Map<String, Object> row : previousSettings) jdbc.update(
                "INSERT INTO shared.app_settings(key,value,updated_by,updated_at) VALUES (?,?,?,?)",
                row.get("key"), row.get("value"), row.get("updated_by"), row.get("updated_at"));
        advanceClock();
        for (long user : fixtures.users) {
            for (long question : jdbc.queryForList("SELECT q.id FROM learning.questions q JOIN learning.service_requests r ON r.id=q.created_by_request_id WHERE r.user_id=?", Long.class, user)) {
                jdbc.update("DELETE FROM learning.question_knowledge_points WHERE question_id=?", question);
                jdbc.update("DELETE FROM learning.questions WHERE id=?", question);
            }
            jdbc.update("DELETE FROM auth.user_roles WHERE user_id=?", user);
        }
        fixtures.close(); redis.delete(TOKEN_KEY);
    }

    @Test
    void acceptedManualRunCallsProviderOnWorkerOutsideTransactionAndBlocksCronAndManualContenders() throws Exception {
        candidate(); completion();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        long caller = Thread.currentThread().getId(); AtomicLong workerThread = new AtomicLong();
        doAnswer(invocation -> {
            workerThread.set(Thread.currentThread().getId());
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            entered.countDown(); await(release); return invocation.callRealMethod();
        }).when(provider).complete(anyString(), anyList(), anyList(), anyInt(), any());
        WorkExecutor worker = worker(false);
        AiRunTrigger trigger = trigger(worker);
        try {
            asAdmin(trigger::trigger); await(entered);
            assertThat(workerThread.get()).isNotEqualTo(caller);
            assertThat(worker.done.isDone()).isFalse();
            assertThat(lockCount()).isEqualTo(1);
            assertThat(new AiGenerationGuard(dataSource).tryAcquire()).isEmpty();
            assertThat(service.runOnce().outcome()).isEqualTo(AiGenerationService.Outcome.BUSY);
            error(() -> asAdmin(trigger::trigger), ErrorCode.AI_GENERATION_BUSY);
            assertThat(requests()).hasSize(1);
        } finally { release.countDown(); }
        worker.awaitDone();
        assertThat(provider.callCount() - calls).isEqualTo(1);
        assertThat(requests()).singleElement().satisfies(row -> assertThat(row.get("status")).isEqualTo("COMPLETED"));
        assertThat(lockCount()).isZero();
    }

    @Test
    void reservationAlreadyBlocksCronAndManualBeforeWorkerBeginsAndIsReleasedAfterCompletion() throws Exception {
        candidate(); completion(); WorkExecutor worker = worker(true); AiRunTrigger trigger = trigger(worker);
        asAdmin(trigger::trigger);
        assertThat(lockCount()).isEqualTo(1); assertThat(requests()).isEmpty();
        assertThat(provider.callCount()).isEqualTo(calls);
        assertThat(service.runOnce().outcome()).isEqualTo(AiGenerationService.Outcome.BUSY);
        error(() -> asAdmin(trigger::trigger), ErrorCode.AI_GENERATION_BUSY);
        worker.release.countDown(); worker.awaitDone();
        assertThat(lockCount()).isZero();
        assertThat(provider.callCount() - calls).isEqualTo(1);
        try (var next = guard.tryAcquire().orElseThrow()) { next.assertOwned(); }
    }

    @Test
    void cronHoldingProviderMakesManualBusyWithoutASecondProviderOrRequest() throws Exception {
        candidate(); completion(); CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        doAnswer(invocation -> { entered.countDown(); await(release); return invocation.callRealMethod(); })
                .when(provider).complete(anyString(), anyList(), anyList(), anyInt(), any());
        var cron = Executors.newSingleThreadExecutor();
        try {
            var result = cron.submit(service::runOnce); await(entered);
            error(() -> asAdmin(trigger(worker(false))::trigger), ErrorCode.AI_GENERATION_BUSY);
            assertThat(requests()).hasSize(1); assertThat(lockCount()).isEqualTo(1);
            release.countDown(); assertThat(result.get(25, TimeUnit.SECONDS).questions()).isEqualTo(1);
            assertThat(provider.callCount() - calls).isEqualTo(1);
        } finally { release.countDown(); cron.shutdown(); assertThat(cron.awaitTermination(25, TimeUnit.SECONDS)).isTrue(); }
        assertThat(lockCount()).isZero();
    }

    @Test
    void revokedSystemAdminAfterAcceptanceStopsBeforeSelectionClaimOrProviderAndReleasesLease() throws Exception {
        candidate(); WorkExecutor worker = worker(true); AiRunTrigger trigger = trigger(worker);
        asAdmin(trigger::trigger);
        jdbc.update("DELETE FROM auth.user_roles WHERE user_id=? AND role_code='ADMIN'", admin);
        worker.release.countDown(); worker.awaitDone();
        assertThat(requests()).isEmpty(); assertThat(provider.callCount()).isEqualTo(calls);
        assertThat(lockCount()).isZero();
        error(() -> asAdmin(trigger::trigger), ErrorCode.AI_GENERATION_DISABLED);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void providerFailureOrRevocationDuringProviderFinalizesFailureAndAlwaysReleasesLease(boolean revoke) throws Exception {
        candidate(); WorkExecutor worker = worker(false);
        if (revoke) {
            completion();
            doAnswer(invocation -> {
                jdbc.update("DELETE FROM auth.user_roles WHERE user_id=? AND role_code='ADMIN'", admin);
                return invocation.callRealMethod();
            }).when(provider).complete(anyString(), anyList(), anyList(), anyInt(), any());
        } else provider.enqueueFailure(AiUnavailableException.Reason.TIMEOUT);
        asAdmin(trigger(worker)::trigger); worker.awaitDone();
        assertThat(requests()).singleElement().satisfies(row -> {
            assertThat(row.get("status")).isEqualTo("FAILED");
            assertThat(row.get("error_message")).isEqualTo(revoke ? "CONTEXT_UNAVAILABLE" : "PROVIDER_ERROR");
        });
        assertThat(provider.callCount() - calls).isEqualTo(1); assertThat(lockCount()).isZero();
        try (var next = new AiGenerationGuard(dataSource).tryAcquire().orElseThrow()) { next.assertOwned(); }
    }

    @Test
    void executorRejectionReleasesTheRealLeaseAndReturns500BeforeCreatingAnyRequest() {
        candidate();
        Executor rejected = task -> { throw new RejectedExecutionException("Synthetic full executor"); };
        error(() -> asAdmin(trigger(rejected)::trigger), ErrorCode.INTERNAL_ERROR);
        assertThat(requests()).isEmpty(); assertThat(provider.callCount()).isEqualTo(calls); assertThat(lockCount()).isZero();
        try (var next = guard.tryAcquire().orElseThrow()) { next.assertOwned(); }
    }

    @Test
    void reservedSpringEntrySuspendsAnAmbientCallerTransactionWithPoolThreeAndRetainsCallerLeaseOwnership() {
        candidate(); completion();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return invocation.callRealMethod();
        }).when(provider).complete(anyString(), anyList(), anyList(), anyInt(), any());
        try (var lease = guard.tryAcquire().orElseThrow()) {
            var snapshot = new AiGenerationService.Settings(admin, config.getGenerateModel(), config.batchSize(),
                    config.maxBatchesPerRun(), config.minBank(), config.getTimeout(), config.dailyCap(),
                    config.semanticThreshold());
            var result = new TransactionTemplate(transactions).execute(status -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                var run = service.runReserved(lease, snapshot);
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                return run;
            });
            assertThat(result.questions()).isEqualTo(1);
            lease.assertOwned(); assertThat(lockCount()).isEqualTo(1);
        }
        assertThat(lockCount()).isZero();
    }

    private void candidate() {
        long node = fixtures.node(); fixtures.wrong(admin, node, 20);
    }

    @Test
    void managedHandoffUsesTheSharedRealGuardAndSeparateReservedSessionsWithoutProviderOverlap() {
        candidate(); completion(); completion();
        CountDownLatch firstClosed = new CountDownLatch(1), releaseTail = new CountDownLatch(1);
        CountDownLatch secondProvider = new CountDownLatch(1), releaseProvider = new CountDownLatch(1);
        var leases = new java.util.concurrent.CopyOnWriteArrayList<AiGenerationGuard.Lease>();
        var activeProviders = new java.util.concurrent.atomic.AtomicInteger();
        var providerEntries = new java.util.concurrent.atomic.AtomicInteger();
        AiGenerationGuard sharedGuard = mock(AiGenerationGuard.class);
        when(sharedGuard.tryAcquire()).thenAnswer(invocation -> guard.tryAcquire().map(real -> {
            AiGenerationGuard.Lease reserved = spy(real);
            leases.add(reserved);
            if (leases.size() == 1) doAnswer(close -> {
                close.callRealMethod();
                firstClosed.countDown(); await(releaseTail); return null;
            }).when(reserved).close();
            return reserved;
        }));
        AiGenerationService observedService = mock(AiGenerationService.class);
        doAnswer(invocation -> {
            AiGenerationGuard.Lease reserved = invocation.getArgument(0);
            assertThat(leases).contains(reserved); reserved.assertOwned();
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return service.runReserved(reserved, invocation.getArgument(1));
        }).when(observedService).runReserved(any(), any());
        doAnswer(invocation -> {
            assertThat(activeProviders.incrementAndGet()).isEqualTo(1);
            try {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                if (providerEntries.incrementAndGet() == 2) { secondProvider.countDown(); await(releaseProvider); }
                return invocation.callRealMethod();
            } finally { activeProviders.decrementAndGet(); }
        }).when(provider).complete(anyString(), anyList(), anyList(), anyInt(), any());
        AiRunTrigger managed = new AiRunTrigger(auth, config, sharedGuard, observedService);
        try {
            asAdmin(managed::trigger); await(firstClosed);
            assertThat(lockCount()).isZero();
            candidate(); advanceClock();
            asAdmin(managed::trigger); await(secondProvider);
            assertThat(leases).hasSize(2); assertThat(leases.get(1)).isNotSameAs(leases.get(0));
            leases.get(1).assertOwned(); assertThat(lockCount()).isEqualTo(1);
            error(() -> asAdmin(managed::trigger), ErrorCode.AI_GENERATION_BUSY);
            assertThat(service.runOnce().outcome()).isEqualTo(AiGenerationService.Outcome.BUSY);
            verify(observedService, times(2)).runReserved(any(), any());
        } finally { releaseProvider.countDown(); releaseTail.countDown(); managed.shutdown(); }
        assertThat(provider.callCount() - calls).isEqualTo(2);
        assertThat(lockCount()).isZero();
        verify(leases.get(0), times(1)).close(); verify(leases.get(1), times(1)).close();
    }
    private void completion() {
        provider.enqueue(new ClaudeClient.Completion("{\"questions\":[{\"stem\":\"Self-authored asynchronous example\",\"explanation\":\"Self-authored explanation\",\"difficulty\":2,"
                + "\"options\":[{\"key\":\"A\",\"text\":\"Choice A\",\"isCorrect\":true},{\"key\":\"B\",\"text\":\"Choice B\",\"isCorrect\":false},"
                + "{\"key\":\"C\",\"text\":\"Choice C\",\"isCorrect\":false},{\"key\":\"D\",\"text\":\"Choice D\",\"isCorrect\":false}]}]}", 3, 5));
    }
    private AiRunTrigger trigger(Executor executor) { return new AiRunTrigger(auth, config, guard, service, executor); }
    private WorkExecutor worker(boolean paused) { WorkExecutor worker = new WorkExecutor(paused); workers.add(worker); return worker; }
    private void setting(SettingKey key, int value) {
        jdbc.update("INSERT INTO shared.app_settings(key,value,updated_by) VALUES (?,?,?) ON CONFLICT(key) DO UPDATE SET value=EXCLUDED.value,updated_by=EXCLUDED.updated_by",
                key.key(), Integer.toString(value), admin);
    }
    private void advanceClock() {
        long millis = MILLIS.addAndGet(31000); when(clock.millis()).thenReturn(millis); when(clock.instant()).thenReturn(Instant.ofEpochMilli(millis));
    }
    private List<Map<String, Object>> requests() { return jdbc.queryForList("SELECT * FROM learning.service_requests WHERE user_id=? ORDER BY id", admin); }
    private long lockCount() {
        return jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE locktype='advisory' AND granted "
                + "AND classid=CAST((hashtextextended('ai-generate:job',0) >> 32) & 4294967295 AS oid) "
                + "AND objid=CAST(hashtextextended('ai-generate:job',0) & 4294967295 AS oid) AND objsubid=1", Long.class);
    }
    private void asAdmin(Runnable action) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(admin, Set.of("ADMIN"), true, UUID.randomUUID().toString(), UUID.randomUUID(), NOW), null, List.of()));
        try { action.run(); } finally { SecurityContextHolder.clearContext(); }
    }
    private static void error(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, failure -> assertThat(failure.getCode()).isEqualTo(code));
    }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
    }
    private static final class WorkExecutor implements Executor, AutoCloseable {
        final CountDownLatch release;
        final CompletableFuture<Void> done = new CompletableFuture<>();
        final ThreadPoolExecutor executor = new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS, new SynchronousQueue<>(), new ThreadPoolExecutor.AbortPolicy());
        private boolean accepted;
        WorkExecutor(boolean paused) { release = new CountDownLatch(paused ? 1 : 0); }
        @Override public void execute(Runnable task) {
            executor.execute(() -> {
                try { await(release); task.run(); done.complete(null); }
                catch (Throwable failure) { done.completeExceptionally(failure); }
            }); accepted = true;
        }
        void awaitDone() throws Exception { done.get(25, TimeUnit.SECONDS); }
        @Override public void close() throws Exception {
            release.countDown(); executor.shutdown();
            assertThat(executor.awaitTermination(25, TimeUnit.SECONDS)).isTrue();
            if (accepted) awaitDone();
        }
    }
}
