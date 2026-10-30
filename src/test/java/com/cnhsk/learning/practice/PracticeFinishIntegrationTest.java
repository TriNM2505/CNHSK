package com.cnhsk.learning.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import com.cnhsk.learning.practice.PracticeService.FinishResult;
import com.cnhsk.learning.practice.PracticeService.StartPracticeCommand;
import com.cnhsk.learning.practice.PracticeSourceHandler.Answer;
import com.cnhsk.learning.practice.PracticeSourceHandler.FinishContext;
import com.cnhsk.learning.practice.entity.Attempt;
import com.cnhsk.learning.practice.entity.AttemptItemRepository;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.learning.practice.event.ItemGradedEvent;
import com.cnhsk.learning.practice.event.SessionFinishedEvent;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.security.RateLimiter;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.parser.OpenAPIV3Parser;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@AutoConfigureMockMvc
@Import(PracticeFinishIntegrationTest.EventConfiguration.class)
class PracticeFinishIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = PracticeServiceTest.NOW;
    @Autowired private MockMvc mvc;
    @Autowired private PracticeService practice;
    @Autowired private AttemptRepository attempts;
    @Autowired private AttemptItemRepository items;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private org.springframework.data.redis.core.StringRedisTemplate redis;
    @Autowired private EventProbe probe;
    @MockitoBean private Clock clock;
    @MockitoBean(name = "stubPracticeSourceHandler") private PracticeSourceHandler handler;
    @MockitoSpyBean private RateLimiter counters;
    private long owner;
    private long nextRef;

    @BeforeEach
    void setup() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        when(handler.source()).thenReturn(PracticeSource.TOPIC_TEST);
        when(handler.enrichMeta(any(), any())).thenReturn(Map.of("sourceChecked", true));
        when(handler.isSuspicious(any())).thenCallRealMethod();
        when(handler.onFinish(any())).thenReturn(Map.of("sourceResult", "done"));
        probe.clear();
        nextRef = 1;
        owner = createUser();
    }

    @Test
    void FR008_AT13_concurrentFinishCommitsExactlyOneSubmissionAndOneStudyEvent() throws Exception {
        long id = start(PracticeSource.TOPIC_TEST, choices(2));
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var tasks = IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
                ready.countDown();
                assertThat(go.await(10, TimeUnit.SECONDS)).isTrue();
                return finish(id, owner, Map.of("answers", List.of(body(1, "A", 1000)))).getStatus();
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            assertThat(List.of(tasks.get(0).get(30, TimeUnit.SECONDS), tasks.get(1).get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            go.countDown();
            executor.shutdownNow();
        }
        assertThat(items.findByAttemptIdOrderByOrderIndexAsc(id)).hasSize(2);
        assertThat(studyCount(id)).isEqualTo(1);
        assertThat(probe.finished).hasSize(1);
    }

    @Test
    void FR008_AT14_twentyQuestionsFifteenAnswersTwelveCorrectIncludesFiveMissing() throws Exception {
        long id = start(PracticeSource.TOPIC_TEST, choices(20));
        List<Map<String, Object>> answers = IntStream.rangeClosed(1, 15)
                .mapToObj(no -> body(no, no <= 12 ? "A" : "B", 1000)).toList();
        JsonNode result = json(finish(id, owner, Map.of("answers", answers)));
        assertThat(result.path("score").asLong()).isEqualTo(12);
        assertThat(result.path("maxScore").asLong()).isEqualTo(20);
        assertThat(result.path("percent").asInt()).isEqualTo(60);
        assertThat(result.path("items")).hasSize(20);
        assertThat(result.path("extra").path("sourceResult").asText()).isEqualTo("done");
        assertThat(items.findByAttemptIdOrderByOrderIndexAsc(id)).filteredOn(item -> item.getOrderIndex() > 15)
                .allSatisfy(item -> {
                    assertThat(item.getCorrect()).isFalse();
                    assertThat(item.getAnswer()).isNull();
                    assertThat(item.getMeta()).containsEntry("missing", true);
                }).hasSize(5);
    }

    @Test
    void FR008_AT15_studyEvidenceUsesVietnamDateServerDurationAndSourceReference() {
        long id = start(PracticeSource.TOPIC_TEST, choices(1));
        when(clock.instant()).thenReturn(NOW.plusSeconds(120));
        FinishResult result = practice.finish(owner, id, List.of(answer(1, "A", 1000)));
        var row = jdbc.queryForMap("select * from learning.study_events where user_id = ?", owner);
        assertThat(row.get("event_type")).isEqualTo("STUDY");
        assertThat(row.get("ref_type")).isEqualTo("TOPIC_TEST");
        assertThat(((Number) row.get("ref_id")).longValue()).isEqualTo(1);
        assertThat(((java.sql.Date) row.get("local_date")).toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(row.get("duration_ms")).isEqualTo(120_000);
        Attempt saved = attempts.findById(id).orElseThrow();
        assertThat(saved.getSubmittedAt()).isEqualTo(NOW.plusSeconds(120));
        assertThat(saved.getDurationMs()).isEqualTo(120_000);
        assertThat(saved.getScore()).isEqualTo(result.score());
        assertThat(saved.getStatus()).isEqualTo("SUBMITTED");
    }

    @Test
    void FR009_AT17_eventsCoverEachNewlyRecordedItemAndOneFinishedSessionInsideTransactions() {
        long id = start(PracticeSource.TOPIC_TEST, choices(3));
        practice.answer(owner, id, answer(1, "A", 1000));
        assertThat(probe.graded).isEmpty();
        practice.finish(owner, id, List.of(answer(2, "B", 1000)));
        assertThat(probe.graded).hasSize(3).allSatisfy(event -> {
            assertThat(event.userId()).isEqualTo(owner);
            assertThat(event.sessionId()).isEqualTo(id);
            assertThat(event.source()).isEqualTo(PracticeSource.TOPIC_TEST);
            assertThat(event.refId()).isEqualTo(1);
            assertThat(event.skill()).isEqualTo(Skill.VOCAB);
            assertThat(event.itemType()).isEqualTo(ItemType.MEANING_CHOICE);
            assertThat(event.meta()).containsEntry("sourceChecked", true);
        });
        assertThat(probe.graded).extracting(ItemGradedEvent::no).containsExactly(1, 2, 3);
        assertThat(probe.finished).containsExactly(new SessionFinishedEvent(owner, id, PracticeSource.TOPIC_TEST,
                1L, 1L, 3L, false));
        assertThat(probe.allTransactional).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"item", "session", "handler", "validation", "enrichment", "suspicion"})
    void FR009_AT17_listenerOrSourceFailureRollsBackScoresItemsStudyEventAndDraftMerge(String failure) {
        long id = start(PracticeSource.TOPIC_TEST, choices(2));
        practice.answer(owner, id, answer(1, "B", 1000));
        JsonNode oldDraft = attempts.findById(id).orElseThrow().getDraftAnswers().deepCopy();
        switch (failure) {
            case "item" -> probe.rejectItem = true;
            case "session" -> probe.rejectSession = true;
            case "handler" -> when(handler.onFinish(any())).thenThrow(rejected());
            case "validation" -> doThrow(rejected()).when(handler).validateAnswer(any(), any());
            case "enrichment" -> when(handler.enrichMeta(any(), any())).thenThrow(rejected());
            case "suspicion" -> when(handler.isSuspicious(any())).thenThrow(rejected());
            default -> throw new AssertionError(failure);
        }
        assertThatThrownBy(() -> practice.finish(owner, id, List.of(answer(1, "A", 1000), answer(2, "A", 1000))))
                .isInstanceOf(RuntimeException.class);
        assertUnfinished(id, oldDraft);
    }

    @Test
    void FR009_recordAndPreRecordedItemsPublishOnceAndAreNotRepublishedAtFinish() {
        long id = practice.start(new StartPracticeCommand(owner, PracticeSource.TOPIC_STUDY, nextRef++, null,
                List.of(PracticeServiceTest.card(1), PracticeServiceTest.card(2)),
                List.of(answer(1, "KNOWN", 1000)), null, null)).sessionId();
        assertThat(probe.graded).hasSize(1);
        practice.answer(owner, id, answer(2, "LEARNED", 1000));
        assertThat(probe.graded).hasSize(2).allSatisfy(event -> assertThat(event.correct()).isNull());
        FinishResult result = practice.finish(owner, id, List.of());
        assertThat(probe.graded).hasSize(2);
        assertThat(probe.finished).hasSize(1);
        assertThat(result.score()).isNull();
        assertThat(result.maxScore()).isNull();
        assertThat(result.percent()).isNull();
        assertThat(result.suspicious()).isFalse();
    }

    @Test
    void FR008_deferredMergesSavedDraftAndNewAnswersInOrderWithoutCountingDraftAgain() throws Exception {
        long id = start(PracticeSource.TOPIC_TEST, choices(2));
        practice.answer(owner, id, answer(1, "A", 1000));
        String key = answerCounter();
        redis.opsForValue().set(key, "598");
        JsonNode result = json(finish(id, owner, Map.of("answers",
                List.of(body(2, "B", 1000), body(2, "A", 1200)))));
        assertThat(result.path("score").asLong()).isEqualTo(2);
        assertThat(redis.opsForValue().get(key)).isEqualTo("600");
        assertThat(attempts.findById(id).orElseThrow().getDraftAnswers().path("2").path("durationMs").asInt())
                .isEqualTo(1200);
        assertThat(items.findByAttemptIdOrderByOrderIndexAsc(id)).hasSize(2);
    }

    @Test
    void FR008_rateLimitRejectsWholeFinishAndPreservesPreviousDraftWithoutAnyNewEvidence() throws Exception {
        long id = start(PracticeSource.TOPIC_TEST, choices(2));
        practice.answer(owner, id, answer(1, "B", 1000));
        JsonNode draft = attempts.findById(id).orElseThrow().getDraftAnswers().deepCopy();
        redis.opsForValue().set(answerCounter(), "599");
        var response = finish(id, owner, Map.of("answers", List.of(body(1, "A", 1000), body(2, "A", 1000))));
        assertError(response, 429, "RATE_LIMITED");
        assertThat(mapper.readTree(response.getContentAsString()).path("retryAfterSeconds").asInt()).isPositive();
        assertUnfinished(id, draft);
        assertThat(probe.graded).isEmpty();
        assertThat(probe.finished).isEmpty();
    }

    @Test
    void FR008_redisFailureStillAllowsFinishUsingExistingFailOpenPolicy() throws Exception {
        long id = start(PracticeSource.TOPIC_TEST, choices(1));
        doThrow(new IllegalStateException("redis unavailable")).when(counters).increment(startsWith("practice:ans:"), any());
        assertThat(json(finish(id, owner, Map.of("answers", List.of(body(1, "A", 1000))))).path("score").asLong())
                .isEqualTo(1);
        assertThat(studyCount(id)).isEqualTo(1);
    }

    @Test
    void FR008_twentyQuestionsThreeFastRealAnswersSeventeenMissingStillFlagsSuspicious() throws Exception {
        long id = start(PracticeSource.TOPIC_TEST, choices(20));
        JsonNode result = json(finish(id, owner, Map.of("answers",
                List.of(body(1, "A", 100), body(2, "A", 100), body(3, "A", 100)))));
        assertThat(result.path("suspicious").asBoolean()).isTrue();
        assertThat(result.path("score").asLong()).isEqualTo(3);
        assertThat(result.path("maxScore").asLong()).isEqualTo(20);
        var captured = ArgumentCaptor.forClass(FinishContext.class);
        verify(handler).onFinish(captured.capture());
        assertThat(captured.getValue().suspicious()).isTrue();
        assertThat(probe.finished.get(0).suspicious()).isTrue();
        assertThat(attempts.findById(id).orElseThrow().isSuspicious()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, 3, 4})
    void FR008_suspiciousRatioUsesThirtyPercentBoundaryOfRealAnswers(int fast) {
        long id = start(PracticeSource.TOPIC_TEST, choices(10));
        List<Answer> answers = IntStream.rangeClosed(1, 10)
                .mapToObj(no -> answer(no, "A", no <= fast ? 300 : 1000)).toList();
        assertThat(practice.finish(owner, id, answers).suspicious()).isEqualTo(fast >= 3);
    }

    @Test
    void FR008_sourceSuspicionIsOrCombinedBeforeOnFinishAndMatchesResponseContextAndEvent() {
        long id = start(PracticeSource.TOPIC_TEST, choices(1));
        when(handler.isSuspicious(any())).thenReturn(true);
        FinishResult result = practice.finish(owner, id, List.of(answer(1, "A", 1000)));
        var captured = ArgumentCaptor.forClass(FinishContext.class);
        verify(handler).onFinish(captured.capture());
        assertThat(result.suspicious()).isTrue();
        assertThat(captured.getValue().suspicious()).isTrue();
        assertThat(probe.finished.get(0).suspicious()).isTrue();
    }

    @Test
    void FR008_deferredSkipsAndMissingCountAsWrongWhileAudioAndRecordAreExcluded() {
        long id = start(PracticeSource.TOPIC_TEST,
                List.of(PracticeServiceTest.choice(1), audio(2), PracticeServiceTest.card(3), PracticeServiceTest.choice(4)));
        FinishResult result = practice.finish(owner, id, List.of(
                new Answer(1, null, 100, 0, true, false, null),
                new Answer(2, null, 100, 1, false, true, null)));
        assertThat(result.score()).isZero();
        assertThat(result.maxScore()).isEqualTo(2);
        assertThat(result.suspicious()).isFalse();
        assertThat(items.findByAttemptIdOrderByOrderIndexAsc(id)).allSatisfy(item -> {
            if (item.getOrderIndex() == 2 || item.getOrderIndex() == 3) {
                assertThat(item.getCorrect()).isNull();
            } else {
                assertThat(item.getCorrect()).isFalse();
            }
        });
    }

    @Test
    void FR008_previouslyRecordedNonGradableDeferredItemsAreKeptAndNotScoredOrReemitted() {
        long id = start(PracticeSource.TOPIC_TEST, List.of(PracticeServiceTest.card(1), PracticeServiceTest.choice(2)));
        practice.answer(owner, id, answer(1, "KNOWN", 100));
        assertThat(probe.graded).hasSize(1);
        FinishResult result = practice.finish(owner, id, List.of(answer(2, "A", 1000)));
        assertThat(result.score()).isEqualTo(1);
        assertThat(result.maxScore()).isEqualTo(1);
        assertThat(result.suspicious()).isFalse();
        assertThat(probe.graded).hasSize(2);
    }

    @Test
    void FR008_immediateScoreExcludesSkippedFailedAndUnansweredItemsAndDoesNotReemitGrades() {
        long id = start(PracticeSource.TOPIC_PRACTICE,
                List.of(PracticeServiceTest.choice(1), PracticeServiceTest.choice(2), audio(3), PracticeServiceTest.choice(4)));
        practice.answer(owner, id, answer(1, "A", 1000));
        practice.answer(owner, id, new Answer(2, null, 100, 0, true, false, null));
        practice.answer(owner, id, new Answer(3, null, 100, 1, false, true, null));
        assertThat(probe.graded).hasSize(3);
        FinishResult result = practice.finish(owner, id, List.of());
        assertThat(result.score()).isEqualTo(1);
        assertThat(result.maxScore()).isEqualTo(1);
        assertThat(result.percent()).isEqualTo(100);
        assertThat(result.suspicious()).isFalse();
        assertThat(probe.graded).hasSize(3);
        assertThat(result.items()).hasSize(3);
    }

    @Test
    void FR008_allAudioFailedHasZeroMaximumAndNullPercent() {
        long id = start(PracticeSource.TOPIC_TEST, List.of(audio(1)));
        FinishResult result = practice.finish(owner, id, List.of(new Answer(1, null, 1000, 1, false, true, null)));
        assertThat(result.score()).isZero();
        assertThat(result.maxScore()).isZero();
        assertThat(result.percent()).isNull();
        assertThat(result.suspicious()).isFalse();
    }

    @Test
    void FR008_gameMaximumUsesServedGradableItemsAndWritesGameStudyEvent() {
        long id = start(PracticeSource.GAME_ROUND, choices(20));
        for (int no = 1; no <= 12; no++) {
            practice.answer(owner, id, answer(no, no <= 10 ? "A" : "B", 1000));
        }
        FinishResult result = practice.finish(owner, id, List.of());
        assertThat(result.score()).isEqualTo(10);
        assertThat(result.maxScore()).isEqualTo(20);
        assertThat(result.percent()).isEqualTo(50);
        assertThat(jdbc.queryForObject("select event_type from learning.study_events where user_id = ?",
                String.class, owner)).isEqualTo("GAME");
    }

    @Test
    void FR008_gameWithNullRefIdExcludesAudioFailureFromServedMaximum() {
        long id = practice.start(new StartPracticeCommand(owner, PracticeSource.GAME_ROUND, null, "test-game",
                List.of(audio(1), PracticeServiceTest.choice(2)), null, Duration.ofMinutes(5), null)).sessionId();
        practice.answer(owner, id, new Answer(1, null, 1000, 1, false, true, null));
        practice.answer(owner, id, answer(2, "A", 1000));
        FinishResult result = practice.finish(owner, id, List.of());
        assertThat(result.score()).isEqualTo(1);
        assertThat(result.maxScore()).isEqualTo(1);
        var row = jdbc.queryForMap("select event_type, ref_id from learning.study_events where user_id = ?", owner);
        assertThat(row.get("event_type")).isEqualTo("GAME");
        assertThat(row.get("ref_id")).isNull();
        assertThat(probe.finished.get(0).refId()).isNull();
    }

    @Test
    void FR008_pinyinUsesServerAcceptAndFinishReturnsCanonicalCorrectAnswer() {
        ServedItem base = PracticeServiceTest.choice(1);
        ServedItem pinyin = new ServedItem(1, ItemType.PINYIN_INPUT, Skill.VOCAB, null, base.prompt(), null,
                null, List.of("wo3men5"), null, base.rules());
        long id = start(PracticeSource.TOPIC_TEST, List.of(pinyin));
        FinishResult result = practice.finish(owner, id, List.of(answer(1, "wǒmen", 1000)));
        assertThat(result.score()).isEqualTo(1);
        assertThat(result.items().get(0).correctAnswer()).isEqualTo("wo3men5");
        assertThat(result.items().get(0).yourAnswer()).isEqualTo("wǒmen");
    }

    @Test
    void FR008_missingAndUnownedHaveSameForbiddenResponseWithoutInformationLeak() throws Exception {
        long id = start(PracticeSource.TOPIC_TEST, choices(1));
        long other = createUser();
        var missing = finish(Long.MAX_VALUE, other, null);
        var denied = finish(id, other, null);
        assertError(missing, 403, "ATTEMPT_NOT_OWNED");
        assertError(denied, 403, "ATTEMPT_NOT_OWNED");
        assertThat(mapper.readTree(missing.getContentAsString())).isEqualTo(mapper.readTree(denied.getContentAsString()));
        assertUnfinished(id, null);
    }

    @Test
    void FR008_expiredFinishCommitsExpiredAndPreservesExistingEvidenceEvenWhenReturning422() throws Exception {
        long id = start(PracticeSource.TOPIC_PRACTICE, choices(2));
        practice.answer(owner, id, answer(1, "A", 1000));
        Attempt attempt = attempts.findById(id).orElseThrow();
        attempt.setExpiresAt(NOW.minusSeconds(1));
        attempts.saveAndFlush(attempt);
        assertError(finish(id, owner, null), 422, "SESSION_EXPIRED");
        assertThat(attempts.findById(id).orElseThrow().getStatus()).isEqualTo("EXPIRED");
        assertThat(items.findByAttemptIdOrderByOrderIndexAsc(id)).hasSize(1);
        assertThat(studyCount(id)).isZero();
        assertError(finish(id, owner, null), 422, "SESSION_EXPIRED");
    }

    @Test
    void FR008_exactExpiryBoundaryIsValidAndRepeatedFinishReturns409() throws Exception {
        long id = start(PracticeSource.TOPIC_TEST, choices(1));
        Attempt attempt = attempts.findById(id).orElseThrow();
        attempt.setExpiresAt(NOW);
        attempts.saveAndFlush(attempt);
        assertThat(finish(id, owner, null).getStatus()).isEqualTo(200);
        assertError(finish(id, owner, null), 409, "SESSION_ALREADY_SUBMITTED");
        assertThat(studyCount(id)).isEqualTo(1);
    }

    @Test
    void FR008_newAnswerBatchValidatesServedNumbersOptionsAudioAndFlagsBeforePersisting() throws Exception {
        long id = start(PracticeSource.TOPIC_TEST, choices(1));
        List<Map<String, Object>> bad = List.of(body(99, "A", 1000), body(1, "Z", 1000),
                Map.of("no", 1, "audioFailed", true, "durationMs", 1000),
                Map.of("no", 1, "answer", "A", "replayCount", 4, "durationMs", 1000),
                Map.of("no", 1, "skipped", true, "audioFailed", true, "durationMs", 1000));
        List<String> codes = List.of("ITEM_NOT_IN_SESSION", "OPTION_NOT_IN_ITEM", "AUDIO_NOT_IN_ITEM",
                "TOO_MANY_REPLAYS", "VALIDATION_FAILED");
        for (int index = 0; index < bad.size(); index++) {
            assertError(finish(id, owner, Map.of("answers", List.of(bad.get(index)))), index == 4 ? 422 : 400, codes.get(index));
            assertUnfinished(id, null);
        }
    }

    @Test
    void FR008_nonGradableBatchAnswersAreRecordedOnceAndDuplicateRollsBackWholeFinish() throws Exception {
        long id = start(PracticeSource.TOPIC_TEST, List.of(PracticeServiceTest.card(1)));
        assertError(finish(id, owner, Map.of("answers", List.of(body(1, "KNOWN", 1000), body(1, "LEARNED", 1000)))),
                409, "ANSWER_ALREADY_SUBMITTED");
        assertUnfinished(id, null);
        JsonNode result = json(finish(id, owner, Map.of("answers", List.of(body(1, "KNOWN", 1000)))));
        assertThat(result.path("score").isNull()).isTrue();
        assertThat(result.path("maxScore").isNull()).isTrue();
    }

    @Test
    void FR008_finishRejectsBatchForImmediateSessionsAndMalformedNestedDtos() throws Exception {
        long id = start(PracticeSource.TOPIC_PRACTICE, choices(1));
        assertError(finish(id, owner, Map.of("answers", List.of(body(1, "A", 1000)))), 422, "VALIDATION_FAILED");
        assertUnfinished(id, null);
        for (String body : List.of("{", "{\"answers\":[null]}", "{\"answers\":[{\"no\":1}]}",
                "{\"answers\":[{\"no\":1,\"answer\":\"A\",\"durationMs\":-1}]}")) {
            var response = mvc.perform(post("/api/practice/sessions/{id}/finish", id)
                            .header("Authorization", bearer(owner, "LEARNER"))
                            .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
            assertError(response, 422, "VALIDATION_FAILED");
        }
    }

    @Test
    void FR008_finishRequiresLearnerAndCookieCsrfButAllowsBearerWithoutCsrf() throws Exception {
        long id = start(PracticeSource.TOPIC_TEST, choices(1));
        assertError(mvc.perform(post("/api/practice/sessions/{id}/finish", id)).andReturn().getResponse(), 401, "UNAUTHENTICATED");
        for (String role : List.of("ADMIN", "TEACHER", "MODERATOR", "ACCOUNTANT", "CONTENT_EDITOR")) {
            var denied = mvc.perform(post("/api/practice/sessions/{id}/finish", id)
                    .header("Authorization", bearer(owner, role))).andReturn().getResponse();
            assertError(denied, 403, "FORBIDDEN");
        }
        Cookie access = new Cookie("access_token", token(owner, "LEARNER"));
        assertError(mvc.perform(post("/api/practice/sessions/{id}/finish", id).cookie(access)).andReturn().getResponse(),
                403, "CSRF_TOKEN_INVALID");
        var csrf = mvc.perform(get("/api/auth/csrf").cookie(access)).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        mvc.perform(post("/api/practice/sessions/{id}/finish", id).cookie(access, csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())).andExpect(status().isOk());
        long bearerId = start(PracticeSource.TOPIC_TEST, choices(1));
        assertThat(finish(bearerId, owner, null).getStatus()).isEqualTo(200);
    }

    @Test
    void FR008_openApiFinishStatusesMatchControllerAndKeepSharedErrorSchema() throws Exception {
        String yaml = Files.readString(Path.of("specs/003-vocab-topics/contracts/openapi-practice-vocab.yaml"));
        var parsed = new OpenAPIV3Parser().readContents(yaml);
        assertThat(parsed.getMessages()).isEmpty();
        var operation = parsed.getOpenAPI().getPaths().get("/api/practice/sessions/{id}/finish").getPost();
        assertThat(operation.getResponses().keySet()).containsExactlyInAnyOrder("200", "400", "401", "403", "409", "422", "429");
        assertThat(operation.getDescription()).contains("RATE_LIMITED", "ATTEMPT_NOT_OWNED", "SESSION_EXPIRED");
        var schemas = new ObjectMapper(new YAMLFactory()).readTree(yaml).path("components").path("schemas");
        assertThat(schemas.path("ErrorResponse").path("properties").path("error_code").size()).isEqualTo(1);
        var actual = PracticeControllerResponses.finishResponses();
        assertThat(actual).containsExactlyInAnyOrderElementsOf(operation.getResponses().keySet());
    }

    private long createUser() throws Exception {
        String password = "Aa1" + UUID.randomUUID();
        return mapper.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.test",
                                "password", password, "confirmPassword", password,
                                "displayName", "Finish test user", "acceptTerms", true))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("userId").asLong();
    }

    private long start(PracticeSource source, List<ServedItem> served) {
        return practice.start(new StartPracticeCommand(owner, source, nextRef++, source == PracticeSource.GAME_ROUND ? "test-game" : null,
                served, null, source == PracticeSource.GAME_ROUND ? Duration.ofMinutes(5) : null, null)).sessionId();
    }

    private List<ServedItem> choices(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(PracticeServiceTest::choice).toList();
    }

    private Answer answer(int no, String answer, int duration) {
        return new Answer(no, answer, duration, 0, false, false, null);
    }

    private Map<String, Object> body(int no, String answer, int duration) {
        return Map.of("no", no, "answer", answer, "durationMs", duration);
    }

    private MockHttpServletResponse finish(long id, long user, Map<String, Object> body) throws Exception {
        var request = post("/api/practice/sessions/{id}/finish", id).header("Authorization", bearer(user, "LEARNER"))
                .header("X-Request-Id", "finish-test");
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    private JsonNode json(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(200);
        return mapper.readTree(response.getContentAsString());
    }

    private String bearer(long user, String role) {
        return "Bearer " + token(user, role);
    }

    private String token(long user, String role) {
        return jwt.issueAccessToken(user, List.of(role), false, UUID.randomUUID());
    }

    private String answerCounter() {
        return "practice:ans:" + owner + ":" + DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZoneOffset.UTC).format(NOW);
    }

    private long studyCount(long id) {
        return jdbc.queryForObject("select count(*) from learning.study_events e join learning.attempts a "
                + "on a.user_id=e.user_id and a.ref_type=e.ref_type and a.ref_id=e.ref_id where a.id=?", Long.class, id);
    }

    private void assertUnfinished(long id, JsonNode draft) {
        Attempt saved = attempts.findById(id).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(saved.getSubmittedAt()).isNull();
        assertThat(saved.getScore()).isNull();
        assertThat(saved.getMaxScore()).isNull();
        assertThat(saved.getDraftAnswers()).isEqualTo(draft);
        assertThat(items.findByAttemptIdOrderByOrderIndexAsc(id)).isEmpty();
        assertThat(studyCount(id)).isZero();
    }

    private void assertError(MockHttpServletResponse response, int expectedStatus, String code) throws Exception {
        assertThat(response.getStatus()).isEqualTo(expectedStatus);
        JsonNode error = mapper.readTree(response.getContentAsString());
        assertThat(error.path("error_code").asText()).isEqualTo(code);
        assertThat(error.path("message").asText()).isNotBlank();
        assertThat(error.path("request_id").asText()).isNotBlank();
        assertThat(error.has("stackTrace")).isFalse();
    }

    private ApiException rejected() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Nguồn từ chối kết thúc lượt.", Map.of());
    }

    private ServedItem audio(int no) {
        ServedItem base = PracticeServiceTest.choice(no);
        return new ServedItem(no, ItemType.AUDIO_WORD_CHOICE, Skill.LISTENING, null, base.prompt(), base.options(),
                base.answer(), base.accept(), base.explanation(), new ServedItem.Rules(800, 3, true));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EventConfiguration {
        @Bean
        EventProbe finishEventProbe() {
            return new EventProbe();
        }
    }

    static class EventProbe {
        final List<ItemGradedEvent> graded = new CopyOnWriteArrayList<>();
        final List<SessionFinishedEvent> finished = new CopyOnWriteArrayList<>();
        volatile boolean rejectItem;
        volatile boolean rejectSession;
        volatile boolean allTransactional;

        void clear() {
            graded.clear();
            finished.clear();
            rejectItem = false;
            rejectSession = false;
            allTransactional = true;
        }

        @EventListener
        public void item(ItemGradedEvent event) {
            graded.add(event);
            allTransactional &= TransactionSynchronizationManager.isActualTransactionActive();
            if (rejectItem) {
                throw new IllegalStateException("Test item listener rejected transaction");
            }
        }

        @EventListener
        public void session(SessionFinishedEvent event) {
            finished.add(event);
            allTransactional &= TransactionSynchronizationManager.isActualTransactionActive();
            if (rejectSession) {
                throw new IllegalStateException("Test session listener rejected transaction");
            }
        }
    }

    static class PracticeControllerResponses {
        static List<String> finishResponses() throws Exception {
            return java.util.Arrays.stream(com.cnhsk.learning.practice.web.PracticeController.class
                    .getMethod("finish", String.class, com.cnhsk.learning.practice.web.PracticeController.FinishRequest.class)
                    .getAnnotation(io.swagger.v3.oas.annotations.responses.ApiResponses.class).value())
                    .map(io.swagger.v3.oas.annotations.responses.ApiResponse::responseCode).toList();
        }
    }
}
