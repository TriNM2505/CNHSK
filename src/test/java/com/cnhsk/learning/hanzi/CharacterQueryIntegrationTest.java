package com.cnhsk.learning.hanzi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.practice.FeedbackMode;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler;
import com.cnhsk.learning.practice.Skill;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@Import(CharacterQueryIntegrationTest.EvidenceConfiguration.class)
class CharacterQueryIntegrationTest extends AbstractIntegrationTest {
    private static final java.time.Instant NOW = java.time.Instant.parse("2026-10-31T00:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtService jwt;
    @Autowired LexemeRepository lexemes;
    @Autowired PracticeService practice;
    @Autowired CharacterQueryService characters;
    @MockitoBean Clock clock;
    private long owner;
    private long other;
    private Lexeme charWithStroke;
    private Lexeme charWithoutStroke;
    private Lexeme otherCharacter;
    private Lexeme incompleteCharacter;
    private Lexeme recallCharacter;
    private Lexeme dictationCharacter;

    @BeforeEach
    void setUp() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        owner = createUser();
        other = createUser();
        charWithStroke = character("好", (short) 7);
        charWithoutStroke = character("字", null);
        otherCharacter = character("大", (short) 3);
        incompleteCharacter = character("小", (short) 3);
        recallCharacter = character("中", (short) 4);
        dictationCharacter = character("人", (short) 2);
    }

    @Test
    void FR002_AT02_preRecordedWritingEvidenceUnlocksRecallOnlyForOwnCompletedStroke() {
        record(owner, charWithStroke, "STROKE", "COMPLETED");
        record(owner, incompleteCharacter, "STROKE", "INCOMPLETE");
        record(owner, recallCharacter, "RECALL", "COMPLETED");
        record(owner, dictationCharacter, "DICTATION", "COMPLETED");
        record(other, otherCharacter, "STROKE", "COMPLETED");
        record(owner, charWithoutStroke, "STROKE", "COMPLETED");

        assertThat(characters.detail(owner, charWithStroke.getId()).writing().recallUnlocked()).isTrue();
        assertThat(characters.detail(owner, incompleteCharacter.getId()).writing().recallUnlocked()).isFalse();
        assertThat(characters.detail(owner, recallCharacter.getId()).writing().recallUnlocked()).isFalse();
        assertThat(characters.detail(owner, dictationCharacter.getId()).writing().recallUnlocked()).isFalse();
        assertThat(characters.detail(owner, otherCharacter.getId()).writing().recallUnlocked()).isFalse();
        assertThat(characters.detail(owner, charWithoutStroke.getId()).writing().strokeAvailable()).isFalse();
        assertThat(characters.detail(owner, charWithoutStroke.getId()).writing().recallUnlocked()).isTrue();
    }

    @Test
    void FR001_FR002_onlyLearnersCanReadCharacters() throws Exception {
        for (String role : List.of("ADMIN", "TEACHER", "CONTENT_EDITOR", "MODERATOR", "ACCOUNTANT")) {
            String bearer = "Bearer " + jwt.issueAccessToken(owner, List.of(role), false, UUID.randomUUID());
            mvc.perform(get("/api/characters").header("Authorization", bearer))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
            mvc.perform(get("/api/characters/{id}", charWithStroke.getId()).header("Authorization", bearer))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        }
    }

    @Test
    void FR001_AT01_listsCharactersThroughLearnerHttpAgainstPostgres() throws Exception {
        Lexeme exact = characterWith("好", (short) 9, "hao", Integer.MAX_VALUE);
        Lexeme sameLevel = characterWith("好他", (short) 9, "haoz", 10);
        Lexeme wildcardDecoy = characterWith("偏旁", (short) 9, "pxqz", 9);
        Lexeme backslashPrefix = characterWith("反斜", (short) 9, "p\\qz", 8);

        String bearer = learnerBearer();
        mvc.perform(get("/api/characters").param("size", "50").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.hasItem(exact.getId().intValue())));
        mvc.perform(get("/api/characters").param("hskLevel", "9").param("size", "50")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.hasItems(
                        exact.getId().intValue(), sameLevel.getId().intValue(), wildcardDecoy.getId().intValue(),
                        backslashPrefix.getId().intValue())))
                .andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(get("/api/characters").param("hskLevel", "9").param("q", "好")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.contains(exact.getId().intValue())))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/characters").param("hskLevel", "9").param("q", "hao")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.containsInAnyOrder(
                        exact.getId().intValue(), sameLevel.getId().intValue())))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/characters").param("hskLevel", "9").param("q", "%")
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/characters").param("hskLevel", "9").param("q", "p_q")
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/characters").param("hskLevel", "9").param("q", "--")
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/characters").param("hskLevel", "9").param("q", "p\\q")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.contains(
                        backslashPrefix.getId().intValue())))
                .andExpect(jsonPath("$.totalElements").value(1));

        String orderPrefix = "x" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Lexeme lowerFirst = characterWith("排甲" + orderPrefix, (short) 7, orderPrefix + "a", 10);
        Lexeme lowerSecond = characterWith("排乙" + orderPrefix, (short) 7, orderPrefix + "b", 10);
        Lexeme lowerThird = characterWith("排丙" + orderPrefix, (short) 7, orderPrefix + "c", 5);
        Lexeme higherLevel = characterWith("排丁" + orderPrefix, (short) 8, orderPrefix + "d", 99);
        String pageBearer = learnerBearer();
        mvc.perform(get("/api/characters").param("q", orderPrefix).param("size", "2")
                        .header("Authorization", pageBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.contains(
                        lowerFirst.getId().intValue(), lowerSecond.getId().intValue())))
                .andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(get("/api/characters").param("q", orderPrefix).param("size", "2").param("page", "1")
                        .header("Authorization", pageBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.contains(
                        lowerThird.getId().intValue(), higherLevel.getId().intValue())))
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void FR001_FR002_AT01_rejectsOutOfRangePageAndNonPositiveId() throws Exception {
        String bearer = learnerBearer();
        mvc.perform(get("/api/characters").param("size", "51").header("Authorization", bearer))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/characters").param("page", "9999999999").header("Authorization", bearer))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/characters").param("page", "1000000000").param("size", "50")
                        .header("Authorization", bearer))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/characters/{id}", 0).header("Authorization", bearer))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/characters/{id}", -1).header("Authorization", bearer))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        Lexeme word = lexemes.saveAndFlush(new Lexeme(LexemeKind.WORD, "valid-word-id", "nghĩa"));
        mvc.perform(get("/api/characters/{id}", word.getId()).header("Authorization", bearer))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code").value("CHARACTER_NOT_FOUND"));
    }

    private String learnerBearer() {
        return "Bearer " + jwt.issueAccessToken(owner, List.of("LEARNER"), false, UUID.randomUUID());
    }

    private void record(long userId, Lexeme lexeme, String mode, String answer) {
        var item = new ServedItem(1, ItemType.WRITING_RESULT, Skill.WRITING, lexeme.getId(),
                new ServedItem.Prompt(lexeme.getText(), null, null, lexeme.getMeaningVi(), null,
                        null, null, null, "CHARACTER"), null, "COMPLETED", null, "", new ServedItem.Rules(0, 0, false));
        var evidence = new PracticeSourceHandler.Answer(1, answer, 2_000, 0, false, false, Map.of("mode", mode));
        var session = practice.start(new PracticeService.StartPracticeCommand(userId, PracticeSource.CHAR_WRITING,
                lexeme.getId(), null, List.of(item), List.of(evidence), null, Map.of()));
        practice.finish(userId, session.sessionId(), List.of());
    }

    private Lexeme character(String text, Short strokes) {
        String uniqueText = text + UUID.randomUUID();
        Lexeme lexeme = new Lexeme(LexemeKind.CHAR, uniqueText, "nghĩa " + uniqueText);
        lexeme.setHskLevel((short) 1);
        if (text.startsWith("好")) {
            lexeme.setPinyin("hǎo");
            lexeme.setPinyinNorm("hao");
        }
        lexeme.setStrokeCount(strokes);
        return lexemes.saveAndFlush(lexeme);
    }

    private Lexeme characterWith(String text, Short level, String pinyinNorm, int frequency) {
        Lexeme lexeme = new Lexeme(LexemeKind.CHAR, text, "nghĩa " + text);
        lexeme.setHskLevel(level);
        lexeme.setPinyinNorm(pinyinNorm);
        lexeme.setFrequency(frequency);
        return lexemes.saveAndFlush(lexeme);
    }

    private long createUser() throws Exception {
        String password = "Aa1" + UUID.randomUUID();
        String body = mapper.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.test", "password", password,
                "confirmPassword", password, "displayName", "Hanzi test", "acceptTerms", true));
        return mapper.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("userId").asLong();
    }

    @TestConfiguration
    static class EvidenceConfiguration {
        @Bean
        PracticeSourceHandler testCharacterWritingHandler() {
            return new PracticeSourceHandler() {
                @Override public PracticeSource source() { return PracticeSource.CHAR_WRITING; }
                @Override public Map<String, Object> onFinish(FinishContext context) { return Map.of(); }
                @Override public Map<String, Object> enrichMeta(ServedItem item, Answer answer) {
                    return Map.of("mode", answer.clientMeta().get("mode"));
                }
            };
        }
    }
}
