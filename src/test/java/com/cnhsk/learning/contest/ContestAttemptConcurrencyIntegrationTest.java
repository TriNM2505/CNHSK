package com.cnhsk.learning.contest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.cnhsk.learning.api.ContestAttemptApi.ContestSession;
import com.cnhsk.learning.contest.ContestAttemptIntegrationTest.Fixture;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeSourceHandler.Answer;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ContestAttemptConcurrencyIntegrationTest extends AbstractIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-14T04:00:00Z");
    @Autowired private ContestAttemptService contests;
    @Autowired private PracticeService practice;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private Clock clock;
    private Fixture fixture;
    private Instant now;
    private long user;
    private long exam;

    @BeforeEach
    void setup() {
        now = NOW;
        when(clock.instant()).thenAnswer(ignored -> now);
        when(clock.millis()).thenAnswer(ignored -> now.toEpochMilli());
        fixture = new Fixture(jdbc);
        user = fixture.user();
        exam = fixture.exam("DRAFT");
        fixture.question(exam, "Chọn nghĩa của 水.", 0, 1, "APPROVED", "MULTIPLE_CHOICE", 10);
    }

    @AfterEach
    void cleanup() {
        fixture.close();
    }

    @Test
    void AT02_twentyConcurrentStartsCreateOneAttemptAndReturnTheSameSnapshot() throws Exception {
        List<ContestSession> results = together(20, () -> contests.start(user, exam, exam, NOW.plusSeconds(60)));
        assertThat(results).hasSize(20);
        assertThat(results.stream().filter(result -> !result.resumed()).count()).isEqualTo(1);
        ContestSession first = results.get(0);
        assertThat(results).allSatisfy(result -> {
            assertThat(result.sessionId()).isEqualTo(first.sessionId());
            assertThat(result.deadline()).isEqualTo(NOW.plusSeconds(60));
            assertThat(result.items()).isEqualTo(first.items());
        });
        assertThat(contests.countAttempts(exam)).isEqualTo(1);
    }

    @Test
    void FR004_twoConcurrentSubmissionsCommitOnceAndReturnOneConflictWithoutDeadlock() throws Exception {
        var session = contests.start(user, exam, exam, NOW.plusSeconds(60));
        now = NOW.plusSeconds(20);
        practice.answer(user, session.sessionId(), new Answer(1, "A", 3000, 0, false, false, null));
        List<Object> results = together(2, () -> {
            // A caller transaction must not keep an attempt lock across the engine's REQUIRES_NEW.
            return new TransactionTemplate(transactionManager).execute(status -> {
                try {
                    return contests.submit(user, exam);
                } catch (ApiException exception) {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    return exception.getCode();
                }
            });
        });
        assertThat(results).containsExactlyInAnyOrder(now, ErrorCode.CONTEST_ALREADY_ATTEMPTED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.attempt_items WHERE attempt_id=?", Long.class, session.sessionId()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.study_events WHERE user_id=?", Long.class, user)).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT status,score,max_score FROM learning.attempts WHERE id=?", session.sessionId()))
                .containsEntry("status", "SUBMITTED").containsEntry("score", 1L).containsEntry("max_score", 1L);
    }

    private static <T> List<T> together(int count, Callable<T> action) throws Exception {
        var executor = Executors.newFixedThreadPool(count);
        var ready = new CountDownLatch(count);
        var go = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start barrier timed out");
                    return action.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally {
            go.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
