package com.cnhsk.learning.vocab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeExtra;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.entity.Topic;
import com.cnhsk.learning.content.entity.TopicItem;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.content.repository.TopicItemRepository;
import com.cnhsk.learning.content.repository.TopicRepository;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler.Answer;
import com.cnhsk.learning.practice.PracticeSourceHandler.FinishContext;
import com.cnhsk.learning.practice.PracticeSourceHandler.GradedItem;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.Skill;
import com.cnhsk.learning.practice.entity.Attempt;
import com.cnhsk.learning.practice.entity.AttemptItem;
import com.cnhsk.learning.practice.entity.AttemptItemRepository;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TopicTestIntegrationTest extends AbstractIntegrationTest {

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
    @Autowired private TopicTestHandler handler;
    @MockitoSpyBean private TopicTestRepository repository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private Clock clock;
    @MockitoBean(name = "practiceChoiceRandom") private RandomGenerator random;
    private long owner;
    private long other;

    @BeforeAll
    void identitiesThroughAuthApi() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        owner = createUser();
        other = createUser();
    }

    @BeforeEach
    void fixedTimeAndReproducibleShuffle() {
        when(clock.instant()).thenReturn(NOW);
        // Identity Fisher-Yates makes question positions reproducible; production uses the existing random bean.
        when(random.nextInt(anyInt())).thenAnswer(call -> call.getArgument(0, Integer.class) - 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "999999999999999999999"})
    void FR031_invalidTopicIdentifiersAre422(String id) throws Exception {
        assertError(request(owner, id, 422), "VALIDATION_FAILED");
    }

    @Test
    void FR031_missingOrEmptyTopicIs404EvenWithAnOldActiveTest() throws Exception {
        assertError(request(owner, Long.toString(Long.MAX_VALUE), 404), "TOPIC_NOT_FOUND");
        Topic empty = topic();
        history(owner, empty, "IN_PROGRESS", 0, 0, NOW);
        assertError(start(owner, empty, 404), "TOPIC_NOT_FOUND");
        assertThat(sessionCount(owner, empty)).isEqualTo(1);
    }

    @Test
    void FR031_threeWordsFailBeforeLearnedAndCooldownChecks() throws Exception {
        Topic topic = topic();
        words(topic, 3, false, false);
        history(owner, topic, "SUBMITTED", 0, 3, NOW.minusSeconds(180));
        JsonNode error = start(owner, topic, 422);
        assertError(error, "NOT_ENOUGH_WORDS");
        assertThat(error.path("requiredWords").asInt()).isEqualTo(4);
    }

    @Test
    void FR031_AT33_sevenOfTenFailsBeforeCooldownAndEightUnlocksAllWords() throws Exception {
        Topic topic = topic();
        List<Lexeme> words = words(topic, 10, true, true);
        learn(owner, topic, words.subList(0, 7), "ABANDONED");
        history(owner, topic, "SUBMITTED", 1, 10, NOW.minusSeconds(180));
        JsonNode error = start(owner, topic, 422);
        assertError(error, "NOT_ENOUGH_LEARNED");
        assertThat(error.path("learnedWords").asInt()).isEqualTo(7);
        assertThat(error.path("requiredWords").asInt()).isEqualTo(8);
        learn(owner, topic, words.subList(7, 8), "EXPIRED");
        when(clock.instant()).thenReturn(NOW.plusSeconds(420));
        assertThat(ids(start(owner, topic, 201))).containsExactlyElementsOf(words.stream().map(Lexeme::getId).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"IN_PROGRESS", "ABANDONED", "EXPIRED", "SUBMITTED"})
    void FR031_learnedEvidenceUsesUserAndLexemeAcrossTopicsAndEveryState(String state) throws Exception {
        Topic topic = topic();
        List<Lexeme> words = words(topic, 5, false, false);
        Topic elsewhere = topic();
        learn(owner, elsewhere, words.subList(0, 4), state);
        learn(other, topic, words, state);
        assertThat(ids(start(owner, topic, 201))).hasSize(5);
        Topic unlearned = topic();
        List<Lexeme> unlearnedWords = words(unlearned, 4, false, false);
        learn(other, unlearned, unlearnedWords, state);
        assertError(start(owner, unlearned, 422), "NOT_ENOUGH_LEARNED");
    }

    @Test
    void FR032_AT34_failedLatestTestWaitsThreeMinutesAndExactTenMinutesAllowsRetry() throws Exception {
        Topic topic = learnedTopic(4);
        history(owner, topic, "SUBMITTED", 3, 4, NOW.minusSeconds(180));
        JsonNode error = start(owner, topic, 429);
        assertError(error, "RETRY_TOO_SOON");
        assertThat(error.path("retryAfterSeconds").asInt()).isEqualTo(420);
        when(clock.instant()).thenReturn(NOW.plusSeconds(419));
        assertThat(start(owner, topic, 429).path("retryAfterSeconds").asInt()).isEqualTo(1);
        when(clock.instant()).thenReturn(NOW.plusSeconds(420));
        assertThat(start(owner, topic, 201).path("status").asText()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void FR032_AT34_latestPassedOrAbandonedTestDoesNotBlockRetry() throws Exception {
        for (String state : List.of("SUBMITTED", "ABANDONED", "EXPIRED")) {
            Topic topic = learnedTopic(4);
            history(owner, topic, "SUBMITTED", 0, 4, NOW.minusSeconds(180));
            history(owner, topic, state, 4, 4, NOW.minusSeconds(10));
            assertThat(start(owner, topic, 201).path("sessionId").asLong()).isPositive();
        }
    }

    @Test
    void FR033_resumesBeforeEligibilityChecksButStillRequiresANonemptyTopic() throws Exception {
        Topic topic = topic();
        List<Lexeme> words = words(topic, 4, true, true);
        learn(owner, topic, words, "SUBMITTED");
        JsonNode first = start(owner, topic, 201);
        topicItems.deleteById(topicItems.findAll().stream().filter(item -> item.getTopicId().equals(topic.getId()))
                .findFirst().orElseThrow().getId());
        JsonNode resumed = start(owner, topic, 200);
        assertThat(resumed.path("sessionId")).isEqualTo(first.path("sessionId"));
        assertThat(resumed.path("items")).isEqualTo(first.path("items"));
        when(clock.instant()).thenReturn(NOW.plusSeconds(86400));
        assertThat(start(owner, topic, 200).path("sessionId")).isEqualTo(first.path("sessionId"));
        when(clock.instant()).thenReturn(NOW.plusSeconds(86401));
        assertError(start(owner, topic, 422), "NOT_ENOUGH_WORDS");
    }

    @Test
    void FR033_twoConcurrentPostsForSameUserTopicCreateOneTest() throws Exception {
        Topic topic = learnedTopic(10);
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> concurrentStart(topic, ready, release));
            var second = pool.submit(() -> concurrentStart(topic, ready, release));
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
    void FR033_otherLearnerCanStartWhileFirstLearnersAdvisoryLockIsHeld() throws Exception {
        Topic topic = learnedTopic(4);
        learn(other, topic, repository.words(topic.getId()), "SUBMITTED");
        var pool = Executors.newFixedThreadPool(3);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var holder = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                repository.lockStart(owner, topic.getId());
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
            assertThat(blocked.get(10, TimeUnit.SECONDS).path("sessionId")).isNotEqualTo(otherSession.path("sessionId"));
            holder.get(10, TimeUnit.SECONDS);
            assertThat(sessionCount(owner, topic)).isEqualTo(1);
            assertThat(sessionCount(other, topic)).isEqualTo(1);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void FR034_outsideTopicDistractorsMarkSessionMetaWithoutLeakingAnswers() throws Exception {
        Topic topic = learnedTopic(4);
        List<Lexeme> words = repository.words(topic.getId());
        words.get(1).setMeaningVi(words.get(0).getMeaningVi());
        words.get(1).setMeaningNorm(words.get(0).getMeaningNorm());
        lexemes.saveAndFlush(words.get(1));
        words(topic(), 4, false, false);
        JsonNode session = start(owner, topic, 201);
        assertThat(session.path("meta").path("mixedDistractors").asBoolean()).isTrue();
        assertSafe(session);
    }

    @Test
    void FR034_AT35_twentyOfTwentyFiveFollowGeneratedQuestionCycleAndFallbacks() throws Exception {
        Topic topic = topic();
        List<Lexeme> words = words(topic, 25, false, false);
        for (int i = 0; i < words.size(); i++) {
            Lexeme word = words.get(i);
            if (i < 10) word.setAudioUrl("https://example.test/audio-" + i + ".mp3");
            if (i < 5) word.setExtra(example(word.getText() + "句", "Nghĩa câu"));
            lexemes.saveAndFlush(word);
        }
        learn(owner, topic, words.subList(0, 20), "SUBMITTED");
        JsonNode session = start(owner, topic, 201);
        assertThat(ids(session)).hasSize(20).doesNotHaveDuplicates();
        for (int i = 0; i < 20; i++) {
            JsonNode item = session.path("items").get(i);
            String expected = switch (i % 4) {
                case 1 -> i < 10 ? "AUDIO_WORD_CHOICE" : "MEANING_CHOICE";
                case 2 -> "PINYIN_INPUT";
                case 3 -> i < 5 ? "CLOZE_CHOICE" : "MEANING_CHOICE";
                default -> "MEANING_CHOICE";
            };
            assertThat(item.path("type").asText()).isEqualTo(expected);
            assertThat(item.path("no").asInt()).isEqualTo(i + 1);
            assertThat(item.path("rules").path("maxReplays").asInt()).isEqualTo(3);
            assertThat(item.path("rules").path("hasAudio").asBoolean()).isEqualTo(expected.equals("AUDIO_WORD_CHOICE"));
            if (!expected.equals("PINYIN_INPUT")) assertThat(item.path("options")).hasSize(4);
        }
        assertSafe(session);
    }

    @Test
    void FR034_shuffleUsesTheEntirePoolBeforeTakingTwenty() throws Exception {
        Topic topic = learnedTopic(25);
        when(random.nextInt(anyInt())).thenReturn(0);
        JsonNode session = start(owner, topic, 201);
        List<Lexeme> words = repository.words(topic.getId());
        assertThat(ids(session)).contains(words.get(20).getId()).doesNotContain(words.get(0).getId());
    }

    @Test
    void FR035_missingFirstExampleOrAudioUsesMeaningAndFactoryFailureUsesPinyin() throws Exception {
        Topic topic = learnedTopic(4);
        List<Lexeme> words = repository.words(topic.getId());
        words.get(1).setAudioUrl(null);
        words.get(3).setExtra(new LexemeExtra(List.of(new LexemeExtra.Example("不含目标", null, "nghĩa"),
                new LexemeExtra.Example(words.get(3).getText(), null, "nghĩa")), null, null, null, null, null));
        lexemes.saveAllAndFlush(words);
        doReturn(Optional.empty()).when(repository).choices(argThat(word -> word.getId().equals(words.get(0).getId())),
                eq(topic.getId()), eq(ItemType.MEANING_CHOICE));
        JsonNode session = start(owner, topic, 201);
        assertThat(session.path("items")).extracting(item -> item.path("type").asText())
                .containsExactly("PINYIN_INPUT", "MEANING_CHOICE", "PINYIN_INPUT", "MEANING_CHOICE");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "invalid@@"})
    void FR035_missingOrInvalidPinyinFallsBackToMeaningWithoutEmptyAccept(String pinyin) throws Exception {
        Topic topic = learnedTopic(4);
        List<Lexeme> words = repository.words(topic.getId());
        words.get(2).setPinyin(pinyin);
        lexemes.saveAndFlush(words.get(2));
        JsonNode session = start(owner, topic, 201);
        assertThat(session.path("items").get(2).path("type").asText()).isEqualTo("MEANING_CHOICE");
        assertThat(snapshot(session).get(2).path("accept").isNull()).isTrue();
    }

    @Test
    void FR035_invalidPinyinAndNoDistractorsSkipsAndBackfillsBeyondFirstTwentyCandidates() throws Exception {
        Topic topic = learnedTopic(21);
        List<Lexeme> words = repository.words(topic.getId());
        Lexeme invalid = words.get(2);
        invalid.setPinyin("invalid@@");
        lexemes.saveAndFlush(invalid);
        doReturn(Optional.empty()).when(repository).choices(argThat(word -> word.getId().equals(invalid.getId())),
                eq(topic.getId()), any(ItemType.class));
        JsonNode session = start(owner, topic, 201);
        assertThat(ids(session)).hasSize(20).doesNotContain(invalid.getId()).contains(words.get(20).getId());
        assertThat(session.path("items").get(2).path("type").asText()).isEqualTo("PINYIN_INPUT");
        assertThat(session.path("items").get(19).path("no").asInt()).isEqualTo(20);
    }

    @Test
    void FR035_noGeneratableQuestionsIs422AndCreatesNoAttempt() throws Exception {
        Topic topic = learnedTopic(4);
        for (Lexeme word : repository.words(topic.getId())) {
            word.setPinyin(null);
            lexemes.saveAndFlush(word);
        }
        doReturn(Optional.empty()).when(repository).choices(any(Lexeme.class), eq(topic.getId()), any(ItemType.class));
        assertError(start(owner, topic, 422), "NOT_ENOUGH_WORDS");
        assertThat(sessionCount(owner, topic)).isZero();
    }

    @Test
    void FR036_pinyinHasOnlyTextHintAndNumberedUnspacedServerAccept() throws Exception {
        Topic topic = learnedTopic(4);
        Lexeme word = repository.words(topic.getId()).get(2);
        word.setPinyin("nǐ hǎo");
        lexemes.saveAndFlush(word);
        JsonNode session = start(owner, topic, 201);
        JsonNode item = session.path("items").get(2);
        assertThat(item.path("skill").asText()).isEqualTo("VOCAB");
        assertThat(item.path("prompt").fieldNames()).toIterable().containsExactlyInAnyOrder("text", "hint");
        assertThat(item.path("prompt").path("hint").asText()).isEqualTo(word.getMeaningVi());
        assertThat(snapshot(session).get(2).path("accept")).extracting(JsonNode::asText).containsExactly("ni3hao3");
        assertSafe(session);
    }

    @Test
    void FR037_AT36_clozeReplacesOnlyFirstLiteralOccurrenceAndUsesSentenceMeaning() throws Exception {
        Topic topic = learnedTopic(4);
        Lexeme word = repository.words(topic.getId()).get(3);
        word.setExtra(example("我们是" + word.getText() + "，" + word.getText(), "Chúng tôi là bạn"));
        lexemes.saveAndFlush(word);
        JsonNode session = start(owner, topic, 201);
        JsonNode item = session.path("items").get(3);
        assertThat(item.path("skill").asText()).isEqualTo("READING");
        assertThat(item.path("prompt").path("sentence").asText()).isEqualTo("我们是＿＿，" + word.getText());
        assertThat(item.path("prompt").path("hint").asText()).isEqualTo("Chúng tôi là bạn");
        assertThat(item.path("options")).hasSize(4);
    }

    @ParameterizedTest
    @CsvSource({"10,true", "39,true", "40,false"})
    void FR038_AT37_wallTimeSuspicionKeepsServerScoreAtTwoSecondsPerQuestion(int seconds, boolean suspicious) throws Exception {
        Topic topic = learnedTopic(20);
        JsonNode session = start(owner, topic, 201);
        assertThat(session.path("feedback").asText()).isEqualTo("DEFERRED");
        assertThat(session.path("expiresAt").asText()).isEqualTo(NOW.plusSeconds(86400).toString());
        when(clock.instant()).thenReturn(NOW.plusSeconds(seconds));
        JsonNode result = finish(session, correctAnswers(session, 20), 200);
        assertThat(result.path("score").asInt()).isEqualTo(20);
        assertThat(result.path("maxScore").asInt()).isEqualTo(20);
        assertThat(result.path("suspicious").asBoolean()).isEqualTo(suspicious);
    }

    @Test
    void FR038_draftsAreOverwritableAndResumeNeverExposesAnswersBeforeFinish() throws Exception {
        Topic topic = learnedTopic(4);
        JsonNode session = start(owner, topic, 201);
        JsonNode first = correctAnswers(session, 1).get(0);
        long id = session.path("sessionId").asLong();
        for (int i = 0; i < 2; i++) {
            JsonNode draft = mapper.readTree(mvc.perform(post("/api/practice/sessions/{id}/answers", id)
                            .header("Authorization", bearer(owner, "LEARNER")).contentType(MediaType.APPLICATION_JSON)
                            .content(first.toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(draft.has("correct")).isFalse();
            assertThat(draft.has("correctAnswer")).isFalse();
        }
        JsonNode resumed = start(owner, topic, 200);
        assertThat(resumed.path("answered")).hasSize(1);
        assertSafe(resumed);
        assertSafe(view(owner, id, 200));
        when(clock.instant()).thenReturn(NOW.plusSeconds(20));
        assertThat(finish(session, mapper.createArrayNode(), 200).path("score").asInt()).isEqualTo(1);
    }

    @Test
    void FR039_AT38_exactNinetyPercentPassesOnceAndHistoryKeepsBestCompletion() throws Exception {
        Topic topic = learnedTopic(20);
        JsonNode first = start(owner, topic, 201);
        when(clock.instant()).thenReturn(NOW.plusSeconds(40));
        JsonNode result = finish(first, correctAnswers(first, 18), 200);
        assertThat(result.path("score").asInt()).isEqualTo(18);
        assertThat(result.path("extra").path("passed").asBoolean()).isTrue();
        assertThat(result.path("extra").path("firstCompletion").asBoolean()).isTrue();
        assertThat(result.path("extra").path("bestPercent").asInt()).isEqualTo(90);
        assertThat(result.path("extra").path("wrongWords")).hasSize(2);
        JsonNode second = start(owner, topic, 201);
        when(clock.instant()).thenReturn(NOW.plusSeconds(80));
        result = finish(second, correctAnswers(second, 20), 200);
        assertThat(result.path("extra").path("firstCompletion").asBoolean()).isFalse();
        assertThat(result.path("extra").path("bestPercent").asInt()).isEqualTo(100);
        JsonNode third = start(owner, topic, 201);
        when(clock.instant()).thenReturn(NOW.plusSeconds(120));
        result = finish(third, correctAnswers(third, 17), 200);
        assertThat(result.path("extra").path("passed").asBoolean()).isFalse();
        assertThat(result.path("extra").path("topicCompleted").asBoolean()).isTrue();
        assertThat(result.path("extra").path("bestPercent").asInt()).isEqualTo(100);
        assertThat(result.path("extra").path("wrongWords")).hasSize(3);
    }

    @Test
    void FR039_missingAnswersAreWrongAudioFailuresAreExcludedAndLexemesAreLoadedInBatch() throws Exception {
        Topic topic = learnedTopic(4);
        JsonNode session = start(owner, topic, 201);
        ArrayNode answers = mapper.createArrayNode();
        answers.addObject().put("no", 2).put("durationMs", 1000).put("audioFailed", true);
        when(clock.instant()).thenReturn(NOW.plusSeconds(10));
        JsonNode result = finish(session, answers, 200);
        assertThat(result.path("score").asInt()).isZero();
        assertThat(result.path("maxScore").asInt()).isEqualTo(3);
        assertThat(result.path("extra").path("wrongWords")).hasSize(3);
        assertThat(result.path("extra").path("wrongWords")).extracting(word -> word.path("lexemeId").asLong())
                .containsExactly(ids(session).get(0), ids(session).get(2), ids(session).get(3));
        assertThat(result.path("extra").path("wrongWords").get(0).fieldNames()).toIterable()
                .containsExactlyInAnyOrder("lexemeId", "text", "pinyin", "meaning");
    }

    @Test
    void FR039_historyExcludesCurrentOtherUsersTopicsSourcesAndNonSubmittedAttempts() throws Exception {
        Topic topic = learnedTopic(4);
        history(other, topic, "SUBMITTED", 4, 4, NOW.minusSeconds(10));
        history(owner, topic, "ABANDONED", 4, 4, NOW.minusSeconds(9));
        history(owner, topic(), "SUBMITTED", 4, 4, NOW.minusSeconds(8));
        JsonNode session = start(owner, topic, 201);
        long id = session.path("sessionId").asLong();
        jdbc.update("UPDATE learning.attempts SET score=4,max_score=4,status='SUBMITTED',submitted_at=? WHERE id=?",
                java.sql.Timestamp.from(NOW), id);
        var past = repository.past(owner, topic.getId(), id);
        assertThat(past.completed()).isFalse();
        assertThat(past.bestPercent()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void FR039_handlerToleratesMissingTopicsNullOrDeletedLexemesAndZeroMaxScore(boolean nullRef) {
        Long ref = nullRef ? null : Long.MAX_VALUE;
        var prompt = new ServedItem.Prompt("词", null, null, null, null, null, null, null, null);
        var item = new ServedItem(1, ItemType.PINYIN_INPUT, Skill.VOCAB, nullRef ? null : Long.MAX_VALUE,
                prompt, null, null, List.of("ma1"), null, new ServedItem.Rules(0, 3, false));
        var answer = new Answer(1, null, 0, 0, false, false, null);
        var context = new FinishContext(owner, Long.MAX_VALUE, PracticeSource.TOPIC_TEST, ref, 0L, 0L,
                false, NOW, NOW.plusSeconds(2), List.of(new GradedItem(item, answer, false, true, Map.of())));
        Map<String, Object> extra = handler.onFinish(context);
        assertThat(extra).containsEntry("passed", false).containsEntry("topicCompleted", false)
                .containsEntry("firstCompletion", false).containsEntry("bestPercent", 0);
        assertThat((List<?>) extra.get("wrongWords")).isEmpty();
    }

    @Test
    void FR040_AT39_twentyFiveHoursExpiresWithoutGrading() throws Exception {
        JsonNode session = start(owner, learnedTopic(4), 201);
        when(clock.instant()).thenReturn(NOW.plusSeconds(90000));
        long id = session.path("sessionId").asLong();
        assertThat(view(owner, id, 200).path("status").asText()).isEqualTo("EXPIRED");
        assertThat(finish(session, mapper.createArrayNode(), 422).path("error_code").asText()).isEqualTo("SESSION_EXPIRED");
        assertThat(attempts.findById(id).orElseThrow().getSubmittedAt()).isNull();
        assertThat(attemptItems.findByAttemptIdOrderByOrderIndexAsc(id)).isEmpty();
    }

    @Test
    void FR031_onlyLearnersCanStartCookieRequiresCsrfAndUserIdComesFromPrincipal() throws Exception {
        Topic topic = learnedTopic(4);
        String path = "/api/topics/" + topic.getId() + "/tests";
        mvc.perform(post(path)).andExpect(status().isUnauthorized());
        for (String role : List.of("ADMIN", "TEACHER", "CONTENT_EDITOR", "MODERATOR", "ACCOUNTANT")) {
            mvc.perform(post(path).header("Authorization", bearer(owner, role))).andExpect(status().isForbidden());
        }
        mvc.perform(post(path).header("Authorization", bearer(other, "LEARNER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + owner + "}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("NOT_ENOUGH_LEARNED"));
        Cookie auth = new Cookie("access_token", token(owner, "LEARNER"));
        mvc.perform(post(path).cookie(auth)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        Cookie csrf = mvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent()).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        mvc.perform(post(path).cookie(auth, csrf).header("X-XSRF-TOKEN", csrf.getValue())).andExpect(status().isCreated());
        JsonNode session = start(owner, topic, 200);
        long id = session.path("sessionId").asLong();
        assertThat(view(other, id, 403).path("error_code").asText()).isEqualTo("ATTEMPT_NOT_OWNED");
        assertThat(view(other, Long.MAX_VALUE, 403).path("message")).isEqualTo(view(other, id, 403).path("message"));
    }

    private long createUser() throws Exception {
        String password = "Aa1" + UUID.randomUUID();
        return mapper.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.test",
                                "password", password, "confirmPassword", password, "displayName", "Topic test user", "acceptTerms", true))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("userId").asLong();
    }

    private Topic topic() {
        return topics.saveAndFlush(new Topic("T12-" + UUID.randomUUID(), "Chủ đề kiểm tra thử", (short) 1));
    }

    private Topic learnedTopic(int count) {
        Topic topic = topic();
        learn(owner, topic, words(topic, count, true, true), "SUBMITTED");
        return topic;
    }

    private List<Lexeme> words(Topic topic, int count, boolean audio, boolean examples) {
        var result = new ArrayList<Lexeme>();
        for (int i = 0; i < count; i++) {
            String unique = UUID.randomUUID().toString();
            Lexeme word = new Lexeme(LexemeKind.WORD, "词" + unique, "nghĩa " + unique);
            word.setMeaningNorm(word.getMeaningVi());
            word.setPinyin(PINYIN[i % PINYIN.length]);
            word.setHskLevel(topic.getHskLevel());
            if (audio) word.setAudioUrl("https://example.test/audio-" + unique + ".mp3");
            if (examples) word.setExtra(example(word.getText() + "句", "Nghĩa câu"));
            word = lexemes.saveAndFlush(word);
            TopicItem item = new TopicItem(topic.getId(), word.getId());
            item.setOrderIndex(i);
            topicItems.saveAndFlush(item);
            result.add(word);
        }
        return result;
    }

    private static LexemeExtra example(String sentence, String meaning) {
        return new LexemeExtra(List.of(new LexemeExtra.Example(sentence, null, meaning)), null, null, null, null, null);
    }

    private void learn(long user, Topic topic, List<Lexeme> words, String state) {
        Attempt attempt = new Attempt(user, "PRACTICE", "TOPIC_STUDY", topic.getId(), NOW.minusSeconds(300),
                NOW.plusSeconds(7200), mapper.createObjectNode());
        attempt.setStatus(state);
        if ("SUBMITTED".equals(state)) attempt.setSubmittedAt(NOW.minusSeconds(250));
        attempt = attempts.saveAndFlush(attempt);
        for (int i = 0; i < words.size(); i++) {
            AttemptItem item = new AttemptItem(attempt.getId(), i + 1, "STUDY_CARD", "VOCAB");
            item.setLexemeId(words.get(i).getId());
            item.setMeta(Map.of(i % 2 == 0 ? "skipped" : "audioFailed", true));
            attemptItems.saveAndFlush(item);
        }
    }

    private void history(long user, Topic topic, String state, long score, long max, Instant submitted) {
        Attempt attempt = new Attempt(user, "TOPIC_TEST", "TOPIC_TEST", topic.getId(), submitted.minusSeconds(60),
                NOW.plusSeconds(86400), mapper.createObjectNode());
        attempt.setStatus(state);
        attempt.setScore(score);
        attempt.setMaxScore(max);
        if ("SUBMITTED".equals(state)) attempt.setSubmittedAt(submitted);
        attempts.saveAndFlush(attempt);
    }

    private JsonNode request(long user, String topicId, int expected) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/topics/{id}/tests", topicId)
                        .header("Authorization", bearer(user, "LEARNER")).header("X-Request-Id", "topic-test-start"))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private JsonNode start(long user, Topic topic, int expected) throws Exception {
        return request(user, topic.getId().toString(), expected);
    }

    private MvcResult concurrentStart(Topic topic, CountDownLatch ready, CountDownLatch release) throws Exception {
        ready.countDown();
        assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
        return mvc.perform(post("/api/topics/{id}/tests", topic.getId()).header("Authorization", bearer(owner, "LEARNER"))).andReturn();
    }

    private JsonNode snapshot(JsonNode session) {
        return attempts.findById(session.path("sessionId").asLong()).orElseThrow().getServedItems().path("items");
    }

    private ArrayNode correctAnswers(JsonNode session, int count) {
        ArrayNode answers = mapper.createArrayNode();
        JsonNode snapshot = snapshot(session);
        for (int i = 0; i < count; i++) {
            JsonNode item = snapshot.get(i);
            String value = item.hasNonNull("answer") ? item.path("answer").asText() : item.path("accept").get(0).asText();
            answers.addObject().put("no", i + 1).put("answer", value).put("durationMs", 1000);
        }
        return answers;
    }

    private JsonNode finish(JsonNode session, ArrayNode answers, int expected) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/practice/sessions/{id}/finish", session.path("sessionId").asLong())
                        .header("Authorization", bearer(owner, "LEARNER")).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.createObjectNode().set("answers", answers).toString()))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private JsonNode view(long user, long id, int expected) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/practice/sessions/{id}", id).header("Authorization", bearer(user, "LEARNER")))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private long sessionCount(long user, Topic topic) {
        return jdbc.queryForObject("SELECT count(*) FROM learning.attempts WHERE user_id=? AND ref_type='TOPIC_TEST' AND ref_id=?",
                Long.class, user, topic.getId());
    }

    private String bearer(long user, String role) {
        return "Bearer " + token(user, role);
    }

    private String token(long user, String role) {
        return jwt.issueAccessToken(user, List.of(role), false, UUID.randomUUID());
    }

    private static List<Long> ids(JsonNode session) {
        var ids = new ArrayList<Long>();
        session.path("items").forEach(item -> ids.add(item.path("lexemeId").asLong()));
        return ids;
    }

    private static void assertSafe(JsonNode session) {
        assertThat(session.path("items").toString()).doesNotContain("\"answer\":", "\"accept\":", "correctAnswer", "answerKey", "email", "userId");
        assertThat(session.path("meta").toString()).doesNotContain("answer", "accept", "email", "userId");
        for (JsonNode answered : session.path("answered")) {
            assertThat(answered.has("correct")).isFalse();
            assertThat(answered.has("correctAnswer")).isFalse();
        }
    }

    private static void assertError(JsonNode response, String code) {
        assertThat(response.path("error_code").asText()).isEqualTo(code);
        assertThat(response.path("request_id").asText()).isEqualTo("topic-test-start");
        assertThat(response.path("message").asText()).isNotEmpty();
    }
}
