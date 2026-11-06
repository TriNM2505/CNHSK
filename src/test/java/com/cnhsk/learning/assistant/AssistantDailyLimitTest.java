package com.cnhsk.learning.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class AssistantDailyLimitTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void FR004_reservesAtomicallyAndReleasesUnusedReservation() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L, 0L);
        AssistantDailyLimit limit = new AssistantDailyLimit(redis,
                Clock.fixed(Instant.parse("2026-11-06T04:00:00Z"), ZoneOffset.UTC), 200);

        var reservation = limit.reserve(42L);
        reservation.close();

        verify(redis, times(2)).execute(any(RedisScript.class), eq(List.of("assistant:ask:42:20261106")),
                any(Object[].class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void FR004_returnsSecondsUntilNextVietnameseMidnight() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);
        AssistantDailyLimit limit = new AssistantDailyLimit(redis,
                Clock.fixed(Instant.parse("2026-11-06T16:59:50.100Z"), ZoneOffset.UTC), 200);

        assertThatThrownBy(() -> limit.reserve(42L))
                .isInstanceOfSatisfying(AssistantDailyLimit.RateLimitExceededException.class,
                        exception -> assertThat(exception.retryAfterSeconds()).isEqualTo(10));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void FR004_AT04_redisFailureLetsRequestThroughWithoutRefundingAnUnreservedSlot() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("fixture error"));
        AssistantDailyLimit limit = new AssistantDailyLimit(redis,
                Clock.fixed(Instant.parse("2026-11-06T17:00:00Z"), ZoneOffset.UTC), 200);

        limit.reserve(42L).close();

        verify(redis).execute(any(RedisScript.class), eq(List.of("assistant:ask:42:20261107")),
                any(Object[].class));
    }
}
