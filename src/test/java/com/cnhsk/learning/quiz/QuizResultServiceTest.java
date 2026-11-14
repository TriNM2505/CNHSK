package com.cnhsk.learning.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cnhsk.learning.leaderboard.LeaderboardService;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.PracticeService.ServedSnapshot;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.Skill;
import com.cnhsk.learning.practice.entity.Attempt;
import com.cnhsk.learning.practice.entity.AttemptItem;
import com.cnhsk.learning.practice.entity.AttemptItemRepository;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class QuizResultServiceTest {
    private final AttemptRepository attempts = mock(AttemptRepository.class);
    private final AttemptItemRepository evidence = mock(AttemptItemRepository.class);
    private final QuizRepository quizzes = mock(QuizRepository.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<LeaderboardService> provider = mock(ObjectProvider.class);
    private final LeaderboardService leaderboard = mock(LeaderboardService.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private QuizResultService service;
    private Attempt attempt;

    @BeforeEach
    void setup() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new QuizResultService(attempts, evidence, quizzes, mapper, provider, transactions);
        var items = List.of(item(2), item(1), item(3));
        attempt = new Attempt(7L, "QUIZ", "QUIZ", 9L, Instant.EPOCH, Instant.EPOCH.plusSeconds(1800),
                mapper.valueToTree(new ServedSnapshot(Map.of(), items)));
        ReflectionTestUtils.setField(attempt, "id", 42L);
        attempt.setStatus("SUBMITTED");
        attempt.setSubmittedAt(Instant.EPOCH.plusSeconds(20));
        attempt.setDurationMs(20000);
        attempt.setScore(33L);
        when(attempts.findById(42L)).thenReturn(Optional.of(attempt));
        when(quizzes.topicName(9)).thenReturn(Optional.of("Chủ đề tự soạn"));
        AttemptItem correct = new AttemptItem(42L, 1, "QUESTION_CHOICE", "READING");
        correct.setAnswer("A");
        correct.setCorrect(true);
        AttemptItem skipped = new AttemptItem(42L, 2, "QUESTION_CHOICE", "READING");
        skipped.setAnswer("B");
        skipped.setCorrect(false);
        skipped.setMeta(Map.of("skipped", true));
        when(evidence.findByAttemptIdOrderByOrderIndexAsc(42L)).thenReturn(List.of(correct, skipped));
    }

    @Test
    void usesStoredScoreEvidenceAndSnapshotOrderWithExplicitNullableFields() throws Exception {
        var result = service.result(7, 42);
        assertThat(result.score()).isEqualTo(33);
        assertThat(result.correctCount()).isEqualTo(1);
        assertThat(result.wrongCount()).isEqualTo(2);
        assertThat(result.durationMs()).isEqualTo(20000);
        assertThat(result.items()).extracting(QuizResultService.QuizResultItem::itemId).containsExactly(2L,1L,3L);
        assertThat(result.items().get(0).chosen()).isNull();
        assertThat(result.items().get(1).chosen()).isEqualTo("A");
        assertThat(result.items().get(2).chosen()).isNull();
        var json = mapper.valueToTree(result);
        assertThat(json.path("items").get(0).get("explanation").isNull()).isTrue();
        assertThat(json.path("firstAttempt").get("rank").isNull()).isTrue();
        verify(transactions).getTransaction(argThat(definition -> definition.isReadOnly()
                && definition.getPropagationBehavior() == org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        verifyNoInteractions(leaderboard);
    }

    @Test
    void ownershipPrecedesSourceStatusSnapshotAndRankingAccess() {
        attempt.setStatus("IN_PROGRESS");
        assertThatThrownBy(() -> service.result(8, 42)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ATTEMPT_NOT_OWNED));
        assertThatThrownBy(() -> service.result(7, 99)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ATTEMPT_NOT_OWNED));
        verifyNoInteractions(evidence, quizzes, provider);
    }

    @ParameterizedTest
    @ValueSource(strings = {"IN_PROGRESS", "ABANDONED", "EXPIRED"})
    void unsubmittedQuizNeverReadsAnswersOrRank(String status) {
        attempt.setStatus(status);
        assertThatThrownBy(() -> service.result(7, 42)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getCode()).isEqualTo(ErrorCode.ATTEMPT_NOT_SUBMITTED);
            assertThat(e.getStatus().value()).isEqualTo(422);
        });
        verifyNoInteractions(evidence, quizzes, provider);
    }

    @Test
    void ownNonQuizReturns404AndForeignNonQuizStillReturns403() {
        ReflectionTestUtils.setField(attempt, "refType", "MOCK_EXAM");
        assertThatThrownBy(() -> service.result(7, 42)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getCode()).isEqualTo(ErrorCode.QUIZ_ATTEMPT_NOT_FOUND);
            assertThat(e.getStatus().value()).isEqualTo(404);
        });
        assertThatThrownBy(() -> service.result(8, 42)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ATTEMPT_NOT_OWNED));
        verifyNoInteractions(evidence, quizzes, provider);
    }

    @Test
    void retryAndRankAreMappedAfterReadTransactionHasCommitted() {
        ReflectionTestUtils.setField(attempt, "refType", "QUIZ_RETRY");
        ReflectionTestUtils.setField(attempt, "kind", "PRACTICE");
        when(quizzes.firstSubmission(7, 9)).thenReturn(Optional.of(new QuizRepository.FirstSubmission(80, null, false)));
        when(provider.getIfAvailable()).thenReturn(leaderboard);
        when(leaderboard.rankOf(7, 9)).thenReturn(Optional.of(new LeaderboardService.Rank(12, 240)));
        var result = service.result(7, 42);
        assertThat(result.rankingEligible()).isFalse();
        assertThat(result.firstAttempt()).isEqualTo(new QuizResultService.FirstAttemptRank(80, null, 12L, 240L));
        var order = inOrder(transactions, leaderboard);
        order.verify(transactions).getTransaction(any());
        order.verify(transactions).commit(any());
        order.verify(leaderboard).rankOf(7, 9);
    }

    @Test
    void suspiciousFirstSkipsLookupEvenIfCurrentRetryIsNotSuspicious() {
        ReflectionTestUtils.setField(attempt, "refType", "QUIZ_RETRY");
        when(quizzes.firstSubmission(7, 9)).thenReturn(Optional.of(new QuizRepository.FirstSubmission(80, 12000, true)));
        var result = service.result(7, 42);
        assertThat(result.firstAttempt()).isEqualTo(new QuizResultService.FirstAttemptRank(80, 12000, null, null));
        verifyNoInteractions(provider, leaderboard);
    }

    @Test
    void suspiciousCurrentFirstIsStillFirstAndMissingFirstIsAnExplicitNull() {
        attempt.setSuspicious(true);
        assertThat(service.result(7, 42).rankingEligible()).isTrue();
        ReflectionTestUtils.setField(attempt, "refType", "QUIZ_RETRY");
        when(quizzes.firstSubmission(7, 9)).thenReturn(Optional.empty());
        assertThat(service.result(7, 42).firstAttempt()).isNull();
        verifyNoInteractions(provider, leaderboard);
    }

    @Test
    void emptyRankAndFailedLookupKeepSummaryAndWarnWithoutExceptionDetails() {
        when(provider.getIfAvailable()).thenReturn(leaderboard);
        when(leaderboard.rankOf(7, 9)).thenReturn(Optional.empty());
        assertThat(service.result(7, 42).firstAttempt().rank()).isNull();
        when(leaderboard.rankOf(7, 9)).thenThrow(new IllegalStateException("fixture-sensitive-detail"));
        Logger logger = (Logger) LoggerFactory.getLogger(QuizResultService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThat(service.result(7, 42).firstAttempt()).isEqualTo(
                    new QuizResultService.FirstAttemptRank(33, 20000, null, null));
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
            assertThat(appender.list.get(0).getFormattedMessage()).isEqualTo("Quiz ranking unavailable: sessionId=42, topicId=9");
            assertThat(appender.list.get(0).getThrowableProxy()).isNull();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void leaderboardContractIsOnlyAnUnannotatedInterfaceWithTheApprovedMethodAndRecord() {
        assertThat(LeaderboardService.class.isInterface()).isTrue();
        assertThat(LeaderboardService.class.getDeclaredAnnotations()).isEmpty();
        assertThat(LeaderboardService.class.getDeclaredMethods()).hasSize(1);
        assertThat(LeaderboardService.class.getDeclaredMethods()[0].getName()).isEqualTo("rankOf");
        assertThat(LeaderboardService.Rank.class.getRecordComponents()).extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("rank", "total");
    }

    private ServedItem item(int no) {
        return new ServedItem(no, ItemType.QUESTION_CHOICE, Skill.READING, null,
                new ServedItem.Prompt("Câu tự soạn " + no, null, null, null, null, null, null, null, null),
                List.of(new ServedItem.Option("A", "nước"), new ServedItem.Option("B", "lửa")),
                "A", null, null, new ServedItem.Rules(0,0,false));
    }
}
