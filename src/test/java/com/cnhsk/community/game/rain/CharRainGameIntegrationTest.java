package com.cnhsk.community.game.rain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cnhsk.community.game.GameContext;
import com.cnhsk.community.game.GameDefinition;
import com.cnhsk.learning.api.GameCode;
import com.cnhsk.learning.api.PracticeApi;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(CharRainGameIntegrationTest.Definitions.class)
class CharRainGameIntegrationTest extends AbstractIntegrationTest {
    private static final String BASE = "/api/community/games/";
    private static final String HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;

    private final Map<Long, Integer> strokesById = new LinkedHashMap<>();
    private long userId;
    private String bearer;

    @BeforeEach
    void createLearner() {
        userId = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) "
                + "VALUES (?,?,?) RETURNING id", Long.class, UUID.randomUUID() + "@example.test", HASH, "Mưa chữ test");
        bearer = "Bearer " + jwt.issueAccessToken(userId, List.of("LEARNER"), false, UUID.randomUUID());
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM learning.study_events WHERE user_id=?", userId);
        jdbc.update("DELETE FROM learning.user_progress WHERE user_id=?", userId);
        jdbc.update("DELETE FROM learning.attempt_items WHERE attempt_id IN "
                + "(SELECT id FROM learning.attempts WHERE user_id=?)", userId);
        jdbc.update("DELETE FROM learning.attempts WHERE user_id=?", userId);
        jdbc.update("DELETE FROM auth.users WHERE id=?", userId);
        for (long id : strokesById.keySet()) {
            jdbc.update("DELETE FROM learning.lexemes WHERE id=?", id);
        }
    }

    @Test
    void AT01_startAndResumeExposeTracePromptAndDurationWithoutAnswer() throws Exception {
        seedCharacters(30);
        var started = read(startRound().andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameCode").value("CHAR_RAIN"))
                .andExpect(jsonPath("$.timeLimitSeconds").value(180))
                .andExpect(jsonPath("$.pointsPerCorrect").value(10))
                .andExpect(jsonPath("$.uiConfig.requiredStrokes").value("ALL"))
                .andExpect(jsonPath("$.uiConfig.strokeOrder").value(true)));
        var resumed = read(mvc.perform(get(BASE + "rounds/{id}", started.path("roundId").asLong())
                .header("Authorization", bearer)).andExpect(status().isOk()));

        assertPublicItems(started.path("items"));
        assertPublicItems(resumed.path("items"));
    }

    @Test
    void AT02_fourCharactersCannotStartRound() throws Exception {
        seedCharacters(4);
        startRound().andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("GAME_UNAVAILABLE"));
    }

    @Test
    void AT03_rapidPassesScoreOneHundredWithoutReplacingExistingRecord() throws Exception {
        seedCharacters(30);
        long previous = read(startRound().andExpect(status().isCreated())).path("roundId").asLong();
        answer(previous, 1, 12_000);
        finish(previous).andExpect(jsonPath("$.points").value(10))
                .andExpect(jsonPath("$.newBest").value(true))
                .andExpect(jsonPath("$.bestPoints").value(10))
                .andExpect(jsonPath("$.suspicious").value(false));

        long rapid = read(startRound().andExpect(status().isCreated())).path("roundId").asLong();
        for (int no = 1; no <= 10; no++) {
            answer(rapid, no, 100);
        }
        finish(rapid).andExpect(jsonPath("$.points").value(100))
                .andExpect(jsonPath("$.maxPoints").value(100))
                .andExpect(jsonPath("$.correct").value(10))
                .andExpect(jsonPath("$.suspicious").value(true))
                .andExpect(jsonPath("$.newBest").value(false))
                .andExpect(jsonPath("$.bestPoints").value(10));
    }

    private void seedCharacters(int count) {
        for (int index = 1; index <= count; index++) {
            int strokes = 1 + index % 12;
            long id = jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,pinyin,pinyin_norm,meaning_vi,"
                            + "hsk_level,stroke_count) VALUES ('CHAR',?,'zì','zi4','chữ mẫu',3,?) RETURNING id",
                    Long.class, "字" + UUID.randomUUID(), strokes);
            strokesById.put(id, strokes);
        }
    }

    private ResultActions startRound() throws Exception {
        return mvc.perform(post(BASE + "CHAR_RAIN/rounds").header("Authorization", bearer)
                .contentType("application/json").content("{\"hskLevel\":3}"));
    }

    private void answer(long roundId, int no, int duration) throws Exception {
        var body = mapper.writeValueAsString(Map.of("no", no, "answer", "PASS", "durationMs", duration));
        mvc.perform(post(BASE + "rounds/{id}/answers", roundId).header("Authorization", bearer)
                .contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.correct").value(true));
    }

    private ResultActions finish(long roundId) throws Exception {
        return mvc.perform(post(BASE + "rounds/{id}/finish", roundId).header("Authorization", bearer))
                .andExpect(status().isOk());
    }

    private JsonNode read(ResultActions response) throws Exception {
        return mapper.readTree(response.andReturn().getResponse().getContentAsString());
    }

    private void assertPublicItems(JsonNode items) {
        assertThat(items).hasSize(10);
        assertThat(items).allSatisfy(item -> {
            long id = item.path("lexemeId").asLong();
            assertThat(strokesById).containsKey(id);
            assertThat(item.path("type").asText()).isEqualTo("TRACE_CHAR");
            assertThat(item.path("skill").asText()).isEqualTo("WRITING");
            assertThat(item.path("rules").path("minDurationMs").asInt()).isEqualTo(strokesById.get(id) * 300);
            assertThat(item.path("options").isMissingNode() || item.path("options").isNull()).isTrue();
            assertThat(item.toString()).doesNotContain("\"answer\"", "\"accept\"", "\"correctAnswer\"", "\"PASS\"");
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Definitions {
        @Bean GameDefinition charRain() { return new CharRainGame(); }
        @Bean GameDefinition pinyinMatch() { return new UnavailableDefinition(GameCode.PINYIN_MATCH); }
        @Bean GameDefinition radicalMatch() { return new UnavailableDefinition(GameCode.RADICAL_MATCH); }
        @Bean GameDefinition charCatch() { return new UnavailableDefinition(GameCode.CHAR_CATCH); }
        @Bean GameDefinition brushStroke() { return new UnavailableDefinition(GameCode.BRUSH_STROKE); }
        @Bean GameDefinition flyingDart() { return new UnavailableDefinition(GameCode.FLYING_DART); }
    }

    private record UnavailableDefinition(GameCode code) implements GameDefinition {
        @Override public String description() { return "Game giả dùng cho test."; }
        @Override public PracticeApi.Skill skill() { return PracticeApi.Skill.VOCAB; }
        @Override public String howToPlay() { return "Game giả dùng cho test."; }
        @Override public int timeLimitSeconds() { return 30; }
        @Override public int pointsPerCorrect() { return 10; }
        @Override public boolean isAvailable(GameContext context) { return false; }
        @Override public List<PracticeApi.ServedItem> buildRound(GameContext context) { return List.of(); }
        @Override public void validateAnswer(PracticeApi.ServedItem item, String answer, int elapsedMs) { }
        @Override public Map<String, Object> uiConfig() { return Map.of(); }
    }
}
