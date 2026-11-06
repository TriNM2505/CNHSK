package com.cnhsk.learning.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
@Import(FlashcardReviewIntegrationTest.TimeConfiguration.class)
class FlashcardReviewConcurrencyIntegrationTest extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NamedParameterJdbcTemplate named;
    @Autowired private JwtService jwt;
    @Autowired private MockMvc mvc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private FlashcardReviewIntegrationTest.TestClock clock;
    private long user;
    private long deck;
    private long card;
    private String bearer;

    @BeforeEach
    void caller() {
        clock.now = Instant.parse("2026-11-06T12:30:00Z");
        user = user();
        deck = deck(user);
        card = card(deck);
        bearer = auth(user);
    }

    @Test
    void AT17_twoConcurrentFirstReviewsProduceOneRowAndReviewCountTwo() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            var pending = new TransactionTemplate(transactions).execute(status -> {
                lock();
                var requests = List.of(review(workers, card, bearer), review(workers, card, bearer));
                await(() -> waiters() == 2);
                return requests;
            });
            for (var request : pending) assertThat(request.get(20, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.user_progress WHERE user_id=? AND target_type='FLASHCARD' AND target_id=?", Integer.class, user, card)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT review_count FROM learning.user_progress WHERE user_id=? AND target_type='FLASHCARD' AND target_id=?", Integer.class, user, card)).isEqualTo(2);
        } finally { workers.shutdownNow(); }
    }

    @Test
    void existingProgressIsLockedForUpdateAndClockIsReadAfterAdvisory() throws Exception {
        jdbc.update("INSERT INTO learning.user_progress(user_id,target_type,target_id,last_seen_at,review_count) VALUES (?,'FLASHCARD',?,?,1)", user, card, Timestamp.from(clock.now.minusSeconds(60)));
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            var pending = new TransactionTemplate(transactions).execute(status -> {
                jdbc.queryForObject("SELECT target_id FROM learning.user_progress WHERE user_id=? AND target_type='FLASHCARD' AND target_id=? FOR UPDATE", Long.class, user, card);
                var request = review(worker, card, bearer);
                await(() -> jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE locktype='transactionid' AND NOT granted AND transactionid=pg_current_xact_id()::text::xid", Integer.class) > 0);
                assertThat(request.isDone()).isFalse();
                return request;
            });
            assertThat(pending.get(20, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
            Instant afterWait = clock.now.plusSeconds(30);
            var second = new TransactionTemplate(transactions).execute(status -> {
                lock();
                var request = review(worker, card, bearer);
                await(() -> waiters() == 1);
                clock.now = afterWait;
                return request;
            });
            assertThat(second.get(20, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
            assertThat(jdbc.queryForObject("SELECT last_seen_at FROM learning.user_progress WHERE user_id=? AND target_type='FLASHCARD' AND target_id=?", Timestamp.class, user, card).toInstant()).isEqualTo(afterWait);
        } finally { worker.shutdownNow(); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"CARD", "DECK"})
    void reviewAndDeleteSerializeWithoutResurrectingResources(String target) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            var pending = new TransactionTemplate(transactions).execute(status -> {
                lock();
                var reviewing = review(workers, card, bearer);
                var deleting = CompletableFuture.supplyAsync(() -> {
                    try {
                        var path = target.equals("CARD") ? "/api/flashcard-decks/" + deck + "/cards/" + card : "/api/flashcard-decks/" + deck;
                        return mvc.perform(delete(path).header("Authorization", bearer)).andReturn();
                    } catch (Exception exception) { throw new IllegalStateException(exception); }
                }, workers);
                await(() -> waiters() == 2);
                return List.of(reviewing, deleting);
            });
            int reviewed = pending.get(0).get(20, TimeUnit.SECONDS).getResponse().getStatus();
            assertThat(reviewed).isIn(200, 403);
            assertThat(pending.get(1).get(20, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(204);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.user_progress WHERE user_id=? AND target_type='FLASHCARD' AND target_id=?", Integer.class, user, card)).isEqualTo(reviewed == 200 ? 1 : 0);
            String table = target.equals("CARD") ? "learning.collection_items" : "learning.collections";
            long id = target.equals("CARD") ? card : deck;
            assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM " + table + " WHERE id=?", Boolean.class, id)).isTrue();
        } finally { workers.shutdownNow(); }
    }

    @Test
    void anotherUserCanReviewWhileFirstUsersLockIsHeld() {
        long other = user();
        long otherCard = card(deck(other));
        String otherAuth = auth(other);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(transactions).execute(status -> {
                lock();
                try { assertThat(review(worker, otherCard, otherAuth).get(10, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200); }
                catch (Exception exception) { throw new IllegalStateException(exception); }
                return null;
            });
        } finally { worker.shutdownNow(); }
    }

    private CompletableFuture<MvcResult> review(ExecutorService workers, long targetCard, String authorization) {
        return CompletableFuture.supplyAsync(() -> {
            try { return mvc.perform(post("/api/flashcards/{id}/review", targetCard).header("Authorization", authorization)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"GOOD\"}")).andReturn(); }
            catch (Exception exception) { throw new IllegalStateException(exception); }
        }, workers);
    }
    private long user() { return jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,'fixture-hash-unused','Người thử đồng thời') RETURNING id", Long.class, UUID.randomUUID() + "@example.test"); }
    private long deck(long owner) { return jdbc.queryForObject("INSERT INTO learning.collections(user_id,kind,name) VALUES (?,'FLASHCARD_DECK','Bộ đồng thời') RETURNING id", Long.class, owner); }
    private long card(long parent) { return jdbc.queryForObject("INSERT INTO learning.collection_items(collection_id,title,content) VALUES (?,'Thẻ đồng thời','Mặt sau') RETURNING id", Long.class, parent); }
    private String auth(long owner) { return "Bearer " + jwt.issueAccessToken(owner, List.of("LEARNER"), false, UUID.randomUUID()); }
    private void lock() { named.query("SELECT pg_advisory_xact_lock(hashtextextended(:key,0))", Map.of("key", "library:decks:" + user), rs -> null); }
    private int waiters() { return jdbc.queryForObject("""
            WITH key AS (SELECT hashtextextended(?,0) AS value)
            SELECT count(*)::int FROM pg_locks,key WHERE locktype='advisory' AND NOT granted AND objsubid=1
              AND database=(SELECT oid FROM pg_database WHERE datname=current_database())
              AND classid=((value >> 32) & 4294967295)::oid AND objid=(value & 4294967295)::oid
            """, Integer.class, "library:decks:" + user); }
    private static void await(BooleanSupplier ready) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!ready.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new IllegalStateException("Expected PostgreSQL lock wait was not observed");
            try { Thread.sleep(10); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
        }
    }
}
