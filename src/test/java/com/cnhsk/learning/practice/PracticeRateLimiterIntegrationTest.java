package com.cnhsk.learning.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.RateLimiter;
import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.http.HttpStatus;
import org.testcontainers.containers.GenericContainer;

class PracticeRateLimiterIntegrationTest extends AbstractIntegrationTest {

    @Autowired private RateLimiter counters;
    @Autowired private StringRedisTemplate redis;

    @Test
    void FR001_startLimitAllows120AndRejects121WithTimeUntilTheNextHour() {
        long userId = Math.abs(java.util.UUID.randomUUID().getMostSignificantBits());
        PracticeRateLimiter limiter = limiter(counters, "2026-10-30T12:30:00Z");
        for (int i = 0; i < 120; i++) {
            limiter.checkStart(userId);
        }
        assertThatThrownBy(() -> limiter.checkStart(userId)).isInstanceOfSatisfying(ApiException.class, failure -> {
            assertThat(failure.getCode()).isEqualTo(ErrorCode.RATE_LIMITED);
            assertThat(failure.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(failure.getExtra()).containsEntry("retryAfterSeconds", 1800L);
        });
        String key = "practice:start:" + userId + ":2026103012";
        assertThat(redis.opsForValue().get(key)).isEqualTo("121");
        assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isBetween(1790L, 1800L);
        assertThatCode(() -> limiter.checkStart(userId + 1)).doesNotThrowAnyException();
        assertThatCode(() -> limiter(counters, "2026-10-30T13:00:00Z").checkStart(userId)).doesNotThrowAnyException();
    }

    @Test
    void FR001_concurrentStartsCannotExceedTheSharedRedisCounter() throws Exception {
        long userId = Math.abs(java.util.UUID.randomUUID().getMostSignificantBits());
        var limiter = limiter(counters, "2026-10-30T12:45:00Z");
        String key = "practice:start:" + userId + ":2026103012";
        redis.opsForValue().set(key, "119", Duration.ofMinutes(15));
        var pool = Executors.newFixedThreadPool(8);
        var ready = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> {
                    ready.await();
                    try {
                        limiter.checkStart(userId);
                        return true;
                    } catch (ApiException exception) {
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.RATE_LIMITED);
                        return false;
                    }
                }));
            }
            ready.countDown();
            int accepted = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(20, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }
            assertThat(accepted).isEqualTo(1);
            assertThat(redis.opsForValue().get(key)).isEqualTo("127");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void FR001_hourBoundaryRoundsRetryUpAndNeverReturnsZero() {
        RateLimiter mockCounters = mock(RateLimiter.class);
        when(mockCounters.increment(anyString(), any())).thenReturn(121L);
        assertThatThrownBy(() -> limiter(mockCounters, "2026-10-30T12:59:59.999Z").checkStart(1))
                .isInstanceOfSatisfying(ApiException.class,
                        failure -> assertThat(failure.getExtra()).containsEntry("retryAfterSeconds", 1L));
    }

    @Test
    void FR001_anUnexpectedRedisCounterFailureAllowsStarts() {
        RateLimiter mockCounters = mock(RateLimiter.class);
        when(mockCounters.increment(anyString(), any())).thenThrow(new IllegalStateException("simulated failure"));
        assertThatCode(() -> limiter(mockCounters, "2026-10-30T12:30:00Z").checkStart(1)).doesNotThrowAnyException();
    }

    @Test
    void FR001_stoppedRedisStillAllowsStarts() {
        try (GenericContainer<?> isolatedRedis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379)) {
            isolatedRedis.start();
            var factory = new LettuceConnectionFactory(
                    new RedisStandaloneConfiguration(isolatedRedis.getHost(), isolatedRedis.getMappedPort(6379)),
                    LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(300))
                            .shutdownTimeout(Duration.ZERO).build());
            factory.afterPropertiesSet();
            try {
                StringRedisTemplate isolated = new StringRedisTemplate(factory);
                var limiter = limiter(new RateLimiter(isolated), "2026-10-30T12:30:00Z");
                limiter.checkStart(1);
                assertThat(isolated.opsForValue().get("practice:start:1:2026103012")).isEqualTo("1");
                isolatedRedis.stop();
                assertThatCode(() -> limiter.checkStart(1)).doesNotThrowAnyException();
            } finally {
                factory.destroy();
            }
        }
    }

    private static PracticeRateLimiter limiter(RateLimiter counters, String now) {
        return new PracticeRateLimiter(counters, Clock.fixed(Instant.parse(now), ZoneOffset.UTC));
    }
}
