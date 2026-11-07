package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.learning.credit.web.CreateBatchRequest;
import com.cnhsk.learning.credit.web.CreateBatchRequest.CardType;
import com.cnhsk.learning.credit.web.CreateBatchRequest.Channel;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class CardBatchConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());

    @Autowired private JdbcTemplate jdbc;
    @Autowired private NamedParameterJdbcTemplate namedJdbc;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private Clock clock;
    @Autowired private AuditService audit;

    private long adminId;
    private AuthApi auth;
    private CardCodes codes;

    @BeforeEach
    void setUp() {
        adminId = jdbc.queryForObject("""
                INSERT INTO auth.users(email, password_hash, display_name)
                VALUES (?, ?, 'Card batch concurrency test') RETURNING id
                """, Long.class, "card-concurrency-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH);
        auth = mock(AuthApi.class);
        when(auth.currentUser()).thenReturn(new CurrentUser(adminId, Set.of(RoleCode.ADMIN), true));
        when(auth.hasRole(adminId, RoleCode.ADMIN)).thenReturn(true);
        BillingConfig config = new BillingConfig();
        config.setCardCodePepper(UUID.randomUUID().toString() + UUID.randomUUID());
        codes = new CardCodes(config, clock);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM shared.audit_logs WHERE actor_id = ?", adminId);
        jdbc.update("DELETE FROM learning.credit_cards WHERE created_by = ?", adminId);
        jdbc.update("DELETE FROM auth.users WHERE id = ?", adminId);
    }

    @Test
    void conflictingCommittedHashWaitsThenRetriesOnlyTheMissingCode() throws Exception {
        createOverlappingBatches(false);
    }

    @Test
    void rolledBackHashWakesTheContenderWithoutRegeneratingItsCode() throws Exception {
        createOverlappingBatches(true);
    }

    @Test
    void concurrentVoidsCountEachUnusedCardOnceAndPreserveUsedMetadata() throws Exception {
        String batchId = codes.newBatchId();
        CardBatchService service = service(batchId, codes::generate);
        new TransactionTemplate(manager).execute(status -> service.create(request(5)));
        List<Long> cardIds = jdbc.queryForList("""
                SELECT id FROM learning.credit_cards WHERE batch_id = ? AND created_by = ? ORDER BY id
                """, Long.class, batchId, adminId);
        jdbc.update("""
                UPDATE learning.credit_cards SET status = 'USED', used_by = ?, used_at = ? WHERE id = ?
                """, adminId, Timestamp.from(clock.instant()), cardIds.get(0));
        jdbc.update("UPDATE learning.credit_cards SET status = 'VOIDED' WHERE id = ?", cardIds.get(1));
        var usedBefore = jdbc.queryForMap("""
                SELECT status, used_by, used_at, code_hash FROM learning.credit_cards WHERE id = ?
                """, cardIds.get(0));

        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch firstUpdated = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger firstPid = new AtomicInteger();
        AtomicInteger secondPid = new AtomicInteger();
        try {
            var first = pool.submit(() -> new TransactionTemplate(manager).execute(status -> {
                firstPid.set(backendPid());
                var result = service.voidBatch(batchId);
                firstUpdated.countDown();
                await(releaseFirst);
                return result;
            }));
            await(firstUpdated);
            var second = pool.submit(() -> new TransactionTemplate(manager).execute(status -> {
                secondPid.set(backendPid());
                secondStarted.countDown();
                return service.voidBatch(batchId);
            }));
            await(secondStarted);
            awaitBlockedBy(secondPid.get(), firstPid.get());
            assertThat(second.isDone()).isFalse();
            releaseFirst.countDown();

            var initial = first.get(15, TimeUnit.SECONDS);
            var repeated = second.get(15, TimeUnit.SECONDS);
            assertThat(initial.batchId()).isEqualTo(batchId);
            assertThat(repeated.batchId()).isEqualTo(batchId);
            assertThat(initial.voided()).isEqualTo(3);
            assertThat(repeated.voided()).isZero();
            assertThat(initial.voided() + repeated.voided()).isEqualTo(3);
            assertThat(jdbc.queryForList("""
                    SELECT status FROM learning.credit_cards WHERE batch_id = ? AND created_by = ? ORDER BY id
                    """, String.class, batchId, adminId))
                    .containsExactly("USED", "VOIDED", "VOIDED", "VOIDED", "VOIDED");
            assertThat(jdbc.queryForMap("""
                    SELECT status, used_by, used_at, code_hash FROM learning.credit_cards WHERE id = ?
                    """, cardIds.get(0))).isEqualTo(usedBefore);
            assertThat(jdbc.queryForList("""
                    SELECT (detail ->> 'voided')::integer FROM shared.audit_logs
                    WHERE actor_id = ? AND action = 'CARD_BATCH_VOID' ORDER BY id
                    """, Integer.class, adminId)).containsExactly(3, 0);
        } finally {
            releaseFirst.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void createOverlappingBatches(boolean rollbackFirst) throws Exception {
        String sharedCode = codes.generate();
        String uniqueCode = codes.generate();
        String replacementCode = codes.generate();
        String firstBatch = codes.newBatchId();
        String secondBatch = codes.newBatchId();
        assertThat(secondBatch).isNotEqualTo(firstBatch);
        AtomicInteger generated = new AtomicInteger();
        List<String> contenderCodes = List.of(sharedCode, uniqueCode, replacementCode);
        CardBatchService firstService = service(firstBatch, () -> sharedCode);
        CardBatchService secondService = service(secondBatch, () -> contenderCodes.get(generated.getAndIncrement()));

        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch firstInserted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger firstPid = new AtomicInteger();
        AtomicInteger secondPid = new AtomicInteger();
        try {
            var first = pool.submit(() -> new TransactionTemplate(manager).execute(status -> {
                firstPid.set(backendPid());
                var result = firstService.create(request(1));
                firstInserted.countDown();
                await(releaseFirst);
                if (rollbackFirst) {
                    status.setRollbackOnly();
                }
                return result;
            }));
            await(firstInserted);
            var second = pool.submit(() -> new TransactionTemplate(manager).execute(status -> {
                secondPid.set(backendPid());
                secondStarted.countDown();
                return secondService.create(request(2));
            }));
            await(secondStarted);
            awaitBlockedBy(secondPid.get(), firstPid.get());
            assertThat(second.isDone()).isFalse();
            releaseFirst.countDown();

            var initial = first.get(15, TimeUnit.SECONDS);
            var contender = second.get(15, TimeUnit.SECONDS);
            assertThat(initial.batchId()).isEqualTo(firstBatch);
            assertThat(contender.batchId()).isEqualTo(secondBatch);
            assertThat(contender.codes()).hasSize(2).doesNotHaveDuplicates();
            assertThat(contender.codes()).contains(codes.format(uniqueCode));
            assertThat(generated.get()).isEqualTo(rollbackFirst ? 2 : 3);
            assertThat(contender.codes()).contains(codes.format(rollbackFirst ? sharedCode : replacementCode));
            if (!rollbackFirst) {
                assertThat(initial.codes()).containsExactly(codes.format(sharedCode));
                assertThat(contender.codes()).doesNotContain(codes.format(sharedCode));
            }
            var storedHashes = jdbc.queryForList("""
                    SELECT code_hash FROM learning.credit_cards WHERE created_by = ?
                    """, String.class, adminId);
            assertThat(storedHashes).hasSize(rollbackFirst ? 2 : 3).doesNotHaveDuplicates();
            assertThat(storedHashes).contains(codes.hash(sharedCode), codes.hash(uniqueCode));
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM learning.credit_cards WHERE batch_id = ? AND created_by = ?
                    """, Integer.class, firstBatch, adminId)).isEqualTo(rollbackFirst ? 0 : 1);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM shared.audit_logs WHERE actor_id = ? AND action = 'CARD_BATCH_CREATE'
                    """, Integer.class, adminId)).isEqualTo(rollbackFirst ? 1 : 2);
        } finally {
            releaseFirst.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
    }

    private CardBatchService service(String batchId, Supplier<String> generator) {
        return new CardBatchService(auth, namedJdbc, codes, clock, audit, generator, () -> batchId);
    }

    private CreateBatchRequest request(int quantity) {
        return new CreateBatchRequest(CardType.CREDIT, 10, null, quantity, Channel.DIRECT,
                null, null, null, null);
    }

    private int backendPid() {
        return jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
    }

    private void awaitBlockedBy(int waitingPid, int blockingPid) {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (!Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, blockingPid, waitingPid))) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("The contender did not wait for the first transaction's PostgreSQL lock.");
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating card batch transactions.", exception);
        }
    }
}
