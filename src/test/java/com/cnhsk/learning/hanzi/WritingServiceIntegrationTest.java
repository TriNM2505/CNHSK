package com.cnhsk.learning.hanzi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.practice.PracticeSourceHandler.FinishContext;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.security.RateLimiter;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class WritingServiceIntegrationTest extends AbstractIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");
    private static final AtomicInteger NEXT_CHARACTER_CODE_POINT = new AtomicInteger(0x4DBF);

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private LexemeRepository lexemes;
    @Autowired private AttemptRepository attempts;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @MockitoBean private Clock clock;
    @MockitoSpyBean private WritingAttemptHandler handler;
    @MockitoSpyBean private RateLimiter rateLimiter;

    private long userId;
    private String bearer;

    @BeforeEach
    void setUp() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        userId = createUser();
        bearer = token(userId, "LEARNER");
    }

    @Test
    void FR010_invalidJsonAndMissingOrInvalidRequestFieldsReturn422() throws Exception {
        Lexeme lexeme = character(5, true);
        mvc.perform(post("/api/writing/attempts").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error_code")
                        .value("VALIDATION_FAILED"));
        mvc.perform(post("/api/writing/attempts").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error_code")
                        .value("VALIDATION_FAILED"));
        assertError(lexeme, Map.of("mode", "UNKNOWN"), 422, "VALIDATION_FAILED");
        assertError(lexeme, Map.of("lexemeId", -1), 422, "VALIDATION_FAILED");
        assertError(lexeme, Map.of("strokeMisses", 51), 422, "VALIDATION_FAILED");
        assertError(lexeme, Map.of("durationMs", 3_600_001), 422, "VALIDATION_FAILED");
        assertError(lexeme, Map.of("previewCount", -1), 422, "VALIDATION_FAILED");
        assertError(lexeme, Map.of("durationMs", -1), 422, "VALIDATION_FAILED");
    }

    @Test
    void FR011_invalidAccuracyAndMissingStrokeDataFollowCharacterLookup() throws Exception {
        Lexeme withoutStroke = character(null, true);
        assertError(withoutStroke, Map.of("accuracy", 1.5), 422, "STROKE_DATA_MISSING");
        Lexeme withStroke = character(5, true);
        assertError(withStroke, Map.of("accuracy", 1.5), 400, "INVALID_ACCURACY");
        assertErrorId(Long.MAX_VALUE, request(Map.of()), 404, "CHARACTER_NOT_FOUND");
    }

    @Test
    void FR012_impossibleCompletedDurationIsRejectedBeforeAnyAttemptIsWritten() throws Exception {
        Lexeme lexeme = character(12, true);
        assertError(lexeme, Map.of("durationMs", 500), 400, "IMPOSSIBLE_DURATION");
        assertThat(attemptCount(lexeme)).isZero();
        assertThat(itemCount(lexeme)).isZero();
    }

    @Test
    void FR013_recallRequiresStrokeAndRejectsHintsOrExcessivePreview() throws Exception {
        Lexeme lexeme = character(5, true);
        assertError(lexeme, Map.of("mode", "RECALL"), 403, "MODE_LOCKED");
        record(lexeme, Map.of());
        assertError(lexeme, Map.of("mode", "RECALL", "usedAnimation", true), 400,
                "HINT_USED_IN_RECALL");
        assertError(lexeme, Map.of("mode", "RECALL", "previewCount", 3), 400, "PREVIEW_TOO_LONG");
        assertError(lexeme, Map.of("mode", "RECALL", "previewMs", 11_001), 400, "PREVIEW_TOO_LONG");
    }

    @Test
    void FR014_dictationRequiresAudioAndRejectsReplayOrRevealedCharacter() throws Exception {
        Lexeme withoutAudio = character(5, false);
        assertError(withoutAudio, Map.of("mode", "DICTATION"), 422, "AUDIO_NOT_AVAILABLE");
        Lexeme withAudio = character(5, true);
        assertError(withAudio, Map.of("mode", "DICTATION", "replayCount", 4), 400,
                "TOO_MANY_REPLAYS");
        assertError(withAudio, Map.of("mode", "DICTATION", "usedAnimation", true), 400,
                "CHARACTER_REVEALED");
    }

    @Test
    void FR015_strokeModeRejectsPreviewAndReplayEvidence() throws Exception {
        Lexeme lexeme = character(5, true);
        assertError(lexeme, Map.of("previewCount", 1), 422, "VALIDATION_FAILED");
        assertError(lexeme, Map.of("replayCount", 1), 422, "VALIDATION_FAILED");
    }

    @Test
    void FR016_AT12_recordsAndFinishesAWritingAttemptWithPostgresEvidence() throws Exception {
        Lexeme lexeme = character(5, true);
        JsonNode result = record(lexeme, Map.of());

        assertThat(result.path("attemptId").asLong()).isPositive();
        assertThat(result.path("recorded").asBoolean()).isTrue();
        assertThat(result.path("counted").asBoolean()).isTrue();
        assertThat(result.path("recallUnlocked").asBoolean()).isTrue();
        var attempt = attempts.findById(result.path("attemptId").asLong()).orElseThrow();
        assertThat(attempt.getRefType()).isEqualTo("CHAR_WRITING");
        assertThat(attempt.getRefId()).isEqualTo(lexeme.getId());
        assertThat(attempt.getStatus()).isEqualTo("SUBMITTED");
        var item = jdbc.queryForMap("select item_type, answer, duration_ms, meta->>'mode' as mode, "
                + "meta->>'coefficientProposal' as coefficient from learning.attempt_items where attempt_id = ?",
                attempt.getId());
        assertThat(item).containsEntry("item_type", "WRITING_RESULT").containsEntry("answer", "COMPLETED")
                .containsEntry("mode", "STROKE").containsEntry("coefficient", "0.50");
        JsonNode animated = record(lexeme, Map.of("usedAnimation", true));
        assertThat(animated.path("counted").asBoolean()).isFalse();
        assertMeta(animated.path("attemptId").asLong(), "coefficientProposal", "0.25");
    }

    @Test
    void FR017_AT13_calculatesRecallAndDictationCoefficientsWithBigDecimal() throws Exception {
        Lexeme lexeme = character(5, true);
        record(lexeme, Map.of());
        JsonNode recall = record(lexeme, Map.of("mode", "RECALL", "previewCount", 2, "previewMs", 6_000));
        JsonNode dictation = record(lexeme, Map.of("mode", "DICTATION", "replayCount", 3));
        assertMeta(recall.path("attemptId").asLong(), "coefficientProposal", "0.50");
        Object coefficient = jdbc.queryForObject("select meta->>'coefficientProposal' from learning.attempt_items "
                + "where attempt_id = ?", String.class, dictation.path("attemptId").asLong());
        assertThat(new BigDecimal((String) coefficient).compareTo(new BigDecimal("0.8"))).isZero();
    }

    @Test
    void FR018_AT14_repeatWithinTenMinutesUsesSubmittedAtAndExactBoundaryIsNotRepeat() throws Exception {
        Lexeme lexeme = character(5, true);
        JsonNode first = record(lexeme, Map.of());
        when(clock.instant()).thenReturn(NOW.plusSeconds(180));
        JsonNode repeated = record(lexeme, Map.of());
        assertMeta(first.path("attemptId").asLong(), "repeatWithin10Min", "false");
        assertMeta(repeated.path("attemptId").asLong(), "repeatWithin10Min", "true");
        assertThat(repeated.path("counted").asBoolean()).isFalse();

        when(clock.instant()).thenReturn(NOW.plusSeconds(780));
        JsonNode afterWindow = record(lexeme, Map.of());
        assertMeta(afterWindow.path("attemptId").asLong(), "repeatWithin10Min", "false");
        assertThat(afterWindow.path("counted").asBoolean()).isTrue();
    }

    @Test
    void FR019_AT15_incompleteWritingIsRecordedButNotCounted() throws Exception {
        Lexeme lexeme = character(5, true);
        JsonNode result = record(lexeme, Map.of("completed", false));
        assertThat(result.path("counted").asBoolean()).isFalse();
        assertMeta(result.path("attemptId").asLong(), "coefficientProposal", "0.00");
        assertThat(jdbc.queryForObject("select answer from learning.attempt_items where attempt_id = ?",
                String.class, result.path("attemptId").asLong())).isEqualTo("INCOMPLETE");
    }

    @Test
    void FR019_AT16_writingLimitRejectsAfter300WithoutCreatingAttempt() throws Exception {
        Lexeme lexeme = character(5, true);
        String key = "writing:" + userId + ":2026103112";
        redis.opsForValue().set(key, "300", Duration.ofHours(1));

        assertError(lexeme, Map.of(), 429, "RATE_LIMITED");

        assertThat(attemptCount(lexeme)).isZero();
        assertThat(itemCount(lexeme)).isZero();
    }

    @Test
    void FR019_writingDoesNotUsePracticeStartAllowance() throws Exception {
        Lexeme lexeme = character(5, true);
        String key = "practice:start:" + userId + ":2026103112";
        redis.opsForValue().set(key, "120", Duration.ofHours(1));

        record(lexeme, Map.of());

        assertThat(redis.opsForValue().get(key)).isEqualTo("120");
    }

    @Test
    void FR016_handlerFailureAfterItemInsertRollsBackAttemptItemsAndStudyEvent() throws Exception {
        Lexeme lexeme = character(5, true);
        doThrow(new IllegalStateException("synthetic handler failure")).when(handler).onFinish(any(FinishContext.class));

        assertError(lexeme, Map.of(), 500, "INTERNAL_ERROR");

        assertThat(attemptCount(lexeme)).isZero();
        assertThat(itemCount(lexeme)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from learning.study_events where user_id = ? "
                + "and ref_type = 'CHAR_WRITING' and ref_id = ?", Integer.class, userId, lexeme.getId())).isZero();
    }

    @Test
    void FR019_rateLimiterRedisFailureAllowsValidWritingAttempt() throws Exception {
        Lexeme lexeme = character(5, true);
        doThrow(new IllegalStateException("synthetic Redis outage"))
                .when(rateLimiter).increment(startsWith("writing:"), any(Duration.class));

        JsonNode result = record(lexeme, Map.of());

        assertThat(result.path("recorded").asBoolean()).isTrue();
        assertThat(attemptCount(lexeme)).isEqualTo(1);
    }

    @Test
    void FR019_onlyLearnersCanSubmitWritingAttempts() throws Exception {
        Lexeme lexeme = character(5, true);
        mvc.perform(post("/api/writing/attempts").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(request(lexeme, Map.of()))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/writing/attempts").header("Authorization", token(userId, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(request(lexeme, Map.of()))))
                .andExpect(status().isForbidden());
    }

    private JsonNode record(Lexeme lexeme, Map<String, Object> overrides) throws Exception {
        MvcResult result = mvc.perform(post("/api/writing/attempts").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(request(lexeme, overrides))))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private void assertError(Lexeme lexeme, Map<String, Object> overrides, int statusCode, String code)
            throws Exception {
        String body = mvc.perform(post("/api/writing/attempts").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(request(lexeme, overrides))))
                .andExpect(status().is(statusCode)).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(body).path("error_code").asText()).isEqualTo(code);
    }

    private void assertErrorId(long id, Map<String, Object> overrides, int statusCode, String code) throws Exception {
        Map<String, Object> body = request(overrides);
        body.put("lexemeId", id);
        String response = mvc.perform(post("/api/writing/attempts").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)))
                .andExpect(status().is(statusCode)).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(response).path("error_code").asText()).isEqualTo(code);
    }

    private Map<String, Object> request(Lexeme lexeme, Map<String, Object> overrides) {
        Map<String, Object> request = request(Map.of());
        request.put("lexemeId", lexeme.getId());
        if ("DICTATION".equals(overrides.get("mode")) && !overrides.containsKey("durationMs")) {
            request.put("durationMs", 1_500);
        }
        request.putAll(overrides);
        return request;
    }

    private Map<String, Object> request(Map<String, Object> overrides) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("lexemeId", 1);
        request.put("mode", "STROKE");
        request.put("completed", true);
        request.put("accuracy", 1.0);
        request.put("strokeMisses", 0);
        request.put("durationMs", 1_000);
        if ("DICTATION".equals(overrides.get("mode"))) {
            request.put("durationMs", 1_500);
        }
        request.putAll(overrides);
        return request;
    }

    private void assertMeta(long attemptId, String key, String expected) {
        assertThat(jdbc.queryForObject("select meta->>? from learning.attempt_items where attempt_id = ?",
                String.class, key, attemptId)).isEqualTo(expected);
    }

    private int attemptCount(Lexeme lexeme) {
        return jdbc.queryForObject("select count(*) from learning.attempts where user_id = ? "
                + "and ref_type = 'CHAR_WRITING' and ref_id = ?", Integer.class, userId, lexeme.getId());
    }

    private int itemCount(Lexeme lexeme) {
        return jdbc.queryForObject("select count(*) from learning.attempt_items where lexeme_id = ? "
                + "and item_type = 'WRITING_RESULT' and attempt_id in "
                + "(select id from learning.attempts where user_id = ? and ref_type = 'CHAR_WRITING')",
                Integer.class, lexeme.getId(), userId);
    }

    private Lexeme character(Integer strokeCount, boolean audio) {
        int codePoint = NEXT_CHARACTER_CODE_POINT.getAndDecrement();
        if (codePoint < 0x3400) {
            throw new IllegalStateException("CJK Extension A test characters exhausted.");
        }
        Lexeme lexeme = new Lexeme(LexemeKind.CHAR, new String(Character.toChars(codePoint)),
                "meaning-" + UUID.randomUUID());
        lexeme.setPinyin("hǎo");
        lexeme.setHskLevel((short) 3);
        if (strokeCount != null) {
            lexeme.setStrokeCount(strokeCount.shortValue());
        }
        if (audio) {
            lexeme.setAudioUrl("https://audio.example.test/" + UUID.randomUUID() + ".mp3");
        }
        return lexemes.saveAndFlush(lexeme);
    }

    private long createUser() throws Exception {
        String password = "Aa1" + UUID.randomUUID();
        byte[] body = mapper.writeValueAsBytes(Map.of("email", UUID.randomUUID() + "@example.test",
                "password", password, "confirmPassword", password, "displayName", "Writing test",
                "acceptTerms", true));
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).path("userId").asLong();
    }

    private String token(long id, String role) {
        return "Bearer " + jwt.issueAccessToken(id, List.of(role), false, UUID.randomUUID());
    }
}
