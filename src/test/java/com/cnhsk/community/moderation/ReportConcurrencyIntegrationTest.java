package com.cnhsk.community.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cnhsk.community.reaction.ReactionRepository;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Committed fixtures, real repository SQL, and independently committed HTTP transactions. */
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.minimum-idle=4",
        "spring.datasource.hikari.connection-timeout=20000"
})
class ReportConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Pattern VERIFY_LINK = Pattern.compile("/verify-email\\?token=([A-Za-z0-9_-]+)");
    private static final String INSERT_REPORT = """
            INSERT INTO community.reactions(user_id, target_type, target_id, kind, created_at)
            VALUES (?, ?, ?, 'REPORT', ?)
            """;
    private static final String UPSERT_CASE = """
            INSERT INTO community.moderation_cases(target_type, target_id, reasons, created_at)
            VALUES (?, ?, CAST(? AS jsonb), ?)
            ON CONFLICT (target_type, target_id) WHERE status='PENDING'
            DO UPDATE SET report_count=community.moderation_cases.report_count+1,
                          reasons=community.moderation_cases.reasons || EXCLUDED.reasons
            """;

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Value("${test.mailpit.base-url}") private String mailpitBaseUrl;
    @PersistenceContext private EntityManager entityManager;
    @MockitoSpyBean private ReactionRepository reactions;
    @MockitoSpyBean private ModerationCaseRepository cases;

    @Test
    void FR036_FR038_threeVerifiedReportersOfOneCommentCreateOneCaseWithThreeEntries() throws Exception {
        concurrentReporters("COMMENT", 3);
    }

    @Test
    void FR036_FR037_tenConcurrentReportersDoNotHideApprovedPostOrLoseEntries() throws Exception {
        concurrentReporters("POST", 10);
    }

    private void concurrentReporters(String type, int count) throws Exception {
        Fixture fixture = fixture(type);
        List<Learner> reporters = new ArrayList<>();
        for (int index = 0; index < count; index++) reporters.add(registerVerifiedLearner());
        Map<String, Object> before = targetState(fixture);
        CountDownLatch atUpsert = new CountDownLatch(3);
        doAnswer(invocation -> {
            // Three request transactions must reach the real upsert before any is allowed to finish it.
            // A target row lock would serialize these requests and fail this bounded rendezvous.
            atUpsert.countDown();
            await(atUpsert);
            return delegate(invocation);
        }).when(cases).upsertPendingReport(eq(fixture.type()), eq(fixture.target()), anyString(), any());
        ExecutorService workers = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<CompletableFuture<MvcResult>> pending = new ArrayList<>();
            for (Learner reporter : reporters) pending.add(submit(workers, reporter, fixture, start));
            start.countDown();
            for (CompletableFuture<MvcResult> request : pending) assertReceived(result(request));
            assertPendingCase(fixture, count);
            assertThat(reportCount(fixture)).isEqualTo(count);
            JsonNode entries = pendingEntries(fixture);
            List<Long> storedReporters = new ArrayList<>();
            for (JsonNode entry : entries) {
                storedReporters.add(entry.path("userId").asLong());
                assertThat(entry.path("reason").asText()).isEqualTo("SPAM");
                assertThat(entry.path("detail").asText()).isEqualTo("Synthetic report detail");
                assertThat(Instant.parse(entry.path("reportedAt").asText())).isNotNull();
            }
            assertThat(storedReporters).containsExactlyInAnyOrderElementsOf(
                    reporters.stream().map(Learner::id).toList());
            assertThat(targetState(fixture)).isEqualTo(before);
            System.out.println("REPORT_CONCURRENCY_EVIDENCE reports=" + count + " pendingCases=1 entries=" + count);
        } finally {
            start.countDown();
            stop(workers);
        }
    }

    @ParameterizedTest(name = "uncommitted report primary key rollback={0}")
    @ValueSource(booleans = {false, true})
    void FR036_duplicatePrimaryKeyWaitsForRealInsertCommitOrRollback(boolean rollback) throws Exception {
        Fixture fixture = fixture("POST");
        Learner reporter = registerVerifiedLearner();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<MvcResult> pending = transaction().execute(status -> {
                // Deliberately bypass only the quota lock to expose PostgreSQL's REPORT primary-key conflict.
                seedReport(reporter, fixture);
                int holder = backendPid();
                CompletableFuture<MvcResult> request = submit(worker, reporter, fixture);
                awaitInsertWaiters(holder, "community.reactions", 1);
                if (rollback) status.setRollbackOnly();
                return request;
            });
            MvcResult response = result(pending);
            if (rollback) assertReceived(response);
            else assertError(response, 409, "DUPLICATE_REPORT");
            assertThat(reportCount(fixture)).isEqualTo(1);
            assertPendingCase(fixture, 1);
        } finally {
            stop(worker);
        }
    }

    @ParameterizedTest(name = "uncommitted pending-case partial index rollback={0}")
    @ValueSource(booleans = {false, true})
    void FR036_distinctReportersWaitAtActualPendingCaseUpsert(boolean rollback) throws Exception {
        Fixture fixture = fixture("COMMENT");
        Learner holderReporter = registerVerifiedLearner();
        Learner workerReporter = registerVerifiedLearner();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<MvcResult> pending = transaction().execute(status -> {
                seedReport(holderReporter, fixture);
                int holder = backendPid();
                CompletableFuture<MvcResult> request = submit(worker, workerReporter, fixture);
                // Different users have different quota keys; no target row lock is held by this fixture.
                awaitInsertWaiters(holder, "community.moderation_cases", 1);
                if (rollback) status.setRollbackOnly();
                return request;
            });
            assertReceived(result(pending));
            int expected = rollback ? 1 : 2;
            assertThat(reportCount(fixture)).isEqualTo(expected);
            assertPendingCase(fixture, expected);
        } finally {
            stop(worker);
        }
    }

    @Test
    void FR036_closedCaseAllowsNewReporterButLifetimeDuplicateRemainsRejected() throws Exception {
        Fixture fixture = fixture("POST");
        Learner first = registerVerifiedLearner();
        Learner second = registerVerifiedLearner();
        assertReceived(report(first, fixture));
        Map<String, Object> oldCase = jdbc.queryForMap("SELECT id, reasons::text AS reasons, created_at "
                + "FROM community.moderation_cases WHERE target_type=? AND target_id=? AND status='PENDING'",
                fixture.type(), fixture.target());
        transaction().executeWithoutResult(status -> assertThat(jdbc.update("""
                UPDATE community.moderation_cases SET status='REJECTED', handled_by=?, handled_at=?,
                    decision_note='Synthetic fixture decision' WHERE id=?
                """, fixture.author().id(), Timestamp.from(Instant.now()), oldCase.get("id"))).isEqualTo(1));
        assertReceived(report(second, fixture));
        assertError(report(first, fixture), 409, "DUPLICATE_REPORT");
        assertPendingCase(fixture, 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM community.moderation_cases "
                + "WHERE target_type=? AND target_id=?", Integer.class, fixture.type(), fixture.target())).isEqualTo(2);
        assertThat(jdbc.queryForMap("SELECT id, reasons::text AS reasons, created_at "
                + "FROM community.moderation_cases WHERE id=?", oldCase.get("id"))).isEqualTo(oldCase);
        assertThat(reportCount(fixture)).isEqualTo(2);
    }

    @Test
    void FR036_sqlFailureAfterRealUpsertRollsBackReportAndPreservesExistingCase() throws Exception {
        Fixture fixture = fixture("POST");
        Learner first = registerVerifiedLearner();
        Learner failing = registerVerifiedLearner();
        assertReceived(report(first, fixture));
        Map<String, Object> before = jdbc.queryForMap("SELECT * FROM community.moderation_cases "
                + "WHERE target_type=? AND target_id=?", fixture.type(), fixture.target());
        AtomicReference<TransactionIdentity> insertTransaction = new AtomicReference<>();
        doAnswer(invocation -> {
            Object affected = delegate(invocation);
            insertTransaction.set(identity());
            return affected;
        }).when(reactions).insertReport(eq(failing.id()), eq(fixture.type()), eq(fixture.target()), any());
        doAnswer(invocation -> {
            Object affected = delegate(invocation);
            TransactionIdentity upsert = identity();
            assertThat(upsert).isEqualTo(insertTransaction.get());
            assertThat(upsert.isolation()).isEqualTo("read committed");
            int jpaPid = ((Number) entityManager.createNativeQuery("SELECT pg_backend_pid()")
                    .getSingleResult()).intValue();
            assertThat(jpaPid).isEqualTo(upsert.pid());
            System.out.println("REPORT_TRANSACTION_EVIDENCE backendPid=" + upsert.pid()
                    + " transactionId=" + upsert.transactionId());
            // This real database error aborts the transaction after both successful repository mutations.
            jdbc.execute("SELECT 1 / 0");
            return affected;
        }).when(cases).upsertPendingReport(eq(fixture.type()), eq(fixture.target()), anyString(), any());

        assertError(report(failing, fixture), 500, "INTERNAL_ERROR");
        assertThat(insertTransaction.get()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM community.reactions WHERE user_id=? "
                + "AND kind='REPORT'", Integer.class, failing.id())).isZero();
        assertThat(jdbc.queryForMap("SELECT * FROM community.moderation_cases "
                + "WHERE target_type=? AND target_id=?", fixture.type(), fixture.target())).isEqualTo(before);
        assertThat(reportCount(fixture)).isEqualTo(1);
    }

    @Test
    void FR035_FR036_quotaCountInsertAndCaseUpsertUseOneReadCommittedTransaction() throws Exception {
        Fixture fixture = fixture("COMMENT");
        Learner reporter = registerVerifiedLearner();
        AtomicReference<TransactionIdentity> counted = new AtomicReference<>();
        doAnswer(invocation -> {
            counted.set(identity());
            return delegate(invocation);
        }).when(reactions).countReportsInDay(eq(reporter.id()), any(), any());
        doAnswer(invocation -> {
            assertThat(identity()).isEqualTo(counted.get());
            return delegate(invocation);
        }).when(reactions).insertReport(eq(reporter.id()), eq(fixture.type()), eq(fixture.target()), any());
        doAnswer(invocation -> {
            TransactionIdentity upsert = identity();
            assertThat(upsert).isEqualTo(counted.get());
            assertThat(upsert.isolation()).isEqualTo("read committed");
            Object affected = delegate(invocation);
            assertThat(reportCount(fixture)).isEqualTo(1);
            System.out.println("REPORT_TRANSACTION_EVIDENCE backendPid=" + upsert.pid()
                    + " transactionId=" + upsert.transactionId() + " countInsertUpsert=3");
            return affected;
        }).when(cases).upsertPendingReport(eq(fixture.type()), eq(fixture.target()), anyString(), any());
        assertReceived(report(reporter, fixture));
        assertThat(counted.get()).isNotNull();
        assertPendingCase(fixture, 1);
    }

    private Fixture fixture(String type) throws Exception {
        Learner author = registerVerifiedLearner();
        return transaction().execute(status -> {
            long postId = jdbc.queryForObject("""
                    INSERT INTO community.posts(author_id, category, title, content, status, published_at)
                    VALUES (?, 'QUESTION', 'Report concurrency fixture', 'Synthetic community fixture',
                            'APPROVED', now()) RETURNING id
                    """, Long.class, author.id());
            long targetId = type.equals("POST") ? postId : jdbc.queryForObject("""
                    INSERT INTO community.comments(post_id, author_id, content)
                    VALUES (?, ?, 'Synthetic visible comment') RETURNING id
                    """, Long.class, postId, author.id());
            return new Fixture(author, type, targetId);
        });
    }

    private void seedReport(Learner reporter, Fixture fixture) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String entry;
        try {
            entry = mapper.writeValueAsString(List.of(Map.of("userId", reporter.id(), "reason", "SPAM",
                    "detail", "Synthetic report detail", "reportedAt", now.toString())));
        } catch (Exception exception) {
            throw new AssertionError("Cannot serialize synthetic report", exception);
        }
        assertThat(jdbc.update(INSERT_REPORT, reporter.id(), fixture.type(), fixture.target(), Timestamp.from(now)))
                .isEqualTo(1);
        assertThat(jdbc.update(UPSERT_CASE, fixture.type(), fixture.target(), entry, Timestamp.from(now))).isEqualTo(1);
    }

    private Map<String, Object> targetState(Fixture fixture) {
        return fixture.type().equals("POST")
                ? jdbc.queryForMap("SELECT * FROM community.posts WHERE id=?", fixture.target())
                : jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", fixture.target());
    }

    private int reportCount(Fixture fixture) {
        return jdbc.queryForObject("SELECT count(*) FROM community.reactions "
                + "WHERE target_type=? AND target_id=? AND kind='REPORT'", Integer.class,
                fixture.type(), fixture.target());
    }

    private void assertPendingCase(Fixture fixture, int expected) throws Exception {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT report_count, auto_hidden FROM "
                + "community.moderation_cases WHERE target_type=? AND target_id=? AND status='PENDING'",
                fixture.type(), fixture.target());
        assertThat(rows).hasSize(1);
        assertThat(((Number) rows.get(0).get("report_count")).intValue()).isEqualTo(expected);
        assertThat(rows.get(0).get("auto_hidden")).isEqualTo(false);
        assertThat(pendingEntries(fixture).size()).isEqualTo(expected);
    }

    private JsonNode pendingEntries(Fixture fixture) throws Exception {
        return mapper.readTree(jdbc.queryForObject("SELECT reasons::text FROM community.moderation_cases "
                + "WHERE target_type=? AND target_id=? AND status='PENDING'", String.class,
                fixture.type(), fixture.target()));
    }

    private void awaitInsertWaiters(int holderPid, String table, int expected) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        int observed = -1;
        while (System.nanoTime() < deadline) {
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            List<Integer> pids = jdbc.queryForList("""
                    SELECT a.pid FROM pg_stat_activity a
                    WHERE a.datname=current_database() AND a.backend_type='client backend'
                      AND ?=ANY(pg_blocking_pids(a.pid))
                      AND a.query ILIKE ?
                      AND EXISTS (SELECT 1 FROM pg_locks l WHERE l.pid=a.pid
                        AND NOT l.granted AND l.locktype='transactionid')
                    ORDER BY a.pid
                    """, Integer.class, holderPid, "%insert%into%" + table + "%");
            observed = pids.size();
            if (observed == expected) {
                System.out.println("REPORT_LOCK_EVIDENCE holderPid=" + holderPid + " workerPids=" + pids
                        + " transactionIdWaiters=" + observed);
                return;
            }
            pause();
        }
        throw new AssertionError("Expected " + expected + " transactionid INSERT waiters; observed " + observed);
    }

    private int backendPid() {
        return jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
    }

    private TransactionIdentity identity() {
        return new TransactionIdentity(backendPid(),
                jdbc.queryForObject("SELECT pg_current_xact_id()::text", String.class),
                jdbc.queryForObject("SHOW transaction_isolation", String.class));
    }

    private static Object delegate(InvocationOnMock invocation) throws Throwable {
        return mockingDetails(invocation.getMock()).getMockCreationSettings().getDefaultAnswer().answer(invocation);
    }

    private MvcResult report(Learner reporter, Fixture fixture) throws Exception {
        return mvc.perform(post("/api/community/reports").header("Authorization", "Bearer " + reporter.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                        "targetType", fixture.type(), "targetId", fixture.target(), "reason", "SPAM",
                        "detail", "Synthetic report detail")))).andReturn();
    }

    private CompletableFuture<MvcResult> submit(ExecutorService worker, Learner reporter, Fixture fixture) {
        return submit(worker, reporter, fixture, new CountDownLatch(0));
    }

    private CompletableFuture<MvcResult> submit(ExecutorService workers, Learner reporter, Fixture fixture,
            CountDownLatch start) {
        return CompletableFuture.supplyAsync(() -> {
            await(start);
            try {
                return report(reporter, fixture);
            } catch (Exception exception) {
                throw new IllegalStateException("Report HTTP handler failed", exception);
            }
        }, workers);
    }

    private void assertReceived(MvcResult response) throws Exception {
        assertThat(response.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(response)).isEqualTo(mapper.readTree("{\"received\":true}"));
    }

    private void assertError(MvcResult response, int status, String code) throws Exception {
        assertThat(response.getResponse().getStatus()).isEqualTo(status);
        JsonNode error = body(response);
        assertThat(error.path("error_code").asText()).isEqualTo(code);
        assertThat(error.path("message").asText()).isNotBlank();
        assertThat(error.path("request_id").asText()).isNotBlank();
        assertThat(error.has("userId")).isFalse();
        assertThat(error.has("reasons")).isFalse();
        assertThat(error.has("reportCount")).isFalse();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private TransactionTemplate transaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.setTimeout((int) TIMEOUT.toSeconds() + 5);
        return template;
    }

    private Learner registerVerifiedLearner() throws Exception {
        String email = "report-concurrency-" + UUID.randomUUID() + "@example.test";
        String password = "Report" + UUID.randomUUID() + "A9";
        MvcResult registration = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", password,
                        "confirmPassword", password, "displayName", "Report Concurrency Learner",
                        "acceptTerms", true)))).andReturn();
        assertThat(registration.getResponse().getStatus()).isEqualTo(201);
        long id = body(registration).path("userId").asLong();
        assertThat(id).isPositive();
        JsonNode message = null;
        for (JsonNode summary : mailpit("/api/v1/messages").path("messages")) {
            if (summary.toString().contains(email)) {
                message = mailpit("/api/v1/message/" + summary.path("ID").asText());
                break;
            }
        }
        assertThat(message != null).as("verification email delivered through Mailpit").isTrue();
        Matcher matcher = VERIFY_LINK.matcher(message.path("Text").asText());
        assertThat(matcher.find()).as("verification token delivered").isTrue();
        MvcResult verified = mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("token", matcher.group(1))))).andReturn();
        assertThat(verified.getResponse().getStatus()).isEqualTo(200);
        MvcResult login = mvc.perform(post("/api/auth/login").header("X-Client-Type", "MOBILE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", password)))).andReturn();
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        String token = body(login).path("accessToken").asText();
        assertThat(!token.isBlank()).as("verified mobile token issued").isTrue();
        return new Learner(id, token);
    }

    private JsonNode mailpit(String path) throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder().uri(URI.create(mailpitBaseUrl + path))
                .timeout(TIMEOUT).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body());
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted awaiting request start", exception);
        }
    }

    private static void pause() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted observing PostgreSQL locks", exception);
        }
    }

    private static <T> T result(CompletableFuture<T> future) {
        try {
            return future.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Report request did not finish within bounded timeout", exception);
        }
    }

    private static void stop(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
    }

    private record Learner(long id, String token) { }
    private record Fixture(Learner author, String type, long target) { }
    private record TransactionIdentity(int pid, String transactionId, String isolation) { }
}
