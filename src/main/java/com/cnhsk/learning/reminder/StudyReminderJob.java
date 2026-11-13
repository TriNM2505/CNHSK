package com.cnhsk.learning.reminder;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.OptionalLong;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.ReminderSendResult;
import com.cnhsk.auth.api.ReminderTarget;
import com.cnhsk.learning.review.ReviewSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Sends due-study reminders without holding database transactions during SMTP or retries. */
@Component
public class StudyReminderJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(StudyReminderJob.class);
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Duration RETRY_DELAY = Duration.ofSeconds(2);
    private static final int MAX_RETRIES = 2;

    private final AuthApi auth;
    private final ReviewSessionService reviews;
    private final StudyReminderClaims claims;
    private final Clock clock;
    private final ReminderSleeper sleeper;

    public StudyReminderJob(AuthApi auth, ReviewSessionService reviews, StudyReminderClaims claims,
            Clock clock, ReminderSleeper sleeper) {
        this.auth = auth;
        this.reviews = reviews;
        this.claims = claims;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    @Scheduled(cron = "${cnhsk.reminder.cron:0 0 * * * *}", zone = "Asia/Ho_Chi_Minh")
    public void run() {
        ZonedDateTime startedAt = clock.instant().atZone(VIETNAM);
        LocalTime hour = startedAt.toLocalTime().truncatedTo(ChronoUnit.HOURS);
        LocalDate localDate = startedAt.toLocalDate();
        Counts counts = new Counts();
        try {
            List<ReminderTarget> targets = auth.reminderTargets(hour);
            counts.candidates = targets.size();
            for (ReminderTarget target : targets) {
                try {
                    switch (remind(target.userId(), localDate)) {
                        case SENT -> counts.sent++;
                        case NO_CARDS -> counts.skippedNoCards++;
                        case DUPLICATE -> counts.duplicate++;
                        case INELIGIBLE -> counts.skippedIneligible++;
                    }
                } catch (InterruptedException exception) {
                    counts.failed++;
                    Thread.currentThread().interrupt();
                    warn(target.userId(), exception);
                    break;
                } catch (RuntimeException exception) {
                    counts.failed++;
                    warn(target.userId(), exception);
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                }
            }
        } catch (RuntimeException exception) {
            counts.failed++;
            LOGGER.warn("Study reminder batch failed; errorType={}", exception.getClass().getSimpleName());
        } finally {
            LOGGER.info("Study reminder run hour={}, candidates={}, sent={}, skippedNoCards={}, duplicate={}, "
                            + "skippedIneligible={}, failed={}",
                    hour, counts.candidates, counts.sent, counts.skippedNoCards, counts.duplicate,
                    counts.skippedIneligible, counts.failed);
        }
    }

    private Outcome remind(long userId, LocalDate localDate) throws InterruptedException {
        int dueCount = reviews.summary(userId).dueCount();
        if (dueCount == 0) {
            return Outcome.NO_CARDS;
        }
        OptionalLong claim = claims.claim(userId, localDate);
        if (claim.isEmpty()) {
            return Outcome.DUPLICATE;
        }
        long claimId = claim.getAsLong();
        ReminderSendResult result;
        try {
            result = sendWithRetries(userId, dueCount);
        } catch (InterruptedException exception) {
            try {
                claims.release(claimId);
            } finally {
                Thread.currentThread().interrupt();
            }
            throw exception;
        } catch (RuntimeException exception) {
            claims.release(claimId);
            throw exception;
        }
        if (result == ReminderSendResult.SKIPPED) {
            claims.release(claimId);
            return Outcome.INELIGIBLE;
        }
        return Outcome.SENT;
    }

    private ReminderSendResult sendWithRetries(long userId, int dueCount) throws InterruptedException {
        for (int retry = 0; ; retry++) {
            try {
                return auth.sendStudyReminder(userId, dueCount);
            } catch (RuntimeException exception) {
                if (retry == MAX_RETRIES) {
                    throw exception;
                }
                sleeper.sleep(RETRY_DELAY);
            }
        }
    }

    private static void warn(long userId, Exception exception) {
        // Exception messages and causes can contain the recipient, display name or token.
        LOGGER.warn("Study reminder failed; userId={}, errorType={}", userId, exception.getClass().getSimpleName());
    }

    private enum Outcome { SENT, NO_CARDS, DUPLICATE, INELIGIBLE }

    private static class Counts {
        private int candidates;
        private int sent;
        private int skippedNoCards;
        private int duplicate;
        private int skippedIneligible;
        private int failed;
    }
}
