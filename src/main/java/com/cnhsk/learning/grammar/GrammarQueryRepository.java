package com.cnhsk.learning.grammar;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import org.springframework.stereotype.Repository;

/** Read projections over learning-owned evidence; JSONB is projected only, never filtered. */
@Repository
public class GrammarQueryRepository {

    private static final String SUMMARIES = """
            WITH progress AS (
                SELECT ref_id,
                       count(*) FILTER (WHERE status = 'SUBMITTED') > 0 AS learned,
                       max(floor(score * 100 / nullif(max_score, 0)))
                           FILTER (WHERE status = 'SUBMITTED' AND max_score > 0) AS best_percent,
                       max(submitted_at) FILTER (WHERE status = 'SUBMITTED') AS last_studied_at
                FROM learning.attempts
                WHERE user_id = :userId AND ref_type = 'GRAMMAR_LESSON'
                GROUP BY ref_id
            )
            SELECT x.id, x.text, x.meaning_vi AS title_vi, x.hsk_level,
                   x.extra ->> 'structure' AS structure,
                   coalesce(jsonb_array_length(x.extra -> 'exercises'), 0) AS exercise_count,
                   coalesce(p.learned, false) AS learned, p.best_percent, p.last_studied_at
            FROM learning.lexemes x
            LEFT JOIN progress p ON p.ref_id = x.id
            WHERE x.kind = 'GRAMMAR'
              AND (cast(:allLevels AS boolean) OR x.hsk_level IN (:levels))
              AND (cast(:grammarId AS bigint) IS NULL OR x.id = :grammarId)
            ORDER BY x.hsk_level, x.id
            """;

    private final EntityManager entityManager;

    public GrammarQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<GrammarRow> summaries(long userId, List<Integer> levels, Long grammarId) {
        List<?> rows = entityManager.createNativeQuery(SUMMARIES, Tuple.class)
                .setParameter("userId", userId)
                .setParameter("allLevels", levels.isEmpty())
                .setParameter("levels", levels.isEmpty() ? List.of(-1) : levels)
                .setParameter("grammarId", grammarId)
                .getResultList();
        return rows.stream().map(value -> {
            Tuple row = (Tuple) value;
            Number best = (Number) row.get("best_percent");
            return new GrammarRow(number(row, "id").longValue(), row.get("text", String.class),
                    row.get("title_vi", String.class), number(row, "hsk_level").intValue(),
                    row.get("structure", String.class), number(row, "exercise_count").intValue(),
                    row.get("learned", Boolean.class),
                    best == null ? null : best.intValue(), instant(row.get("last_studied_at")));
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

    public record GrammarRow(long id, String text, String titleVi, int hskLevel, String structure, int exerciseCount,
                             boolean learned, Integer bestPercent, Instant lastStudiedAt) {
    }
}
