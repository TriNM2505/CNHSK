package com.cnhsk.learning.library;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class LibraryRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LibraryRateLimiter.class);
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZoneOffset.UTC);
    private final RateLimiter counters;
    private final Clock clock;
    private final LibraryConfig config;

    public LibraryRateLimiter(RateLimiter counters, Clock clock, LibraryConfig config) {
        this.counters = counters;
        this.clock = clock;
        this.config = config;
    }

    public void check(Long userId, String remoteAddress) {
        var now = clock.instant();
        long retry = Math.max(1, (Duration.between(now, now.truncatedTo(ChronoUnit.HOURS).plus(1, ChronoUnit.HOURS))
                .toMillis() + 999) / 1000);
        boolean guest = userId == null;
        String identity = guest ? "guest:" + remoteAddress : "user:" + userId;
        int limit = guest ? config.getGuestLookupsPerHour() : config.getAuthenticatedLookupsPerHour();
        long count;
        try {
            count = counters.increment("library:dictionary:" + identity + ":" + HOUR.format(now),
                    Duration.ofSeconds(retry));
        } catch (RuntimeException exception) {
            // Redis errors may contain connection details; log neither those nor the caller's IP/query.
            log.warn("Dictionary rate limit unavailable; allowing request");
            return;
        }
        if (count > limit) {
            Map<String, Object> extra = guest
                    ? Map.of("retryAfterSeconds", retry, "loginSuggested", true)
                    : Map.of("retryAfterSeconds", retry);
            throw new ApiException(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS,
                    "Bạn đã tra cứu quá giới hạn. Vui lòng thử lại sau.", extra);
        }
    }

    public void checkFlashcardReview(long userId) {
        var now = clock.instant();
        long retry = Math.max(1, (Duration.between(now, now.truncatedTo(ChronoUnit.HOURS).plus(1, ChronoUnit.HOURS))
                .toMillis() + 999) / 1000);
        long count;
        try {
            count = counters.increment("library:flashcards:review:user:" + userId + ":" + HOUR.format(now), Duration.ofSeconds(retry));
        } catch (RuntimeException exception) {
            log.warn("Flashcard review rate limit unavailable; allowing request");
            return;
        }
        if (count > config.getFlashcardReviewsPerHour()) {
            throw new ApiException(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS,
                    "Bạn đã ôn thẻ quá giới hạn. Vui lòng thử lại sau.", Map.of("retryAfterSeconds", retry));
        }
    }
}
