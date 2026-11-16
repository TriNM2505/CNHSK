package com.cnhsk.community.game.pinyin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
@Import(PinyinMatchGameIntegrationTest.Definitions.class)
class PinyinMatchGameIntegrationTest extends AbstractIntegrationTest {
    private static final String BASE = "/api/community/games/";
    private static final String HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private static final List<String> PINYIN = List.of("bā", "bá", "bǎ", "bà", "mā", "má", "mǎ",
            "mà", "bō", "bó", "bǒ", "bò", "pō", "pó");

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;

    private final List<Long> lexemes = new ArrayList<>();
    private long userId;
    private String bearer;

    @BeforeEach
    void createLearner() {
        userId = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) "
                + "VALUES (?,?,?) RETURNING id", Long.class, UUID.randomUUID() + "@example.test", HASH, "Ghép Pinyin test");
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
        for (long id : lexemes) {
            jdbc.update("DELETE FROM learning.lexemes WHERE id=?", id);
        }
    }

    @Test
    void AT05_charactersOnlyAreNotEnoughToStart() throws Exception {
        for (int index = 0; index < 6; index++) {
            addLexeme("CHAR", PINYIN.get(index));
        }
        startRound().andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("GAME_UNAVAILABLE"));
    }

    @ParameterizedTest
    @CsvSource({"6,1", "14,2"})
    void AT07_AT08_startAndResumeUseTheSameBoardsWithoutLeakingKeys(int poolSize, int boardCount) throws Exception {
        for (int index = 0; index < poolSize; index++) {
            addLexeme("WORD", PINYIN.get(index) + " cí");
        }

        var started = read(startRound().andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameCode").value("PINYIN_MATCH"))
                .andExpect(jsonPath("$.timeLimitSeconds").value(90))
                .andExpect(jsonPath("$.pointsPerCorrect").value(10)));
        var expectedBoards = IntStream.range(0, boardCount)
                .mapToObj(board -> IntStream.rangeClosed(board * 6 + 1, board * 6 + 6).boxed().toList())
                .toList();
        var boards = started.path("uiConfig").path("boards");
        assertThat(boards).isEqualTo(mapper.valueToTree(expectedBoards));

        var items = started.path("items");
        assertThat(items).hasSize(boardCount * 6);
        for (int offset = 0; offset < items.size(); offset += 6) {
            var options = items.get(offset).path("options");
            assertThat(options).hasSize(6);
            for (int index = offset; index < offset + 6; index++) {
                var item = items.get(index);
                assertThat(item.path("no").asInt()).isEqualTo(index + 1);
                assertThat(item.path("type").asText()).isEqualTo("PINYIN_PAIR");
                assertThat(item.path("rules").path("minDurationMs").asInt()).isEqualTo(600);
                assertThat(item.path("options")).isEqualTo(options);
                assertThat(item.path("prompt").has("pinyin")).isFalse();
                assertThat(item.toString()).doesNotContain("\"answer\"", "\"accept\"", "\"correctAnswer\"");
            }
        }

        long roundId = started.path("roundId").asLong();
        var resumed = read(mvc.perform(get(BASE + "rounds/{id}", roundId)
                .header("Authorization", bearer)).andExpect(status().isOk()));
        assertThat(resumed.path("uiConfig").path("boards")).isEqualTo(boards);
        assertThat(resumed.path("items")).isEqualTo(items);

        answer(roundId).andExpect(status().isOk());
        answer(roundId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("ANSWER_ALREADY_SUBMITTED"));
    }

    private void addLexeme(String kind, String pinyin) {
        lexemes.add(jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,pinyin,meaning_vi,hsk_level) "
                        + "VALUES (?,?,?,'từ mẫu',3) RETURNING id",
                Long.class, kind, "詞組" + UUID.randomUUID(), pinyin));
    }

    private ResultActions startRound() throws Exception {
        return mvc.perform(post(BASE + "PINYIN_MATCH/rounds").header("Authorization", bearer)
                .contentType("application/json").content("{\"hskLevel\":3}"));
    }

    private ResultActions answer(long roundId) throws Exception {
        return mvc.perform(post(BASE + "rounds/{id}/answers", roundId).header("Authorization", bearer)
                .contentType("application/json").content("{\"no\":1,\"answer\":\"A\",\"durationMs\":700}"));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Definitions {
        @Bean GameDefinition charRain() { return new UnavailableDefinition(GameCode.CHAR_RAIN); }
        @Bean GameDefinition pinyinMatch() { return new PinyinMatchGame(); }
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
