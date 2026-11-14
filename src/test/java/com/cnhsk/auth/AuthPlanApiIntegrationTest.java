package com.cnhsk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.PlanState;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.support.AbstractIntegrationTest;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.util.Masking;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

class AuthPlanApiIntegrationTest extends AbstractIntegrationTest {

    private static final Instant EXPIRY = Instant.parse("2030-12-01T00:00:00Z");
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());

    @Autowired private AuthApi auth;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager manager;
    private long userId;
    private final List<Long> createdIds = new ArrayList<>();
    private final List<String> createdPlanCodes = new ArrayList<>();
    private final List<Long> createdAuditIds = new ArrayList<>();
    private List<Map<String, Object>> plansBefore;

    @BeforeEach
    void setUp() {
        plansBefore = jdbc.queryForList("SELECT * FROM learning.plans ORDER BY code");
        createPlanIfAbsent("PREMIUM", "Test Premium");
        userId = users.saveAndFlush(new User("plan-" + UUID.randomUUID() + "@example.test",
                PASSWORD_HASH, "Plan test")).getId();
        createdIds.add(userId);
    }

    @AfterEach
    void cleanUp() {
        for (Long auditId : createdAuditIds) {
            jdbc.update("DELETE FROM shared.audit_logs WHERE id = ? AND actor_id = ?", auditId, userId);
        }
        users.deleteAllById(createdIds);
        for (String code : createdPlanCodes) {
            assertThat(jdbc.update("DELETE FROM learning.plans WHERE code = ?", code)).isEqualTo(1);
        }
        assertThat(jdbc.queryForList("SELECT * FROM learning.plans ORDER BY code")).isEqualTo(plansBefore);
    }

    @Test
    void staleUserFlushDoesNotOverwriteJdbcPlanUpdate() {
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            User stale = users.findById(userId).orElseThrow();
            assertThat(stale.getPlanCode()).isEqualTo("FREE");
            auth.lockPlan(userId);
            auth.updatePlan(userId, "PREMIUM", EXPIRY);
            stale.setDisplayName("Updated profile");
            users.saveAndFlush(stale);
        });

        assertThat(jdbc.queryForObject("SELECT plan_code FROM auth.users WHERE id = ?", String.class, userId))
                .isEqualTo("PREMIUM");
        assertThat(jdbc.queryForObject("SELECT plan_expires_at FROM auth.users WHERE id = ?", Timestamp.class, userId)
                .toInstant()).isEqualTo(EXPIRY);
        assertThat(users.findById(userId).orElseThrow().getDisplayName()).isEqualTo("Updated profile");
    }

    @Test
    void readPlanWorksWithoutAWriteTransactionAndReturnsEmptyOnlyForAbsence() {
        assertThat(auth.readPlan(userId)).isPresent();
        assertThat(auth.readPlan(Long.MAX_VALUE)).isEmpty();
        TransactionTemplate readOnly = new TransactionTemplate(manager);
        readOnly.setReadOnly(true);
        readOnly.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        PlanState state = readOnly.execute(status -> auth.readPlan(userId).orElseThrow());
        assertThat(state.planCode()).isEqualTo("FREE");
        assertThatThrownBy(() -> state.freeUsage().put("TRANSLATE", 3)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void readPlanDoesNotWaitForAnUncommittedRowLock() throws Exception {
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var locked = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        try {
            var writer = executor.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
                auth.lockPlan(userId);
                auth.updateUsage(userId, Map.of("TRANSLATE", 8), Instant.EPOCH);
                locked.countDown();
                try { assertThat(release.await(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
            }));
            assertThat(locked.await(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var reader = executor.submit(() -> auth.readPlan(userId).orElseThrow());
            assertThat(reader.get(10, java.util.concurrent.TimeUnit.SECONDS).freeUsage()).isEmpty();
            release.countDown();
            writer.get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(auth.readPlan(userId).orElseThrow().freeUsage()).containsEntry("TRANSLATE", 8);
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test
    void readPlanJoinsTheCallersRepeatableReadSnapshot() throws Exception {
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        TransactionTemplate snapshot = new TransactionTemplate(manager);
        snapshot.setReadOnly(true);
        snapshot.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        try {
            snapshot.executeWithoutResult(status -> {
                PlanState before = auth.readPlan(userId).orElseThrow();
                var writer = executor.submit(() -> new TransactionTemplate(manager).executeWithoutResult(write -> {
                    auth.lockPlan(userId);
                    auth.updateUsage(userId, Map.of("TRANSLATE", 9), Instant.EPOCH);
                }));
                try { writer.get(20, java.util.concurrent.TimeUnit.SECONDS); }
                catch (Exception exception) { throw new IllegalStateException(exception); }
                assertThat(auth.readPlan(userId)).contains(before);
            });
            assertThat(auth.readPlan(userId).orElseThrow().freeUsage()).containsEntry("TRANSLATE", 9);
        } finally { executor.shutdownNow(); }
    }

    @Test
    void auditActorForeignKeyCompletesWhileThePlanLockIsStillHeld() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            var holder = pool.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
                boundedLocks();
                auth.lockPlan(userId);
                locked.countDown();
                await(release);
            }));
            await(locked);
            var insertion = pool.submit(() -> new TransactionTemplate(manager).execute(status -> {
                boundedLocks();
                return jdbc.queryForObject("""
                        INSERT INTO shared.audit_logs(actor_id, action, target_type, detail)
                        VALUES (?, 'PLAN_UPDATED', 'PLAN', '{}'::jsonb) RETURNING id
                        """, Long.class, userId);
            }));
            Long auditId = insertion.get(10, TimeUnit.SECONDS);
            createdAuditIds.add(auditId);
            assertThat(release.getCount()).isEqualTo(1);
            assertThat(holder.isDone()).isFalse();
            assertThat(jdbc.queryForObject("SELECT actor_id FROM shared.audit_logs WHERE id = ?", Long.class, auditId))
                    .isEqualTo(userId);
            release.countDown();
            holder.get(15, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void twoPlanLocksSerializeAndTheWaiterReadsTheCommittedUsage() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch contenderStarted = new CountDownLatch(1);
        AtomicInteger holderPid = new AtomicInteger();
        AtomicInteger contenderPid = new AtomicInteger();
        try {
            var holder = pool.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
                boundedLocks();
                holderPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                auth.lockPlan(userId);
                auth.updateUsage(userId, Map.of("TRANSLATE", 6), Instant.EPOCH);
                locked.countDown();
                await(release);
            }));
            await(locked);
            var contender = pool.submit(() -> new TransactionTemplate(manager).execute(status -> {
                boundedLocks();
                contenderPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                contenderStarted.countDown();
                return auth.lockPlan(userId);
            }));
            await(contenderStarted);
            awaitBlockedBy(contenderPid.get(), holderPid.get());
            assertThat(contender.isDone()).isFalse();
            release.countDown();
            holder.get(15, TimeUnit.SECONDS);
            assertThat(contender.get(15, TimeUnit.SECONDS).freeUsage()).containsExactlyEntriesOf(Map.of("TRANSLATE", 6));
        } finally {
            release.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void boundedLocks() {
        jdbc.execute("SET LOCAL lock_timeout = '10s'");
        jdbc.execute("SET LOCAL statement_timeout = '15s'");
    }

    private void awaitBlockedBy(int waitingPid, int blockingPid) {
        long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        while (!Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, blockingPid, waitingPid))) {
            if (System.nanoTime() >= deadline) throw new AssertionError("Second lockPlan did not wait for the first transaction.");
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating plan locks.", exception);
        }
    }

    @Test
    void lockedSnapshotReflectsJdbcPlanAndUsageUpdates() {
        Instant reset = Instant.parse("2026-11-01T00:00:00Z");
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            PlanState before = auth.lockPlan(userId);
            assertThat(before.planCode()).isEqualTo("FREE");
            assertThat(before.freeUsage()).isEmpty();
            auth.updatePlan(userId, "PREMIUM", EXPIRY);
            auth.updateUsage(userId, Map.of("TRANSLATE", 3), reset);
            PlanState after = auth.lockPlan(userId);
            assertThat(after.planCode()).isEqualTo("PREMIUM");
            assertThat(after.planExpiresAt()).isEqualTo(EXPIRY);
            assertThat(after.usageResetAt()).isEqualTo(reset);
            assertThat(after.freeUsage()).containsExactlyEntriesOf(Map.of("TRANSLATE", 3));
            assertThat(before.freeUsage()).isEmpty();
            assertThatThrownBy(() -> after.freeUsage().put("TRANSLATE", 4))
                    .isInstanceOf(UnsupportedOperationException.class);
            auth.updatePlan(userId, "FREE", null);
            assertThat(auth.lockPlan(userId).planExpiresAt()).isNull();
        });
    }

    @Test
    void planAndUsageChangesRollBackTogether() {
        PlanState before = new TransactionTemplate(manager).execute(status -> auth.lockPlan(userId));
        assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
            auth.lockPlan(userId);
            auth.updatePlan(userId, "PREMIUM", EXPIRY);
            auth.updateUsage(userId, Map.of("TRANSLATE", 7), Instant.EPOCH);
            throw new IllegalStateException("Rollback requested.");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Rollback requested.");
        PlanState after = new TransactionTemplate(manager).execute(status -> auth.lockPlan(userId));
        assertThat(after).isEqualTo(before);
    }

    @Test
    void lockingAndUpdatesRequireAnExistingWritableTransaction() {
        List<Runnable> writes = List.of(() -> auth.lockPlan(userId),
                () -> auth.updatePlan(userId, "PREMIUM", EXPIRY),
                () -> auth.updateUsage(userId, Map.of(), Instant.EPOCH));
        for (Runnable write : writes) {
            assertThatThrownBy(write::run).isInstanceOf(IllegalTransactionStateException.class);
            TransactionTemplate readOnly = new TransactionTemplate(manager);
            readOnly.setReadOnly(true);
            assertThatThrownBy(() -> readOnly.executeWithoutResult(status -> write.run()))
                    .isInstanceOf(IllegalTransactionStateException.class);
        }
    }

    @Test
    void missingUsersHaveTheExistingNotFoundError() {
        long missing = Long.MAX_VALUE;
        for (Runnable call : List.<Runnable>of(() -> auth.lockPlan(missing),
                () -> auth.updatePlan(missing, "PREMIUM", EXPIRY),
                () -> auth.updateUsage(missing, Map.of(), Instant.EPOCH))) {
            assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(status -> call.run()))
                    .isInstanceOf(ApiException.class).satisfies(error -> {
                        ApiException apiError = (ApiException) error;
                        assertThat(apiError.getCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
                        assertThat(apiError.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(apiError.getMessage()).isEqualTo("Không tìm thấy tài khoản.");
                    });
        }
    }

    @Test
    void invalidWriteArgumentsHaveAFixedMessage() {
        for (Runnable call : List.<Runnable>of(() -> auth.lockPlan(0),
                () -> auth.updatePlan(userId, null, EXPIRY),
                () -> auth.updatePlan(userId, "UNKNOWN", EXPIRY),
                () -> auth.updatePlan(userId, "FREE", EXPIRY),
                () -> auth.updatePlan(userId, "PREMIUM", null),
                () -> auth.updateUsage(userId, null, Instant.EPOCH),
                () -> auth.updateUsage(userId, Map.of(), null),
                () -> auth.updateUsage(userId, Map.of("TRANSLATE", -1), Instant.EPOCH),
                () -> auth.downgradeExpiredPlans(null))) {
            assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(status -> call.run()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Tham số không hợp lệ.");
        }
    }

    @Test
    void maskedEmailsUseSharedMaskingAndOmitUnknownUsers() {
        String email = users.findById(userId).orElseThrow().getEmail();
        Map<Long, String> result = auth.maskedEmails(Set.of(userId, Long.MAX_VALUE));
        assertThat(result).containsExactlyEntriesOf(Map.of(userId, Masking.email(email)));
        assertThat(result.get(userId)).isNotEqualTo(email);
        assertThat(auth.maskedEmails(Set.of())).isEmpty();
    }

    @Test
    void conflictingPlanInsertPreservesTheRowWithoutClaimingOwnership() {
        Map<String, Object> before = jdbc.queryForMap("SELECT * FROM learning.plans WHERE code = 'PREMIUM'");
        List<String> ownedBefore = List.copyOf(createdPlanCodes);

        createPlanIfAbsent("PREMIUM", "Must not replace existing plan");

        assertThat(jdbc.queryForMap("SELECT * FROM learning.plans WHERE code = 'PREMIUM'")).isEqualTo(before);
        assertThat(createdPlanCodes).containsExactlyElementsOf(ownedBefore);
    }

    @Test
    void activeCountsIncludeExpiredPlansInFreeWithoutRoleOrStatusFilters() {
        Instant now = Instant.parse("2026-11-07T00:00:00Z");
        long premiumBefore = auth.countActivePlan("PREMIUM", now);
        long freeBefore = auth.countActivePlan("FREE", now);
        long active = additionalUser("PREMIUM", now.plusSeconds(1));
        additionalUser("PREMIUM", now.minusSeconds(1));
        additionalUser("PREMIUM", now);
        createPlanIfAbsent("PREMIUM_PLUS", "Test Plus");
        additionalUser("PREMIUM_PLUS", now.plusSeconds(1));
        additionalUser("PREMIUM_PLUS", now.minusSeconds(1));
        jdbc.update("UPDATE auth.users SET banned_at = ?, ban_reason = 'Test status' WHERE id = ?",
                Timestamp.from(now), active);

        assertThat(auth.countActivePlan("PREMIUM", now)).isEqualTo(premiumBefore + 1);
        assertThat(auth.countActivePlan("FREE", now)).isEqualTo(freeBefore + 3);
        assertThat(auth.countActivePlan("PREMIUM_PLUS", now)).isZero();
        assertThat(auth.countActivePlan("UNKNOWN", now)).isZero();
    }

    @Test
    void downgradeChangesOnlyExpiredPaidPlansIncludingTheBoundary() {
        Instant now = Instant.parse("2026-11-07T00:00:00Z");
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            int existing = jdbc.queryForObject("""
                    SELECT count(*) FROM auth.users WHERE plan_code <> 'FREE' AND plan_expires_at <= ?
                    """, Integer.class, Timestamp.from(now));
            long expired = additionalUser("PREMIUM", now.minusSeconds(1));
            long boundary = additionalUser("PREMIUM", now);
            long active = additionalUser("PREMIUM", now.plusSeconds(1));
            assertThat(auth.downgradeExpiredPlans(now)).isEqualTo(existing + 2);
            assertThat(auth.lockPlan(expired).planCode()).isEqualTo("FREE");
            assertThat(auth.lockPlan(boundary).planExpiresAt()).isNull();
            assertThat(auth.lockPlan(active).planCode()).isEqualTo("PREMIUM");
            assertThat(auth.lockPlan(userId).planCode()).isEqualTo("FREE");
            status.setRollbackOnly();
        });
    }

    @Test
    void corruptStoredUsageIsRejectedWithoutEchoingItsValue() {
        jdbc.update("UPDATE auth.users SET free_usage = '{\"TRANSLATE\":\"invalid-counter\"}'::jsonb WHERE id = ?", userId);
        assertThatThrownBy(() -> new TransactionTemplate(manager).execute(status -> auth.lockPlan(userId)))
                .isInstanceOf(IllegalStateException.class).hasMessage("Dữ liệu hạn mức không hợp lệ.");
    }

    private void createPlanIfAbsent(String code, String name) {
        int inserted = jdbc.update("""
                INSERT INTO learning.plans(code, name, duration_days) VALUES (?, ?, 30)
                ON CONFLICT (code) DO NOTHING
                """, code, name);
        if (inserted == 1) {
            createdPlanCodes.add(code);
        }
    }

    private long additionalUser(String code, Instant expiry) {
        long id = jdbc.queryForObject("""
                INSERT INTO auth.users(email, password_hash, display_name, plan_code, plan_expires_at)
                VALUES (?, ?, 'Count test', ?, ?) RETURNING id
                """, Long.class, "plan-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH,
                code, Timestamp.from(expiry));
        createdIds.add(id);
        return id;
    }
}
