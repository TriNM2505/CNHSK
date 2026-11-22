package com.cnhsk.learning.leaderboard;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;

import com.cnhsk.learning.api.GameCode;
import com.cnhsk.learning.leaderboard.LeaderboardPeriods.Interval;
import com.cnhsk.shared.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class GameProductionIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("game_production_test").withUsername("game_test_owner")
            .withPassword(UUID.randomUUID().toString());

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void localServices(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        properties.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        properties.add("spring.flyway.user", POSTGRES::getUsername);
        properties.add("spring.flyway.password", POSTGRES::getPassword);
        properties.add("spring.data.redis.host", REDIS::getHost);
        properties.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
    private static final String BASE = "/api/community/games/";
    private static final String HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private static final Map<GameCode, RoundSpec> EXPECTED = Map.of(
            GameCode.CHAR_RAIN, new RoundSpec(60, 60, "WRITING"),
            GameCode.PINYIN_MATCH, new RoundSpec(600, 30, "VOCAB"),
            GameCode.RADICAL_MATCH, new RoundSpec(600, 15, "CHARACTER"),
            GameCode.CHAR_CATCH, new RoundSpec(60, 100, "LISTENING"),
            GameCode.BRUSH_STROKE, new RoundSpec(600, 80, "WRITING"),
            GameCode.FLYING_DART, new RoundSpec(60, 60, "CHARACTER"));

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private LeaderboardRepository repository;

    private final Map<Long, Sample> lexemes = new LinkedHashMap<>();
    private long userId;
    private String bearer;

    @BeforeEach
    void createLearner() {
        userId = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) "
                + "VALUES (?,?,?) RETURNING id", Long.class, UUID.randomUUID() + "@example.test", HASH, "Game test");
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
        for (long id : lexemes.keySet()) {
            jdbc.update("DELETE FROM learning.lexemes WHERE id=?", id);
        }
    }

    @Test
    void flyingDartHidesAcceptanceKeysAndGradesSinoVietnameseReadings() throws Exception {
        for (int index = 0; index < 20; index++) {
            addLexeme("CHAR", "xué", null, 8, index);
        }
        for (String input : List.of("học", "HOC", "hok", "xué")) {
            boolean correct = List.of("học", "HOC").contains(input);
            var started = start(GameCode.FLYING_DART);
            long id = started.path("roundId").asLong();
            var resumed = mapper.readTree(mvc.perform(get(BASE + "rounds/{id}", id).header("Authorization", bearer))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(started.path("items")).hasSize(20).isEqualTo(resumed.path("items"));
            for (JsonNode response : List.of(started, resumed)) {
                assertThat(response.toString()).doesNotContain("\"accept\"", "\"answer\"", "\"correctAnswer\"");
                assertThat(response.path("items")).allSatisfy(item -> {
                    assertThat(item.path("prompt").has("pinyin")).isFalse();
                    assertThat(item.path("rules").path("minDurationMs").asInt()).isEqualTo(500);
                });
            }
            answer(id, input).andExpect(jsonPath("$.correct").value(correct))
                    .andExpect(jsonPath("$.pointsAwarded").value(correct ? 10 : 0));
        }
    }

    @Test
    void AT23_AT24_allSixRegisteredGamesUseTheirSpecAndProduceRankableServerScores() throws Exception {
        seedFullPool();
        var leaderboardInterval = new Interval("all", null, null);
        for (GameCode code : GameCode.values()) {
            var spec = EXPECTED.get(code);
            var started = start(code);
            long id = started.path("roundId").asLong();
            assertThat(started.path("gameCode").asText()).isEqualTo(code.name());
            assertThat(started.path("timeLimitSeconds").asInt()).isEqualTo(spec.seconds());
            assertThat(started.path("pointsPerCorrect").asInt()).isEqualTo(10);
            assertThat(started.path("items")).hasSize(spec.itemCount()).allSatisfy(item ->
                    assertThat(item.path("skill").asText()).isEqualTo(spec.skill()));
            var item = started.path("items").get(0);
            answer(id, correctAnswer(code, item)).andExpect(jsonPath("$.correct").value(true));
            mvc.perform(post(BASE + "rounds/{id}/finish", id).header("Authorization", bearer))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.points").value(10))
                    .andExpect(jsonPath("$.maxPoints").value(spec.itemCount() * 10))
                    .andExpect(jsonPath("$.skill").value(spec.skill()))
                    .andExpect(jsonPath("$.suspicious").value(false));

            var persisted = jdbc.queryForMap("SELECT kind,game_code,status,score,suspicious "
                    + "FROM learning.attempts WHERE id=?", id);
            assertThat(persisted).containsEntry("kind", "GAME").containsEntry("game_code", code.name())
                    .containsEntry("status", "SUBMITTED").containsEntry("suspicious", false);
            assertThat(((Number) persisted.get("score")).longValue()).isEqualTo(1);
            var rank = repository.gameRanking(code, 3, leaderboardInterval, Set.of(), userId, 20).myEntry().orElseThrow();
            assertThat(rank.userId()).isEqualTo(userId);
            assertThat(rank.score()).isEqualTo(1);
        }
    }

    private void seedFullPool() {
        var first = List.of("bā", "bá", "bǎ", "bà", "mā");
        var second = List.of("ní", "shí", "bó", "tí");
        var third = List.of("mā", "dā", "lā", "rén");
        var radicals = List.of("氵", "冫", "木", "日", "口");
        for (int index = 0; index < 80; index++) {
            String pinyin = first.get(index % 5) + " " + second.get((index / 5) % 4) + " " + third.get(index / 20);
            addLexeme("CHAR", pinyin, radicals.get(index % radicals.size()), 4, index);
            addLexeme("WORD", pinyin, null, null, index + 80);
        }
    }

    private void addLexeme(String kind, String pinyin, String radical, Integer strokes, int index) {
        String text = ("WORD".equals(kind) ? "詞組" : "字") + UUID.randomUUID();
        long id = jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,pinyin,meaning_vi,hsk_level,radical,stroke_count,sino_viet) "
                        + "VALUES (?,?,?,?,3,?,?,?) RETURNING id", Long.class, kind, text, pinyin, "nghĩa tự soạn " + index,
                radical, strokes, "CHAR".equals(kind) ? "học" : null);
        lexemes.put(id, new Sample(text, pinyin, radical));
    }

    private String correctAnswer(GameCode code, JsonNode item) {
        var sample = lexemes.get(item.path("lexemeId").asLong());
        return switch (code) {
            case CHAR_RAIN, BRUSH_STROKE -> "PASS";
            case FLYING_DART -> "hoc";
            case PINYIN_MATCH -> optionKey(item, sample.pinyin());
            case RADICAL_MATCH -> optionKey(item, sample.radical());
            case CHAR_CATCH -> optionKey(item, sample.text());
        };
    }

    private static String optionKey(JsonNode item, String label) {
        return StreamSupport.stream(item.path("options").spliterator(), false)
                .filter(option -> label.equals(option.path("text").asText()))
                .map(option -> option.path("key").asText()).findFirst().orElseThrow();
    }

    private JsonNode start(GameCode code) throws Exception {
        return mapper.readTree(mvc.perform(post(BASE + "{code}/rounds", code.name()).header("Authorization", bearer)
                        .contentType("application/json").content("{\"hskLevel\":3}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private org.springframework.test.web.servlet.ResultActions answer(long id, String input) throws Exception {
        var body = mapper.writeValueAsString(Map.of("no", 1, "answer", input, "durationMs", 3000));
        return mvc.perform(post(BASE + "rounds/{id}/answers", id).header("Authorization", bearer)
                .contentType("application/json").content(body)).andExpect(status().isOk());
    }

    private record Sample(String text, String pinyin, String radical) { }
    private record RoundSpec(int seconds, int itemCount, String skill) { }
}
