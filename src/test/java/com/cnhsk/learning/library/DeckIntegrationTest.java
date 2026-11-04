package com.cnhsk.learning.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.sql.Timestamp;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
@Import(DeckIntegrationTest.TimeConfiguration.class)
class DeckIntegrationTest extends AbstractIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private TestClock clock;
    @MockitoSpyBean private DeckRepository decks;
    private long user;
    private String authorization;

    @BeforeEach
    void caller() {
        clock.now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        user = user();
        authorization = bearer(user);
    }

    @Test
    void FR013_AT12_emptyReadsDoNotCreateAndEmptyDecksArePrivateSnapshots() throws Exception {
        mvc.perform(get("/api/flashcard-decks").header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        assertThat(count()).isZero();
        JsonNode first = create(Map.of("name", "Tên trùng", "userId", user(), "isPublic", true));
        JsonNode second = create(Map.of("name", "Tên trùng"));
        assertThat(first.path("id").asLong()).isNotEqualTo(second.path("id").asLong());
        assertThat(first.path("description").isNull()).isTrue();
        assertThat(first.path("cards")).isEmpty();
        assertThat(first.path("cardCount").asLong()).isZero();
        assertThat(first.path("dueCount").asLong()).isZero();
        assertThat(Instant.parse(first.path("createdAt").asText())).isEqualTo(clock.now);
        assertThat(jdbc.queryForObject("SELECT user_id FROM learning.collections WHERE id=?", Long.class, first.path("id").asLong())).isEqualTo(user);
        assertThat(jdbc.queryForObject("SELECT is_public FROM learning.collections WHERE id=?", Boolean.class, first.path("id").asLong())).isFalse();
        mvc.perform(get("/api/flashcard-decks").header("Authorization", authorization))
                .andExpect(jsonPath("$[0].id").value(second.path("id").asLong()));
    }

    @Test
    void FR013_namesAndDescriptionsUseCodePointsAndKeepText() throws Exception {
        for (String name : List.of("字", "字".repeat(100), "𠀀".repeat(100))) {
            JsonNode result = create(Map.of("name", name, "description", "𠀀".repeat(500)));
            assertThat(result.path("name").asText()).isEqualTo(name);
            assertThat(result.path("description").asText()).isEqualTo("𠀀".repeat(500));
        }
        assertThat(create(Map.of("name", "<script>văn bản tự soạn</script>", "description", "")).path("name").asText())
                .isEqualTo("<script>văn bản tự soạn</script>");
    }

    @Test
    void FR013_invalidRequestsAre422AndDoNotWrite() throws Exception {
        for (var body : List.of(Map.of(), Map.of("name", ""), Map.of("name", "  "), Map.of("name", "x".repeat(101)),
                Map.of("name", "𠀀".repeat(101)), Map.of("name", "x\u0000"),
                Map.of("name", "x", "description", "𠀀".repeat(501)), Map.of("name", "x", "description", "\u0000"))) {
            mvc.perform(post("/api/flashcard-decks").header("Authorization", authorization)
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        }
        mvc.perform(post("/api/flashcard-decks").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\",\"topicId\":\"invalid\"}"))
                .andExpect(status().isUnprocessableEntity());
        assertThat(count()).isZero();
    }

    @Test
    void FR013_missingTopicPrecedesQuotaAndDeletedDecksDoNotCount() throws Exception {
        jdbc.update("INSERT INTO learning.collections(user_id,kind,name) SELECT ?,'FLASHCARD_DECK','Tự soạn '||n FROM generate_series(1,49) n", user);
        long id = create(Map.of("name", "Bộ thứ 50")).path("id").asLong();
        mvc.perform(post("/api/flashcard-decks").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Chủ đề thiếu\",\"topicId\":9223372036854775807}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code").value("TOPIC_NOT_FOUND"));
        mvc.perform(post("/api/flashcard-decks").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Vượt hạn mức\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("LIBRARY_LIMIT_REACHED"))
                .andExpect(jsonPath("$.details.kind").value("DECKS")).andExpect(jsonPath("$.details.limit").value(50));
        mvc.perform(patch("/api/flashcard-decks/{id}", id).header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Vẫn sửa được\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Vẫn sửa được"));
        mvc.perform(delete("/api/flashcard-decks/{id}", id).header("Authorization", authorization)).andExpect(status().isNoContent());
        create(Map.of("name", "Thay bộ đã xoá"));
        assertThat(count()).isEqualTo(50);
    }

    @Test
    void FR013_AT13_topicCopiesOnlyLexemesInStableOrderWithNoProgressAndNoLiveLink() throws Exception {
        long topic = topic();
        long first = lexeme("Từ thứ nhất", "tự soạn", "Nghĩa thứ nhất");
        long second = lexeme("Từ thứ hai", null, "Nghĩa thứ hai");
        long third = lexeme("Từ thứ ba", "", "Nghĩa thứ ba");
        item(topic, second, 5);
        item(topic, third, 5);
        item(topic, first, 1);
        long kp = jdbc.queryForObject("""
                INSERT INTO learning.knowledge_points(code,title,skill_type,hsk_level,domain,name_zh)
                SELECT 'TV1-'||lpad(n::text,3,'0'),'Điểm tự soạn','VOCAB',1,'TV','Tự soạn'
                FROM generate_series(1,999) n WHERE NOT EXISTS (
                    SELECT 1 FROM learning.knowledge_points WHERE code='TV1-'||lpad(n::text,3,'0')) LIMIT 1 RETURNING id
                """, Long.class);
        jdbc.update("INSERT INTO learning.topic_items(topic_id,knowledge_point_id,order_index) VALUES (?,?,0)", topic, kp);
        JsonNode result = create(Map.of("name", "Bản chụp", "topicId", topic));
        assertThat(result.path("cards")).hasSize(3);
        assertThat(result.path("cardCount").asInt()).isEqualTo(3);
        assertThat(result.path("dueCount").asInt()).isEqualTo(3);
        assertThat(result.path("cards").get(0).path("lexemeId").asLong()).isEqualTo(first);
        assertThat(result.path("cards").get(1).path("lexemeId").asLong()).isEqualTo(second);
        assertThat(result.path("cards").get(2).path("lexemeId").asLong()).isEqualTo(third);
        assertThat(result.path("cards").get(0).path("front").asText()).isEqualTo("Từ thứ nhất");
        assertThat(result.path("cards").get(0).path("back").asText()).isEqualTo("tự soạn — Nghĩa thứ nhất");
        assertThat(result.path("cards").get(1).path("back").asText()).isEqualTo("Nghĩa thứ hai");
        assertThat(result.path("cards").get(2).path("back").asText()).isEqualTo("Nghĩa thứ ba");
        for (JsonNode card : result.path("cards")) {
            assertThat(card.path("dueAt").isNull()).isTrue();
            assertThat(card.path("reviewCount").asInt()).isZero();
            assertThat(card.path("lapseCount").asInt()).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.user_progress WHERE user_id=?", Integer.class, user)).isZero();
        jdbc.update("UPDATE learning.lexemes SET text='Đã đổi nguồn',meaning_vi='Không đổi bản chụp' WHERE id=?", first);
        jdbc.update("DELETE FROM learning.topic_items WHERE topic_id=?", topic);
        mvc.perform(get("/api/flashcard-decks/{id}", result.path("id").asLong()).header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cards[0].front").value("Từ thứ nhất"))
                .andExpect(jsonPath("$.cards[0].back").value("tự soạn — Nghĩa thứ nhất"));
        assertThat(create(Map.of("name", "Chủ đề rỗng", "topicId", topic)).path("cards")).isEmpty();
    }

    @Test
    void FR013_AT13_topicWith501LexemesCopiesFirst500WithoutInventingResponseField() throws Exception {
        long topic = topic();
        String prefix = "Tự soạn " + UUID.randomUUID() + "-";
        jdbc.update("INSERT INTO learning.lexemes(kind,text,meaning_vi) SELECT 'WORD',?||n,'Nghĩa tự soạn' FROM generate_series(1,501) n", prefix);
        jdbc.update("INSERT INTO learning.topic_items(topic_id,lexeme_id,order_index) SELECT ?,id,0 FROM learning.lexemes WHERE text LIKE ? ORDER BY id DESC", topic, prefix + "%");
        List<Long> expected = jdbc.queryForList("SELECT lexeme_id FROM learning.topic_items WHERE topic_id=? ORDER BY order_index,id LIMIT 500", Long.class, topic);
        JsonNode result = create(Map.of("name", "Chép tối đa", "topicId", topic));
        assertThat(result.path("cards")).hasSize(500);
        assertThat(result.path("cardCount").asInt()).isEqualTo(500);
        assertThat(result.path("dueCount").asInt()).isEqualTo(500);
        for (int n = 0; n < 500; n++) assertThat(result.path("cards").get(n).path("lexemeId").asLong()).isEqualTo(expected.get(n));
        assertThat(result.has("truncated")).isFalse();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void FR013_copyFailureRollsBackParentAndAllCards() throws Exception {
        long topic = topic();
        item(topic, lexeme("Bản chụp lỗi", null, "Tự soạn"), 0);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("Synthetic failure after copying cards");
        }).when(decks).copyTopic(anyLong(), eq(topic), anyInt(), any());
        mvc.perform(post("/api/flashcard-decks").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("name", "Phải rollback", "topicId", topic))))
                .andExpect(status().isInternalServerError());
        assertThat(count()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.collection_items i JOIN learning.collections c ON c.id=i.collection_id WHERE c.user_id=?", Integer.class, user)).isZero();
    }

    @Test
    void FR014_countsAreCallerScopedIncludeNewAndDueBoundaryAndIgnoreDeletedItems() throws Exception {
        long id = create(Map.of("name", "Đếm thẻ")).path("id").asLong();
        long fresh = card(id, "Mới", 0);
        long due = card(id, "Đúng hạn", 1);
        long future = card(id, "Tương lai", 2);
        card(id, "Mới chưa có tiến độ", 3);
        long deleted = card(id, "Đã xoá", 4);
        progress(user, due, clock.now, 4, 2);
        progress(user, future, clock.now.plusSeconds(1), 3, 1);
        progress(user(), fresh, clock.now.plusSeconds(3600), 9, 8);
        jdbc.update("INSERT INTO learning.user_progress(user_id,target_type,target_id,next_review_at) VALUES (?,'TOPIC',?,?)", user, fresh, Timestamp.from(clock.now.plusSeconds(3600)));
        jdbc.update("UPDATE learning.collection_items SET deleted_at=now() WHERE id=?", deleted);
        mvc.perform(get("/api/flashcard-decks/{id}", id).header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cardCount").value(4)).andExpect(jsonPath("$.dueCount").value(3))
                .andExpect(jsonPath("$.cards.length()").value(4)).andExpect(jsonPath("$.cards[0].reviewCount").value(0))
                .andExpect(jsonPath("$.cards[1].reviewCount").value(4)).andExpect(jsonPath("$.cards[1].lapseCount").value(2))
                .andExpect(jsonPath("$.cards[1].dueAt").value(clock.now.toString()));
        mvc.perform(get("/api/flashcard-decks").header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].cardCount").value(4)).andExpect(jsonPath("$[0].dueCount").value(3))
                .andExpect(jsonPath("$[0].cards").doesNotExist());
        mvc.perform(get("/api/flashcard-decks").header("Authorization", bearer(user())))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void FR014_patchOnlyEditsMetadataAndDeleteOnlyMarksParentRetainingSchedules() throws Exception {
        long id = create(Map.of("name", "Bản đầu", "description", "Mô tả đầu")).path("id").asLong();
        long card = card(id, "Thẻ giữ nguyên", 0);
        progress(user, card, clock.now.plusSeconds(3600), 8, 2);
        String oldCard = jdbc.queryForObject("SELECT row_to_json(i)::text FROM learning.collection_items i WHERE id=?", String.class, card);
        Map<String, Object> oldProgress = jdbc.queryForMap("SELECT * FROM learning.user_progress WHERE user_id=? AND target_type='FLASHCARD' AND target_id=?", user, card);
        Instant created = clock.now;
        clock.now = clock.now.plusSeconds(10);
        mvc.perform(patch("/api/flashcard-decks/{id}", id).header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Tên mới\",\"topicId\":9223372036854775807,\"isPublic\":true,\"userId\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Tên mới"))
                .andExpect(jsonPath("$.description").isEmpty()).andExpect(jsonPath("$.cards").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT updated_at FROM learning.collections WHERE id=?", Timestamp.class, id).toInstant()).isEqualTo(clock.now);
        assertThat(jdbc.queryForObject("SELECT created_at FROM learning.collections WHERE id=?", Timestamp.class, id).toInstant()).isEqualTo(created);
        assertThat(jdbc.queryForObject("SELECT is_public FROM learning.collections WHERE id=?", Boolean.class, id)).isFalse();
        Map<String, Object> beforeDelete = jdbc.queryForMap("SELECT * FROM learning.collections WHERE id=?", id);
        clock.now = clock.now.plusSeconds(10);
        mvc.perform(delete("/api/flashcard-decks/{id}", id).header("Authorization", authorization))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        Map<String, Object> afterDelete = jdbc.queryForMap("SELECT * FROM learning.collections WHERE id=?", id);
        assertThat(((Timestamp) afterDelete.remove("deleted_at")).toInstant()).isEqualTo(clock.now);
        beforeDelete.remove("deleted_at");
        assertThat(afterDelete).isEqualTo(beforeDelete);
        assertThat(jdbc.queryForObject("SELECT row_to_json(i)::text FROM learning.collection_items i WHERE id=?", String.class, card)).isEqualTo(oldCard);
        assertThat(jdbc.queryForMap("SELECT * FROM learning.user_progress WHERE user_id=? AND target_type='FLASHCARD' AND target_id=?", user, card)).isEqualTo(oldProgress);
        denied(Long.toString(id), authorization);
        mvc.perform(get("/api/flashcard-decks").header("Authorization", authorization)).andExpect(content().json("[]"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"bad", "0", "-1", "9223372036854775808", "9223372036854775807"})
    void FR014_invalidAndMissingIdsAre403ForEveryOperation(String id) throws Exception { denied(id, authorization); }

    @Test
    void FR014_foreignAndWrongKindIdsAre403AndNeverModified() throws Exception {
        long id = create(Map.of("name", "Bộ riêng")).path("id").asLong();
        denied(Long.toString(id), bearer(user()));
        long notebook = jdbc.queryForObject("INSERT INTO learning.collections(user_id,kind,name) VALUES (?,'NOTEBOOK','Sổ tay') RETURNING id", Long.class, user);
        denied(Long.toString(notebook), authorization);
        assertThat(jdbc.queryForObject("SELECT name FROM learning.collections WHERE id=?", String.class, id)).isEqualTo("Bộ riêng");
    }

    private void denied(String id, String bearer) throws Exception {
        for (var request : List.of(get("/api/flashcard-decks/{id}", id), delete("/api/flashcard-decks/{id}", id),
                patch("/api/flashcard-decks/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Không được sửa\"}"))) {
            mvc.perform(request.header("Authorization", bearer)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("COLLECTION_NOT_OWNED"));
        }
    }

    private JsonNode create(Map<String, ?> body) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/flashcard-decks").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray());
    }

    private long user() {
        return jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,'fixture-hash-unused','Người thử') RETURNING id", Long.class, UUID.randomUUID() + "@example.test");
    }

    private String bearer(long id) { return "Bearer " + jwt.issueAccessToken(id, List.of("LEARNER"), false, UUID.randomUUID()); }
    private int count() { return jdbc.queryForObject("SELECT count(*) FROM learning.collections WHERE user_id=? AND kind='FLASHCARD_DECK' AND deleted_at IS NULL", Integer.class, user); }
    private long topic() { return jdbc.queryForObject("INSERT INTO learning.topics(code,name,hsk_level) VALUES (?,'Chủ đề tự soạn',1) RETURNING id", Long.class, "T10-" + UUID.randomUUID()); }
    private long lexeme(String text, String pinyin, String meaning) {
        return jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,pinyin,meaning_vi) VALUES ('WORD',?,?,?) RETURNING id", Long.class, text, pinyin, meaning);
    }
    private void item(long topic, long lexeme, int order) { jdbc.update("INSERT INTO learning.topic_items(topic_id,lexeme_id,order_index) VALUES (?,?,?)", topic, lexeme, order); }
    private long card(long deck, String front, int order) {
        return jdbc.queryForObject("INSERT INTO learning.collection_items(collection_id,title,content,order_index) VALUES (?,?,'Mặt sau tự soạn',?) RETURNING id", Long.class, deck, front, order);
    }
    private void progress(long owner, long card, Instant due, int reviews, int lapses) {
        jdbc.update("INSERT INTO learning.user_progress(user_id,target_type,target_id,next_review_at,review_count,lapse_count) VALUES (?,'FLASHCARD',?,?,?,?)",
                owner, card, due == null ? null : Timestamp.from(due), reviews, lapses);
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
