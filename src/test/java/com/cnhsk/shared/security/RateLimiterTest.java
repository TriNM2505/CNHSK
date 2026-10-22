package com.cnhsk.shared.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class RateLimiterTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final RateLimiter rateLimiter = new RateLimiter(redisTemplate);

    @Test
    void rejectsInvalidKeysAndWindowsBeforeCallingRedis() {
        assertThrows(IllegalArgumentException.class, () -> rateLimiter.increment(" ", Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class, () -> rateLimiter.increment("key", Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> rateLimiter.increment("key", Duration.ofMillis(500)));
        assertThrows(IllegalArgumentException.class, () -> rateLimiter.clear(null));

        verifyNoInteractions(redisTemplate);
    }
}
