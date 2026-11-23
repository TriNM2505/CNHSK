package com.cnhsk.learning.api;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Learning events for the notification bell, read from where they already live: graded or expired essays for the
 * learner, AI question batches reviewed by teachers and finished imports or AI runs for the content editor, and
 * grading payouts for the teacher. Nothing is stored for the bell itself.
 */
@Component
public class LearningNotificationApi {
    private static final String ITEMS = """
            WITH items AS (
                SELECT 'grading:' || r.id || ':' || r.status AS key,
                       CASE r.status WHEN 'COMPLETED' THEN 'GRADED' ELSE 'GRADING_EXPIRED' END AS type,
                       r.completed_at AS at, CAST(COALESCE(r.score, 0) AS bigint) AS amount, r.id AS ref_id,
                       CAST(NULL AS bigint) AS second, CAST(NULL AS text) AS detail
                FROM learning.service_requests r
                WHERE r.kind = 'GRADING' AND r.user_id = :me AND r.status IN ('COMPLETED', 'EXPIRED')
                  AND r.completed_at >= :since
                UNION ALL
                SELECT 'ai-review:' || r.id, 'AI_REVIEWED', MAX(q.reviewed_at),
                       COUNT(*) FILTER (WHERE q.status = 'APPROVED'), r.id,
                       COUNT(*) FILTER (WHERE q.status = 'REJECTED'), CAST(NULL AS text)
                FROM learning.questions q
                JOIN learning.service_requests r ON r.id = q.created_by_request_id
                WHERE r.user_id = :me AND q.source = 'AI' AND q.reviewed_by IS NOT NULL AND q.reviewed_by <> :me
                  AND q.reviewed_at >= :since AND q.status IN ('APPROVED', 'REJECTED')
                GROUP BY r.id
                UNION ALL
                SELECT 'job:' || r.id || ':' || r.status,
                       r.kind || CASE r.status WHEN 'COMPLETED' THEN '_DONE' ELSE '_FAILED' END,
                       r.completed_at, CAST(0 AS bigint), r.id, CAST(NULL AS bigint), left(r.error_message, 140)
                FROM learning.service_requests r
                WHERE r.kind IN ('IMPORT', 'AI_GENERATE') AND r.user_id = :me AND r.status IN ('COMPLETED', 'FAILED')
                  AND r.completed_at >= :since
                UNION ALL
                SELECT 'payout:' || t.id, 'PAYOUT', t.created_at, CAST(t.amount AS bigint), t.request_id,
                       CAST(NULL AS bigint), CAST(NULL AS text)
                FROM learning.credit_transactions t
                WHERE t.user_id = :me AND t.tx_type = 'TEACHER_PAYOUT' AND t.created_at >= :since
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;

    LearningNotificationApi(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<Item> recent(long userId, Instant since, int limit) {
        return jdbc.query(ITEMS + "SELECT * FROM items WHERE at IS NOT NULL ORDER BY at DESC, key LIMIT :limit",
                parameters(userId, since).addValue("limit", limit), (row, number) -> new Item(row.getString("key"),
                        row.getString("type"), row.getTimestamp("at").toInstant(), row.getLong("amount"),
                        row.getObject("ref_id", Long.class), row.getObject("second", Long.class),
                        row.getString("detail")));
    }

    @Transactional(readOnly = true)
    public long countAfter(long userId, Instant since, Instant seenAt) {
        var parameters = parameters(userId, since)
                .addValue("seen", (seenAt == null ? since : seenAt).atOffset(ZoneOffset.UTC));
        Long count = jdbc.queryForObject(ITEMS + "SELECT COUNT(*) FROM items WHERE at > :seen", parameters, Long.class);
        return count == null ? 0 : count;
    }

    private static MapSqlParameterSource parameters(long userId, Instant since) {
        return new MapSqlParameterSource().addValue("me", userId).addValue("since", since.atOffset(ZoneOffset.UTC));
    }

    /**
     * {@code type}: GRADED (amount = score), GRADING_EXPIRED, AI_REVIEWED (amount approved, second rejected),
     * IMPORT_DONE, IMPORT_FAILED, AI_GENERATE_DONE, AI_GENERATE_FAILED (detail = error) or PAYOUT (amount = points).
     * {@code refId} is the service request.
     */
    public record Item(String key, String type, Instant at, long amount, Long refId, Long second, String detail) { }
}
