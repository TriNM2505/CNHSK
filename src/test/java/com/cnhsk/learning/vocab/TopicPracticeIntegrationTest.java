package com.cnhsk.learning.vocab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.entity.Topic;
import com.cnhsk.learning.content.entity.TopicItem;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.content.repository.TopicItemRepository;
import com.cnhsk.learning.content.repository.TopicRepository;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.entity.Attempt;
import com.cnhsk.learning.practice.entity.AttemptItem;
import com.cnhsk.learning.practice.entity.AttemptItemRepository;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.learning.practice.generate.ChoiceItemFactory;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TopicPracticeIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final String[] PINYIN = {"mā", "má", "mǎ", "mà", "bā", "bá", "bǎ", "bà",
            "dā", "dá", "dǎ", "dà", "gā", "gá", "gǎ", "gà", "kā", "ká", "kǎ", "kà"};
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private TopicRepository topics;
    @Autowired private TopicItemRepository topicItems;
    @Autowired private LexemeRepository lexemes;
    @Autowired private AttemptRepository attempts;
    @Autowired private AttemptItemRepository attemptItems;
    @Autowired private TopicPracticeRepository repository;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @MockitoBean private Clock clock;
    @MockitoSpyBean private ChoiceItemFactory choices;
    private long owner;
    private long other;
    private int wordSequence;

    @BeforeAll
    void identitiesThroughAuthApi() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        owner = createUser();
        other = createUser();
    }

    @BeforeEach
    void fixedTime() {
        when(clock.instant()).thenReturn(NOW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"IN_PROGRESS", "ABANDONED", "EXPIRED", "SUBMITTED"})
    void FR025_AT29_prioritizesZeroCorrectAcrossTopicsModesAndAllStates(String state) throws Exception {
        Topic topic = topic(1);
        List<Lexeme> words = words(topic, 11, true);
        learn(owner, topic, words, "SUBMITTED");
        Topic elsewhere = topic(2);
        Lexeme strong = words.get(10);
        attach(elsewhere, strong);
        history(owner, elsewhere, strong, "TOPIC_PRACTICE", state, "MEANING_CHOICE", true, 2);
        history(owner, elsewhere, strong, "TOPIC_PRACTICE", state, "AUDIO_WORD_CHOICE", true, 1);
        Lexeme weak = words.get(0);
        history(other, topic, weak, "TOPIC_PRACTICE", "SUBMITTED", "MEANING_CHOICE", true, 4);
        history(owner, topic, weak, "TOPIC_TEST", "SUBMITTED", "MEANING_CHOICE", true, 4);
        history(owner, topic, weak, "TOPIC_PRACTICE", state, "MEANING_CHOICE", false, 4);
        history(owner, topic, weak, "TOPIC_PRACTICE", state, "AUDIO_WORD_CHOICE", null, 4);
        JsonNode session = start(owner, topic, "RECOGNITION", null, 201);
        assertThat(ids(session)).containsExactlyInAnyOrderElementsOf(words.subList(0, 10).stream().map(Lexeme::getId).toList());
        assertThat(ids(session)).doesNotContain(strong.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"IN_PROGRESS", "ABANDONED", "EXPIRED", "SUBMITTED"})
    void FR025_learnedEvidenceIsGlobalAndCountsStudySkipAndAudioFailure(String state) throws Exception {
        Topic topic = topic(9);
        List<Lexeme> words = words(topic, 4, true);
        Topic elsewhere = topic(1);
        attach(elsewhere, words.get(0));
        learn(owner, elsewhere, words.subList(0, 1), state);
        learn(other, topic, words.subList(1, 4), state);
        JsonNode session = start(owner, topic, "RECOGNITION", null, 201);
        assertThat(ids(session)).containsExactly(words.get(0).getId());
        assertThat(ids(start(other, topic, "RECOGNITION", null, 201))).doesNotContain(words.get(0).getId());
    }

    @Test
    void FR025_tiesAreRandomAndCandidateCountsUseOneAggregateQuery() {
        Topic topic = topic(1);
        List<Lexeme> words = words(topic, 5, false);
        learn(owner, topic, words, "SUBMITTED");
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean enabled = statistics.isStatisticsEnabled();
        try {
            statistics.setStatisticsEnabled(true);
            statistics.clear();
            assertThat(repository.candidates(owner, topic.getId())).extracting(Lexeme::getId)
                    .containsExactlyInAnyOrderElementsOf(words.stream().map(Lexeme::getId).toList());
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        } finally {
            statistics.setStatisticsEnabled(enabled);
        }
        var orders = new HashSet<List<Long>>();
        for (int i = 0; i < 15; i++) {
            orders.add(repository.candidates(owner, topic.getId()).stream().map(Lexeme::getId).toList());
        }
        assertThat(orders.size()).isGreaterThan(1);
    }

    @Test
    void FR025_backfillsPastTheFirstTenWhenPriorityTargetLacksDistractors() throws Exception {
        Topic topic = topic(1);
        List<Lexeme> words = words(topic, 12, false);
        learn(owner, topic, words, "SUBMITTED");
        Lexeme rejected = words.get(0);
        for (Lexeme word : words.subList(1, 12)) {
            history(owner, topic, word, "TOPIC_PRACTICE", "SUBMITTED", "MEANING_CHOICE", true, 1);
        }
        assertThat(repository.candidates(owner, topic.getId()).get(0).getId()).isEqualTo(rejected.getId());
        doReturn(Optional.empty()).when(choices).create(argThat(word -> word.getId().equals(rejected.getId())),
                eq(topic.getId()), eq(ItemType.MEANING_CHOICE), eq(4));
        JsonNode session = start(owner, topic, "RECOGNITION", null, 201);
        assertThat(ids(session)).hasSize(10).doesNotContain(rejected.getId()).doesNotHaveDuplicates();
        verify(choices, times(11)).create(any(Lexeme.class), eq(topic.getId()), eq(ItemType.MEANING_CHOICE), eq(4));
        assertNumbers(session);
    }

    @Test
    void FR025_stopsAtTenAndEachPostCreatesANewSessionWithoutModeMeta() throws Exception {
        Topic topic = topic(1);
        List<Lexeme> words = words(topic, 14, false);
        learn(owner, topic, words, "SUBMITTED");
        JsonNode first = start(owner, topic, "RECOGNITION", null, 201);
        assertThat(ids(first)).hasSize(10).doesNotHaveDuplicates();
        verify(choices, times(10)).create(any(Lexeme.class), eq(topic.getId()), eq(ItemType.MEANING_CHOICE), eq(4));
        JsonNode second = start(owner, topic, "RECOGNITION", null, 201);
        assertThat(first.path("sessionId")).isNotEqualTo(second.path("sessionId"));
        assertThat(first.path("source").asText()).isEqualTo("TOPIC_PRACTICE");
        assertThat(first.path("feedback").asText()).isEqualTo("IMMEDIATE");
        assertThat(first.path("expiresAt").asText()).isEqualTo(NOW.plusSeconds(7200).toString());
        assertThat(first.path("meta").has("mode")).isFalse();
        assertThat(sessionCount(owner, topic)).isEqualTo(2);
        assertNumbers(first);
    }

    @Test
    void FR026_AT30_recognitionHasFourMeaningOptionsAndNoPinyinOrAnswersInViews() throws Exception {
        Topic topic = topic(1);
        List<Lexeme> words = words(topic, 4, true);
        learn(owner, topic, words, "SUBMITTED");
        JsonNode session = start(owner, topic, "RECOGNITION", null, 201);
        for (JsonNode item : session.path("items")) {
            assertThat(item.path("type").asText()).isEqualTo("MEANING_CHOICE");
            assertThat(item.path("skill").asText()).isEqualTo("VOCAB");
            assertThat(item.path("prompt").fieldNames()).toIterable().containsExactly("text");
            assertThat(item.path("options")).hasSize(4);
            var keys = new HashSet<String>();
            var texts = new HashSet<String>();
            item.path("options").forEach(option -> { keys.add(option.path("key").asText()); texts.add(option.path("text").asText()); });
            assertThat(keys).containsExactlyInAnyOrder("A", "B", "C", "D");
            assertThat(texts).hasSize(4);
            assertRules(item, false);
        }
        assertSafe(session);
        JsonNode restored = mapper.readTree(mvc.perform(get("/api/practice/sessions/{id}", session.path("sessionId").asLong())
                        .header("Authorization", bearer(owner, "LEARNER"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertSafe(restored);
    }

    @Test
    void FR026_factoryExcludesSynonymsAndMarksDistractorsOutsideTheTopic() throws Exception {
        Topic topic = topic(1);
        List<Lexeme> words = words(topic, 2, false);
        words.get(1).setMeaningVi(words.get(0).getMeaningVi());
        words.get(1).setMeaningNorm(words.get(0).getMeaningNorm());
        lexemes.saveAndFlush(words.get(1));
        words(topic(1), 4, false);
        learn(owner, topic, words.subList(0, 1), "SUBMITTED");
        JsonNode session = start(owner, topic, "RECOGNITION", null, 201);
        assertThat(session.path("meta").path("mixedDistractors").asBoolean()).isTrue();
        JsonNode options = session.path("items").get(0).path("options");
        long matching = 0;
        for (JsonNode option : options) if (option.path("text").asText().equals(words.get(0).getMeaningVi())) matching++;
        assertThat(matching).isEqualTo(1);
        assertThat(session.path("meta").fieldNames()).toIterable().containsExactly("mixedDistractors");
    }

    @Test
    void FR027_listeningFiltersTargetsWithAudioAndDoesNotRevealTargetText() throws Exception {
        Topic topic = topic(1);
        List<Lexeme> audible = words(topic, 4, true);
        List<Lexeme> silent = words(topic, 1, false);
        var all = new ArrayList<>(audible);
        all.addAll(silent);
        learn(owner, topic, all, "SUBMITTED");
        JsonNode session = start(owner, topic, "LISTENING", null, 201);
        assertThat(ids(session)).containsExactlyInAnyOrderElementsOf(audible.stream().map(Lexeme::getId).toList());
        for (JsonNode item : session.path("items")) {
            assertThat(item.path("type").asText()).isEqualTo("AUDIO_WORD_CHOICE");
            assertThat(item.path("skill").asText()).isEqualTo("LISTENING");
            assertThat(item.path("prompt").fieldNames()).toIterable().containsExactly("audioUrl");
            assertThat(item.path("options")).hasSize(4);
            assertRules(item, true);
        }
        assertSafe(session);
    }

    @Test
    void FR027_audioOptionsNeverContainHomophones() throws Exception {
        Topic topic = topic(1);
        List<Lexeme> words = words(topic, 5, true);
        words.get(0).setPinyin("shì");
        words.get(1).setPinyin("shì");
        lexemes.saveAndFlush(words.get(0));
        lexemes.saveAndFlush(words.get(1));
        learn(owner, topic, words.subList(0, 1), "SUBMITTED");
        JsonNode options = start(owner, topic, "LISTENING", List.of(words.get(0).getId()), 201).path("items").get(0).path("options");
        var texts = new HashSet<String>();
        options.forEach(option -> texts.add(option.path("text").asText()));
        assertThat(texts).contains(words.get(0).getText()).doesNotContain(words.get(1).getText());
    }

    @Test
    void FR028_AT31_noAudioAfterFocusFailsEvenWhenOtherLearnedWordsHaveAudio() throws Exception {
        Topic topic = topic(1);
        List<Lexeme> silent = words(topic, 1, false);
        List<Lexeme> audible = words(topic, 4, true);
        learn(owner, topic, silent, "SUBMITTED");
        assertError(start(owner, topic, "LISTENING", null, 422), "NO_AUDIO_WORDS");
        learn(owner, topic, audible, "SUBMITTED");
        assertError(start(owner, topic, "LISTENING", List.of(silent.get(0).getId()), 422), "NO_AUDIO_WORDS");
        assertThat(sessionCount(owner, topic)).isZero();
    }

    @Test
    void FR028_missingTopicThenNoLearnedThenInvalidFocusHaveTheApprovedPrecedence() throws Exception {
        JsonNode missing = request(owner, Long.toString(Long.MAX_VALUE), "{\"mode\":\"LISTENING\",\"focusLexemeIds\":[1]}", 404);
        assertError(missing, "TOPIC_NOT_FOUND");
        Topic empty = topic(1);
        assertError(start(owner, empty, "RECOGNITION", null, 404), "TOPIC_NOT_FOUND");
        Topic topic = topic(1);
        List<Lexeme> words = words(topic, 4, false);
        assertError(start(owner, topic, "LISTENING", List.of(Long.MAX_VALUE), 422), "NO_LEARNED_WORDS");
        learn(owner, topic, words.subList(0, 1), "SUBMITTED");
        assertError(start(owner, topic, "LISTENING", List.of(words.get(1).getId()), 422), "INVALID_FOCUS_WORDS");
        assertError(start(owner, topic, "LISTENING", List.of(words.get(0).getId()), 422), "NO_AUDIO_WORDS");
    }

    @Test
    void FR028_noGeneratedQuestionReturnsNotEnoughWordsAndCreatesNoAttempt() throws Exception {
        Topic topic = topic(1);
        List<Lexeme> words = words(topic, 3, false);
        learn(owner, topic, words, "SUBMITTED");
        doReturn(Optional.empty()).when(choices).create(any(Lexeme.class), eq(topic.getId()), eq(ItemType.MEANING_CHOICE), eq(4));
        assertError(start(owner, topic, "RECOGNITION", null, 422), "NOT_ENOUGH_WORDS");
        assertThat(sessionCount(owner, topic)).isZero();
        verify(choices, times(3)).create(any(Lexeme.class), eq(topic.getId()), eq(ItemType.MEANING_CHOICE), eq(4));
    }

    @Test
    void FR029_AT32_rejectsEveryUnknownOtherTopicOrUnlearnedFocusBeforeAudioFiltering() throws Exception {
        Topic topic = topic(1);
        List<Lexeme> words = words(topic, 4, false);
        learn(owner, topic, words.subList(0, 1), "SUBMITTED");
        Topic elsewhere = topic(1);
        Lexeme outsider = words(elsewhere, 1, true).get(0);
        learn(owner, elsewhere, List.of(outsider), "SUBMITTED");
        for (long invalid : List.of(Long.MAX_VALUE, words.get(1).getId(), outsider.getId())) {
            assertError(start(owner, topic, "LISTENING", List.of(words.get(0).getId(), invalid), 422), "INVALID_FOCUS_WORDS");
        }
        assertThat(sessionCount(owner, topic)).isZero();
    }

    @Test
    void FR029_deduplicatesFocusTargetsWhileDistractorsCanUseOtherUnlearnedWords() throws Exception {
        Topic topic = topic(1);
        List<Lexeme> words = words(topic, 4, false);
        learn(owner, topic, words.subList(0, 1), "SUBMITTED");
        List<Long> repeated = java.util.Collections.nCopies(20, words.get(0).getId());
        JsonNode session = start(owner, topic, "RECOGNITION", repeated, 201);
        assertThat(ids(session)).containsExactly(words.get(0).getId());
        assertThat(session.path("items").get(0).path("options")).hasSize(4);
        assertThat(session.path("meta").has("mixedDistractors")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "{", "[]", "{\"mode\":\"RECOGNITION\"} garbage", "{\"mode\":null}", "{\"mode\":\"\"}",
            "{\"mode\":\"recognition\"}", "{\"mode\":\"OTHER\"}", "{\"mode\":0}",
            "{\"mode\":\"RECOGNITION\",\"focusLexemeIds\":[]}",
            "{\"mode\":\"RECOGNITION\",\"focusLexemeIds\":null}",
            "{\"mode\":\"RECOGNITION\",\"focusLexemeIds\":[null]}",
            "{\"mode\":\"RECOGNITION\",\"focusLexemeIds\":[0]}",
            "{\"mode\":\"RECOGNITION\",\"focusLexemeIds\":[-1]}",
            "{\"mode\":\"RECOGNITION\",\"focusLexemeIds\":[\"1\"]}",
            "{\"mode\":\"RECOGNITION\",\"focusLexemeIds\":[1.5]}",
            "{\"mode\":\"RECOGNITION\",\"focusLexemeIds\":[true]}",
            "{\"mode\":\"RECOGNITION\",\"focusLexemeIds\":[9223372036854775808]}",
            "{\"mode\":\"RECOGNITION\",\"focusLexemeIds\":{}}"})
    void FR025_invalidBodyReturns422BeforeTopicLookup(String body) throws Exception {
        assertError(request(owner, Long.toString(Long.MAX_VALUE), body, 422), "VALIDATION_FAILED");
    }

    @Test
    void FR025_moreThanTwentyFocusIdsIsValidationFailureBeforeDeduplication() throws Exception {
        String body = mapper.writeValueAsString(Map.of("mode", "RECOGNITION", "focusLexemeIds", java.util.Collections.nCopies(21, 1L)));
        assertError(request(owner, "abc", body, 422), "VALIDATION_FAILED");
        assertError(request(owner, "1", body, 422), "VALIDATION_FAILED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "999999999999999999999"})
    void FR025_invalidTopicIdReturns422(String id) throws Exception {
        assertError(request(owner, id, "{\"mode\":\"RECOGNITION\"}", 422), "VALIDATION_FAILED");
    }

    @ParameterizedTest
    @ValueSource(ints = {799, 800})
    void FR030_serverGradesThenFinishesFromEvidenceAtTheDurationBoundary(int duration) throws Exception {
        Topic topic = topic(1);
        List<Lexeme> words = words(topic, 4, false);
        learn(owner, topic, words.subList(0, 1), "SUBMITTED");
        long id = start(owner, topic, "RECOGNITION", List.of(words.get(0).getId()), 201).path("sessionId").asLong();
        String key = attempts.findById(id).orElseThrow().getServedItems().path("items").get(0).path("answer").asText();
        mvc.perform(post("/api/practice/sessions/{id}/answers", id).header("Authorization", bearer(owner, "LEARNER"))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("no", 1,
                                "answer", key, "durationMs", duration, "score", 999999))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.correctAnswer").value(key));
        assertThat(attemptItems.findByAttemptIdOrderByOrderIndexAsc(id).get(0).getMeta().getOrDefault("suspicious", false))
                .isEqualTo(duration < 800);
        mvc.perform(post("/api/practice/sessions/{id}/finish", id).header("Authorization", bearer(owner, "LEARNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(1)).andExpect(jsonPath("$.maxScore").value(1));
    }

    @Test
    void FR027_audioReplaySkipAndFailureUseExistingEngineRules() throws Exception {
        Topic topic = topic(1);
        List<Lexeme> words = words(topic, 4, true);
        learn(owner, topic, words, "SUBMITTED");
        long id = start(owner, topic, "LISTENING", words.subList(0, 3).stream().map(Lexeme::getId).toList(), 201)
                .path("sessionId").asLong();
        JsonNode snapshot = attempts.findById(id).orElseThrow().getServedItems().path("items");
        String key = snapshot.get(0).path("answer").asText();
        mvc.perform(post("/api/practice/sessions/{id}/answers", id).header("Authorization", bearer(owner, "LEARNER"))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("no", 1,
                                "answer", key, "durationMs", 1000, "replayCount", 4))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_code").value("TOO_MANY_REPLAYS"));
        mvc.perform(post("/api/practice/sessions/{id}/answers", id).header("Authorization", bearer(owner, "LEARNER"))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("no", 1,
                                "answer", key, "durationMs", 1000, "replayCount", 3))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.correct").value(true));
        for (int no : List.of(2, 3)) {
            mvc.perform(post("/api/practice/sessions/{id}/answers", id).header("Authorization", bearer(owner, "LEARNER"))
                            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("no", no,
                                    "durationMs", 1000, no == 2 ? "skipped" : "audioFailed", true))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.counted").value(false));
        }
        mvc.perform(post("/api/practice/sessions/{id}/finish", id).header("Authorization", bearer(owner, "LEARNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(1)).andExpect(jsonPath("$.maxScore").value(1));
    }

    @Test
    void FR025_onlyLearnersCanStartAndCookieWritesRequireCsrf() throws Exception {
        Topic topic = topic(1);
        List<Lexeme> words = words(topic, 4, false);
        learn(owner, topic, words, "SUBMITTED");
        String path = "/api/topics/" + topic.getId() + "/practice-sessions";
        String body = "{\"mode\":\"RECOGNITION\"}";
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        for (String role : List.of("ADMIN", "TEACHER", "CONTENT_EDITOR", "MODERATOR", "ACCOUNTANT")) {
            mvc.perform(post(path).header("Authorization", bearer(owner, role)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        }
        Cookie auth = new Cookie("access_token", token(owner, "LEARNER"));
        mvc.perform(post(path).cookie(auth).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        Cookie csrf = mvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent()).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        mvc.perform(post(path).cookie(auth, csrf).header("X-XSRF-TOKEN", "wrong").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post(path).cookie(auth, csrf).header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        assertThat(start(owner, topic, "RECOGNITION", null, 201).path("sessionId").asLong()).isPositive();
    }

    @Test
    void FR025_clientUserIdCannotUseAnotherLearnersEvidence() throws Exception {
        Topic topic = topic(1);
        List<Lexeme> words = words(topic, 4, false);
        learn(other, topic, words, "SUBMITTED");
        String body = mapper.writeValueAsString(Map.of("mode", "RECOGNITION", "userId", other));
        assertError(request(owner, topic.getId().toString(), body, 422), "NO_LEARNED_WORDS");
    }

    @Test
    void FR025_newSessionsUseTheExistingHourlyRateLimit() throws Exception {
        long limited = createUser();
        Topic topic = topic(1);
        List<Lexeme> words = words(topic, 4, false);
        learn(limited, topic, words, "SUBMITTED");
        String key = "practice:start:" + limited + ":2026103100";
        redis.opsForValue().set(key, "120", java.time.Duration.ofMinutes(5));
        JsonNode response = start(limited, topic, "RECOGNITION", null, 429);
        assertError(response, "RATE_LIMITED");
        assertThat(response.path("retryAfterSeconds").asLong()).isPositive();
        assertThat(sessionCount(limited, topic)).isZero();
    }

    private long createUser() throws Exception {
        String password = "Aa1" + UUID.randomUUID();
        return mapper.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.test",
                                "password", password, "confirmPassword", password, "displayName", "Practice test user",
                                "acceptTerms", true)))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).path("userId").asLong();
    }

    private Topic topic(int level) {
        return topics.saveAndFlush(new Topic("T11-" + UUID.randomUUID(), "Chủ đề luyện thử", (short) level));
    }

    private List<Lexeme> words(Topic topic, int count, boolean audio) {
        var result = new ArrayList<Lexeme>();
        for (int i = 0; i < count; i++) {
            String unique = UUID.randomUUID().toString();
            Lexeme word = new Lexeme(LexemeKind.WORD, "词" + unique, "nghĩa " + unique);
            word.setMeaningNorm(word.getMeaningVi());
            word.setPinyin(PINYIN[wordSequence++ % PINYIN.length]);
            word.setHskLevel(topic.getHskLevel());
            if (audio) word.setAudioUrl("https://example.test/audio-" + unique + ".mp3");
            result.add(lexemes.saveAndFlush(word));
            attach(topic, word);
        }
        return result;
    }

    private void attach(Topic topic, Lexeme word) {
        topicItems.saveAndFlush(new TopicItem(topic.getId(), word.getId()));
    }

    private Attempt evidence(long user, Topic topic, String source, String state) {
        Attempt attempt = new Attempt(user, "TOPIC_TEST".equals(source) ? "TOPIC_TEST" : "PRACTICE", source,
                topic.getId(), NOW.minusSeconds(100), NOW.plusSeconds(7200), mapper.createObjectNode());
        attempt.setStatus(state);
        if ("SUBMITTED".equals(state)) attempt.setSubmittedAt(NOW.minusSeconds(10));
        return attempts.saveAndFlush(attempt);
    }

    private void learn(long user, Topic topic, List<Lexeme> words, String state) {
        Attempt attempt = evidence(user, topic, "TOPIC_STUDY", state);
        for (int i = 0; i < words.size(); i++) {
            AttemptItem item = new AttemptItem(attempt.getId(), i + 1, "STUDY_CARD", "VOCAB");
            item.setLexemeId(words.get(i).getId());
            item.setDurationMs(1000);
            item.setMeta(Map.of(words.get(i).getAudioUrl() != null && i % 2 == 0 ? "audioFailed" : "skipped", true));
            attemptItems.saveAndFlush(item);
        }
    }

    private void history(long user, Topic topic, Lexeme word, String source, String state,
            String type, Boolean correct, int count) {
        Attempt attempt = evidence(user, topic, source, state);
        for (int i = 0; i < count; i++) {
            AttemptItem item = new AttemptItem(attempt.getId(), i + 1, type,
                    "AUDIO_WORD_CHOICE".equals(type) ? "LISTENING" : "VOCAB");
            item.setLexemeId(word.getId());
            item.setCorrect(correct);
            item.setAnswer("A");
            item.setDurationMs(1000);
            if (correct == null) item.setMeta(Map.of("audioFailed", true));
            attemptItems.saveAndFlush(item);
        }
    }

    private JsonNode start(long user, Topic topic, String mode, List<Long> focus, int status) throws Exception {
        var body = mapper.createObjectNode().put("mode", mode);
        if (focus != null) body.set("focusLexemeIds", mapper.valueToTree(focus));
        return request(user, topic.getId().toString(), body.toString(), status);
    }

    private JsonNode request(long user, String topicId, String body, int expectedStatus) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/topics/{id}/practice-sessions", topicId)
                        .header("Authorization", bearer(user, "LEARNER")).header("X-Request-Id", "practice-start")
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString());
    }

    private static void assertError(JsonNode response, String code) {
        assertThat(response.path("error_code").asText()).isEqualTo(code);
        assertThat(response.path("request_id").asText()).isEqualTo("practice-start");
        assertThat(response.path("message").asText()).isNotEmpty();
    }

    private static List<Long> ids(JsonNode session) {
        var ids = new ArrayList<Long>();
        session.path("items").forEach(item -> ids.add(item.path("lexemeId").asLong()));
        return ids;
    }

    private static void assertRules(JsonNode item, boolean audio) {
        assertThat(item.path("rules").path("minDurationMs").asInt()).isEqualTo(800);
        assertThat(item.path("rules").path("maxReplays").asInt()).isEqualTo(3);
        assertThat(item.path("rules").path("hasAudio").asBoolean()).isEqualTo(audio);
    }

    private static void assertNumbers(JsonNode session) {
        for (int i = 0; i < session.path("items").size(); i++) assertThat(session.path("items").get(i).path("no").asInt()).isEqualTo(i + 1);
    }

    private static void assertSafe(JsonNode session) {
        assertThat(session.path("items").toString()).doesNotContain("\"answer\":", "\"accept\":", "answerKey", "correctAnswer", "email", "userId");
        assertThat(session.path("meta").toString()).doesNotContain("answer", "accept", "mode", "email", "userId");
    }

    private long sessionCount(long user, Topic topic) {
        return jdbc.queryForObject("SELECT count(*) FROM learning.attempts WHERE user_id = ? "
                + "AND ref_type = 'TOPIC_PRACTICE' AND ref_id = ?", Long.class, user, topic.getId());
    }

    private String bearer(long user, String role) {
        return "Bearer " + token(user, role);
    }

    private String token(long user, String role) {
        return jwt.issueAccessToken(user, List.of(role), false, UUID.randomUUID());
    }
}
