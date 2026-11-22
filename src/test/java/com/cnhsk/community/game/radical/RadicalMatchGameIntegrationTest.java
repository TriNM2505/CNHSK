package com.cnhsk.community.game.radical;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
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
    @Autowired private StringRedisTemplate redis;

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
        seedCharacters(15, " 氵 ", "冫", "木", "日", "口");

        var started = read(startRound().andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameCode").value("RADICAL_MATCH"))
                .andExpect(jsonPath("$.pointsPerCorrect").value(10))
                .andExpect(jsonPath("$.timeLimitSeconds").value(600))
                .andExpect(jsonPath("$.uiConfig.maxLives").value(3)));
        long roundId = started.path("roundId").asLong();
        var resumed = read(mvc.perform(get(BASE + "rounds/{id}", roundId)
                .header("Authorization", bearer)).andExpect(status().isOk()));

        var items = started.path("items");
        assertThat(items).hasSize(15).isEqualTo(resumed.path("items"));
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
    void AT11_enoughCharactersWithOnlyThreeRadicalsReturnUnavailable() throws Exception {
        seedCharacters(15, " 氵 ", "氵", "木", "日");
        startRound().andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("GAME_UNAVAILABLE"));
    }

    @Test
    void theRoundStoresItsLevelAndParallelWrongAnswersCannotSpendMoreThanThreeLives() throws Exception {
        seedCharacters(15, "氵", "冫", "木", "日", "口");
        var started = read(startRound().andExpect(status().isCreated()));
        long roundId = started.path("roundId").asLong();
        assertThat(jdbc.queryForObject("SELECT hsk_level FROM learning.attempts WHERE id=?", Integer.class, roundId)).isEqualTo(3);

        int players = 8;
        var executor = Executors.newFixedThreadPool(players);
        var go = new CountDownLatch(1);
        List<Integer> statuses = new ArrayList<>();
        try {
            List<Future<Integer>> answers = new ArrayList<>();
            for (int index = 0; index < players; index++) {
                var item = started.path("items").get(index);
                answers.add(executor.submit(() -> {
                    if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start gate timed out.");
                    return answer(roundId, item.path("no").asInt(), wrongKey(item)).andReturn().getResponse().getStatus();
                }));
            }
            go.countDown();
            for (var answer : answers) statuses.add(answer.get(30, TimeUnit.SECONDS));
        } finally {
            go.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(statuses).filteredOn(status -> status == 200).hasSize(3);
        assertThat(statuses).filteredOn(status -> status == 422).hasSize(players - 3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.attempt_items WHERE attempt_id=?", Integer.class, roundId))
                .isEqualTo(3);
    }

    @Test
    void theHourlyPracticeAnswerLimitNeverStopsARoundMidway() throws Exception {
        seedCharacters(15, "氵", "冫", "木", "日", "口");
        var started = read(startRound().andExpect(status().isCreated()));
        var hour = DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZoneOffset.UTC);
        // Both hours, so the test also holds across an hour boundary.
        var keys = List.of("practice:ans:" + userId + ":" + hour.format(Instant.now()),
                "practice:ans:" + userId + ":" + hour.format(Instant.now().plusSeconds(3600)));
        try {
            keys.forEach(key -> redis.opsForValue().set(key, "600", Duration.ofMinutes(5)));
            var item = started.path("items").get(0);
            answer(started.path("roundId").asLong(), item.path("no").asInt(), wrongKey(item)).andExpect(status().isOk());
            assertThat(redis.opsForValue().multiGet(keys)).containsExactly("600", "600");
        } finally {
            redis.delete(keys);
        }
    }

    private String wrongKey(JsonNode item) {
        String radical = radicals.get(item.path("lexemeId").asLong());
        for (JsonNode option : item.path("options")) {
            if (!radical.equals(option.path("text").asText())) return option.path("key").asText();
        }
        throw new IllegalStateException("Fixture has no wrong option.");
    }

    private ResultActions answer(long roundId, int no, String key) throws Exception {
        return mvc.perform(post(BASE + "rounds/{id}/answers", roundId).header("Authorization", bearer)
                .contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("no", no, "answer", key, "durationMs", 3000))));
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
