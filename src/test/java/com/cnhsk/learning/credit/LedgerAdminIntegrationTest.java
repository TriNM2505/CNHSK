package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.shared.audit.AuditService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class LedgerAdminIntegrationTest extends LedgerAdminIntegrationSupport {
    @Autowired private LedgerQueryService ledger;
    @Autowired private LedgerService writer;
    @Autowired private ReconciliationService reconciliation;
    @Autowired private EntityManager entityManager;
    @MockitoSpyBean private AuditService auditService;
    @MockitoSpyBean private NamedParameterJdbcTemplate named;
    @MockitoSpyBean private AuthApi auth;
    @MockitoSpyBean private LedgerCsvWriter csvWriter;

    @Test
    void listUsesVietnamInclusiveBoundsAndCreatedAtThenIdOrderAndTotalsAcrossEveryPage() throws Exception {
        long first = newUser(0);
        long second = newUser(0);
        insertLedger(first, 1, "TOPUP", 5, 0, 5, null, null, AT.minusSeconds(1));
        long oldest = insertLedger(first, 2, "TOPUP", 1_500_000_000, 5, 1_500_000_005, "LEGACY_FEATURE", "Safe reason", AT);
        long middle = insertLedger(second, 1, "TOPUP", 1_500_000_000, 0, 1_500_000_000, null, null, AT);
        long newest = insertLedger(second, 2, "DEDUCT", -7, 1_500_000_000, 1_499_999_993, null, null, AT.plusSeconds(86399));
        insertLedger(second, 3, "TOPUP", 1, 1_499_999_993, 1_499_999_994, null, null, AT.plusSeconds(86400));
        BusinessSnapshot before = snapshot();
        JsonNode page = json(filtered("").param("size", "2"), 200);
        assertThat(page.path("page").asInt()).isZero();
        assertThat(page.path("size").asInt()).isEqualTo(2);
        assertThat(page.path("total").asLong()).isEqualTo(3);
        assertThat(page.path("items").findValuesAsText("id")).containsExactly(Long.toString(newest), Long.toString(middle));
        assertTotals(page, 3, 3_000_000_000L, -7);
        JsonNode next = json(filtered("").param("page", "1").param("size", "2"), 200);
        assertThat(next.path("items").get(0).path("id").asLong()).isEqualTo(oldest);
        assertThat(next.path("items").get(0).path("featureCode").asText()).isEqualTo("LEGACY_FEATURE");
        assertTotals(next, 3, 3_000_000_000L, -7);
        JsonNode distant = json(filtered("").param("page", "2147483647").param("size", "100"), 200);
        assertThat(distant.path("items").isEmpty()).isTrue();
        assertTotals(distant, 3, 3_000_000_000L, -7);
        JsonNode debit = json(filtered("").param("type", "DEDUCT"), 200);
        assertTotals(debit, 1, 0, -7);
        assertThat(debit.path("items").get(0).path("amount").asInt()).isEqualTo(-7);
        assertThat(snapshot()).isEqualTo(before);
        assertThat(auditCount()).isEqualTo(4);
        JsonNode detail = audit("LEDGER_READ");
        assertThat(detail.size()).isEqualTo(3);
        assertThat(detail.path("from").asText()).isEqualTo("2031-11-15");
        assertThat(detail.path("to").asText()).isEqualTo("2031-11-15");
        assertThat(detail.path("type").asText()).isEqualTo("DEDUCT");
    }

    @Test
    void emptyListAndExportReturnRealShapesAndAuditEachSuccessfulRead() throws Exception {
        BusinessSnapshot before = snapshot();
        JsonNode page = json(filtered(""), 200);
        assertThat(page.path("items").isEmpty()).isTrue();
        assertThat(page.path("size").asInt()).isEqualTo(20);
        assertTotals(page, 0, 0, 0);
        assertThat(audit("LEDGER_READ").path("type").isNull()).isTrue();
        var result = response(filtered("/export"), 200).getResponse();
        assertThat(result.getContentType()).startsWith("text/csv");
        assertThat(records(result.getContentAsString(StandardCharsets.UTF_8))).isEmpty();
        assertThat(audit("LEDGER_EXPORT").path("rows").asLong()).isZero();
        assertThat(auditCount()).isEqualTo(2);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void invalidDatesTypesAndPaginationReturnJson422EvenWithCsvAcceptAndNeverAudit() throws Exception {
        List<Map<String, String>> invalid = List.of(Map.of("to", "2031-11-15"), Map.of("from", "2031-11-15"),
                Map.of("from", "", "to", "2031-11-15"), Map.of("from", "2031-02-29", "to", "2031-03-01"),
                Map.of("from", "2031-11-15T00:00:00Z", "to", "2031-11-15"),
                Map.of("from", "2031-11-16", "to", "2031-11-15"), Map.of("from", "2031-01-01", "to", "2031-04-04"),
                Map.of("from", "2031-11-15", "to", "2031-11-15", "type", ""),
                Map.of("from", "2031-11-15", "to", "2031-11-15", "type", "PRIVATE_UNKNOWN"));
        BusinessSnapshot before = snapshot();
        for (String suffix : List.of("", "/export")) for (Map<String, String> parameters : invalid) {
            var request = get(BASE + suffix).header("Authorization", bearer()).accept("text/csv");
            parameters.forEach(request::param);
            var result = response(request, 422).getResponse();
            assertThat(result.getContentType()).startsWith("application/json");
            JsonNode error = mapper.readTree(result.getContentAsByteArray());
            assertThat(error.path("error_code").asText()).isEqualTo("VALIDATION_FAILED");
            assertThat(error.path("request_id").asText()).isNotBlank();
            assertThat(error.toString()).doesNotContain("PRIVATE_UNKNOWN", "stackTrace", "Exception");
        }
        for (String page : List.of("-1", "word", "2147483648")) json(filtered("").param("page", page), 422);
        for (String size : List.of("0", "101", "word", "2147483648")) json(filtered("").param("size", size), 422);
        assertThat(auditCount()).isZero();
        assertThat(snapshot()).isEqualTo(before);
        json(get(BASE).header("Authorization", bearer()).param("from", "2031-01-01").param("to", "2031-04-03"), 200);
        assertThat(auditCount()).isEqualTo(1);
    }

    @Test
    void allTransactionTypesAndZeroAmountsUseTheSameDateAndTypeProjectionForCsvAndJson() throws Exception {
        long user = newUser(0);
        String[] types = {"TOPUP", "DEDUCT", "REFUND", "ADJUSTMENT", "TEACHER_PAYOUT", "SUBSCRIPTION"};
        for (int index = 0; index < types.length; index++) insertLedger(user, index + 1, types[index], 0, 0, 0, null, "Zero transaction", AT);
        BusinessSnapshot before = snapshot();
        for (String type : types) {
            JsonNode page = json(filtered("").param("type", type).param("size", "1"), 200);
            assertTotals(page, 1, 0, 0);
            assertThat(page.path("items").get(0).path("type").asText()).isEqualTo(type);
            List<CSVRecord> csv = records(response(filtered("/export").param("type", type), 200).getResponse().getContentAsString());
            assertThat(csv).hasSize(1);
            assertThat(csv.get(0).get("type")).isEqualTo(type);
            assertThat(csv.get(0).get("amount")).isEqualTo("0");
        }
        assertThat(auditCount()).isEqualTo(12);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void csvRoundTripsUtf8QuotesNewlinesAndEscapesEveryDangerousRenderedCellIncludingNegativeNumbers() throws Exception {
        long user = newUser(0);
        insertLedger(user, 1, "TOPUP", 20, 0, 20, "=SUM(1)", "Tiếng Việt, 中文 \"quoted\"\r\nnext", AT);
        insertLedger(user, 2, "DEDUCT", -1, 20, 19, "+formula", "@SUM(1)", AT.plusSeconds(1));
        BusinessSnapshot before = snapshot();
        var result = response(filtered("/export"), 200).getResponse();
        byte[] bytes = result.getContentAsByteArray();
        assertThat(bytes.length).isGreaterThan(3);
        assertThat(bytes[0]).isNotEqualTo((byte) 0xef);
        String text = new String(bytes, StandardCharsets.UTF_8);
        assertThat(text).startsWith("id,userId,maskedEmail,seq,type,amount,balanceBefore,balanceAfter,featureCode,reason,createdAt\r\n");
        assertThat(result.getHeader("Content-Disposition")).contains("attachment", ".csv").doesNotContain("2031-11-15/");
        List<CSVRecord> csv = records(text);
        assertThat(csv).hasSize(2).allSatisfy(row -> assertThat(row.size()).isEqualTo(11));
        assertThat(csv.get(0).get("amount")).isEqualTo("'-1");
        assertThat(csv.get(0).get("featureCode")).isEqualTo("'+formula");
        assertThat(csv.get(0).get("reason")).isEqualTo("'@SUM(1)");
        assertThat(csv.get(1).get("featureCode")).isEqualTo("'=SUM(1)");
        assertThat(csv.get(1).get("reason")).isEqualTo("Tiếng Việt, 中文 \"quoted\"\r\nnext");
        assertThat(csv.get(1).get("createdAt")).isEqualTo(AT.toString());
        assertThat(snapshot()).isEqualTo(before);
        assertThat(audit("LEDGER_EXPORT").path("rows").asLong()).isEqualTo(2);
    }

    @Test
    void sensitiveLegacyMetadataIsNullOnlyInProjectionAndEmailComesFromOneAuthBatch() throws Exception {
        long user = newUser(0);
        String email = jdbc.queryForObject("SELECT email FROM auth.users WHERE id=?", String.class, user);
        String hash = "a".repeat(64);
        insertLedger(user, 1, "TOPUP", 1, 0, 1, "person@example.test", "card_hash=" + hash, AT);
        insertLedger(user, 2, "TOPUP", 1, 1, 2, "LEGACY_FEATURE", "Bearer " + bearer().substring(7), AT.plusSeconds(1));
        BusinessSnapshot before = snapshot();
        long masks = maskCalls();
        JsonNode result = json(filtered(""), 200);
        assertThat(maskCalls() - masks).isEqualTo(1);
        assertThat(result.toString()).doesNotContain(email, hash, "person@example.test", "Bearer ");
        assertThat(result.path("items").get(0).path("reason").isNull()).isTrue();
        assertThat(result.path("items").get(1).path("featureCode").isNull()).isTrue();
        assertThat(result.path("items").get(1).path("reason").isNull()).isTrue();
        masks = maskCalls();
        String csv = response(filtered("/export"), 200).getResponse().getContentAsString();
        assertThat(maskCalls() - masks).isEqualTo(1);
        assertThat(csv).doesNotContain(email, hash, "person@example.test", "Bearer ");
        assertThat(audit("LEDGER_READ").toString()).doesNotContain(email, hash);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void missingAuthMaskFailsClosedAndCannotCommitASuccessAudit() throws Exception {
        newUser(1);
        BusinessSnapshot before = snapshot();
        AuthApi target = AopTestUtils.getUltimateTargetObject(auth);
        doAnswer(invocation -> Map.of()).when(target).maskedEmails(anySet());
        json(filtered(""), 500);
        json(filtered("/export").accept("text/csv"), 500);
        assertThat(auditCount()).isZero();
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void exportAcceptsTenThousandRowsAndRejectsTenThousandAndOneWithoutTruncationOrAudit() throws Exception {
        long user = newUser(0);
        seedRows(user, 10_000);
        BusinessSnapshot before = snapshot();
        long queries = ledgerQueries();
        long masks = maskCalls();
        assertThat(records(response(filtered("/export"), 200).getResponse().getContentAsString())).hasSize(10_000);
        assertThat(ledgerQueries() - queries).isEqualTo(1);
        assertThat(maskCalls() - masks).isEqualTo(1);
        assertThat(snapshot()).isEqualTo(before);
        assertThat(audit("LEDGER_EXPORT").path("rows").asLong()).isEqualTo(10_000);
        insertLedger(user, 10_001, "TOPUP", 1, 10_000, 10_001, null, null, AT);
        before = snapshot();
        long audits = auditCount();
        queries = ledgerQueries();
        var error = json(filtered("/export").accept("text/csv"), 422);
        assertThat(error.path("error_code").asText()).isEqualTo("EXPORT_TOO_LARGE");
        assertThat(ledgerQueries() - queries).isEqualTo(1);
        assertThat(auditCount()).isEqualTo(audits);
        assertThat(snapshot()).isEqualTo(before);
        assertThat(json(filtered(""), 200).path("total").asLong()).isEqualTo(10_001);
    }

    @Test
    void listUsesExactlyTwoRealLedgerQueriesAndOneMaskBatchForThePage() throws Exception {
        for (int index = 0; index < 30; index++) newUser(1);
        long queries = ledgerQueries();
        long masks = maskCalls();
        JsonNode result = json(filtered("").param("size", "20"), 200);
        assertThat(result.path("items").size()).isEqualTo(20);
        assertThat(result.path("total").asLong()).isEqualTo(30);
        assertThat(ledgerQueries() - queries).isEqualTo(2);
        assertThat(maskCalls() - masks).isEqualTo(1);
        assertThat(maskBatchSizes()).allSatisfy(size -> assertThat(size).isBetween(1, 20));
    }

    @Test
    void pageAndTotalsKeepOneRepeatableReadSnapshotWhenAppendCommitsBetweenTheQueries() throws Exception {
        Instant now = Instant.now();
        String date = LocalDate.ofInstant(now, ZoneId.of("Asia/Ho_Chi_Minh")).toString();
        long user = newUser(0);
        insertLedger(user, 1, "TOPUP", 10, 0, 10, null, null, now);
        CountDownLatch selected = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean intercept = new AtomicBoolean(true);
        NamedParameterJdbcTemplate target = AopTestUtils.getUltimateTargetObject(named);
        doAnswer(invocation -> {
            Object value = invocation.callRealMethod();
            if (((String) invocation.getArgument(0)).contains("learning.credit_transactions") && intercept.getAndSet(false)) {
                assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
                assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()).isEqualTo(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
                selected.countDown();
                await(release);
            }
            return value;
        }).when(target).queryForObject(anyString(), org.mockito.ArgumentMatchers.<SqlParameterSource>any(),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any());
        var pool = Executors.newFixedThreadPool(2);
        String token = bearer();
        try {
            var read = pool.submit(() -> json(get(BASE).header("Authorization", token).param("from", date).param("to", date), 200));
            await(selected);
            var append = pool.submit(() -> new TransactionTemplate(manager).execute(status -> {
                jdbc.execute("SET LOCAL lock_timeout = '5s'");
                return writer.append(new LedgerService.AppendCommand(user, "TOPUP", 20, null, null, null, null, null, null, null));
            }));
            assertThat(append.get(15, TimeUnit.SECONDS).balanceAfter()).isEqualTo(30);
            release.countDown();
            JsonNode result = read.get(15, TimeUnit.SECONDS);
            assertTotals(result, 1, 10, 0);
            assertThat(result.path("items").size()).isEqualTo(1);
            assertTotals(json(get(BASE).header("Authorization", token).param("from", date).param("to", date), 200), 2, 30, 0);
            assertThat(auditCount()).isEqualTo(2);
        } finally {
            release.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/export", "/reconciliation"})
    void auditInsertIsReallyFlushedThenFailureRollsBackTheReadTransaction(String suffix) throws Exception {
        newUser(1);
        BusinessSnapshot before = snapshot();
        AuditService target = AopTestUtils.getUltimateTargetObject(auditService);
        AtomicBoolean flushed = new AtomicBoolean();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            entityManager.flush();
            assertThat(auditCount()).isEqualTo(1);
            flushed.set(true);
            throw new IllegalStateException("synthetic-private-audit-failure");
        }).when(target).recordDetail(eq(admin), anyString(), eq("CREDIT_LEDGER"), anyMap());
        var result = response(filtered(suffix).accept(suffix.equals("/export") ? "text/csv" : "application/json"), 500).getResponse();
        assertThat(result.getContentType()).startsWith("application/json");
        JsonNode error = mapper.readTree(result.getContentAsByteArray());
        assertThat(error.path("error_code").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(error.toString()).doesNotContain("synthetic-private-audit-failure", "IllegalStateException", "stackTrace");
        assertThat(flushed).isTrue();
        assertThat(auditCount()).isZero();
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void csvSerializationFailureReturnsJsonAndRollsBackAudit() throws Exception {
        newUser(1);
        BusinessSnapshot before = snapshot();
        doThrow(new IllegalStateException("synthetic-private-csv-failure")).when(csvWriter).write(anyList());
        JsonNode error = json(filtered("/export").accept("text/csv"), 500);
        assertThat(error.path("error_code").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(error.toString()).doesNotContain("synthetic-private-csv-failure", "Exception");
        assertThat(auditCount()).isZero();
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void readOnlyOuterTransactionsCannotSilentlyDiscardTheMandatoryAudit() {
        newUser(1);
        BusinessSnapshot before = snapshot();
        principal();
        TransactionTemplate transaction = new TransactionTemplate(manager);
        transaction.setReadOnly(true);
        LedgerFilter filter = LedgerFilter.parse("2031-11-15", "2031-11-15", null);
        assertThatThrownBy(() -> transaction.execute(status -> ledger.list(filter, 0, 20))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> transaction.execute(status -> ledger.export(filter))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> transaction.execute(status -> reconciliation.reconcile())).isInstanceOf(RuntimeException.class);
        assertThat(auditCount()).isZero();
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void realCookieAndBearerAdminGetsNeedNoCsrfAndDeniedRequestsNeverAudit() throws Exception {
        long learner = createUser("Denied learner", "LEARNER");
        for (String suffix : List.of("", "/export", "/reconciliation")) {
            response(get(BASE + suffix), 401);
            response(get(BASE + suffix).header("Authorization", bearer(learner, "LEARNER")), 403);
        }
        assertThat(auditCount()).isZero();
        Cookie cookie = new Cookie("access_token", jwt.issueAccessToken(admin, List.of("ADMIN"), true, UUID.randomUUID()));
        for (String suffix : List.of("", "/export", "/reconciliation")) {
            response(filtered(suffix), 200);
            response(get(BASE + suffix).cookie(cookie).param("from", "2031-11-15").param("to", "2031-11-15"), 200);
        }
        assertThat(auditCount()).isEqualTo(6);
    }

    private void seedRows(long user, int rows) {
        jdbc.update("""
                INSERT INTO learning.credit_transactions(user_id,seq,tx_type,amount,balance_before,balance_after,created_at)
                SELECT ?,seq,'TOPUP',1,seq-1,seq,? FROM generate_series(1,?) AS seq
                """, user, Timestamp.from(AT), rows);
    }
    private static List<CSVRecord> records(String csv) throws Exception {
        try (var parser = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).build().parse(new StringReader(csv))) {
            return parser.getRecords();
        }
    }
    private static void assertTotals(JsonNode page, long count, long credited, long debited) {
        assertThat(page.path("total").asLong()).isEqualTo(count);
        assertThat(page.path("totals").path("count").asLong()).isEqualTo(count);
        assertThat(page.path("totals").path("credited").asLong()).isEqualTo(credited);
        assertThat(page.path("totals").path("debited").asLong()).isEqualTo(debited);
    }
    private long ledgerQueries() {
        return mockingDetails(AopTestUtils.getUltimateTargetObject(named)).getInvocations().stream()
                .filter(call -> (call.getMethod().getName().equals("query") || call.getMethod().getName().equals("queryForObject"))
                        && call.getArguments().length == 3 && call.getArgument(1) instanceof SqlParameterSource
                        && call.getArgument(2) instanceof RowMapper<?> && call.getArgument(0) instanceof String sql
                        && sql.contains("learning.credit_transactions")).count();
    }
    private long maskCalls() { return maskBatchSizes().size(); }
    private List<Integer> maskBatchSizes() {
        List<Integer> sizes = new ArrayList<>();
        mockingDetails(AopTestUtils.getUltimateTargetObject(auth)).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("maskedEmails"))
                .forEach(call -> sizes.add(((java.util.Set<?>) call.getArgument(0)).size()));
        return sizes;
    }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted while coordinating ledger transactions.", exception); }
    }
}
