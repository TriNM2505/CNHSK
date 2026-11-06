package com.cnhsk.learning.library;

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

class LibraryRateLimiterTest {

    private final RateLimiter counters = mock(RateLimiter.class);

    @Test
    void FR002_AT02_allowsTheLastGuestRequestAndRejectsTheNextWithLoginSuggestion() {
        when(counters.increment(anyString(), any())).thenReturn(60L, 61L);
        var limiter = limiter("2026-11-04T12:30:00Z", 60, 600);
        assertThatCode(() -> limiter.check(null, "192.0.2.1")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.check(null, "192.0.2.1")).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(ErrorCode.RATE_LIMITED);
            assertThat(error.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(error.getExtra()).containsEntry("retryAfterSeconds", 1800L).containsEntry("loginSuggested", true);
        });
    }

    @Test
    void FR002_AT02_authenticatedLimitUsesUserIdAndDoesNotSuggestLogin() {
        when(counters.increment(anyString(), any())).thenReturn(600L, 601L);
        var limiter = limiter("2026-11-04T12:30:00Z", 60, 600);
        limiter.check(7L, "192.0.2.1");
        assertThatThrownBy(() -> limiter.check(7L, "192.0.2.2")).isInstanceOfSatisfying(ApiException.class, error ->
                assertThat(error.getExtra()).containsEntry("retryAfterSeconds", 1800L).doesNotContainKey("loginSuggested"));
    }

    @Test
    void FR002_usesConfiguredLimitsAndRoundsRetryUpAtTheHourBoundary() {
        when(counters.increment(anyString(), any())).thenReturn(3L);
        assertThatThrownBy(() -> limiter("2026-11-04T12:59:59.999Z", 2, 4).check(null, "192.0.2.3"))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getExtra())
                        .containsEntry("retryAfterSeconds", 1L));
        limiter("2026-11-04T13:00:00Z", 2, 4).check(7L, "192.0.2.3");
        verify(counters).increment("library:dictionary:guest:192.0.2.3:2026110412", Duration.ofSeconds(1));
        verify(counters).increment("library:dictionary:user:7:2026110413", Duration.ofHours(1));
    }

    @Test
    void FR002_AT02_counterFailureAllowsLookupAndWarnsWithoutConnectionDetailsOrIp() {
        when(counters.increment(anyString(), any())).thenThrow(new IllegalStateException("fake-connection-details 192.0.2.4"));
        Logger logger = (Logger) LoggerFactory.getLogger(LibraryRateLimiter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatCode(() -> limiter("2026-11-04T12:00:00Z", 60, 600).check(null, "192.0.2.4"))
                    .doesNotThrowAnyException();
            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel().toString()).isEqualTo("WARN");
                assertThat(event.getFormattedMessage()).doesNotContain("fake-connection-details", "192.0.2.4");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void T12_reviewAllows500AndRejects501WithAnIndependentUserCounter() {
        when(counters.increment(anyString(), any())).thenReturn(500L, 501L);
        var limiter = limiter("2026-11-06T12:30:00Z", 60, 600);
        assertThatCode(() -> limiter.checkFlashcardReview(7L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.checkFlashcardReview(7L)).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(ErrorCode.RATE_LIMITED);
            assertThat(error.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(error.getExtra()).containsOnlyKeys("retryAfterSeconds").containsEntry("retryAfterSeconds", 1800L);
        });
        verify(counters, org.mockito.Mockito.times(2)).increment("library:flashcards:review:user:7:2026110612", Duration.ofMinutes(30));
    }

    @Test
    void T12_reviewUsesConfiguredLimitAndNewUtcHourKey() {
        when(counters.increment(anyString(), any())).thenReturn(3L);
        LibraryConfig config = new LibraryConfig();
        config.setFlashcardReviewsPerHour(2);
        var limiter = new LibraryRateLimiter(counters, Clock.fixed(Instant.parse("2026-11-06T12:59:59.999Z"), ZoneOffset.UTC), config);
        assertThatThrownBy(() -> limiter.checkFlashcardReview(7L)).isInstanceOfSatisfying(ApiException.class, error ->
                assertThat(error.getExtra()).containsEntry("retryAfterSeconds", 1L));
        verify(counters).increment("library:flashcards:review:user:7:2026110612", Duration.ofSeconds(1));
        limiter("2026-11-06T13:00:00Z", 60, 600).checkFlashcardReview(7L);
        verify(counters).increment("library:flashcards:review:user:7:2026110613", Duration.ofHours(1));
    }

    @Test
    void T12_reviewCounterFailureAllowsRequestAndWarnsWithoutConnectionDetails() {
        when(counters.increment(anyString(), any())).thenThrow(new IllegalStateException("fake-review-connection-secret"));
        Logger logger = (Logger) LoggerFactory.getLogger(LibraryRateLimiter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatCode(() -> limiter("2026-11-06T12:00:00Z", 60, 600).checkFlashcardReview(7L)).doesNotThrowAnyException();
            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel().toString()).isEqualTo("WARN");
                assertThat(event.getFormattedMessage()).doesNotContain("fake-review-connection-secret");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally { logger.detachAppender(appender); appender.stop(); }
    }

    private LibraryRateLimiter limiter(String now, int guest, int authenticated) {
        LibraryConfig config = new LibraryConfig();
        config.setGuestLookupsPerHour(guest);
        config.setAuthenticatedLookupsPerHour(authenticated);
        config.setFlashcardReviewsPerHour(500);
        return new LibraryRateLimiter(counters, Clock.fixed(Instant.parse(now), ZoneOffset.UTC), config);
    }
}
