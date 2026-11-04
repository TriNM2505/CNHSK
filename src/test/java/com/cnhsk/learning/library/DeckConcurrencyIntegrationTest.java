package com.cnhsk.learning.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Separate committed transactions, with observed waiters on the real PostgreSQL key. */
@AutoConfigureMockMvc
@Import(DeckConcurrencyIntegrationTest.TimeConfiguration.class)
class DeckConcurrencyIntegrationTest extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NamedParameterJdbcTemplate namedJdbc;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private TestClock clock;
    private long user;
    private String bearer;

    @BeforeEach
    void caller() {
        clock.now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        user = user();
        bearer = bearer(user);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 49, 50})
    void FR013_AT12_parallelCreatesNeverExceed50(int count) throws Exception {
        jdbc.update("INSERT INTO learning.collections(user_id,kind,name) SELECT ?,'FLASHCARD_DECK','Tự soạn '||n FROM generate_series(1,?) n", user, count);
        List<MvcResult> results = together("POST", "", "POST", "");
        int[] expected = count == 0 ? new int[] {201, 201} : count == 49 ? new int[] {201, 422} : new int[] {422, 422};
        assertThat(results.stream().mapToInt(result -> result.getResponse().getStatus()).toArray()).containsExactlyInAnyOrder(expected);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.collections WHERE user_id=? AND kind='FLASHCARD_DECK' AND deleted_at IS NULL", Integer.class, user))
                .isEqualTo(count == 0 ? 2 : 50);
        for (MvcResult result : results) if (result.getResponse().getStatus() == 422) {
            var json = mapper.readTree(result.getResponse().getContentAsByteArray());
            assertThat(json.path("error_code").asText()).isEqualTo("LIBRARY_LIMIT_REACHED");
            assertThat(json.path("details").path("kind").asText()).isEqualTo("DECKS");
            assertThat(json.path("details").path("limit").asInt()).isEqualTo(50);
        }
    }

    @Test
    void FR014_concurrentDeletesReturn204And403AndPreserveInitialTimestamp() throws Exception {
        long deck = jdbc.queryForObject("INSERT INTO learning.collections(user_id,kind,name) VALUES (?,'FLASHCARD_DECK','Xoá đồng thời') RETURNING id", Long.class, user);
        var results = together("DELETE", "/" + deck, "DELETE", "/" + deck);
        assertThat(results.stream().mapToInt(result -> result.getResponse().getStatus()).toArray()).containsExactlyInAnyOrder(204, 403);
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM learning.collections WHERE id=?", Timestamp.class, deck).toInstant()).isEqualTo(clock.now);
    }

    @Test
    void clockIsReadAfterWaitingForLock() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        Instant afterWait = clock.now.plusSeconds(30);
        try {
            CompletableFuture<MvcResult> request = new TransactionTemplate(transactions).execute(status -> {
                lock("library:decks:" + user);
                CompletableFuture<MvcResult> pending = submit(worker, "POST", "", bearer);
                awaitWaiters(1);
                clock.now = afterWait;
                return pending;
            });
            var response = request.get(20, TimeUnit.SECONDS);
            assertThat(response.getResponse().getStatus()).isEqualTo(201);
            long id = mapper.readTree(response.getResponse().getContentAsByteArray()).path("id").asLong();
            assertThat(jdbc.queryForObject("SELECT created_at FROM learning.collections WHERE id=?", Timestamp.class, id).toInstant()).isEqualTo(afterWait);
        } finally { worker.shutdownNow(); }
    }

    @Test
    void otherUserAndNotebookOperationsDoNotShareDeckLock() {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        String otherBearer = bearer(user());
        try {
            new TransactionTemplate(transactions).execute(status -> {
                lock("library:decks:" + user);
                try {
                    assertThat(submit(worker, "POST", "", otherBearer).get(10, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(201);
                    var notebook = CompletableFuture.supplyAsync(() -> {
                        try { return mvc.perform(post("/api/notes").header("Authorization", bearer)
                                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Sổ tay không bị khoá bộ thẻ\"}"))
                                .andReturn().getResponse().getStatus(); }
                        catch (Exception exception) { throw new IllegalStateException(exception); }
                    }, worker);
                    assertThat(notebook.get(10, TimeUnit.SECONDS)).isEqualTo(201);
                } catch (Exception exception) { throw new IllegalStateException(exception); }
                return null;
            });
        } finally { worker.shutdownNow(); }
    }

    private List<MvcResult> together(String firstMethod, String firstPath, String secondMethod, String secondPath) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            var pending = new TransactionTemplate(transactions).execute(status -> {
                lock("library:decks:" + user);
                var requests = List.of(submit(workers, firstMethod, firstPath, bearer), submit(workers, secondMethod, secondPath, bearer));
                awaitWaiters(2);
                return requests;
            });
            return List.of(pending.get(0).get(20, TimeUnit.SECONDS), pending.get(1).get(20, TimeUnit.SECONDS));
        } finally { workers.shutdownNow(); }
    }

    private CompletableFuture<MvcResult> submit(ExecutorService workers, String method, String path, String authorization) {
        return CompletableFuture.supplyAsync(() -> {
            try { return mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), "/api/flashcard-decks" + path)
                    .header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Bộ tự soạn đồng thời\"}"))
                    .andReturn(); }
            catch (Exception exception) { throw new IllegalStateException(exception); }
        }, workers);
    }

    private void lock(String key) { namedJdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(:key,0))", Map.of("key", key), rs -> null); }

    private void awaitWaiters(int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (jdbc.queryForObject("""
                WITH key AS (SELECT hashtextextended(?,0) AS value)
                SELECT count(*)::int FROM pg_locks,key WHERE locktype='advisory' AND NOT granted AND objsubid=1
                  AND database=(SELECT oid FROM pg_database WHERE datname=current_database())
                  AND classid=((value >> 32) & 4294967295)::oid AND objid=(value & 4294967295)::oid
                """, Integer.class, "library:decks:" + user) != expected) {
            if (System.nanoTime() > deadline) throw new IllegalStateException("Requests did not wait on deck advisory lock");
            try { Thread.sleep(10); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
        }
    }

    private long user() { return jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,'fixture-hash-unused','Người thử đồng thời') RETURNING id", Long.class, UUID.randomUUID() + "@example.test"); }
    private String bearer(long id) { return "Bearer " + jwt.issueAccessToken(id, List.of("LEARNER"), false, UUID.randomUUID()); }

    @TestConfiguration
    static class TimeConfiguration {
        @Bean @Primary TestClock testClock() { return new TestClock(); }
    }

    static class TestClock extends Clock {
        volatile Instant now = Instant.now();
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
