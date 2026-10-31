package com.cnhsk.learning.hanzi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.random.RandomGenerator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeService.StartPracticeCommand;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler.Answer;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.Skill;
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
import static org.mockito.ArgumentMatchers.anyInt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class WritingChallengeIntegrationTest extends AbstractIntegrationTest {
    private static final AtomicInteger NEXT_CHARACTER_CODE_POINT = new AtomicInteger(0x4000);
    private static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private LexemeRepository lexemes;
    @Autowired private AttemptRepository attempts;
    @Autowired private PracticeService practice;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @MockitoBean private Clock clock;
    @MockitoBean(name = "practiceChoiceRandom") private RandomGenerator random;

    private final AtomicReference<Instant> now = new AtomicReference<>(NOW);
    private long userId;
    private String bearer;

    @BeforeEach
    void setUp() throws Exception {
        now.set(NOW);
        org.mockito.Mockito.when(clock.instant()).thenAnswer(invocation -> now.get());
        org.mockito.Mockito.when(random.nextInt(anyInt()))
                .thenAnswer(invocation -> (int) invocation.getArgument(0) - 1);
        userId = createUser();
        bearer = token(userId, "LEARNER");
    }

    @Test
    void FR020_AT17_returnsHaveAndNeedWhenOnlyFiveCharactersAreEligible() throws Exception {
        for (int i = 0; i < 5; i++) {
            Lexeme character = character(5);
            recordStroke(character);
        }

        JsonNode error = postChallenge(bearer, 422);

        assertThat(error.path("error_code").asText()).isEqualTo("NOT_ENOUGH_CHARACTERS");
        assertThat(error.path("have").asInt()).isEqualTo(5);
        assertThat(error.path("need").asInt()).isEqualTo(10);
        assertThat(attemptCount()).isZero();
    }

    @Test
    void FR021_AT18_startsChallengeWithTenEligibleCharactersAndNoAnswer() throws Exception {
        for (int i = 0; i < 10; i++) recordStroke(character(5));

        JsonNode response = postChallenge(bearer, 201);

        assertThat(response.path("source").asText()).isEqualTo("WRITING_CHALLENGE");
        assertThat(response.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(response.path("items")).hasSize(10);
        assertThat(response.path("expiresAt").asText()).isEqualTo(NOW.plusSeconds(210).toString());
        assertThat(response.path("meta").path("timeLimitSeconds").asInt()).isEqualTo(200);
        assertThat(response.path("meta").path("maxMistakes").asInt()).isEqualTo(3);
        assertThat(response.path("meta").path("perCharSeconds").asInt()).isEqualTo(20);
        assertThat(response.path("items").findValuesAsText("type")).containsOnly("WRITING_RESULT");
        assertThat(response.path("items").findValuesAsText("lexemeId")).doesNotHaveDuplicates();
        for (JsonNode item : response.path("items")) {
            assertThat(item.path("prompt").path("strokeCount").asInt()).isEqualTo(5);
            assertThat(item.path("prompt").path("text").asText()).isNotBlank();
            assertThat(item.path("prompt").path("pinyin").asText()).isNotBlank();
            assertThat(item.path("prompt").path("meaning").asText()).isNotBlank();
            assertThat(item.has("answer")).isFalse();
            assertThat(item.has("correctAnswer")).isFalse();
        }
        assertThat(attempts.findById(response.path("sessionId").asLong()).orElseThrow().getStatus())
                .isEqualTo("IN_PROGRESS");
    }

    @Test
    void FR020_eligibilityRequiresStrokeCompletedAndStrokeCount() throws Exception {
        Lexeme stroke = character(5);
        Lexeme recall = character(5);
        Lexeme incomplete = character(5);
        Lexeme withoutStrokes = character(null);
        recordStroke(stroke);
        recordPracticeMode(recall, "RECALL");
        recordWriting(incomplete, "STROKE", false);

        JsonNode error = postChallenge(bearer, 422);

        assertThat(error.path("have").asInt()).isEqualTo(1);
    }

    @Test
    void FR021_prioritizesCharactersWithTheOldestWritingPractice() throws Exception {
        List<Lexeme> characters = new java.util.ArrayList<>();
        for (int i = 0; i < 11; i++) characters.add(character(5));
        for (int i = 0; i < 10; i++) {
            now.set(NOW.plusSeconds(i * 10L));
            long attemptId = recordStroke(characters.get(i));
            jdbc.update("update learning.attempt_items set answered_at = ? where attempt_id = ?",
                    java.sql.Timestamp.from(NOW.plusSeconds(i * 10L)), attemptId);
        }
        now.set(NOW.plusSeconds(200));
        long newestAttemptId = recordStroke(characters.get(10));
        jdbc.update("update learning.attempt_items set answered_at = ? where attempt_id = ?",
                java.sql.Timestamp.from(NOW.plusSeconds(100)), newestAttemptId);

        JsonNode response = postChallenge(bearer, 201);

        List<String> selected = response.path("items").findValuesAsText("lexemeId");
        assertThat(selected).contains(characters.get(0).getId().toString());
        assertThat(selected).doesNotContain(characters.get(10).getId().toString());
    }

    @Test
    void FR021_tiedPracticeTimesUseInjectableRandomGenerator() throws Exception {
        List<Lexeme> characters = new java.util.ArrayList<>();
        for (int i = 0; i < 11; i++) {
            Lexeme character = character(5);
            characters.add(character);
            long attemptId = recordStroke(character);
            jdbc.update("update learning.attempt_items set answered_at = ? where attempt_id = ?",
                    java.sql.Timestamp.from(NOW), attemptId);
        }

        JsonNode response = postChallenge(bearer, 201);

        assertThat(response.path("items").findValuesAsText("lexemeId"))
                .containsExactlyElementsOf(characters.subList(0, 10).stream()
                        .map(character -> character.getId().toString()).toList());
    }

    @Test
    void FR021_rankingIncludesWritingAttemptsFromEveryMode() throws Exception {
        List<Lexeme> characters = new java.util.ArrayList<>();
        for (int i = 0; i < 11; i++) {
            Lexeme character = character(5);
            characters.add(character);
            long attemptId = recordStroke(character);
            jdbc.update("update learning.attempt_items set answered_at = ? where attempt_id = ?",
                    java.sql.Timestamp.from(NOW), attemptId);
        }
        now.set(NOW.plusSeconds(100));
        recordPracticeMode(characters.get(0), "RECALL");
        jdbc.update("update learning.attempt_items set answered_at = ? where attempt_id = "
                        + "(select max(id) from learning.attempts where user_id = ? and ref_type = 'CHAR_WRITING')",
                java.sql.Timestamp.from(NOW.plusSeconds(100)), userId);

        JsonNode response = postChallenge(bearer, 201);

        assertThat(response.path("items").findValuesAsText("lexemeId"))
                .contains(characters.get(10).getId().toString())
                .doesNotContain(characters.get(0).getId().toString());
    }

    @Test
    void FR021_rankingIncludesCompletedWritingChallengeItems() throws Exception {
        List<Lexeme> characters = new java.util.ArrayList<>();
        for (int i = 0; i < 11; i++) {
            Lexeme character = character(5);
            characters.add(character);
            long attemptId = recordStroke(character);
            jdbc.update("update learning.attempt_items set answered_at = ? where attempt_id = ?",
                    java.sql.Timestamp.from(NOW), attemptId);
        }
        JsonNode first = postChallenge(bearer, 201);
        long challengedCharacterId = first.path("items").get(0).path("lexemeId").asLong();
        now.set(NOW.plusSeconds(100));
        finishChallenge(first.path("sessionId").asLong(), 1);
        jdbc.update("update learning.attempt_items set answered_at = ? where attempt_id = ? and order_index = 1",
                java.sql.Timestamp.from(NOW.plusSeconds(100)), first.path("sessionId").asLong());
        now.set(NOW.plusSeconds(200));

        JsonNode next = postChallenge(bearer, 201);

        assertThat(next.path("items").findValuesAsText("lexemeId"))
                .contains(characters.get(10).getId().toString())
                .doesNotContain(Long.toString(challengedCharacterId));
    }

    @Test
    void FR023_returnsActiveChallengeAndExpiredChallengeIsReplaced() throws Exception {
        for (int i = 0; i < 10; i++) recordStroke(character(5));
        JsonNode first = postChallenge(bearer, 201);
        String rateKey = "practice:start:" + userId + ":2026103112";
        assertThat(redis.opsForValue().get(rateKey)).isEqualTo("1");
        JsonNode active = postChallenge(bearer, 200);
        assertThat(active.path("sessionId").asLong()).isEqualTo(first.path("sessionId").asLong());
        assertThat(redis.opsForValue().get(rateKey)).isEqualTo("1");

        now.set(NOW.plusSeconds(211));
        JsonNode replacement = postChallenge(bearer, 201);
        assertThat(replacement.path("sessionId").asLong()).isNotEqualTo(first.path("sessionId").asLong());
        assertThat(attemptCount()).isEqualTo(2);
    }

    @Test
    void FR023_concurrentChallengePostsCreateOnlyOneAttempt() throws Exception {
        for (int i = 0; i < 10; i++) recordStroke(character(5));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = CompletableFuture.supplyAsync(() -> postChallengeUnchecked(), executor);
            var second = CompletableFuture.supplyAsync(() -> postChallengeUnchecked(), executor);
            List<Integer> statuses = List.of(first.join().getResponse().getStatus(), second.join().getResponse().getStatus());
            assertThat(statuses).containsExactlyInAnyOrder(200, 201);
            assertThat(attemptCount()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void FR023_newChallengeStillUsesPracticeStartRateLimit() throws Exception {
        for (int i = 0; i < 10; i++) recordStroke(character(5));
        redis.opsForValue().set("practice:start:" + userId + ":2026103112", "120", Duration.ofHours(1));

        JsonNode error = postChallenge(bearer, 429);

        assertThat(error.path("error_code").asText()).isEqualTo("RATE_LIMITED");
        assertThat(attemptCount()).isZero();
    }

    @Test
    void FR020_onlyLearnersCanStartChallenge() throws Exception {
        mvc.perform(post("/api/writing/challenges").header("Authorization", token(userId, "ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void FR021_oldPracticeSnapshotWithoutStrokeCountStillDeserializes() throws Exception {
        String oldPrompt = """
                {"text":"字","pinyin":"zì","sinoViet":null,"meaning":"chữ","audioUrl":null,
                 "sentence":null,"hint":null,"examples":null,"kind":"CHARACTER"}
                """;

        var prompt = mapper.readValue(oldPrompt, com.cnhsk.learning.practice.ServedItem.Prompt.class);

        assertThat(prompt.text()).isEqualTo("字");
        assertThat(prompt.strokeCount()).isNull();
    }

    private JsonNode postChallenge(String authorization, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(post("/api/writing/challenges").header("Authorization", authorization))
                .andExpect(status().is(expectedStatus)).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private MvcResult postChallengeUnchecked() {
        try {
            return mvc.perform(post("/api/writing/challenges").header("Authorization", bearer)).andReturn();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void finishChallenge(long sessionId, int questionNo) throws Exception {
        Map<String, Object> answer = Map.of("no", questionNo, "answer", "COMPLETED", "durationMs", 1_000,
                "replayCount", 0, "skipped", false, "audioFailed", false);
        mvc.perform(post("/api/practice/sessions/{id}/finish", sessionId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("answers", List.of(answer)))))
                .andExpect(status().isOk());
    }

    private long recordStroke(Lexeme character) throws Exception {
        return recordWriting(character, "STROKE", true);
    }

    private long recordWriting(Lexeme character, String mode, boolean completed) throws Exception {
        Map<String, Object> body = Map.of("lexemeId", character.getId(), "mode", mode, "completed", completed,
                "accuracy", BigDecimal.ONE, "strokeMisses", 0, "durationMs", 1_000,
                "usedAnimation", false, "previewCount", 0, "previewMs", 0, "replayCount", 0);
        String bodyResponse = mvc.perform(post("/api/writing/attempts").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(bodyResponse).path("attemptId").asLong();
    }

    private void recordPracticeMode(Lexeme character, String mode) {
        var prompt = new ServedItem.Prompt(character.getText(), character.getPinyin(), null,
                character.getMeaningVi(), null, null, null, null, "CHARACTER", character.getStrokeCount().intValue());
        var item = new ServedItem(1, ItemType.WRITING_RESULT, Skill.WRITING, character.getId(), prompt,
                null, null, null, null, new ServedItem.Rules(0, 3, false));
        var metadata = Map.<String, Object>of("mode", mode, "completed", true, "strokeCount",
                character.getStrokeCount().intValue(), "coefficientProposal", 1.0);
        practice.recordAndFinish(new StartPracticeCommand(userId, PracticeSource.CHAR_WRITING, character.getId(),
                null, List.of(item), List.of(new Answer(1, "COMPLETED", 1_000, 0, false, false, metadata)),
                null, Map.of()));
    }

    private Lexeme character(Integer strokeCount) {
        int codePoint = NEXT_CHARACTER_CODE_POINT.getAndIncrement();
        if (codePoint > 0x4A00) throw new IllegalStateException("CJK Extension A test characters exhausted.");
        Lexeme lexeme = new Lexeme(LexemeKind.CHAR, new String(Character.toChars(codePoint)),
                "meaning-" + UUID.randomUUID());
        lexeme.setPinyin("hǎo");
        lexeme.setHskLevel((short) 3);
        if (strokeCount != null) lexeme.setStrokeCount(strokeCount.shortValue());
        return lexemes.saveAndFlush(lexeme);
    }

    private int attemptCount() {
        return jdbc.queryForObject("select count(*) from learning.attempts where user_id = ? "
                + "and ref_type = 'WRITING_CHALLENGE'", Integer.class, userId);
    }

    private long createUser() throws Exception {
        String password = "Aa1" + UUID.randomUUID();
        byte[] body = mapper.writeValueAsBytes(Map.of("email", UUID.randomUUID() + "@example.test",
                "password", password, "confirmPassword", password, "displayName", "Challenge test",
                "acceptTerms", true));
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).path("userId").asLong();
    }

    private String token(long id, String role) {
        return "Bearer " + jwt.issueAccessToken(id, List.of(role), false, UUID.randomUUID());
    }
}
