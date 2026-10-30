package com.cnhsk.learning.vocab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.entity.Topic;
import com.cnhsk.learning.content.entity.TopicItem;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.content.repository.TopicItemRepository;
import com.cnhsk.learning.content.repository.TopicRepository;
import com.cnhsk.learning.practice.entity.Attempt;
import com.cnhsk.learning.practice.entity.AttemptItem;
import com.cnhsk.learning.practice.entity.AttemptItemRepository;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TopicQueryIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");
    @Autowired private org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private TopicRepository topics;
    @Autowired private TopicItemRepository topicItems;
    @Autowired private LexemeRepository lexemes;
    @Autowired private AttemptRepository attempts;
    @Autowired private AttemptItemRepository attemptItems;
    @Autowired private TopicQueryService service;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @MockitoBean private Clock clock;
    private long owner;
    private long other;

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
    @ValueSource(strings = {"0", "10", "-1", "text", "1.5", "", "999999999999999999999"})
    void FR013_AT22_invalidHskLevelReturnsStandardValidation(String level) throws Exception {
        mvc.perform(get("/api/topics").param("hskLevel", level).header("Authorization", bearer(owner, "LEARNER"))
                        .header("X-Request-Id", "topic-validation"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("hskLevel"))
                .andExpect(jsonPath("$.request_id").value("topic-validation"))
                .andExpect(header().string("X-Request-Id", "topic-validation"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "999999999999999999999"})
    void FR018_invalidIdReturns422InsteadOf500(String id) throws Exception {
        mvc.perform(get("/api/topics/{id}", id).header("Authorization", bearer(owner, "LEARNER")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("id"))
                .andExpect(jsonPath("$.request_id").isNotEmpty());
    }

    @Test
    void FR013_AT22_filtersLevelsOneThroughNineOmitsEmptyTopicsAndSortsStably() throws Exception {
        Topic empty = topic(1, 0);
        Topic second = topicWithWords(1, 10, 1);
        Topic first = topicWithWords(1, 1, 1);
        Topic tie = topicWithWords(1, 1, 1);
        Topic advanced = topicWithWords(9, 0, 1);
        List<Long> ownIds = List.of(empty.getId(), second.getId(), first.getId(), tie.getId(), advanced.getId());
        JsonNode all = list(owner, null);
        List<Long> ordered = new ArrayList<>();
        all.forEach(row -> { if (ownIds.contains(row.path("id").asLong())) ordered.add(row.path("id").asLong()); });
        assertThat(ordered).containsExactly(first.getId(), tie.getId(), second.getId(), advanced.getId());
        assertThat(find(list(owner, "1"), first.getId()).path("hskLevel").asInt()).isEqualTo(1);
        assertThat(find(list(owner, "9"), advanced.getId()).path("hskLevel").asInt()).isEqualTo(9);
        assertThat(list(owner, "1")).allSatisfy(row -> assertThat(row.path("hskLevel").asInt()).isEqualTo(1));
    }

    @Test
    void FR014_percentUsesIntegerDivisionAndRepeatedCardsDoNotMultiplyCounts() throws Exception {
        Topic topic = topicWithWords(1, 0, 3);
        Lexeme word = words(topic).get(0);
        learn(owner, topic, List.of(word), "IN_PROGRESS");
        learn(owner, topic, List.of(word), "SUBMITTED");
        testAttempt(owner, topic, 1, 3, NOW.minusSeconds(100), NOW.minusSeconds(60));
        JsonNode row = find(list(owner, null), topic.getId());
        assertThat(row.path("totalWords").asLong()).isEqualTo(3);
        assertThat(row.path("learnedWords").asLong()).isEqualTo(1);
        assertThat(row.path("percent").asInt()).isEqualTo(33);
        assertThat(row.path("bestTestPercent").asInt()).isEqualTo(33);
    }

    @ParameterizedTest
    @ValueSource(strings = {"IN_PROGRESS", "ABANDONED", "EXPIRED", "SUBMITTED"})
    void FR015_AT23_sharedLexemeCountsAcrossTopicsAndAllAttemptStates(String state) throws Exception {
        Topic a = topic(1, 0);
        Topic b = topic(2, 0);
        Lexeme shared = word(true);
        attach(a, shared, 0);
        attach(b, shared, 0);
        learn(owner, a, List.of(shared), state);
        JsonNode rows = list(owner, null);
        assertThat(find(rows, a.getId()).path("learnedWords").asLong()).isEqualTo(1);
        assertThat(find(rows, b.getId()).path("learnedWords").asLong()).isEqualTo(1);
        assertThat(detail(owner, b).path("words").get(0).path("learned").asBoolean()).isTrue();
    }

    @Test
    void FR015_otherLearnersCannotSeeProgressEvenWhenUserIdIsSupplied() throws Exception {
        Topic topic = topicWithWords(1, 0, 5);
        learn(owner, topic, words(topic), "IN_PROGRESS");
        testAttempt(owner, topic, 5, 5, NOW.minusSeconds(100), NOW.minusSeconds(60));
        JsonNode row = find(list(other, null), topic.getId());
        assertThat(row.path("learnedWords").asLong()).isZero();
        assertThat(row.path("status").asText()).isEqualTo("NOT_STARTED");
        assertThat(row.path("bestTestPercent").isNull()).isTrue();
        JsonNode detail = mapper.readTree(mvc.perform(get("/api/topics/{id}", topic.getId())
                        .param("userId", Long.toString(owner)).header("Authorization", bearer(other, "LEARNER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(detail.path("learnedWords").asLong()).isZero();
        assertThat(detail.path("words")).allSatisfy(word -> assertThat(word.path("learned").asBoolean()).isFalse());
    }

    @Test
    void FR016_FR017_AT24_completionAndBestScoreSurviveALowerLaterScore() throws Exception {
        Topic topic = topicWithWords(1, 0, 5);
        testAttempt(owner, topic, 19, 20, NOW.minusSeconds(900), NOW.minusSeconds(800));
        testAttempt(owner, topic, 14, 20, NOW.minusSeconds(100), NOW.minusSeconds(60));
        JsonNode row = find(list(owner, null), topic.getId());
        assertThat(row.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(row.path("bestTestPercent").asInt()).isEqualTo(95);
        JsonNode detail = detail(owner, topic);
        assertThat(detail.path("bestTestPercent").asInt()).isEqualTo(95);
        assertThat(detail.path("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void FR016_activityWithoutLearnedWordsIsInProgressButOtherSourcesDoNotCount() throws Exception {
        Topic topic = topicWithWords(1, 0, 1);
        attempt(owner, "GRAMMAR_LESSON", topic, "IN_PROGRESS", NOW, NOW.plusSeconds(100));
        assertThat(detail(owner, topic).path("status").asText()).isEqualTo("NOT_STARTED");
        attempt(owner, "TOPIC_PRACTICE", topic, "ABANDONED", NOW, NOW.plusSeconds(100));
        assertThat(detail(owner, topic).path("status").asText()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void FR017_ignoresZeroDenominatorsAndUnsubmittedTests() throws Exception {
        Topic topic = topicWithWords(1, 0, 4);
        testAttempt(owner, topic, 0, 0, NOW.minusSeconds(900), NOW.minusSeconds(800));
        assertThat(detail(owner, topic).path("bestTestPercent").isNull()).isTrue();
        testAttempt(owner, topic, 2, 3, NOW.minusSeconds(700), NOW.minusSeconds(600));
        Attempt open = attempt(owner, "TOPIC_TEST", topic, "IN_PROGRESS", NOW, NOW.plusSeconds(100));
        open.setScore(3L);
        open.setMaxScore(3L);
        attempts.saveAndFlush(open);
        assertThat(detail(owner, topic).path("bestTestPercent").asInt()).isEqualTo(66);
    }

    @Test
    void FR018_wordsUseTopicOrderAndExposeOnlyTheContractFields() throws Exception {
        Topic topic = topic(1, 0);
        Lexeme first = word(true);
        Lexeme second = word(false);
        attach(topic, second, 9);
        attach(topic, first, 1);
        learn(owner, topic, List.of(first), "IN_PROGRESS");
        JsonNode detail = detail(owner, topic);
        assertThat(detail.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "id", "code", "name", "hskLevel", "totalWords", "learnedWords", "percent", "status",
                "bestTestPercent", "words", "actions");
        JsonNode words = detail.path("words");
        assertThat(words.get(0).path("lexemeId").asLong()).isEqualTo(first.getId());
        assertThat(words.get(1).path("lexemeId").asLong()).isEqualTo(second.getId());
        assertThat(words.get(0).path("pinyin").asText()).isEqualTo(first.getPinyin());
        assertThat(words.get(0).path("sinoViet").asText()).isEqualTo(first.getSinoViet());
        assertThat(words.get(0).path("meaning").asText()).isEqualTo(first.getMeaningVi());
        assertThat(words.get(0).path("learned").asBoolean()).isTrue();
        assertThat(words.get(1).path("audioUrl").isNull()).isTrue();
        assertThat(words.get(1).path("learned").asBoolean()).isFalse();
        assertThat(detail.toString()).doesNotContain("userId", "email", "servedItems", "draftAnswers", "password");
    }

    @Test
    void FR018_missingAndEmptyTopicsReturn404WithTheStandardError() throws Exception {
        Topic empty = topic(1, 0);
        for (long id : List.of(empty.getId(), Long.MAX_VALUE)) {
            mvc.perform(get("/api/topics/{id}", id).header("Authorization", bearer(owner, "LEARNER")))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code").value("TOPIC_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").isNotEmpty()).andExpect(jsonPath("$.request_id").isNotEmpty());
        }
    }

    @Test
    void FR018_studyRecognitionAndListeningDependOnLearnedWordsWithAudio() throws Exception {
        Topic topic = topicWithWords(1, 0, 5);
        List<Lexeme> words = words(topic);
        assertActions(topic, true, false, false, false, "NOT_ENOUGH_LEARNED");
        learn(owner, topic, List.of(words.get(0)), "IN_PROGRESS");
        assertActions(topic, true, true, false, false, "NOT_ENOUGH_LEARNED");
        words.get(1).setAudioUrl("https://example.test/voice.mp3");
        lexemes.saveAndFlush(words.get(1));
        assertActions(topic, true, true, false, false, "NOT_ENOUGH_LEARNED");
        learn(owner, topic, List.of(words.get(1)), "IN_PROGRESS");
        assertActions(topic, true, true, true, false, "NOT_ENOUGH_LEARNED");
        learn(owner, topic, words.subList(2, 5), "IN_PROGRESS");
        assertActions(topic, false, true, true, true, null);
    }

    @Test
    void FR018_tooFewWordsTakesPrecedenceOverLearningAndCooldown() throws Exception {
        Topic topic = topicWithWords(1, 0, 3);
        testAttempt(owner, topic, 0, 3, NOW.minusSeconds(100), NOW.minusSeconds(60));
        assertActions(topic, true, false, false, false, "TOO_FEW_WORDS");
    }

    @Test
    void FR018_eightyPercentAndFourWordsAreInclusiveUsingIntegerArithmetic() throws Exception {
        Topic topic = topicWithWords(1, 0, 5);
        List<Lexeme> words = words(topic);
        learn(owner, topic, words.subList(0, 3), "IN_PROGRESS");
        assertActions(topic, true, true, false, false, "NOT_ENOUGH_LEARNED");
        assertThat(detail(owner, topic).path("actions").path("requiredWords").asLong()).isEqualTo(4);
        learn(owner, topic, List.of(words.get(3)), "IN_PROGRESS");
        assertActions(topic, true, true, false, true, null);
        Topic four = topicWithWords(1, 0, 4);
        learn(owner, four, words(four), "IN_PROGRESS");
        assertActions(four, false, true, false, true, null);
    }

    @Test
    void FR018_notEnoughLearnedTakesPrecedenceOverRecentFailedTest() throws Exception {
        Topic topic = topicWithWords(1, 0, 5);
        testAttempt(owner, topic, 0, 5, NOW.minusSeconds(100), NOW.minusSeconds(60));
        assertActions(topic, true, false, false, false, "NOT_ENOUGH_LEARNED");
    }

    @Test
    void FR018_retryDelayHasAnExactTenMinuteBoundaryFromClock() throws Exception {
        Topic topic = topicWithWords(1, 0, 5);
        learn(owner, topic, words(topic), "IN_PROGRESS");
        Instant submitted = NOW.minusSeconds(599);
        testAttempt(owner, topic, 3, 5, NOW.minusSeconds(700), submitted);
        assertActions(topic, false, true, false, false, "RETRY_TOO_SOON");
        assertThat(detail(owner, topic).path("actions").path("retryAvailableAt").asText())
                .isEqualTo(submitted.plusSeconds(600).toString());
        when(clock.instant()).thenReturn(NOW.plusSeconds(1));
        assertActions(topic, false, true, false, true, null);
    }

    @Test
    void FR018_passingMostRecentTestAllowsImmediateRetry() throws Exception {
        Topic topic = topicWithWords(1, 0, 5);
        learn(owner, topic, words(topic), "IN_PROGRESS");
        testAttempt(owner, topic, 1, 5, NOW.minusSeconds(200), NOW.minusSeconds(100));
        testAttempt(owner, topic, 5, 5, NOW.minusSeconds(50), NOW.minusSeconds(10));
        assertActions(topic, false, true, false, true, null);
    }

    @Test
    void FR018_activeTestOverridesAllGatesAndExpirationBoundaryIsInclusive() throws Exception {
        Topic topic = topicWithWords(1, 0, 1);
        testAttempt(owner, topic, 0, 1, NOW.minusSeconds(100), NOW.minusSeconds(60));
        attempt(owner, "TOPIC_TEST", topic, "IN_PROGRESS", NOW, NOW);
        assertActions(topic, true, false, false, true, null);
        when(clock.instant()).thenReturn(NOW.plusNanos(1));
        assertActions(topic, true, false, false, false, "TOO_FEW_WORDS");
    }

    @Test
    void FR013_FR018_onlyLearnersCanCallBothReadsWithCookieOrBearer() throws Exception {
        Topic topic = topicWithWords(1, 0, 1);
        for (String path : List.of("/api/topics", "/api/topics/" + topic.getId())) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
            for (String role : List.of("ADMIN", "TEACHER", "CONTENT_EDITOR", "MODERATOR", "ACCOUNTANT")) {
                mvc.perform(get(path).header("Authorization", bearer(owner, role)))
                        .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
            }
            mvc.perform(get(path).cookie(new jakarta.servlet.http.Cookie("access_token", token(owner, "LEARNER"))))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void FR015_listAlwaysUsesOneAggregateQueryWithoutCacheOrNPlusOne() {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean previouslyEnabled = statistics.isStatisticsEnabled();
        try {
            Topic first = topicWithWords(1, 0, 1);
            learn(owner, first, words(first), "IN_PROGRESS");
            entityManager.flush();
            entityManager.clear();
            statistics.setStatisticsEnabled(true);
            statistics.clear();
            service.list(owner, null);
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
            for (int i = 0; i < 199; i++) topicWithWords(1, i, 1);
            for (int i = 1; i < 10; i++) attach(first, word(false), i);
            List<Lexeme> benchmarkWords = words(first);
            for (int i = 0; i < 500; i++) {
                learn(owner, first, benchmarkWords.subList(0, i == 499 ? 9 : 10), "SUBMITTED");
            }
            entityManager.flush();
            entityManager.clear();
            statistics.clear();
            assertThat(service.list(owner, null)).extracting(TopicQueryService.TopicSummary::id).contains(first.getId());
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
            statistics.clear();
            long[] durations = new long[20];
            for (int i = 0; i < durations.length; i++) {
                long started = System.nanoTime();
                service.list(owner, null);
                durations[i] = System.nanoTime() - started;
            }
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(durations.length);
            java.util.Arrays.sort(durations);
            System.out.println("T09 list aggregate: topics>=200, answers=5000, queries=20, local p95 ms="
                    + durations[18] / 1_000_000);
        } finally {
            statistics.setStatisticsEnabled(previouslyEnabled);
        }
    }

    private long createUser() throws Exception {
        String password = "Aa1" + UUID.randomUUID();
        String body = mapper.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.test", "password", password,
                "confirmPassword", password, "displayName", "Topic test user", "acceptTerms", true));
        return mapper.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("userId").asLong();
    }

    private Topic topic(int level, int order) {
        Topic topic = new Topic("T09-" + UUID.randomUUID(), "Chủ đề thử nghiệm", (short) level);
        topic.setOrderIndex(order);
        return topics.saveAndFlush(topic);
    }

    private Topic topicWithWords(int level, int order, int count) {
        Topic topic = topic(level, order);
        for (int i = 0; i < count; i++) attach(topic, word(false), i);
        return topic;
    }

    private Lexeme word(boolean audio) {
        Lexeme word = new Lexeme(LexemeKind.WORD, "词" + UUID.randomUUID(), "nghĩa thử nghiệm");
        word.setHskLevel((short) 1);
        word.setPinyin("cí");
        word.setSinoViet("từ");
        if (audio) word.setAudioUrl("https://example.test/voice.mp3");
        return lexemes.saveAndFlush(word);
    }

    private void attach(Topic topic, Lexeme word, int order) {
        TopicItem item = new TopicItem(topic.getId(), word.getId());
        item.setOrderIndex(order);
        topicItems.saveAndFlush(item);
    }

    private List<Lexeme> words(Topic topic) {
        return topicItems.findAll().stream().filter(item -> item.getTopicId().equals(topic.getId()))
                .sorted(java.util.Comparator.comparingInt(TopicItem::getOrderIndex))
                .map(item -> lexemes.findById(item.getLexemeId()).orElseThrow()).toList();
    }

    private Attempt attempt(long user, String source, Topic topic, String state, Instant served, Instant expires) {
        Attempt attempt = new Attempt(user, "TOPIC_TEST".equals(source) ? "TOPIC_TEST" : "PRACTICE", source,
                topic.getId(), served, expires, mapper.createObjectNode());
        attempt.setStatus(state);
        if ("SUBMITTED".equals(state)) attempt.setSubmittedAt(NOW.minusSeconds(60));
        return attempts.saveAndFlush(attempt);
    }

    private void learn(long user, Topic topic, List<Lexeme> learned, String state) {
        Attempt attempt = attempt(user, "TOPIC_STUDY", topic, state, NOW.minusSeconds(900), NOW.plusSeconds(100));
        for (int i = 0; i < learned.size(); i++) {
            AttemptItem item = new AttemptItem(attempt.getId(), i + 1, "STUDY_CARD", "VOCAB");
            item.setLexemeId(learned.get(i).getId());
            item.setAnswer("LEARNED");
            item.setDurationMs(1000);
            attemptItems.saveAndFlush(item);
        }
    }

    private void testAttempt(long user, Topic topic, long score, long max, Instant served, Instant submitted) {
        Attempt attempt = attempt(user, "TOPIC_TEST", topic, "SUBMITTED", served, NOW.plusSeconds(100));
        attempt.setScore(score);
        attempt.setMaxScore(max);
        attempt.setSubmittedAt(submitted);
        attempts.saveAndFlush(attempt);
    }

    private JsonNode list(long user, String level) throws Exception {
        var request = get("/api/topics").header("Authorization", bearer(user, "LEARNER"));
        if (level != null) request.param("hskLevel", level);
        return mapper.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode detail(long user, Topic topic) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/topics/{id}", topic.getId())
                .header("Authorization", bearer(user, "LEARNER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static JsonNode find(JsonNode rows, long id) {
        for (JsonNode row : rows) if (row.path("id").asLong() == id) return row;
        throw new AssertionError("Topic missing from response: " + id);
    }

    private void assertActions(Topic topic, boolean study, boolean recognition, boolean listening,
                               boolean test, String reason) throws Exception {
        JsonNode actions = detail(owner, topic).path("actions");
        assertThat(actions.path("canStudy").asBoolean()).isEqualTo(study);
        assertThat(actions.path("canPracticeRecognition").asBoolean()).isEqualTo(recognition);
        assertThat(actions.path("canPracticeListening").asBoolean()).isEqualTo(listening);
        assertThat(actions.path("canTakeTest").asBoolean()).isEqualTo(test);
        if (reason == null) assertThat(actions.path("testBlockedReason").isNull()).isTrue();
        else assertThat(actions.path("testBlockedReason").asText()).isEqualTo(reason);
        if (!"RETRY_TOO_SOON".equals(reason)) assertThat(actions.path("retryAvailableAt").isNull()).isTrue();
    }

    private String bearer(long user, String role) {
        return "Bearer " + token(user, role);
    }

    private String token(long user, String role) {
        return jwt.issueAccessToken(user, List.of(role), false, UUID.randomUUID());
    }
}
