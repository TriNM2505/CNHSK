package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.PlanState;
import com.cnhsk.learning.credit.LedgerService.AppendCommand;
import com.cnhsk.learning.credit.LedgerService.LedgerEntry;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;

class LedgerServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());

    @Autowired private LedgerService ledger;
    @Autowired private AuthApi auth;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private Clock clock;
    private long userId;

    @BeforeEach
    void setUp() {
        userId = jdbc.queryForObject("""
                INSERT INTO auth.users(email, password_hash, display_name)
                VALUES (?, ?, 'Ledger test') RETURNING id
                """, Long.class, "ledger-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM learning.credit_transactions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM auth.users WHERE id = ?", userId);
    }

    @Test
    void firstAppendStartsAtOneWithZeroBalanceAndStoredTimestamp() {
        assertThat(ledger.balance(userId)).isZero();
        Instant before = clock.instant().minusSeconds(1);
        LedgerEntry entry = append("TOPUP", 40);
        assertThat(entry.id()).isPositive();
        assertThat(entry.seq()).isEqualTo(1);
        assertThat(entry.balanceBefore()).isZero();
        assertThat(entry.balanceAfter()).isEqualTo(40);
        assertThat(ledger.balance(userId)).isEqualTo(40);
        Instant created = jdbc.queryForObject("SELECT created_at FROM learning.credit_transactions WHERE id = ?",
                Timestamp.class, entry.id()).toInstant();
        assertThat(created).isBetween(before, clock.instant().plusSeconds(1));
    }

    @Test
    void sequentialAppendUsesTheLastBalanceAndPersistsMetadata() {
        append("TOPUP", 40);
        LedgerEntry debit = new TransactionTemplate(manager).execute(status -> ledger.append(
                new AppendCommand(userId, "DEDUCT", -7, "TRANSLATE", null, null, null,
                        "Self-composed test reason", null, "test-" + UUID.randomUUID())));
        assertThat(debit.seq()).isEqualTo(2);
        assertThat(debit.balanceBefore()).isEqualTo(40);
        assertThat(debit.balanceAfter()).isEqualTo(33);
        Map<String, Object> stored = jdbc.queryForMap("""
                SELECT tx_type, amount, feature_code, reason, dispute_ref
                FROM learning.credit_transactions WHERE id = ?
                """, debit.id());
        assertThat(stored).containsEntry("tx_type", "DEDUCT").containsEntry("amount", -7)
                .containsEntry("feature_code", "TRANSLATE").containsEntry("reason", "Self-composed test reason");
        assertThat(stored.get("dispute_ref")).asString().startsWith("test-");
        assertThat(ledger.balance(userId)).isEqualTo(33);
    }

    @Test
    void appendRequiresACallerTransaction() {
        assertThatThrownBy(() -> ledger.append(command("TOPUP", 1)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(count()).isZero();
    }

    @Test
    void readOnlyTransactionCannotAppend() {
        TransactionTemplate transaction = new TransactionTemplate(manager);
        transaction.setReadOnly(true);
        assertThatThrownBy(() -> transaction.execute(status -> ledger.append(command("TOPUP", 1))))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(count()).isZero();
    }

    @Test
    void callerCanAlreadyHoldThePlanLock() {
        LedgerEntry entry = new TransactionTemplate(manager).execute(status -> {
            auth.lockPlan(userId);
            return ledger.append(command("TOPUP", 12));
        });
        assertThat(entry.seq()).isEqualTo(1);
        assertThat(ledger.balance(userId)).isEqualTo(12);
    }

    @Test
    void overdraftIsRejectedByTheDatabaseWithoutAnInsertedRow() {
        assertThatThrownBy(() -> append("DEDUCT", -1))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count()).isZero();
        append("TOPUP", 5);
        assertThatThrownBy(() -> append("DEDUCT", -6))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count()).isEqualTo(1);
        assertThat(ledger.balance(userId)).isEqualTo(5);
        assertThat(append("DEDUCT", -5).seq()).isEqualTo(2);
        assertThat(ledger.balance(userId)).isZero();
    }

    @Test
    void failedAppendRollsBackPlanUsageAndEarlierAppends() {
        jdbc.update("""
                INSERT INTO learning.plans(code, name, duration_days) VALUES ('PREMIUM', 'Test Premium', 30)
                ON CONFLICT (code) DO NOTHING
                """);
        PlanState before = new TransactionTemplate(manager).execute(status -> auth.lockPlan(userId));
        assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
            auth.lockPlan(userId);
            auth.updatePlan(userId, "PREMIUM", Instant.parse("2030-01-01T00:00:00Z"));
            auth.updateUsage(userId, Map.of("TRANSLATE", 4), Instant.EPOCH);
            ledger.append(command("TOPUP", 5));
            ledger.append(command("DEDUCT", -6));
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count()).isZero();
        assertThat(ledger.balance(userId)).isZero();
        PlanState after = new TransactionTemplate(manager).execute(status -> auth.lockPlan(userId));
        assertThat(after).isEqualTo(before);
    }

    @Test
    void caughtAppendFailureStillMarksTheCallerTransactionRollbackOnly() {
        assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
            ledger.append(command("TOPUP", 5));
            assertThatThrownBy(() -> ledger.append(command("DEDUCT", -6)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        })).isInstanceOf(UnexpectedRollbackException.class);
        assertThat(count()).isZero();
    }

    @Test
    void arithmeticOverflowCannotWrapTheBalance() {
        append("TOPUP", Integer.MAX_VALUE);
        assertThatThrownBy(() -> append("TOPUP", 1)).isInstanceOf(ArithmeticException.class);
        assertThat(count()).isEqualTo(1);
        assertThat(ledger.balance(userId)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void refundReferencesArePersistedAndDatabaseUniquenessRollsBackDuplicates() {
        append("TOPUP", 10);
        LedgerEntry debit = append("DEDUCT", -3);
        AppendCommand refund = new AppendCommand(userId, "REFUND", 3, "TRANSLATE",
                null, null, debit.id(), null, null, null);
        LedgerEntry entry = new TransactionTemplate(manager).execute(status -> ledger.append(refund));
        assertThat(entry.seq()).isEqualTo(3);
        assertThat(ledger.balance(userId)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT ref_transaction_id FROM learning.credit_transactions WHERE id = ?",
                Long.class, entry.id())).isEqualTo(debit.id());
        assertThatThrownBy(() -> new TransactionTemplate(manager).execute(status -> ledger.append(refund)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count()).isEqualTo(3);
        assertThat(ledger.balance(userId)).isEqualTo(10);
    }

    @Test
    void missingUserHasTheExistingNotFoundErrorAndNoLedgerRow() {
        assertThatThrownBy(() -> new TransactionTemplate(manager).execute(status -> ledger.append(
                new AppendCommand(Long.MAX_VALUE, "TOPUP", 1, null, null, null, null, null, null, null))))
                .isInstanceOf(ApiException.class).satisfies(error ->
                        assertThat(((ApiException) error).getCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
        assertThat(count()).isZero();
    }

    @Test
    void invalidArgumentsAreRejectedWithAFixedMessage() {
        assertThatThrownBy(() -> new TransactionTemplate(manager).execute(status -> ledger.append(null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Tham số không hợp lệ.");
        assertThatThrownBy(() -> ledger.balance(0))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Tham số không hợp lệ.");
    }

    @Test
    void sameUserConcurrentAppendsWaitForTheLockAndKeepContinuousSequence() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch firstWritten = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger secondPid = new AtomicInteger();
        try {
            var first = pool.submit(() -> new TransactionTemplate(manager).execute(status -> {
                LedgerEntry entry = ledger.append(command("TOPUP", 40));
                firstWritten.countDown();
                await(releaseFirst);
                return entry;
            }));
            await(firstWritten);
            var second = pool.submit(() -> new TransactionTemplate(manager).execute(status -> {
                secondPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                secondStarted.countDown();
                return ledger.append(command("DEDUCT", -7));
            }));
            await(secondStarted);
            awaitCondition(() -> jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity WHERE pid = ? AND wait_event_type = 'Lock'
                    """, Integer.class, secondPid.get()) == 1);
            assertThat(second.isDone()).isFalse();
            releaseFirst.countDown();
            LedgerEntry initial = first.get(15, TimeUnit.SECONDS);
            LedgerEntry next = second.get(15, TimeUnit.SECONDS);
            assertThat(initial.seq()).isEqualTo(1);
            assertThat(initial.balanceBefore()).isZero();
            assertThat(initial.balanceAfter()).isEqualTo(40);
            assertThat(next.seq()).isEqualTo(2);
            assertThat(next.balanceBefore()).isEqualTo(40);
            assertThat(next.balanceAfter()).isEqualTo(33);
            assertThat(jdbc.queryForList("SELECT seq FROM learning.credit_transactions WHERE user_id = ? ORDER BY seq",
                    Integer.class, userId)).containsExactly(1, 2);
            assertThat(ledger.balance(userId)).isEqualTo(33);
        } finally {
            releaseFirst.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
    }

    private AppendCommand command(String type, int amount) {
        return new AppendCommand(userId, type, amount, null, null, null, null, null, null, null);
    }

    private LedgerEntry append(String type, int amount) {
        return new TransactionTemplate(manager).execute(status -> ledger.append(command(type, amount)));
    }

    private long count() {
        return jdbc.queryForObject("SELECT count(*) FROM learning.credit_transactions WHERE user_id = ?", Long.class, userId);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating test transactions.", exception);
        }
    }

    private static void awaitCondition(BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("The second transaction did not wait for the user lock.");
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
    }
}
