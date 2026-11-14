package com.cnhsk.learning.api;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.cnhsk.learning.practice.GameStatsQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Read-only game statistics boundary; caller identity is obtained by the calling module. */
@Component
public class GameStatsApi {
    private static final Logger log = LoggerFactory.getLogger(GameStatsApi.class);
    private final GameStatsQueryService queries;

    GameStatsApi(GameStatsQueryService queries) {
        this.queries = queries;
    }

    public Map<GameCode, GameStats> gameStats(long userId) {
        Map<GameCode, GameStats> result = new EnumMap<>(GameCode.class);
        for (var row : queries.gameStats(userId)) {
            GameCode code;
            try {
                code = GameCode.valueOf(row.gameCode());
            } catch (IllegalArgumentException exception) {
                log.warn("Unknown stored game code in game statistics; row ignored.");
                continue;
            }
            result.put(code, new GameStats(row.bestCorrect(), row.roundsPlayed(), row.lastPlayedAt()));
        }
        return Collections.unmodifiableMap(result);
    }

    public RecentGameRounds countRecentGameRounds(long userId, Instant since) {
        var result = queries.countRecentGameRounds(userId, since);
        return new RecentGameRounds(result.count(), result.oldestServedAt());
    }

    public record GameStats(long bestCorrect, long roundsPlayed, Instant lastPlayedAt) { }

    public record RecentGameRounds(long count, Instant oldestServedAt) { }
}
