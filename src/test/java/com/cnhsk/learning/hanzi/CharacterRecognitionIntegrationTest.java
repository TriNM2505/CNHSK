package com.cnhsk.learning.hanzi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.generate.ChoiceItemFactory;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class CharacterRecognitionIntegrationTest extends AbstractIntegrationTest {
    private static final AtomicInteger NEXT_CHARACTER_CODE_POINT = new AtomicInteger(0x3400);

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private LexemeRepository lexemes;
    @Autowired private AttemptRepository attempts;
    @MockitoBean private Clock clock;
    @MockitoSpyBean private ChoiceItemFactory choiceFactory;
    private long userId;
    private String bearer;
    private String testRadical;
    private List<Lexeme> characters;

    @BeforeEach
    void setUp() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-10-31T00:00:00Z"));
        testRadical = "r" + UUID.randomUUID().toString().replace("-", "").substring(0, 7);
        userId = createUser();
        bearer = token(userId, "LEARNER");
        characters = List.of(
                character("hǎo", "một nghĩa " + UUID.randomUUID(), false),
                character("zì", "hai nghĩa " + UUID.randomUUID(), false),
                character("dà", "ba nghĩa " + UUID.randomUUID(), false),
                character("xiǎo", "bốn nghĩa " + UUID.randomUUID(), false),
                character("zhōng", "năm nghĩa " + UUID.randomUUID(), false),
                character("rén", "sáu nghĩa " + UUID.randomUUID(), false),
                character("shí", "bảy nghĩa " + UUID.randomUUID(), false),
                character("míng", "tám nghĩa " + UUID.randomUUID(), false));
    }

    @Test
    void FR006_AT03_postsFourChoicesWithoutAnswerAndUsesSameRadicalDistractors() throws Exception {
        JsonNode response = start(bearer, "CHAR_TO_MEANING", 9, 20, 201);
        assertThat(response.has("sessionId")).isTrue();
        assertThat(response.has("session")).isFalse();
        JsonNode items = response.path("items");
        assertThat(items).hasSizeGreaterThanOrEqualTo(characters.size());
        boolean sawFixtureCharacter = false;
        for (JsonNode item : items) {
            assertThat(item.path("type").asText()).isEqualTo("CHAR_MEANING_CHOICE");
            assertThat(item.path("skill").asText()).isEqualTo("CHARACTER");
            assertThat(item.path("prompt").path("text").asText()).isNotBlank();
            assertThat(item.path("prompt").has("pinyin")).isFalse();
            assertThat(item.path("options")).hasSize(4);
            assertThat(item.path("rules").path("minDurationMs").asInt()).isEqualTo(800);
            assertThat(item.path("rules").path("maxReplays").asInt()).isEqualTo(3);
            assertThat(item.path("rules").path("hasAudio").asBoolean()).isFalse();
            assertThat(item.has("answer")).isFalse();
            assertThat(item.has("correctAnswer")).isFalse();
            assertThat(item.has("accept")).isFalse();
            long targetId = item.path("lexemeId").asLong();
            Lexeme fixtureTarget = characters.stream().filter(character -> character.getId() == targetId)
                    .findFirst().orElse(null);
            if (fixtureTarget != null) {
                sawFixtureCharacter = true;
                assertThat(item.path("prompt").path("text").asText())
                        .isEqualTo(fixtureTarget.getText()).hasSize(1);
                assertThat(item.path("options").findValuesAsText("text"))
                        .anyMatch(text -> characters.stream().anyMatch(character -> character.getId() != targetId
                                && character.getMeaningVi().equals(text)));
            }
        }
        assertThat(sawFixtureCharacter).isTrue();
    }

    @Test
    void FR007_AT04_latestWrongCharacterIsPrioritizedUntilAnsweredCorrectly() throws Exception {
        JsonNode first = start(bearer, "CHAR_TO_MEANING", 9, 5, 201);
        JsonNode firstItem = first.path("items").get(0);
        long targetId = firstItem.path("lexemeId").asLong();
        String targetKey = correctAnswer(first.path("sessionId").asLong(), 1);
        String wrongKey = firstItem.path("options").findValuesAsText("key").stream()
                .filter(key -> !key.equals(targetKey)).findFirst().orElseThrow();
        JsonNode wrongResult = answer(first.path("sessionId").asLong(), 1, wrongKey, false);
        assertThat(wrongResult.path("correct").asBoolean()).isFalse();

        JsonNode second = start(bearer, "CHAR_TO_MEANING", 9, 5, 201);
        JsonNode prioritized = second.path("items").get(0);
        assertThat(prioritized.path("lexemeId").asLong()).isEqualTo(targetId);
        String secondAnswer = correctAnswer(second.path("sessionId").asLong(), 1);
        assertThat(answer(second.path("sessionId").asLong(), 1, secondAnswer, false).path("correct").asBoolean())
                .isTrue();

        JsonNode third = start(bearer, "CHAR_TO_MEANING", 9, 5, 201);
        assertThat(third.path("items").get(0).path("lexemeId").asLong()).isNotEqualTo(targetId);
    }

    @Test
    void FR005_skipsPreferredCharacterWithoutFourDistractorsAndFillsFromNextCandidate() throws Exception {
        JsonNode initial = start(bearer, "CHAR_TO_MEANING", 9, 5, 201);
        JsonNode firstItem = initial.path("items").get(0);
        long preferredId = firstItem.path("lexemeId").asLong();
        String correct = correctAnswer(initial.path("sessionId").asLong(), 1);
        String wrong = firstItem.path("options").findValuesAsText("key").stream()
                .filter(key -> !key.equals(correct)).findFirst().orElseThrow();
        assertThat(answer(initial.path("sessionId").asLong(), 1, wrong, false).path("correct").asBoolean())
                .isFalse();

        doReturn(Optional.empty()).when(choiceFactory).create(
                argThat(candidate -> candidate != null && candidate.getId().equals(preferredId)),
                isNull(), eq(ItemType.CHAR_MEANING_CHOICE), eq(4));

        JsonNode filled = start(bearer, "CHAR_TO_MEANING", 9, 5, 201);
        assertThat(filled.path("items")).hasSize(5);
        assertThat(filled.path("items").findValuesAsText("lexemeId")).doesNotContain(Long.toString(preferredId));
    }

    @Test
    void FR008_AT06_returnsNoCharactersAsSuccessfulEmptyResult() throws Exception {
        JsonNode response = start(bearer, "AUDIO_TO_CHAR", 8, 5, 200);
        assertThat(response.path("session").isNull()).isTrue();
        assertThat(response.path("reason").asText()).isEqualTo("NO_CHARACTERS");
    }

    @Test
    void FR007_AT07_audioFailureIsNotStoredAsWrongAnswer() throws Exception {
        List<Lexeme> withAudio = List.of(
                character("hǎo", "audio một " + UUID.randomUUID(), true),
                character("zì", "audio hai " + UUID.randomUUID(), true),
                character("dà", "audio ba " + UUID.randomUUID(), true),
                character("xiǎo", "audio bốn " + UUID.randomUUID(), true),
                character("zhōng", "audio năm " + UUID.randomUUID(), true),
                character("rén", "audio sáu " + UUID.randomUUID(), true));
        JsonNode first = start(bearer, "AUDIO_TO_CHAR", 7, 5, 201);
        JsonNode item = first.path("items").get(0);
        long targetId = item.path("lexemeId").asLong();
        assertThat(item.path("type").asText()).isEqualTo("AUDIO_CHAR_CHOICE");
        assertThat(item.path("skill").asText()).isEqualTo("LISTENING");
        assertThat(item.path("prompt").path("audioUrl").asText()).isNotBlank();
        assertThat(item.path("prompt").has("text")).isFalse();
        assertThat(item.path("prompt").has("pinyin")).isFalse();
        assertThat(item.path("rules").path("hasAudio").asBoolean()).isTrue();
        mvc.perform(post("/api/practice/sessions/{id}/answers", first.path("sessionId").asLong())
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("no", 1, "durationMs", 800, "audioFailed", true))))
                .andExpect(status().isOk());

        JsonNode next = start(bearer, "AUDIO_TO_CHAR", 7, 5, 201);
        assertThat(next.path("items").get(0).path("lexemeId").asLong()).isNotEqualTo(targetId);
        assertThat(withAudio).hasSize(6);
    }

    @Test
    void FR004_activeRecognitionCanBeRestoredWithoutRefId() throws Exception {
        JsonNode first = start(bearer, "CHAR_TO_MEANING", 9, 5, 201);
        JsonNode second = start(bearer, "CHAR_TO_MEANING", 9, 5, 201);
        JsonNode active = mapper.readTree(mvc.perform(get("/api/practice/sessions/active")
                        .param("source", "CHAR_RECOGNITION").header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(active.path("sessionId").asLong()).isEqualTo(second.path("sessionId").asLong());
        assertThat(active.path("sessionId").asLong()).isNotEqualTo(first.path("sessionId").asLong());
    }

    @Test
    void FR004_nonLearnerCannotStartRecognition() throws Exception {
        mvc.perform(post("/api/characters/recognition-sessions").header("Authorization", token(userId, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("mode", "CHAR_TO_MEANING", "hskLevel", 9))))
                .andExpect(status().isForbidden());
    }

    @Test
    void FR004_invalidRecognitionRequestReturnsValidationFailed() throws Exception {
        List<Map<String, Object>> requests = List.of(
                Map.of("hskLevel", 9), Map.of("mode", "UNKNOWN", "hskLevel", 9),
                Map.of("mode", "CHAR_TO_MEANING", "hskLevel", 0),
                Map.of("mode", "CHAR_TO_MEANING", "hskLevel", 9, "count", 4),
                Map.of("mode", "CHAR_TO_MEANING", "hskLevel", 9, "count", 21));
        for (Map<String, Object> body : requests) {
            mvc.perform(post("/api/characters/recognition-sessions").header("Authorization", bearer)
                            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(result -> assertThat(mapper.readTree(result.getResponse().getContentAsString())
                            .path("error_code").asText()).isEqualTo("VALIDATION_FAILED"));
        }
    }

    private JsonNode start(String authorization, String mode, int level, int count, int expectedStatus)
            throws Exception {
        byte[] body = mapper.writeValueAsBytes(Map.of("mode", mode, "hskLevel", level, "count", count));
        String content = mvc.perform(post("/api/characters/recognition-sessions")
                        .header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return mapper.readTree(content);
    }

    private JsonNode answer(long sessionId, int no, String key, boolean audioFailed) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/practice/sessions/{id}/answers", sessionId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("no", no, "answer", key, "durationMs", 800,
                                "audioFailed", audioFailed))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String correctAnswer(long sessionId, int no) {
        JsonNode item = attempts.findById(sessionId).orElseThrow().getServedItems().path("items").get(no - 1);
        return item.path("answer").asText();
    }

    private Lexeme character(String pinyin, String meaning, boolean audio) {
        int codePoint = NEXT_CHARACTER_CODE_POINT.getAndIncrement();
        if (codePoint > 0x4DBF) {
            throw new IllegalStateException("CJK Extension A test characters exhausted.");
        }
        String text = new String(Character.toChars(codePoint));
        Lexeme lexeme = new Lexeme(LexemeKind.CHAR, text, meaning);
        lexeme.setPinyin(pinyin);
        lexeme.setHskLevel((short) (audio ? 7 : 9));
        lexeme.setRadical(testRadical);
        if (audio) {
            lexeme.setAudioUrl("https://audio.example.test/" + UUID.randomUUID() + ".mp3");
        }
        return lexemes.saveAndFlush(lexeme);
    }

    private long createUser() throws Exception {
        String password = "Aa1" + UUID.randomUUID();
        byte[] body = mapper.writeValueAsBytes(Map.of("email", UUID.randomUUID() + "@example.test", "password", password,
                "confirmPassword", password, "displayName", "Recognition test", "acceptTerms", true));
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).path("userId").asLong();
    }

    private String token(long id, String role) {
        return "Bearer " + jwt.issueAccessToken(id, List.of(role), false, UUID.randomUUID());
    }
}
