package com.cnhsk.auth.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.RateLimiter;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

class ReminderUnsubscribeRateLimiterTest {

    private final RateLimiter counters = mock(RateLimiter.class);

    @Test
    void FR002_allowsTwentyAndRejectsTwentyOneWithRemainingMinuteRetry() {
        when(counters.increment(anyString(), any())).thenReturn(20L, 21L);
        var limiter = limiter("2031-11-15T13:00:20Z");
        assertThatCode(() -> limiter.check("192.0.2.1")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.check("192.0.2.1"))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(error.getCode()).isEqualTo(ErrorCode.RATE_LIMITED);
                    assertThat(error.getExtra()).containsOnlyKeys("retryAfterSeconds")
                            .containsEntry("retryAfterSeconds", 40L);
                });
    }

    @Test
    void FR002_usesAnIndependentUtcCalendarMinuteKeyAndRoundsTtlUp() {
        limiter("2031-11-15T13:00:59.999Z").check("2001:db8::1");
        limiter("2031-11-15T13:01:00Z").check("2001:db8::1");
        limiter("2031-11-15T13:01:00Z").check("2001:db8::2");
        verify(counters).increment("reminder:unsubscribe:2001:db8::1:203111151300", Duration.ofSeconds(1));
        verify(counters).increment("reminder:unsubscribe:2001:db8::1:203111151301", Duration.ofSeconds(60));
        verify(counters).increment("reminder:unsubscribe:2001:db8::2:203111151301", Duration.ofSeconds(60));
    }

    @Test
    void FR002_redisFailureAllowsRequestAndLogsOnlyAFixedWarningWithoutThrowable() {
        when(counters.increment(anyString(), any())).thenThrow(
                new IllegalStateException("fixture-token userId=42 192.0.2.1 connection-secret"));
        Logger logger = (Logger) LoggerFactory.getLogger(ReminderUnsubscribeRateLimiter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatCode(() -> limiter("2031-11-15T13:00:20Z").check("192.0.2.1"))
                    .doesNotThrowAnyException();
            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel().toString()).isEqualTo("WARN");
                assertThat(event.getFormattedMessage())
                        .isEqualTo("Reminder unsubscribe rate limit unavailable; allowing request");
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getArgumentArray()).isNullOrEmpty();
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private ReminderUnsubscribeRateLimiter limiter(String now) {
        return new ReminderUnsubscribeRateLimiter(counters,
                Clock.fixed(Instant.parse(now), ZoneOffset.UTC));
    }
}
