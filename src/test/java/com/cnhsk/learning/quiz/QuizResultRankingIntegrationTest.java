package com.cnhsk.learning.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.cnhsk.learning.leaderboard.LeaderboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class QuizResultRankingIntegrationTest extends QuizIntegrationFixture {
    @MockitoBean LeaderboardService leaderboard;
    @Autowired QuizResultService results;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void rankLookupRunsAfterReadTransactionAndMapsRankAndTotal() throws Exception {
        Topic topic = topic(10, 0);
        long id = finish(topic, 20);
        when(leaderboard.rankOf(user, topic.id())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return Optional.of(new LeaderboardService.Rank(12, 240));
        });
        mvc.perform(get(path(id)).header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.firstAttempt.rank").value(12))
                .andExpect(jsonPath("$.firstAttempt.total").value(240));
        verify(leaderboard).rankOf(user, topic.id());
    }

    @Test
    void failedLookupWithItsOwnTransactionStillReturns200AndPreservesFirstSummary() throws Exception {
        Topic topic = topic(10, 0);
        long id = finish(topic, 20);
        AtomicBoolean outsideRead = new AtomicBoolean();
        when(leaderboard.rankOf(user, topic.id())).thenAnswer(invocation -> {
            outsideRead.set(!TransactionSynchronizationManager.isActualTransactionActive());
            // If accidentally invoked inside the result transaction, REQUIRED poisons it rollback-only.
            return new TransactionTemplate(transactions).execute(status -> {
                throw new IllegalStateException("fixture-sensitive-detail");
            });
        });
        String body = mvc.perform(get(path(id)).header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(80))
                .andExpect(jsonPath("$.firstAttempt.score").value(80))
                .andExpect(jsonPath("$.firstAttempt.durationMs").value(20000))
                .andExpect(jsonPath("$.firstAttempt.rank").isEmpty())
                .andExpect(jsonPath("$.firstAttempt.total").isEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(outsideRead).isTrue();
        assertThat(body).doesNotContain("fixture-sensitive-detail", "stackTrace", "UnexpectedRollbackException");
        verify(leaderboard).rankOf(user, topic.id());
    }

    @Test
    void callerTransactionIsSuspendedDuringLookupAndResumesWithoutRollbackOnly() {
        Topic topic = topic(10, 0);
        long id = finish(topic, 20);
        when(leaderboard.rankOf(user, topic.id())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return Optional.of(new LeaderboardService.Rank(1, 1));
        });
        var result = new TransactionTemplate(transactions).execute(status -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            var dto = results.result(user, id);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(status.isRollbackOnly()).isFalse();
            return dto;
        });
        assertThat(result.firstAttempt().rank()).isEqualTo(1);
    }

    @Test
    void suspiciousFirstSkipsBeanForBothFirstAndSubsequentRetry() {
        Topic topic = topic(10, 0);
        long first = finish(topic, 12);
        assertThat(results.result(user, first).firstAttempt().rank()).isNull();
        long retry = sessions.start(user, topic.id()).session().sessionId();
        advance(32);
        practice.finish(user, retry, answers(10));
        assertThat(results.result(user, retry).firstAttempt()).isEqualTo(
                new QuizResultService.FirstAttemptRank(80, 12000, null, null));
        verifyNoInteractions(leaderboard);
    }

    @Test
    void emptyRankStillReturns200AndRetryRankAlwaysBelongsToFirstAttempt() throws Exception {
        Topic topic = topic(10, 0);
        finish(topic, 20);
        long retry = sessions.start(user, topic.id()).session().sessionId();
        advance(40);
        practice.finish(user, retry, answers(10));
        when(leaderboard.rankOf(user, topic.id())).thenReturn(Optional.empty());
        mvc.perform(get(path(retry)).header("Authorization", bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(100)).andExpect(jsonPath("$.rankingEligible").value(false))
                .andExpect(jsonPath("$.firstAttempt.score").value(80)).andExpect(jsonPath("$.firstAttempt.rank").isEmpty());
        verify(leaderboard).rankOf(user, topic.id());
    }

    private long finish(Topic topic, long seconds) {
        long id = sessions.start(user, topic.id()).session().sessionId();
        advance(seconds);
        practice.finish(user, id, answers(8));
        return id;
    }

    private String path(long id) { return "/api/quiz-attempts/"+id+"/result"; }
}
