package com.cnhsk.learning.quiz;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.cnhsk.learning.knowledge.entity.Question;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Learning-owned read projections; one statement includes empty-page totals and personal scores. */
@Repository
public class QuizRepository {
    // Shared by catalog and picker. Media with blank URLs is absent, as in QuestionItemFactory.
    private static final String SUPPORTED = """
            q.qtype IN ('MULTIPLE_CHOICE', 'TRUE_FALSE')
            AND coalesce(btrim(q.audio_url), '') = '' AND coalesce(btrim(q.image_url), '') = ''
            """;
    private static final String CATALOG = """
            WITH eligible AS (
                SELECT t.id, t.name, t.hsk_level, t.order_index, count(q.id) AS available
                FROM learning.topics t
                JOIN learning.question_knowledge_points qk
                  ON qk.knowledge_point_id = t.knowledge_point_id AND qk.role = 'PRIMARY'
                JOIN learning.questions q ON q.id = qk.question_id AND q.status = 'APPROVED'
                WHERE t.knowledge_point_id IS NOT NULL AND (%s)
                  AND (cast(:hsk AS integer) IS NULL OR t.hsk_level = :hsk)
                GROUP BY t.id
                HAVING count(q.id) >= :questionCount
            ), page_topics AS (
                SELECT * FROM eligible ORDER BY hsk_level, order_index, id LIMIT :size OFFSET :offset
            ), retry_scores AS (
                SELECT a.ref_id, max(a.score) AS best_retry_score
                FROM learning.attempts a JOIN page_topics p ON p.id = a.ref_id
                WHERE a.user_id = :userId AND a.kind = 'PRACTICE'
                  AND a.ref_type = 'QUIZ_RETRY' AND a.status = 'SUBMITTED'
                GROUP BY a.ref_id
            )
            SELECT totals.total, p.id AS topic_id, p.name AS topic_name, p.hsk_level, p.available,
                   first_attempt.id AS first_id, first_attempt.score AS first_score,
                   first_attempt.submitted_at AS first_submitted_at, first_attempt.suspicious AS first_suspicious,
                   retry_scores.best_retry_score
            FROM (SELECT count(*) AS total FROM eligible) totals
            LEFT JOIN page_topics p ON true
            LEFT JOIN learning.attempts first_attempt
              ON first_attempt.ref_id = p.id AND first_attempt.user_id = :userId
             AND first_attempt.kind = 'QUIZ' AND first_attempt.ref_type = 'QUIZ'
             AND first_attempt.status = 'SUBMITTED'
            LEFT JOIN retry_scores ON retry_scores.ref_id = p.id
            ORDER BY p.hsk_level, p.order_index, p.id
            """.formatted(SUPPORTED);

    private final EntityManager entityManager;
    private final NamedParameterJdbcTemplate jdbc;

    public QuizRepository(EntityManager entityManager, NamedParameterJdbcTemplate jdbc) {
        this.entityManager = entityManager;
        this.jdbc = jdbc;
    }

    public void lockStart(long userId) { lock("quiz:start:" + userId); }

    public void lockSubmission(long userId, long topicId) { lock("quiz:submit:" + userId + ":" + topicId); }

    private void lock(String key) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))", Map.of("key", key), row -> null);
    }

    public Optional<Long> active(long userId, long topicId, Instant now) {
        // Scalar JDBC projection avoids caching a stale entity while finish owns the same row.
        return jdbc.query("""
                SELECT id FROM learning.attempts WHERE user_id=:userId AND ref_id=:topicId
                  AND ref_type IN ('QUIZ','QUIZ_RETRY') AND status='IN_PROGRESS' AND expires_at >= :now
                ORDER BY served_at DESC, id DESC LIMIT 1 FOR NO KEY UPDATE
                """, Map.of("userId", userId, "topicId", topicId, "now", Timestamp.from(now)),
                (row, no) -> row.getLong(1)).stream().findFirst();
    }

    public boolean topicExists(long topicId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM learning.topics WHERE id=:id)",
                Map.of("id", topicId), Boolean.class));
    }

    public boolean hasFirstSubmission(long userId, long topicId, long excludedSession) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM learning.attempts WHERE user_id=:userId AND ref_id=:topicId
                  AND kind='QUIZ' AND status='SUBMITTED' AND id<>:sessionId)
                """, Map.of("userId", userId, "topicId", topicId, "sessionId", excludedSession), Boolean.class));
    }

    public List<Question> questions(long topicId) {
        List<?> rows = entityManager.createNativeQuery("""
                SELECT q.* FROM learning.topics t
                JOIN learning.question_knowledge_points qk
                  ON qk.knowledge_point_id=t.knowledge_point_id AND qk.role='PRIMARY'
                JOIN learning.questions q ON q.id=qk.question_id AND q.status='APPROVED'
                WHERE t.id=:topicId AND (%s) ORDER BY q.id
                """.formatted(SUPPORTED), Question.class).setParameter("topicId", topicId).getResultList();
        return rows.stream().map(Question.class::cast).toList();
    }

    public CatalogPage list(long userId, Integer hsk, int page, int size, int questionCount) {
        List<?> rows = entityManager.createNativeQuery(CATALOG, Tuple.class)
                .setParameter("userId", userId).setParameter("hsk", hsk)
                .setParameter("questionCount", questionCount).setParameter("size", size)
                .setParameter("offset", (long) page * size).getResultList();
        long total = 0;
        List<TopicRow> items = new ArrayList<>();
        for (Object value : rows) {
            Tuple row = (Tuple) value;
            total = number(row, "total").longValue();
            if (row.get("topic_id") == null) continue;
            items.add(new TopicRow(number(row, "topic_id").longValue(), row.get("topic_name", String.class),
                    number(row, "hsk_level").intValue(), number(row, "available").longValue(),
                    nullableLong(row, "first_id"), nullableLong(row, "first_score"),
                    instant(row.get("first_submitted_at")), Boolean.TRUE.equals(row.get("first_suspicious")),
                    nullableLong(row, "best_retry_score")));
        }
        return new CatalogPage(total, List.copyOf(items));
    }

    private static Number number(Tuple row, String name) { return (Number) row.get(name); }
    private static Long nullableLong(Tuple row, String name) {
        Number value = number(row, name);
        return value == null ? null : value.longValue();
    }

    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offset) return offset.toInstant();
        return ((Timestamp) value).toInstant();
    }

    public record CatalogPage(long total, List<TopicRow> items) { }
    public record TopicRow(long topicId, String topicName, int hskLevel, long availableQuestions,
            Long firstAttemptId, Long firstScore, Instant firstSubmittedAt, boolean firstSuspicious,
            Long bestRetryScore) { }
}
