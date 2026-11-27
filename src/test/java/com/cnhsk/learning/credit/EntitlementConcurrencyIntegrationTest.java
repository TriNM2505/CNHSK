package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.api.Consumption;
import com.cnhsk.learning.api.Consumption.Source;
import com.cnhsk.learning.api.PaidFeature;
import com.cnhsk.learning.credit.LedgerService.AppendCommand;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class EntitlementConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private static final Instant NOW = Instant.parse("2031-11-15T10:00:00Z");
    private static final Instant MONTH = Instant.parse("2031-10-31T17:00:00Z");

    @Autowired private AuthApi auth;
    @Autowired private LedgerService ledger;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NamedParameterJdbcTemplate namedJdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper mapper;
    @Autowired private PlatformTransactionManager manager;
    private long userId;
    private EntitlementService service;

    @BeforeEach
    void setUp() {
        userId = jdbc.queryForObject("""
                INSERT INTO auth.users(email, password_hash, display_name)
                VALUES (?, ?, 'Entitlement concurrency test') RETURNING id
                """, Long.class, "entitlement-race-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH);
        service = new EntitlementService(auth, ledger, namedJdbc, redis, mapper,
                settings(new BillingConfig()), Clock.fixed(NOW, ZoneOffset.UTC));
        usage(0);
    }

    @AfterEach
    void cleanUp() {
        var keys = redis.keys("billing:*:" + userId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        jdbc.update("DELETE FROM learning.credit_transactions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM auth.users WHERE id = ?", userId);
    }

    @Test
    void lastFreeUseAndTheNextRequestSerializeOnTheUserLockWithoutSpendingPoints() throws Exception {
        usage(9);
        topup(1);
        List<String> outcomes = contend(() -> {
            assertThat(consume("last-quota").source()).isEqualTo(Source.QUOTA);
            return "free";
        }, () -> {
            assertThatThrownBy(() -> consume("over-quota")).isInstanceOf(ApiException.class).satisfies(error ->
                    assertThat(((ApiException) error).getCode()).isEqualTo(ErrorCode.INSUFFICIENT_CREDITS));
            return "upgrade";
        }, false);
        assertThat(outcomes).containsExactly("free", "upgrade");
        assertThat(used()).isEqualTo(10);
        assertThat(ledger.balance(userId)).isEqualTo(1);
        assertThat(amounts()).containsExactly(1);
        assertThat(redis.hasKey(idemKey("over-quota"))).isFalse();
    }
    @Test
    void concurrentCreditRefundsAppendOnlyOneCompensation() throws Exception {
        // Points are no longer spent, but a debit recorded earlier can still be refunded exactly once.
        topup(1);
        long debit = transaction().execute(status -> ledger.append(new AppendCommand(userId, "DEDUCT", -1,
                "TRANSLATE", null, null, null, null, null, null)).id());
        Consumption receipt = new Consumption(Source.CREDIT, debit, PaidFeature.TRANSLATE, "credit-refund", MONTH);
        contend(() -> refund(receipt), () -> refund(receipt), false);
        assertThat(amounts()).containsExactly(1, -1, 1);
        assertThat(sequences()).containsExactly(1, 2, 3);
        assertThat(ledger.balance(userId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM learning.credit_transactions
                WHERE user_id = ? AND tx_type = 'REFUND' AND ref_transaction_id = ?
                """, Integer.class, userId, receipt.transactionId())).isEqualTo(1);
    }
    @Test
    void concurrentQuotaRefundsDecrementOnlyOnceWhileOtherUsesRemain() throws Exception {
        Consumption receipt = transaction().execute(status -> consume("quota-refund"));
        transaction().execute(status -> consume("other-quota"));
        contend(() -> refund(receipt), () -> refund(receipt), false);
        assertThat(used()).isEqualTo(1);
        assertThat(amounts()).isEmpty();
        assertThat(redis.hasKey("billing:refund:" + userId + ":quota-refund")).isTrue();
    }

    @Test
    void rollbackOfFirstConsumerLetsWaiterUseTheSameQuotaWithoutAStaleReceipt() throws Exception {
        usage(9);
        List<Consumption> receipts = contend(() -> consume("rolled-back"), () -> consume("waiter"), true);
        assertThat(receipts).allSatisfy(receipt -> assertThat(receipt.source()).isEqualTo(Source.QUOTA));
        assertThat(used()).isEqualTo(10);
        assertThat(amounts()).isEmpty();
        assertThat(redis.hasKey(idemKey("rolled-back"))).isFalse();
        assertThat(redis.hasKey(idemKey("waiter"))).isTrue();
    }

    @Test
    void waiterRechecksRedisAfterAcquiringThePlanLock() throws Exception {
        Consumption committed = transaction().execute(status -> consume("published"));
        String json = redis.opsForValue().get(idemKey("published"));
        assertThat(json).isNotBlank();
        redis.delete(idemKey("published"));
        List<Consumption> results = contend(() -> {
            auth.lockPlan(userId);
            return committed;
        }, () -> consume("published"), false,
                () -> redis.opsForValue().set(idemKey("published"), json, Duration.ofHours(24)));
        assertThat(results).containsExactly(committed, committed);
        assertThat(used()).isEqualTo(1);
        assertThat(amounts()).isEmpty();
    }

    @Test
    void waitingConsumerCapturesTheVietnamMonthAfterAcquiringTheLock() throws Exception {
        Instant october = Instant.parse("2031-10-31T16:59:59Z");
        transaction().executeWithoutResult(status -> {
            auth.lockPlan(userId);
            auth.updateUsage(userId, Map.of("TRANSLATE", 10, "GRADING", 4),
                    Instant.parse("2031-09-30T17:00:00Z"));
        });
        AtomicReference<Instant> now = new AtomicReference<>(october);
        Clock movingClock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return Clock.fixed(now.get(), zone);
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        EntitlementService movingService = new EntitlementService(auth, ledger, namedJdbc, redis, mapper,
                settings(new BillingConfig()), movingClock);
        contend(() -> {
            auth.lockPlan(userId);
            return true;
        }, () -> {
            Consumption receipt = movingService.consume(userId, PaidFeature.TRANSLATE, "month-waiter");
            assertThat(receipt.periodStart()).isEqualTo(MONTH);
            assertThat(receipt.source()).isEqualTo(Source.QUOTA);
            return true;
        }, false, () -> now.set(MONTH));
        assertThat(transaction().execute(status -> auth.lockPlan(userId)).freeUsage())
                .containsExactlyEntriesOf(Map.of("TRANSLATE", 1));
        assertThat(used()).isEqualTo(1);
        assertThat(amounts()).isEmpty();
    }

    private <T> List<T> contend(Supplier<T> firstOperation, Supplier<T> secondOperation,
            boolean rollbackFirst) throws Exception {
        return contend(firstOperation, secondOperation, rollbackFirst, () -> {});
    }

    private <T> List<T> contend(Supplier<T> firstOperation, Supplier<T> secondOperation,
            boolean rollbackFirst, Runnable beforeRelease) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch firstCompletedOperation = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger secondPid = new AtomicInteger();
        try {
            var first = pool.submit(() -> transaction().execute(status -> {
                T result = firstOperation.get();
                firstCompletedOperation.countDown();
                await(releaseFirst);
                if (rollbackFirst) {
                    status.setRollbackOnly();
                }
                return result;
            }));
            await(firstCompletedOperation);
            var second = pool.submit(() -> transaction().execute(status -> {
                secondPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                secondStarted.countDown();
                return secondOperation.get();
            }));
            await(secondStarted);
            awaitCondition(() -> jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity WHERE pid = ? AND wait_event_type = 'Lock'
                    """, Integer.class, secondPid.get()) == 1);
            assertThat(second.isDone()).isFalse();
            beforeRelease.run();
            releaseFirst.countDown();
            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Consumption consume(String key) {
        return service.consume(userId, PaidFeature.TRANSLATE, key);
    }

    private Boolean refund(Consumption receipt) {
        service.refund(userId, receipt);
        return true;
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(manager);
    }

    private void usage(int count) {
        transaction().executeWithoutResult(status -> {
            auth.lockPlan(userId);
            auth.updateUsage(userId, Map.of("TRANSLATE", count), MONTH);
        });
    }

    private int used() {
        return transaction().execute(status -> auth.lockPlan(userId)).freeUsage().getOrDefault("TRANSLATE", 0);
    }

    private void topup(int amount) {
        transaction().executeWithoutResult(status -> ledger.append(new AppendCommand(userId, "TOPUP", amount,
                null, null, null, null, null, null, null)));
    }

    private List<Integer> sequences() {
        return jdbc.queryForList("SELECT seq FROM learning.credit_transactions WHERE user_id = ? ORDER BY seq",
                Integer.class, userId);
    }

    private List<Integer> amounts() {
        return jdbc.queryForList("SELECT amount FROM learning.credit_transactions WHERE user_id = ? ORDER BY seq",
                Integer.class, userId);
    }

    private String idemKey(String key) {
        return "billing:idem:" + userId + ":" + key;
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(20, TimeUnit.SECONDS)).as("transaction coordination completed").isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating entitlement transactions.", exception);
        }
    }

    private static void awaitCondition(BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("The second entitlement transaction did not wait for the user lock.");
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
    }

    private static com.cnhsk.shared.settings.SettingsService settings(BillingConfig config) {
        var settings = org.mockito.Mockito.mock(com.cnhsk.shared.settings.SettingsService.class);
        org.mockito.Mockito.when(settings.getInt(com.cnhsk.shared.settings.SettingKey.BILLING_FREE_MONTHLY_LIMIT))
                .thenAnswer(invocation -> config.getFreeMonthlyLimit());
        // Learning features have their own monthly allowance; these tests give it the same configured value.
        org.mockito.Mockito.when(settings.getInt(com.cnhsk.shared.settings.SettingKey.LEARNING_FREE_MONTHLY_LIMIT))
                .thenAnswer(invocation -> config.getFreeMonthlyLimit());
        return settings;
    }
}
