package com.cnhsk.learning.grading;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.cnhsk.learning.api.Consumption;
import com.cnhsk.learning.api.EntitlementApi;
import com.cnhsk.learning.grading.web.GradingRequestViews.Criteria;
import com.cnhsk.learning.grading.web.GradingRequestWrite;
import com.cnhsk.learning.grading.web.GradingResultWrite;
import com.cnhsk.shared.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Own infrastructure: a global expiry scan must never process another suite's fixtures. */
@SpringBootTest
@ActiveProfiles("test")
@Import(GradingExpiryJobIntegrationTest.TimeConfiguration.class)
class GradingExpiryJobIntegrationTest {
    private static final Instant NOW = Instant.parse("2035-11-15T01:00:00Z");
    private static final String HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("grading_expiry_test").withUsername("grading_expiry_owner").withPassword(UUID.randomUUID().toString());
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    static { POSTGRES.start(); REDIS.start(); }

    @DynamicPropertySource static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired private GradingExpiryJob job;
    @Autowired private GradingRequestService requests;
    @Autowired private TeacherGradingService teachers;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private TestClock clock;
    @MockitoSpyBean private GradingRepository repository;
    @MockitoSpyBean private EntitlementApi entitlement;
    @MockitoSpyBean private StringRedisTemplate redis;
    @MockitoSpyBean private GradingConfig config;
    private GradingRepository repositoryTarget;
    private EntitlementApi entitlementTarget;
    private GradingConfig configTarget;
    private final List<Long> users = new ArrayList<>();
    private int essayNumber;

    @BeforeEach void setup() {
        clock.set(NOW);
        repositoryTarget = AopTestUtils.getUltimateTargetObject(repository);
        entitlementTarget = AopTestUtils.getUltimateTargetObject(entitlement);
        configTarget = AopTestUtils.getUltimateTargetObject(config);
    }

    @AfterEach void cleanup() {
        reset(repositoryTarget, entitlementTarget, configTarget, redis);
        for (long id : users) {
            var keys = redis.keys("billing:*:" + id + ":*"); if (keys != null && !keys.isEmpty()) redis.delete(keys);
            jdbc.update("DELETE FROM learning.credit_transactions WHERE user_id=?", id);
        }
        for (long id : users) jdbc.update("DELETE FROM learning.service_requests WHERE user_id=?", id);
        for (long id : users) jdbc.update("DELETE FROM auth.users WHERE id=?", id);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void AT13_pendingAndAssignedExpireAndRefundOnlyOnceAfterThreeRuns(boolean credit) {
        long owner = user(), teacher = user();
        long pending = pending(owner, credit); long assigned = pending(owner, credit);
        teachers.claim(teacher, assigned);
        var assignment = repository.find(assigned).orElseThrow();
        deadline(pending, NOW.minusSeconds(2)); deadline(assigned, NOW.minusSeconds(1));
        for (int index = 0; index < 3; index++) job.run();
        for (long id : List.of(pending, assigned)) {
            var row = repository.find(id).orElseThrow(); assertThat(row.status()).isEqualTo("EXPIRED");
            assertThat(row.completedAt()).isEqualTo(NOW); assertThat(row.payout()).isNull();
        }
        var expired = repository.find(assigned).orElseThrow();
        assertThat(expired.assigneeId()).isEqualTo(teacher); assertThat(expired.assignedAt()).isEqualTo(assignment.assignedAt());
        assertThat(refunds(owner)).isEqualTo(credit ? 2 : 0); assertThat(balance(owner)).isEqualTo(credit ? 2 : 0);
        assertThat(used(owner)).isEqualTo(credit ? 10 : 0);
        verify(entitlementTarget, times(2)).refund(eq(owner), any());
        assertThat(repository.countOverdue(NOW)).isZero();
    }

    @ParameterizedTest @CsvSource({"false,PENDING", "true,PENDING", "false,ASSIGNED", "true,ASSIGNED"})
    void AT15_failureAfterRealRefundRollsBackStatusMoneyAndQuotaMarkerThenCanRetry(boolean credit, String state) throws Exception {
        long owner = user(), teacher = user(), id = pending(owner, credit);
        if (state.equals("ASSIGNED")) teachers.claim(teacher, id);
        deadline(id, NOW.minusSeconds(1)); var before = repository.find(id).orElseThrow(); var receipt = receipt(id);
        doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("Simulated failure after real refund."); })
                .when(entitlementTarget).refund(owner, receipt);
        job.run();
        assertThat(repository.find(id).orElseThrow()).isEqualTo(before);
        assertThat(refunds(owner)).isZero(); assertThat(balance(owner)).isZero(); assertThat(used(owner)).isEqualTo(credit ? 10 : 1);
        assertThat(redis.hasKey("billing:refund:" + owner + ":" + receipt.idempotencyKey())).isFalse();
        reset(entitlementTarget); job.run(); job.run();
        assertThat(repository.find(id).orElseThrow().status()).isEqualTo("EXPIRED");
        assertThat(refunds(owner)).isEqualTo(credit ? 1 : 0); assertThat(balance(owner)).isEqualTo(credit ? 1 : 0);
        assertThat(used(owner)).isEqualTo(credit ? 10 : 0); verify(entitlementTarget).refund(owner, receipt);
    }

    @Test void AT15_badReceiptDoesNotPreventTheFollowingHealthyRequestFromCommitting() {
        long badOwner = user(), goodOwner = user(), bad = pending(badOwner, false), good = pending(goodOwner, false);
        deadline(bad, NOW.minusSeconds(2)); deadline(good, NOW.minusSeconds(1));
        jdbc.update("UPDATE learning.service_requests SET payload=payload-'consumption' WHERE id=?", bad);
        job.run(); assertThat(repository.find(bad).orElseThrow().status()).isEqualTo("PENDING");
        assertThat(repository.find(good).orElseThrow().status()).isEqualTo("EXPIRED");
        assertThat(used(badOwner)).isEqualTo(1); assertThat(used(goodOwner)).isZero();
        assertThat(repository.countOverdue(NOW)).isEqualTo(1);
    }

    @Test void FR017_oneTickProcessesAtMost100AndTheNextTickProcessesTheRemainingFive() {
        var ids = new ArrayList<Long>();
        for (int index = 0; index < 105; index++) {
            long id = pending(user(), false); deadline(id, NOW.minusSeconds(1)); ids.add(id);
        }
        assertThat(repository.countOverdue(NOW)).isEqualTo(105);
        job.run();
        assertThat(repository.countOverdue(NOW)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.service_requests WHERE kind='GRADING' AND status='EXPIRED'", Integer.class)).isEqualTo(100);
        assertThat(repository.find(ids.get(100)).orElseThrow().status()).isEqualTo("PENDING");
        job.run(); assertThat(repository.countOverdue(NOW)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.service_requests WHERE kind='GRADING' AND status='EXPIRED'", Integer.class)).isEqualTo(105);
        for (long owner : users) assertThat(used(owner)).isZero();
    }

    @Test void FR017_exactDeadlineFutureClosedAndOtherKindsAreNotExpiredOrCounted() {
        long owner = user();
        long exact = pending(owner, false), future = pending(owner, false), cancelled = pending(owner, false);
        deadline(exact, NOW); deadline(future, NOW.plusSeconds(1)); requests.cancel(owner, cancelled);
        jdbc.update("INSERT INTO learning.service_requests(user_id,kind,status,deadline_at) VALUES (?,'TRANSLATE','PENDING',?)", owner, Timestamp.from(NOW.minusSeconds(1)));
        assertThat(repository.countOverdue(NOW)).isZero(); job.run();
        assertThat(repository.find(exact).orElseThrow().status()).isEqualTo("PENDING");
        assertThat(repository.find(future).orElseThrow().status()).isEqualTo("PENDING");
        assertThat(repository.find(cancelled).orElseThrow().status()).isEqualTo("FAILED"); assertThat(used(owner)).isEqualTo(2);
    }

    @Test void Q_T05_01_outerRollbackDoesNotUndoTheCommittedPerRowTransaction() {
        long owner = user(), id = pending(owner, false); deadline(id, NOW.minusSeconds(1));
        new TransactionTemplate(manager).executeWithoutResult(status -> { job.run(); status.setRollbackOnly(); });
        assertThat(repository.find(id).orElseThrow().status()).isEqualTo("EXPIRED"); assertThat(used(owner)).isZero();
    }

    @Test void Q_T05_03_quotaRedisFailureIsIntentionallyNotObservableByTheJobButCreditRefundStillWorks() {
        long quotaOwner = user(), creditOwner = user(), quota = pending(quotaOwner, false), credit = pending(creditOwner, true);
        deadline(quota, NOW.minusSeconds(1)); deadline(credit, NOW.minusSeconds(1));
        doThrow(new RedisConnectionFailureException("Simulated Redis outage.")).when(redis).opsForValue();
        job.run();
        assertThat(repository.find(quota).orElseThrow().status()).isEqualTo("EXPIRED"); assertThat(used(quotaOwner)).isEqualTo(1);
        assertThat(repository.find(credit).orElseThrow().status()).isEqualTo("EXPIRED"); assertThat(refunds(creditOwner)).isEqualTo(1);
        assertThat(balance(creditOwner)).isEqualTo(1);
    }

    @Test void AT13_twoJobsWithTheSameSnapshotCannotRefundEitherSourceTwice() throws Exception {
        long firstOwner = user(), secondOwner = user();
        long first = pending(firstOwner, false), second = pending(secondOwner, true);
        deadline(first, NOW.minusSeconds(1)); deadline(second, NOW.minusSeconds(1));
        var scanned = new CountDownLatch(2); var proceed = new CountDownLatch(1);
        doAnswer(call -> {
            var result = call.callRealMethod(); scanned.countDown(); await(proceed); return result;
        }).when(repositoryTarget).findOverdue(any());
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(job::run); var b = executor.submit(job::run);
            assertThat(scanned.await(15, TimeUnit.SECONDS)).isTrue(); proceed.countDown();
            a.get(30, TimeUnit.SECONDS); b.get(30, TimeUnit.SECONDS);
        } finally { proceed.countDown(); executor.shutdownNow(); executor.awaitTermination(10, TimeUnit.SECONDS); }
        assertThat(used(firstOwner)).isZero(); assertThat(refunds(secondOwner)).isEqualTo(1); assertThat(balance(secondOwner)).isEqualTo(1);
        verify(entitlementTarget).refund(eq(firstOwner), any()); verify(entitlementTarget).refund(eq(secondOwner), any());
    }

    @Test void AT14_oneHundredOverlappingClaimAndExpiryAttemptsNeverAssignAndRefundTheSameRequest() throws Exception {
        long started = System.nanoTime();
        for (int iteration = 0; iteration < 100; iteration++) {
            clock.set(NOW); long owner = user(), teacher = user(), id = pending(owner, iteration % 2 == 0);
            deadline(id, NOW.plusSeconds(1));
            overlappingClaim(id, teacher, iteration % 4 < 2);
            var row = repository.find(id).orElseThrow(); boolean assigned = row.status().equals("ASSIGNED");
            assertThat(row.status()).isIn("ASSIGNED", "EXPIRED");
            assertThat(refunds(owner)).isEqualTo(!assigned && iteration % 2 == 0 ? 1 : 0);
            assertThat(used(owner)).isEqualTo(iteration % 2 == 0 ? 10 : assigned ? 1 : 0);
            assertThat(balance(owner)).isEqualTo(!assigned && iteration % 2 == 0 ? 1 : 0);
            reset(repositoryTarget);
        }
        System.out.println("AT-14 grading claim/expiry: 100 overlapping iterations, elapsed_ms=" + (System.nanoTime() - started) / 1_000_000);
    }

    private void overlappingClaim(long id, long teacher, boolean claimWins) throws Exception {
        var claimEntered = new CountDownLatch(1); var releaseClaim = new CountDownLatch(1);
        var expiryEntered = new CountDownLatch(1); var releaseExpiry = new CountDownLatch(1);
        doAnswer(call -> { claimEntered.countDown(); await(releaseClaim); return call.callRealMethod(); })
                .when(repositoryTarget).claim(eq(id), eq(teacher), any(), any());
        doAnswer(call -> { expiryEntered.countDown(); await(releaseExpiry); return call.callRealMethod(); })
                .when(repositoryTarget).expire(eq(id), eq("PENDING"), any());
        var executor = Executors.newFixedThreadPool(2);
        try {
            var claim = executor.submit(() -> {
                try { teachers.claim(teacher, id); return true; }
                catch (ApiException error) { assertThat(error.getCode().name()).isEqualTo("GRADING_ALREADY_CLAIMED"); return false; }
            });
            assertThat(claimEntered.await(15, TimeUnit.SECONDS)).isTrue(); clock.set(NOW.plusSeconds(2));
            var expiry = executor.submit(job::run); assertThat(expiryEntered.await(15, TimeUnit.SECONDS)).isTrue();
            if (claimWins) { releaseClaim.countDown(); assertThat(claim.get(20, TimeUnit.SECONDS)).isTrue(); releaseExpiry.countDown(); expiry.get(20, TimeUnit.SECONDS); }
            else { releaseExpiry.countDown(); expiry.get(20, TimeUnit.SECONDS); releaseClaim.countDown(); assertThat(claim.get(20, TimeUnit.SECONDS)).isFalse(); }
        } finally { releaseClaim.countDown(); releaseExpiry.countDown(); executor.shutdownNow(); executor.awaitTermination(10, TimeUnit.SECONDS); }
    }

    @ParameterizedTest @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void FR018_completionAndExpiryCannotBothPayAndRefund(boolean credit, boolean completeWins) throws Exception {
        long owner = user(), teacher = user(), id = pending(owner, credit); teachers.claim(teacher, id);
        doReturn(2).when(configTarget).getTeacherPayoutCredits(); deadline(id, NOW.plusSeconds(1));
        var completeEntered = new CountDownLatch(1); var releaseComplete = new CountDownLatch(1);
        var expiryEntered = new CountDownLatch(1); var releaseExpiry = new CountDownLatch(1);
        doAnswer(call -> { completeEntered.countDown(); await(releaseComplete); return call.callRealMethod(); })
                .when(repositoryTarget).complete(eq(id), eq(teacher), any(), anyInt(), anyString(), any(), anyInt());
        doAnswer(call -> { expiryEntered.countDown(); await(releaseExpiry); return call.callRealMethod(); })
                .when(repositoryTarget).expire(eq(id), eq("ASSIGNED"), any());
        var executor = Executors.newFixedThreadPool(2);
        try {
            var complete = executor.submit(() -> {
                try { teachers.complete(teacher, id, new GradingResultWrite(new Criteria(20, 20, 20, 20), "Nhận xét tự soạn.", List.of())); return true; }
                catch (ApiException error) { assertThat(error.getCode().name()).isEqualTo("GRADING_NOT_ASSIGNEE"); return false; }
            });
            assertThat(completeEntered.await(15, TimeUnit.SECONDS)).isTrue(); clock.set(NOW.plusSeconds(2));
            var expiry = executor.submit(job::run); assertThat(expiryEntered.await(15, TimeUnit.SECONDS)).isTrue();
            if (completeWins) { releaseComplete.countDown(); assertThat(complete.get(20, TimeUnit.SECONDS)).isTrue(); releaseExpiry.countDown(); expiry.get(20, TimeUnit.SECONDS); }
            else { releaseExpiry.countDown(); expiry.get(20, TimeUnit.SECONDS); releaseComplete.countDown(); assertThat(complete.get(20, TimeUnit.SECONDS)).isFalse(); }
        } finally { releaseComplete.countDown(); releaseExpiry.countDown(); executor.shutdownNow(); executor.awaitTermination(10, TimeUnit.SECONDS); }
        assertThat(repository.find(id).orElseThrow().status()).isEqualTo(completeWins ? "COMPLETED" : "EXPIRED");
        assertThat(balance(teacher)).isEqualTo(completeWins ? 2 : 0);
        assertThat(refunds(owner)).isEqualTo(!completeWins && credit ? 1 : 0);
        assertThat(used(owner)).isEqualTo(credit ? 10 : completeWins ? 1 : 0);
        assertThat(balance(owner)).isEqualTo(!completeWins && credit ? 1 : 0);
    }

    @ParameterizedTest @CsvSource({"false,false,false", "false,false,true", "false,true,false", "false,true,true",
            "true,false,false", "true,false,true", "true,true,false", "true,true,true"})
    void FR018_cancelOrExpiredReleaseAndJobRefundOnlyOnce(boolean credit, boolean release, boolean actorWins) throws Exception {
        long owner = user(), teacher = user(), id = pending(owner, credit);
        if (release) {
            teachers.claim(teacher, id);
            jdbc.update("UPDATE learning.service_requests SET created_at=?,payload=jsonb_set(payload,'{claimDeadline}',to_jsonb(CAST(? AS timestamptz))) WHERE id=?",
                    Timestamp.from(NOW.minusSeconds(49 * 3600)), Timestamp.from(NOW.minusSeconds(1)), id);
        }
        deadline(id, NOW.minusSeconds(1));
        var entered = new CountDownLatch(1); var proceed = new CountDownLatch(1);
        org.mockito.stubbing.Answer<Object> pause = call -> { entered.countDown(); await(proceed); return call.callRealMethod(); };
        if (actorWins) doAnswer(pause).when(repositoryTarget).expire(eq(id), eq(release ? "ASSIGNED" : "PENDING"), any());
        else if (release) doAnswer(pause).when(repositoryTarget).release(eq(id), eq(teacher), any());
        else doAnswer(pause).when(repositoryTarget).cancel(eq(id), eq(owner), any());
        var executor = Executors.newSingleThreadExecutor();
        try {
            var delayed = executor.submit(() -> {
                if (actorWins) { job.run(); return true; }
                return cancelOrRelease(owner, teacher, id, release);
            });
            assertThat(entered.await(15, TimeUnit.SECONDS)).isTrue();
            if (actorWins) assertThat(cancelOrRelease(owner, teacher, id, release)).isTrue(); else job.run();
            proceed.countDown(); assertThat(delayed.get(20, TimeUnit.SECONDS)).isEqualTo(actorWins);
        } finally { proceed.countDown(); executor.shutdownNow(); executor.awaitTermination(10, TimeUnit.SECONDS); }
        assertThat(repository.find(id).orElseThrow().status()).isEqualTo(!release && actorWins ? "FAILED" : "EXPIRED");
        assertThat(refunds(owner)).isEqualTo(credit ? 1 : 0); assertThat(balance(owner)).isEqualTo(credit ? 1 : 0);
        assertThat(used(owner)).isEqualTo(credit ? 10 : 0); verify(entitlementTarget).refund(eq(owner), any());
    }

    private boolean cancelOrRelease(long owner, long teacher, long id, boolean release) {
        try {
            if (release) teachers.release(teacher, id); else requests.cancel(owner, id);
            return true;
        } catch (ApiException error) {
            assertThat(error.getCode().name()).isEqualTo(release ? "GRADING_NOT_ASSIGNEE" : "GRADING_NOT_CANCELLABLE");
            return false;
        }
    }

    private void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Fixture overlap timeout.");
    }
    private long user() {
        long id = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,'Expiry fixture') RETURNING id",
                Long.class, UUID.randomUUID() + "@example.test", HASH); users.add(id); usage(id, 0); return id;
    }
    private long pending(long owner, boolean credit) {
        if (credit) {
            usage(owner, 10);
            int before = balance(owner);
            jdbc.update("INSERT INTO learning.credit_transactions(user_id,seq,tx_type,amount,balance_before,balance_after) "
                    + "VALUES (?,(SELECT COALESCE(MAX(seq),0)+1 FROM learning.credit_transactions WHERE user_id=?),'TOPUP',1,?,?)",
                    owner, owner, before, before + 1);
        }
        return requests.submit(owner, new GradingRequestWrite("字".repeat(50) + ++essayNumber, null, 3)).id();
    }
    private Consumption receipt(long id) throws Exception { return mapper.treeToValue(repository.find(id).orElseThrow().payload().get("consumption"), Consumption.class); }
    private void deadline(long id, Instant value) { jdbc.update("UPDATE learning.service_requests SET deadline_at=? WHERE id=?", Timestamp.from(value), id); }
    private int used(long id) { return jdbc.queryForObject("SELECT COALESCE((free_usage->>'GRADING')::integer,0) FROM auth.users WHERE id=?", Integer.class, id); }
    private int balance(long id) { return jdbc.queryForList("SELECT balance_after FROM learning.credit_transactions WHERE user_id=? ORDER BY seq DESC LIMIT 1", Integer.class, id).stream().findFirst().orElse(0); }
    private int refunds(long id) { return jdbc.queryForObject("SELECT count(*) FROM learning.credit_transactions WHERE user_id=? AND tx_type='REFUND'", Integer.class, id); }
    private void usage(long id, int value) {
        Instant month = NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        jdbc.update("UPDATE auth.users SET free_usage=?::jsonb,usage_reset_at=? WHERE id=?", "{\"GRADING\":" + value + "}", Timestamp.from(month), id);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration { @Bean @Primary TestClock gradingExpiryClock() { return new TestClock(); } }
    static final class TestClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(NOW);
        void set(Instant value) { now.set(value); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }
}
