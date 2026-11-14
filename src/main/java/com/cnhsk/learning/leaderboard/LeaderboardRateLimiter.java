package com.cnhsk.learning.leaderboard;

import java.time.Clock;
import java.time.Duration;
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
public class LeaderboardRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(LeaderboardRateLimiter.class);
    private final RateLimiter counters;
    private final Clock clock;

    public LeaderboardRateLimiter(RateLimiter counters, Clock clock) {
        this.counters = counters;
        this.clock = clock;
    }

    public void check(Long userId, String remoteAddr) {
        if (userId != null) return;
        var now = clock.instant();
        var minute = now.truncatedTo(ChronoUnit.MINUTES);
        long retry = Math.max(1, (Duration.between(now, minute.plus(1, ChronoUnit.MINUTES)).toMillis() + 999) / 1000);
        long count;
        try {
            count = counters.increment("lb:rate:guest:" + remoteAddr + ":" + minute.getEpochSecond(), Duration.ofSeconds(retry));
        } catch (RuntimeException exception) {
            log.warn("Leaderboard rate limit unavailable; allowing request");
            return;
        }
        if (count > 120) throw new ApiException(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS,
                "Bạn đã xem quá giới hạn. Vui lòng thử lại sau.", Map.of("retryAfterSeconds", retry));
    }
}
