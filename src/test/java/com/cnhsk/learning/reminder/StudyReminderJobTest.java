package com.cnhsk.learning.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.ReminderSendResult;
import com.cnhsk.auth.api.ReminderTarget;
import com.cnhsk.learning.review.ReviewSessionService;
import com.cnhsk.learning.review.ReviewSessionService.ReviewSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class StudyReminderJobTest {

    private static final Instant NOW = Instant.parse("2026-11-14T13:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);
    private static final LocalTime HOUR = LocalTime.of(20, 0);
    private static final Duration DELAY = Duration.ofSeconds(2);
    private static final String PRIVATE_DETAIL = "private@example.test Private Display Name private-token";

    @Mock private AuthApi auth;
    @Mock private ReviewSessionService reviews;
    @Mock private StudyReminderClaims claims;
    @Mock private ReminderSleeper sleeper;

    private StudyReminderJob job;
    private Logger logger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        job = new StudyReminderJob(auth, reviews, claims, Clock.fixed(NOW, ZoneOffset.UTC), sleeper);
        logger = (Logger) LoggerFactory.getLogger(StudyReminderJob.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void detachLoggerAndClearOwnInterruptFlag() {
        logger.detachAppender(logs);
        logs.stop();
        Thread.interrupted();
    }

    @Test
    void FR013_emptyBatchStillLogsAllCounters() {
        targets();

        job.run();

        assertSummary(0, 0, 0, 0, 0, 0);
        verifyNoInteractions(reviews, claims, sleeper);
    }

    @Test
    void FR009_AT07_noCardsSkipsBeforeClaimOrSending() {
        targets(1);
        when(reviews.summary(1)).thenReturn(new ReviewSummary(0, null));

        job.run();

        assertSummary(1, 0, 1, 0, 0, 0);
        verifyNoInteractions(claims, sleeper);
        verify(auth, never()).sendStudyReminder(anyLong(), anyInt());
    }

    @Test
    void FR010_duplicateDoesNotSendOrReleaseSomeoneElsesClaim() {
        targets(1);
        due(1);
        when(claims.claim(1, TODAY)).thenReturn(OptionalLong.empty());

        job.run();

        assertSummary(1, 0, 0, 1, 0, 0);
        verify(auth, never()).sendStudyReminder(anyLong(), anyInt());
        verify(claims, never()).release(anyLong());
        verifyNoInteractions(sleeper);
    }

    @Test
    void FR007_FR010_sendsOnlyAfterSummaryAndCommittedClaim() {
        targets(1);
        claimable(1, 101);
        when(auth.sendStudyReminder(1, 5)).thenReturn(ReminderSendResult.SENT);

        job.run();

        var ordered = inOrder(auth, reviews, claims);
        ordered.verify(auth).reminderTargets(HOUR);
        ordered.verify(reviews).summary(1);
        ordered.verify(claims).claim(1, TODAY);
        ordered.verify(auth).sendStudyReminder(1, 5);
        verify(claims, never()).release(anyLong());
        verifyNoInteractions(sleeper);
        assertSummary(1, 1, 0, 0, 0, 0);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void FR011_retriesAtTwoSecondIntervalsWithoutClaimingAgain(int failures) throws Exception {
        targets(1);
        claimable(1, 101);
        AtomicInteger attempts = new AtomicInteger();
        when(auth.sendStudyReminder(1, 5)).thenAnswer(invocation -> {
            if (attempts.getAndIncrement() < failures) throw new MailSendException(PRIVATE_DETAIL);
            return ReminderSendResult.SENT;
        });

        job.run();

        verify(auth, times(failures + 1)).sendStudyReminder(1, 5);
        verify(sleeper, times(failures)).sleep(DELAY);
        verify(claims).claim(1, TODAY);
        verify(claims, never()).release(anyLong());
        assertSummary(1, 1, 0, 0, 0, 0);
        assertPrivateDetailsAbsent();
    }

    @Test
    void FR011_FR012_AT10_exhaustedRetriesReleaseOnlyAWhileBStillSends() throws Exception {
        targets(1, 2);
        claimable(1, 101);
        claimable(2, 102);
        when(auth.sendStudyReminder(1, 5)).thenThrow(new MailSendException(PRIVATE_DETAIL));
        when(auth.sendStudyReminder(2, 5)).thenReturn(ReminderSendResult.SENT);

        job.run();

        verify(auth, times(3)).sendStudyReminder(1, 5);
        verify(auth).sendStudyReminder(2, 5);
        verify(sleeper, times(2)).sleep(DELAY);
        verify(claims).release(101);
        verify(claims, never()).release(102);
        assertSummary(2, 1, 0, 0, 0, 1);
        assertPrivateDetailsAbsent();
    }

    @Test
    void Q_T04_01_skippedDeliveryReleasesOwnClaimWithoutRetryOrSentCount() {
        targets(1);
        claimable(1, 101);
        when(auth.sendStudyReminder(1, 5)).thenReturn(ReminderSendResult.SKIPPED);

        job.run();

        verify(claims).release(101);
        verify(auth).sendStudyReminder(1, 5);
        verifyNoInteractions(sleeper);
        assertSummary(1, 0, 0, 0, 1, 0);
    }

    @Test
    void Q_T04_01_skippedRetryStopsFurtherRetriesAndReleasesClaim() throws Exception {
        targets(1);
        claimable(1, 101);
        when(auth.sendStudyReminder(1, 5)).thenThrow(new MailSendException(PRIVATE_DETAIL))
                .thenReturn(ReminderSendResult.SKIPPED);

        job.run();

        verify(auth, times(2)).sendStudyReminder(1, 5);
        verify(sleeper).sleep(DELAY);
        verify(claims).release(101);
        assertSummary(1, 0, 0, 0, 1, 0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void Q_T04_02_interruptAttemptsReleaseRestoresFlagStopsBatchAndLogsSummary(boolean releaseFails)
            throws Exception {
        targets(1, 2);
        claimable(1, 101);
        when(auth.sendStudyReminder(1, 5)).thenThrow(new MailSendException(PRIVATE_DETAIL));
        doThrow(new InterruptedException(PRIVATE_DETAIL)).when(sleeper).sleep(DELAY);
        if (releaseFails) doThrow(new IllegalStateException(PRIVATE_DETAIL)).when(claims).release(101);

        job.run();

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verify(claims).release(101);
        verify(reviews, never()).summary(2);
        verify(auth, never()).sendStudyReminder(eq(2L), anyInt());
        assertSummary(2, 0, 0, 0, 0, 1);
        assertPrivateDetailsAbsent();
    }

    @Test
    void FR012_summaryFailureDoesNotClaimAndDoesNotStopTheNextUser() {
        targets(1, 2);
        when(reviews.summary(1)).thenThrow(new IllegalStateException(PRIVATE_DETAIL));
        claimable(2, 102);
        when(auth.sendStudyReminder(2, 5)).thenReturn(ReminderSendResult.SENT);

        job.run();

        verify(claims, never()).claim(1, TODAY);
        verify(auth).sendStudyReminder(2, 5);
        assertSummary(2, 1, 0, 0, 0, 1);
        assertPrivateDetailsAbsent();
    }

    @Test
    void FR012_claimFailureDoesNotSendAndStillLogsSummary() {
        targets(1);
        due(1);
        when(claims.claim(1, TODAY)).thenThrow(new IllegalStateException(PRIVATE_DETAIL));

        job.run();

        verify(auth, never()).sendStudyReminder(anyLong(), anyInt());
        verifyNoInteractions(sleeper);
        assertSummary(1, 0, 0, 0, 0, 1);
        assertPrivateDetailsAbsent();
    }

    @Test
    void FR013_targetLookupFailureLogsOnlyItsTypeAndFinalCounters() {
        when(auth.reminderTargets(HOUR)).thenThrow(new IllegalStateException(PRIVATE_DETAIL));

        job.run();

        verifyNoInteractions(reviews, claims, sleeper);
        assertSummary(0, 0, 0, 0, 0, 1);
        assertPrivateDetailsAbsent();
    }

    @ParameterizedTest
    @CsvSource({
            "2026-11-13T16:59:59Z, 2026-11-13, 23:00",
            "2026-11-13T17:00:00Z, 2026-11-14, 00:00",
            "2026-11-14T00:15:00Z, 2026-11-14, 07:00"
    })
    void FR007_hourAndDateUseVietnamIncludingUtcDayBoundaries(String instant, String date, String time) {
        LocalDate localDate = LocalDate.parse(date);
        LocalTime hour = LocalTime.parse(time);
        when(auth.reminderTargets(hour)).thenReturn(List.of(new ReminderTarget(1)));
        due(1);
        when(claims.claim(1, localDate)).thenReturn(OptionalLong.of(101));
        when(auth.sendStudyReminder(1, 5)).thenReturn(ReminderSendResult.SENT);
        StudyReminderJob timed = new StudyReminderJob(auth, reviews, claims,
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC), sleeper);

        timed.run();

        verify(auth).reminderTargets(hour);
        verify(claims).claim(1, localDate);
        verify(auth).sendStudyReminder(1, 5);
    }

    @Test
    void FR007_AT19_schedulerHasExactCronVietnamZoneAndCanBeDisabled() throws Exception {
        Scheduled scheduled = StudyReminderJob.class.getMethod("run").getAnnotation(Scheduled.class);
        assertThat(scheduled.cron()).isEqualTo("${cnhsk.reminder.cron:0 0 * * * *}");
        assertThat(scheduled.zone()).isEqualTo("Asia/Ho_Chi_Minh");
        MockEnvironment environment = new MockEnvironment();
        assertThat(environment.resolveRequiredPlaceholders(scheduled.cron())).isEqualTo("0 0 * * * *");
        environment.setProperty("cnhsk.reminder.cron", "-");
        assertThat(environment.resolveRequiredPlaceholders(scheduled.cron())).isEqualTo(Scheduled.CRON_DISABLED);
    }

    private void targets(long... ids) {
        when(auth.reminderTargets(HOUR)).thenReturn(Arrays.stream(ids).mapToObj(ReminderTarget::new).toList());
    }

    private void due(long userId) {
        when(reviews.summary(userId)).thenReturn(new ReviewSummary(5, null));
    }

    private void claimable(long userId, long claimId) {
        due(userId);
        when(claims.claim(userId, TODAY)).thenReturn(OptionalLong.of(claimId));
    }

    private void assertSummary(int candidates, int sent, int noCards, int duplicate, int ineligible, int failed) {
        assertThat(logs.list.stream().filter(event -> event.getLevel() == Level.INFO).toList())
                .singleElement().satisfies(event -> assertThat(event.getFormattedMessage()).isEqualTo(
                        "Study reminder run hour=20:00, candidates=" + candidates + ", sent=" + sent
                                + ", skippedNoCards=" + noCards + ", duplicate=" + duplicate
                                + ", skippedIneligible=" + ineligible + ", failed=" + failed));
    }

    private void assertPrivateDetailsAbsent() {
        assertThat(logs.list).allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).doesNotContain("private@example.test", "Private Display Name",
                    "private-token");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }
}
