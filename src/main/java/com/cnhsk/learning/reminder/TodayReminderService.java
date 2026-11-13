package com.cnhsk.learning.reminder;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.ReminderSettings;
import com.cnhsk.learning.review.ReviewSessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads the caller's web reminder without recording delivery or study activity. */
@Service
public class TodayReminderService {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final AuthApi auth;
    private final ReviewSessionService reviews;
    private final Clock clock;

    public TodayReminderService(AuthApi auth, ReviewSessionService reviews, Clock clock) {
        this.auth = auth;
        this.reviews = reviews;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TodayReminder today() {
        long userId = auth.currentUser().userId();
        ReminderSettings settings = auth.reminderSettings(userId);
        int dueCount = reviews.summary(userId).dueCount();
        LocalTime now = clock.instant().atZone(VIETNAM).toLocalTime();
        boolean show = settings.web() && !now.isBefore(settings.reminderTime()) && dueCount > 0;
        return new TodayReminder(show, dueCount, TIME_FORMAT.format(settings.reminderTime()));
    }

    public record TodayReminder(boolean show, int dueCount, String reminderTime) { }
}
