package com.cnhsk.community.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Real PostgreSQL locks, committed fixtures, and separate HTTP request transactions. */
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CommentMutationConcurrencyIntegrationTest.MutationConfiguration.class)
class CommentMutationConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final Instant INITIAL_TIME = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final Pattern VERIFY_LINK = Pattern.compile("/verify-email\\?token=([A-Za-z0-9_-]+)");
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private MutationClock clock;
    @Autowired private MutationSqlCapture capture;
    @Value("${test.mailpit.base-url}") private String mailpitUrl;

    @BeforeEach
    void resetBeforeRequests() {
        clock.set(INITIAL_TIME);
        capture.clear();
    }

    @AfterEach
    void restoreTimeAfterRequests() {
        clock.set(INITIAL_TIME);
    }

    @ParameterizedTest(name = "expired edit observes time after held {0} lock")
    @ValueSource(strings = {"post", "comment"})
    void editWaitingForEitherLockExpiresWithoutAnUpdate(String heldLock) throws Exception {
        Fixture fixture = fixture(INITIAL_TIME.minus(Duration.ofMinutes(15)).plusSeconds(1));
        Map<String, Object> before = row(fixture.comment());
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<MvcResult> pending = transaction().execute(status -> {
                lock(heldLock, heldLock.equals("post") ? fixture.post() : fixture.comment());
                int holder = backendPid();
                CompletableFuture<MvcResult> request = submit(workers, "expired", fixture.author(),
                        put(path(fixture.comment())), "Must not be written");
                awaitBlocked(holder, heldLock.equals("post") ? "posts" : "comments");
                // Advance only after authentication and the actual PostgreSQL lock wait.
                clock.set(INITIAL_TIME.plusSeconds(1).plusNanos(1));
                return request;
            });
            assertError(result(pending), "COMMENT_EDIT_WINDOW_EXPIRED");
            assertThat(row(fixture.comment())).isEqualTo(before);
            assertThat(capture.updates("expired")).isZero();
            assertOwnershipAndLockOrder("expired");
            System.out.println("COMMENT_MUTATION_TIME_EVIDENCE lock=" + heldLock
                    + " transactionIdWait=true rawNanosecondExpired=true updates=0");
        } finally {
            stop(workers);
        }
    }

    @ParameterizedTest(name = "allowed edit stores microseconds with {0} extra nanoseconds")
    @ValueSource(longs = {0, 987})
    void allowedEditAfterCommentLockKeepsExactDeadlineAndTruncatesPersistedTimestamp(long extraNanos)
            throws Exception {
        Fixture fixture = fixture(extraNanos == 0
                ? INITIAL_TIME.minus(Duration.ofMinutes(15)).plusSeconds(1) : INITIAL_TIME.minusSeconds(10));
        Instant requestTime = INITIAL_TIME.plusSeconds(1).plusNanos(extraNanos);
        Instant savedTime = requestTime.truncatedTo(ChronoUnit.MICROS);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<MvcResult> pending = transaction().execute(status -> {
                lock("comment", fixture.comment());
                CompletableFuture<MvcResult> request = submit(workers, "boundary", fixture.author(),
                        put(path(fixture.comment())), "Exact deadline");
                awaitBlocked(backendPid(), "comments");
                clock.set(requestTime);
                return request;
            });
            MvcResult response = result(pending);
            assertThat(response.getResponse().getStatus()).isEqualTo(200);
            assertThat(Instant.parse(body(response).path("editedAt").asText()))
                    .isEqualTo(savedTime);
            assertThat(row(fixture.comment()).get("updated_at"))
                    .isEqualTo(Timestamp.from(savedTime));
            assertThat(capture.updates("boundary")).isEqualTo(1);
        } finally {
            stop(workers);
        }
    }

    @Test
    void concurrentPutsSerializeCompleteWritesAndLastExecutingContentWins() throws Exception {
        Fixture fixture = fixture(INITIAL_TIME.minusSeconds(10));
        Map<String, Object> before = row(fixture.comment());
        Map<String, Object> postBefore = postRow(fixture.post());
        String firstContent = "First complete value " + "甲".repeat(200);
        String secondContent = "Second complete value " + "乙".repeat(200);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Requests pending = queuedBehindComment(workers, fixture,
                    put(path(fixture.comment())), firstContent,
                    put(path(fixture.comment())), secondContent);
            MvcResult first = result(pending.first());
            MvcResult second = result(pending.second());
            assertThat(first.getResponse().getStatus()).isEqualTo(200);
            assertThat(second.getResponse().getStatus()).isEqualTo(200);
            assertThat(body(first).path("content").asText()).isEqualTo(firstContent);
            assertThat(body(second).path("content").asText()).isEqualTo(secondContent);
            Map<String, Object> saved = row(fixture.comment());
            assertThat(saved.get("content")).isEqualTo(secondContent);
            assertThat(saved.get("updated_at")).isEqualTo(Timestamp.from(INITIAL_TIME));
            assertProtectedFields(before, saved);
            assertThat(postRow(fixture.post())).isEqualTo(postBefore);
            assertThat(capture.updates("first")).isEqualTo(1);
            assertThat(capture.updates("second")).isEqualTo(1);
            assertOwnershipAndLockOrder("first");
            assertOwnershipAndLockOrder("second");
            System.out.println("COMMENT_MUTATION_SERIAL_EVIDENCE firstCommentWait=true secondPostWait=true"
                    + " statuses=200,200 completeWrites=true lastExecutingWins=true");
        } finally {
            stop(workers);
        }
    }

    @Test
    void concurrentDeletesReturn204WithOneUpdateAndStableFirstDeletionTimestamp() throws Exception {
        Fixture fixture = fixture(INITIAL_TIME.minusSeconds(10));
        Map<String, Object> before = row(fixture.comment());
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Requests pending = queuedBehindComment(workers, fixture,
                    delete(path(fixture.comment())), null, delete(path(fixture.comment())), null);
            assertThat(result(pending.first()).getResponse().getStatus()).isEqualTo(204);
            assertThat(result(pending.second()).getResponse().getStatus()).isEqualTo(204);
            Map<String, Object> deleted = row(fixture.comment());
            assertDeleted(fixture, deleted, before.get("content"));
            assertThat(deleted.get("updated_at")).isEqualTo(Timestamp.from(INITIAL_TIME));
            assertThat(capture.updates("first")).isEqualTo(1);
            assertThat(capture.updates("second")).isZero();
            clock.set(INITIAL_TIME.plusSeconds(30));
            assertThat(result(submit(workers, "repeated", fixture.author(),
                    delete(path(fixture.comment())), null)).getResponse().getStatus()).isEqualTo(204);
            assertThat(row(fixture.comment())).isEqualTo(deleted);
            assertThat(capture.updates("repeated")).isZero();
            System.out.println("COMMENT_MUTATION_DELETE_EVIDENCE statuses=204,204 sqlUpdates=1"
                    + " repeatedDeleteNoOp=true firstTimestampStable=true");
        } finally {
            stop(workers);
        }
    }

    @Test
    void deleteBeforePutRejectsStaleEditWithoutResurrection() throws Exception {
        Fixture fixture = fixture(INITIAL_TIME.minusSeconds(10));
        Object originalContent = row(fixture.comment()).get("content");
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Requests pending = queuedBehindComment(workers, fixture,
                    delete(path(fixture.comment())), null, put(path(fixture.comment())), "No resurrection");
            assertThat(result(pending.first()).getResponse().getStatus()).isEqualTo(204);
            assertError(result(pending.second()), "COMMENT_NOT_EDITABLE");
            assertDeleted(fixture, row(fixture.comment()), originalContent);
            assertThat(capture.updates("first")).isEqualTo(1);
            assertThat(capture.updates("second")).isZero();
            System.out.println("COMMENT_MUTATION_RACE_EVIDENCE order=DELETE,PUT statuses=204,409 resurrection=false");
        } finally {
            stop(workers);
        }
    }

    @Test
    void putBeforeDeletePreservesEditedContentInDeletedRow() throws Exception {
        Fixture fixture = fixture(INITIAL_TIME.minusSeconds(10));
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Requests pending = queuedBehindComment(workers, fixture,
                    put(path(fixture.comment())), "Edited before deletion", delete(path(fixture.comment())), null);
            MvcResult edited = result(pending.first());
            assertThat(edited.getResponse().getStatus()).isEqualTo(200);
            assertThat(body(edited).path("status").asText()).isEqualTo("VISIBLE");
            assertThat(result(pending.second()).getResponse().getStatus()).isEqualTo(204);
            assertDeleted(fixture, row(fixture.comment()), "Edited before deletion");
            assertThat(capture.updates("first")).isEqualTo(1);
            assertThat(capture.updates("second")).isEqualTo(1);
            System.out.println("COMMENT_MUTATION_RACE_EVIDENCE order=PUT,DELETE statuses=200,204");
        } finally {
            stop(workers);
        }
    }

    @Test
    void concurrentReplyAndParentDeleteFinishWithoutDeadlockAndPreserveReferences() throws Exception {
        Fixture fixture = fixture(INITIAL_TIME.minusSeconds(10));
        Map<String, Object> childBefore = row(fixture.child());
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Requests pending = transaction().execute(status -> {
                lock("comment", fixture.comment());
                CompletableFuture<MvcResult> deletion = submit(workers, "first", fixture.author(),
                        delete(path(fixture.comment())), null);
                int deleter = awaitBlocked(backendPid(), "comments");
                CompletableFuture<MvcResult> reply = submit(workers, "reply", fixture.other(),
                        post(path(fixture.comment()) + "/replies"), "Reply racing parent deletion");
                awaitBlocked(deleter, "posts");
                return new Requests(deletion, reply);
            });
            assertThat(result(pending.first()).getResponse().getStatus()).isEqualTo(204);
            MvcResult reply = result(pending.second());
            assertThat(reply.getResponse().getStatus()).isEqualTo(201);
            long replyId = body(reply).path("id").asLong();
            assertThat(replyId).isPositive();
            assertThat(body(reply).path("parentId").asLong()).isEqualTo(fixture.comment());
            Map<String, Object> savedReply = row(replyId);
            assertThat(((Number) savedReply.get("parent_id")).longValue()).isEqualTo(fixture.comment());
            assertThat(((Number) savedReply.get("post_id")).longValue()).isEqualTo(fixture.post());
            assertThat(savedReply.get("status")).isEqualTo("VISIBLE");
            assertThat(row(fixture.comment()).get("status")).isEqualTo("DELETED");
            assertThat(row(fixture.child())).isEqualTo(childBefore);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM community.comments WHERE parent_id=?",
                    Integer.class, fixture.comment())).isEqualTo(2);
            System.out.println("COMMENT_MUTATION_REPLY_EVIDENCE commentThenPostWait=true statuses=204,201"
                    + " deadlock=false existingChildPreserved=true replyReferencePreserved=true");
        } finally {
            stop(workers);
        }
    }

    @Test
    void statusChangedByLockHolderIsRecheckedBeforeWindowAndCannotBeOverwritten() throws Exception {
        Fixture fixture = fixture(INITIAL_TIME.minusSeconds(10));
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<MvcResult> pending = transaction().execute(status -> {
                lock("comment", fixture.comment());
                jdbc.update("UPDATE community.comments SET status='HIDDEN', moderated_by=?, "
                        + "moderation_reason='Synthetic moderation' WHERE id=?",
                        fixture.other().userId(), fixture.comment());
                CompletableFuture<MvcResult> edit = submit(workers, "stale", fixture.author(),
                        put(path(fixture.comment())), "Must not overwrite moderation");
                awaitBlocked(backendPid(), "comments");
                clock.set(INITIAL_TIME.plus(Duration.ofMinutes(16)));
                return edit;
            });
            assertError(result(pending), "COMMENT_NOT_EDITABLE");
            Map<String, Object> saved = row(fixture.comment());
            assertThat(saved.get("status")).isEqualTo("HIDDEN");
            assertThat(saved.get("content")).isEqualTo("Committed synthetic parent");
            assertThat(((Number) saved.get("moderated_by")).longValue()).isEqualTo(fixture.other().userId());
            assertThat(saved.get("moderation_reason")).isEqualTo("Synthetic moderation");
            assertThat(capture.updates("stale")).isZero();
            assertOwnershipAndLockOrder("stale");
            System.out.println("COMMENT_MUTATION_STATUS_EVIDENCE statusRecheckedAfterCommentLock=true"
                    + " notEditableBeforeExpired=true updates=0");
        } finally {
            stop(workers);
        }
    }

    private Requests queuedBehindComment(ExecutorService workers, Fixture fixture,
            MockHttpServletRequestBuilder firstRequest, String firstContent,
            MockHttpServletRequestBuilder secondRequest, String secondContent) {
        return transaction().execute(status -> {
            lock("comment", fixture.comment());
            CompletableFuture<MvcResult> first = submit(workers, "first", fixture.author(),
                    firstRequest, firstContent);
            int firstPid = awaitBlocked(backendPid(), "comments");
            CompletableFuture<MvcResult> second = submit(workers, "second", fixture.author(),
                    secondRequest, secondContent);
            int secondPid = awaitBlocked(firstPid, "posts");
            assertThat(firstPid).isNotEqualTo(secondPid);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE pid IN (?, ?) "
                    + "AND locktype='advisory'", Integer.class, firstPid, secondPid)).isZero();
            return new Requests(first, second);
        });
    }

    private int awaitBlocked(int blocker, String table) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            // Activity snapshots are cached per transaction; the holder must refresh every poll.
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            List<Integer> waiting = jdbc.queryForList("""
                    SELECT activity.pid FROM pg_stat_activity activity
                    WHERE ?=ANY(pg_blocking_pids(activity.pid))
                      AND activity.query ILIKE ?
                      AND EXISTS (SELECT 1 FROM pg_locks locks WHERE locks.pid=activity.pid
                        AND NOT locks.granted AND locks.locktype='transactionid')
                    """, Integer.class, blocker, "%community." + table + "%");
            if (waiting.size() == 1) return waiting.get(0);
            try {
                Thread.sleep(25);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while observing PostgreSQL row lock", exception);
            }
        }
        throw new AssertionError("No transactionid waiter blocked by backend " + blocker
                + " on community." + table + " within " + TIMEOUT.toSeconds() + " seconds");
    }

    private void assertOwnershipAndLockOrder(String key) {
        List<String> statements = capture.statements(key);
        List<String> commentReads = statements.stream()
                .filter(sql -> sql.startsWith("select") && sql.contains("from community.comments")).toList();
        assertThat(commentReads).hasSize(2);
        String ownership = commentReads.get(0);
        assertThat(ownership.substring(0, ownership.indexOf(" from ")))
                .contains(".post_id").doesNotContain(",", "content", "status", "updated_at");
        assertThat(ownership).contains("author_id").doesNotContain("for update", "for no key update");
        String lockedComment = commentReads.get(1);
        assertThat(lockedComment).contains("author_id", "post_id");
        assertThat(isWriteLock(lockedComment)).isTrue();
        List<String> postReads = statements.stream()
                .filter(sql -> sql.startsWith("select") && sql.contains("from community.posts")).toList();
        assertThat(postReads).hasSize(1);
        assertThat(isWriteLock(postReads.get(0))).isTrue();
        assertThat(statements.indexOf(ownership)).isLessThan(statements.indexOf(postReads.get(0)));
        assertThat(statements.indexOf(postReads.get(0))).isLessThan(statements.indexOf(lockedComment));
    }

    private static boolean isWriteLock(String sql) {
        return sql.contains("for update") || sql.contains("for no key update");
    }

    private void assertProtectedFields(Map<String, Object> before, Map<String, Object> after) {
        for (String field : List.of("id", "post_id", "parent_id", "author_id", "depth", "status",
                "moderated_by", "moderation_reason", "created_at")) {
            assertThat(after.get(field)).as("protected comment field %s", field).isEqualTo(before.get(field));
        }
    }

    private void assertDeleted(Fixture fixture, Map<String, Object> saved, Object content) {
        assertThat(saved.get("status")).isEqualTo("DELETED");
        assertThat(saved.get("content")).isEqualTo(content);
        assertThat(((Number) saved.get("moderated_by")).longValue()).isEqualTo(fixture.author().userId());
        assertThat(saved.get("moderation_reason")).isEqualTo("AUTHOR_DELETED");
        assertThat(((Number) saved.get("post_id")).longValue()).isEqualTo(fixture.post());
        assertThat(saved.get("parent_id")).isNull();
        assertThat(((Number) saved.get("depth")).intValue()).isZero();
    }

    private Fixture fixture(Instant createdAt) throws Exception {
        Learner author = registerVerifyAndLogin();
        Learner other = registerVerifyAndLogin();
        return transaction().execute(status -> {
            long postId = jdbc.queryForObject("INSERT INTO community.posts(author_id,category,title,content,status,"
                    + "published_at,created_at,updated_at) VALUES (?,'QUESTION','Mutation fixture',"
                    + "'Synthetic community post','APPROVED',?,?,?) RETURNING id", Long.class,
                    other.userId(), Timestamp.from(INITIAL_TIME), Timestamp.from(INITIAL_TIME),
                    Timestamp.from(INITIAL_TIME));
            long comment = seedComment(postId, null, author.userId(), createdAt, "Committed synthetic parent");
            long child = seedComment(postId, comment, other.userId(), createdAt.plusSeconds(1),
                    "Committed synthetic child");
            return new Fixture(author, other, postId, comment, child);
        });
    }

    private long seedComment(long postId, Long parentId, long authorId, Instant createdAt, String content) {
        return jdbc.queryForObject("INSERT INTO community.comments(post_id,parent_id,author_id,content,depth,"
                + "status,created_at,updated_at) VALUES (?,?,?,?,?,'VISIBLE',?,?) RETURNING id", Long.class,
                postId, parentId, authorId, content, parentId == null ? 0 : 1,
                Timestamp.from(createdAt), Timestamp.from(createdAt));
    }

    private void lock(String table, long id) {
        String sql = table.equals("post") ? "SELECT id FROM community.posts WHERE id=? FOR UPDATE"
                : "SELECT id FROM community.comments WHERE id=? FOR UPDATE";
        jdbc.queryForObject(sql, Long.class, id);
    }

    private int backendPid() {
        return jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
    }

    private Map<String, Object> row(long id) {
        return jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", id);
    }

    private Map<String, Object> postRow(long id) {
        return jdbc.queryForMap("SELECT * FROM community.posts WHERE id=?", id);
    }

    private TransactionTemplate transaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return transaction;
    }

    private CompletableFuture<MvcResult> submit(ExecutorService workers, String key, Learner learner,
            MockHttpServletRequestBuilder request, String content) {
        return CompletableFuture.supplyAsync(() -> {
            capture.start(key);
            try {
                request.header("Authorization", "Bearer " + learner.accessToken());
                if (content != null) {
                    request.contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("content", content)));
                }
                return mvc.perform(request).andReturn();
            } catch (Exception exception) {
                throw new IllegalStateException("Comment mutation HTTP request failed", exception);
            } finally {
                capture.stop();
            }
        }, workers);
    }

    private MvcResult result(CompletableFuture<MvcResult> request) {
        try {
            return request.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Comment request did not finish within the bounded timeout", exception);
        }
    }

    private void assertError(MvcResult response, String code) throws Exception {
        assertThat(response.getResponse().getStatus()).isEqualTo(409);
        JsonNode error = body(response);
        assertThat(error.path("error_code").asText()).isEqualTo(code);
        assertThat(error.path("message").asText()).isNotBlank();
        assertThat(error.path("request_id").asText()).isNotBlank();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private void stop(ExecutorService workers) throws InterruptedException {
        workers.shutdownNow();
        assertThat(workers.awaitTermination(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
    }

    private static String path(long id) {
        return "/api/community/comments/" + id;
    }

    private Learner registerVerifyAndLogin() throws Exception {
        String email = "comment-mutation-" + UUID.randomUUID() + "@example.test";
        String password = "Mutation" + UUID.randomUUID() + "A9";
        MvcResult registration = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", password,
                        "confirmPassword", password, "displayName", "Synthetic Mutation Learner",
                        "acceptTerms", true)))).andReturn();
        assertThat(registration.getResponse().getStatus()).isEqualTo(201);
        long userId = body(registration).path("userId").asLong();
        assertThat(userId).isPositive();
        JsonNode verification = null;
        for (JsonNode message : mailpit("/api/v1/messages").path("messages")) {
            if (message.toString().contains(email)) {
                verification = mailpit("/api/v1/message/" + message.path("ID").asText());
                break;
            }
        }
        assertThat(verification != null).as("verification email delivered through Mailpit").isTrue();
        Matcher token = VERIFY_LINK.matcher(verification.path("Text").asText());
        assertThat(token.find()).as("verification token delivered").isTrue();
        MvcResult verified = mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("token", token.group(1))))).andReturn();
        assertThat(verified.getResponse().getStatus()).isEqualTo(200);
        MvcResult login = mvc.perform(post("/api/auth/login").header("X-Client-Type", "MOBILE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", password)))).andReturn();
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        String accessToken = body(login).path("accessToken").asText();
        assertThat(!accessToken.isBlank()).as("mobile access token issued").isTrue();
        return new Learner(userId, accessToken);
    }

    private JsonNode mailpit(String path) throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder().uri(URI.create(mailpitUrl + path))
                .timeout(TIMEOUT).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body());
    }

    private record Learner(long userId, String accessToken) { }
    private record Fixture(Learner author, Learner other, long post, long comment, long child) { }
    private record Requests(CompletableFuture<MvcResult> first, CompletableFuture<MvcResult> second) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class MutationConfiguration {
        @Bean @Primary MutationClock mutationClock() { return new MutationClock(); }
        @Bean MutationSqlCapture mutationSqlCapture() { return new MutationSqlCapture(); }
        @Bean HibernatePropertiesCustomizer mutationStatementInspector(MutationSqlCapture capture) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", capture);
        }
    }

    static final class MutationClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(INITIAL_TIME);
        void set(Instant value) { now.set(value); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return now.get(); }
    }

    static final class MutationSqlCapture implements StatementInspector {
        private final ThreadLocal<String> active = new ThreadLocal<>();
        private final Map<String, List<String>> statements = new ConcurrentHashMap<>();
        void clear() { statements.clear(); }
        void start(String key) {
            statements.put(key, new CopyOnWriteArrayList<>());
            active.set(key);
        }
        void stop() { active.remove(); }
        List<String> statements(String key) { return List.copyOf(statements.get(key)); }
        long updates(String key) {
            return statements(key).stream().filter(sql -> sql.startsWith("update community.comments ")).count();
        }
        @Override public String inspect(String sql) {
            String key = active.get();
            if (key != null) statements.get(key).add(sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip());
            return sql;
        }
    }
}
