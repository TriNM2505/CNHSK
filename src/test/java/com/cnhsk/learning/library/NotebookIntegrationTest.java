package com.cnhsk.learning.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class NotebookIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    private long user;
    private String authorization;

    @BeforeEach
    void caller() {
        user = user();
        authorization = bearer(user);
    }

    @Test
    void FR008_AT08_getEmptyDoesNotCreateNotebookAndClientCannotSelectOwner() throws Exception {
        mvc.perform(get("/api/notes").header("Authorization", authorization)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.total").value(0));
        assertThat(countBooks()).isZero();
        long other = user();
        JsonNode note = create(Map.of("content", "Ghi chú tự soạn", "userId", other));
        assertThat(note.path("tags")).isEmpty();
        assertThat(note.path("lexemeId").isNull()).isTrue();
        assertThat(countBooks()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT user_id FROM learning.collections WHERE id=(SELECT collection_id FROM learning.collection_items WHERE id=?)",
                Long.class, note.path("id").asLong())).isEqualTo(user);
    }

    @Test
    void FR009_AT08_validNotesPreserveTextAndNullableFields() throws Exception {
        String content = "Dòng một\n<script>văn bản tự soạn</script>\nDòng hai";
        JsonNode note = create(Map.of("title", "Tiêu đề tự soạn", "content", content, "tags", List.of("hsk1", "tự soạn"), "sourceType", "MANUAL"));
        assertThat(note.path("content").asText()).isEqualTo(content);
        assertThat(note.path("tags")).hasSize(2);
        mvc.perform(get("/api/notes/{id}", note.path("id").asLong()).header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").value(content));
    }

    @Test
    void FR009_AT08_invalidBodiesReturn422WithoutCreatingNotebook() throws Exception {
        List<Map<String, Object>> cases = List.of(Map.of(), Map.of("content", ""), Map.of("content", "  "),
                Map.of("content", "x".repeat(10_001)), Map.of("content", "x", "title", "x".repeat(201)),
                Map.of("content", "x", "tags", java.util.Collections.nCopies(11, "tag")),
                Map.of("content", "x", "tags", List.of("x".repeat(51))), Map.of("content", "x", "sourceType", "OTHER"),
                Map.of("content", "x\u0000"));
        for (var body : cases) mvc.perform(post("/api/notes").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/notes").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"x\",\"lexemeId\":\"bad\"}"))
                .andExpect(status().isUnprocessableEntity());
        assertThat(countBooks()).isZero();
    }

    @Test
    void FR009_AT08_limitReturnsDetailsButEditingAtLimitStillWorks() throws Exception {
        JsonNode note = create(Map.of("content", "Ghi chú đầu"));
        long id = note.path("id").asLong();
        jdbc.update("""
                INSERT INTO learning.collection_items(collection_id, content)
                SELECT (SELECT collection_id FROM learning.collection_items WHERE id=?), 'Ghi chú hạn mức tự soạn ' || n
                FROM generate_series(1,999) n
                """, id);
        mvc.perform(post("/api/notes").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Ghi chú vượt giới hạn\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("LIBRARY_LIMIT_REACHED"))
                .andExpect(jsonPath("$.details.kind").value("NOTES")).andExpect(jsonPath("$.details.limit").value(1000));
        mvc.perform(put("/api/notes/{id}", id).header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Vẫn được sửa\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("Vẫn được sửa"));
    }

    @Test
    void FR009_FR011_duplicateLexemeReturnsExistingIdAndDeletedNotesDoNotConflict() throws Exception {
        long lexeme = lexeme();
        JsonNode first = create(Map.of("content", "Ghi chú từ", "lexemeId", lexeme));
        long firstId = first.path("id").asLong();
        String body = mapper.writeValueAsString(Map.of("content", "Ghi chú cùng từ", "lexemeId", lexeme));
        mvc.perform(post("/api/notes").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error_code").value("NOTE_DUPLICATE"))
                .andExpect(jsonPath("$.details.noteId").value(firstId));
        long second = create(Map.of("content", "Ghi chú rời")).path("id").asLong();
        mvc.perform(put("/api/notes/{id}", second).header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.details.noteId").value(firstId));
        mvc.perform(put("/api/notes/{id}", firstId).header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        jdbc.update("UPDATE learning.collection_items SET deleted_at=now() WHERE id=?", firstId);
        assertThat(create(Map.of("content", "Ghi chú mới sau khi xoá", "lexemeId", lexeme)).path("id").asLong()).isNotEqualTo(firstId);
        create(Map.of("content", "Ghi chú không gắn từ"));
        create(Map.of("content", "Ghi chú không gắn từ"));
    }

    @Test
    void FR009_FR011_missingLexemeIs404ForBothWritesAndOwnershipIsCheckedFirst() throws Exception {
        long id = create(Map.of("content", "Ghi chú thử")).path("id").asLong();
        String body = "{\"content\":\"Thử mục từ thiếu\",\"lexemeId\":9223372036854775807}";
        for (var request : List.of(post("/api/notes"), put("/api/notes/{id}", id))) {
            mvc.perform(request.header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code").value("LEXEME_NOT_FOUND"));
        }
        mvc.perform(put("/api/notes/{id}", id).header("Authorization", bearer(user())).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("COLLECTION_NOT_OWNED"));
    }

    @Test
    void FR010_AT09_searchRanksTitleAndContentMatchesFiltersTagsAndReturnsRawSnippet() throws Exception {
        long exact = create(Map.of("title", "gia đình", "content", "A".repeat(300) + " gia đình <script>thử</script> " + "B".repeat(300), "tags", List.of("hsk1")))
                .path("id").asLong();
        create(Map.of("content", "Ví dụ tự soạn về gia đình", "tags", List.of("hsk2")));
        create(Map.of("content", "Ghi chú không khớp", "tags", List.of("hsk1")));
        JsonNode result = list("gia đình", "hsk1");
        assertThat(result.path("total").asInt()).isEqualTo(1);
        assertThat(result.path("items").get(0).path("id").asLong()).isEqualTo(exact);
        String snippet = result.path("items").get(0).path("snippet").asText();
        assertThat(snippet).contains("gia đình", "<script>");
        assertThat(snippet.codePointCount(0, snippet.length())).isLessThanOrEqualTo(200);
        assertThat(list("gia đình", null).path("items").get(0).path("id").asLong()).isEqualTo(exact);
        assertThat(list(null, "hsk").path("items")).isEmpty();
    }

    @Test
    void FR010_AT09_searchBindsQuotesAndEscapesWildcards() throws Exception {
        long id = create(Map.of("content", "O'Brien 100%_! tự soạn")).path("id").asLong();
        create(Map.of("content", "100XX tự soạn"));
        for (String query : List.of("O'Brien", "100%_!")) {
            assertThat(list(query, null).path("items")).hasSize(1);
            assertThat(list(query, null).path("items").get(0).path("id").asLong()).isEqualTo(id);
        }
        assertThat(list("' OR 1=1 --", null).path("items")).isEmpty();
    }

    @Test
    void FR010_paginationTotalsAndDeletedOrOtherUsersNotesAreIsolated() throws Exception {
        long id = create(Map.of("content", "Ghi chú phân trang")).path("id").asLong();
        jdbc.update("""
                INSERT INTO learning.collection_items(collection_id, content)
                SELECT (SELECT collection_id FROM learning.collection_items WHERE id=?), 'Phân trang tự soạn ' || n
                FROM generate_series(1,54) n
                """, id);
        assertThat(list(null, null).path("items")).hasSize(20);
        mvc.perform(get("/api/notes").param("page", "1").param("size", "50").header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(5)).andExpect(jsonPath("$.total").value(55));
        jdbc.update("UPDATE learning.collection_items SET deleted_at=now() WHERE id=?", id);
        assertThat(list(null, null).path("total").asInt()).isEqualTo(54);
        mvc.perform(get("/api/notes").header("Authorization", bearer(user()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/notes").param("page", "2147483647").param("size", "50").header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.total").value(54));
    }

    @Test
    void FR010_AT09_invalidSearchParametersReturn422() throws Exception {
        for (var params : List.of(Map.of("q", "家"), Map.of("q", ""), Map.of("q", "x".repeat(101)), Map.of("q", "a\u0000"),
                Map.of("tag", "x".repeat(51)), Map.of("size", "0"), Map.of("size", "51"), Map.of("page", "-1"),
                Map.of("size", "bad"), Map.of("page", "2147483648"))) {
            var request = get("/api/notes").header("Authorization", authorization);
            params.forEach(request::param);
            mvc.perform(request).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"bad", "0", "-1", "9223372036854775808", "9223372036854775807"})
    void FR011_missingOrMalformedNoteIdsReturnSame403(String id) throws Exception {
        mvc.perform(get("/api/notes/{id}", id).header("Authorization", authorization))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("COLLECTION_NOT_OWNED"));
    }

    @Test
    void FR011_AT10_lastWriteWinsAndUpdatesTimestampAndOptionalFields() throws Exception {
        JsonNode first = create(Map.of("content", "Bản đầu", "tags", List.of("hsk1"), "title", "Tiêu đề"));
        long id = first.path("id").asLong();
        for (String content : List.of("Thiết bị A", "Thiết bị B")) {
            mvc.perform(put("/api/notes/{id}", id).header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("content", content)))).andExpect(status().isOk());
        }
        JsonNode result = mapper.readTree(mvc.perform(get("/api/notes/{id}", id).header("Authorization", authorization))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(result.path("content").asText()).isEqualTo("Thiết bị B");
        assertThat(Instant.parse(result.path("updatedAt").asText())).isAfter(Instant.parse(first.path("updatedAt").asText()));
        assertThat(result.path("title").isNull()).isTrue();
        assertThat(result.path("tags")).isEmpty();
        for (var request : List.of(get("/api/notes/{id}", id), put("/api/notes/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"x\"}"))) {
            mvc.perform(request.header("Authorization", bearer(user()))).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("COLLECTION_NOT_OWNED"));
        }
    }

    private JsonNode create(Map<String, ?> body) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/notes").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray());
    }

    private JsonNode list(String q, String tag) throws Exception {
        var request = get("/api/notes").header("Authorization", authorization);
        if (q != null) request.param("q", q);
        if (tag != null) request.param("tag", tag);
        return mapper.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }

    private long user() {
        return jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?, 'fixture-hash-unused', 'Người thử') RETURNING id",
                Long.class, UUID.randomUUID() + "@example.test");
    }

    private int countBooks() {
        return jdbc.queryForObject("SELECT count(*) FROM learning.collections WHERE user_id=? AND kind='NOTEBOOK'", Integer.class, user);
    }

    private long lexeme() {
        return jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,meaning_vi) VALUES ('WORD', ?, 'Mục từ tự soạn') RETURNING id",
                Long.class, "笔试-" + UUID.randomUUID());
    }

    private String bearer(long id) { return "Bearer " + jwt.issueAccessToken(id, List.of("LEARNER"), false, UUID.randomUUID()); }
}
