package com.cnhsk.learning.vocab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeExtra;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.entity.Topic;
import com.cnhsk.learning.content.entity.TopicItem;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.content.repository.TopicItemRepository;
import com.cnhsk.learning.content.repository.TopicRepository;
import com.cnhsk.learning.practice.entity.AttemptItem;
import com.cnhsk.learning.practice.entity.AttemptItemRepository;
import com.cnhsk.learning.practice.entity.AttemptRepository;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TopicStudyIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private TopicRepository topics;
    @Autowired private TopicItemRepository topicItems;
    @Autowired private LexemeRepository lexemes;
    @Autowired private AttemptRepository attempts;
    @Autowired private AttemptItemRepository attemptItems;
    @Autowired private TopicStudyRepository repository;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
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
    @ValueSource(strings = {"EXPIRED", "ABANDONED", "SUBMITTED"})
    void FR019_AT25_preservesFourLearnedCardsAndServesNextTenInOrder(String state) throws Exception {
        Topic topic = topic(1);
        List<Lexeme> words = words(topic, 14, false);
        long old = start(owner, topic, 201).path("sessionId").asLong();
        for (int i = 1; i <= 4; i++) answer(owner, old, i, "LEARNED", 1000, false, false, 0, 200);
        if ("EXPIRED".equals(state)) {
            when(clock.instant()).thenReturn(NOW.plusSeconds(7201));
            mvc.perform(get("/api/practice/sessions/{id}", old).header("Authorization", bearer(owner, "LEARNER")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EXPIRED"));
        } else {
            mvc.perform(post("/api/practice/sessions/{id}/" + ("SUBMITTED".equals(state) ? "finish" : "abandon"), old)
                            .header("Authorization", bearer(owner, "LEARNER")))
                    .andExpect("SUBMITTED".equals(state) ? status().isOk() : status().isNoContent());
        }
        JsonNode next = start(owner, topic, 201);
        assertThat(ids(next)).containsExactlyElementsOf(words.subList(4, 14).stream().map(Lexeme::getId).toList());
        assertThat(attemptItems.findByAttemptIdOrderByOrderIndexAsc(old)).hasSize(4);
        assertThat(attempts.findById(old).orElseThrow().getStatus()).isEqualTo(state);
    }

    @Test
    void FR019_AT25_excludesLearnedLexemesAcrossTopicsButNeverOtherUsersEvidence() throws Exception {
        Topic first = topic(1);
        Topic second = topic(9);
        Lexeme shared = words(first, 1, false).get(0);
        attach(second, shared, 0);
        Lexeme next = words(second, 1, false).get(0);
        long previous = start(owner, first, 201).path("sessionId").asLong();
        answer(owner, previous, 1, "LEARNED", 1000, false, false, 0, 200);
        assertThat(ids(start(owner, second, 201))).containsExactly(next.getId());
        assertThat(ids(start(other, second, 201))).containsExactly(shared.getId(), next.getId());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 9, 10, 14})
    void FR019_servesAtMostTenWithoutRecordingAnyCardAtStart(int count) throws Exception {
        Topic topic = topic(1);
        List<Lexeme> words = words(topic, count, false);
        JsonNode session = start(owner, topic, 201);
        assertThat(ids(session)).containsExactlyElementsOf(words.subList(0, Math.min(10, count)).stream()
                .map(Lexeme::getId).toList());
        assertThat(session.path("source").asText()).isEqualTo("TOPIC_STUDY");
        assertThat(session.path("feedback").asText()).isEqualTo("RECORD");
        assertThat(session.path("expiresAt").asText()).isEqualTo(NOW.plusSeconds(7200).toString());
        assertThat(attemptItems.findByAttemptIdOrderByOrderIndexAsc(session.path("sessionId").asLong())).isEmpty();
        assertThat(detail(owner, topic).path("learnedWords").asInt()).isZero();
        for (int i = 0; i < session.path("items").size(); i++) {
            assertThat(session.path("items").get(i).path("no").asInt()).isEqualTo(i + 1);
        }
    }

    @Test
    void FR019_AT26_allLearnedReturnsExactlyNoNewWordsOnlyAfterActiveSessionEnds() throws Exception {
        Topic topic = topic(1);
        words(topic, 1, false);
        long id = start(owner, topic, 201).path("sessionId").asLong();
        answer(owner, id, 1, "LEARNED", 1000, false, false, 0, 200);
        assertThat(start(owner, topic, 200).path("sessionId").asLong()).isEqualTo(id);
        mvc.perform(post("/api/practice/sessions/{id}/finish", id).header("Authorization", bearer(owner, "LEARNER")))
                .andExpect(status().isOk());
        JsonNode none = start(owner, topic, 200);
        assertThat(none.fieldNames()).toIterable().containsExactlyInAnyOrder("session", "reason");
        assertThat(none.path("session").isNull()).isTrue();
        assertThat(none.path("reason").asText()).isEqualTo("NO_NEW_WORDS");
    }

    @Test
    void FR019_missingAndEmptyTopicsReturn404() throws Exception {
        for (long id : List.of(Long.MAX_VALUE, topic(1).getId())) {
            mvc.perform(post("/api/topics/{id}/study-sessions", id).header("Authorization", bearer(owner, "LEARNER")))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code").value("TOPIC_NOT_FOUND"));
        }
    }

    @Test
    void FR022_emptyTopicTakesPrecedenceWhileOldSessionRemainsAccessible() throws Exception {
        Topic topic = topic(1);
        words(topic, 1, false);
        long id = start(owner, topic, 201).path("sessionId").asLong();
        // Remove only the fixture's association; the immutable session snapshot remains valid.
        jdbc.update("DELETE FROM learning.topic_items WHERE topic_id = ?", topic.getId());
        start(owner, topic, 404);
        mvc.perform(get("/api/practice/sessions/{id}", id).header("Authorization", bearer(owner, "LEARNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void FR020_completePromptOnlyExposesWhitelistedFieldsIncludingAllMeaningAndExamples() throws Exception {
        Topic topic = topic(3);
        Lexeme word = words(topic, 1, true).get(0);
        word.setMeaningVi("bạn; bằng hữu");
        word.setExtra(new LexemeExtra(List.of(new LexemeExtra.Example("我们是朋友", "wǒmen shì péngyou", "Chúng tôi là bạn")),
                List.of("private-tag"), "private-structure", "private-explanation", "private-mistake",
                List.of(new LexemeExtra.Exercise(1, "private-exercise", List.of(new LexemeExtra.Option("A", "secret")),
                        "private-answer", "private-answer-explanation"))));
        lexemes.saveAndFlush(word);
        JsonNode session = start(owner, topic, 201);
        JsonNode card = session.path("items").get(0);
        assertThat(card.path("type").asText()).isEqualTo("STUDY_CARD");
        assertThat(card.path("skill").asText()).isEqualTo("VOCAB");
        JsonNode prompt = card.path("prompt");
        assertThat(prompt.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("text", "pinyin", "sinoViet", "meaning", "examples", "audioUrl", "kind");
        assertThat(prompt.path("meaning").asText()).isEqualTo("bạn; bằng hữu");
        assertThat(prompt.path("examples").get(0).path("vi").asText()).isEqualTo("Chúng tôi là bạn");
        assertThat(prompt.path("kind").asText()).isEqualTo("WORD");
        assertSafe(session);
        JsonNode restored = mapper.readTree(mvc.perform(get("/api/practice/sessions/{id}", session.path("sessionId").asLong())
                .header("Authorization", bearer(owner, "LEARNER"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertSafe(restored);
    }

    @Test
    void FR020_nullOptionalFieldsProduceUsableCardWithEmptyExamplesAndNoAudio() throws Exception {
        Topic topic = topic(1);
        Lexeme word = words(topic, 1, false).get(0);
        word.setPinyin(null);
        word.setSinoViet(null);
        lexemes.saveAndFlush(word);
        JsonNode card = start(owner, topic, 201).path("items").get(0);
        assertThat(card.path("prompt").path("examples").isArray()).isTrue();
        assertThat(card.path("prompt").path("examples").isEmpty()).isTrue();
        assertThat(card.path("rules").path("hasAudio").asBoolean()).isFalse();
        assertSafe(card);
    }

    @ParameterizedTest
    @CsvSource({"LEARNED,200,true,false", "LEARNED,999,true,false", "LEARNED,1000,false,false",
            "KNOWN,200,true,true", "KNOWN,1000,false,true"})
    void FR021_AT27_validAnswersRecordOnlyTheirOwnFlagsAndNeverScores(String value, int duration,
            boolean suspicious, boolean declared) throws Exception {
        Topic topic = topic(1);
        words(topic, 1, false);
        long id = start(owner, topic, 201).path("sessionId").asLong();
        JsonNode response = answer(owner, id, 1, value, duration, false, false, 0, 200);
        assertThat(response.path("recorded").asBoolean()).isTrue();
        AttemptItem item = attemptItems.findByAttemptIdOrderByOrderIndexAsc(id).get(0);
        assertThat(item.getCorrect()).isNull();
        assertThat(item.getMeta().getOrDefault("suspicious", false)).isEqualTo(suspicious);
        assertThat(item.getMeta().getOrDefault("selfDeclared", false)).isEqualTo(declared);
        assertThat(detail(owner, topic).path("learnedWords").asInt()).isEqualTo(1);
        JsonNode result = mapper.readTree(mvc.perform(post("/api/practice/sessions/{id}/finish", id)
                        .header("Authorization", bearer(owner, "LEARNER"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(result.path("score").isNull()).isTrue();
        assertThat(result.path("maxScore").isNull()).isTrue();
        assertThat(result.path("extra").isEmpty()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", "learned", "", "UNKNOWN"})
    void FR021_invalidEnumReturns422WithoutRecordingEvidence(String value) throws Exception {
        Topic topic = topic(1);
        words(topic, 1, false);
        long id = start(owner, topic, 201).path("sessionId").asLong();
        JsonNode rejected = answer(owner, id, 1, value, 200, false, false, 0, 422);
        assertThat(rejected.path("error_code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(rejected.path("details").get(0).path("field").asText()).isEqualTo("answer");
        assertThat(attemptItems.findByAttemptIdOrderByOrderIndexAsc(id)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void FR021_skipAndAudioFailureFollowEngineRulesAndCountAsLearnedWithoutSourceFlags(boolean audioFailed) throws Exception {
        Topic topic = topic(1);
        words(topic, 1, true);
        long id = start(owner, topic, 201).path("sessionId").asLong();
        answer(owner, id, 1, audioFailed ? null : "KNOWN", 200, !audioFailed, audioFailed, 0, 200);
        AttemptItem item = attemptItems.findByAttemptIdOrderByOrderIndexAsc(id).get(0);
        assertThat(item.getCorrect()).isNull();
        assertThat(item.getMeta()).doesNotContainKeys("suspicious", "selfDeclared");
        assertThat(item.getMeta().get(audioFailed ? "audioFailed" : "skipped")).isEqualTo(true);
        assertThat(detail(owner, topic).path("learnedWords").asInt()).isEqualTo(1);
    }

    @Test
    void FR021_audioFailuresRequireAudioAndReplayLimitCountsTheFirstPlayback() throws Exception {
        Topic topic = topic(1);
        words(topic, 1, false);
        words(topic, 1, true);
        JsonNode session = start(owner, topic, 201);
        long id = session.path("sessionId").asLong();
        assertThat(session.path("items").get(1).path("rules").path("hasAudio").asBoolean()).isTrue();
        assertThat(session.path("items").get(1).path("rules").path("maxReplays").asInt()).isEqualTo(3);
        assertThat(answer(owner, id, 1, null, 0, false, true, 0, 400).path("error_code").asText())
                .isEqualTo("AUDIO_NOT_IN_ITEM");
        assertThat(answer(owner, id, 2, "LEARNED", 1000, false, false, 4, 400).path("error_code").asText())
                .isEqualTo("TOO_MANY_REPLAYS");
        answer(owner, id, 2, "LEARNED", 1000, false, false, 3, 200);
    }

    @Test
    void FR022_AT28_resumesSameUserTopicAndInclusiveExpiryWithoutUsingNewStartAllowance() throws Exception {
        Topic topic = topic(1);
        words(topic, 1, false);
        JsonNode first = start(owner, topic, 201);
        long id = first.path("sessionId").asLong();
        String key = "practice:start:" + owner + ":2026103012";
        String before = redis.opsForValue().get(key);
        answer(owner, id, 1, "KNOWN", 1000, false, false, 0, 200);
        JsonNode resumed = start(owner, topic, 200);
        assertThat(resumed.path("sessionId").asLong()).isEqualTo(id);
        assertThat(resumed.path("answered").get(0).path("answer").asText()).isEqualTo("KNOWN");
        assertThat(redis.opsForValue().get(key)).isEqualTo(before);
        when(clock.instant()).thenReturn(NOW.plusSeconds(7200));
        assertThat(start(owner, topic, 200).path("sessionId").asLong()).isEqualTo(id);
    }

    @Test
    void FR022_AT28_concurrentSameUserTopicStartsCreateExactlyOneSession() throws Exception {
        Topic topic = topic(1);
        words(topic, 1, false);
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> concurrentStart(owner, topic, ready, release));
            var second = pool.submit(() -> concurrentStart(owner, topic, ready, release));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            MvcResult a = first.get(20, TimeUnit.SECONDS);
            MvcResult b = second.get(20, TimeUnit.SECONDS);
            assertThat(List.of(a.getResponse().getStatus(), b.getResponse().getStatus())).containsExactlyInAnyOrder(200, 201);
            assertThat(mapper.readTree(a.getResponse().getContentAsString()).path("sessionId"))
                    .isEqualTo(mapper.readTree(b.getResponse().getContentAsString()).path("sessionId"));
            assertThat(sessionCount(owner, topic)).isEqualTo(1);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void FR022_otherUserCanStartWhileFirstUsersTopicLockIsHeld() throws Exception {
        Topic topic = topic(1);
        words(topic, 1, false);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(3);
        try {
            var holder = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                repository.lock(owner, topic.getId());
                locked.countDown();
                try {
                    assertThat(release.await(30, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var blocked = pool.submit(() -> start(owner, topic, 201));
            var independent = pool.submit(() -> start(other, topic, 201));
            JsonNode otherSession = independent.get(10, TimeUnit.SECONDS);
            assertThat(blocked.isDone()).isFalse();
            release.countDown();
            JsonNode ownSession = blocked.get(10, TimeUnit.SECONDS);
            holder.get(10, TimeUnit.SECONDS);
            assertThat(ownSession.path("sessionId")).isNotEqualTo(otherSession.path("sessionId"));
            assertThat(sessionCount(owner, topic)).isEqualTo(1);
            assertThat(sessionCount(other, topic)).isEqualTo(1);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "999999999999999999999"})
    void FR019_invalidTopicIdReturnsStandard422(String id) throws Exception {
        mvc.perform(post("/api/topics/{id}/study-sessions", id).header("Authorization", bearer(owner, "LEARNER"))
                        .header("X-Request-Id", "study-validation"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("id"))
                .andExpect(jsonPath("$.request_id").value("study-validation"));
    }

    @Test
    void FR019_onlyLearnersCanStartAndCookieWritesRequireCsrf() throws Exception {
        Topic topic = topic(1);
        words(topic, 1, false);
        String path = "/api/topics/" + topic.getId() + "/study-sessions";
        mvc.perform(post(path)).andExpect(status().isUnauthorized());
        for (String role : List.of("ADMIN", "TEACHER", "CONTENT_EDITOR", "MODERATOR", "ACCOUNTANT")) {
            mvc.perform(post(path).header("Authorization", bearer(owner, role)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        }
        Cookie auth = new Cookie("access_token", token(owner, "LEARNER"));
        mvc.perform(post(path).cookie(auth)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        Cookie csrf = mvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        mvc.perform(post(path).cookie(auth, csrf).header("X-XSRF-TOKEN", csrf.getValue())).andExpect(status().isCreated());
        assertThat(start(owner, topic, 200).path("sessionId").asLong()).isPositive();
    }

    @Test
    void FR019_newStartsUseExistingEngineRateLimitButResumeAndNoNewWordsDoNot() throws Exception {
        long limited = createUser();
        Topic topic = topic(1);
        words(topic, 1, false);
        long id = start(limited, topic, 201).path("sessionId").asLong();
        String key = "practice:start:" + limited + ":2026103012";
        redis.opsForValue().set(key, "120", java.time.Duration.ofMinutes(5));
        assertThat(start(limited, topic, 200).path("sessionId").asLong()).isEqualTo(id);
        Topic fresh = topic(1);
        words(fresh, 1, false);
        JsonNode error = start(limited, fresh, 429);
        assertThat(error.path("error_code").asText()).isEqualTo("RATE_LIMITED");
        assertThat(error.path("retryAfterSeconds").asLong()).isPositive();
        assertThat(sessionCount(limited, fresh)).isZero();
        answer(limited, id, 1, "LEARNED", 1000, false, false, 0, 200);
        mvc.perform(post("/api/practice/sessions/{id}/finish", id).header("Authorization", bearer(limited, "LEARNER")))
                .andExpect(status().isOk());
        assertThat(start(limited, topic, 200).path("reason").asText()).isEqualTo("NO_NEW_WORDS");
        assertThat(redis.opsForValue().get(key)).isEqualTo("121");
    }

    @Test
    void FR019_unlearnedSelectionIsOneLimitedQueryWithStableTies() {
        Topic topic = topic(1);
        List<Lexeme> words = words(topic, 20, false);
        jdbc.update("UPDATE learning.topic_items SET order_index = 1 WHERE topic_id = ? AND lexeme_id = ?",
                topic.getId(), words.get(0).getId());
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean enabled = statistics.isStatisticsEnabled();
        try {
            statistics.setStatisticsEnabled(true);
            statistics.clear();
            assertThat(repository.unlearned(owner, topic.getId())).extracting(Lexeme::getId)
                    .containsExactlyElementsOf(words.subList(1, 11).stream().map(Lexeme::getId).toList());
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        } finally {
            statistics.setStatisticsEnabled(enabled);
        }
    }

    private long createUser() throws Exception {
        String password = "Aa1" + UUID.randomUUID();
        return mapper.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.test",
                                "password", password, "confirmPassword", password, "displayName", "Study test user",
                                "acceptTerms", true)))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).path("userId").asLong();
    }

    private Topic topic(int level) {
        return topics.saveAndFlush(new Topic("T10-" + UUID.randomUUID(), "Chủ đề học thử", (short) level));
    }

    private List<Lexeme> words(Topic topic, int count, boolean audio) {
        var result = new ArrayList<Lexeme>();
        for (int i = 0; i < count; i++) {
            Lexeme word = new Lexeme(LexemeKind.WORD, "词" + UUID.randomUUID(), "nghĩa thử nghiệm");
            word.setPinyin("cí");
            word.setSinoViet("từ");
            word.setHskLevel(topic.getHskLevel());
            if (audio) word.setAudioUrl("https://example.test/audio.mp3");
            result.add(lexemes.saveAndFlush(word));
            attach(topic, word, 0);
        }
        return result;
    }

    private void attach(Topic topic, Lexeme word, int order) {
        TopicItem item = new TopicItem(topic.getId(), word.getId());
        item.setOrderIndex(order);
        topicItems.saveAndFlush(item);
    }

    private JsonNode start(long user, Topic topic, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(post("/api/topics/{id}/study-sessions", topic.getId())
                .header("Authorization", bearer(user, "LEARNER"))).andExpect(status().is(expectedStatus)).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private MvcResult concurrentStart(long user, Topic topic, CountDownLatch ready, CountDownLatch release) throws Exception {
        ready.countDown();
        assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
        return mvc.perform(post("/api/topics/{id}/study-sessions", topic.getId())
                .header("Authorization", bearer(user, "LEARNER"))).andReturn();
    }

    private JsonNode answer(long user, long id, int no, String value, int duration,
            boolean skip, boolean failed, int replays, int expectedStatus) throws Exception {
        var body = mapper.createObjectNode().put("no", no).put("durationMs", duration).put("skipped", skip)
                .put("audioFailed", failed).put("replayCount", replays);
        if (value != null) body.put("answer", value);
        return mapper.readTree(mvc.perform(post("/api/practice/sessions/{id}/answers", id)
                        .header("Authorization", bearer(user, "LEARNER")).contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString())).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString());
    }

    private JsonNode detail(long user, Topic topic) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/topics/{id}", topic.getId())
                .header("Authorization", bearer(user, "LEARNER"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static List<Long> ids(JsonNode session) {
        var ids = new ArrayList<Long>();
        session.path("items").forEach(item -> ids.add(item.path("lexemeId").asLong()));
        return ids;
    }

    private long sessionCount(long user, Topic topic) {
        return jdbc.queryForObject("SELECT count(*) FROM learning.attempts WHERE user_id = ? "
                + "AND ref_type = 'TOPIC_STUDY' AND ref_id = ?", Long.class, user, topic.getId());
    }

    private static void assertSafe(JsonNode value) {
        assertThat(value.toString()).doesNotContain("\"answer\":", "\"accept\":", "answerKey", "private-", "email", "userId");
    }

    private String bearer(long user, String role) {
        return "Bearer " + token(user, role);
    }

    private String token(long user, String role) {
        return jwt.issueAccessToken(user, List.of(role), false, UUID.randomUUID());
    }
}
