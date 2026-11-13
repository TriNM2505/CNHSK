package com.cnhsk.auth.reminder;

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
public class ReminderUnsubscribeRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(ReminderUnsubscribeRateLimiter.class);
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
            .withZone(ZoneOffset.UTC);
    private final RateLimiter counters;
    private final Clock clock;

    public ReminderUnsubscribeRateLimiter(RateLimiter counters, Clock clock) {
        this.counters = counters;
        this.clock = clock;
    }

    public void check(String remoteAddress) {
        var now = clock.instant();
        long retry = Math.max(1, (Duration.between(now,
                now.truncatedTo(ChronoUnit.MINUTES).plus(1, ChronoUnit.MINUTES)).toMillis() + 999) / 1000);
        long count;
        try {
            count = counters.increment("reminder:unsubscribe:" + remoteAddress + ":" + MINUTE.format(now),
                    Duration.ofSeconds(retry));
        } catch (RuntimeException exception) {
            log.warn("Reminder unsubscribe rate limit unavailable; allowing request");
            return;
        }
        if (count > 20) {
            throw new ApiException(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS,
                    "Bạn đã gửi quá nhiều yêu cầu. Vui lòng thử lại sau.", Map.of("retryAfterSeconds", retry));
        }
    }
}
