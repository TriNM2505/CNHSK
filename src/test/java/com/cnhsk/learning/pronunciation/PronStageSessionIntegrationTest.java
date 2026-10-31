package com.cnhsk.learning.pronunciation;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import com.cnhsk.learning.content.entity.PronStage;
import com.cnhsk.learning.content.entity.PronUnit;
import com.cnhsk.learning.content.repository.PronStageRepository;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.Skill;
import com.cnhsk.shared.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.parser.OpenAPIV3Parser;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.packages-to-scan=com.cnhsk.learning.pronunciation.web"
})
class PronStageSessionIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-30T05:00:00Z");
    private static final String PATH = "/api/pronunciation/stages/{stageIndex}/sessions";

    // HTTP answer transactions commit independently; keep their fixtures isolated from other specs.
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:18-alpine")).withDatabaseName("pronunciation_test")
            .withUsername("pronunciation_test_owner").withPassword(UUID.randomUUID().toString());
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
    private static final GenericContainer<?> MAILPIT = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.27"))
            .withExposedPorts(1025, 8025);

    static {
        POSTGRES.start();
        REDIS.start();
        MAILPIT.start();
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
        registry.add("test.mailpit.base-url", () -> "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025));
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private PronStageRepository stages;
    @Autowired private PracticeService practice;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private JwtService jwt;
    @MockitoBean private Clock clock;
    private long owner;

    @BeforeEach
    void setup() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        jdbc.update("DELETE FROM learning.pron_stages WHERE stage_index BETWEEN 1 AND 8");
        owner = createUser();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "9", "99999999999999999999999999", "-99999999999999999999999999"})
    void FR006_AT04_outOfRangeIntegersReturn404(String index) throws Exception {
        assertError(start(owner, index), 404, "STAGE_NOT_FOUND");
        assertThat(sessionCount(owner)).isZero();
    }

    @Test
    void FR006_missingImportedStageReturns404() throws Exception {
        assertError(start(owner, "1"), 404, "STAGE_NOT_FOUND");
        assertThat(sessionCount(owner)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-number", "1.5", "NaN"})
    void FR006_nonIntegerStageIndexUsesTheValidationErrorContract(String index) throws Exception {
        var response = start(owner, index);
        assertError(response, 422, "VALIDATION_FAILED");
        assertThat(mapper.readTree(response.getContentAsString()).path("details").get(0).path("field").asText())
                .isEqualTo("stageIndex");
    }

    @Test
    void FR006_AT03_lockedStageReturnsRequiredPredecessorBeforeEmptyAudioOrResume() throws Exception {
        PronStage first = stage(1, units(1));
        progress(owner, first, "100.00");
        PronStage fifth = stage(5, List.of());
        var served = new ServedItem(1, ItemType.PRON_CHOICE, Skill.PRONUNCIATION, null,
                new ServedItem.Prompt("Đơn vị đã phát", null, null, null, "https://example.test/old.mp3", null, null, null, null),
                List.of(new ServedItem.Option("A", "z"), new ServedItem.Option("B", "zh")),
                "B", null, null, new ServedItem.Rules(500, 3, true));
        practice.start(new PracticeService.StartPracticeCommand(owner, PracticeSource.PRON_STAGE, fifth.getId(), null,
                List.of(served), null, null, null));

        var response = start(owner, "5");

        assertError(response, 403, "STAGE_LOCKED");
        assertThat(mapper.readTree(response.getContentAsString()).path("requiredStageIndex").asInt()).isEqualTo(4);
        assertThat(sessionCount(owner)).isEqualTo(1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void FR006_AT02_allUnitsWithoutAudioReturn422(String missingAudio) throws Exception {
        stage(1, List.of(unit(1, missingAudio)));
        assertError(start(owner, "1"), 422, "STAGE_HAS_NO_UNITS");
        assertThat(sessionCount(owner)).isZero();
    }

    @Test
    void FR006_AT02_emptyAudioIsCheckedBeforeResumingAnExistingSession() throws Exception {
        PronStage stage = stage(1, units(1));
        assertThat(start(owner, "1").getStatus()).isEqualTo(201);
        stage.setUnits(List.of());
        stages.saveAndFlush(stage);

        assertError(start(owner, "1"), 422, "STAGE_HAS_NO_UNITS");
        assertThat(sessionCount(owner)).isEqualTo(1);
    }

    @Test
    void FR008_AT06_newSessionUsesEveryPlayableUnitAndNeverExposesServerAnswers() throws Exception {
        PronStage stage = stage(1, units(10));

        var response = start(owner, "1");

        assertThat(response.getStatus()).isEqualTo(201);
        JsonNode session = mapper.readTree(response.getContentAsString());
        assertThat(session.path("source").asText()).isEqualTo("PRON_STAGE");
        assertThat(session.path("refId").asLong()).isEqualTo(stage.getId());
        assertThat(session.path("feedback").asText()).isEqualTo("IMMEDIATE");
        assertThat(session.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(Instant.parse(session.path("expiresAt").asText())).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(session.path("items")).hasSize(10);
        assertThat(session.path("items")).extracting(item -> item.path("prompt").path("text").asText())
                .containsExactlyInAnyOrderElementsOf(units(10).stream().map(PronUnit::prompt).toList());
        assertThat(session.path("answered")).isEmpty();
        for (JsonNode item : session.path("items")) {
            assertThat(item.path("type").asText()).isEqualTo("PRON_CHOICE");
            assertThat(item.path("skill").asText()).isEqualTo("PRONUNCIATION");
            assertThat(item.path("rules").path("hasAudio").asBoolean()).isTrue();
            assertThat(item.path("rules").path("maxReplays").asInt()).isEqualTo(3);
            assertThat(item.path("rules").path("minDurationMs").asInt()).isEqualTo(500);
            assertThat(item.path("options")).extracting(option -> option.path("key").asText())
                    .containsExactly("A", "B", "C", "D");
        }
        assertThat(session.path("items").toString()).doesNotContain("\"answer\"", "answerKey", "correctAnswer",
                "\"accept\"", "explanation");
        assertThat(session.path("meta").toString()).doesNotContain("answer", "accept");
        assertThat(response.getContentAsString()).doesNotContain("Giải thích nội bộ", "@example.test", "userId");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM learning.user_progress WHERE user_id = ?", Integer.class, owner))
                .isZero();
    }

    @Test
    void FR008_mixedAudioStageServesExactlyTheUnitCountReportedByTheList() throws Exception {
        stage(1, List.of(unit(1, null), unit(2, "https://example.test/2.mp3"), unit(3, " "),
                unit(4, "https://example.test/4.mp3"), unit(5, "")));
        JsonNode summary = mapper.readTree(mvc.perform(get("/api/pronunciation/stages")
                        .header("Authorization", bearer(owner, "LEARNER"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get(0);

        JsonNode session = mapper.readTree(start(owner, "1").getContentAsString());

        assertThat(summary.path("unitCount").asInt()).isEqualTo(2);
        assertThat(session.path("items")).hasSize(summary.path("unitCount").asInt());
        assertThat(session.path("items")).extracting(item -> item.path("prompt").path("audioUrl").asText())
                .containsExactlyInAnyOrder("https://example.test/2.mp3", "https://example.test/4.mp3");
    }

    @Test
    void FR007_AT05_resumingPreservesTheSnapshotAndFourGradedAnswersAfterContentChanges() throws Exception {
        PronStage stage = stage(1, units(10));
        JsonNode original = mapper.readTree(start(owner, "1").getContentAsString());
        long id = original.path("sessionId").asLong();
        for (int index = 0; index < 4; index++) {
            JsonNode item = original.path("items").get(index);
            JsonNode result = answer(owner, id, Map.of("no", item.path("no").asInt(),
                    "answer", correctKey(item), "durationMs", 1000, "replayCount", 1));
            assertThat(result.path("correct").asBoolean()).isTrue();
        }
        stage.setUnits(List.of(unit(99, "https://example.test/reimported.mp3")));
        stages.saveAndFlush(stage);

        var response = start(owner, "1");
        JsonNode restored = mapper.readTree(response.getContentAsString());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(restored.path("sessionId").asLong()).isEqualTo(id);
        assertThat(restored.path("items")).isEqualTo(original.path("items"));
        assertThat(restored.path("expiresAt")).isEqualTo(original.path("expiresAt"));
        assertThat(restored.path("answered")).hasSize(4);
        for (JsonNode recorded : restored.path("answered")) {
            assertThat(recorded.path("correct").asBoolean()).isTrue();
            assertThat(recorded.path("answer").asText()).isEqualTo(recorded.path("correctAnswer").asText());
        }
        assertThat(restored.path("items").toString()).doesNotContain("\"answer\"", "correctAnswer", "\"accept\"");
        assertThat(sessionCount(owner)).isEqualTo(1);
    }

    @Test
    void FR007_activeSessionsAreIsolatedByUserAndStage() throws Exception {
        stage(1, units(1));
        PronStage second = stage(2, units(1));
        progress(owner, second, null);
        JsonNode first = mapper.readTree(start(owner, "1").getContentAsString());
        long other = createUser();
        JsonNode otherSession = mapper.readTree(start(other, "1").getContentAsString());
        JsonNode otherStage = mapper.readTree(start(owner, "2").getContentAsString());

        JsonNode resumed = mapper.readTree(start(owner, "1").getContentAsString());

        assertThat(resumed.path("sessionId")).isEqualTo(first.path("sessionId"));
        assertThat(resumed.path("sessionId")).isNotEqualTo(otherSession.path("sessionId"));
        assertThat(resumed.path("sessionId")).isNotEqualTo(otherStage.path("sessionId"));
        assertThat(sessionCount(owner)).isEqualTo(2);
        assertThat(sessionCount(other)).isEqualTo(1);
    }

    @Test
    void FR007_expiredSessionCreatesANewSessionWithoutLosingTheOldAnswers() throws Exception {
        stage(1, units(1));
        JsonNode original = mapper.readTree(start(owner, "1").getContentAsString());
        long id = original.path("sessionId").asLong();
        answer(owner, id, Map.of("no", 1, "answer", correctKey(original.path("items").get(0)), "durationMs", 1000));
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofDays(7)).plusSeconds(1));

        var response = start(owner, "1");
        JsonNode fresh = mapper.readTree(response.getContentAsString());

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(fresh.path("sessionId").asLong()).isNotEqualTo(id);
        assertThat(fresh.path("answered")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM learning.attempt_items WHERE attempt_id = ?", Integer.class, id))
                .isEqualTo(1);
        assertThat(sessionCount(owner)).isEqualTo(2);
    }

    @Test
    void FR009_AT07_skipIsRejectedAndTheSameItemCanStillBeAnsweredWithAudioFailed() throws Exception {
        stage(1, units(1));
        JsonNode session = mapper.readTree(start(owner, "1").getContentAsString());
        long id = session.path("sessionId").asLong();
        var skipped = mvc.perform(post("/api/practice/sessions/{id}/answers", id)
                        .header("Authorization", bearer(owner, "LEARNER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"no\":1,\"skipped\":true,\"durationMs\":1000}"))
                .andReturn().getResponse();
        assertError(skipped, 400, "SKIP_NOT_ALLOWED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM learning.attempt_items WHERE attempt_id = ?", Integer.class, id))
                .isZero();

        JsonNode accepted = answer(owner, id, Map.of("no", 1, "audioFailed", true, "durationMs", 1000));

        assertThat(accepted.path("counted").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT is_correct FROM learning.attempt_items WHERE attempt_id = ?", Boolean.class, id))
                .isNull();
        JsonNode restored = mapper.readTree(start(owner, "1").getContentAsString());
        assertThat(restored.path("answered")).hasSize(1);
    }

    @Test
    void FR009_pronunciationUsesImmediateServerGradingAndTheThreePlayLimit() throws Exception {
        stage(1, units(1));
        JsonNode session = mapper.readTree(start(owner, "1").getContentAsString());
        long id = session.path("sessionId").asLong();
        JsonNode item = session.path("items").get(0);
        String correct = correctKey(item);
        var denied = mvc.perform(post("/api/practice/sessions/{id}/answers", id)
                        .header("Authorization", bearer(owner, "LEARNER")).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("no", 1, "answer", correct, "durationMs", 1000, "replayCount", 4))))
                .andReturn().getResponse();
        assertError(denied, 400, "TOO_MANY_REPLAYS");
        String wrong = item.path("options").get(0).path("key").asText().equals(correct) ? "B" : "A";

        JsonNode graded = answer(owner, id, Map.of("no", 1, "answer", wrong, "durationMs", 1000, "replayCount", 3));

        assertThat(graded.path("correct").asBoolean()).isFalse();
        assertThat(graded.path("correctAnswer").asText()).isEqualTo(correct);
        assertThat(graded.path("explanation").asText()).startsWith("Giải thích nội bộ");
    }

    @Test
    void FR006_postRequiresLearnerRoleAndCookieCsrfButBearerRequiresNoCsrf() throws Exception {
        stage(1, units(1));
        assertError(mvc.perform(post(PATH, 1)).andReturn().getResponse(), 401, "UNAUTHENTICATED");
        for (String role : List.of("ADMIN", "TEACHER", "MODERATOR", "CONTENT_EDITOR", "ACCOUNTANT")) {
            assertError(mvc.perform(post(PATH, 1).header("Authorization", bearer(owner, role)))
                    .andReturn().getResponse(), 403, "FORBIDDEN");
        }
        Cookie access = new Cookie("access_token", token(owner, "LEARNER"));
        assertError(mvc.perform(post(PATH, 1).cookie(access)).andReturn().getResponse(), 403, "CSRF_TOKEN_INVALID");
        Cookie csrf = mvc.perform(get("/api/auth/csrf").cookie(access)).andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        assertError(mvc.perform(post(PATH, 1).cookie(access, csrf).header("X-XSRF-TOKEN", "incorrect-token"))
                .andReturn().getResponse(), 403, "CSRF_TOKEN_INVALID");
        mvc.perform(post(PATH, 1).cookie(access, csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isCreated());
        assertThat(start(owner, "1").getStatus()).isEqualTo(200);
    }

    @Test
    void FR006_startRateLimitReturns429AndDoesNotInsertASession() throws Exception {
        stage(1, units(1));
        redis.opsForValue().set("practice:start:" + owner + ":2026103005", "120", Duration.ofHours(1));

        var response = start(owner, "1");

        assertError(response, 429, "RATE_LIMITED");
        assertThat(mapper.readTree(response.getContentAsString()).path("retryAfterSeconds").asInt()).isEqualTo(3600);
        assertThat(sessionCount(owner)).isZero();
    }

    @Test
    void FR007_resumingDoesNotConsumeTheNewSessionQuota() throws Exception {
        stage(1, units(1));
        assertThat(start(owner, "1").getStatus()).isEqualTo(201);
        String key = "practice:start:" + owner + ":2026103005";
        redis.opsForValue().set(key, "120", Duration.ofHours(1));

        assertThat(start(owner, "1").getStatus()).isEqualTo(200);

        assertThat(redis.opsForValue().get(key)).isEqualTo("120");
        assertThat(sessionCount(owner)).isEqualTo(1);
    }

    @Test
    void FR006_runtimePostOperationMatchesTheApprovedContract() throws Exception {
        var parsed = new OpenAPIV3Parser().readLocation(
                "specs/005-pronunciation-stages/contracts/openapi-pronunciation.yaml", null, null);
        assertThat(parsed.getMessages()).isEmpty();
        var source = parsed.getOpenAPI().getPaths().get(PATH).getPost();
        JsonNode runtime = mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode operation = runtime.path("paths").path(PATH).path("post");

        assertThat(operation.path("operationId").asText()).isEqualTo(source.getOperationId());
        assertThat(operation.path("responses").fieldNames()).toIterable()
                .containsExactlyInAnyOrderElementsOf(source.getResponses().keySet());
        assertThat(operation.path("parameters").get(0).path("schema").path("type").asText()).isEqualTo("integer");
        for (String success : List.of("200", "201")) {
            assertThat(operation.path("responses").path(success).path("content").path(MediaType.APPLICATION_JSON_VALUE)
                    .path("schema").path("$ref").asText()).isEqualTo("#/components/schemas/SessionView");
        }
        JsonNode properties = runtime.path("components").path("schemas").path("ServedItemView").path("properties");
        assertThat(properties.has("answer")).isFalse();
        assertThat(properties.has("accept")).isFalse();
    }

    private PronStage stage(int index, List<PronUnit> units) {
        PronStage stage = new PronStage((short) index, "Tầng " + index);
        stage.setUnits(units);
        return stages.saveAndFlush(stage);
    }

    private List<PronUnit> units(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(no -> unit(no, "https://example.test/" + no + ".mp3")).toList();
    }

    private PronUnit unit(int no, String audio) {
        return new PronUnit("U" + no, audio, "Chọn âm " + no, List.of(new PronUnit.Option("A", "z" + no),
                new PronUnit.Option("B", "zh" + no), new PronUnit.Option("C", "s" + no),
                new PronUnit.Option("D", "sh" + no)), "C", "Giải thích nội bộ " + no);
    }

    private String correctKey(JsonNode item) {
        String correctText = "s" + item.path("prompt").path("text").asText().substring("Chọn âm ".length());
        for (JsonNode option : item.path("options")) {
            if (option.path("text").asText().equals(correctText)) {
                return option.path("key").asText();
            }
        }
        throw new AssertionError("Missing correct fixture option");
    }

    private void progress(long userId, PronStage stage, String percent) {
        jdbc.update("INSERT INTO learning.user_progress (user_id, target_type, target_id, percent, state) VALUES (?, ?, ?, ?, ?)",
                userId, "PRON_STAGE", stage.getId(), percent == null ? null : new BigDecimal(percent),
                percent == null ? "AVAILABLE" : "COMPLETED");
    }

    private int sessionCount(long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM learning.attempts WHERE user_id = ? AND ref_type = ?",
                Integer.class, userId, "PRON_STAGE");
    }

    private MockHttpServletResponse start(long userId, String index) throws Exception {
        return mvc.perform(post(PATH, index).header("Authorization", bearer(userId, "LEARNER")))
                .andReturn().getResponse();
    }

    private JsonNode answer(long userId, long sessionId, Map<String, Object> body) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/practice/sessions/{id}/answers", sessionId)
                        .header("Authorization", bearer(userId, "LEARNER")).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private void assertError(MockHttpServletResponse response, int status, String code) throws Exception {
        assertThat(response.getStatus()).isEqualTo(status);
        JsonNode error = mapper.readTree(response.getContentAsString());
        assertThat(error.path("error_code").asText()).isEqualTo(code);
        assertThat(error.path("message").asText()).isNotBlank();
        assertThat(error.path("request_id").asText()).isNotBlank();
        assertThat(error.toString()).doesNotContain("stackTrace", "\"trace\"", "@example.test", "password");
    }

    private String bearer(long userId, String role) {
        return "Bearer " + token(userId, role);
    }

    private String token(long userId, String role) {
        return jwt.issueAccessToken(userId, List.of(role), false, UUID.randomUUID());
    }

    private long createUser() throws Exception {
        String password = "Aa1" + UUID.randomUUID();
        return mapper.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.test",
                                "password", password, "confirmPassword", password,
                                "displayName", "Pronunciation session test", "acceptTerms", true))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("userId").asLong();
    }
}
