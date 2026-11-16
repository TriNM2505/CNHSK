package com.cnhsk.community.game.catchgame;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
class CharCatchGameIntegrationTest extends AbstractIntegrationTest {
    private static final String BASE = "/api/community/games/";
    private static final String HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;

    private final Map<Long, String> lexemesById = new LinkedHashMap<>();
    private long userId;
    private String bearer;

    @BeforeEach
    void createLearner() {
        userId = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) "
                + "VALUES (?,?,?) RETURNING id", Long.class, UUID.randomUUID() + "@example.test", HASH, "Bắt Chữ test");
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
        for (long id : lexemesById.keySet()) {
            jdbc.update("DELETE FROM learning.lexemes WHERE id=?", id);
        }
    }

    @Test
    void AT12_missingAudioUsesMeaningInTheAudioSlot() throws Exception {
        seed(8, false);
        var response = read(startRound().andExpect(status().isCreated()));
        var items = response.path("items");
        assertThat(items).hasSize(8);
        assertPublicItems(items, false);
    }

    @Test
    void belowMinimumReturnsUnavailable() throws Exception {
        seed(7, true);
        startRound().andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("GAME_UNAVAILABLE"));
    }

    @Test
    void AT13_AT15_roundsHideAnswersAndTwentyCorrectAnswersScoreTwoHundred() throws Exception {
        seed(20, true);
        var started = read(startRound().andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameCode").value("CHAR_CATCH"))
                .andExpect(jsonPath("$.timeLimitSeconds").value(75))
                .andExpect(jsonPath("$.pointsPerCorrect").value(10))
                .andExpect(jsonPath("$.uiConfig.perItemMs").value(4000))
                .andExpect(jsonPath("$.uiConfig.gridColumns").value(3)));
        long roundId = started.path("roundId").asLong();
        var resumed = read(mvc.perform(get(BASE + "rounds/{id}", roundId)
                .header("Authorization", bearer)).andExpect(status().isOk()));
        var items = started.path("items");
        assertThat(items).hasSize(20).isEqualTo(resumed.path("items"));
        assertPublicItems(items, true);
        assertPublicItems(resumed.path("items"), true);
        for (var item : items) {
            String correctText = lexemesById.get(item.path("lexemeId").asLong());
            var options = item.path("options");
            String key = StreamSupport.stream(options.spliterator(), false)
                    .filter(option -> option.path("text").asText().equals(correctText))
                    .map(option -> option.path("key").asText()).findFirst().orElseThrow();
            String payload = mapper.writeValueAsString(
                    Map.of("no", item.path("no").asInt(), "answer", key, "durationMs", 1000));
            mvc.perform(post(BASE + "rounds/{id}/answers", roundId).header("Authorization", bearer)
                    .contentType("application/json").content(payload))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.correct").value(true));
        }
        mvc.perform(post(BASE + "rounds/{id}/finish", roundId).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(200))
                .andExpect(jsonPath("$.maxPoints").value(200))
                .andExpect(jsonPath("$.correct").value(20))
                .andExpect(jsonPath("$.suspicious").value(false));
    }

    private void seed(int count, boolean hasAudio) {
        var first = List.of("bā", "bá", "bǎ", "bà", "mā");
        var second = List.of("ba", "bá", "bǎ", "bà");
        for (int index = 0; index < count; index++) {
            String kind = index % 2 == 0 ? "CHAR" : "WORD";
            String text = "字" + UUID.randomUUID();
            String pinyin = first.get(index % first.size()) + " " + second.get(index / first.size());
            String audioUrl = hasAudio ? "https://audio.example.test/" + index : null;
            long id = jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,pinyin,meaning_vi,hsk_level,audio_url) "
                            + "VALUES (?,?,?,?,3,?) RETURNING id",
                    Long.class, kind, text, pinyin, "nghĩa mẫu " + index, audioUrl);
            lexemesById.put(id, text);
        }
    }

    private ResultActions startRound() throws Exception {
        return mvc.perform(post(BASE + "CHAR_CATCH/rounds").header("Authorization", bearer)
                .contentType("application/json").content("{\"hskLevel\":3}"));
    }

    private static void assertPublicItems(JsonNode items, boolean hasAudio) {
        for (var item : items) {
            int mode = (item.path("no").asInt() - 1) % 3;
            boolean audio = hasAudio && mode == 2;
            String displayedClue = mode == 1 ? "pinyin" : audio ? "audioUrl" : "hint";
            var prompt = item.path("prompt");
            assertThat(prompt.size()).isEqualTo(1);
            assertThat(prompt.path(displayedClue).asText()).isNotBlank();
            assertThat(prompt.has("text")).isFalse();
            assertThat(item.toString()).doesNotContain("\"answer\"", "\"accept\"", "\"correctAnswer\"");
            assertThat(item.path("options")).hasSize(6);
            assertThat(item.path("type").asText()).isEqualTo("CATCH_CHOICE");
            assertThat(item.path("skill").asText()).isEqualTo("LISTENING");
            var rules = item.path("rules");
            assertThat(rules.path("minDurationMs").asInt()).isEqualTo(300);
            assertThat(rules.path("hasAudio").asBoolean()).isEqualTo(audio);
            assertThat(rules.path("maxReplays").asInt()).isEqualTo(audio ? 1 : 0);
        }
    }

    private JsonNode read(ResultActions response) throws Exception {
        return mapper.readTree(response.andReturn().getResponse().getContentAsString());
    }
}
