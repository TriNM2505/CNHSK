package com.cnhsk.learning.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.ReminderSettings;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.learning.review.ReviewSessionService;
import com.cnhsk.learning.review.ReviewSessionService.ReviewSummary;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TodayReminderServiceTest {

    @Mock private AuthApi auth;
    @Mock private ReviewSessionService reviews;

    @ParameterizedTest(name = "{0}, reminder={1}, web={2}, configured={3}, due={4}: show={5}")
    @CsvSource({
            "2026-11-14T12:59:59.999999999Z, 20:00, true, true, 3, false",
            "2026-11-14T13:00:00Z, 20:00, true, true, 3, true",
            "2026-11-14T13:00:00.000000001Z, 20:00, true, true, 3, true",
            "2026-11-14T13:30:00Z, 20:00, true, true, 3, true",
            "2026-11-14T13:30:00Z, 20:00, true, true, 0, false",
            "2026-11-14T13:30:00Z, 20:00, false, true, 3, false",
            "2026-11-14T13:30:00Z, 20:00, true, false, 3, true",
            "2026-11-14T16:59:59Z, 23:00, true, true, 3, true",
            "2026-11-14T17:30:00Z, 20:00, true, true, 3, false",
            "2026-11-14T17:00:00Z, 00:00, true, true, 3, true",
            "2026-11-14T16:00:00Z, 23:00, true, true, 3, true"
    })
    void AT18_usesVietnamTimeWebPreferenceAndDueCardsWithoutCheckingConfiguration(
            String instant, String reminderTime, boolean web, boolean configured, int dueCount, boolean show) {
        when(auth.currentUser()).thenReturn(new CurrentUser(42L, Set.of(RoleCode.LEARNER), false));
        when(auth.reminderSettings(42L)).thenReturn(new ReminderSettings(
                LocalTime.parse(reminderTime), web, false, false, configured));
        when(reviews.summary(42L)).thenReturn(new ReviewSummary(dueCount, null));
        // The injected clock's own zone must not affect the Vietnam comparison.
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneId.of("America/New_York"));

        var result = new TodayReminderService(auth, reviews, clock).today();

        assertThat(result.show()).isEqualTo(show);
        assertThat(result.dueCount()).isEqualTo(dueCount);
        assertThat(result.reminderTime()).isEqualTo(reminderTime);
        verify(auth).currentUser();
        verify(auth).reminderSettings(42L);
        verify(reviews).summary(42L);
        verifyNoMoreInteractions(auth, reviews);
    }
}
