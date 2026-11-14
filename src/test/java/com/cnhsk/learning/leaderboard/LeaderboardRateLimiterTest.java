package com.cnhsk.learning.leaderboard;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.RateLimiter;
import org.junit.jupiter.api.Test;

class LeaderboardRateLimiterTest {
    private final RateLimiter counters = mock(RateLimiter.class);
    private final Clock clock = mock(Clock.class);
    private final LeaderboardRateLimiter limiter = new LeaderboardRateLimiter(counters, clock);

    @Test void guest120IsAllowed121Is429AndWindowChangesAtNextMinute() {
        Instant now = Instant.parse("2026-11-16T00:00:59.500Z");
        when(clock.instant()).thenReturn(now);
        when(counters.increment(anyString(), any())).thenReturn(120L, 121L);
        limiter.check(null, "192.0.2.1");
        assertThatThrownBy(() -> limiter.check(null, "192.0.2.1")).isInstanceOfSatisfying(ApiException.class, ex -> {
            assertThat(ex.getCode()).isEqualTo(ErrorCode.RATE_LIMITED);
            assertThat(ex.getStatus().value()).isEqualTo(429);
            assertThat(ex.getExtra()).containsEntry("retryAfterSeconds", 1L);
        });
        verify(counters, times(2)).increment("lb:rate:guest:192.0.2.1:" + now.minusSeconds(59).getEpochSecond(), Duration.ofSeconds(1));
        when(clock.instant()).thenReturn(now.plusMillis(500));
        when(counters.increment(anyString(), any())).thenReturn(1L);
        limiter.check(null, "192.0.2.1");
        verify(counters).increment("lb:rate:guest:192.0.2.1:" + now.plusMillis(500).getEpochSecond(), Duration.ofSeconds(60));
    }

    @Test void authenticatedRequestsDoNotTouchCounterOrClock() {
        limiter.check(7L, "192.0.2.1");
        verifyNoInteractions(counters, clock);
    }

    @Test void counterFailureFailsOpen() {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-16T00:00:00Z"));
        when(counters.increment(anyString(), any())).thenThrow(new IllegalStateException("sensitive-url"));
        assertThatCode(() -> limiter.check(null, "192.0.2.1")).doesNotThrowAnyException();
    }
}
