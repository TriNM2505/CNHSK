package com.cnhsk.community.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** The report quota is measured using real committed rows and PostgreSQL advisory-lock waiters. */
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ReportQuotaConcurrencyIntegrationTest.TimeConfiguration.class)
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.minimum-idle=4",
        "spring.datasource.hikari.connection-timeout=20000"
})
class ReportQuotaConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final Instant INITIAL_TIME = Instant.parse("2030-03-15T05:00:00.250123456Z");
    private static final Instant BEFORE_MIDNIGHT = Instant.parse("2030-03-15T16:59:59.750123456Z");
    private static final Instant AFTER_MIDNIGHT = Instant.parse("2030-03-15T17:00:00.123456789Z");
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Pattern VERIFY_LINK = Pattern.compile("/verify-email\\?token=([A-Za-z0-9_-]+)");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NamedParameterJdbcTemplate namedJdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private AdjustableClock clock;
    @Value("${test.mailpit.base-url}") private String mailpitBaseUrl;
    @MockitoSpyBean private ReactionRepository reactions;

    @BeforeEach
    @AfterEach
    void resetClock() {
        clock.set(INITIAL_TIME);
    }

    @Test
    void FR035_mixedPostAndCommentReportsCompeteForExactlyOneTwentiethSlot() throws Exception {
        Fixture fixture = fixture();
        seedReports(fixture, 19, INITIAL_TIME.minusSeconds(60));
        assertThat(reportCount(fixture.reporter())).isEqualTo(19);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT target_type) FROM community.reactions "
                + "WHERE user_id=? AND kind='REPORT'", Integer.class, fixture.reporter().id())).isEqualTo(2);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            List<CompletableFuture<MvcResult>> pending = transaction().execute(status -> {
                lockQuota(fixture.reporter());
                int holder = backendPid();
                List<CompletableFuture<MvcResult>> requests = List.of(
                        submit(workers, fixture.reporter(), fixture.post()),
                        submit(workers, fixture.reporter(), fixture.comment()));
                awaitQuotaWaiters(holder, fixture.reporter(), 2);
                return requests;
            });
            List<MvcResult> responses = List.of(result(pending.get(0)), result(pending.get(1)));
            assertThat(responses.stream().map(response -> response.getResponse().getStatus()).toList())
                    .containsExactlyInAnyOrder(201, 429);
            for (MvcResult response : responses) {
                if (response.getResponse().getStatus() == 201) assertReceived(response);
                else assertQuotaError(response, 43200);
            }
            assertThat(reportCount(fixture.reporter())).isEqualTo(20);
            assertThat(reportsOn(fixture.reporter(), fixture.post())
                    + reportsOn(fixture.reporter(), fixture.comment())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM community.moderation_cases WHERE "
                    + "(target_type='POST' AND target_id=?) OR (target_type='COMMENT' AND target_id=?)",
                    Integer.class, fixture.post().id(), fixture.comment().id())).isEqualTo(1);
            System.out.println("REPORT_QUOTA_EVIDENCE advisoryWaiters=2 reports=20");
        } finally {
            stop(workers);
        }
    }

    @Test
    void FR035_FR036_previousVietnamDayIsExcludedAndDuplicateAtQuotaDoesNotSpendAnotherSlot() throws Exception {
        Fixture fixture = fixture();
        Instant dayStart = dayStart(INITIAL_TIME);
        List<Target> current = seedReports(fixture, 19, dayStart);
        seedReports(fixture, 20, dayStart.minusSeconds(60));
        // Non-REPORT reactions are outside this quota even when their timestamp is inside today's window.
        transaction().executeWithoutResult(status -> jdbc.update("INSERT INTO community.reactions "
                + "(user_id, target_type, target_id, kind, created_at) VALUES (?, 'POST', ?, 'LIKE', ?)",
                fixture.reporter().id(), fixture.post().id(), Timestamp.from(INITIAL_TIME)));
        assertReceived(report(fixture.reporter(), fixture.post()));
        assertThat(dayReportCount(fixture.reporter(), INITIAL_TIME)).isEqualTo(20);
        assertError(report(fixture.reporter(), current.get(0)), 409, "DUPLICATE_REPORT");
        assertQuotaError(report(fixture.reporter(), fixture.comment()), 43200);
        assertThat(dayReportCount(fixture.reporter(), INITIAL_TIME)).isEqualTo(20);
        assertThat(reportCount(fixture.reporter())).isEqualTo(40);
        assertThat(reportsOn(fixture.reporter(), fixture.comment())).isZero();
    }

    @Test
    void FR035_midnightResetsSameUsersQuotaAndRetryDelayRoundsUp() throws Exception {
        clock.set(BEFORE_MIDNIGHT);
        Fixture fixture = fixture();
        seedReports(fixture, 19, BEFORE_MIDNIGHT.minusSeconds(60));
        assertReceived(report(fixture.reporter(), fixture.post()));
        assertQuotaError(report(fixture.reporter(), fixture.comment()), 1);
        assertThat(dayReportCount(fixture.reporter(), BEFORE_MIDNIGHT)).isEqualTo(20);

        clock.set(AFTER_MIDNIGHT);
        // Reauthenticate this same registered user so time changes never test an expired/stale JWT instead.
        Learner refreshed = login(fixture.reporter().id(), fixture.reporter().email(), fixture.reporter().password());
        assertThat(refreshed.id()).isEqualTo(fixture.reporter().id());
        assertReceived(report(refreshed, fixture.comment()));
        assertThat(dayReportCount(refreshed, AFTER_MIDNIGHT)).isEqualTo(1);
        assertThat(reportCount(refreshed)).isEqualTo(21);
        assertSavedTime(refreshed, fixture.comment(), AFTER_MIDNIGHT);
    }

    @Test
    void FR035_FR036_waitingRequestTakesReadCommittedDaySnapshotAfterQuotaLockAcrossMidnight() throws Exception {
        clock.set(BEFORE_MIDNIGHT);
        Fixture fixture = fixture();
        seedReports(fixture, 20, BEFORE_MIDNIGHT.minusSeconds(60));
        AtomicReference<TransactionIdentity> countingTransaction = new AtomicReference<>();
        AtomicReference<List<Instant>> countedWindow = new AtomicReference<>();
        doAnswer(invocation -> {
            countingTransaction.set(identity());
            countedWindow.set(List.of(invocation.getArgument(1, Instant.class),
                    invocation.getArgument(2, Instant.class)));
            return delegate(invocation);
        }).when(reactions).countReportsInDay(eq(fixture.reporter().id()), any(), any());
        doAnswer(invocation -> {
            TransactionIdentity inserted = identity();
            assertThat(inserted).isEqualTo(countingTransaction.get());
            assertThat(inserted.isolation()).isEqualTo("read committed");
            assertThat(invocation.getArgument(3, Instant.class))
                    .isEqualTo(AFTER_MIDNIGHT.truncatedTo(ChronoUnit.MICROS));
            return delegate(invocation);
        }).when(reactions).insertReport(eq(fixture.reporter().id()), eq("POST"), eq(fixture.post().id()), any());
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<MvcResult> pending = transaction().execute(status -> {
                lockQuota(fixture.reporter());
                CompletableFuture<MvcResult> request = submit(worker, fixture.reporter(), fixture.post());
                // Authentication and the visibility read already completed before the actual DB lock wait.
                awaitQuotaWaiters(backendPid(), fixture.reporter(), 1);
                clock.set(AFTER_MIDNIGHT);
                return request;
            });
            assertReceived(result(pending));
            assertThat(countedWindow.get()).containsExactly(dayStart(AFTER_MIDNIGHT),
                    dayStart(AFTER_MIDNIGHT).plus(Duration.ofDays(1)));
            assertThat(dayReportCount(fixture.reporter(), AFTER_MIDNIGHT)).isEqualTo(1);
            assertSavedTime(fixture.reporter(), fixture.post(), AFTER_MIDNIGHT);
            TransactionIdentity observed = countingTransaction.get();
            assertThat(observed).isNotNull();
            System.out.println("REPORT_QUOTA_TRANSACTION_EVIDENCE backendPid=" + observed.pid()
                    + " transactionId=" + observed.transactionId() + " reports=1");
        } finally {
            stop(worker);
        }
    }

    @Test
    void FR035_FR036_rolledBackReportDoesNotConsumeQuotaAndReleasesRealWaitingRequest() throws Exception {
        Fixture fixture = fixture();
        seedReports(fixture, 19, INITIAL_TIME.minusSeconds(60));
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<MvcResult> pending = transaction().execute(status -> {
                lockQuota(fixture.reporter());
                seedReport(fixture.reporter(), fixture.post(), INITIAL_TIME);
                assertThat(dayReportCount(fixture.reporter(), INITIAL_TIME)).isEqualTo(20);
                CompletableFuture<MvcResult> request = submit(worker, fixture.reporter(), fixture.comment());
                awaitQuotaWaiters(backendPid(), fixture.reporter(), 1);
                status.setRollbackOnly();
                return request;
            });
            assertReceived(result(pending));
            assertThat(reportsOn(fixture.reporter(), fixture.post())).isZero();
            assertThat(reportsOn(fixture.reporter(), fixture.comment())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM community.moderation_cases "
                    + "WHERE target_type='POST' AND target_id=?", Integer.class, fixture.post().id())).isZero();
            assertThat(dayReportCount(fixture.reporter(), INITIAL_TIME)).isEqualTo(20);
        } finally {
            stop(worker);
        }
    }

    private Fixture fixture() throws Exception {
        Learner reporter = registerVerifiedLearner();
        Learner author = registerVerifiedLearner();
        return transaction().execute(status -> {
            Target post = seedPost(author);
            Target comment = seedComment(author, post.id());
            return new Fixture(reporter, author, post, comment);
        });
    }

    private Target seedPost(Learner author) {
        return new Target("POST", jdbc.queryForObject("""
                INSERT INTO community.posts(author_id, category, title, content, status, published_at)
                VALUES (?, 'QUESTION', 'Report quota fixture', 'Synthetic community fixture',
                        'APPROVED', ?) RETURNING id
                """, Long.class, author.id(), Timestamp.from(clock.instant())));
    }

    private Target seedComment(Learner author, long postId) {
        return new Target("COMMENT", jdbc.queryForObject("""
                INSERT INTO community.comments(post_id, author_id, content)
                VALUES (?, ?, 'Synthetic visible quota comment') RETURNING id
                """, Long.class, postId, author.id()));
    }

    private List<Target> seedReports(Fixture fixture, int count, Instant createdAt) {
        return transaction().execute(status -> {
            List<Target> targets = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                Target target = index % 2 == 0 ? seedPost(fixture.author())
                        : seedComment(fixture.author(), fixture.post().id());
                seedReport(fixture.reporter(), target, createdAt);
                targets.add(target);
            }
            return targets;
        });
    }

    private void seedReport(Learner reporter, Target target, Instant createdAt) {
        Instant time = createdAt.truncatedTo(ChronoUnit.MICROS);
        String entry;
        try {
            entry = mapper.writeValueAsString(List.of(Map.of("userId", reporter.id(), "reason", "SPAM",
                    "reportedAt", time.toString())));
        } catch (Exception exception) {
            throw new AssertionError("Cannot serialize synthetic report", exception);
        }
        assertThat(jdbc.update("INSERT INTO community.reactions(user_id, target_type, target_id, kind, created_at) "
                + "VALUES (?, ?, ?, 'REPORT', ?)", reporter.id(), target.type(), target.id(), Timestamp.from(time)))
                .isEqualTo(1);
        assertThat(jdbc.update("INSERT INTO community.moderation_cases(target_type, target_id, reasons, created_at) "
                + "VALUES (?, ?, CAST(? AS jsonb), ?)", target.type(), target.id(), entry, Timestamp.from(time)))
                .isEqualTo(1);
    }

    private void assertSavedTime(Learner reporter, Target target, Instant snapshot) throws Exception {
        Instant expected = snapshot.truncatedTo(ChronoUnit.MICROS);
        Timestamp reactionTime = jdbc.queryForObject("SELECT created_at FROM community.reactions WHERE "
                + "user_id=? AND target_type=? AND target_id=? AND kind='REPORT'", Timestamp.class,
                reporter.id(), target.type(), target.id());
        assertThat(reactionTime.toInstant()).isEqualTo(expected);
        Map<String, Object> savedCase = jdbc.queryForMap("SELECT created_at, reasons::text AS reasons FROM "
                + "community.moderation_cases WHERE target_type=? AND target_id=? AND status='PENDING'",
                target.type(), target.id());
        assertThat(((Timestamp) savedCase.get("created_at")).toInstant()).isEqualTo(expected);
        JsonNode entries = mapper.readTree((String) savedCase.get("reasons"));
        assertThat(entries.size()).isEqualTo(1);
        assertThat(Instant.parse(entries.get(0).path("reportedAt").asText())).isEqualTo(expected);
        assertThat(entries.get(0).path("userId").asLong() == reporter.id()).isTrue();
    }

    private int reportCount(Learner reporter) {
        return jdbc.queryForObject("SELECT count(*) FROM community.reactions WHERE user_id=? AND kind='REPORT'",
                Integer.class, reporter.id());
    }

    private int dayReportCount(Learner reporter, Instant now) {
        return jdbc.queryForObject("SELECT count(*) FROM community.reactions WHERE user_id=? AND kind='REPORT' "
                + "AND created_at>=? AND created_at<?", Integer.class, reporter.id(),
                Timestamp.from(dayStart(now)), Timestamp.from(dayStart(now).plus(Duration.ofDays(1))));
    }

    private int reportsOn(Learner reporter, Target target) {
        return jdbc.queryForObject("SELECT count(*) FROM community.reactions WHERE user_id=? AND target_type=? "
                + "AND target_id=? AND kind='REPORT'", Integer.class, reporter.id(), target.type(), target.id());
    }

    private void lockQuota(Learner reporter) {
        namedJdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))",
                Map.of("key", quotaKey(reporter)), resultSet -> null);
    }

    private void awaitQuotaWaiters(int holderPid, Learner reporter, int expected) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        int observed = -1;
        while (System.nanoTime() < deadline) {
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            List<Integer> pids = jdbc.queryForList("""
                    WITH lock_key AS (SELECT hashtextextended(?, 0) AS value)
                    SELECT a.pid FROM pg_stat_activity a JOIN pg_locks l ON l.pid=a.pid, lock_key
                    WHERE a.datname=current_database() AND ?=ANY(pg_blocking_pids(a.pid))
                      AND l.locktype='advisory' AND NOT l.granted AND l.objsubid=1
                      AND l.database=(SELECT oid FROM pg_database WHERE datname=current_database())
                      AND l.classid=((value >> 32) & 4294967295)::oid
                      AND l.objid=(value & 4294967295)::oid
                    ORDER BY a.pid
                    """, Integer.class, quotaKey(reporter), holderPid);
            observed = pids.size();
            if (observed == expected) {
                System.out.println("REPORT_QUOTA_LOCK_EVIDENCE holderPid=" + holderPid
                        + " workerPids=" + pids + " advisoryWaiters=" + observed);
                return;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted observing report quota advisory locks", exception);
            }
        }
        throw new AssertionError("Expected " + expected + " real quota advisory waiters; observed " + observed);
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

    private MvcResult report(Learner reporter, Target target) throws Exception {
        return mvc.perform(post("/api/community/reports").header("Authorization", "Bearer " + reporter.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                        "targetType", target.type(), "targetId", target.id(), "reason", "SPAM")))).andReturn();
    }

    private CompletableFuture<MvcResult> submit(ExecutorService executor, Learner reporter, Target target) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return report(reporter, target);
            } catch (Exception exception) {
                throw new IllegalStateException("Report HTTP handler failed", exception);
            }
        }, executor);
    }

    private void assertReceived(MvcResult response) throws Exception {
        assertThat(response.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(response)).isEqualTo(mapper.readTree("{\"received\":true}"));
    }

    private void assertQuotaError(MvcResult response, long retryAfterSeconds) throws Exception {
        assertError(response, 429, "TOO_MANY_REQUESTS");
        assertThat(body(response).path("retryAfterSeconds").asLong()).isEqualTo(retryAfterSeconds);
    }

    private void assertError(MvcResult response, int status, String code) throws Exception {
        assertThat(response.getResponse().getStatus()).isEqualTo(status);
        JsonNode error = body(response);
        assertThat(error.path("error_code").asText()).isEqualTo(code);
        assertThat(error.path("message").asText()).isNotBlank();
        assertThat(error.path("request_id").asText()).isNotBlank();
    }

    private JsonNode body(MvcResult response) throws Exception {
        return mapper.readTree(response.getResponse().getContentAsString());
    }

    private TransactionTemplate transaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.setTimeout((int) TIMEOUT.toSeconds() + 5);
        return template;
    }

    private Learner registerVerifiedLearner() throws Exception {
        String email = "report-quota-" + UUID.randomUUID() + "@example.test";
        String password = "Quota" + UUID.randomUUID() + "A9";
        MvcResult registration = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", password,
                        "confirmPassword", password, "displayName", "Report Quota Learner", "acceptTerms", true))))
                .andReturn();
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
        MvcResult verification = mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("token", matcher.group(1))))).andReturn();
        assertThat(verification.getResponse().getStatus()).isEqualTo(200);
        return login(id, email, password);
    }

    private Learner login(long id, String email, String password) throws Exception {
        MvcResult response = mvc.perform(post("/api/auth/login").header("X-Client-Type", "MOBILE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", password)))).andReturn();
        assertThat(response.getResponse().getStatus()).isEqualTo(200);
        String token = body(response).path("accessToken").asText();
        assertThat(!token.isBlank()).as("verified mobile token issued").isTrue();
        return new Learner(id, token, email, password);
    }

    private JsonNode mailpit(String path) throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder().uri(URI.create(mailpitBaseUrl + path))
                .timeout(TIMEOUT).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body());
    }

    private static String quotaKey(Learner reporter) {
        return "community:report-quota:" + reporter.id();
    }

    private static Instant dayStart(Instant time) {
        return time.atZone(VIETNAM).toLocalDate().atStartOfDay(VIETNAM).toInstant();
    }

    private static MvcResult result(CompletableFuture<MvcResult> future) {
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

    private record Learner(long id, String token, String email, String password) { }
    private record Target(String type, long id) { }
    private record Fixture(Learner reporter, Learner author, Target post, Target comment) { }
    private record TransactionIdentity(int pid, String transactionId, String isolation) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean
        @Primary
        AdjustableClock reportQuotaClock() {
            return new AdjustableClock();
        }
    }

    static final class AdjustableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(INITIAL_TIME);

        void set(Instant instant) {
            now.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
