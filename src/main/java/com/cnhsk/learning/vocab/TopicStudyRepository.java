package com.cnhsk.learning.vocab;

import java.util.List;
import java.util.Map;

import com.cnhsk.learning.content.entity.Lexeme;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Relational learning evidence; the transaction lock belongs to one learner/topic pair. */
@Repository
public class TopicStudyRepository {

    private static final String UNLEARNED = """
            SELECT x.* FROM learning.topic_items ti
            JOIN learning.lexemes x ON x.id = ti.lexeme_id
            WHERE ti.topic_id = :topicId AND NOT EXISTS (
                SELECT 1 FROM learning.attempt_items ai
                JOIN learning.attempts a ON a.id = ai.attempt_id
                WHERE a.user_id = :userId AND a.ref_type = 'TOPIC_STUDY'
                  AND ai.item_type = 'STUDY_CARD' AND ai.lexeme_id = x.id
            )
            ORDER BY ti.order_index, ti.id LIMIT 10
            """;

    private final EntityManager entityManager;
    private final NamedParameterJdbcTemplate jdbc;

    public TopicStudyRepository(EntityManager entityManager, NamedParameterJdbcTemplate jdbc) {
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

    public void lock(long userId, long topicId) {
        // Values form a bound key, never SQL. The lock releases at transaction completion.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))",
                Map.of("key", "TOPIC_STUDY:" + userId + ":" + topicId), result -> null);
    }

    public List<Lexeme> unlearned(long userId, long topicId) {
        List<?> rows = entityManager.createNativeQuery(UNLEARNED, Lexeme.class)
                .setParameter("userId", userId).setParameter("topicId", topicId).getResultList();
        return rows.stream().map(Lexeme.class::cast).toList();
    }
}
