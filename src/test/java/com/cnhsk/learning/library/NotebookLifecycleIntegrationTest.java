package com.cnhsk.learning.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
@Import(NotebookLifecycleIntegrationTest.LifecycleClockConfiguration.class)
class NotebookLifecycleIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-05T05:00:00.123456Z");
    private static final String CONTENT = "Ghi chú tự soạn <script>chỉ là văn bản</script>";
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private LifecycleClock clock;
    private long user;

    @BeforeEach
    void caller() {
        clock.set(NOW);
        user = user();
    }

    @AfterEach
    void resetClock() { clock.reset(); }

    @Test
    void FR012_AT11_deleteIs204AndRepeatedDeletePreservesOriginalTimestamp() throws Exception {
        long lexeme = lexeme();
        long id = mapper.readTree(mvc.perform(post("/api/notes").header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                        "title", "Tiêu đề tự soạn", "content", CONTENT, "tags", List.of("hsk1"),
                        "lexemeId", lexeme, "sourceType", "DICTIONARY"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray()).path("id").asLong();
        mvc.perform(delete("/api/notes/{id}", id).header("Authorization", bearer(user)))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(deletedAt(id)).isEqualTo(NOW);
        mvc.perform(get("/api/notes/{id}", id).header("Authorization", bearer(user)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("COLLECTION_NOT_OWNED"));
        mvc.perform(put("/api/notes/{id}", id).header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Không được sửa bản đã xoá\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("COLLECTION_NOT_OWNED"));
        mvc.perform(get("/api/notes").header("Authorization", bearer(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        JsonNode trash = trash();
        assertThat(trash).hasSize(1);
        assertThat(trash.get(0).path("deletedAt").asText()).isEqualTo(NOW.toString());
        assertThat(trash.get(0).path("restorableUntil").asText()).isEqualTo(NOW.plus(Duration.ofDays(30)).toString());
        assertThat(trash.get(0).path("snippet").asText()).isEqualTo(CONTENT);
        clock.set(NOW.plus(Duration.ofDays(10)));
        mvc.perform(delete("/api/notes/{id}", id).header("Authorization", bearer(user)))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(deletedAt(id)).isEqualTo(NOW);
        mvc.perform(post("/api/notes/{id}/restore", id).header("Authorization", bearer(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").value(CONTENT))
                .andExpect(jsonPath("$.lexemeId").value(lexeme)).andExpect(jsonPath("$.sourceType").value("DICTIONARY"))
                .andExpect(jsonPath("$.tags[0]").value("hsk1"))
                .andExpect(jsonPath("$.updatedAt").value(clock.instant().toString()));
        assertThat(deletedAt(id)).isNull();
        assertThat(trash()).isEmpty();
        mvc.perform(get("/api/notes/{id}", id).header("Authorization", bearer(user))).andExpect(status().isOk());
    }

    @Test
    void FR012_emptyTrashDoesNotCreateNotebook() throws Exception {
        assertThat(trash()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.collections WHERE user_id=?", Integer.class, user)).isZero();
    }

    @ParameterizedTest
    @CsvSource({"29,0,true", "30,0,true", "30,1,false", "31,0,false"})
    void FR012_AT11_clockControlsInclusive30DayBoundary(int days, long extraMicros, boolean restorable) throws Exception {
        Instant deleted = NOW.minus(Duration.ofDays(days)).minus(extraMicros, ChronoUnit.MICROS);
        long id = item(book(user, "NOTEBOOK", false), null, deleted);
        JsonNode trash = trash();
        assertThat(trash.size()).isEqualTo(restorable ? 1 : 0);
        if (restorable) {
            assertThat(trash.get(0).path("restorableUntil").asText()).isEqualTo(deleted.plus(Duration.ofDays(30)).toString());
            mvc.perform(post("/api/notes/{id}/restore", id).header("Authorization", bearer(user)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.content").value(CONTENT)).andExpect(jsonPath("$.updatedAt").value(NOW.toString()));
            assertThat(deletedAt(id)).isNull();
        } else {
            mvc.perform(post("/api/notes/{id}/restore", id).header("Authorization", bearer(user)))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error_code").value("NOTE_NOT_RESTORABLE"));
            assertThat(deletedAt(id)).isEqualTo(deleted);
        }
        assertThat(jdbc.queryForObject("SELECT content FROM learning.collection_items WHERE id=?", String.class, id)).isEqualTo(CONTENT);
    }

    @Test
    void FR012_trashIsPrivateOrderedAndContainsRawCodePointBoundedSnippets() throws Exception {
        long book = book(user, "NOTEBOOK", false);
        long older = item(book, null, NOW.minus(Duration.ofDays(29)));
        long first = item(book, null, NOW.minusSeconds(60));
        long second = item(book, null, NOW.minusSeconds(60));
        item(book, null, null);
        item(book, null, NOW.minus(Duration.ofDays(31)));
        item(book(user(), "NOTEBOOK", false), null, NOW);
        item(book(user, "FLASHCARD_DECK", false), null, NOW);
        item(book(user, "NOTEBOOK", true), null, NOW);
        String raw = "<script>" + "𠀀".repeat(250) + "</script>";
        jdbc.update("UPDATE learning.collection_items SET content=? WHERE id=?", raw, second);
        JsonNode trash = trash();
        assertThat(trash).hasSize(3);
        assertThat(trash.findValuesAsText("id")).containsExactly(String.valueOf(second), String.valueOf(first), String.valueOf(older));
        String snippet = trash.get(0).path("snippet").asText();
        assertThat(snippet).startsWith("<script>");
        assertThat(snippet.codePointCount(0, snippet.length())).isEqualTo(200);
        assertThat(trash.get(0).has("content")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {999, 1000})
    void FR012_E11_restoreUnlinkedNoteChecksActiveQuotaAndLeavesRejectedNoteDeleted(int activeCount) throws Exception {
        long book = book(user, "NOTEBOOK", false);
        long id = item(book, null, NOW.minusSeconds(60));
        fill(book, activeCount);
        var request = post("/api/notes/{id}/restore", id).header("Authorization", bearer(user));
        if (activeCount == 999) {
            mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.lexemeId").isEmpty());
            assertThat(deletedAt(id)).isNull();
        } else {
            mvc.perform(request).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error_code").value("LIBRARY_LIMIT_REACHED"))
                    .andExpect(jsonPath("$.details.kind").value("NOTES")).andExpect(jsonPath("$.details.limit").value(1000));
            assertThat(deletedAt(id)).isEqualTo(NOW.minusSeconds(60));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.collection_items WHERE collection_id=? AND deleted_at IS NULL", Integer.class, book)).isEqualTo(1000);
    }

    @Test
    void FR012_restoreChecksOwnershipThenStateThenDuplicateThenLimit() throws Exception {
        long book = book(user, "NOTEBOOK", false);
        long lexeme = lexeme();
        long existing = item(book, lexeme, null);
        Instant expired = NOW.minus(Duration.ofDays(31));
        long target = item(book, lexeme, expired);
        fill(book, 999);
        mvc.perform(post("/api/notes/{id}/restore", target).header("Authorization", bearer(user())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("COLLECTION_NOT_OWNED"));
        mvc.perform(post("/api/notes/{id}/restore", target).header("Authorization", bearer(user)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error_code").value("NOTE_NOT_RESTORABLE"));
        jdbc.update("UPDATE learning.collection_items SET deleted_at=? WHERE id=?", Timestamp.from(NOW.minusSeconds(60)), target);
        mvc.perform(post("/api/notes/{id}/restore", target).header("Authorization", bearer(user)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error_code").value("NOTE_DUPLICATE"))
                .andExpect(jsonPath("$.details.noteId").value(existing));
        assertThat(deletedAt(target)).isEqualTo(NOW.minusSeconds(60));
        mvc.perform(post("/api/notes/{id}/restore", existing).header("Authorization", bearer(user)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error_code").value("NOTE_NOT_RESTORABLE"));
        jdbc.update("UPDATE learning.collection_items SET deleted_at=? WHERE id=?", Timestamp.from(NOW), existing);
        mvc.perform(post("/api/notes/{id}/restore", target).header("Authorization", bearer(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lexemeId").value(lexeme));
    }

    @ParameterizedTest
    @ValueSource(strings = {"bad", "0", "-1", "1.5", "9223372036854775808", "9223372036854775807"})
    void FR012_invalidAndMissingIdsUseSame403(String id) throws Exception {
        for (var request : List.of(delete("/api/notes/{id}", id), post("/api/notes/{id}/restore", id))) {
            mvc.perform(request.header("Authorization", bearer(user)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("COLLECTION_NOT_OWNED"))
                    .andExpect(jsonPath("$.message").value("Bạn không có quyền truy cập ghi chú này."));
        }
    }

    @Test
    void FR012_foreignNotesWrongKindAndDeletedNotebooksUseSame403() throws Exception {
        for (long book : List.of(book(user(), "NOTEBOOK", false), book(user, "FLASHCARD_DECK", false), book(user, "NOTEBOOK", true))) {
            long id = item(book, null, NOW.minusSeconds(60));
            for (var request : List.of(delete("/api/notes/{id}", id), post("/api/notes/{id}/restore", id))) {
                mvc.perform(request.header("Authorization", bearer(user)))
                        .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("COLLECTION_NOT_OWNED"));
            }
            assertThat(deletedAt(id)).isEqualTo(NOW.minusSeconds(60));
        }
    }

    private JsonNode trash() throws Exception {
        return mapper.readTree(mvc.perform(get("/api/notes/trash").header("Authorization", bearer(user)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }

    private long user() {
        return jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,'fixture-hash-unused','Người thử vòng đời') RETURNING id",
                Long.class, UUID.randomUUID() + "@example.test");
    }

    private long book(long owner, String kind, boolean deleted) {
        return jdbc.queryForObject("INSERT INTO learning.collections(user_id,kind,name,deleted_at) VALUES (?,?,'Sổ tự soạn',?) RETURNING id",
                Long.class, owner, kind, deleted ? Timestamp.from(NOW) : null);
    }

    private long item(long book, Long lexeme, Instant deleted) {
        return jdbc.queryForObject("""
                INSERT INTO learning.collection_items(collection_id,lexeme_id,title,content,tags,source_type,deleted_at)
                VALUES (?,?,'Tiêu đề tự soạn',?,ARRAY['hsk1'],'MANUAL',?) RETURNING id
                """, Long.class, book, lexeme, CONTENT, deleted == null ? null : Timestamp.from(deleted));
    }

    private void fill(long book, int count) {
        jdbc.update("INSERT INTO learning.collection_items(collection_id,content) SELECT ?, 'Ghi chú hạn mức ' || n FROM generate_series(1,?) n", book, count);
    }

    private long lexeme() {
        return jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,meaning_vi) VALUES ('WORD',?,'Từ tự soạn') RETURNING id",
                Long.class, "复试-" + UUID.randomUUID());
    }

    private Instant deletedAt(long id) {
        Timestamp deleted = jdbc.queryForObject("SELECT deleted_at FROM learning.collection_items WHERE id=?", Timestamp.class, id);
        return deleted == null ? null : deleted.toInstant();
    }

    private String bearer(long id) { return "Bearer " + jwt.issueAccessToken(id, List.of("LEARNER"), false, UUID.randomUUID()); }

    @TestConfiguration
    static class LifecycleClockConfiguration {
        @Bean @Primary LifecycleClock notebookLifecycleClock() { return new LifecycleClock(); }
    }

    static class LifecycleClock extends Clock {
        private final AtomicReference<Instant> fixed = new AtomicReference<>();
        void set(Instant now) { fixed.set(now); }
        void reset() { fixed.set(null); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() {
            Instant now = fixed.get();
            return now == null ? Clock.systemUTC().instant() : now;
        }
    }
}
