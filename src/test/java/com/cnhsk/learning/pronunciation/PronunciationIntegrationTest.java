package com.cnhsk.learning.pronunciation;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cnhsk.learning.content.entity.PronStage;
import com.cnhsk.learning.content.entity.PronUnit;
import com.cnhsk.learning.content.repository.PronStageRepository;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.Skill;
import com.cnhsk.learning.practice.entity.AttemptItem;
import com.cnhsk.learning.practice.entity.AttemptItemRepository;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.parser.OpenAPIV3Parser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.packages-to-scan=com.cnhsk.learning.pronunciation.web"
})
@Transactional
class PronunciationIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-30T05:00:00Z");
    private static final String PATH = "/api/pronunciation/stages";

    @Autowired private PronStageRepository stages;
    @Autowired private PracticeService practice;
    @Autowired private AttemptRepository attempts;
    @Autowired private AttemptItemRepository items;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @MockitoBean private Clock clock;

    private long owner;

    @BeforeEach
    void setup() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        // Isolate reference-data fixtures; the test transaction restores existing stages afterwards.
        jdbc.update("DELETE FROM learning.pron_stages WHERE stage_index BETWEEN 1 AND 8");
        owner = createUser();
    }

    @Test
    void FR001_AT01_newLearnerReceivesAllEightStagesSortedAndOnlyStageOneIsAvailable() throws Exception {
        for (int index = 8; index >= 1; index--) {
            stage(index, List.of(unit("https://example.test/audio.mp3")));
        }

        JsonNode result = list(owner);

        assertThat(result).hasSize(8);
        for (int index = 0; index < 8; index++) {
            assertThat(result.get(index).path("stageIndex").asInt()).isEqualTo(index + 1);
            assertThat(result.get(index).path("state").asText()).isEqualTo(index == 0 ? "AVAILABLE" : "LOCKED");
            assertThat(result.get(index).path("bestAccuracy").isNull()).isTrue();
        }
    }

    @Test
    void FR001_emptyContentReturnsAnEmptyArrayAndDoesNotCreateProgress() throws Exception {
        assertThat(list(owner)).isEmpty();
        assertThat(progressCount(owner)).isZero();
    }

    @Test
    void FR001_responseContainsOnlySummaryFieldsAndNeverAnswersOrPersonalData() throws Exception {
        PronStage stage = stage(1, List.of(unit("https://example.test/audio.mp3")));
        long active = start(owner, PracticeSource.PRON_STAGE, stage);

        JsonNode summary = list(owner).get(0);

        assertThat(summary.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "id", "stageIndex", "name", "description", "targetSounds", "passPercent", "unitCount",
                "playable", "state", "bestAccuracy", "activeSessionId", "activeAnswered");
        assertThat(summary.path("id").asLong()).isEqualTo(stage.getId());
        assertThat(summary.path("name").asText()).isEqualTo("Tầng 1");
        assertThat(summary.path("description").asText()).isEqualTo("Bài luyện tự tạo");
        assertThat(summary.path("targetSounds").get(0).asText()).isEqualTo("z");
        assertThat(summary.path("passPercent").decimalValue()).isEqualByComparingTo("80.00");
        assertThat(summary.path("unitCount").asInt()).isEqualTo(1);
        assertThat(summary.path("playable").asBoolean()).isTrue();
        assertThat(summary.path("activeSessionId").asLong()).isEqualTo(active);
        assertThat(summary.path("activeAnswered").asInt()).isZero();
        assertThat(summary.toString()).doesNotContain("answer", "answerKey", "correctAnswer", "accept",
                "explanation", "Giải thích nội bộ", "audioUrl", "options", "@example.test", "userId");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void FR002_AT02_missingAudioMakesImportedUnitsUnplayableWithoutAnError(String audio) throws Exception {
        stage(1, List.of(unit(audio), unit(null)));

        JsonNode summary = list(owner).get(0);

        assertThat(summary.path("unitCount").asInt()).isZero();
        assertThat(summary.path("playable").asBoolean()).isFalse();
        assertThat(summary.path("state").asText()).isEqualTo("AVAILABLE");
    }

    @Test
    void FR002_AT02_emptyStageAndOnlyAudioBearingUnitsAreCountedIndependently() throws Exception {
        stage(1, List.of());
        stage(2, List.of(unit(null), unit("https://example.test/one.mp3"), unit(" "),
                unit("https://example.test/two.mp3")));

        JsonNode result = list(owner);

        assertThat(result.get(0).path("unitCount").asInt()).isZero();
        assertThat(result.get(0).path("playable").asBoolean()).isFalse();
        assertThat(result.get(1).path("unitCount").asInt()).isEqualTo(2);
        assertThat(result.get(1).path("playable").asBoolean()).isTrue();
        assertThat(result.get(1).path("state").asText()).isEqualTo("LOCKED");
    }

    @Test
    void FR003_FR004_exactConfiguredThresholdCompletesOnlyTheCurrentStageAndOpensItsSuccessor() throws Exception {
        PronStage first = stage(1, List.of());
        first.setPassPercent(new BigDecimal("82.50"));
        stages.saveAndFlush(first);
        stage(2, List.of());
        stage(3, List.of());
        progress(owner, first, "PRON_STAGE", "82.50", "AVAILABLE");

        JsonNode result = list(owner);

        assertThat(result.get(0).path("state").asText()).isEqualTo("COMPLETED");
        assertThat(result.get(0).path("bestAccuracy").decimalValue()).isEqualByComparingTo("82.50");
        assertThat(result.get(1).path("state").asText()).isEqualTo("AVAILABLE");
        assertThat(result.get(1).path("bestAccuracy").isNull()).isTrue();
        assertThat(result.get(2).path("state").asText()).isEqualTo("LOCKED");
        assertThat(progressCount(owner)).isEqualTo(1);
    }

    @Test
    void FR004_AT16_importingTheNextStageAfterPassingMakesItAvailableWithoutCreatingProgress() throws Exception {
        PronStage second = stage(2, List.of());
        progress(owner, second, "PRON_STAGE", "90.00", "COMPLETED");
        assertThat(list(owner)).hasSize(1);

        PronStage third = stage(3, List.of());
        JsonNode result = list(owner);

        assertThat(result).hasSize(2);
        assertThat(result.get(1).path("id").asLong()).isEqualTo(third.getId());
        assertThat(result.get(1).path("state").asText()).isEqualTo("AVAILABLE");
        assertThat(progressCount(owner)).isEqualTo(1);
    }

    @Test
    void FR004_AT17_increasingThePredecessorThresholdNeverRelocksAnExistingProgressRow() throws Exception {
        PronStage first = stage(1, List.of());
        PronStage second = stage(2, List.of());
        progress(owner, first, "PRON_STAGE", "90.00", "COMPLETED");
        progress(owner, second, "PRON_STAGE", null, "AVAILABLE");
        Map<String, Object> before = jdbc.queryForMap(
                "SELECT * FROM learning.user_progress WHERE user_id = ? AND target_type = ? AND target_id = ?",
                owner, "PRON_STAGE", second.getId());
        first.setPassPercent(new BigDecimal("95.00"));
        stages.saveAndFlush(first);

        JsonNode result = list(owner);

        assertThat(result.get(0).path("state").asText()).isEqualTo("AVAILABLE");
        assertThat(result.get(0).path("bestAccuracy").decimalValue()).isEqualByComparingTo("90.00");
        assertThat(result.get(1).path("state").asText()).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForMap(
                "SELECT * FROM learning.user_progress WHERE user_id = ? AND target_type = ? AND target_id = ?",
                owner, "PRON_STAGE", second.getId())).isEqualTo(before);
    }

    @Test
    void FR004_FR005_anotherUsersProgressAndOtherTargetTypesCannotUnlockStagesOrLeakAccuracy() throws Exception {
        PronStage first = stage(1, List.of());
        PronStage second = stage(2, List.of());
        long other = createUser();
        progress(other, first, "PRON_STAGE", "100.00", "COMPLETED");
        progress(other, second, "PRON_STAGE", null, "AVAILABLE");
        progress(owner, first, "TOPIC", "100.00", "COMPLETED");

        JsonNode result = list(owner);

        assertThat(result.get(0).path("bestAccuracy").isNull()).isTrue();
        assertThat(result.get(0).path("state").asText()).isEqualTo("AVAILABLE");
        assertThat(result.get(1).path("state").asText()).isEqualTo("LOCKED");
    }

    @Test
    void FR005_activeSessionUsesTheLatestUnexpiredSessionForEachStageAndTheCaller() throws Exception {
        PronStage first = stage(1, List.of());
        PronStage second = stage(2, List.of());
        start(owner, PracticeSource.PRON_STAGE, first);
        long active = start(owner, PracticeSource.PRON_STAGE, first);
        record(active, 1, true);
        record(active, 2, null);
        long secondActive = start(owner, PracticeSource.PRON_STAGE, second);
        record(secondActive, 1, false);
        start(createUser(), PracticeSource.PRON_STAGE, first);
        start(owner, PracticeSource.TOPIC_PRACTICE, first);
        long expiredId = start(owner, PracticeSource.PRON_STAGE, first);
        var expired = attempts.findById(expiredId).orElseThrow();
        expired.setExpiresAt(NOW.minusSeconds(1));
        attempts.saveAndFlush(expired);
        for (String terminal : List.of("SUBMITTED", "ABANDONED", "EXPIRED")) {
            long id = start(owner, PracticeSource.PRON_STAGE, first);
            var attempt = attempts.findById(id).orElseThrow();
            attempt.setStatus(terminal);
            if ("SUBMITTED".equals(terminal)) {
                attempt.setSubmittedAt(NOW);
            }
            attempts.saveAndFlush(attempt);
        }

        JsonNode result = list(owner);

        assertThat(result.get(0).path("activeSessionId").asLong()).isEqualTo(active);
        assertThat(result.get(0).path("activeAnswered").asInt()).isEqualTo(2);
        assertThat(result.get(1).path("activeSessionId").asLong()).isEqualTo(secondActive);
        assertThat(result.get(1).path("activeAnswered").asInt()).isEqualTo(1);
        assertThat(result.toString()).doesNotContain("correctAnswer", "answerKey", "Giải thích nội bộ");
    }

    @Test
    void FR005_absentActiveSessionReturnsBothFieldsAsNull() throws Exception {
        stage(1, List.of());

        JsonNode summary = list(owner).get(0);

        assertThat(summary.has("activeSessionId")).isTrue();
        assertThat(summary.path("activeSessionId").isNull()).isTrue();
        assertThat(summary.has("activeAnswered")).isTrue();
        assertThat(summary.path("activeAnswered").isNull()).isTrue();
    }

    @Test
    void FR001_onlyLearnersCanListStagesWithCookieOrBearerAndErrorsUseTheSharedContract() throws Exception {
        mvc.perform(get(PATH).header("X-Request-Id", "pron-unauthenticated"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.request_id").value("pron-unauthenticated"))
                .andExpect(header().string("X-Request-Id", "pron-unauthenticated"));
        for (String role : List.of("ADMIN", "TEACHER", "CONTENT_EDITOR", "MODERATOR", "ACCOUNTANT")) {
            mvc.perform(get(PATH).header("Authorization", bearer(owner, role)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.request_id").isNotEmpty());
        }
        list(owner);
        mvc.perform(get(PATH).cookie(new jakarta.servlet.http.Cookie("access_token", token(owner, "LEARNER"))))
                .andExpect(status().isOk());
    }

    @Test
    void FR001_runtimeGetOperationAndSummarySchemaMatchTheApprovedOpenApi() throws Exception {
        var parsed = new OpenAPIV3Parser().readLocation(
                "specs/005-pronunciation-stages/contracts/openapi-pronunciation.yaml", null, null);
        assertThat(parsed.getMessages()).isEmpty();
        var contract = parsed.getOpenAPI();
        JsonNode runtime = mapper.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode operation = runtime.path("paths").path(PATH).path("get");

        assertThat(operation.path("operationId").asText())
                .isEqualTo(contract.getPaths().get(PATH).getGet().getOperationId());
        assertThat(operation.path("responses").fieldNames()).toIterable()
                .containsExactlyInAnyOrderElementsOf(contract.getPaths().get(PATH).getGet().getResponses().keySet());
        assertThat(operation.path("responses").path("200").path("content").has(MediaType.APPLICATION_JSON_VALUE)).isTrue();
        for (String errorStatus : List.of("401", "403")) {
            assertThat(operation.path("responses").path(errorStatus).path("content")
                    .path(MediaType.APPLICATION_JSON_VALUE).path("schema").path("$ref").asText())
                    .isEqualTo("#/components/schemas/ErrorResponse");
        }
        JsonNode summary = runtime.path("components").path("schemas").path("StageSummary");
        var sourceSummary = contract.getComponents().getSchemas().get("StageSummary");
        assertThat(summary.path("properties").fieldNames()).toIterable()
                .containsExactlyInAnyOrderElementsOf(sourceSummary.getProperties().keySet());
        assertThat(summary.path("required")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrderElementsOf(sourceSummary.getRequired());
        assertThat(runtime.path("components").path("schemas").path("StageState").path("enum"))
                .extracting(JsonNode::asText).containsExactly("LOCKED", "AVAILABLE", "COMPLETED");
    }

    private PronStage stage(int index, List<PronUnit> units) {
        PronStage stage = new PronStage((short) index, "Tầng " + index);
        stage.setDescription("Bài luyện tự tạo");
        stage.setTargetSounds(List.of("z", "zh"));
        stage.setUnits(units);
        return stages.saveAndFlush(stage);
    }

    private PronUnit unit(String audio) {
        return new PronUnit("U1", audio, "Chọn âm", List.of(new PronUnit.Option("A", "z"),
                new PronUnit.Option("B", "zh"), new PronUnit.Option("C", "s"), new PronUnit.Option("D", "sh")),
                "B", "Giải thích nội bộ");
    }

    private void progress(long userId, PronStage stage, String type, String accuracy, String state) {
        jdbc.update("INSERT INTO learning.user_progress (user_id, target_type, target_id, percent, state, unlocked_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)", userId, type, stage.getId(),
                accuracy == null ? null : new BigDecimal(accuracy), state, java.sql.Timestamp.from(NOW));
        entityManager.clear();
    }

    private int progressCount(long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM learning.user_progress WHERE user_id = ?", Integer.class, userId);
    }

    private long start(long userId, PracticeSource source, PronStage stage) {
        var served = java.util.stream.IntStream.rangeClosed(1, 3).mapToObj(no -> new ServedItem(no,
                ItemType.PRON_CHOICE, Skill.PRONUNCIATION, null,
                new ServedItem.Prompt("Chọn âm", null, null, null, "https://example.test/audio.mp3", null, null, null, null),
                List.of(new ServedItem.Option("A", "z"), new ServedItem.Option("B", "zh")),
                "B", null, "Giải thích nội bộ", new ServedItem.Rules(500, 3, true))).toList();
        return practice.start(new PracticeService.StartPracticeCommand(userId, source, stage.getId(), null,
                served, null, null, null)).sessionId();
    }

    private void record(long sessionId, int no, Boolean correct) {
        AttemptItem item = new AttemptItem(sessionId, no, "PRON_CHOICE", "PRONUNCIATION");
        item.setAnswer(correct == null ? null : "B");
        item.setCorrect(correct);
        item.setMeta(correct == null ? Map.of("audioFailed", true) : Map.of());
        items.saveAndFlush(item);
    }

    private JsonNode list(long userId) throws Exception {
        return mapper.readTree(mvc.perform(get(PATH).header("Authorization", bearer(userId, "LEARNER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String bearer(long userId, String role) {
        return "Bearer " + token(userId, role);
    }

    private String token(long userId, String role) {
        return jwt.issueAccessToken(userId, List.of(role), false, UUID.randomUUID());
    }

    private long createUser() throws Exception {
        String password = "Aa1" + UUID.randomUUID();
        String body = mapper.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.test",
                "password", password, "confirmPassword", password, "displayName", "Pronunciation test user", "acceptTerms", true));
        return mapper.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("userId").asLong();
    }
}
