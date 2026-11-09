package com.cnhsk.learning.credit;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RedeemRateLimitService {
    private static final Logger LOG = LoggerFactory.getLogger(RedeemRateLimitService.class);
    private static final Duration WINDOW = Duration.ofHours(1);
    private final StringRedisTemplate redis;
    private final RateLimiter limiter;

    public RedeemRateLimitService(StringRedisTemplate redis, RateLimiter limiter, Clock clock) {
        this.redis = redis;
        this.limiter = limiter;
    }

    public void check(long userId, String ip) {
        long retry;
        try {
            retry = Math.max(retryAfter(userKey(userId), 5), retryAfter(ipKey(ip), 30));
        } catch (RuntimeException exception) {
            warn(exception);
            return;
        }
        if (retry > 0) {
            throw new ApiException(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS,
                    "Bạn đã nhập sai quá nhiều lần. Vui lòng thử lại sau.", Map.of("retryAfterSeconds", retry));
        }
    }

    public void recordFailure(long userId, String ip) {
        increment(userKey(userId));
        increment(ipKey(ip));
    }

    private long retryAfter(String key, int threshold) {
        for (int attempt = 0; attempt < 2; attempt++) {
            String value = redis.opsForValue().get(key);
            if (value == null) return 0;
            long count = Long.parseLong(value);
            if (count < 0) throw new IllegalStateException("Invalid rate-limit counter.");
            if (count < threshold) return 0;
            Long remaining = redis.getExpire(key, TimeUnit.MILLISECONDS);
            if (remaining == null) throw new IllegalStateException("Rate-limit expiry is unavailable.");
            if (remaining == -2 || remaining == 0) continue;
            if (remaining < 0) throw new IllegalStateException("Rate-limit expiry is unavailable.");
            if (remaining > 0) {
                // The TTL can belong to a new window if the old counter expired after GET.
                String confirmed = redis.opsForValue().get(key);
                if (confirmed == null || Long.parseLong(confirmed) < threshold) return 0;
                return Math.min(WINDOW.toSeconds(), 1 + (remaining - 1) / 1000);
            }
        }
        return 0;
    }

    private void increment(String key) {
        try {
            limiter.increment(key, WINDOW);
        } catch (RuntimeException exception) {
            warn(exception);
        }
    }

    private static String userKey(long userId) { return "billing:redeem:fail:user:" + userId; }
    private static String ipKey(String ip) { return "billing:redeem:fail:ip:" + ip; }
    private static void warn(RuntimeException exception) {
        LOG.warn("Redeem rate limit unavailable; cause_type={}", exception.getClass().getSimpleName());
    }
}
