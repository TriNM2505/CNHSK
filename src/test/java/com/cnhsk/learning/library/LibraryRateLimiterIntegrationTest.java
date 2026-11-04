package com.cnhsk.learning.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

class LibraryRateLimiterIntegrationTest extends AbstractIntegrationTest {

    @Autowired private RateLimiter counters;
    @Autowired private StringRedisTemplate redis;
    @Autowired private LibraryConfig config;

    @Test
    void FR002_AT02_guestAllows60AndBlocks61WhileKeepingTheFirstHitTtl() {
        String address = "2001:db8::" + UUID.randomUUID().toString().substring(0, 4);
        var limiter = limiter(counters, "2026-11-04T12:30:00Z");
        for (int index = 0; index < 60; index++) limiter.check(null, address);
        assertThatThrownBy(() -> limiter.check(null, address)).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(ErrorCode.RATE_LIMITED);
            assertThat(error.getExtra()).containsEntry("loginSuggested", true).containsEntry("retryAfterSeconds", 1800L);
        });
        String key = "library:dictionary:guest:" + address + ":2026110412";
        assertThat(redis.opsForValue().get(key)).isEqualTo("61");
        assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isBetween(1790L, 1800L);
        assertThatCode(() -> limiter(counters, "2026-11-04T13:00:00Z").check(null, address))
                .doesNotThrowAnyException();
    }

    @Test
    void FR002_AT02_authenticatedAllows600AndBlocks601AcrossDifferentAddresses() {
        long userId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        var limiter = limiter(counters, "2026-11-04T12:00:00Z");
        for (int index = 0; index < 600; index++) limiter.check(userId, "192.0.2.1");
        assertThatThrownBy(() -> limiter.check(userId, "192.0.2.2"))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getExtra())
                        .containsEntry("retryAfterSeconds", 3600L).doesNotContainKey("loginSuggested"));
        assertThatCode(() -> limiter.check(userId + 1, "192.0.2.1")).doesNotThrowAnyException();
    }

    @Test
    void FR002_concurrentLookupsShareOneAtomicCounter() throws Exception {
        String address = "2001:db8::" + UUID.randomUUID().toString().substring(0, 4);
        String key = "library:dictionary:guest:" + address + ":2026110412";
        redis.opsForValue().set(key, "59", Duration.ofMinutes(30));
        var limiter = limiter(counters, "2026-11-04T12:30:00Z");
        var pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int index = 0; index < 8; index++) results.add(pool.submit(() -> {
                start.await();
                try {
                    limiter.check(null, address);
                    return true;
                } catch (ApiException error) {
                    assertThat(error.getCode()).isEqualTo(ErrorCode.RATE_LIMITED);
                    return false;
                }
            }));
            start.countDown();
            int accepted = 0;
            for (Future<Boolean> result : results) if (result.get(20, TimeUnit.SECONDS)) accepted++;
            assertThat(accepted).isEqualTo(1);
            assertThat(redis.opsForValue().get(key)).isEqualTo("67");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void FR002_AT02_stoppedRedisStillAllowsLookups() {
        try (GenericContainer<?> isolated = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379)) {
            isolated.start();
            var factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(isolated.getHost(),
                    isolated.getMappedPort(6379)), LettuceClientConfiguration.builder()
                    .commandTimeout(Duration.ofMillis(300)).shutdownTimeout(Duration.ZERO).build());
            factory.afterPropertiesSet();
            try {
                var limiter = limiter(new RateLimiter(new StringRedisTemplate(factory)), "2026-11-04T12:30:00Z");
                limiter.check(null, "192.0.2.9");
                isolated.stop();
                assertThatCode(() -> limiter.check(null, "192.0.2.9")).doesNotThrowAnyException();
            } finally {
                factory.destroy();
            }
        }
    }

    private LibraryRateLimiter limiter(RateLimiter backend, String now) {
        return new LibraryRateLimiter(backend, Clock.fixed(Instant.parse(now), ZoneOffset.UTC), config);
    }
}
