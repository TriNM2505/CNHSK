package com.cnhsk.learning.leaderboard;

import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.cnhsk.learning.api.GameCode;
import com.cnhsk.learning.leaderboard.LeaderboardPeriods.Interval;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Read-only ranking over learning-owned attempts; callers supply the auth-owned banned IDs. */
@Repository
@Transactional(readOnly = true)
public class LeaderboardRepository {
    private static final String TIME_FILTER = "AND submitted_at >= :start AND submitted_at < :end";
    private static final String TOPIC = """
            WITH eligible AS (
                SELECT id, user_id, score, duration_ms, submitted_at
                FROM learning.attempts
                WHERE kind = 'QUIZ' AND ref_type = 'QUIZ' AND ref_id = :topicId
                  AND status = 'SUBMITTED' AND suspicious = false AND score IS NOT NULL
                  AND user_id <> ALL(:banned)
                  %s
            ),
            """;
    private static final String GAME = """
            WITH eligible AS (
                SELECT id, user_id, score, duration_ms, submitted_at
                FROM learning.attempts
                WHERE kind = 'GAME' AND game_code = :gameCode
                  AND status = 'SUBMITTED' AND suspicious = false AND score IS NOT NULL
                  AND user_id <> ALL(:banned)
                  %s
            ), best AS (
                SELECT DISTINCT ON (user_id) id, user_id, score, duration_ms, submitted_at
                FROM eligible
                ORDER BY user_id, score DESC, duration_ms ASC NULLS LAST, submitted_at ASC, id ASC
            ),
            """;
    private static final String RANKING = """
            ranked AS (
                SELECT user_id, score, duration_ms, submitted_at,
                       ROW_NUMBER() OVER (ORDER BY score DESC, duration_ms ASC NULLS LAST,
                                                   submitted_at ASC, user_id ASC) AS rank,
                       COUNT(*) OVER () AS total
                FROM %s
            )
            SELECT rank, user_id, score, duration_ms, submitted_at, total
            FROM ranked
            WHERE rank <= :topSize OR user_id = CAST(:viewerId AS bigint)
            ORDER BY rank
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public LeaderboardRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc);
    }

    public Snapshot topicRanking(long topicId, Interval interval, Set<Long> bannedUserIds,
            Long viewerId, int topSize) {
        require(topicId > 0);
        var parameters = parameters(interval, bannedUserIds, viewerId, topSize).addValue("topicId", topicId);
        return read(topicSql(interval), parameters, viewerId, topSize);
    }

    public Snapshot gameRanking(GameCode game, Interval interval, Set<Long> bannedUserIds,
            Long viewerId, int topSize) {
        require(game != null);
        var parameters = parameters(interval, bannedUserIds, viewerId, topSize).addValue("gameCode", game.name());
        return read(gameSql(interval), parameters, viewerId, topSize);
    }

    private Snapshot read(String sql, MapSqlParameterSource parameters, Long viewerId, int topSize) {
        var rows = jdbc.query(sql, parameters, (rs, index) -> new RankedRow(
                new Row(rs.getLong("rank"), rs.getLong("user_id"), rs.getLong("score"),
                        (Integer) rs.getObject("duration_ms"), rs.getTimestamp("submitted_at").toInstant()),
                rs.getLong("total")));
        List<Row> entries = rows.stream().map(RankedRow::entry).filter(row -> row.rank() <= topSize).toList();
        Optional<Row> mine = rows.stream().map(RankedRow::entry)
                .filter(row -> viewerId != null && row.userId() == viewerId).findFirst();
        return new Snapshot(entries, mine, rows.isEmpty() ? 0 : rows.get(0).total());
    }

    // Package-private so EXPLAIN tests run the same SQL and typed bindings as production queries.
    static String topicSql(Interval interval) {
        return TOPIC.formatted(timeFilter(interval)) + RANKING.formatted("eligible");
    }

    static String gameSql(Interval interval) {
        return GAME.formatted(timeFilter(interval)) + RANKING.formatted("best");
    }

    static MapSqlParameterSource parameters(Interval interval, Set<Long> bannedUserIds, Long viewerId, int topSize) {
        require(interval != null && bannedUserIds != null && topSize >= 1 && topSize <= 20
                && (viewerId == null || viewerId > 0));
        require(bannedUserIds.stream().allMatch(id -> id != null && id > 0));
        var result = new MapSqlParameterSource()
                .addValue("banned", new SqlArrayValue("bigint", bannedUserIds.toArray()))
                .addValue("viewerId", viewerId, Types.BIGINT).addValue("topSize", topSize);
        if (interval.start() != null) {
            result.addValue("start", interval.start()).addValue("end", interval.end());
        }
        return result;
    }

    private static String timeFilter(Interval interval) {
        return interval.start() == null ? "" : TIME_FILTER;
    }

    private static void require(boolean valid) {
        if (!valid) throw new IllegalArgumentException("Tham số không hợp lệ.");
    }

    private record RankedRow(Row entry, long total) { }

    public record Row(long rank, long userId, long score, Integer durationMs, Instant submittedAt) { }

    public record Snapshot(List<Row> entries, Optional<Row> myEntry, long total) {
        public Snapshot {
            entries = List.copyOf(entries);
            java.util.Objects.requireNonNull(myEntry);
        }
    }
}
