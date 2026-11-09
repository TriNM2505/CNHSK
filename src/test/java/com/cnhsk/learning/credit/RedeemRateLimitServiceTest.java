package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(OutputCaptureExtension.class)
class RedeemRateLimitServiceTest {
    private static final String USER = "billing:redeem:fail:user:42";
    private static final String IP = "billing:redeem:fail:ip:192.0.2.42";
    private static final Instant NOW = Instant.parse("2026-11-08T00:00:00Z");
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final RateLimiter limiter = mock(RateLimiter.class);
    private final RedeemRateLimitService service = new RedeemRateLimitService(redis, limiter, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach void values() { when(redis.opsForValue()).thenReturn(values); }

    @Test void exactCountersAreReadAndBelowThresholdDoesNotReadExpiry() {
        when(values.get(USER)).thenReturn("4");
        when(values.get(IP)).thenReturn("29");
        service.check(42, "192.0.2.42");
        verify(values).get(USER);
        verify(values).get(IP);
        verify(redis, never()).getExpire(anyString(), any(TimeUnit.class));
        verifyNoInteractions(limiter);
    }

    @Test void thresholdsUseMaximumRemainingTtlRoundedUpWithoutIncrementing() {
        when(values.get(USER)).thenReturn("5");
        when(values.get(IP)).thenReturn("30");
        when(redis.getExpire(USER, TimeUnit.MILLISECONDS)).thenReturn(1001L);
        when(redis.getExpire(IP, TimeUnit.MILLISECONDS)).thenReturn(21001L);
        assertThatThrownBy(() -> service.check(42, "192.0.2.42")).isInstanceOf(ApiException.class)
                .satisfies(f -> {
                    ApiException api = (ApiException) f;
                    assertThat(api.getCode()).isEqualTo(ErrorCode.RATE_LIMITED);
                    assertThat(api.getStatus().value()).isEqualTo(429);
                    assertThat(api.getExtra()).containsEntry("retryAfterSeconds", 22L);
                });
        verify(limiter, never()).increment(anyString(), any());
    }

    @Test void failureIncrementsBothExactKeysWithOneHourWindow() {
        service.recordFailure(42, "192.0.2.42");
        verify(limiter).increment(USER, Duration.ofHours(1));
        verify(limiter).increment(IP, Duration.ofHours(1));
        verifyNoInteractions(values);
    }

    @Test void expiryRaceRetriesAndNewWindowBelowThresholdIsAllowed() {
        when(values.get(USER)).thenReturn("5", "1");
        when(redis.getExpire(USER, TimeUnit.MILLISECONDS)).thenReturn(3600000L);
        service.check(42, "192.0.2.42");
        verify(values, times(2)).get(USER);
    }

    @Test void disappearedCounterRetriesWithoutBlocking() {
        when(values.get(USER)).thenReturn("5", null);
        when(redis.getExpire(USER, TimeUnit.MILLISECONDS)).thenReturn(-2L);
        service.check(42, "192.0.2.42");
    }

    @Test void elapsedExpiryDoesNotBlock() {
        when(values.get(USER)).thenReturn("5");
        when(redis.getExpire(USER, TimeUnit.MILLISECONDS)).thenReturn(0L);
        service.check(42, "192.0.2.42");
    }

    @Test void redisReadFailureFailsOpenAndWarnsOnlyClass(CapturedOutput output) {
        when(values.get(USER)).thenThrow(new IllegalStateException("synthetic-raw-hash-read",
                new RuntimeException("synthetic-private-cause")));
        service.check(42, "192.0.2.42");
        assertSafe(output);
    }

    @Test void redisExpiryFailureFailsOpenAndWarnsOnlyClass(CapturedOutput output) {
        when(values.get(USER)).thenReturn("5");
        when(redis.getExpire(USER, TimeUnit.MILLISECONDS)).thenThrow(new IllegalStateException("synthetic-raw-hash-read",
                new RuntimeException("synthetic-private-cause")));
        service.check(42, "192.0.2.42");
        assertSafe(output);
    }

    @Test void eachCounterWriteFailsOpenIndependentlyAndWarnsOnlyClass(CapturedOutput output) {
        doThrow(new IllegalStateException("synthetic-raw-hash-read", new RuntimeException("synthetic-private-cause")))
                .when(limiter).increment(USER, Duration.ofHours(1));
        service.recordFailure(42, "192.0.2.42");
        verify(limiter).increment(IP, Duration.ofHours(1));
        assertSafe(output);
    }

    @Test void malformedNegativeAndUnexpiringCountersFailOpen(CapturedOutput output) {
        for (String value : java.util.List.of("synthetic-counter-input", "-1", "5")) {
            when(values.get(USER)).thenReturn(value);
            when(redis.getExpire(USER, TimeUnit.MILLISECONDS)).thenReturn(-1L);
            service.check(42, "192.0.2.42");
        }
        assertThat(output.getAll()).contains("WARN").doesNotContain("synthetic-counter-input");
    }

    @Test void remainingTtlRoundsUpAtMillisecondBoundariesAndNeverExceedsTheWindow() {
        when(values.get(USER)).thenReturn("5");
        long[] remaining = {1, 999, 1000, 1001, 3599999, 3600000, 3600001, Long.MAX_VALUE};
        long[] expected = {1, 1, 1, 2, 3600, 3600, 3600, 3600};
        for (int index = 0; index < remaining.length; index++) {
            when(redis.getExpire(USER, TimeUnit.MILLISECONDS)).thenReturn(remaining[index]);
            long retry = expected[index];
            assertThatThrownBy(() -> service.check(42, "192.0.2.42")).isInstanceOf(ApiException.class)
                    .satisfies(f -> assertThat(((ApiException) f).getExtra()).containsEntry("retryAfterSeconds", retry));
        }
        verify(limiter, never()).increment(anyString(), any());
    }

    @Test void retryAfterDoesNotConsultTheJvmWallClock() {
        Clock unrelatedClock = mock(Clock.class);
        when(unrelatedClock.millis()).thenThrow(new AssertionError("The JVM clock must not determine a Redis TTL."));
        RedeemRateLimitService independent = new RedeemRateLimitService(redis, limiter, unrelatedClock);
        when(values.get(USER)).thenReturn("5");
        when(values.get(IP)).thenReturn("30");
        when(redis.getExpire(USER, TimeUnit.MILLISECONDS)).thenReturn(1001L);
        when(redis.getExpire(IP, TimeUnit.MILLISECONDS)).thenReturn(21001L);
        assertThatThrownBy(() -> independent.check(42, "192.0.2.42")).isInstanceOf(ApiException.class)
                .satisfies(f -> assertThat(((ApiException) f).getExtra()).containsEntry("retryAfterSeconds", 22L));
        verifyNoInteractions(unrelatedClock);
    }

    @Test void nullTtlFailsOpenAndWarnsOnlyClass(CapturedOutput output) {
        when(values.get(USER)).thenReturn("5");
        when(redis.getExpire(USER, TimeUnit.MILLISECONDS)).thenReturn(null);
        service.check(42, "192.0.2.42");
        assertSafe(output);
    }

    private static void assertSafe(CapturedOutput output) {
        assertThat(output.getAll()).contains("WARN", "IllegalStateException")
                .doesNotContain("synthetic-raw-hash-read", "synthetic-private-cause", USER, IP);
    }
}

