package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.RateLimiter;
import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedeemRateLimitIntegrationTest extends AbstractIntegrationTest {
    @Autowired private StringRedisTemplate redis;
    @Autowired private RateLimiter limiter;
    @Autowired private RedeemRateLimitService service;
    private final List<String> owned = new ArrayList<>();

    @AfterEach void cleanup() { if (!owned.isEmpty()) redis.delete(owned); }

    @Test void failureWindowExpiresOneHourAfterFirstIncrementWithoutRefreshing() {
        long user = Math.abs(UUID.randomUUID().getMostSignificantBits());
        String ip = "rate-test-" + UUID.randomUUID();
        String userKey = key("user", Long.toString(user));
        String ipKey = key("ip", ip);
        service.recordFailure(user, ip);
        long firstUser = limiter.expiryTimeMillis(userKey);
        long firstIp = limiter.expiryTimeMillis(ipKey);
        assertThat(redis.getExpire(userKey, TimeUnit.MILLISECONDS)).isBetween(3599000L, 3601000L);
        assertThat(redis.getExpire(ipKey, TimeUnit.MILLISECONDS)).isBetween(3599000L, 3601000L);
        service.recordFailure(user, ip);
        assertThat(limiter.expiryTimeMillis(userKey)).isEqualTo(firstUser);
        assertThat(limiter.expiryTimeMillis(ipKey)).isEqualTo(firstIp);
        assertThat(redis.opsForValue().get(userKey)).isEqualTo("2");
        assertThat(redis.opsForValue().get(ipKey)).isEqualTo("2");
    }

    @Test void userFifthAndIpThirtiethFailuresBlockNextAttemptWithoutIncrementing() {
        long user = Math.abs(UUID.randomUUID().getMostSignificantBits());
        String ip = "rate-test-" + UUID.randomUUID();
        String userKey = key("user", Long.toString(user));
        String ipKey = key("ip", ip);
        for (int attempt = 0; attempt < 5; attempt++) {
            service.check(user, ip);
            service.recordFailure(user, ip);
        }
        assertRateLimited(() -> service.check(user, ip));
        assertThat(redis.opsForValue().get(userKey)).isEqualTo("5");
        assertThat(redis.opsForValue().get(ipKey)).isEqualTo("5");
        redis.delete(userKey);
        redis.opsForValue().set(ipKey, "29", Duration.ofHours(1));
        service.check(user, ip);
        service.recordFailure(user, ip);
        assertRateLimited(() -> service.check(user, ip));
        assertThat(redis.opsForValue().get(ipKey)).isEqualTo("30");
        assertThat(redis.opsForValue().get(userKey)).isEqualTo("1");
    }

    @Test void retryAfterUsesMaximumBlockingCounterRemainingTtl() {
        long user = Math.abs(UUID.randomUUID().getMostSignificantBits());
        String ip = "rate-test-" + UUID.randomUUID();
        String userKey = key("user", Long.toString(user));
        String ipKey = key("ip", ip);
        redis.opsForValue().set(userKey, "5", Duration.ofSeconds(60));
        redis.opsForValue().set(ipKey, "30", Duration.ofSeconds(120));
        long ttlBefore = redis.getExpire(ipKey, TimeUnit.MILLISECONDS);
        assertThatThrownBy(() -> service.check(user, ip)).isInstanceOf(ApiException.class).satisfies(f -> {
            long retry = ((Number) ((ApiException) f).getExtra().get("retryAfterSeconds")).longValue();
            long ttlAfter = redis.getExpire(ipKey, TimeUnit.MILLISECONDS);
            assertThat(retry).isBetween((ttlAfter + 999) / 1000, (ttlBefore + 999) / 1000);
            assertThat(retry).isBetween(119L, 120L);
        });
    }

    @Test void expiredCounterIsAllowedWithoutDeletingAnotherTestKeys() {
        long user = Math.abs(UUID.randomUUID().getMostSignificantBits());
        String ip = "rate-test-" + UUID.randomUUID();
        String userKey = key("user", Long.toString(user));
        key("ip", ip);
        redis.opsForValue().set(userKey, "5", Duration.ofHours(1));
        redis.expireAt(userKey, java.time.Instant.now().minusSeconds(1));
        service.check(user, ip);
        assertThat(redis.hasKey(userKey)).isFalse();
    }

    private String key(String kind, String value) {
        String key = "billing:redeem:fail:" + kind + ":" + value;
        owned.add(key);
        return key;
    }

    private static void assertRateLimited(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class).satisfies(f -> {
            assertThat(((ApiException) f).getCode()).isEqualTo(ErrorCode.RATE_LIMITED);
            assertThat(((Number) ((ApiException) f).getExtra().get("retryAfterSeconds")).longValue()).isPositive();
        });
    }
}
