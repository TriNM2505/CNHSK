package com.cnhsk.learning.practice;

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
public class PracticeRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(PracticeRateLimiter.class);
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZoneOffset.UTC);
    private final RateLimiter counters;
    private final Clock clock;

    public PracticeRateLimiter(RateLimiter counters, Clock clock) {
        this.counters = counters;
        this.clock = clock;
    }

    public void checkStart(long userId) {
        check(userId, "start", 120, "Bạn đã tạo quá nhiều lượt luyện. Vui lòng thử lại sau.");
    }

    public void checkAnswer(long userId) {
        check(userId, "ans", 600, "Bạn đã trả lời quá nhiều câu. Vui lòng thử lại sau.");
    }

    private void check(long userId, String operation, long limit, String message) {
        var now = clock.instant();
        long retry = Math.max(1, (Duration.between(now, now.truncatedTo(ChronoUnit.HOURS).plus(1, ChronoUnit.HOURS))
                .toMillis() + 999) / 1000);
        long count;
        try {
            count = counters.increment("practice:" + operation + ":" + userId + ":" + HOUR.format(now),
                    Duration.ofSeconds(retry));
        } catch (RuntimeException exception) {
            // Do not log Redis URLs, exception messages or caller-supplied content.
            log.warn("Practice {} rate limit unavailable; allowing request", operation);
            return;
        }
        if (count > limit) {
            throw new ApiException(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS,
                    message, Map.of("retryAfterSeconds", retry));
        }
    }
}
