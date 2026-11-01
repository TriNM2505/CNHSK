package com.cnhsk.community.common;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CommunityLimitsTest {

    @Test
    void FR008_FR035_dailyQuotasResetAtVietnamMidnightWithUtcClock() {
        CommunityLimits limits = limitsAt("2026-11-01T16:59:59Z");
        CommunityLimits.Snapshot beforeMidnight = limits.snapshot();

        assertThat(beforeMidnight.now()).isEqualTo(Instant.parse("2026-11-01T16:59:59Z"));
        assertThat(beforeMidnight.dayStart()).isEqualTo(Instant.parse("2026-10-31T17:00:00Z"));
        assertThat(beforeMidnight.nextDayStart()).isEqualTo(Instant.parse("2026-11-01T17:00:00Z"));
        limits.assertCanCreatePost(4, beforeMidnight);
        limits.assertCanReport(19, beforeMidnight);
        assertQuota(() -> limits.assertCanCreatePost(5, beforeMidnight), 1);
        assertQuota(() -> limits.assertCanReport(20, beforeMidnight), 1);

        CommunityLimits nextDayLimits = limitsAt("2026-11-01T17:00:00Z");
        CommunityLimits.Snapshot midnight = nextDayLimits.snapshot();
        assertThat(midnight.dayStart()).isEqualTo(beforeMidnight.nextDayStart());
        assertThat(midnight.nextDayStart()).isEqualTo(Instant.parse("2026-11-02T17:00:00Z"));
        nextDayLimits.assertCanCreatePost(0, midnight);
        nextDayLimits.assertCanReport(0, midnight);
        assertQuota(() -> nextDayLimits.assertCanCreatePost(5, midnight), 86_400);
        assertQuota(() -> nextDayLimits.assertCanReport(20, midnight), 86_400);
    }

    @Test
    void FR019_commentQuotaUsesOldestActiveCommentInsteadOfCalendarHour() {
        CommunityLimits limits = limitsAt("2026-11-01T05:30:00Z");
        CommunityLimits.Snapshot snapshot = limits.snapshot();
        Instant oldest = Instant.parse("2026-11-01T04:45:00Z");

        assertThat(snapshot.commentCutoff()).isEqualTo(Instant.parse("2026-11-01T04:30:00Z"));
        limits.assertCanComment(29, oldest, snapshot);
        limits.assertCanComment(0, null, snapshot);
        assertQuota(() -> limits.assertCanComment(30, oldest, snapshot), 900);
        assertQuota(() -> limits.assertCanComment(31, oldest, snapshot), 900);
    }

    @Test
    void FR019_commentAtExactCutoffIsExpired() {
        CommunityLimits limits = limitsAt("2026-11-01T05:30:00Z");
        CommunityLimits.Snapshot snapshot = limits.snapshot();

        limits.assertCanComment(29, null, snapshot);
        assertThatThrownBy(() -> limits.assertCanComment(30, snapshot.commentCutoff(), snapshot))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("oldestCreatedAt must be within the active comment window");
        assertQuota(() -> limits.assertCanComment(30, snapshot.commentCutoff().plusNanos(1), snapshot), 1);
    }

    @Test
    void FR019_blockedCountRequiresAnOldestCommentInsideTheWindow() {
        CommunityLimits limits = limitsAt("2026-11-01T05:30:00Z");
        CommunityLimits.Snapshot snapshot = limits.snapshot();
        for (Instant invalid : new Instant[]{null, snapshot.commentCutoff().minusNanos(1),
                snapshot.now().plusNanos(1)}) {
            assertThatThrownBy(() -> limits.assertCanComment(30, invalid, snapshot))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("oldestCreatedAt must be within the active comment window");
        }
        assertQuota(() -> limits.assertCanComment(30, snapshot.now(), snapshot), 3600);
    }

    @ParameterizedTest
    @CsvSource({"1, 1", "999999, 1", "1000000, 1", "1000000000, 1", "1000000001, 2", "1500000000, 2"})
    void FR019_retryRoundsUpWithoutDiscardingSubmillisecondPrecision(long remainingNanos, long seconds) {
        CommunityLimits limits = limitsAt("2026-11-01T05:30:00.123456789Z");
        CommunityLimits.Snapshot snapshot = limits.snapshot();

        assertQuota(() -> limits.assertCanComment(30,
                snapshot.commentCutoff().plusNanos(remainingNanos), snapshot), seconds);
    }

    @ParameterizedTest
    @CsvSource({
            "2026-11-01T16:59:59.999999999Z, 1",
            "2026-11-01T16:59:59.999Z, 1",
            "2026-11-01T16:59:58.999999999Z, 2",
            "2026-11-01T16:59:58.500Z, 2"
    })
    void FR008_FR035_dailyRetryRoundsUpFractionalSeconds(String time, long seconds) {
        CommunityLimits limits = limitsAt(time);
        CommunityLimits.Snapshot snapshot = limits.snapshot();

        assertQuota(() -> limits.assertCanCreatePost(5, snapshot), seconds);
        assertQuota(() -> limits.assertCanReport(20, snapshot), seconds);
    }

    @Test
    void FR008_FR019_FR035_allChecksUseOneCapturedInstant() {
        Clock clock = mock(Clock.class);
        Instant now = Instant.parse("2026-11-01T16:59:59Z");
        when(clock.instant()).thenReturn(now, now.plusSeconds(2));
        CommunityLimits limits = new CommunityLimits(clock, new CommunityProperties());

        CommunityLimits.Snapshot snapshot = limits.snapshot();
        assertQuota(() -> limits.assertCanCreatePost(5, snapshot), 1);
        assertQuota(() -> limits.assertCanReport(20, snapshot), 1);
        assertQuota(() -> limits.assertCanComment(30, now.minusSeconds(60), snapshot), 3540);

        verify(clock).instant();
        verifyNoMoreInteractions(clock);
    }

    @Test
    void FR008_FR019_FR035_eachQuotaUsesItsConfiguredThreshold() {
        CommunityProperties properties = new CommunityProperties();
        properties.setPostsPerDay(8);
        properties.setCommentsPerHour(45);
        properties.setReportsPerDay(25);
        CommunityLimits limits = new CommunityLimits(
                Clock.fixed(Instant.parse("2026-11-01T05:00:00Z"), ZoneOffset.UTC), properties);
        CommunityLimits.Snapshot snapshot = limits.snapshot();

        limits.assertCanCreatePost(7, snapshot);
        limits.assertCanComment(44, snapshot.now(), snapshot);
        limits.assertCanReport(24, snapshot);
        assertQuota(() -> limits.assertCanCreatePost(8, snapshot), 43_200);
        assertQuota(() -> limits.assertCanComment(45, snapshot.now(), snapshot), 3600);
        assertQuota(() -> limits.assertCanReport(25, snapshot), 43_200);
    }

    @Test
    void FR008_FR019_FR035_negativeCountsAreInternalErrors() {
        CommunityLimits limits = limitsAt("2026-11-01T05:00:00Z");
        CommunityLimits.Snapshot snapshot = limits.snapshot();

        assertThatThrownBy(() -> limits.assertCanCreatePost(-1, snapshot))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("count must not be negative");
        assertThatThrownBy(() -> limits.assertCanComment(-1, null, snapshot))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("count must not be negative");
        assertThatThrownBy(() -> limits.assertCanReport(-1, snapshot))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("count must not be negative");
    }

    private static CommunityLimits limitsAt(String time) {
        return new CommunityLimits(Clock.fixed(Instant.parse(time), ZoneOffset.UTC), new CommunityProperties());
    }

    private static void assertQuota(Runnable operation, long retryAfterSeconds) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.getCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
            assertThat(exception.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(exception.getMessage()).contains("giới hạn");
            assertThat(exception.getExtra()).containsOnlyKeys("retryAfterSeconds");
            assertThat(exception.getExtra().get("retryAfterSeconds")).isInstanceOf(Long.class)
                    .isEqualTo(retryAfterSeconds);
        });
    }
}
