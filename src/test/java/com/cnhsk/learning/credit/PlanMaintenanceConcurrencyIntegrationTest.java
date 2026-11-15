package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.cnhsk.learning.api.Consumption;
import com.cnhsk.learning.api.Consumption.Source;
import com.cnhsk.learning.api.PaidFeature;
import com.cnhsk.learning.credit.LedgerService.AppendCommand;
import com.cnhsk.learning.credit.web.Benefits;
import com.cnhsk.learning.credit.web.PlanStatus.PlanCode;
import com.cnhsk.learning.credit.web.PlanUpdateRequest;
import com.cnhsk.learning.credit.web.RedeemRequest;
import com.cnhsk.learning.credit.web.RedeemResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

class PlanMaintenanceConcurrencyIntegrationTest extends PlanMaintenanceIntegrationSupport {
    private static final Instant MONTH = Instant.parse("2035-10-31T17:00:00Z");
    private static final Map<String, Integer> USAGE = Map.of("TRANSLATE", 7, "GRADING", 3);

    @Test
    void committedRenewalMakesWaitingJobRecheckAndKeepTheFutureSubscription() throws Exception {
        long learner = expiredLearner();
        IssuedCard card = card("SUBSCRIPTION", 30, createUser("ADMIN"));
        Race<RedeemResult, Boolean> result = contend(() -> renew(learner, card), this::runJob, false);
        assertRenewed(learner, card, result.first());
        assertThat(result.second()).isTrue();
        assertUnchangedUsage(learner);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void waitingRenewalSurvivesBothCommittedAndRolledBackDowngrades(boolean rollbackJob) throws Exception {
        long learner = expiredLearner();
        IssuedCard card = card("SUBSCRIPTION", 30, createUser("ADMIN"));
        Race<Boolean, RedeemResult> result = contend(this::runJob, () -> renew(learner, card), rollbackJob);
        assertThat(result.first()).isTrue();
        assertRenewed(learner, card, result.second());
        assertUnchangedUsage(learner);
    }

    @Test
    void overlappingJobsKeepTheFirstCommittedCutoffWithoutDuplicateEffects() throws Exception {
        long first = expiredLearner();
        long second = user("PREMIUM_PLUS", NOW.minusSeconds(2));
        var catalog = catalogRows();
        var ledgerBefore = jdbc.queryForList("SELECT * FROM learning.credit_transactions ORDER BY id");
        var auditBefore = jdbc.queryForList("SELECT * FROM shared.audit_logs ORDER BY id");
        PlanMaintenance later = new PlanMaintenance(auth, Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));
        Race<Boolean, Boolean> results = contend(this::runJob, () -> { later.downgradeExpiredPlans(); return true; }, false);
        assertThat(results.first()).isTrue();
        assertThat(results.second()).isTrue();
        for (long id : List.of(first, second)) {
            assertThat(state(id).planCode()).isEqualTo("FREE");
            assertThat(state(id).planExpiresAt()).isNull();
            assertThat(jdbc.queryForObject("SELECT updated_at FROM auth.users WHERE id=?", Timestamp.class, id))
                    .isEqualTo(Timestamp.from(NOW));
        }
        assertUnchangedUsage(first);
        assertThat(catalogRows()).isEqualTo(catalog);
        assertThat(jdbc.queryForList("SELECT * FROM learning.credit_transactions ORDER BY id")).isEqualTo(ledgerBefore);
        assertThat(jdbc.queryForList("SELECT * FROM shared.audit_logs ORDER BY id")).isEqualTo(auditBefore);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FREE", "PREMIUM"})
    void catalogAndActorAuditCompleteWhileTheSameExpiredActorIsHeldByTheJob(String code) throws Exception {
        long actor = createUser("ADMIN", "LEARNER");
        plan(actor, "PREMIUM", NOW.minusSeconds(1));
        CountDownLatch catalogLocked = new CountDownLatch(1);
        CountDownLatch releaseCatalog = new CountDownLatch(1);
        CountDownLatch jobUpdated = new CountDownLatch(1);
        CountDownLatch releaseJob = new CountDownLatch(1);
        var gated = new CatalogGateJdbc(jdbc, () -> { catalogLocked.countDown(); await(releaseCatalog); });
        PlanAdminService plans = new PlanAdminService(auth, gated, mapper, audit, FIXED_CLOCK);
        PlanUpdateRequest request = listing(code);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var update = pool.submit(() -> transaction(() -> asAdmin(actor,
                    () -> plans.updatePlan(PlanCode.valueOf(code), request)), new AtomicInteger(), null, null, false));
            await(catalogLocked);
            var job = pool.submit(() -> transaction(this::runJob, new AtomicInteger(), jobUpdated, releaseJob, false));
            await(jobUpdated);
            assertThat(jdbc.queryForObject("SELECT plan_code FROM auth.users WHERE id=?", String.class, actor))
                    .as("other connections still see the uncommitted job's old version").isEqualTo("PREMIUM");
            releaseCatalog.countDown();
            assertThat(update.get(8, TimeUnit.SECONDS).name()).isEqualTo(request.name());
            assertThat(releaseJob.getCount()).isEqualTo(1);
            assertThat(job.isDone()).isFalse();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs WHERE actor_id=? AND action='PLAN_UPDATED'",
                    Long.class, actor)).isEqualTo(1);
            releaseJob.countDown();
            assertThat(job.get(8, TimeUnit.SECONDS)).isTrue();
            assertThat(state(actor).planCode()).isEqualTo("FREE");
            assertThat(jdbc.queryForObject("SELECT name FROM learning.plans WHERE code=?", String.class, code))
                    .isEqualTo(request.name());
        } finally {
            releaseCatalog.countDown();
            releaseJob.countDown();
            finish(pool);
        }
    }

    @Test
    void catalogAuditReleasesTheWaitingRenewalBeforeTheJobRechecksTheSameActor() throws Exception {
        long actor = createUser("ADMIN", "LEARNER");
        plan(actor, "PREMIUM", NOW.minusSeconds(1));
        usage(actor, USAGE, MONTH);
        IssuedCard card = card("SUBSCRIPTION", 30, createUser("ADMIN"));
        CountDownLatch catalogLocked = new CountDownLatch(1);
        CountDownLatch releaseCatalog = new CountDownLatch(1);
        CountDownLatch renewalStarted = new CountDownLatch(1);
        CountDownLatch jobStarted = new CountDownLatch(1);
        AtomicInteger adminPid = new AtomicInteger();
        AtomicInteger renewalPid = new AtomicInteger();
        AtomicInteger jobPid = new AtomicInteger();
        var gated = new CatalogGateJdbc(jdbc, () -> { catalogLocked.countDown(); await(releaseCatalog); });
        PlanAdminService plans = new PlanAdminService(auth, gated, mapper, audit, FIXED_CLOCK);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            var update = pool.submit(() -> transaction(() -> asAdmin(actor,
                    () -> plans.updatePlan(PlanCode.PREMIUM, listing("PREMIUM"))), adminPid, null, null, false));
            await(catalogLocked);
            var renewal = pool.submit(() -> transaction(() -> {
                renewalStarted.countDown();
                return renew(actor, card);
            }, renewalPid, null, null, false));
            await(renewalStarted);
            awaitBlockedBy(renewalPid.get(), adminPid.get(), renewal);
            var job = pool.submit(() -> transaction(() -> {
                jobStarted.countDown();
                return runJob();
            }, jobPid, null, null, false));
            await(jobStarted);
            awaitBlockedBy(jobPid.get(), renewalPid.get(), job);
            releaseCatalog.countDown();
            assertThat(update.get(8, TimeUnit.SECONDS).name()).isEqualTo(listing("PREMIUM").name());
            RedeemResult renewed = renewal.get(8, TimeUnit.SECONDS);
            assertThat(renewed.plan().name()).isEqualTo(listing("PREMIUM").name());
            assertThat(job.get(8, TimeUnit.SECONDS)).isTrue();
            assertRenewed(actor, card, renewed);
            assertUnchangedUsage(actor);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs WHERE actor_id=? AND action='PLAN_UPDATED'",
                    Long.class, actor)).isEqualTo(1);
        } finally {
            releaseCatalog.countDown();
            finish(pool);
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void quotaAndCreditConsumptionPreserveUsageAndReceiptsInBothJobOrders(boolean jobFirst, boolean credit)
            throws Exception {
        long learner = user("PREMIUM", NOW.minusSeconds(1));
        usage(learner, Map.of("TRANSLATE", credit ? 10 : 9, "GRADING", 3), MONTH);
        if (credit) topup(learner, 1);
        EntitlementService service = entitlement();
        String key = "consume-" + UUID.randomUUID();
        Supplier<Consumption> consume = () -> service.consume(learner, PaidFeature.TRANSLATE, key);
        Consumption receipt;
        if (jobFirst) receipt = contend(this::runJob, consume, false).second();
        else receipt = contend(consume, this::runJob, false).first();
        assertThat(receipt.source()).isEqualTo(credit ? Source.CREDIT : Source.QUOTA);
        assertThat(receipt.periodStart()).isEqualTo(MONTH);
        assertThat(state(learner).planCode()).isEqualTo("FREE");
        assertThat(state(learner).planExpiresAt()).isNull();
        assertThat(state(learner).freeUsage()).containsExactlyInAnyOrderEntriesOf(Map.of("TRANSLATE", 10, "GRADING", 3));
        assertThat(state(learner).usageResetAt()).isEqualTo(MONTH);
        assertThat(redis.opsForValue().get(idemKey(learner, key))).isEqualTo(jsonText(receipt));
        assertThat(tx(consume)).isEqualTo(receipt);
        if (credit) {
            assertLedger(learner, List.of(1, 2), List.of(1, -1), List.of(0, 1), List.of(1, 0));
            assertThat(ledger.balance(learner)).isZero();
        } else assertThat(rows(learner)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void refundAndDowngradePreserveCompensationAndDuplicateRefundProtection(boolean jobFirst, boolean credit)
            throws Exception {
        long learner = user("PREMIUM", NOW.minusSeconds(1));
        usage(learner, Map.of("TRANSLATE", credit ? 10 : 2, "GRADING", 3), MONTH);
        if (credit) topup(learner, 1);
        EntitlementService service = entitlement();
        String key = "refund-" + UUID.randomUUID();
        Consumption receipt = tx(() -> service.consume(learner, PaidFeature.TRANSLATE, key));
        String markerBefore = redis.opsForValue().get(idemKey(learner, key));
        Supplier<Boolean> refund = () -> { service.refund(learner, receipt); return true; };
        if (jobFirst) contend(this::runJob, refund, false);
        else contend(refund, this::runJob, false);
        var committed = rows(learner);
        tx(refund);
        assertThat(rows(learner)).isEqualTo(committed);
        assertThat(state(learner).planCode()).isEqualTo("FREE");
        assertThat(state(learner).planExpiresAt()).isNull();
        assertThat(state(learner).freeUsage()).containsExactlyInAnyOrderEntriesOf(
                Map.of("TRANSLATE", credit ? 10 : 2, "GRADING", 3));
        assertThat(state(learner).usageResetAt()).isEqualTo(MONTH);
        assertThat(redis.opsForValue().get(idemKey(learner, key))).isEqualTo(markerBefore);
        if (credit) {
            assertLedger(learner, List.of(1, 2, 3), List.of(1, -1, 1), List.of(0, 1, 0), List.of(1, 0, 1));
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM learning.credit_transactions
                    WHERE user_id=? AND tx_type='REFUND' AND ref_transaction_id=?
                    """, Long.class, learner, receipt.transactionId())).isEqualTo(1);
            assertThat(ledger.balance(learner)).isEqualTo(1);
        } else {
            assertThat(committed).isEmpty();
            assertThat(redis.opsForValue().get("billing:refund:" + learner + ":" + key)).isEqualTo("1");
        }
    }

    private long expiredLearner() {
        long id = user("PREMIUM", NOW.minusSeconds(1));
        usage(id, USAGE, MONTH);
        return id;
    }

    private RedeemResult renew(long learner, IssuedCard card) {
        var service = new RedeemService(auth, named, ledger, codes, attempts, FIXED_CLOCK);
        return asLearner(learner, () -> service.redeem(new RedeemRequest(card.raw()), "maintenance-race"));
    }

    private void assertRenewed(long learner, IssuedCard card, RedeemResult result) {
        Instant future = NOW.plusSeconds(30L * 86400);
        assertThat(result.plan().code()).isEqualTo(PlanCode.PREMIUM);
        assertThat(result.plan().expiresAt()).isEqualTo(future);
        assertThat(state(learner).planCode()).isEqualTo("PREMIUM");
        assertThat(state(learner).planExpiresAt()).isEqualTo(future);
        assertThat(jdbc.queryForMap("SELECT status,used_by,used_at FROM learning.credit_cards WHERE id=?", card.id()))
                .containsEntry("status", "USED").containsEntry("used_by", learner).containsEntry("used_at", Timestamp.from(NOW));
        assertThat(jdbc.queryForMap("""
                SELECT tx_type,seq,amount,balance_before,balance_after FROM learning.credit_transactions WHERE card_id=?
                """, card.id())).containsEntry("tx_type", "SUBSCRIPTION").containsEntry("seq", 1)
                .containsEntry("amount", 0).containsEntry("balance_before", 0).containsEntry("balance_after", 0);
        assertThat(rows(learner)).hasSize(1);
    }

    private void assertUnchangedUsage(long learner) {
        assertThat(state(learner).freeUsage()).isEqualTo(USAGE);
        assertThat(state(learner).usageResetAt()).isEqualTo(MONTH);
    }

    private Boolean runJob() { maintenance().downgradeExpiredPlans(); return true; }

    private EntitlementService entitlement() {
        return new EntitlementService(auth, ledger, named, redis, mapper, config, FIXED_CLOCK);
    }

    private void topup(long learner, int amount) {
        tx(() -> { ledger.append(new AppendCommand(learner, "TOPUP", amount, null, null, null, null, null, null, null)); });
    }

    private List<Map<String, Object>> rows(long learner) {
        return jdbc.queryForList("SELECT * FROM learning.credit_transactions WHERE user_id=? ORDER BY seq", learner);
    }

    private void assertLedger(long learner, List<Integer> sequence, List<Integer> amounts,
            List<Integer> before, List<Integer> after) {
        var rows = rows(learner);
        assertThat(rows).extracting(row -> row.get("seq")).containsExactlyElementsOf(sequence);
        assertThat(rows).extracting(row -> row.get("amount")).containsExactlyElementsOf(amounts);
        assertThat(rows).extracting(row -> row.get("balance_before")).containsExactlyElementsOf(before);
        assertThat(rows).extracting(row -> row.get("balance_after")).containsExactlyElementsOf(after);
    }

    private String idemKey(long learner, String key) { return "billing:idem:" + learner + ":" + key; }

    private PlanUpdateRequest listing(String code) {
        return "FREE".equals(code)
                ? new PlanUpdateRequest("Updated free", "Updated maintenance listing", null, null, null, null)
                : new PlanUpdateRequest("Updated premium", "Updated maintenance listing", 45, "180000.00", new Benefits(BENEFITS), false);
    }

    private <A, B> Race<A, B> contend(Supplier<A> firstAction, Supplier<B> secondAction, boolean rollbackFirst)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        AtomicInteger firstPid = new AtomicInteger();
        AtomicInteger secondPid = new AtomicInteger();
        try {
            Future<A> first = pool.submit(() -> transaction(firstAction, firstPid, held, release, rollbackFirst));
            await(held);
            Future<B> second = pool.submit(() -> transaction(() -> {
                started.countDown();
                return secondAction.get();
            }, secondPid, null, null, false));
            await(started);
            awaitBlockedBy(secondPid.get(), firstPid.get(), second);
            release.countDown();
            return new Race<>(first.get(8, TimeUnit.SECONDS), second.get(8, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            finish(pool);
        }
    }

    private <T> T transaction(Supplier<T> action, AtomicInteger pid, CountDownLatch held,
            CountDownLatch release, boolean rollback) {
        TransactionTemplate transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        try {
            return transaction.execute(status -> {
                jdbc.execute("SET LOCAL lock_timeout = '5s'");
                jdbc.execute("SET LOCAL statement_timeout = '8s'");
                jdbc.queryForObject("SELECT set_config('application_name', ?, true)", String.class,
                        "maintenance-race-" + UUID.randomUUID());
                pid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                T result = action.get();
                if (held != null) held.countDown();
                if (release != null) await(release);
                if (rollback) status.setRollbackOnly();
                return result;
            });
        } finally { SecurityContextHolder.clearContext(); }
    }

    private void awaitBlockedBy(int waiter, int holder, Future<?> waiting) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(4).toNanos();
        while (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT ? = ANY(pg_blocking_pids(pid)) AND wait_event_type='Lock'
                FROM pg_stat_activity WHERE pid=?
                """, Boolean.class, holder, waiter))) {
            if (waiting.isDone()) {
                waiting.get(1, TimeUnit.SECONDS);
                throw new AssertionError("Contender completed without the expected PostgreSQL lock wait");
            }
            if (System.nanoTime() >= deadline) throw new AssertionError("Expected PostgreSQL blocker was not observed");
            Thread.yield();
        }
        assertThat(waiting.isDone()).isFalse();
    }

    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(8, TimeUnit.SECONDS)).as("transaction coordination completes").isTrue(); }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Transaction coordination interrupted", exception);
        }
    }

    private static void finish(ExecutorService pool) throws InterruptedException {
        pool.shutdownNow();
        assertThat(pool.awaitTermination(8, TimeUnit.SECONDS)).as("all workers release their transactions").isTrue();
    }

    private record Race<A, B>(A first, B second) {}

    private static final class CatalogGateJdbc extends NamedParameterJdbcTemplate {
        private final Runnable afterCatalogLock;
        CatalogGateJdbc(JdbcTemplate jdbc, Runnable afterCatalogLock) {
            super(jdbc);
            this.afterCatalogLock = afterCatalogLock;
        }
        @Override
        public <T> List<T> query(String sql, Map<String, ?> parameters, RowMapper<T> rowMapper) {
            List<T> rows = super.query(sql, parameters, rowMapper);
            if (sql.contains("FOR NO KEY UPDATE") && !rows.isEmpty()) afterCatalogLock.run();
            return rows;
        }
    }
}
