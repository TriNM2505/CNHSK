package com.cnhsk.community.reaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.cnhsk.community.post.PostRepository;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
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

/** Committed fixtures and independent transactions against the real container PostgreSQL. */
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=3",
        "spring.datasource.hikari.minimum-idle=3",
        "spring.datasource.hikari.connection-timeout=20000"
})
class LikeConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final String INSERT = "INSERT INTO community.reactions(user_id, target_type, target_id, kind) "
            + "VALUES (?, 'POST', ?, 'LIKE')";
    private static final String DELETE = "DELETE FROM community.reactions WHERE user_id=? "
            + "AND target_type='POST' AND target_id=? AND kind='LIKE'";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoSpyBean private ReactionRepository reactions;
    @MockitoSpyBean private PostRepository posts;

    @BeforeEach
    @AfterEach
    void cleanCommunityFixtures() {
        jdbc.update("DELETE FROM community.reactions");
        jdbc.update("DELETE FROM community.comments");
        jdbc.update("DELETE FROM community.posts");
    }

    @Test
    void FR028_FR030_FR031_tenConcurrentUnverifiedSelfLikesUseOnePrimaryKeyRow() throws Exception {
        Fixture fixture = fixture();
        ExecutorService workers = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<CompletableFuture<MvcResult>> pending = transaction().execute(status -> {
                jdbc.update(INSERT, fixture.first().id(), fixture.post());
                int holder = backendPid();
                List<CompletableFuture<MvcResult>> requests = new ArrayList<>();
                for (int index = 0; index < 10; index++) {
                    requests.add(submit(workers, fixture.first(), fixture.post(), true, start));
                }
                start.countDown();
                // The holder occupies one of three connections; the other two execute real INSERTs.
                awaitInsertWaiters(holder, 2);
                status.setRollbackOnly();
                return requests;
            });
            for (CompletableFuture<MvcResult> request : pending) {
                assertResponse(result(request), true, 1);
            }
            // The holder rolled back, so this row must have been committed by an HTTP request.
            assertThat(rowCount(fixture.first().id(), fixture.post())).isEqualTo(1);
            assertThat(likeCount(fixture.post())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT email_verified_at IS NULL FROM auth.users WHERE id=?",
                    Boolean.class, fixture.first().id())).isTrue();
        } finally {
            start.countDown();
            stop(workers);
        }
    }

    @ParameterizedTest(name = "conflicting INSERT holder rollback={0}")
    @ValueSource(booleans = {false, true})
    void FR028_FR031_uniqueConflictWaitsAndReturnsUsableTransaction(boolean rollback) throws Exception {
        Fixture fixture = fixture();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<InsertResult> pending = transaction().execute(status -> {
                jdbc.update(INSERT, fixture.first().id(), fixture.post());
                int holder = backendPid();
                CompletableFuture<InsertResult> request = CompletableFuture.supplyAsync(() ->
                        transaction().execute(other -> {
                            int affected = reactions.insertPostLike(fixture.first().id(), fixture.post());
                            int count = likeCount(fixture.post());
                            int usable = jdbc.queryForObject("SELECT 1", Integer.class);
                            return new InsertResult(affected, count, usable);
                        }), worker);
                awaitInsertWaiters(holder, 1);
                if (rollback) status.setRollbackOnly();
                return request;
            });
            InsertResult inserted = result(pending);
            assertThat(inserted.affected()).isEqualTo(rollback ? 1 : 0);
            assertThat(inserted.count()).isEqualTo(1);
            assertThat(inserted.usable()).isEqualTo(1);
            assertThat(rowCount(fixture.first().id(), fixture.post())).isEqualTo(1);
        } finally {
            stop(worker);
        }
    }

    @ParameterizedTest(name = "first committed HTTP operation liked={0}")
    @ValueSource(booleans = {true, false})
    void FR028_FR029_committedLikeThenUnlikeIsAbsentAndReverseIsPresent(boolean firstLike) throws Exception {
        Fixture fixture = fixture();
        if (!firstLike) {
            transaction().executeWithoutResult(status -> jdbc.update(INSERT, fixture.first().id(), fixture.post()));
        }
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            assertResponse(result(submit(worker, fixture.first(), fixture.post(), firstLike)), firstLike,
                    firstLike ? 1 : 0);
            assertResponse(result(submit(worker, fixture.first(), fixture.post(), !firstLike)), !firstLike,
                    firstLike ? 0 : 1);
            assertThat(rowCount(fixture.first().id(), fixture.post())).isEqualTo(firstLike ? 0 : 1);
        } finally {
            stop(worker);
        }
    }

    @Test
    void FR029_deleteCannotSeeUncommittedInsertAndCompletesBeforeItsCommit() throws Exception {
        Fixture fixture = fixture();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            transaction().executeWithoutResult(status -> {
                jdbc.update(INSERT, fixture.first().id(), fixture.post());
                MvcResult response = result(submit(worker, fixture.first(), fixture.post(), false));
                assertResponse(response, false, 0);
                assertThat(rowCount(fixture.first().id(), fixture.post())).isEqualTo(1);
            });
            // READ COMMITTED DELETE did not see the pending INSERT; no last-commit-wins promise exists.
            assertThat(rowCount(fixture.first().id(), fixture.post())).isEqualTo(1);
        } finally {
            stop(worker);
        }
    }

    @ParameterizedTest(name = "uncommitted DELETE holder rollback={0}")
    @ValueSource(booleans = {false, true})
    void FR028_FR029_insertWaitsForDeleteAndRetriesUniqueCheck(boolean rollback) throws Exception {
        Fixture fixture = fixture();
        transaction().executeWithoutResult(status -> jdbc.update(INSERT, fixture.first().id(), fixture.post()));
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<InsertResult> pending = transaction().execute(status -> {
                assertThat(jdbc.update(DELETE, fixture.first().id(), fixture.post())).isEqualTo(1);
                int holder = backendPid();
                CompletableFuture<InsertResult> request = CompletableFuture.supplyAsync(() ->
                        transaction().execute(other -> new InsertResult(
                                reactions.insertPostLike(fixture.first().id(), fixture.post()),
                                likeCount(fixture.post()), jdbc.queryForObject("SELECT 1", Integer.class))), worker);
                awaitInsertWaiters(holder, 1);
                if (rollback) status.setRollbackOnly();
                return request;
            });
            InsertResult inserted = result(pending);
            assertThat(inserted.affected()).isEqualTo(rollback ? 0 : 1);
            assertThat(inserted.count()).isEqualTo(1);
            assertThat(inserted.usable()).isEqualTo(1);
            assertThat(rowCount(fixture.first().id(), fixture.post())).isEqualTo(1);
        } finally {
            stop(worker);
        }
    }

    @Test
    void FR031_twoDifferentUsersLikeConcurrentlyAndBothRowsAreCounted() throws Exception {
        Fixture fixture = fixture();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            CompletableFuture<MvcResult> first = submit(workers, fixture.first(), fixture.post(), true, start);
            CompletableFuture<MvcResult> second = submit(workers, fixture.second(), fixture.post(), true, start);
            start.countDown();
            for (CompletableFuture<MvcResult> request : List.of(first, second)) {
                MvcResult response = result(request);
                assertThat(response.getResponse().getStatus()).isEqualTo(200);
                JsonNode body = body(response);
                assertThat(body.path("liked").asBoolean()).isTrue();
                assertThat(body.path("likeCount").asInt()).isBetween(1, 2);
            }
            assertThat(rowCount(fixture.first().id(), fixture.post())).isEqualTo(1);
            assertThat(rowCount(fixture.second().id(), fixture.post())).isEqualTo(1);
            assertThat(likeCount(fixture.post())).isEqualTo(2);
        } finally {
            start.countDown();
            stop(workers);
        }
    }

    @ParameterizedTest(name = "published read before writer commit, liked={0}")
    @ValueSource(booleans = {true, false})
    void FR028_FR029_nonlockingVisibilityReadAllowsMutationAfterPostBecomesPending(boolean liked)
            throws Exception {
        Fixture fixture = fixture();
        if (!liked) {
            transaction().executeWithoutResult(status -> jdbc.update(INSERT, fixture.first().id(), fixture.post()));
        }
        CountDownLatch readApproved = new CountDownLatch(1);
        CountDownLatch writerCommitted = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object published = delegate(invocation);
            assertThat((List<?>) published).hasSize(1);
            readApproved.countDown();
            await(writerCommitted, "post writer commit after the actual published read");
            return published;
        }).when(posts).findPublished(eq(fixture.post()), any(), any());
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<MvcResult> pending = submit(worker, fixture.first(), fixture.post(), liked);
            await(readApproved, "actual nonlocking published SELECT");
            transaction().executeWithoutResult(status -> assertThat(jdbc.update(
                    "UPDATE community.posts SET status='PENDING' WHERE id=?", fixture.post())).isEqualTo(1));
            writerCommitted.countDown();
            assertResponse(result(pending), liked, liked ? 1 : 0);
            assertThat(jdbc.queryForObject("SELECT status FROM community.posts WHERE id=?",
                    String.class, fixture.post())).isEqualTo("PENDING");
            assertThat(rowCount(fixture.first().id(), fixture.post())).isEqualTo(liked ? 1 : 0);
        } finally {
            writerCommitted.countDown();
            stop(worker);
        }
    }

    @Test
    void FR031_groupedCountIncludesOwnInsertAndOtherCommittedLikeAtCountStatement() throws Exception {
        Fixture fixture = fixture();
        CountDownLatch ownInsertComplete = new CountDownLatch(1);
        CountDownLatch otherCommitted = new CountDownLatch(1);
        AtomicReference<TransactionIdentity> insertTransaction = new AtomicReference<>();
        doAnswer(invocation -> {
            Object affected = delegate(invocation);
            insertTransaction.set(transactionIdentity());
            return affected;
        }).when(reactions).insertPostLike(fixture.first().id(), fixture.post());
        doAnswer(invocation -> {
            assertThat(rowCount(fixture.first().id(), fixture.post())).isEqualTo(1);
            TransactionIdentity countingTransaction = transactionIdentity();
            assertThat(countingTransaction).isEqualTo(insertTransaction.get());
            assertThat(countingTransaction.isolation()).isEqualTo("read committed");
            System.out.println("LIKE_TRANSACTION_EVIDENCE backendPid=" + countingTransaction.pid()
                    + " transactionId=" + countingTransaction.transactionId()
                    + " sameInsertAndCountTransaction=true isolation=" + countingTransaction.isolation());
            ownInsertComplete.countDown();
            await(otherCommitted, "other user's committed like before grouped count");
            return delegate(invocation);
        }).when(reactions).countLikesByPostIds(Set.of(fixture.post()));
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<MvcResult> pending = submit(worker, fixture.first(), fixture.post(), true);
            await(ownInsertComplete, "own INSERT before grouped count");
            transaction().executeWithoutResult(status ->
                    assertThat(reactions.insertPostLike(fixture.second().id(), fixture.post())).isEqualTo(1));
            otherCommitted.countDown();
            assertResponse(result(pending), true, 2);
            assertThat(likeCount(fixture.post())).isEqualTo(2);
        } finally {
            otherCommitted.countDown();
            stop(worker);
        }
    }

    private Fixture fixture() throws Exception {
        Learner first = registerUnverifiedLearner();
        Learner second = registerUnverifiedLearner();
        long postId = transaction().execute(status -> jdbc.queryForObject("""
                INSERT INTO community.posts(author_id, category, title, content, status, published_at)
                VALUES (?, 'QUESTION', 'Like concurrency fixture', 'Synthetic community fixture', 'APPROVED', now())
                RETURNING id
                """, Long.class, first.id()));
        return new Fixture(first, second, postId);
    }

    private Learner registerUnverifiedLearner() throws Exception {
        String email = "like-concurrency-" + UUID.randomUUID() + "@example.test";
        String password = "Like" + UUID.randomUUID() + "A9";
        MvcResult registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", password,
                        "confirmPassword", password, "displayName", "Like Concurrency Learner",
                        "acceptTerms", true)))).andReturn();
        assertThat(registered.getResponse().getStatus()).isEqualTo(201);
        long id = body(registered).path("userId").asLong();
        assertThat(id).isPositive();
        return new Learner(id, jwt.issueAccessToken(id, List.of("LEARNER"), false, UUID.randomUUID()));
    }

    private void awaitInsertWaiters(int holderPid, int expected) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        int observed = -1;
        while (System.nanoTime() < deadline) {
            // The polling holder is itself in a transaction; activity snapshots must be cleared every time.
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            List<Integer> workers = jdbc.queryForList("""
                    SELECT activity.pid FROM pg_stat_activity activity
                    WHERE activity.datname=current_database() AND activity.backend_type='client backend'
                      AND activity.pid<>pg_backend_pid()
                      AND ?=ANY(pg_blocking_pids(activity.pid))
                      AND activity.query ILIKE '%insert%into%community.reactions%'
                      AND EXISTS (SELECT 1 FROM pg_locks locks WHERE locks.pid=activity.pid
                        AND NOT locks.granted AND locks.locktype='transactionid')
                    ORDER BY activity.pid
                    """, Integer.class, holderPid);
            observed = workers.size();
            if (observed == expected) {
                System.out.println("LIKE_LOCK_EVIDENCE holderPid=" + holderPid + " workerPids=" + workers
                        + " transactionIdInsertWaiters=" + observed);
                return;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while observing PostgreSQL INSERT waiters", exception);
            }
        }
        throw new AssertionError("Expected " + expected + " actual transactionid INSERT waiters; observed "
                + observed + " within " + TIMEOUT.toSeconds() + " seconds");
    }

    private int backendPid() {
        return jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
    }

    private TransactionIdentity transactionIdentity() {
        int pid = backendPid();
        String transactionId = jdbc.queryForObject("SELECT pg_current_xact_id()::text", String.class);
        String isolation = jdbc.queryForObject("SHOW transaction_isolation", String.class);
        return new TransactionIdentity(pid, transactionId, isolation);
    }

    private static Object delegate(InvocationOnMock invocation) throws Throwable {
        // Spring's repository spy delegates to the real JDK proxy; interface query methods are abstract.
        return mockingDetails(invocation.getMock()).getMockCreationSettings().getDefaultAnswer().answer(invocation);
    }

    private int rowCount(long userId, long postId) {
        return jdbc.queryForObject("SELECT count(*) FROM community.reactions WHERE user_id=? "
                + "AND target_type='POST' AND target_id=? AND kind='LIKE'", Integer.class, userId, postId);
    }

    private int likeCount(long postId) {
        return jdbc.queryForObject("SELECT count(*) FROM community.reactions WHERE target_type='POST' "
                + "AND target_id=? AND kind='LIKE'", Integer.class, postId);
    }

    private CompletableFuture<MvcResult> submit(ExecutorService executor, Learner learner, long postId,
            boolean liked) {
        return submit(executor, learner, postId, liked, new CountDownLatch(0));
    }

    private CompletableFuture<MvcResult> submit(ExecutorService executor, Learner learner, long postId,
            boolean liked, CountDownLatch start) {
        return CompletableFuture.supplyAsync(() -> {
            await(start, "concurrent HTTP start");
            try {
                String path = "/api/community/posts/" + postId + "/like";
                return mvc.perform((liked ? post(path) : delete(path))
                        .header("Authorization", "Bearer " + learner.token())).andReturn();
            } catch (Exception exception) {
                throw new IllegalStateException("Like HTTP handler failed", exception);
            }
        }, executor);
    }

    private void assertResponse(MvcResult response, boolean liked, int count) {
        assertThat(response.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = body(response);
        assertThat(body.path("liked").asBoolean()).isEqualTo(liked);
        assertThat(body.path("likeCount").asInt()).isEqualTo(count);
    }

    private JsonNode body(MvcResult response) {
        try {
            return mapper.readTree(response.getResponse().getContentAsString());
        } catch (Exception exception) {
            throw new AssertionError("Expected JSON response", exception);
        }
    }

    private TransactionTemplate transaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout((int) TIMEOUT.toSeconds() + 5);
        return transaction;
    }

    private static void await(CountDownLatch latch, String description) {
        try {
            assertThat(latch.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).as(description).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while awaiting " + description, exception);
        }
    }

    private static <T> T result(CompletableFuture<T> future) {
        try {
            return future.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Concurrent operation did not complete within the bounded timeout", exception);
        }
    }

    private static void stop(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
    }

    private record Learner(long id, String token) { }
    private record Fixture(Learner first, Learner second, long post) { }
    private record InsertResult(int affected, int count, int usable) { }
    private record TransactionIdentity(int pid, String transactionId, String isolation) { }
}
