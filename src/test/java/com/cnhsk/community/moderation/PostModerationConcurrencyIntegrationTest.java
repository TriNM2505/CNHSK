package com.cnhsk.community.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.cnhsk.community.post.PostRepository;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Real conditional UPDATE contention, committed fixtures and separate HTTP transactions. */
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostModerationConcurrencyIntegrationTest.ModerationConfiguration.class)
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.minimum-idle=4",
        "spring.datasource.hikari.connection-timeout=20000"
})
class PostModerationConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final Instant SAVED_TIME = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final Instant REQUEST_TIME = SAVED_TIME.plusNanos(987);
    private static final Instant CREATED_TIME = SAVED_TIME.minus(Duration.ofDays(1));
    private static final Instant OLD_PUBLICATION = CREATED_TIME.minusSeconds(60);
    private static final String OLD_REASON = "Previous synthetic review";
    private static final String REASON = "Synthetic rejection reason";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ModerationSqlCapture capture;
    @PersistenceContext private EntityManager entityManager;
    @MockitoSpyBean private PostRepository posts;
    @MockitoSpyBean private AuditService audit;

    @ParameterizedTest(name = "conditional UPDATE winner approves={0}")
    @ValueSource(booleans = {true, false})
    void FR041_FR045_competingDecisionsCommitExactlyOneConsistentWinner(boolean approveWinner) throws Exception {
        Fixture fixture = fixture(!approveWinner);
        Hold hold = holdAfterUpdate(fixture, approveWinner);
        holdAudit(fixture, fixture.first(), hold, false);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<MvcResult> winner = submit(workers, "winner", fixture.first(), fixture, approveWinner);
            await(hold.updated, "winner's successful real conditional UPDATE");
            CompletableFuture<MvcResult> loser = submit(workers, "loser", fixture.second(), fixture, !approveWinner);
            int contenderPid = awaitUpdateWaiter(hold.identity.get().pid());
            assertThat(contenderPid).isNotEqualTo(hold.identity.get().pid());
            hold.release.countDown();
            MvcResult winningResponse = result(winner);
            assertDecision(winningResponse, fixture, fixture.first(), approveWinner);
            assertError(result(loser), 409, "POST_NOT_PENDING");
            assertSingleAudit(fixture, fixture.first(), approveWinner);
            assertUpdateWithoutExplicitLocks("winner");
            assertUpdateWithoutExplicitLocks("loser");
            System.out.println("POST_MODERATION_RACE_EVIDENCE winnerApprove=" + approveWinner
                    + " statuses=200,409 actualTransactionIdUpdateWait=true auditRows=1");
        } finally {
            hold.release.countDown();
            stop(workers);
        }
    }

    @ParameterizedTest(name = "audit failure releases waiting opposite decision, first approves={0}")
    @ValueSource(booleans = {true, false})
    void FR042_FR044_waitingContenderCanDecideAfterPostAndRealAuditInsertRollback(boolean approveFirst)
            throws Exception {
        Fixture fixture = fixture(true);
        Hold hold = holdAfterUpdate(fixture, approveFirst);
        holdAudit(fixture, fixture.first(), hold, true);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<MvcResult> failing = submit(workers, "failing", fixture.first(), fixture, approveFirst);
            await(hold.updated, "first decision updated the row");
            CompletableFuture<MvcResult> contender = submit(workers, "contender", fixture.second(), fixture,
                    !approveFirst);
            awaitUpdateWaiter(hold.identity.get().pid());
            hold.release.countDown();
            assertError(result(failing), 500, "INTERNAL_ERROR");
            assertDecision(result(contender), fixture, fixture.second(), !approveFirst);
            assertSingleAudit(fixture, fixture.second(), !approveFirst);
            assertThat(hold.auditIdentity.get()).isEqualTo(hold.identity.get());
            assertUpdateWithoutExplicitLocks("failing");
            assertUpdateWithoutExplicitLocks("contender");
            System.out.println("POST_MODERATION_ROLLBACK_RACE_EVIDENCE firstApprove=" + approveFirst
                    + " statuses=500,200 firstRealAuditRolledBack=true waiterDecidedPending=true");
        } finally {
            hold.release.countDown();
            stop(workers);
        }
    }

    @Test
    void FR044_databaseFailureAfterSuccessfulUpdateAndRealAuditInsertRollsBackBoth() throws Exception {
        Fixture fixture = fixture(false);
        Map<String, Object> before = row(fixture.post());
        Hold hold = holdAfterUpdate(fixture, true);
        hold.release.countDown();
        holdAudit(fixture, fixture.first(), hold, true);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            assertError(result(submit(worker, "rollback", fixture.first(), fixture, true)), 500, "INTERNAL_ERROR");
            assertThat(hold.identity.get()).isNotNull();
            assertThat(hold.auditIdentity.get()).isEqualTo(hold.identity.get());
            assertThat(row(fixture.post())).isEqualTo(before);
            assertThat(auditRows(fixture)).isEmpty();
        } finally {
            hold.release.countDown();
            stop(worker);
        }
    }

    @Test
    void FR041_approvalKeepsExistingPublicationAndReasonAndReturnsFreshDatabaseState() throws Exception {
        Fixture fixture = fixture(true);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            assertDecision(result(submit(worker, "preserve", fixture.first(), fixture, true)),
                    fixture, fixture.first(), true);
            assertSingleAudit(fixture, fixture.first(), true);
            assertUpdateWithoutExplicitLocks("preserve");
        } finally {
            stop(worker);
        }
    }

    @ParameterizedTest(name = "real ck_post_self on conditional UPDATE approves={0}")
    @ValueSource(booleans = {true, false})
    void FR041_FR043_actualSelfConstraintAfterAuthorRaceMapsOnlyNamedCheckToForbidden(boolean approve)
            throws Exception {
        Fixture fixture = fixture(false);
        CountDownLatch authorRead = new CountDownLatch(1);
        CountDownLatch authorChanged = new CountDownLatch(1);
        AtomicReference<ConstraintFailure> failure = new AtomicReference<>();
        doAnswer(invocation -> {
            Object author = repositoryDelegate(invocation);
            authorRead.countDown();
            await(authorChanged, "committed author change after scalar author SELECT");
            return author;
        }).when(posts).findModerationAuthorId(fixture.post());
        captureConstraint(fixture, fixture.first(), approve, failure, false);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<MvcResult> pending = submit(worker, "self-check", fixture.first(), fixture, approve);
            await(authorRead, "actual scalar author SELECT before self guard");
            transaction().executeWithoutResult(status -> assertThat(jdbc.update(
                    "UPDATE community.posts SET author_id=? WHERE id=?", fixture.first().id(), fixture.post()))
                    .isEqualTo(1));
            Map<String, Object> changed = row(fixture.post());
            authorChanged.countDown();
            assertError(result(pending), 403, "SELF_MODERATION");
            assertThat(failure.get()).isEqualTo(new ConstraintFailure("ck_post_self", "23514"));
            assertThat(row(fixture.post())).isEqualTo(changed);
            assertThat(auditRows(fixture)).isEmpty();
            assertUpdateWithoutExplicitLocks("self-check");
        } finally {
            authorChanged.countDown();
            stop(worker);
        }
    }

    @Test
    void unrelatedActualPostCheckRemainsGenericServerErrorAndRollsBack() throws Exception {
        Fixture fixture = fixture(false);
        Map<String, Object> before = row(fixture.post());
        AtomicReference<ConstraintFailure> failure = new AtomicReference<>();
        // Fault injection changes only the bound timestamp; the real native UPDATE violates ck_post_pub.
        captureConstraint(fixture, fixture.first(), true, failure, true);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            assertError(result(submit(worker, "other-check", fixture.first(), fixture, true)), 500, "INTERNAL_ERROR");
            assertThat(failure.get()).isEqualTo(new ConstraintFailure("ck_post_pub", "23514"));
            assertThat(row(fixture.post())).isEqualTo(before);
            assertThat(auditRows(fixture)).isEmpty();
        } finally {
            stop(worker);
        }
    }

    private Hold holdAfterUpdate(Fixture fixture, boolean approve) {
        Hold hold = new Hold();
        org.mockito.stubbing.Answer<Object> answer = invocation -> {
            Object affected = repositoryDelegate(invocation);
            assertThat(affected).isEqualTo(1);
            hold.identity.set(identity());
            assertThat(hold.identity.get().isolation()).isEqualTo("read committed");
            assertThat(row(fixture.post()).get("status")).isEqualTo(approve ? "APPROVED" : "REJECTED");
            return affected;
        };
        if (approve) doAnswer(answer).when(posts).approvePending(eq(fixture.post()), eq(fixture.first().id()), any());
        else doAnswer(answer).when(posts).rejectPending(eq(fixture.post()), eq(fixture.first().id()), eq(REASON), any());
        return hold;
    }

    private void holdAudit(Fixture fixture, Moderator moderator, Hold hold, boolean fail) {
        doAnswer(invocation -> {
            TransactionIdentity beforeAudit = identity();
            assertThat(beforeAudit).isEqualTo(hold.identity.get());
            // A class spy can invoke its real method; save performs the real IDENTITY audit INSERT.
            invocation.callRealMethod();
            entityManager.flush();
            hold.auditIdentity.set(identity());
            assertThat(hold.auditIdentity.get()).isEqualTo(beforeAudit);
            assertThat(((Number) entityManager.createNativeQuery("SELECT pg_backend_pid()")
                    .getSingleResult()).intValue()).isEqualTo(beforeAudit.pid());
            assertThat(auditRows(fixture)).hasSize(1);
            hold.updated.countDown();
            await(hold.release, "release after real conditional UPDATE and audit INSERT, before commit");
            System.out.println("POST_MODERATION_TRANSACTION_EVIDENCE backendPid=" + beforeAudit.pid()
                    + " transactionId=" + beforeAudit.transactionId() + " updateAndAuditSameTransaction=true");
            if (fail) jdbc.execute("SELECT 1 / 0");
            return null;
        }).when(audit).recordDetail(eq(Long.valueOf(moderator.id())), any(), eq("POST"), eq(fixture.post()), any());
    }

    private void captureConstraint(Fixture fixture, Moderator moderator, boolean approve,
            AtomicReference<ConstraintFailure> failure, boolean nullTimestamp) {
        org.mockito.stubbing.Answer<Object> answer = invocation -> {
            if (nullTimestamp) invocation.getArguments()[2] = null;
            try {
                return repositoryDelegate(invocation);
            } catch (Throwable exception) {
                for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                    if (cause instanceof ConstraintViolationException violation) {
                        failure.set(new ConstraintFailure(violation.getConstraintName(), violation.getSQLState()));
                        break;
                    }
                }
                throw exception;
            }
        };
        if (approve) doAnswer(answer).when(posts).approvePending(eq(fixture.post()), eq(moderator.id()), any());
        else doAnswer(answer).when(posts).rejectPending(eq(fixture.post()), eq(moderator.id()), eq(REASON), any());
    }

    private void assertDecision(MvcResult response, Fixture fixture, Moderator moderator, boolean approve)
            throws Exception {
        assertThat(response.getResponse().getStatus()).isEqualTo(200);
        Map<String, Object> saved = row(fixture.post());
        String status = approve ? "APPROVED" : "REJECTED";
        String reason = approve ? OLD_REASON : REASON;
        Instant published = fixture.published() != null ? fixture.published() : approve ? SAVED_TIME : null;
        assertThat(saved.get("status")).isEqualTo(status);
        assertThat(((Number) saved.get("reviewed_by")).longValue()).isEqualTo(moderator.id());
        assertThat(saved.get("reviewed_at")).isEqualTo(Timestamp.from(SAVED_TIME));
        assertThat(saved.get("review_reason")).isEqualTo(reason);
        assertThat(saved.get("published_at")).isEqualTo(published == null ? null : Timestamp.from(published));
        assertThat(saved.get("created_at")).isEqualTo(Timestamp.from(CREATED_TIME));
        assertThat(saved.get("updated_at")).isEqualTo(Timestamp.from(CREATED_TIME));
        assertThat(((Number) saved.get("author_id")).longValue()).isEqualTo(fixture.author());
        assertThat(saved.get("category")).isEqualTo("QUESTION");
        assertThat(saved.get("title")).isEqualTo("Post moderation concurrency fixture");
        assertThat(saved.get("content")).isEqualTo("Synthetic moderation content");
        assertThat(saved.get("comments_locked")).isEqualTo(true);
        JsonNode json = body(response);
        assertThat(json.path("id").asLong()).isEqualTo(fixture.post());
        assertThat(json.path("title").asText()).isEqualTo(saved.get("title"));
        assertThat(json.path("content").asText()).isEqualTo(saved.get("content"));
        assertThat(json.path("category").asText()).isEqualTo(saved.get("category"));
        assertThat(json.path("status").asText()).isEqualTo(saved.get("status"));
        assertThat(json.path("reviewReason").asText()).isEqualTo(saved.get("review_reason"));
        assertThat(json.path("reviewedBy").path("id").asLong()).isEqualTo(moderator.id());
        assertThat(json.path("reviewedBy").path("displayName").asText()).isEqualTo(moderator.name());
        assertThat(json.path("author").path("id").asLong()).isEqualTo(fixture.author());
        assertThat(Instant.parse(json.path("reviewedAt").asText())).isEqualTo(SAVED_TIME);
        assertThat(Instant.parse(json.path("createdAt").asText())).isEqualTo(CREATED_TIME);
        assertThat(Instant.parse(json.path("updatedAt").asText())).isEqualTo(CREATED_TIME);
        if (published == null) assertThat(json.path("publishedAt").isNull() || json.path("publishedAt").isMissingNode()).isTrue();
        else assertThat(Instant.parse(json.path("publishedAt").asText())).isEqualTo(published);
    }

    private void assertSingleAudit(Fixture fixture, Moderator moderator, boolean approve) throws Exception {
        List<Map<String, Object>> rows = auditRows(fixture);
        assertThat(rows).hasSize(1);
        Map<String, Object> saved = rows.get(0);
        assertThat(((Number) saved.get("actor_id")).longValue()).isEqualTo(moderator.id());
        assertThat(saved.get("action")).isEqualTo(approve ? "COMMUNITY_POST_APPROVE" : "COMMUNITY_POST_REJECT");
        assertThat(saved.get("target_type")).isEqualTo("POST");
        assertThat(((Number) saved.get("target_id")).longValue()).isEqualTo(fixture.post());
        assertThat(mapper.readTree(saved.get("detail").toString()))
                .isEqualTo(mapper.valueToTree(approve ? Map.of() : Map.of("reason", REASON)));
    }

    private void assertUpdateWithoutExplicitLocks(String key) {
        List<String> sql = capture.statements(key);
        assertThat(sql).noneMatch(statement -> statement.contains("for update")
                || statement.contains("for no key update") || statement.contains("for share")
                || statement.contains("for key share") || statement.contains("pg_advisory"));
        List<String> updates = sql.stream().filter(statement -> statement.startsWith("update community.posts ")).toList();
        assertThat(updates).hasSize(1);
        assertThat(updates.get(0).replaceAll("\\s+", "")).contains("status='pending'", "where", "id");
    }

    private int awaitUpdateWaiter(int holderPid) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            List<Integer> waiters = jdbc.queryForList("""
                    SELECT activity.pid FROM pg_stat_activity activity
                    WHERE activity.datname=current_database() AND activity.backend_type='client backend'
                      AND ?=ANY(pg_blocking_pids(activity.pid))
                      AND activity.query ILIKE '%update%community.posts%'
                      AND EXISTS (SELECT 1 FROM pg_locks locks WHERE locks.pid=activity.pid
                        AND NOT locks.granted AND locks.locktype='transactionid')
                    """, Integer.class, holderPid);
            if (waiters.size() == 1) {
                assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE pid IN (?, ?) "
                        + "AND locktype='advisory'", Integer.class, holderPid, waiters.get(0))).isZero();
                System.out.println("POST_MODERATION_LOCK_EVIDENCE holderPid=" + holderPid
                        + " waiterPid=" + waiters.get(0) + " conditionalUpdateTransactionIdWait=true");
                return waiters.get(0);
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while observing conditional UPDATE wait", exception);
            }
        }
        throw new AssertionError("No actual transactionid waiter at conditional UPDATE within " + TIMEOUT);
    }

    private Fixture fixture(boolean published) throws Exception {
        Moderator author = register(false);
        Moderator first = register(true);
        Moderator second = register(true);
        long postId = transaction().execute(status -> jdbc.queryForObject("""
                INSERT INTO community.posts(author_id,category,title,content,status,review_reason,
                    comments_locked,published_at,created_at,updated_at)
                VALUES (?,'QUESTION','Post moderation concurrency fixture','Synthetic moderation content',
                    'PENDING',?,true,?,?,?) RETURNING id
                """, Long.class, author.id(), OLD_REASON, published ? Timestamp.from(OLD_PUBLICATION) : null,
                Timestamp.from(CREATED_TIME), Timestamp.from(CREATED_TIME)));
        return new Fixture(author.id(), first, second, postId, published ? OLD_PUBLICATION : null);
    }

    private Moderator register(boolean moderator) throws Exception {
        String email = "post-moderation-" + UUID.randomUUID() + "@example.test";
        String password = "Moderation" + UUID.randomUUID() + "A9";
        String name = "Synthetic Moderator " + UUID.randomUUID();
        MvcResult registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", password,
                        "confirmPassword", password, "displayName", name, "acceptTerms", true)))).andReturn();
        assertThat(registered.getResponse().getStatus()).isEqualTo(201);
        long id = body(registered).path("userId").asLong();
        assertThat(id).isPositive();
        if (moderator) transaction().executeWithoutResult(status -> jdbc.update(
                "INSERT INTO auth.user_roles(user_id,role_code) VALUES (?,'MODERATOR')", id));
        return new Moderator(id, jwt.issueAccessToken(id, List.of(moderator ? "MODERATOR" : "LEARNER"),
                false, UUID.randomUUID()), name);
    }

    private CompletableFuture<MvcResult> submit(ExecutorService executor, String key, Moderator moderator,
            Fixture fixture, boolean approve) {
        return CompletableFuture.supplyAsync(() -> {
            capture.start(key);
            try {
                var request = patch("/api/community/moderation/posts/" + fixture.post()
                        + (approve ? "/approve" : "/reject"))
                        .header("Authorization", "Bearer " + moderator.token());
                if (!approve) request.contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("reason", REASON)));
                return mvc.perform(request).andReturn();
            } catch (Exception exception) {
                throw new IllegalStateException("Post moderation HTTP request failed", exception);
            } finally {
                capture.stop();
            }
        }, executor);
    }

    private Map<String, Object> row(long id) {
        return jdbc.queryForMap("SELECT * FROM community.posts WHERE id=?", id);
    }

    private List<Map<String, Object>> auditRows(Fixture fixture) {
        return jdbc.queryForList("SELECT actor_id,action,target_type,target_id,detail::text AS detail "
                + "FROM shared.audit_logs WHERE target_type='POST' AND target_id=?", fixture.post());
    }

    private TransactionIdentity identity() {
        return new TransactionIdentity(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class),
                jdbc.queryForObject("SELECT pg_current_xact_id()::text", String.class),
                jdbc.queryForObject("SHOW transaction_isolation", String.class));
    }

    private TransactionTemplate transaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.setTimeout((int) TIMEOUT.toSeconds() + 5);
        return template;
    }

    private static Object repositoryDelegate(InvocationOnMock invocation) throws Throwable {
        return mockingDetails(invocation.getMock()).getMockCreationSettings().getDefaultAnswer().answer(invocation);
    }

    private void assertError(MvcResult response, int status, String code) throws Exception {
        assertThat(response.getResponse().getStatus()).isEqualTo(status);
        JsonNode json = body(response);
        assertThat(json.path("error_code").asText()).isEqualTo(code);
        assertThat(json.path("message").asText()).isNotBlank();
        assertThat(json.path("request_id").asText()).isNotBlank();
        assertThat(json.has("trace")).isFalse();
        assertThat(response.getResponse().getContentAsString()).doesNotContain("ck_post_", "org.hibernate", "PSQLException");
    }

    private JsonNode body(MvcResult response) throws Exception {
        return mapper.readTree(response.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static void await(CountDownLatch latch, String description) {
        try {
            assertThat(latch.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).as(description).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while awaiting " + description, exception);
        }
    }

    private static <T> T result(CompletableFuture<T> pending) {
        try {
            return pending.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Moderation request did not finish within the bounded timeout", exception);
        }
    }

    private static void stop(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
    }

    private record Moderator(long id, String token, String name) { }
    private record Fixture(long author, Moderator first, Moderator second, long post, Instant published) { }
    private record TransactionIdentity(int pid, String transactionId, String isolation) { }
    private record ConstraintFailure(String name, String sqlState) { }

    private static final class Hold {
        private final CountDownLatch updated = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicReference<TransactionIdentity> identity = new AtomicReference<>();
        private final AtomicReference<TransactionIdentity> auditIdentity = new AtomicReference<>();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ModerationConfiguration {
        @Bean @Primary Clock moderationClock() { return Clock.fixed(REQUEST_TIME, ZoneOffset.UTC); }
        @Bean ModerationSqlCapture moderationSqlCapture() { return new ModerationSqlCapture(); }
        @Bean HibernatePropertiesCustomizer moderationStatementInspector(ModerationSqlCapture capture) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", capture);
        }
    }

    static final class ModerationSqlCapture implements StatementInspector {
        private final ThreadLocal<String> active = new ThreadLocal<>();
        private final Map<String, List<String>> statements = new ConcurrentHashMap<>();
        void start(String key) {
            statements.put(key, new CopyOnWriteArrayList<>());
            active.set(key);
        }
        void stop() { active.remove(); }
        List<String> statements(String key) { return List.copyOf(statements.get(key)); }
        @Override public String inspect(String sql) {
            String key = active.get();
            if (key != null) statements.get(key).add(sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip());
            return sql;
        }
    }
}
