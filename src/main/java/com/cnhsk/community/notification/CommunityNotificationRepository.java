package com.cnhsk.community.notification;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notifications are not stored: they are read from what already happened in the community — reviews of my posts,
 * comments on them, replies to my comments, likes (one line per post) and new followers. Nothing a user did
 * themselves is reported back to them.
 */
@Repository
@Transactional(readOnly = true)
public class CommunityNotificationRepository {
    private static final String ITEMS = """
            WITH items AS (
                SELECT 'post:' || p.id || ':' || p.status AS key,
                       CASE p.status WHEN 'APPROVED' THEN 'POST_APPROVED'
                                     WHEN 'REJECTED' THEN 'POST_REJECTED' ELSE 'POST_HIDDEN' END AS type,
                       p.reviewed_at AS at, CAST(NULL AS bigint) AS actor_id, 1 AS total,
                       p.id AS post_id, p.title AS post_title, CAST(NULL AS bigint) AS comment_id,
                       p.review_reason AS excerpt
                FROM community.posts p
                WHERE p.author_id = :me AND p.status IN ('APPROVED', 'REJECTED', 'HIDDEN')
                  AND p.reviewed_by IS NOT NULL AND p.reviewed_by <> :me AND p.reviewed_at >= :since
                UNION ALL
                SELECT 'comment:' || c.id,
                       CASE WHEN parent.author_id = :me THEN 'REPLY' ELSE 'COMMENT' END,
                       c.created_at, c.author_id, 1, p.id, p.title, c.id, left(c.content, 140)
                FROM community.comments c
                JOIN community.posts p ON p.id = c.post_id
                LEFT JOIN community.comments parent ON parent.id = c.parent_id
                WHERE c.status = 'VISIBLE' AND c.author_id <> :me AND c.created_at >= :since
                  AND (parent.author_id = :me OR p.author_id = :me)
                UNION ALL
                SELECT 'like:' || p.id, 'LIKE', MAX(r.created_at),
                       (array_agg(r.user_id ORDER BY r.created_at DESC))[1], CAST(COUNT(*) AS integer),
                       p.id, p.title, CAST(NULL AS bigint), CAST(NULL AS text)
                FROM community.reactions r
                JOIN community.posts p ON p.id = r.target_id
                WHERE r.target_type = 'POST' AND r.kind = 'LIKE' AND p.author_id = :me AND r.user_id <> :me
                  AND r.created_at >= :since
                GROUP BY p.id, p.title
                UNION ALL
                SELECT 'follow:' || r.user_id, 'FOLLOW', r.created_at, r.user_id, 1,
                       CAST(NULL AS bigint), CAST(NULL AS text), CAST(NULL AS bigint), CAST(NULL AS text)
                FROM community.reactions r
                WHERE r.target_type = 'USER' AND r.kind = 'FOLLOW' AND r.target_id = :me AND r.created_at >= :since
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public CommunityNotificationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Item> recent(long userId, Instant since, int limit) {
        return jdbc.query(ITEMS + "SELECT * FROM items ORDER BY at DESC, key LIMIT :limit",
                parameters(userId, since).addValue("limit", limit), (row, number) -> new Item(
                        row.getString("key"), row.getString("type"), row.getTimestamp("at").toInstant(),
                        row.getObject("actor_id", Long.class), row.getInt("total"),
                        row.getObject("post_id", Long.class), row.getString("post_title"),
                        row.getObject("comment_id", Long.class), row.getString("excerpt")));
    }

    /** Items newer than the last time the bell was opened; everything in the window when it never was. */
    public long countAfter(long userId, Instant since, Instant seenAt) {
        var parameters = parameters(userId, since).addValue("seen", utc(seenAt == null ? since : seenAt));
        Long count = jdbc.queryForObject(ITEMS + "SELECT COUNT(*) FROM items WHERE at > :seen", parameters, Long.class);
        return count == null ? 0 : count;
    }

    /** What moderators still have to handle, counted live so a queue someone else cleared reads zero. */
    public Map<String, Long> moderatorQueues() {
        return jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM community.posts WHERE status = 'PENDING') AS posts,
                       (SELECT COUNT(*) FROM community.moderation_cases WHERE status = 'PENDING') AS reports
                """, Map.of(), (row, number) -> Map.of(
                "PENDING_POSTS", row.getLong("posts"), "PENDING_REPORTS", row.getLong("reports")));
    }

    private static MapSqlParameterSource parameters(long userId, Instant since) {
        return new MapSqlParameterSource().addValue("me", userId).addValue("since", utc(since));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    public record Item(String key, String type, Instant at, Long actorId, int total, Long postId, String postTitle,
            Long commentId, String excerpt) { }
}
