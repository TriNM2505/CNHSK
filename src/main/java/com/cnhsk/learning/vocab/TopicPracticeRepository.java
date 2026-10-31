package com.cnhsk.learning.vocab;

import java.util.List;
import java.util.Map;

import com.cnhsk.learning.content.entity.Lexeme;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Learning-owned evidence is counted using relational columns, never answer/meta JSONB. */
@Repository
public class TopicPracticeRepository {

    private static final String CANDIDATES = """
            WITH correct_counts AS (
                SELECT ai.lexeme_id, count(*) AS correct_count
                FROM learning.attempt_items ai JOIN learning.attempts a ON a.id = ai.attempt_id
                WHERE a.user_id = :userId AND a.ref_type = 'TOPIC_PRACTICE' AND ai.is_correct = true
                GROUP BY ai.lexeme_id
            )
            SELECT x.* FROM learning.topic_items ti JOIN learning.lexemes x ON x.id = ti.lexeme_id
            LEFT JOIN correct_counts c ON c.lexeme_id = x.id
            WHERE ti.topic_id = :topicId AND EXISTS (
                SELECT 1 FROM learning.attempt_items ai JOIN learning.attempts a ON a.id = ai.attempt_id
                WHERE a.user_id = :userId AND a.ref_type = 'TOPIC_STUDY'
                  AND ai.item_type = 'STUDY_CARD' AND ai.lexeme_id = x.id
            )
            ORDER BY coalesce(c.correct_count, 0), random()
            """;

    private final EntityManager entityManager;
    private final NamedParameterJdbcTemplate jdbc;

    public TopicPracticeRepository(EntityManager entityManager, NamedParameterJdbcTemplate jdbc) {
        this.entityManager = entityManager;
        this.jdbc = jdbc;
    }

    public boolean hasWords(long topicId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM learning.topics t
                    JOIN learning.topic_items ti ON ti.topic_id = t.id
                    JOIN learning.lexemes x ON x.id = ti.lexeme_id WHERE t.id = :topicId)
                """, Map.of("topicId", topicId), Boolean.class));
    }

    public List<Lexeme> candidates(long userId, long topicId) {
        // Do not LIMIT 10 here: later candidates must replace targets rejected by G-4.
        List<?> rows = entityManager.createNativeQuery(CANDIDATES, Lexeme.class)
                .setParameter("userId", userId).setParameter("topicId", topicId).getResultList();
        return rows.stream().map(Lexeme.class::cast).toList();
    }
}
