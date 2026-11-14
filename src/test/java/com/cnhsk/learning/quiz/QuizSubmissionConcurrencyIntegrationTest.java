package com.cnhsk.learning.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.cnhsk.learning.practice.PracticeService.FinishResult;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.event.ItemGradedEvent;
import com.cnhsk.learning.practice.event.SessionFinishedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.event.ApplicationEventMulticaster;

class QuizSubmissionConcurrencyIntegrationTest extends QuizIntegrationFixture {
    @Autowired ConfigurableApplicationContext application;

    @Test
    void concurrentFirstSubmissionsSerializeAndPersistOneFirstAndOneRetryWithoutUniqueViolation() throws Exception {
        Topic topic = topic(10, 0);
        // Legacy/two-device sessions already exist before submit; normal start itself resumes one session.
        long one = directQuiz(topic);
        long two = directQuiz(topic);
        advance(20);
        var published = new ConcurrentLinkedQueue<Object>();
        ApplicationListener<ApplicationEvent> listener = event -> {
            if (event instanceof PayloadApplicationEvent<?> payload) published.add(payload.getPayload());
        };
        List<FinishResult> results;
        application.addApplicationListener(listener);
        try {
            results = race(() -> practice.finish(user, one, answers(8)), () -> practice.finish(user, two, answers(10)));
        } finally {
            application.getBean("applicationEventMulticaster", ApplicationEventMulticaster.class)
                    .removeApplicationListener(listener);
        }
        assertThat(results).extracting(FinishResult::score).containsExactlyInAnyOrder(80L, 100L);
        assertThat(results).allSatisfy(result -> assertThat(result.status()).isEqualTo("SUBMITTED"));
        assertThat(results.stream().filter(r -> Boolean.TRUE.equals(r.extra().get("rankingEligible")))).hasSize(1);
        assertThat(jdbc.queryForList("SELECT kind || ':' || ref_type FROM learning.attempts WHERE user_id=? ORDER BY id",
                String.class, user)).containsExactlyInAnyOrder("QUIZ:QUIZ", "PRACTICE:QUIZ_RETRY");
        assertThat(jdbc.queryForList("SELECT ref_type FROM learning.study_events WHERE user_id=?", String.class, user))
                .containsExactlyInAnyOrder("QUIZ", "QUIZ_RETRY");
        for (var result : results) {
            boolean eligible = (Boolean) result.extra().get("rankingEligible");
            PracticeSource expected = eligible ? PracticeSource.QUIZ : PracticeSource.QUIZ_RETRY;
            assertThat(jdbc.queryForObject("SELECT ref_type FROM learning.attempts WHERE id=?", String.class, result.sessionId()))
                    .isEqualTo(eligible ? "QUIZ" : "QUIZ_RETRY");
            assertThat(published.stream().filter(ItemGradedEvent.class::isInstance).map(ItemGradedEvent.class::cast)
                    .filter(event -> event.sessionId() == result.sessionId())).hasSize(10).allSatisfy(event ->
                            assertThat(event.source()).isEqualTo(expected));
            assertThat(published.stream().filter(SessionFinishedEvent.class::isInstance).map(SessionFinishedEvent.class::cast)
                    .filter(event -> event.sessionId() == result.sessionId())).hasSize(1).allSatisfy(event -> {
                        assertThat(event.source()).isEqualTo(expected);
                        assertThat(event.score()).isEqualTo(result.score());
                        assertThat(event.maxScore()).isEqualTo(100);
                    });
        }
    }

    @Test
    void concurrentStartsResumeOneSessionAndConsumeOnlyOneQuotaEntry() throws Exception {
        Topic topic = topic(10, 0);
        var results = race(() -> sessions.start(user, topic.id()), () -> sessions.start(user, topic.id()));
        assertThat(results.get(0).session().sessionId()).isEqualTo(results.get(1).session().sessionId());
        assertThat(results.stream().filter(QuizSessionService.Started::created)).hasSize(1);
        assertThat(redisCount()).isEqualTo("1");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.attempts WHERE user_id=?", Integer.class, user)).isEqualTo(1);
    }

    @Test
    void concurrentStartAndFinishCompleteWithoutLockCycle() throws Exception {
        Topic topic = topic(10, 0);
        long id = sessions.start(user, topic.id()).session().sessionId();
        advance(20);
        race(() -> (Object) sessions.start(user, topic.id()), () -> practice.finish(user, id, answers(10)));
        var current = sessions.start(user, topic.id()).session();
        assertThat(current.rankingEligible()).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.attempts WHERE user_id=? AND kind='QUIZ' AND status='SUBMITTED'",
                Integer.class, user)).isEqualTo(1);
    }

    private <T> List<T> race(Callable<T> one, Callable<T> two) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var barrier = new CyclicBarrier(2);
        try {
            var first = pool.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return one.call(); });
            var second = pool.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return two.call(); });
            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
