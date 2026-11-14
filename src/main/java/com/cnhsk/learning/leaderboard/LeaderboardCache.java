package com.cnhsk.learning.leaderboard;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.cnhsk.learning.leaderboard.LeaderboardPeriods.Interval;
import com.cnhsk.learning.leaderboard.LeaderboardRepository.Row;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LeaderboardCache {
    private static final Logger log = LoggerFactory.getLogger(LeaderboardCache.class);
    static final Duration TTL = Duration.ofSeconds(60);
    private final LeaderboardRedisClient redis;
    private final ObjectMapper mapper;

    public LeaderboardCache(LeaderboardRedisClient redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public Lookup read(String key) {
        try {
            String json = redis.get(key);
            if (json == null) return new Lookup(Optional.empty(), true);
            Payload payload = mapper.readerFor(Payload.class)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(json);
            if (!valid(key, payload)) {
                log.warn("Leaderboard cache payload invalid");
                return new Lookup(Optional.empty(), true);
            }
            return new Lookup(Optional.of(payload), true);
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("Leaderboard cache read failed type={}", exception.getClass().getSimpleName());
            return new Lookup(Optional.empty(), false);
        }
    }

    public void write(String key, List<Row> top, long total, Set<Long> banned) {
        try {
            redis.set(key, mapper.writeValueAsString(new Payload(1, key, top, total, banned)), TTL);
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("Leaderboard cache write failed type={}", exception.getClass().getSimpleName());
        }
    }

    static String key(String type, String ref, Interval interval) {
        String start = interval.start() == null ? "all" : Long.toString(interval.start().toLocalDate().toEpochDay());
        return "lb:" + type + ":" + ref + ":" + interval.period() + ":" + start;
    }

    private static boolean valid(String key, Payload value) {
        if (value == null || value.version() != 1 || !key.equals(value.key()) || value.top() == null
                || value.banned() == null || value.total() < 0 || value.top().size() != Math.min(20, value.total())
                || value.banned().stream().anyMatch(id -> id == null || id <= 0)) return false;
        var seen = new java.util.HashSet<Long>();
        for (int i = 0; i < value.top().size(); i++) {
            Row row = value.top().get(i);
            if (row == null || row.rank() != i + 1L || row.userId() <= 0 || row.score() < 0
                    || row.submittedAt() == null || (row.durationMs() != null && row.durationMs() < 0)
                    || value.banned().contains(row.userId()) || !seen.add(row.userId())) return false;
        }
        return true;
    }

    public record Payload(int version, String key, List<Row> top, long total, Set<Long> banned) { }
    public record Lookup(Optional<Payload> payload, boolean writable) { }
}
