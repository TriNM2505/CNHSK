package com.cnhsk.learning.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler;
import com.cnhsk.learning.practice.entity.Attempt;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class QuizHandlerTest {
    private final QuizRepository repository = mock(QuizRepository.class);
    private final QuizConfig config = new QuizConfig();
    private final QuizHandler configuration = new QuizHandler();

    @Test
    void twoSourcesUseConfiguredServerLifetimeAndOnlyFirstSourceLocksAndChecksHistory() {
        config.setSessionMinutes(7);
        var first = configuration.firstQuizHandler(repository, config);
        var retry = configuration.retryQuizHandler(repository, config);
        assertThat(first.source()).isEqualTo(PracticeSource.QUIZ);
        assertThat(retry.source()).isEqualTo(PracticeSource.QUIZ_RETRY);
        assertThat(first.sessionPolicy().lifetime()).isEqualTo(Duration.ofMinutes(7));
        assertThat(retry.sessionPolicy().lifetime()).isEqualTo(Duration.ofMinutes(7));
        Attempt attempt = attempt();
        when(repository.hasFirstSubmission(1, 2, 3)).thenReturn(true);
        assertThat(first.prepareSubmission(attempt).scoreScale()).isEqualTo(100);
        var order = inOrder(repository);
        order.verify(repository).lockSubmission(1, 2);
        order.verify(repository).hasFirstSubmission(1, 2, 3);
        assertThat(attempt.getKind()).isEqualTo("PRACTICE");
        assertThat(attempt.getRefType()).isEqualTo("QUIZ_RETRY");
        clearInvocations(repository);
        assertThat(retry.prepareSubmission(attempt).scoreScale()).isEqualTo(100);
        verifyNoInteractions(repository);
    }

    @Test
    void firstWithoutHistoryStaysQuizAndTransitionRejectsRepeatedOrFinishedOrNonQuizAttempts() {
        Attempt first = attempt();
        configuration.firstQuizHandler(repository, config).prepareSubmission(first);
        assertThat(first.getRefType()).isEqualTo("QUIZ");
        first.setStatus("SUBMITTED");
        assertThatThrownBy(first::convertQuizToRetry).isInstanceOf(IllegalStateException.class);
        Attempt converted = attempt();
        converted.convertQuizToRetry();
        assertThatThrownBy(converted::convertQuizToRetry).isInstanceOf(IllegalStateException.class);
        Attempt practice = new Attempt(1L, "PRACTICE", "TOPIC_PRACTICE", 2L, Instant.EPOCH, Instant.MAX, null);
        assertThatThrownBy(practice::convertQuizToRetry).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void serverTimeThresholdIsStrictAndZeroDisablesIt() {
        var handler = configuration.firstQuizHandler(repository, config);
        var item = mock(PracticeSourceHandler.GradedItem.class);
        var items = java.util.Collections.nCopies(10, item);
        var fast = new PracticeSourceHandler.FinishContext(1, 3, PracticeSource.QUIZ, 2L, 80L, 100L,
                false, Instant.EPOCH, Instant.EPOCH.plusSeconds(12), items);
        var boundary = new PracticeSourceHandler.FinishContext(1, 3, PracticeSource.QUIZ_RETRY, 2L, 80L, 100L,
                false, Instant.EPOCH, Instant.EPOCH.plusSeconds(20), items);
        assertThat(handler.isSuspicious(fast)).isTrue();
        assertThat(handler.isSuspicious(boundary)).isFalse();
        assertThat(handler.onFinish(fast)).containsEntry("rankingEligible", true);
        assertThat(handler.onFinish(boundary)).containsEntry("rankingEligible", false);
        config.setMinSecondsPerQuestion(0);
        assertThat(handler.isSuspicious(fast)).isFalse();
    }

    private Attempt attempt() {
        Attempt attempt = new Attempt(1L, "QUIZ", "QUIZ", 2L, Instant.EPOCH, Instant.MAX, null);
        ReflectionTestUtils.setField(attempt, "id", 3L);
        return attempt;
    }

    @Test
    void redisFailurePropagatesWithoutInventingAQuizFallbackOrCreatingAnAttempt() {
        var picker = mock(QuizQuestionPicker.class);
        var practice = mock(com.cnhsk.learning.practice.PracticeService.class);
        var limiter = mock(com.cnhsk.shared.security.RateLimiter.class);
        var clock = java.time.Clock.fixed(Instant.EPOCH, java.time.ZoneOffset.UTC);
        var service = new QuizSessionService(repository, picker, config, practice, limiter, clock);
        when(repository.active(1, 2, Instant.EPOCH)).thenReturn(java.util.Optional.empty());
        when(repository.topicExists(2)).thenReturn(true);
        when(picker.pick(2, 10)).thenReturn(List.of());
        var unavailable = new IllegalStateException("simulated unavailable counter");
        when(limiter.increment("quiz:start:1", Duration.ofHours(1))).thenThrow(unavailable);
        assertThatThrownBy(() -> service.start(1, 2)).isSameAs(unavailable);
        verifyNoInteractions(practice);
    }
}
