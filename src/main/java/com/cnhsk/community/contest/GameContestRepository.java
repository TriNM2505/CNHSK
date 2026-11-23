package com.cnhsk.community.contest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

/**
 * Ranking of a game contest: each player's best round started inside the window, ties broken by the faster round
 * and then by who reached it first. Suspicious rounds, banned players and excluded players never rank.
 */
@Repository
public class GameContestRepository {
    private static final String RANKING = """
            WITH eligible AS (
                SELECT id, user_id, score, duration_ms, submitted_at
                FROM learning.attempts
                WHERE kind = 'GAME' AND ref_type = 'GAME_ROUND' AND ref_id = :contestId AND game_code = :gameCode
                  AND status = 'SUBMITTED' AND suspicious = false AND score IS NOT NULL
                  AND served_at >= :startsAt AND served_at < :endsAt
                  AND user_id <> ALL(:banned)
                  AND NOT EXISTS (SELECT 1 FROM community.contest_exclusions x
                                  WHERE x.contest_id = :contestId AND x.user_id = learning.attempts.user_id)
            ), best AS (
                SELECT DISTINCT ON (user_id) user_id, score, duration_ms, submitted_at
                FROM eligible
                ORDER BY user_id, score DESC, duration_ms ASC NULLS LAST, submitted_at ASC, id ASC
            )
            SELECT ROW_NUMBER() OVER (ORDER BY score DESC, duration_ms ASC NULLS LAST, submitted_at ASC, user_id ASC)
                       AS rank,
                   user_id, score, duration_ms, submitted_at, COUNT(*) OVER () AS total
            FROM best
            ORDER BY rank
            LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public GameContestRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Ranking ranking(Contest contest, Set<Long> banned, int limit) {
        var parameters = new MapSqlParameterSource()
                .addValue("contestId", contest.getId())
                .addValue("gameCode", contest.getGameCode())
                .addValue("startsAt", utc(contest.getStartsAt()))
                .addValue("endsAt", utc(contest.getEndsAt()))
                .addValue("banned", new SqlArrayValue("bigint", banned.toArray()))
                .addValue("limit", limit);
        long[] total = {0};
        List<Row> rows = jdbc.query(RANKING, parameters, (row, number) -> {
            total[0] = row.getLong("total");
            return new Row(row.getLong("rank"), row.getLong("user_id"), row.getLong("score"),
                    row.getObject("duration_ms", Integer.class), row.getTimestamp("submitted_at").toInstant());
        });
        return new Ranking(List.copyOf(rows), total[0]);
    }

    public void exclude(long contestId, long userId, String note, long decidedBy, Instant now) {
        jdbc.update("""
                INSERT INTO community.contest_exclusions (contest_id, user_id, note, decided_by, decided_at)
                VALUES (:contestId, :userId, :note, :decidedBy, :now)
                ON CONFLICT (contest_id, user_id) DO UPDATE
                    SET note = EXCLUDED.note, decided_by = EXCLUDED.decided_by, decided_at = EXCLUDED.decided_at
                """, new MapSqlParameterSource().addValue("contestId", contestId).addValue("userId", userId)
                .addValue("note", note).addValue("decidedBy", decidedBy)
                .addValue("now", utc(now)));
    }

    public void include(long contestId, long userId) {
        jdbc.update("DELETE FROM community.contest_exclusions WHERE contest_id = :contestId AND user_id = :userId",
                Map.of("contestId", contestId, "userId", userId));
    }

    public List<Exclusion> exclusions(long contestId) {
        return jdbc.query("""
                SELECT user_id, note, decided_at FROM community.contest_exclusions
                WHERE contest_id = :contestId ORDER BY decided_at, user_id
                """, Map.of("contestId", contestId), (row, number) -> new Exclusion(row.getLong("user_id"),
                row.getString("note"), row.getTimestamp("decided_at").toInstant()));
    }

    private static java.time.OffsetDateTime utc(Instant instant) {
        return instant.atOffset(java.time.ZoneOffset.UTC);
    }

    public record Row(long rank, long userId, long points, Integer durationMs, Instant achievedAt) { }

    public record Ranking(List<Row> rows, long total) { }

    public record Exclusion(long userId, String note, Instant decidedAt) { }
}
