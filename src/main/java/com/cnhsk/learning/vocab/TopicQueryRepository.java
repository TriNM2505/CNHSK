package com.cnhsk.learning.vocab;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import org.springframework.stereotype.Repository;

/** Read projections over learning-owned relational evidence; no JSONB predicates. */
@Repository
public class TopicQueryRepository {

    private static final String SUMMARIES = """
            WITH user_attempts AS (
                SELECT id, ref_type, ref_id, status, score, max_score, served_at, submitted_at, expires_at
                FROM learning.attempts WHERE user_id = :userId
            ), learned AS (
                SELECT DISTINCT ai.lexeme_id
                FROM learning.attempt_items ai JOIN user_attempts a ON a.id = ai.attempt_id
                WHERE a.ref_type = 'TOPIC_STUDY' AND ai.item_type = 'STUDY_CARD'
                  AND ai.lexeme_id IS NOT NULL
            ), activity AS (
                SELECT ref_id, count(*) > 0 AS has_attempt,
                       bool_or(ref_type = 'TOPIC_TEST' AND status = 'SUBMITTED'
                               AND score * 100 >= 90 * max_score) AS completed,
                       max(CASE WHEN ref_type = 'TOPIC_TEST' AND status = 'SUBMITTED'
                                THEN score * 100 / nullif(max_score, 0) END) AS best_percent,
                       max(CASE WHEN ref_type = 'TOPIC_TEST' AND status = 'IN_PROGRESS'
                                THEN expires_at END) AS active_test_expires_at
                FROM user_attempts
                WHERE ref_type IN ('TOPIC_STUDY', 'TOPIC_PRACTICE', 'TOPIC_TEST')
                GROUP BY ref_id
            ), latest_test AS (
                SELECT DISTINCT ON (ref_id) ref_id,
                       CASE WHEN status = 'SUBMITTED' AND score * 100 < 90 * max_score
                            THEN submitted_at END AS failed_at
                FROM user_attempts WHERE ref_type = 'TOPIC_TEST'
                ORDER BY ref_id, served_at DESC, id DESC
            )
            SELECT t.id, t.code, t.name, t.hsk_level, count(*) AS total_words,
                   count(l.lexeme_id) AS learned_words,
                   coalesce(a.has_attempt, false) AS has_attempt,
                   coalesce(a.completed, false) AS completed, a.best_percent,
                   a.active_test_expires_at, lt.failed_at,
                   count(*) FILTER (WHERE l.lexeme_id IS NOT NULL AND x.audio_url IS NOT NULL) AS learned_audio
            FROM learning.topics t JOIN learning.topic_items ti ON ti.topic_id = t.id
            JOIN learning.lexemes x ON x.id = ti.lexeme_id
            LEFT JOIN learned l ON l.lexeme_id = ti.lexeme_id
            LEFT JOIN activity a ON a.ref_id = t.id
            LEFT JOIN latest_test lt ON lt.ref_id = t.id
            WHERE (cast(:hskLevel AS integer) IS NULL OR t.hsk_level = :hskLevel)
              AND (cast(:topicId AS bigint) IS NULL OR t.id = :topicId)
            GROUP BY t.id, a.has_attempt, a.completed, a.best_percent, a.active_test_expires_at, lt.failed_at
            ORDER BY t.hsk_level, t.order_index, t.id
            """;

    private static final String WORDS = """
            WITH learned AS (
                SELECT DISTINCT ai.lexeme_id
                FROM learning.attempt_items ai JOIN learning.attempts a ON a.id = ai.attempt_id
                WHERE a.user_id = :userId AND a.ref_type = 'TOPIC_STUDY' AND ai.item_type = 'STUDY_CARD'
                  AND ai.lexeme_id IS NOT NULL
            )
            SELECT x.id, x.text, x.pinyin, x.sino_viet, x.meaning_vi, x.hsk_level, x.audio_url,
                   l.lexeme_id IS NOT NULL AS learned
            FROM learning.topic_items ti JOIN learning.lexemes x ON x.id = ti.lexeme_id
            LEFT JOIN learned l ON l.lexeme_id = x.id
            WHERE ti.topic_id = :topicId ORDER BY ti.order_index, ti.id
            """;

    private final EntityManager entityManager;

    public TopicQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<TopicRow> summaries(long userId, Integer hskLevel, Long topicId) {
        List<?> rows = entityManager.createNativeQuery(SUMMARIES, Tuple.class)
                .setParameter("userId", userId).setParameter("hskLevel", hskLevel)
                .setParameter("topicId", topicId).getResultList();
        return rows.stream().map(value -> {
            Tuple row = (Tuple) value;
            Number best = (Number) row.get("best_percent");
            return new TopicRow(number(row, "id").longValue(), row.get("code", String.class),
                    row.get("name", String.class), number(row, "hsk_level").intValue(),
                    number(row, "total_words").longValue(), number(row, "learned_words").longValue(),
                    row.get("has_attempt", Boolean.class), row.get("completed", Boolean.class),
                    best == null ? null : best.intValue(), instant(row.get("active_test_expires_at")),
                    instant(row.get("failed_at")), number(row, "learned_audio").longValue());
        }).toList();
    }

    public List<TopicQueryService.TopicWord> words(long userId, long topicId) {
        List<?> rows = entityManager.createNativeQuery(WORDS, Tuple.class)
                .setParameter("userId", userId).setParameter("topicId", topicId).getResultList();
        return rows.stream().map(value -> {
            Tuple row = (Tuple) value;
            Number level = (Number) row.get("hsk_level");
            return new TopicQueryService.TopicWord(number(row, "id").longValue(), row.get("text", String.class),
                    row.get("pinyin", String.class), row.get("sino_viet", String.class),
                    row.get("meaning_vi", String.class), level == null ? null : level.intValue(),
                    row.get("audio_url", String.class), row.get("learned", Boolean.class));
        }).toList();
    }

    private static Number number(Tuple row, String field) {
        return (Number) row.get(field);
    }

    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime dateTime) return dateTime.toInstant();
        return ((Timestamp) value).toInstant();
    }

    public record TopicRow(long id, String code, String name, int hskLevel, long totalWords,
                           long learnedWords, boolean hasAttempt, boolean completed, Integer bestPercent,
                           Instant activeTestExpiresAt, Instant failedAt, long learnedAudio) {
    }
}
