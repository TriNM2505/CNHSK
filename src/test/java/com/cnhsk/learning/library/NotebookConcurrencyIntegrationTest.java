package com.cnhsk.learning.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Independent committed request transactions contend on the actual PostgreSQL advisory key. */
@AutoConfigureMockMvc
class NotebookConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private NamedParameterJdbcTemplate namedJdbc;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private PlatformTransactionManager transactionManager;
    private long user;
    private String bearer;

    @BeforeEach
    void caller() {
        user = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?, 'fixture-hash-unused', 'Người thử song song') RETURNING id",
                Long.class, UUID.randomUUID() + "@example.test");
        bearer = "Bearer " + jwt.issueAccessToken(user, List.of("LEARNER"), false, UUID.randomUUID());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 999, 1000})
    void FR008_FR009_AT08_parallelCreatesShareOneNotebookAndNeverExceed1000(int count) throws Exception {
        if (count > 0) {
            long book = jdbc.queryForObject("INSERT INTO learning.collections(user_id,kind,name) VALUES (?,'NOTEBOOK','Sổ tay') RETURNING id", Long.class, user);
            jdbc.update("INSERT INTO learning.collection_items(collection_id,content) SELECT ?, 'Ghi chú song song ' || n FROM generate_series(1,?) n", book, count);
        }
        List<MvcResult> results = together(Map.of("content", "Ghi chú đồng thời A"), Map.of("content", "Ghi chú đồng thời B"));
        int[] expected = count == 0 ? new int[] {201, 201} : count == 999 ? new int[] {201, 422} : new int[] {422, 422};
        assertThat(results.stream().mapToInt(result -> result.getResponse().getStatus()).toArray()).containsExactlyInAnyOrder(expected);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.collections WHERE user_id=? AND kind='NOTEBOOK'", Integer.class, user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.collection_items WHERE collection_id IN (SELECT id FROM learning.collections WHERE user_id=?)",
                Integer.class, user)).isEqualTo(count == 0 ? 2 : 1000);
        for (var result : results) if (result.getResponse().getStatus() == 422) {
            var json = mapper.readTree(result.getResponse().getContentAsByteArray());
            assertThat(json.path("error_code").asText()).isEqualTo("LIBRARY_LIMIT_REACHED");
            assertThat(json.path("details").path("limit").asInt()).isEqualTo(1000);
        }
    }

    @Test
    void FR009_parallelDuplicateLexemeReturns201And409WithExistingNoteId() throws Exception {
        long lexeme = jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,meaning_vi) VALUES ('WORD',?,'Từ tự soạn') RETURNING id",
                Long.class, "并试-" + UUID.randomUUID());
        var body = Map.of("content", "Ghi chú cùng từ đồng thời", "lexemeId", lexeme);
        List<MvcResult> results = together(body, body);
        assertThat(results.stream().mapToInt(result -> result.getResponse().getStatus()).toArray()).containsExactlyInAnyOrder(201, 409);
        var created = results.stream().filter(result -> result.getResponse().getStatus() == 201).findFirst().orElseThrow();
        var duplicate = results.stream().filter(result -> result.getResponse().getStatus() == 409).findFirst().orElseThrow();
        long noteId = mapper.readTree(created.getResponse().getContentAsByteArray()).path("id").asLong();
        var error = mapper.readTree(duplicate.getResponse().getContentAsByteArray());
        assertThat(error.path("error_code").asText()).isEqualTo("NOTE_DUPLICATE");
        assertThat(error.path("details").path("noteId").asLong()).isEqualTo(noteId);
    }

    @Test
    void anotherUserCanCreateWhileFirstUserLockIsHeld() {
        long other = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?, 'fixture-hash-unused', 'Người thử khác') RETURNING id",
                Long.class, UUID.randomUUID() + "@example.test");
        String otherBearer = "Bearer " + jwt.issueAccessToken(other, List.of("LEARNER"), false, UUID.randomUUID());
        ExecutorService workers = Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                lock();
                try {
                    int response = CompletableFuture.supplyAsync(() -> {
                        try { return mvc.perform(post("/api/notes").header("Authorization", otherBearer)
                                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Không bị khoá chung\"}"))
                                .andReturn().getResponse().getStatus(); }
                        catch (Exception exception) { throw new IllegalStateException(exception); }
                    }, workers).get(10, TimeUnit.SECONDS);
                    assertThat(response).isEqualTo(201);
                } catch (Exception exception) { throw new IllegalStateException(exception); }
                return null;
            });
        } finally { workers.shutdownNow(); }
    }

    private List<MvcResult> together(Map<String, ?> first, Map<String, ?> second) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            List<CompletableFuture<MvcResult>> requests = new TransactionTemplate(transactionManager).execute(status -> {
                lock();
                var pending = List.of(submit(workers, first), submit(workers, second));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                while (waiters() != 2) {
                    if (System.nanoTime() > deadline) throw new IllegalStateException("Two requests did not wait on notebook advisory lock");
                    try { Thread.sleep(10); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
                }
                return pending;
            });
            return List.of(requests.get(0).get(20, TimeUnit.SECONDS), requests.get(1).get(20, TimeUnit.SECONDS));
        } finally { workers.shutdownNow(); }
    }

    private CompletableFuture<MvcResult> submit(ExecutorService workers, Map<String, ?> body) {
        return CompletableFuture.supplyAsync(() -> {
            try { return mvc.perform(post("/api/notes").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(body))).andReturn(); }
            catch (Exception exception) { throw new IllegalStateException(exception); }
        }, workers);
    }

    private void lock() {
        namedJdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(:key,0))", Map.of("key", "library:notebook:" + user), rs -> null);
    }

    private int waiters() {
        return jdbc.queryForObject("""
                WITH key AS (SELECT hashtextextended(?,0) AS value)
                SELECT count(*)::int FROM pg_locks, key WHERE locktype='advisory' AND NOT granted AND objsubid=1
                  AND database=(SELECT oid FROM pg_database WHERE datname=current_database())
                  AND classid=((value >> 32) & 4294967295)::oid AND objid=(value & 4294967295)::oid
                """, Integer.class, "library:notebook:" + user);
    }
}
