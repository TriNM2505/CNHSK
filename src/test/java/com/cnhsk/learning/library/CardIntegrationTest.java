package com.cnhsk.learning.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
@Import(CardIntegrationTest.TimeConfiguration.class)
class CardIntegrationTest extends AbstractIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private ObjectMapper mapper;
    @Autowired private TestClock clock;
    private long user;
    private long deck;
    private String bearer;

    @BeforeEach
    void caller() {
        clock.now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        user = user();
        deck = deck(user, "FLASHCARD_DECK");
        bearer = bearer(user);
    }

    @Test
    void FR015_AT14_manualCardsKeepTextAndAppendAfterExistingAndDeletedCardsWithoutProgress() throws Exception {
        long old = add(Map.of("front", "Đầu", "back", "Mặt sau đầu")).path("id").asLong();
        jdbc.update("UPDATE learning.collection_items SET order_index=17,deleted_at=now() WHERE id=?", old);
        String front = "  <script>tự soạn</script>\t ví dụ  ";
        String back = "Nghĩa tự soạn\nDòng thứ hai <b>văn bản</b>";
        JsonNode card = add(Map.of("front", front, "back", back, "userId", user(), "collectionId", deck(user, "FLASHCARD_DECK"), "orderIndex", -1));
        assertThat(card.path("front").asText()).isEqualTo(front);
        assertThat(card.path("back").asText()).isEqualTo(back);
        assertThat(card.path("lexemeId").isNull()).isTrue();
        assertThat(card.path("dueAt").isNull()).isTrue();
        assertThat(card.path("reviewCount").asInt()).isZero();
        assertThat(card.path("lapseCount").asInt()).isZero();
        long id = card.path("id").asLong();
        assertThat(jdbc.queryForObject("SELECT collection_id FROM learning.collection_items WHERE id=?", Long.class, id)).isEqualTo(deck);
        assertThat(jdbc.queryForObject("SELECT order_index FROM learning.collection_items WHERE id=?", Integer.class, id)).isEqualTo(18);
        assertThat(jdbc.queryForObject("SELECT created_at FROM learning.collection_items WHERE id=?", Timestamp.class, id).toInstant()).isEqualTo(clock.now);
        assertThat(jdbc.queryForObject("SELECT updated_at FROM learning.collection_items WHERE id=?", Timestamp.class, id).toInstant()).isEqualTo(clock.now);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.user_progress WHERE user_id=?", Integer.class, user)).isZero();
        mvc.perform(get("/api/flashcard-decks/{id}", deck).header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cardCount").value(1)).andExpect(jsonPath("$.dueCount").value(1))
                .andExpect(jsonPath("$.cards[0].id").value(id));
    }

    @Test
    void FR015_AT14_lexemeCardsAreSnapshotsAndNotLinkedToLexemeSchedules() throws Exception {
        long lexeme = lexeme("Từ chụp", "pinyin tự soạn", "Nghĩa chụp");
        jdbc.update("INSERT INTO learning.user_progress(user_id,target_type,target_id,next_review_at,review_count) VALUES (?,'LEXEME',?,now()+interval '1 day',9)", user, lexeme);
        JsonNode card = add(Map.of("lexemeId", lexeme));
        assertThat(card.path("lexemeId").asLong()).isEqualTo(lexeme);
        assertThat(card.path("front").asText()).isEqualTo("Từ chụp");
        assertThat(card.path("back").asText()).isEqualTo("pinyin tự soạn — Nghĩa chụp");
        assertThat(card.path("dueAt").isNull()).isTrue();
        assertThat(card.path("reviewCount").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.user_progress WHERE user_id=? AND target_type='FLASHCARD'", Integer.class, user)).isZero();
        jdbc.update("UPDATE learning.lexemes SET text='Nguồn đã đổi',pinyin='Đã đổi',meaning_vi='Đã đổi' WHERE id=?", lexeme);
        mvc.perform(get("/api/flashcard-decks/{id}", deck).header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cards[0].front").value("Từ chụp"))
                .andExpect(jsonPath("$.cards[0].back").value("pinyin tự soạn — Nghĩa chụp"));
        for (String pinyin : new String[] {null, ""}) {
            long other = lexeme("Không pinyin " + UUID.randomUUID(), pinyin, "Chỉ nghĩa");
            assertThat(add(Map.of("lexemeId", other)).path("back").asText()).isEqualTo("Chỉ nghĩa");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 200})
    void FR015_frontBoundaryUsesUnicodeCodePoints(int length) throws Exception {
        String front = "𠀀".repeat(length);
        assertThat(add(Map.of("front", front, "back", "Mặt sau")).path("front").asText()).isEqualTo(front);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2000})
    void FR015_backBoundaryUsesUnicodeCodePoints(int length) throws Exception {
        String back = "𠀀".repeat(length);
        assertThat(add(Map.of("front", "Mặt trước", "back", back)).path("back").asText()).isEqualTo(back);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"front\":\"x\"}", "{\"back\":\"x\"}", "{\"front\":null,\"back\":\"x\"}",
            "{\"front\":\"x\",\"back\":null}", "{\"front\":\"\",\"back\":\"x\"}", "{\"front\":\"x\",\"back\":\"\"}",
            "{\"lexemeId\":null}", "{\"lexemeId\":1.5}", "{\"lexemeId\":\"1\"}", "{\"lexemeId\":9223372036854775808}",
            "{\"front\":1,\"back\":\"x\"}", "{\"front\":\"x\",\"back\":[]}",
            "{\"lexemeId\":9223372036854775807,\"front\":\"x\"}",
            "{\"lexemeId\":null,\"front\":\"x\",\"back\":\"y\"}"
    })
    void FR015_invalidOrMixedPostBodiesReturn422WithoutWriting(String body) throws Exception {
        perform(post(cardsPath()), body, 422, "VALIDATION_FAILED");
        assertThat(count()).isZero();
    }

    @Test
    void FR015_invalidLengthsWhitespaceAndNulAre422() throws Exception {
        for (var body : List.of(Map.of("front", "𠀀".repeat(201), "back", "x"), Map.of("front", "x", "back", "𠀀".repeat(2001)),
                Map.of("front", " \t\n\u00a0\u3000", "back", "x"), Map.of("front", "x", "back", "\u202f"),
                Map.of("front", "x\u0000", "back", "x"), Map.of("front", "x", "back", "x\u0000"))) {
            perform(post(cardsPath()), mapper.writeValueAsString(body), 422, "VALIDATION_FAILED");
        }
        assertThat(count()).isZero();
    }

    @Test
    void FR015_AT14_duplicatesCheckBothLexemeAndFrontAcrossManualAndDictionaryCards() throws Exception {
        long lexeme = lexeme("Cùng mặt trước", "pinyin", "Nghĩa đầu");
        long first = add(Map.of("lexemeId", lexeme)).path("id").asLong();
        jdbc.update("UPDATE learning.lexemes SET text='Nguồn khác' WHERE id=?", lexeme);
        duplicate(Map.of("lexemeId", lexeme));
        duplicate(Map.of("front", "  Cùng\t mặt\ntrước  ", "back", "Mặt sau khác"));
        long other = lexeme("Cùng mặt trước", null, "Nghĩa khác");
        duplicate(Map.of("lexemeId", other));
        jdbc.update("UPDATE learning.collection_items SET deleted_at=now() WHERE id=?", first);
        assertThat(add(Map.of("lexemeId", lexeme)).path("id").asLong()).isNotEqualTo(first);
        add(Map.of("front", "Cùng mặt trước", "back", "Được thêm sau xoá"));
        long otherDeck = deck(user, "FLASHCARD_DECK");
        mvc.perform(post("/api/flashcard-decks/{id}/cards", otherDeck).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("lexemeId", lexeme))))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "\t", "\n", "\r\n", "\u00a0", "\u2007", "\u202f", "\u3000"})
    void FR015_whitespaceNormalizationTrimsAndCollapsesUnicodeWhitespace(String space) throws Exception {
        add(Map.of("front", "Từ ví dụ", "back", "Mặt sau"));
        duplicate(Map.of("front", space + "Từ" + space + space + "ví" + space + "dụ" + space, "back", "Khác"));
    }

    @Test
    void FR015_normalizationKeepsCaseAccentsAndUnicodeComposition() throws Exception {
        for (String front : List.of("A", "a", "á", "é", "e\u0301")) add(Map.of("front", front, "back", "Tự soạn"));
        assertThat(count()).isEqualTo(5);
    }

    @Test
    void FR015_errorPriorityIsOwnershipThenDataThenMissingLexemeThenDuplicateThenQuota() throws Exception {
        long lexeme = lexeme("Trùng ở hạn mức", null, "Tự soạn");
        long first = add(Map.of("lexemeId", lexeme)).path("id").asLong();
        jdbc.update("INSERT INTO learning.collection_items(collection_id,title,content,order_index) SELECT ?,'Thẻ hạn mức '||n,'Mặt sau',n FROM generate_series(1,499) n", deck);
        long foreignDeck = deck(user(), "FLASHCARD_DECK");
        perform(post("/api/flashcard-decks/{id}/cards", foreignDeck), "{}", 403, "COLLECTION_NOT_OWNED");
        perform(post(cardsPath()), "{}", 422, "VALIDATION_FAILED");
        perform(post(cardsPath()), "{\"lexemeId\":9223372036854775807}", 404, "LEXEME_NOT_FOUND");
        duplicate(Map.of("lexemeId", lexeme));
        mvc.perform(post(cardsPath()).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"front\":\"Thẻ thứ 501\",\"back\":\"Mặt sau\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("LIBRARY_LIMIT_REACHED"))
                .andExpect(jsonPath("$.details.kind").value("CARDS")).andExpect(jsonPath("$.details.limit").value(500));
        mvc.perform(patch(cardsPath() + "/" + first).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"back\":\"Vẫn sửa được\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.back").value("Vẫn sửa được"));
        mvc.perform(delete(cardsPath() + "/" + first).header("Authorization", bearer)).andExpect(status().isNoContent());
        add(Map.of("front", "Thay thẻ đã xoá", "back", "Mặt sau"));
        assertThat(count()).isEqualTo(500);
    }

    @Test
    void FR015_499CardsAcceptExactlyOneMore() throws Exception {
        jdbc.update("INSERT INTO learning.collection_items(collection_id,title,content) SELECT ?,'Biên '||n,'Mặt sau' FROM generate_series(1,499) n", deck);
        add(Map.of("front", "Thẻ thứ 500", "back", "Mặt sau"));
        assertThat(count()).isEqualTo(500);
    }

    @Test
    void FR016_AT15_partialPatchKeepsLinkOrderAndEntireV6Schedule() throws Exception {
        long lexeme = lexeme("Mặt gốc", "pinyin", "Nghĩa gốc");
        long card = add(Map.of("lexemeId", lexeme)).path("id").asLong();
        jdbc.update("UPDATE learning.collection_items SET order_index=37 WHERE id=?", card);
        jdbc.update("""
                INSERT INTO learning.user_progress(user_id,target_type,target_id,next_review_at,review_count,lapse_count,mastery,stability,difficulty,evidence_item_ids)
                VALUES (?,'FLASHCARD',?,?,8,2,0.6,12,4,ARRAY[101,202]::bigint[])
                """, user, card, Timestamp.from(clock.now.plusSeconds(3600)));
        String schedule = progressSnapshot(card);
        Instant created = clock.now;
        clock.now = clock.now.plusSeconds(10);
        mvc.perform(patch(cardsPath() + "/" + card).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"front\":\"Mặt đã sửa\",\"lexemeId\":{\"ignored\":true},\"orderIndex\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.front").value("Mặt đã sửa"))
                .andExpect(jsonPath("$.back").value("pinyin — Nghĩa gốc")).andExpect(jsonPath("$.lexemeId").value(lexeme))
                .andExpect(jsonPath("$.reviewCount").value(8)).andExpect(jsonPath("$.lapseCount").value(2));
        mvc.perform(patch(cardsPath() + "/" + card).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"back\":\"Mặt sau đã sửa\",\"lexemeId\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.front").value("Mặt đã sửa"))
                .andExpect(jsonPath("$.back").value("Mặt sau đã sửa")).andExpect(jsonPath("$.lexemeId").value(lexeme));
        assertThat(progressSnapshot(card)).isEqualTo(schedule);
        assertThat(jdbc.queryForObject("SELECT order_index FROM learning.collection_items WHERE id=?", Integer.class, card)).isEqualTo(37);
        assertThat(jdbc.queryForObject("SELECT created_at FROM learning.collection_items WHERE id=?", Timestamp.class, card).toInstant()).isEqualTo(created);
        assertThat(jdbc.queryForObject("SELECT updated_at FROM learning.collection_items WHERE id=?", Timestamp.class, card).toInstant()).isEqualTo(clock.now);
        mvc.perform(patch(cardsPath() + "/" + card).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"front\":\"Mặt đã sửa\"}"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"lexemeId\":42}", "{\"front\":null}", "{\"back\":null}", "{\"front\":\"\"}", "{\"back\":\"\"}",
            "{\"front\":7}", "{\"back\":[]}", "{\"front\":\" \"}", "{\"front\":\"Mới\",\"back\":null}"})
    void FR016_invalidPatchKeepsBothFaces(String body) throws Exception {
        long card = add(Map.of("front", "Gốc", "back", "Mặt sau gốc")).path("id").asLong();
        perform(patch(cardsPath() + "/" + card), body, 422, "VALIDATION_FAILED");
        assertThat(jdbc.queryForObject("SELECT title||'/'||content FROM learning.collection_items WHERE id=?", String.class, card)).isEqualTo("Gốc/Mặt sau gốc");
    }

    @Test
    void FR016_AT15_duplicatePatchExcludesSelfAndDoesNotWriteRejectedChanges() throws Exception {
        long first = add(Map.of("front", "Một hai", "back", "Sau một")).path("id").asLong();
        long second = add(Map.of("front", "Ba bốn", "back", "Sau hai")).path("id").asLong();
        perform(patch(cardsPath() + "/" + second), "{\"front\":\" Một\\t hai \",\"back\":\"Không được ghi\"}", 409, "CARD_DUPLICATE");
        assertThat(jdbc.queryForObject("SELECT content FROM learning.collection_items WHERE id=?", String.class, second)).isEqualTo("Sau hai");
        mvc.perform(patch(cardsPath() + "/" + first).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"front\":\" Một\\t hai \"}"))
                .andExpect(status().isOk());
        mvc.perform(delete(cardsPath() + "/" + first).header("Authorization", bearer)).andExpect(status().isNoContent());
        mvc.perform(patch(cardsPath() + "/" + second).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"front\":\"Một hai\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void FR016_AT15_deleteOnlyMarksCardAndPreservesV6Schedule() throws Exception {
        long card = add(Map.of("front", "Thẻ xoá", "back", "Mặt sau" )).path("id").asLong();
        jdbc.update("INSERT INTO learning.user_progress(user_id,target_type,target_id,next_review_at,review_count,evidence_item_ids) VALUES (?,'FLASHCARD',?,?,4,ARRAY[11]::bigint[])", user, card, Timestamp.from(clock.now));
        String before = cardSnapshotWithoutDeletedAt(card);
        String schedule = progressSnapshot(card);
        clock.now = clock.now.plusSeconds(20);
        mvc.perform(delete(cardsPath() + "/" + card).header("Authorization", bearer)).andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(cardSnapshotWithoutDeletedAt(card)).isEqualTo(before);
        assertThat(progressSnapshot(card)).isEqualTo(schedule);
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM learning.collection_items WHERE id=?", Timestamp.class, card).toInstant()).isEqualTo(clock.now);
        mvc.perform(get("/api/flashcard-decks/{id}", deck).header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cards").isEmpty()).andExpect(jsonPath("$.cardCount").value(0)).andExpect(jsonPath("$.dueCount").value(0));
        perform(delete(cardsPath() + "/" + card), "", 403, "COLLECTION_NOT_OWNED");
        perform(patch(cardsPath() + "/" + card), "{}", 403, "COLLECTION_NOT_OWNED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"bad", "0", "-1", "9223372036854775808", "9223372036854775807"})
    void FR015_FR016_invalidOrMissingDeckAndCardIdsReturnSame403(String id) throws Exception {
        perform(post("/api/flashcard-decks/{id}/cards", id), "{}", 403, "COLLECTION_NOT_OWNED");
        for (var request : List.of(patch("/api/flashcard-decks/{id}/cards/1", id), delete("/api/flashcard-decks/{id}/cards/1", id),
                patch("/api/flashcard-decks/{id}/cards/{cardId}", deck, id), delete("/api/flashcard-decks/{id}/cards/{cardId}", deck, id))) {
            perform(request, "{}", 403, "COLLECTION_NOT_OWNED");
        }
    }

    @Test
    void FR015_FR016_foreignWrongKindWrongDeckAndDeletedParentsAre403BeforeData() throws Exception {
        long ownCard = add(Map.of("front", "Riêng tư", "back", "Mặt sau")).path("id").asLong();
        long otherDeck = deck(user(), "FLASHCARD_DECK");
        long notebook = deck(user, "NOTEBOOK");
        long sameUserOtherDeck = deck(user, "FLASHCARD_DECK");
        for (long wrong : new long[] {otherDeck, notebook, sameUserOtherDeck}) {
            if (wrong != sameUserOtherDeck) perform(post("/api/flashcard-decks/{id}/cards", wrong), "{}", 403, "COLLECTION_NOT_OWNED");
            perform(patch("/api/flashcard-decks/{id}/cards/{cardId}", wrong, ownCard), "{}", 403, "COLLECTION_NOT_OWNED");
            perform(delete("/api/flashcard-decks/{id}/cards/{cardId}", wrong, ownCard), "", 403, "COLLECTION_NOT_OWNED");
        }
        mvc.perform(patch(cardsPath() + "/" + ownCard).header("Authorization", bearer(user()))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("COLLECTION_NOT_OWNED"));
        jdbc.update("UPDATE learning.collections SET deleted_at=now() WHERE id=?", deck);
        perform(post(cardsPath()), "{}", 403, "COLLECTION_NOT_OWNED");
        perform(patch(cardsPath() + "/" + ownCard), "{}", 403, "COLLECTION_NOT_OWNED");
        perform(delete(cardsPath() + "/" + ownCard), "", 403, "COLLECTION_NOT_OWNED");
    }

    private JsonNode add(Map<String, ?> body) throws Exception {
        return mapper.readTree(mvc.perform(post(cardsPath()).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray());
    }

    private void duplicate(Map<String, ?> body) throws Exception { perform(post(cardsPath()), mapper.writeValueAsString(body), 409, "CARD_DUPLICATE"); }

    private void perform(MockHttpServletRequestBuilder request, String body, int status, String code) throws Exception {
        var result = mvc.perform(request.header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(status)).andExpect(jsonPath("$.error_code").value(code)).andExpect(jsonPath("$.request_id").isNotEmpty());
        if (code.equals("CARD_DUPLICATE")) result.andExpect(jsonPath("$.details").doesNotExist());
    }

    private String cardsPath() { return "/api/flashcard-decks/" + deck + "/cards"; }
    private int count() { return jdbc.queryForObject("SELECT count(*) FROM learning.collection_items WHERE collection_id=? AND deleted_at IS NULL", Integer.class, deck); }
    private long user() { return jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,'fixture-hash-unused','Người thử thẻ') RETURNING id", Long.class, UUID.randomUUID() + "@example.test"); }
    private long deck(long owner, String kind) { return jdbc.queryForObject("INSERT INTO learning.collections(user_id,kind,name) VALUES (?,?,'Bộ tự soạn') RETURNING id", Long.class, owner, kind); }
    private long lexeme(String front, String pinyin, String meaning) { return jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,pinyin,meaning_vi) VALUES ('WORD',?,?,?) RETURNING id", Long.class, front, pinyin, meaning); }
    private String bearer(long owner) { return "Bearer " + jwt.issueAccessToken(owner, List.of("LEARNER"), false, UUID.randomUUID()); }
    private String progressSnapshot(long card) {
        // PostgreSQL converts V6 evidence arrays to JSON values; no PgArray identity comparison.
        return jdbc.queryForObject("SELECT row_to_json(p)::text FROM learning.user_progress p WHERE user_id=? AND target_type='FLASHCARD' AND target_id=?", String.class, user, card);
    }
    private String cardSnapshotWithoutDeletedAt(long card) { return jdbc.queryForObject("SELECT (to_jsonb(i)-'deleted_at')::text FROM learning.collection_items i WHERE id=?", String.class, card); }

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
