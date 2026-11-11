package com.cnhsk.learning.review;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class ReviewControllerIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-11T08:00:00Z");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @MockitoBean Clock clock;
    private long learner;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        learner = user();
    }

    @Test
    void reviewRoutesRequireLearnerAndUseCallerOwnedSchedules() throws Exception {
        mvc.perform(get("/api/reviews/summary")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/reviews/sessions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reviews/summary").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/reviews/sessions").header("Authorization", bearer("TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/reviews/summary").header("Authorization", bearer("LEARNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dueCount").value(0))
                .andExpect(jsonPath("$.nextDueAt").value(nullValue()));
        mvc.perform(post("/api/reviews/sessions").header("Authorization", bearer("LEARNER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.session").isEmpty())
                .andExpect(jsonPath("$.reason").value("NOTHING_DUE"));
    }

    @Test
    void dueReviewSessionUsesContractStatusesAndResumesTheSameSession() throws Exception {
        long lexeme = jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,meaning_vi) "
                + "VALUES ('GRAMMAR',?, 'fixture grammar meaning') RETURNING id", Long.class, "grammar-" + UUID.randomUUID());
        jdbc.update("INSERT INTO learning.user_progress(user_id,target_type,target_id,stability,difficulty,"
                        + "review_count,lapse_count,last_seen_at,next_review_at) VALUES (?,'LEXEME',?,1,5,0,0,?,?)",
                learner, lexeme, java.sql.Timestamp.from(NOW.minusSeconds(86_400)),
                java.sql.Timestamp.from(NOW.minusSeconds(60)));

        var created = mvc.perform(post("/api/reviews/sessions").header("Authorization", bearer("LEARNER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.source").value("DUE_REVIEW"))
                .andExpect(jsonPath("$.items[0].type").value("FLASHCARD_RATING"))
                .andExpect(jsonPath("$.items[0].prompt.text").isNotEmpty())
                .andExpect(jsonPath("$.items[0].prompt.meaning").value("fixture grammar meaning"))
                .andReturn();
        long sessionId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(created.getResponse().getContentAsString()).path("sessionId").asLong();

        mvc.perform(post("/api/reviews/sessions").header("Authorization", bearer("LEARNER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ahead\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value(sessionId));
    }

    @Test
    void choicePromptDoesNotRevealAnswerInReviewSessionJson() throws Exception {
        String fixtureId = UUID.randomUUID().toString();
        String text = "review-choice-" + fixtureId;
        String meaning = "fixture target meaning " + fixtureId;
        long target = jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,pinyin,sino_viet,meaning_vi,"
                        + "audio_url,extra) VALUES ('WORD',?,?,?,?,?,?::jsonb) RETURNING id", Long.class,
                text, "fixture pinyin", "fixture sino viet", meaning, "https://fixture.invalid/audio.mp3",
                "{\"examples\":[{\"cn\":\"fixture example\",\"pinyin\":\"fixture\",\"vi\":\"fixture translation\"}]}");
        word("fixture-distractor-a-" + fixtureId, "fixture meaning a " + fixtureId);
        word("fixture-distractor-b-" + fixtureId, "fixture meaning b " + fixtureId);
        word("fixture-distractor-c-" + fixtureId, "fixture meaning c " + fixtureId);
        String charText = "复习字" + fixtureId.substring(0, 8);
        long charTarget = jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,pinyin,sino_viet,meaning_vi,"
                        + "audio_url,extra) VALUES ('CHAR',?,?,?,?,?,?::jsonb) RETURNING id", Long.class,
                charText, "fixture char pinyin", "fixture char sino", "fixture char meaning " + fixtureId,
                "https://fixture.invalid/char-audio.mp3",
                "{\"examples\":[{\"cn\":\"char fixture example\",\"pinyin\":\"fixture\",\"vi\":\"char translation\"}]}");
        lexeme("CHAR", "fixture-char-a-" + fixtureId, "fixture char meaning a " + fixtureId);
        lexeme("CHAR", "fixture-char-b-" + fixtureId, "fixture char meaning b " + fixtureId);
        lexeme("CHAR", "fixture-char-c-" + fixtureId, "fixture char meaning c " + fixtureId);
        jdbc.update("INSERT INTO learning.user_progress(user_id,target_type,target_id,stability,difficulty,"
                        + "review_count,lapse_count,last_seen_at,next_review_at) VALUES (?,'LEXEME',?,1,5,0,0,?,?)",
                learner, target, java.sql.Timestamp.from(NOW.minusSeconds(86_400)),
                java.sql.Timestamp.from(NOW.minusSeconds(60)));
        jdbc.update("INSERT INTO learning.user_progress(user_id,target_type,target_id,stability,difficulty,"
                        + "review_count,lapse_count,last_seen_at,next_review_at) VALUES (?,'LEXEME',?,1,5,0,0,?,?)",
                learner, charTarget, java.sql.Timestamp.from(NOW.minusSeconds(86_400)),
                java.sql.Timestamp.from(NOW.minusSeconds(30)));

        mvc.perform(post("/api/reviews/sessions").header("Authorization", bearer("LEARNER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].type").value("MEANING_CHOICE"))
                .andExpect(jsonPath("$.items[0].prompt.text").value(text))
                .andExpect(jsonPath("$.items[0].prompt.pinyin").doesNotExist())
                .andExpect(jsonPath("$.items[0].prompt.sinoViet").doesNotExist())
                .andExpect(jsonPath("$.items[0].prompt.meaning").doesNotExist())
                .andExpect(jsonPath("$.items[0].prompt.audioUrl").doesNotExist())
                .andExpect(jsonPath("$.items[0].prompt.examples.length()").value(0))
                .andExpect(jsonPath("$.items[1].type").value("CHAR_MEANING_CHOICE"))
                .andExpect(jsonPath("$.items[1].prompt.text").value(charText))
                .andExpect(jsonPath("$.items[1].prompt.pinyin").doesNotExist())
                .andExpect(jsonPath("$.items[1].prompt.sinoViet").doesNotExist())
                .andExpect(jsonPath("$.items[1].prompt.meaning").doesNotExist())
                .andExpect(jsonPath("$.items[1].prompt.audioUrl").doesNotExist())
                .andExpect(jsonPath("$.items[1].prompt.examples.length()").value(0));
    }

    private long word(String text, String meaning) {
        return lexeme("WORD", text, meaning);
    }

    private long lexeme(String kind, String text, String meaning) {
        return jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,meaning_vi) VALUES (?, ?, ?) RETURNING id",
                Long.class, kind, text, meaning);
    }

    private long user() {
        return jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,'T14 API fixture') RETURNING id",
                Long.class, "t14-api-" + UUID.randomUUID() + "@example.test",
                "$2a$12$fixtureTestHashForDatabaseOnly00000000000000000000000000");
    }

    private String bearer(String role) {
        return "Bearer " + jwt.issueAccessToken(learner, List.of(role), false, UUID.randomUUID());
    }
}
