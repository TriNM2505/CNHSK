package com.cnhsk.learning.practice;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only aggregates over server-recorded game attempts. */
@Service
public class GameStatsQueryService {
    private final NamedParameterJdbcTemplate jdbc;

    public GameStatsQueryService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<GameStatsRow> gameStats(long userId) {
        return jdbc.query("SELECT game_code, COALESCE(MAX(score) FILTER (WHERE suspicious = false), 0) AS best_correct, "
                        + "COUNT(*) AS rounds_played, MAX(submitted_at) AS last_played_at "
                        + "FROM learning.attempts WHERE user_id = :userId AND kind = 'GAME' AND status = 'SUBMITTED' "
                        + "GROUP BY game_code", Map.of("userId", userId), (row, index) ->
                new GameStatsRow(row.getString("game_code"), row.getLong("best_correct"),
                        row.getLong("rounds_played"), instant(row, "last_played_at")));
    }

    @Transactional(readOnly = true)
    public RecentGameRoundsRow countRecentGameRounds(long userId, Instant since) {
        return jdbc.queryForObject("SELECT COUNT(*) AS rounds, MIN(served_at) AS oldest_served_at "
                        + "FROM learning.attempts WHERE user_id = :userId AND kind = 'GAME' AND served_at > :since",
                Map.of("userId", userId, "since", Timestamp.from(since)), (row, index) ->
                        new RecentGameRoundsRow(row.getLong("rounds"), instant(row, "oldest_served_at")));
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record GameStatsRow(String gameCode, long bestCorrect, long roundsPlayed, Instant lastPlayedAt) { }

    public record RecentGameRoundsRow(long count, Instant oldestServedAt) { }
}
