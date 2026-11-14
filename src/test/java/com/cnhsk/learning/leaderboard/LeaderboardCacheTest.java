package com.cnhsk.learning.leaderboard;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import com.cnhsk.learning.leaderboard.LeaderboardCache.Payload;
import com.cnhsk.learning.leaderboard.LeaderboardRepository.Row;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LeaderboardCacheTest {
    private final LeaderboardRedisClient redis = mock(LeaderboardRedisClient.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final LeaderboardCache cache = new LeaderboardCache(redis, mapper);
    private final Row row = new Row(1, 7, 80, null, Instant.parse("2026-11-16T00:00:00Z"));
    private final String key = "lb:game:CHAR_RAIN:all:all";

    @Test void FR016_roundTripHasOnlyTopTotalBannedAndSixtySecondTtl() throws Exception {
        cache.write(key, List.of(row), 1, Set.of(99L));
        var json = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(redis).set(eq(key), json.capture(), eq(Duration.ofSeconds(60)));
        assertThat(mapper.readTree(json.getValue()).fieldNames()).toIterable()
                .containsExactlyInAnyOrder("version", "key", "top", "total", "banned");
        assertThat(json.getValue()).doesNotContain("myEntry", "displayName", "avatarUrl", "email", "token");
        when(redis.get(key)).thenReturn(json.getValue());
        assertThat(cache.read(key).payload()).contains(new Payload(1, key, List.of(row), 1, Set.of(99L)));
    }

    @Test void FR016_keysUseVietnamEpochDayAndChangeAtWeekAndMonthBoundary() {
        var sunday = new LeaderboardPeriods(Clock.fixed(Instant.parse("2026-11-15T16:59:59Z"), ZoneOffset.UTC));
        var monday = new LeaderboardPeriods(Clock.fixed(Instant.parse("2026-11-15T17:00:00Z"), ZoneOffset.UTC));
        var monthEnd = new LeaderboardPeriods(Clock.fixed(Instant.parse("2026-11-30T16:59:59Z"), ZoneOffset.UTC));
        var monthStart = new LeaderboardPeriods(Clock.fixed(Instant.parse("2026-11-30T17:00:00Z"), ZoneOffset.UTC));
        assertThat(LeaderboardCache.key("topic", "9", monday.resolve("all"))).isEqualTo("lb:topic:9:all:all");
        assertThat(LeaderboardCache.key("game", "CHAR_RAIN", monday.resolve("week")))
                .isEqualTo("lb:game:CHAR_RAIN:week:" + monday.resolve("week").start().toLocalDate().toEpochDay());
        assertThat(LeaderboardCache.key("topic", "9", sunday.resolve("week")))
                .isNotEqualTo(LeaderboardCache.key("topic", "9", monday.resolve("week")));
        assertThat(LeaderboardCache.key("topic", "9", monthEnd.resolve("month")))
                .isNotEqualTo(LeaderboardCache.key("topic", "9", monthStart.resolve("month")));
    }

    @ParameterizedTest @ValueSource(strings = {"{", "null", "{}", "{\"version\":2}", "{} {}"})
    void FR017_corruptPayloadIsMiss(String json) {
        when(redis.get(key)).thenReturn(json);
        assertThat(cache.read(key).payload()).isEmpty();
    }

    @Test void FR017_semanticallyInvalidPayloadIsMiss() throws Exception {
        for (Payload value : List.of(new Payload(2, key, List.of(row), 1, Set.of()),
                new Payload(1, "wrong", List.of(row), 1, Set.of()),
                new Payload(1, key, List.of(row), -1, Set.of()),
                new Payload(1, key, List.of(row), 1, Set.of(7L)),
                new Payload(1, key, List.of(row), 1, Set.of(0L)),
                new Payload(1, key, List.of(row, row), 2, Set.of()),
                new Payload(1, key, List.of(new Row(2, 7, 80, 100, row.submittedAt())), 1, Set.of()))) {
            when(redis.get(key)).thenReturn(mapper.writeValueAsString(value));
            assertThat(cache.read(key).payload()).isEmpty();
        }
    }

    @Test void FR017_missReadFailureAndWriteFailureAreIsolated() {
        assertThat(cache.read(key).writable()).isTrue();
        when(redis.get(key)).thenThrow(new IllegalStateException("sensitive-connection"));
        var lookup = cache.read(key);
        assertThat(lookup.payload()).isEmpty();
        assertThat(lookup.writable()).isFalse();
        doThrow(new IllegalStateException("sensitive-connection")).when(redis).set(anyString(), anyString(), any());
        assertThatCode(() -> cache.write(key, List.of(row), 1, Set.of())).doesNotThrowAnyException();
    }
}
