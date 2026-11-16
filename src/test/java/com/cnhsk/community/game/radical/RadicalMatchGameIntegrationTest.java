package com.cnhsk.community.game.radical;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
class RadicalMatchGameIntegrationTest extends AbstractIntegrationTest {
    private static final String BASE = "/api/community/games/";
    private static final String HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;

    private final Map<Long, String> radicals = new LinkedHashMap<>();
    private long userId;
    private String bearer;

    @BeforeEach
    void createLearner() {
        userId = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) "
                + "VALUES (?,?,?) RETURNING id", Long.class, UUID.randomUUID() + "@example.test", HASH, "Ghép Bộ thủ test");
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
        for (long id : radicals.keySet()) {
            jdbc.update("DELETE FROM learning.lexemes WHERE id=?", id);
        }
    }

    @Test
    void AT09_AT10_roundExposesFourDistinctChoicesAndPreferLookalikes() throws Exception {
        seedCharacters(8, " 氵 ", "冫", "木", "日", "口");

        var started = read(startRound().andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameCode").value("RADICAL_MATCH"))
                .andExpect(jsonPath("$.pointsPerCorrect").value(10))
                .andExpect(jsonPath("$.timeLimitSeconds").value(90))
                .andExpect(jsonPath("$.uiConfig").isEmpty()));
        long roundId = started.path("roundId").asLong();
        var resumed = read(mvc.perform(get(BASE + "rounds/{id}", roundId)
                .header("Authorization", bearer)).andExpect(status().isOk()));

        var items = started.path("items");
        assertThat(items).hasSize(8).isEqualTo(resumed.path("items"));
        assertThat(items).allSatisfy(item -> {
            var radical = radicals.get(item.path("lexemeId").asLong());
            assertThat(radical).isNotNull();
            assertThat(item.path("type").asText()).isEqualTo("RADICAL_CHOICE");
            assertThat(item.path("skill").asText()).isEqualTo("CHARACTER");
            assertThat(item.path("prompt").path("hint").asText()).isEqualTo("chữ mẫu");
            assertThat(item.path("rules").path("minDurationMs").asInt()).isEqualTo(700);
            assertThat(item.toString()).doesNotContain("\"answer\"", "\"accept\"", "\"correctAnswer\"");
            List<String> labels = new ArrayList<>();
            item.path("options").forEach(option -> labels.add(option.path("text").asText()));
            assertThat(labels).hasSize(4).doesNotHaveDuplicates().contains(radical);
            if (radical.equals("氵")) assertThat(labels).contains("冫");
            if (radical.equals("冫")) assertThat(labels).contains("氵");
        });
    }

    @Test
    void AT11_tenCharactersWithOnlyThreeRadicalsReturnUnavailable() throws Exception {
        seedCharacters(10, " 氵 ", "氵", "木", "日");
        startRound().andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("GAME_UNAVAILABLE"));
    }

    private void seedCharacters(int count, String... pool) {
        for (int index = 0; index < count; index++) {
            String radical = pool[index % pool.length];
            long id = jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,meaning_vi,hsk_level,radical) "
                            + "VALUES ('CHAR',?,'chữ mẫu',3,?) RETURNING id",
                    Long.class, "字" + UUID.randomUUID(), radical);
            radicals.put(id, radical.strip());
        }
    }

    private ResultActions startRound() throws Exception {
        return mvc.perform(post(BASE + "RADICAL_MATCH/rounds").header("Authorization", bearer)
                .contentType("application/json").content("{\"hskLevel\":3}"));
    }

    private JsonNode read(ResultActions response) throws Exception {
        return mapper.readTree(response.andReturn().getResponse().getContentAsString());
    }
}
