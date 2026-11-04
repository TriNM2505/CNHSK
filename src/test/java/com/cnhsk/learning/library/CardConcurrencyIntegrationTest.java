package com.cnhsk.learning.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Each API call commits independently against PostgreSQL, not an in-memory substitute. */
@AutoConfigureMockMvc
@Import(CardConcurrencyIntegrationTest.TimeConfiguration.class)
class CardConcurrencyIntegrationTest extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NamedParameterJdbcTemplate namedJdbc;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private TestClock clock;
    @MockitoSpyBean private CardRepository cards;
    private long user;
    private long deck;
    private String bearer;

    @BeforeEach
    void caller() {
        clock.now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        user = user();
        deck = deck(user);
        bearer = bearer(user);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 499, 500})
    void FR015_AT14_parallelCreatesNeverExceed500(int count) throws Exception {
        seed(count);
        var responses = together("POST", path(), Map.of("front", "Thẻ A", "back", "Tự soạn"),
                "POST", path(), Map.of("front", "Thẻ B", "back", "Tự soạn"));
        int[] expected = count == 0 ? new int[] {201, 201} : count == 499 ? new int[] {201, 422} : new int[] {422, 422};
        assertStatuses(responses, expected);
        assertThat(count()).isEqualTo(count == 0 ? 2 : 500);
        for (var result : responses) if (result.getResponse().getStatus() == 422) {
            var error = mapper.readTree(result.getResponse().getContentAsByteArray());
            assertThat(error.path("error_code").asText()).isEqualTo("LIBRARY_LIMIT_REACHED");
            assertThat(error.path("details").path("kind").asText()).isEqualTo("CARDS");
            assertThat(error.path("details").path("limit").asInt()).isEqualTo(500);
        }
    }

    @Test
    void FR015_parallelSameLexemeReturns201And409() throws Exception {
        long lexeme = lexeme();
        var body = Map.of("lexemeId", lexeme);
        var responses = together("POST", path(), body, "POST", path(), body);
        assertStatuses(responses, 201, 409);
        assertDuplicate(responses);
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void FR015_parallelNormalizedFrontReturns201And409() throws Exception {
        var responses = together("POST", path(), Map.of("front", " Từ\t ví\u00a0dụ ", "back", "A"),
                "POST", path(), Map.of("front", "Từ ví dụ", "back", "B"));
        assertStatuses(responses, 201, 409);
        assertDuplicate(responses);
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void FR016_parallelPatchesCannotGiveTwoCardsTheSameFront() throws Exception {
        seed(2);
        var ids = jdbc.queryForList("SELECT id FROM learning.collection_items WHERE collection_id=? ORDER BY id", Long.class, deck);
        var responses = together("PATCH", path() + "/" + ids.get(0), Map.of("front", "Mặt chung"),
                "PATCH", path() + "/" + ids.get(1), Map.of("front", " Mặt\n chung "));
        assertStatuses(responses, 200, 409);
        assertDuplicate(responses);
    }

    @Test
    void FR015_databaseUniqueConflictAfterPrecheckIs409AndRollsBackInsteadOf500() throws Exception {
        long lexeme = lexeme();
        AtomicBoolean insertedDirectly = new AtomicBoolean();
        doAnswer(invocation -> {
            // Bypass the precheck after it has run; exercise uq_coll_lex and the real ON CONFLICT SQL.
            jdbc.update("INSERT INTO learning.collection_items(collection_id,lexeme_id,title,content) VALUES (?,?,'Thẻ chèn trực tiếp','Mặt sau')", deck, lexeme);
            insertedDirectly.set(true);
            return invocation.callRealMethod();
        }).when(cards).insert(eq(deck), eq(lexeme), anyString(), anyString(), any());
        var response = submitDirect("POST", path(), Map.of("lexemeId", lexeme), bearer);
        assertThat(response.getResponse().getStatus()).isEqualTo(409);
        var error = mapper.readTree(response.getResponse().getContentAsByteArray());
        assertThat(error.path("error_code").asText()).isEqualTo("CARD_DUPLICATE");
        assertThat(error.has("details")).isFalse();
        assertThat(insertedDirectly).isTrue();
        assertThat(count()).isZero();
    }

    @Test
    void clockIsReadAfterWaitingForDeckAdvisoryLock() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        Instant afterWait = clock.now.plusSeconds(30);
        try {
            var pending = new TransactionTemplate(transactions).execute(status -> {
                lock();
                var response = submit(worker, "POST", path(), Map.of("front", "Chờ khoá", "back", "Tự soạn"), bearer);
                await(() -> waiters() == 1);
                clock.now = afterWait;
                return response;
            });
            var response = pending.get(20, TimeUnit.SECONDS);
            assertThat(response.getResponse().getStatus()).isEqualTo(201);
            long id = mapper.readTree(response.getResponse().getContentAsByteArray()).path("id").asLong();
            assertThat(jdbc.queryForObject("SELECT created_at FROM learning.collection_items WHERE id=?", Timestamp.class, id).toInstant()).isEqualTo(afterWait);
        } finally { worker.shutdownNow(); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"PATCH", "DELETE"})
    void FR016_writesWaitForTheCardRowLock(String method) throws Exception {
        seed(1);
        long card = jdbc.queryForObject("SELECT id FROM learning.collection_items WHERE collection_id=?", Long.class, deck);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            var pending = new TransactionTemplate(transactions).execute(status -> {
                jdbc.queryForObject("SELECT id FROM learning.collection_items WHERE id=? FOR UPDATE", Long.class, card);
                var response = submit(worker, method, path() + "/" + card, Map.of("back", "Đợi khoá dòng"), bearer);
                await(() -> jdbc.queryForObject("""
                        SELECT count(*) FROM pg_locks WHERE locktype='transactionid' AND NOT granted
                          AND transactionid=pg_current_xact_id()::text::xid
                        """, Integer.class) > 0);
                assertThat(response.isDone()).isFalse();
                return response;
            });
            assertThat(pending.get(20, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(method.equals("PATCH") ? 200 : 204);
        } finally { worker.shutdownNow(); }
    }

    @Test
    void anotherUserIsNotBlockedByFirstUsersDeckLock() {
        long other = user();
        long otherDeck = deck(other);
        String otherBearer = bearer(other);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(transactions).execute(status -> {
                lock();
                try {
                    var response = submit(worker, "POST", "/api/flashcard-decks/" + otherDeck + "/cards", Map.of("front", "Không chung khoá", "back", "Tự soạn"), otherBearer)
                            .get(10, TimeUnit.SECONDS);
                    assertThat(response.getResponse().getStatus()).isEqualTo(201);
                } catch (Exception exception) { throw new IllegalStateException(exception); }
                return null;
            });
        } finally { worker.shutdownNow(); }
    }

    private List<MvcResult> together(String methodA, String pathA, Map<String, ?> bodyA, String methodB, String pathB, Map<String, ?> bodyB) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            var pending = new TransactionTemplate(transactions).execute(status -> {
                lock();
                var responses = List.of(submit(workers, methodA, pathA, bodyA, bearer), submit(workers, methodB, pathB, bodyB, bearer));
                await(() -> waiters() == 2);
                return responses;
            });
            return List.of(pending.get(0).get(20, TimeUnit.SECONDS), pending.get(1).get(20, TimeUnit.SECONDS));
        } finally { workers.shutdownNow(); }
    }

    private CompletableFuture<MvcResult> submit(ExecutorService worker, String method, String path, Map<String, ?> body, String authorization) {
        return CompletableFuture.supplyAsync(() -> {
            try { return submitDirect(method, path, body, authorization); }
            catch (Exception exception) { throw new IllegalStateException(exception); }
        }, worker);
    }

    private MvcResult submitDirect(String method, String path, Map<String, ?> body, String authorization) throws Exception {
        return mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), path).header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))).andReturn();
    }

    private void assertStatuses(List<MvcResult> responses, int... expected) {
        assertThat(responses.stream().mapToInt(response -> response.getResponse().getStatus()).toArray()).containsExactlyInAnyOrder(expected);
    }

    private void assertDuplicate(List<MvcResult> responses) throws Exception {
        var duplicate = responses.stream().filter(result -> result.getResponse().getStatus() == 409).findFirst().orElseThrow();
        var error = mapper.readTree(duplicate.getResponse().getContentAsByteArray());
        assertThat(error.path("error_code").asText()).isEqualTo("CARD_DUPLICATE");
        assertThat(error.has("details")).isFalse();
    }

    private void seed(int count) { jdbc.update("INSERT INTO learning.collection_items(collection_id,title,content,order_index) SELECT ?,'Thẻ tự soạn '||n,'Mặt sau',n-1 FROM generate_series(1,?) n", deck, count); }
    private int count() { return jdbc.queryForObject("SELECT count(*) FROM learning.collection_items WHERE collection_id=? AND deleted_at IS NULL", Integer.class, deck); }
    private long user() { return jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,'fixture-hash-unused','Người thử đồng thời') RETURNING id", Long.class, UUID.randomUUID() + "@example.test"); }
    private long deck(long owner) { return jdbc.queryForObject("INSERT INTO learning.collections(user_id,kind,name) VALUES (?,'FLASHCARD_DECK','Bộ đồng thời') RETURNING id", Long.class, owner); }
    private long lexeme() { return jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,meaning_vi) VALUES ('WORD',?,'Nghĩa tự soạn') RETURNING id", Long.class, "Từ đồng thời " + UUID.randomUUID()); }
    private String bearer(long owner) { return "Bearer " + jwt.issueAccessToken(owner, List.of("LEARNER"), false, UUID.randomUUID()); }
    private String path() { return "/api/flashcard-decks/" + deck + "/cards"; }
    private void lock() { namedJdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(:key,0))", Map.of("key", "library:decks:" + user), rs -> null); }
    private int waiters() {
        return jdbc.queryForObject("""
                WITH key AS (SELECT hashtextextended(?,0) AS value)
                SELECT count(*)::int FROM pg_locks,key WHERE locktype='advisory' AND NOT granted AND objsubid=1
                  AND database=(SELECT oid FROM pg_database WHERE datname=current_database())
                  AND classid=((value >> 32) & 4294967295)::oid AND objid=(value & 4294967295)::oid
                """, Integer.class, "library:decks:" + user);
    }
    private static void await(BooleanSupplier ready) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!ready.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new IllegalStateException("Expected PostgreSQL lock wait was not observed");
            try { Thread.sleep(10); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
        }
    }

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
