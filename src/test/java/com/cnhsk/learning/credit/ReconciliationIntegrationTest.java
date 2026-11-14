package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.credit.LedgerService.AppendCommand;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class ReconciliationIntegrationTest extends LedgerAdminIntegrationSupport {
    @Autowired private LedgerService writer;
    @MockitoSpyBean private NamedParameterJdbcTemplate named;
    @MockitoSpyBean private AuthApi auth;

    @Test
    void emptyGlobalLedgerHasZeroSummaryAndDoesNotCountUsersWithoutTransactions() throws Exception {
        newUser(0);
        BusinessSnapshot before = snapshot();
        long queries = ledgerQueries();
        JsonNode result = reconcile();
        assertSummary(result, 0, 0, 0, 0);
        assertThat(result.path("issues").isEmpty()).isTrue();
        assertThat(ledgerQueries() - queries).isEqualTo(1);
        assertThat(maskBatches()).isEmpty();
        assertAudit(result);
        assertThat(auditCount()).isEqualTo(1);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void cleanGlobalSummaryUsesLatestSequenceRatherThanIdOrTimestampAndLongAggregates() throws Exception {
        long first = newUser(0);
        long second = newUser(0);
        insertLedger(first, 2, "TOPUP", 500_000_000, 1_000_000_000, 1_500_000_000, null, null, AT.minusSeconds(86400));
        insertLedger(first, 1, "TOPUP", 1_000_000_000, 0, 1_000_000_000, null, null, AT.plusSeconds(86400));
        insertLedger(second, 1, "TOPUP", 1_500_000_000, 0, 1_500_000_000, null, null, AT);
        BusinessSnapshot before = snapshot();
        JsonNode result = json(get(BASE + "/reconciliation").header("Authorization", bearer())
                .param("from", "1900-01-01").param("to", "1900-01-01").param("type", "DEDUCT"), 200);
        assertSummary(result, 2, 3, 3_000_000_000L, 3_000_000_000L);
        assertThat(result.path("issues").isEmpty()).isTrue();
        assertAudit(result);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void eachBrokenRuleProducesItsOwnIssueInDeterministicUserSequenceAndEnumOrder() throws Exception {
        long first = newUser(0);
        long second = newUser(0);
        // All fixtures keep positive sequences and the per-row arithmetic CHECK enabled.
        insertLedger(first, 2, "TOPUP", 5, 10, 15, null, null, AT);
        insertLedger(first, 4, "TOPUP", 1, 16, 17, null, null, AT);
        insertLedger(second, 1, "TOPUP", 1, 0, 1, null, null, AT);
        insertLedger(second, 2, "TOPUP", 1, 3, 4, null, null, AT);
        BusinessSnapshot before = snapshot();
        JsonNode result = reconcile();
        assertSummary(result, 2, 4, 21, 8);
        assertThat(issueKeys(result)).containsExactly(first + ":2:SEQ_GAP", first + ":2:FIRST_BALANCE_NOT_ZERO",
                first + ":4:SEQ_GAP", first + ":4:BALANCE_CHAIN_BROKEN", second + ":2:BALANCE_CHAIN_BROKEN");
        assertThat(result.path("issues").findValuesAsText("maskedEmail")).allSatisfy(email -> assertThat(email).contains("\u2022\u2022\u2022"));
        assertAudit(result);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void firstGapWithZeroBeforeAndFirstNonzeroWithSequenceOneAreIndependentRules() throws Exception {
        long gap = newUser(0);
        long nonzero = newUser(0);
        insertLedger(gap, 2, "TOPUP", 1, 0, 1, null, null, AT);
        insertLedger(nonzero, 1, "TOPUP", 1, 2, 3, null, null, AT);
        JsonNode result = reconcile();
        assertThat(issueKeys(result)).containsExactly(gap + ":2:SEQ_GAP", nonzero + ":1:FIRST_BALANCE_NOT_ZERO");
    }

    @Test
    void opposingBrokenChainsCannotHideBehindEqualGlobalAmountAndBalanceTotals() throws Exception {
        long first = newUser(10);
        long second = newUser(10);
        insertLedger(first, 2, "TOPUP", 1, 12, 13, null, null, AT);
        insertLedger(second, 2, "TOPUP", 1, 8, 9, null, null, AT);
        BusinessSnapshot before = snapshot();
        JsonNode result = reconcile();
        assertSummary(result, 2, 4, 22, 22);
        assertThat(issueKeys(result)).containsExactly(first + ":2:BALANCE_CHAIN_BROKEN", second + ":2:BALANCE_CHAIN_BROKEN");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void manyCorruptUsersUseMaskBatchesOfAtMostOneThousandAndOneLedgerWindowStatement() throws Exception {
        List<Object[]> rows = new ArrayList<>();
        for (int index = 0; index < 1001; index++) rows.add(new Object[] {newUser(0), Timestamp.from(AT)});
        jdbc.batchUpdate("""
                INSERT INTO learning.credit_transactions(user_id,seq,tx_type,amount,balance_before,balance_after,created_at)
                VALUES (?,2,'TOPUP',1,0,1,?)
                """, rows);
        long queries = ledgerQueries();
        JsonNode result = reconcile();
        assertSummary(result, 1001, 1001, 1001, 1001);
        assertThat(result.path("issues").size()).isEqualTo(1001);
        assertThat(maskBatches()).containsExactly(1000, 1);
        assertThat(ledgerQueries() - queries).isEqualTo(1);
        assertThat(ledgerSql()).allSatisfy(sql -> assertThat(sql.toLowerCase(java.util.Locale.ROOT)).contains("lag(", "partition by user_id", "order by seq"));
        assertAudit(result);
    }

    @Test
    void missingIssueEmailMaskFailsClosedWithoutAnAuditOrBusinessWrite() throws Exception {
        long user = newUser(0);
        insertLedger(user, 2, "TOPUP", 1, 0, 1, null, null, AT);
        BusinessSnapshot before = snapshot();
        AuthApi target = AopTestUtils.getUltimateTargetObject(auth);
        doAnswer(invocation -> Map.of()).when(target).maskedEmails(anySet());
        JsonNode result = json(get(BASE + "/reconciliation").header("Authorization", bearer()), 500);
        assertThat(result.path("error_code").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(result.toString()).doesNotContain("Exception", "stackTrace", "Masked ledger identity");
        assertThat(auditCount()).isZero();
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void reconciliationKeepsItsSnapshotWhenAWriterCommitsAfterTheRealWindowQuery() throws Exception {
        long user = newUser(10);
        CountDownLatch selected = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean intercept = new AtomicBoolean(true);
        NamedParameterJdbcTemplate target = AopTestUtils.getUltimateTargetObject(named);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (((String) invocation.getArgument(0)).contains("learning.credit_transactions") && intercept.getAndSet(false)) {
                assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
                assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()).isEqualTo(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
                selected.countDown();
                await(release);
            }
            return result;
        }).when(target).query(anyString(), org.mockito.ArgumentMatchers.<Map<String, ?>>any(),
                org.mockito.ArgumentMatchers.<ResultSetExtractor<Object>>any());
        var pool = Executors.newFixedThreadPool(2);
        try {
            var read = pool.submit(this::reconcile);
            await(selected);
            var append = pool.submit(() -> new TransactionTemplate(manager).execute(status -> {
                jdbc.execute("SET LOCAL lock_timeout = '5s'");
                return writer.append(new AppendCommand(user, "TOPUP", 20, null, null, null, null, null, null, null));
            }));
            assertThat(append.get(15, TimeUnit.SECONDS).balanceAfter()).isEqualTo(30);
            release.countDown();
            JsonNode result = read.get(15, TimeUnit.SECONDS);
            assertSummary(result, 1, 1, 10, 10);
            assertThat(result.path("issues").isEmpty()).isTrue();
            assertThat(reconcile().path("totalBalance").asLong()).isEqualTo(30);
        } finally {
            release.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void sameActorNoKeyUpdateLockDoesNotDeadlockTheAuditForeignKey() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var lock = pool.submit(() -> new TransactionTemplate(manager).execute(status -> {
                jdbc.execute("SET LOCAL lock_timeout = '5s'");
                jdbc.queryForObject("SELECT id FROM auth.users WHERE id=? FOR NO KEY UPDATE", Long.class, admin);
                locked.countDown();
                await(release);
                return null;
            }));
            await(locked);
            var read = pool.submit(this::reconcile);
            assertSummary(read.get(15, TimeUnit.SECONDS), 0, 0, 0, 0);
            assertThat(auditCount()).isEqualTo(1);
            release.countDown();
            lock.get(15, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void ledgerReconciliation100kMeasuresP95() throws Exception {
        List<Object[]> seeds = new ArrayList<>();
        for (int user = 0; user < 1000; user++) seeds.add(new Object[] {newUser(0), Timestamp.from(AT)});
        jdbc.batchUpdate("""
                INSERT INTO learning.credit_transactions(user_id,seq,tx_type,amount,balance_before,balance_after,created_at)
                SELECT ?,seq,'TOPUP',1,seq-1,seq,? FROM generate_series(1,100) AS seq
                """, seeds);
        jdbc.execute("ANALYZE learning.credit_transactions");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.credit_transactions", Long.class)).isEqualTo(100_000);
        Map<String, Object> before = ledgerFingerprint();
        List<Map<String, Object>> usersBefore = jdbc.queryForList("SELECT * FROM auth.users ORDER BY id");
        List<Map<String, Object>> plansBefore = jdbc.queryForList("SELECT * FROM learning.plans ORDER BY code");
        List<Map<String, Object>> cardsBefore = jdbc.queryForList("SELECT * FROM learning.credit_cards ORDER BY id");
        String token = bearer();
        for (int warmup = 0; warmup < 5; warmup++) {
            assertSummary(json(get(BASE + "/reconciliation").header("Authorization", token), 200), 1000, 100_000, 100_000, 100_000);
        }
        List<Long> samples = new ArrayList<>();
        List<Long> queryCounts = new ArrayList<>();
        List<Integer> statuses = new ArrayList<>();
        for (int sample = 0; sample < 20; sample++) {
            long count = ledgerQueries();
            long started = System.nanoTime();
            var response = mvc.perform(get(BASE + "/reconciliation").header("Authorization", token)).andReturn().getResponse();
            samples.add(System.nanoTime() - started);
            queryCounts.add(ledgerQueries() - count);
            statuses.add(response.getStatus());
            JsonNode result = mapper.readTree(response.getContentAsByteArray());
            assertSummary(result, 1000, 100_000, 100_000, 100_000);
            assertThat(result.path("issues").isEmpty()).isTrue();
        }
        long[] sorted = samples.stream().mapToLong(Long::longValue).sorted().toArray();
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("method", "ledgerReconciliation100kMeasuresP95");
        evidence.put("endpoint", "GET " + BASE + "/reconciliation");
        evidence.put("database", jdbc.queryForMap("SELECT current_database() AS name,version() AS version"));
        evidence.put("rows", 100_000); evidence.put("users", 1000); evidence.put("warmups", 5);
        evidence.put("samplesNanoseconds", samples); evidence.put("p50Nanoseconds", sorted[9]);
        evidence.put("p95Nanoseconds", sorted[18]); evidence.put("maxNanoseconds", sorted[19]);
        evidence.put("ledgerQueryCounts", queryCounts); evidence.put("statuses", statuses);
        evidence.put("auditRows", auditCount()); evidence.put("referenceBudgetNanoseconds", 5_000_000_000L);
        evidence.put("timingScope", "MockMvc authentication, service, audit commit, serialization; setup and warmups excluded");
        Path directory = Path.of(System.getProperty("java.io.tmpdir"), "t11-ledger-evidence");
        Files.createDirectories(directory);
        Path artifact = directory.resolve("ledger-reconciliation-100k-" + UUID.randomUUID() + ".json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(artifact.toFile(), evidence);
        System.out.println("T11 ledgerReconciliation100kMeasuresP95 evidence=" + artifact + " samplesNanoseconds=" + samples
                + " p50=" + sorted[9] + " p95=" + sorted[18] + " max=" + sorted[19]);
        assertThat(samples).hasSize(20).allSatisfy(value -> assertThat(value).isPositive());
        assertThat(statuses).containsOnly(200);
        assertThat(queryCounts).containsOnly(1L);
        assertThat(auditCount()).isEqualTo(25);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs WHERE actor_id=? AND action='LEDGER_RECONCILE' AND target_type='CREDIT_LEDGER' AND target_id IS NULL", Long.class, admin)).isEqualTo(25);
        JsonNode detail = audit("LEDGER_RECONCILE");
        assertThat(detail.path("users").asLong()).isEqualTo(1000);
        assertThat(detail.path("rows").asLong()).isEqualTo(100_000);
        assertThat(detail.path("totalBalance").asLong()).isEqualTo(100_000);
        assertThat(detail.path("totalAmount").asLong()).isEqualTo(100_000);
        assertThat(detail.path("issueCount").asLong()).isZero();
        assertThat(ledgerFingerprint()).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM auth.users ORDER BY id")).isEqualTo(usersBefore);
        assertThat(jdbc.queryForList("SELECT * FROM learning.plans ORDER BY code")).isEqualTo(plansBefore);
        assertThat(jdbc.queryForList("SELECT * FROM learning.credit_cards ORDER BY id")).isEqualTo(cardsBefore);
    }

    private JsonNode reconcile() throws Exception { return json(get(BASE + "/reconciliation").header("Authorization", bearer()), 200); }
    private static void assertSummary(JsonNode result, long users, long rows, long balance, long amount) {
        assertThat(result.path("users").asLong()).isEqualTo(users);
        assertThat(result.path("rows").asLong()).isEqualTo(rows);
        assertThat(result.path("totalBalance").asLong()).isEqualTo(balance);
        assertThat(result.path("totalAmount").asLong()).isEqualTo(amount);
    }
    private void assertAudit(JsonNode result) throws Exception {
        JsonNode detail = audit("LEDGER_RECONCILE");
        assertThat(detail.size()).isEqualTo(5);
        for (String field : List.of("users", "rows", "totalBalance", "totalAmount")) assertThat(detail.path(field)).isEqualTo(result.path(field));
        assertThat(detail.path("issueCount").asLong()).isEqualTo(result.path("issues").size());
    }
    private static List<String> issueKeys(JsonNode result) {
        List<String> values = new ArrayList<>();
        result.path("issues").forEach(issue -> values.add(issue.path("userId").asLong() + ":" + issue.path("seq").asInt() + ":" + issue.path("kind").asText()));
        return values;
    }
    private List<Integer> maskBatches() {
        return mockingDetails(AopTestUtils.getUltimateTargetObject(auth)).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("maskedEmails"))
                .map(call -> ((Set<?>) call.getArgument(0)).size()).toList();
    }
    private List<String> ledgerSql() {
        return mockingDetails(AopTestUtils.getUltimateTargetObject(named)).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("query") && call.getArguments().length == 3
                        && call.getArgument(1) instanceof Map<?, ?> && call.getArgument(0) instanceof String sql
                        && sql.contains("learning.credit_transactions"))
                .map(call -> (String) call.getArgument(0)).toList();
    }
    private long ledgerQueries() { return ledgerSql().size(); }
    private Map<String, Object> ledgerFingerprint() {
        return jdbc.queryForMap("SELECT count(*) AS rows,md5(string_agg(to_jsonb(t)::text,',' ORDER BY id)) AS digest FROM learning.credit_transactions t");
    }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted while coordinating ledger transactions.", exception); }
    }
}
