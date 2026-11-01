package com.cnhsk.community.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Exercises the daily post quota with PostgreSQL transaction-level concurrency. */
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostQuotaConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "PostQuotaPass123";
    private static final Pattern VERIFY_LINK = Pattern.compile("/verify-email\\?token=([A-Za-z0-9_-]+)");
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final Duration LOCK_WAIT_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Value("${test.mailpit.base-url}") private String mailpitBaseUrl;

    @Test
    void concurrentPostRequestsSerializeQuotaAndOnlyOneCanInsert() throws Exception {
        AuthenticatedLearner learner = registerVerifyAndLogin();
        seedFourCommittedPosts(learner.userId());
        String quotaLockKey = "community:post-quota:" + learner.userId();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        ExecutorService requestWorkers = Executors.newFixedThreadPool(2);

        try {
            PostWorkers workers = transaction.execute(status -> {
                jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                        resultSet -> { }, quotaLockKey);

                CompletableFuture<MvcResult> first = submitPost(requestWorkers, learner.accessToken(), "Concurrent post A");
                CompletableFuture<MvcResult> second = submitPost(requestWorkers, learner.accessToken(), "Concurrent post B");

                try {
                    awaitTwoAdvisoryWaiters(quotaLockKey);
                } catch (Exception failure) {
                    first.cancel(true);
                    second.cancel(true);
                    throw new AssertionError("Timed out waiting for both POST workers to block on quota lock "
                            + quotaLockKey, failure);
                }

                // Returning commits this transaction and releases the advisory lock; both workers
                // have already reached PostgreSQL's lock wait queue at this point.
                return new PostWorkers(first, second);
            });

            MvcResult first = workers.first().get(REQUEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            MvcResult second = workers.second().get(REQUEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS);

            assertThat(new int[] { first.getResponse().getStatus(), second.getResponse().getStatus() })
                    .containsExactlyInAnyOrder(201, 429);
            MvcResult rejected = first.getResponse().getStatus() == 429 ? first : second;
            JsonNode error = objectMapper.readTree(rejected.getResponse().getContentAsString());
            assertThat(error.path("error_code").asText()).isEqualTo("TOO_MANY_REQUESTS");
            assertThat(error.path("retryAfterSeconds").asLong()).isPositive();
            assertThat(countPosts(learner.userId())).isEqualTo(5);
            System.out.println("POST_QUOTA_EVIDENCE ungranted_waiters=2 statuses=201,429 final_count=5");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM community.posts "
                    + "WHERE author_id=? AND title IN ('Concurrent post A','Concurrent post B')",
                    Integer.class, learner.userId())).isEqualTo(1);
        } finally {
            requestWorkers.shutdownNow();
            assertThat(requestWorkers.awaitTermination(REQUEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void differentAuthorsUseIndependentQuotaLocks() throws Exception {
        AuthenticatedLearner firstAuthor = registerVerifyAndLogin();
        AuthenticatedLearner secondAuthor = registerVerifyAndLogin();
        String firstLockKey = "community:post-quota:" + firstAuthor.userId();
        ExecutorService requestWorkers = Executors.newFixedThreadPool(2);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        try {
            CompletableFuture<MvcResult> firstResult = transaction.execute(status -> {
                jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                        resultSet -> { }, firstLockKey);
                CompletableFuture<MvcResult> blockedFirst = submitPost(requestWorkers,
                        firstAuthor.accessToken(), "First author post");
                CompletableFuture<MvcResult> independentSecond = submitPost(requestWorkers,
                        secondAuthor.accessToken(), "Second author post");
                awaitAdvisoryWaiters(firstLockKey, 1);
                try {
                    assertThat(independentSecond.get(REQUEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                            .getResponse().getStatus()).isEqualTo(201);
                } catch (Exception exception) {
                    blockedFirst.cancel(true);
                    independentSecond.cancel(true);
                    throw new AssertionError("A different author's POST should not wait for the first author's quota lock",
                            exception);
                }
                return blockedFirst;
            });

            assertThat(firstResult.get(REQUEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS).getResponse().getStatus())
                    .isEqualTo(201);
            assertThat(countPosts(firstAuthor.userId())).isEqualTo(1);
            assertThat(countPosts(secondAuthor.userId())).isEqualTo(1);
        } finally {
            requestWorkers.shutdownNow();
            assertThat(requestWorkers.awaitTermination(REQUEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void moderationWinningWriteLockPreventsStaleAuthorUpdateFromRevivingHiddenPost() throws Exception {
        AuthenticatedLearner learner = registerVerifyAndLogin();
        long id = seedPendingPost(learner.userId());
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<MvcResult> author = new TransactionTemplate(transactionManager).execute(status -> {
                assertThat(hidePendingPost(id)).isEqualTo(1);
                int holderPid = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
                CompletableFuture<MvcResult> pending = CompletableFuture.supplyAsync(
                        () -> updatePost(learner, id), worker);
                awaitBlockedWriter(holderPid);
                return pending;
            });
            MvcResult result = author.get(REQUEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertThat(result.getResponse().getStatus()).isEqualTo(409);
            assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                    .path("error_code").asText()).isEqualTo("POST_NOT_EDITABLE");
            assertThat(jdbc.queryForMap("SELECT status, title FROM community.posts WHERE id=?", id))
                    .containsEntry("status", "HIDDEN").containsEntry("title", "Original race title");
            System.out.println("POST_RACE_EVIDENCE order=moderation-first blocked_author=true author_http=409 final=HIDDEN");
        } finally {
            worker.shutdownNow();
            assertThat(worker.awaitTermination(REQUEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void authorWinningWriteLockCommitsBeforeConditionalModerationAndFinalStateStaysHidden() throws Exception {
        AuthenticatedLearner learner = registerVerifyAndLogin();
        long id = seedPendingPost(learner.userId());
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<Integer> moderation = new TransactionTemplate(transactionManager).execute(status -> {
                // The real HTTP handler joins this transaction; its first Post read takes PESSIMISTIC_WRITE.
                MvcResult author = updatePost(learner, id);
                assertThat(author.getResponse().getStatus()).isEqualTo(200);
                int holderPid = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
                CompletableFuture<Integer> pending = CompletableFuture.supplyAsync(() ->
                        new TransactionTemplate(transactionManager).execute(moderationStatus -> {
                            jdbc.execute("SET LOCAL lock_timeout = '15s'");
                            return hidePendingPost(id);
                        }), worker);
                awaitBlockedWriter(holderPid);
                return pending;
            });
            assertThat(moderation.get(REQUEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(jdbc.queryForMap("SELECT status, title FROM community.posts WHERE id=?", id))
                    .containsEntry("status", "HIDDEN").containsEntry("title", "Author updated title");
            System.out.println("POST_RACE_EVIDENCE order=author-first blocked_moderation=true author_http=200 conditional_rows=1 final=HIDDEN");
        } finally {
            worker.shutdownNow();
            assertThat(worker.awaitTermination(REQUEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        }
    }

    private long seedPendingPost(long authorId) {
        return new TransactionTemplate(transactionManager).execute(status -> jdbc.queryForObject(
                "INSERT INTO community.posts(author_id, category, title, content, status) "
                        + "VALUES (?, 'QUESTION', 'Original race title', 'Race body', 'PENDING') RETURNING id",
                Long.class, authorId));
    }

    private int hidePendingPost(long id) {
        // Test-only simulation of a moderation conditional write.
        return jdbc.update("UPDATE community.posts SET status='HIDDEN' WHERE id=? AND status='PENDING'", id);
    }

    private MvcResult updatePost(AuthenticatedLearner learner, long id) {
        try {
            return mockMvc.perform(put("/api/community/posts/{id}", id)
                            .header("Authorization", "Bearer " + learner.accessToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("title", "Author updated title",
                                    "content", "Author updated body", "category", "QUESTION", "submit", true))))
                    .andReturn();
        } catch (Exception exception) {
            throw new IllegalStateException("Author update request failed", exception);
        }
    }

    private void awaitBlockedWriter(int holderPid) {
        long deadline = System.nanoTime() + LOCK_WAIT_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            int blocked = jdbc.queryForObject("SELECT count(*)::int FROM pg_stat_activity "
                    + "WHERE ? = ANY(pg_blocking_pids(pid))", Integer.class, holderPid);
            if (blocked == 1) return;
            try {
                Thread.sleep(25);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while observing PostgreSQL row-lock contention", exception);
            }
        }
        throw new AssertionError("Expected one PostgreSQL writer blocked by the held transaction within "
                + LOCK_WAIT_TIMEOUT.toSeconds() + " seconds");
    }

    private CompletableFuture<MvcResult> submitPost(ExecutorService executor, String accessToken, String title) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return mockMvc.perform(post("/api/community/posts")
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "title", title,
                                        "content", "Concurrency quota integration test",
                                        "category", "QUESTION",
                                        "submit", true))))
                        .andReturn();
            } catch (Exception exception) {
                throw new IllegalStateException("Concurrent post request failed", exception);
            }
        }, executor);
    }

    private void awaitTwoAdvisoryWaiters(String key) {
        awaitAdvisoryWaiters(key, 2);
    }

    private void awaitAdvisoryWaiters(String key, int expected) {
        long deadline = System.nanoTime() + LOCK_WAIT_TIMEOUT.toNanos();
        int waiting = 0;
        while (System.nanoTime() < deadline) {
            waiting = jdbc.queryForObject("""
                    WITH lock_key AS (SELECT hashtextextended(?, 0) AS value)
                    SELECT count(*)::int
                    FROM pg_locks, lock_key
                    WHERE locktype = 'advisory' AND NOT granted AND objsubid = 1
                      AND classid = ((value >> 32) & 4294967295)::oid
                      AND objid = (value & 4294967295)::oid
                    """, Integer.class, key);
            if (waiting == expected) return;
            try {
                Thread.sleep(25);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while polling PostgreSQL advisory lock waiters", exception);
            }
        }
        throw new AssertionError("Expected exactly " + expected + " ungranted PostgreSQL advisory waiters for key '"
                + key + "' within " + LOCK_WAIT_TIMEOUT.toSeconds() + " seconds; observed " + waiting);
    }

    private void seedFourCommittedPosts(long authorId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (int index = 1; index <= 4; index++) {
                jdbc.update("INSERT INTO community.posts(author_id, category, title, content) "
                                + "VALUES (?, 'QUESTION', ?, 'Committed quota seed')",
                        authorId, "Quota seed " + index + " " + UUID.randomUUID());
            }
        });
        assertThat(countPosts(authorId)).isEqualTo(4);
    }

    private int countPosts(long authorId) {
        return jdbc.queryForObject("SELECT count(*) FROM community.posts WHERE author_id=?",
                Integer.class, authorId);
    }

    private AuthenticatedLearner registerVerifyAndLogin() throws Exception {
        String email = "post-quota-" + UUID.randomUUID() + "@example.test";
        String registrationBody = objectMapper.writeValueAsString(Map.of(
                "email", email,
                "password", PASSWORD,
                "confirmPassword", PASSWORD,
                "displayName", "Post Quota Learner",
                "acceptTerms", true));
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody))
                .andReturn();
        assertThat(registration.getResponse().getStatus()).isEqualTo(201);
        long userId = objectMapper.readTree(registration.getResponse().getContentAsString())
                .path("userId").asLong();
        assertThat(userId).isPositive();

        JsonNode messages = getMailpitMessages().path("messages");
        JsonNode verificationMessage = null;
        for (JsonNode message : messages) {
            if (message.toString().contains(email)) {
                verificationMessage = readMailpitMessage(message.path("ID").asText());
                break;
            }
        }
        assertThat(verificationMessage).as("verification email delivered through Mailpit").isNotNull();
        Matcher tokenMatcher = VERIFY_LINK.matcher(verificationMessage.path("Text").asText());
        assertThat(tokenMatcher.find()).as("verification token in Mailpit message").isTrue();
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", tokenMatcher.group(1)))))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .header("X-Client-Type", "MOBILE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andReturn();
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        String accessToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .path("accessToken").asText();
        assertThat(accessToken).isNotBlank();
        return new AuthenticatedLearner(userId, accessToken);
    }

    private JsonNode getMailpitMessages() throws Exception {
        HttpResponse<String> response = HTTP_CLIENT.send(
                HttpRequest.newBuilder().uri(URI.create(mailpitBaseUrl + "/api/v1/messages")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    private JsonNode readMailpitMessage(String messageId) throws Exception {
        HttpResponse<String> response = HTTP_CLIENT.send(
                HttpRequest.newBuilder().uri(URI.create(mailpitBaseUrl + "/api/v1/message/" + messageId))
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    private record AuthenticatedLearner(long userId, String accessToken) { }
    private record PostWorkers(CompletableFuture<MvcResult> first, CompletableFuture<MvcResult> second) { }
}
