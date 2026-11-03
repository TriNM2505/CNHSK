package com.cnhsk.community.comment;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Uses committed fixtures and independent request transactions against container PostgreSQL. */
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CommentQuotaConcurrencyIntegrationTest.TimeConfiguration.class)
class CommentQuotaConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final Pattern VERIFY_LINK = Pattern.compile("/verify-email\\?token=([A-Za-z0-9_-]+)");
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final Instant INITIAL_TIME = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final long EXPECTED_RETRY_SECONDS = 1201;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NamedParameterJdbcTemplate namedJdbc;
    @Autowired private CommentRepository comments;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private AdjustableClock clock;
    @PersistenceContext private EntityManager entityManager;
    @Value("${test.mailpit.base-url}") private String mailpitBaseUrl;

    @BeforeEach
    void resetTimeBeforeRequest() {
        clock.set(INITIAL_TIME);
    }

    @AfterEach
    void resetTimeAfterRequestsFinish() {
        clock.set(INITIAL_TIME);
    }

    @ParameterizedTest(name = "{0} existing comments share one root and reply quota")
    @ValueSource(ints = {0, 29, 30})
    void concurrentRootAndReplyRequestsCountEveryStatusAndPost(int initialCount) throws Exception {
        Fixture fixture = fixture();
        seedMixedComments(fixture, initialCount);
        assertThat(countComments(fixture.author().userId())).isEqualTo(initialCount);
        String rootContent = "Concurrent root " + UUID.randomUUID();
        String replyContent = "Concurrent reply " + UUID.randomUUID();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Requests pending = transaction().execute(status -> {
                lockQuota(fixture.author().userId());
                CompletableFuture<MvcResult> root = submit(workers, fixture.author(), rootPath(fixture.firstPost()),
                        rootContent);
                CompletableFuture<MvcResult> reply = submit(workers, fixture.author(), replyPath(fixture.parent()),
                        replyContent);
                // The holder polls on its transaction-bound connection: the other two pool slots
                // are occupied by the actual requests waiting for this exact advisory key.
                awaitQuotaWaiters(fixture.author().userId(), 2);
                return new Requests(root, reply);
            });

            MvcResult root = result(pending.first());
            MvcResult reply = result(pending.second());
            int[] expectedStatuses = initialCount == 0 ? new int[] {201, 201}
                    : initialCount == 29 ? new int[] {201, 429} : new int[] {429, 429};
            assertThat(new int[] {root.getResponse().getStatus(), reply.getResponse().getStatus()})
                    .containsExactlyInAnyOrder(expectedStatuses);
            for (MvcResult response : new MvcResult[] {root, reply}) {
                if (response.getResponse().getStatus() == 429) assertQuotaError(response);
            }
            int inserts = initialCount == 0 ? 2 : initialCount == 29 ? 1 : 0;
            assertThat(countComments(fixture.author().userId())).isEqualTo(initialCount + inserts);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM community.comments "
                            + "WHERE author_id=? AND content IN (?, ?)", Integer.class,
                    fixture.author().userId(), rootContent, replyContent)).isEqualTo(inserts);
            if (initialCount == 29) {
                System.out.println("COMMENT_QUOTA_EVIDENCE quotaWaiters=2 statuses=201,429 finalCount=30");
            }
        } finally {
            stop(workers);
        }
    }

    @Test
    void anotherAuthorCanCreateWhileFirstAuthorsQuotaLockIsHeld() throws Exception {
        Fixture fixture = fixture();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<MvcResult> blocked = transaction().execute(status -> {
                lockQuota(fixture.author().userId());
                CompletableFuture<MvcResult> first = submit(workers, fixture.author(),
                        rootPath(fixture.firstPost()), "Blocked author root");
                CompletableFuture<MvcResult> independent = submit(workers, fixture.otherAuthor(),
                        rootPath(fixture.secondPost()), "Independent author root");
                awaitQuotaWaiters(fixture.author().userId(), 1);
                assertThat(result(independent).getResponse().getStatus()).isEqualTo(201);
                assertThat(first.isDone()).isFalse();
                return first;
            });
            assertThat(result(blocked).getResponse().getStatus()).isEqualTo(201);
            assertThat(countComments(fixture.author().userId())).isEqualTo(1);
            // The second author also owns the pre-existing reply parent.
            assertThat(countComments(fixture.otherAuthor().userId())).isEqualTo(2);
            System.out.println("COMMENT_QUOTA_EVIDENCE independentAuthor=201 blockedAuthor=201");
        } finally {
            stop(workers);
        }
    }

    @Test
    void replyRechecksPostVisibilityAfterWaitingForConcurrentPostWriter() throws Exception {
        Fixture fixture = fixture();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<MvcResult> pending = transaction().execute(status -> {
                // The parent lookup in the request sees the committed APPROVED version under MVCC.
                // Its subsequent post row lock must wait for this uncommitted visibility change.
                assertThat(jdbc.update("UPDATE community.posts SET status='PENDING' WHERE id=? "
                        + "AND status='APPROVED'", fixture.secondPost())).isEqualTo(1);
                int holderPid = backendPid();
                CompletableFuture<MvcResult> reply = submit(worker, fixture.author(),
                        replyPath(fixture.parent()), "Reply losing visibility race");
                awaitPostWriter(holderPid);
                return reply;
            });
            MvcResult response = result(pending);
            assertThat(response.getResponse().getStatus()).isEqualTo(404);
            assertThat(body(response).path("error_code").asText()).isEqualTo("COMMENT_NOT_FOUND");
            assertThat(jdbc.queryForObject("SELECT status FROM community.posts WHERE id=?",
                    String.class, fixture.secondPost())).isEqualTo("PENDING");
            assertThat(countComments(fixture.author().userId())).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM community.comments WHERE parent_id=?",
                    Integer.class, fixture.parent())).isZero();
            System.out.println("COMMENT_RACE_EVIDENCE postWaiter=1 replyStatus=404 noInsert=true");
        } finally {
            stop(worker);
        }
    }

    @ParameterizedTest(name = "snapshot follows the {0} lock")
    @ValueSource(strings = {"quota", "post"})
    void snapshotAndInsertTimeAreCapturedAfterBothLocks(String heldLock) throws Exception {
        Fixture fixture = fixture();
        seedMixedComments(fixture, 30);
        Instant advanced = INITIAL_TIME.plus(Duration.ofMinutes(61));
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<MvcResult> pending = transaction().execute(status -> {
                if (heldLock.equals("quota")) {
                    lockQuota(fixture.author().userId());
                } else {
                    jdbc.queryForObject("SELECT id FROM community.posts WHERE id=? FOR UPDATE",
                            Long.class, fixture.firstPost());
                }
                int holderPid = backendPid();
                CompletableFuture<MvcResult> request = submit(worker, fixture.author(),
                        rootPath(fixture.firstPost()), "Snapshot after held " + heldLock);
                if (heldLock.equals("quota")) {
                    awaitQuotaWaiters(fixture.author().userId(), 1);
                } else {
                    awaitPostWriter(holderPid);
                    assertThat(grantedQuotaLocks(fixture.author().userId())).isEqualTo(1);
                }
                // Authentication has already succeeded before the request reaches either DB lock.
                clock.set(advanced);
                return request;
            });
            MvcResult response = result(pending);
            assertThat(response.getResponse().getStatus()).isEqualTo(201);
            long id = body(response).path("id").asLong();
            assertThat(id).isPositive();
            assertThat(Instant.parse(body(response).path("createdAt").asText())).isEqualTo(advanced);
            Map<String, Object> saved = jdbc.queryForMap(
                    "SELECT created_at, updated_at FROM community.comments WHERE id=?", id);
            assertThat(((Timestamp) saved.get("created_at")).toInstant()).isEqualTo(advanced);
            assertThat(((Timestamp) saved.get("updated_at")).toInstant()).isEqualTo(advanced);
            assertThat(countComments(fixture.author().userId())).isEqualTo(31);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM community.comments WHERE author_id=? "
                            + "AND created_at>?", Integer.class, fixture.author().userId(),
                    Timestamp.from(advanced.minus(Duration.ofMinutes(60))))).isEqualTo(1);
            System.out.println("COMMENT_TIME_EVIDENCE snapshotAfter=" + heldLock + " timestampMatches=true");
        } finally {
            stop(worker);
        }
    }

    @Test
    void jdbcQuotaLockAndJpaInsertShareConnectionAndRollbackReleasesWaitingRequest() throws Exception {
        Fixture fixture = fixture();
        String rolledBackContent = "Rolled back comment " + UUID.randomUUID();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<MvcResult> pending = transaction().execute(status -> {
                lockQuota(fixture.author().userId());
                int jdbcPid = namedJdbc.queryForObject("SELECT pg_backend_pid()", Map.of(), Integer.class);
                int jpaPid = ((Number) entityManager.createNativeQuery("SELECT pg_backend_pid()")
                        .getSingleResult()).intValue();
                assertThat(jdbcPid).isEqualTo(jpaPid);
                Comment uncommitted = comments.saveAndFlush(new Comment(fixture.firstPost(), null,
                        fixture.author().userId(), rolledBackContent, (short) 0, INITIAL_TIME));
                assertThat(jdbc.queryForObject("SELECT count(*) FROM community.comments WHERE id=?",
                        Integer.class, uncommitted.getId())).isEqualTo(1);
                CompletableFuture<MvcResult> request = submit(worker, fixture.author(),
                        rootPath(fixture.secondPost()), "Comment after rollback");
                awaitQuotaWaiters(fixture.author().userId(), 1);
                status.setRollbackOnly();
                return request;
            });
            assertThat(result(pending).getResponse().getStatus()).isEqualTo(201);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM community.comments WHERE content=?",
                    Integer.class, rolledBackContent)).isZero();
            assertThat(countComments(fixture.author().userId())).isEqualTo(1);
            System.out.println("COMMENT_TRANSACTION_EVIDENCE sameConnection=true rollbackReleasedLock=true");
        } finally {
            stop(worker);
        }
    }

    private Fixture fixture() throws Exception {
        Learner author = registerVerifyAndLogin();
        Learner other = registerVerifyAndLogin();
        return transaction().execute(status -> {
            long first = seedPost(other.userId(), "APPROVED");
            long second = seedPost(other.userId(), "APPROVED");
            long nonpublic = seedPost(other.userId(), "PENDING");
            long parent = seedComment(second, null, other.userId(), "VISIBLE", INITIAL_TIME.minusSeconds(10));
            return new Fixture(author, other, first, second, nonpublic, parent);
        });
    }

    private long seedPost(long authorId, String status) {
        return jdbc.queryForObject("INSERT INTO community.posts(author_id, category, title, content, status, "
                        + "published_at, created_at, updated_at) VALUES (?, 'QUESTION', 'Comment quota fixture', "
                        + "'Synthetic community fixture', ?, ?, ?, ?) RETURNING id", Long.class, authorId,
                status, Timestamp.from(INITIAL_TIME), Timestamp.from(INITIAL_TIME), Timestamp.from(INITIAL_TIME));
    }

    private void seedMixedComments(Fixture fixture, int count) {
        transaction().executeWithoutResult(status -> {
            // This oldest timestamp gives an exact ceil(1200.25 seconds) retry delay.
            Instant oldest = INITIAL_TIME.minusSeconds(2400).plusMillis(250);
            String[] statuses = {"VISIBLE", "HIDDEN", "DELETED"};
            for (int index = 0; index < count; index++) {
                boolean reply = index % 2 == 1;
                long postId = reply ? fixture.secondPost()
                        : index % 4 == 0 ? fixture.firstPost() : fixture.nonpublicPost();
                seedComment(postId, reply ? fixture.parent() : null, fixture.author().userId(),
                        statuses[index % statuses.length], oldest.plus(index, ChronoUnit.MICROS));
            }
        });
    }

    private long seedComment(long postId, Long parentId, long authorId, String status, Instant createdAt) {
        return jdbc.queryForObject("INSERT INTO community.comments(post_id, parent_id, author_id, content, "
                        + "depth, status, moderation_reason, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'Committed synthetic comment', ?, ?, ?, ?, ?) RETURNING id",
                Long.class, postId, parentId, authorId, parentId == null ? 0 : 1, status,
                status.equals("VISIBLE") ? null : "Synthetic fixture moderation", Timestamp.from(createdAt),
                Timestamp.from(createdAt));
    }

    private int countComments(long authorId) {
        return jdbc.queryForObject("SELECT count(*) FROM community.comments WHERE author_id=?",
                Integer.class, authorId);
    }

    private void lockQuota(long authorId) {
        namedJdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))",
                Map.of("key", quotaKey(authorId)), resultSet -> null);
    }

    private int backendPid() {
        return jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
    }

    private void awaitQuotaWaiters(long authorId, int expected) {
        awaitCount(() -> quotaLocks(authorId, false), expected, "ungranted comment quota advisory locks");
    }

    private int grantedQuotaLocks(long authorId) {
        return quotaLocks(authorId, true);
    }

    private int quotaLocks(long authorId, boolean granted) {
        return jdbc.queryForObject("""
                WITH lock_key AS (SELECT hashtextextended(?, 0) AS value)
                SELECT count(*)::int FROM pg_locks, lock_key
                WHERE locktype='advisory' AND granted=? AND objsubid=1
                  AND database=(SELECT oid FROM pg_database WHERE datname=current_database())
                  AND classid=((value >> 32) & 4294967295)::oid
                  AND objid=(value & 4294967295)::oid
                """, Integer.class, quotaKey(authorId), granted);
    }

    private void awaitPostWriter(int holderPid) {
        try {
            awaitCount(() -> {
                // PostgreSQL caches activity within a transaction; refresh before every poll on this connection.
                jdbc.execute("SELECT pg_stat_clear_snapshot()");
                return jdbc.queryForObject("""
                SELECT count(*)::int FROM pg_stat_activity activity
                WHERE ?=ANY(pg_blocking_pids(activity.pid))
                  AND activity.query ILIKE '%community.posts%'
                  AND EXISTS (SELECT 1 FROM pg_locks locks
                    WHERE locks.pid=activity.pid AND NOT locks.granted
                      AND locks.locktype='transactionid')
                """, Integer.class, holderPid);
            }, 1, "request blocked on the held post row");
        } catch (AssertionError failure) {
            logPostLockFailure(holderPid);
            throw failure;
        }
    }

    private void awaitCount(IntSupplier observe, int expected, String description) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        int observed = -1;
        while (System.nanoTime() < deadline) {
            observed = observe.getAsInt();
            if (observed == expected) return;
            try {
                Thread.sleep(25);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while observing " + description, exception);
            }
        }
        throw new AssertionError("Expected exactly " + expected + " " + description
                + " within " + TIMEOUT.toSeconds() + " seconds; observed " + observed);
    }

    private CompletableFuture<MvcResult> submit(ExecutorService executor, Learner learner, String path,
            String content) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return mockMvc.perform(post(path).header("Authorization", "Bearer " + learner.accessToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of("content", content))))
                        .andReturn();
            } catch (Exception exception) {
                throw new IllegalStateException("Comment HTTP handler failed", exception);
            }
        }, executor);
    }

    private void logPostLockFailure(int holderPid) {
        try {
            jdbc.execute("SELECT pg_stat_clear_snapshot()", (PreparedStatementCallback<Boolean>) statement -> {
                statement.setQueryTimeout(2);
                return statement.execute();
            });
            List<Map<String, Object>> activity = diagnosticRows("""
                    SELECT pid, state, wait_event_type, wait_event,
                      pg_blocking_pids(pid)::text AS blocking_pids, left(query, 300) AS query
                    FROM pg_stat_activity
                    WHERE datname=current_database() AND backend_type='client backend'
                    ORDER BY pid
                    """);
            activity.forEach(row -> row.computeIfPresent("query", (key, value) -> safeSql(value.toString())));
            List<Map<String, Object>> locks = diagnosticRows("""
                    SELECT l.pid, l.locktype, l.mode, l.granted,
                      l.relation::regclass::text AS relation, l.transactionid::text AS transactionid
                    FROM pg_locks l
                    WHERE l.pid IN (SELECT pid FROM pg_stat_activity
                      WHERE datname=current_database() AND backend_type='client backend')
                    ORDER BY l.pid, l.locktype, l.granted
                    """);
            System.out.println("COMMENT_LOCK_FAILURE " + objectMapper.writeValueAsString(
                    Map.of("holderPid", holderPid, "observerPid", backendPid(), "activity", activity, "locks", locks)));
        } catch (Exception exception) {
            System.out.println("COMMENT_LOCK_FAILURE diagnosticsUnavailable=" + exception.getClass().getName());
        }
    }

    private List<Map<String, Object>> diagnosticRows(String sql) {
        return jdbc.query(connection -> {
            var statement = connection.prepareStatement(sql);
            statement.setQueryTimeout(2);
            return statement;
        }, new ColumnMapRowMapper());
    }


    private static String safeSql(String sql) {
        return sql.replaceAll("'(?:''|[^'])*(?:'|$)", "'[REDACTED]'")
                .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+", "[REDACTED]")
                .replaceAll("\\s+", " ").strip();
    }


    private MvcResult result(CompletableFuture<MvcResult> future) {
        try {
            return future.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Comment request did not finish within the bounded timeout", exception);
        }
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void assertQuotaError(MvcResult result) throws Exception {
        JsonNode error = body(result);
        assertThat(error.path("error_code").asText()).isEqualTo("TOO_MANY_REQUESTS");
        assertThat(error.path("message").asText()).isNotBlank();
        assertThat(error.path("request_id").asText()).isNotBlank();
        assertThat(error.path("retryAfterSeconds").asLong()).isEqualTo(EXPECTED_RETRY_SECONDS);
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private void stop(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
    }

    private static String quotaKey(long authorId) {
        return "community:comment-quota:" + authorId;
    }

    private static String rootPath(long postId) {
        return "/api/community/posts/" + postId + "/comments";
    }

    private static String replyPath(long parentId) {
        return "/api/community/comments/" + parentId + "/replies";
    }

    private Learner registerVerifyAndLogin() throws Exception {
        String email = "comment-quota-" + UUID.randomUUID() + "@example.test";
        String password = "Quota" + UUID.randomUUID() + "A9";
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password,
                                "confirmPassword", password, "displayName", "Comment Quota Learner",
                                "acceptTerms", true))))
                .andReturn();
        assertThat(registration.getResponse().getStatus()).isEqualTo(201);
        long userId = body(registration).path("userId").asLong();
        assertThat(userId).isPositive();

        JsonNode verificationMessage = null;
        for (JsonNode message : mailpit("/api/v1/messages").path("messages")) {
            if (message.toString().contains(email)) {
                verificationMessage = mailpit("/api/v1/message/" + message.path("ID").asText());
                break;
            }
        }
        assertThat(verificationMessage != null).as("verification email delivered through Mailpit").isTrue();
        Matcher matcher = VERIFY_LINK.matcher(verificationMessage.path("Text").asText());
        assertThat(matcher.find()).as("verification token delivered").isTrue();
        MvcResult verification = mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", matcher.group(1)))))
                .andReturn();
        assertThat(verification.getResponse().getStatus()).isEqualTo(200);
        MvcResult login = mockMvc.perform(post("/api/auth/login").header("X-Client-Type", "MOBILE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andReturn();
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        String token = body(login).path("accessToken").asText();
        assertThat(!token.isBlank()).as("mobile access token issued").isTrue();
        return new Learner(userId, token);
    }

    private JsonNode mailpit(String path) throws Exception {
        HttpResponse<String> response = HTTP_CLIENT.send(HttpRequest.newBuilder()
                        .uri(URI.create(mailpitBaseUrl + path)).timeout(TIMEOUT).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    private record Learner(long userId, String accessToken) { }
    private record Fixture(Learner author, Learner otherAuthor, long firstPost, long secondPost,
            long nonpublicPost, long parent) { }
    private record Requests(CompletableFuture<MvcResult> first, CompletableFuture<MvcResult> second) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean
        @Primary
        AdjustableClock commentQuotaClock() {
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
