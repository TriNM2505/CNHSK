package com.cnhsk.learning.knowledge.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.cnhsk.learning.knowledge.entity.KnowledgeLabelRole;
import com.cnhsk.learning.knowledge.entity.Question;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import org.springframework.stereotype.Repository;

/** Read projections over learning-owned tables; JSONB is read, never filtered. */
@Repository
public class KnowledgeTreeQueryRepository {

    private final EntityManager entityManager;

    public KnowledgeTreeQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<QuestionLabelRow> questionLabels(Set<Long> questionIds) {
        return entityManager.createQuery("""
                SELECT q AS question, kp.code AS kid, label.role AS role
                FROM QuestionKnowledgePoint label
                JOIN Question q ON q.id = label.questionId
                JOIN KnowledgePoint kp ON kp.id = label.knowledgePointId
                WHERE label.questionId IN :questionIds
                ORDER BY q.id, kp.code
                """, Tuple.class)
                .setParameter("questionIds", questionIds)
                .getResultList().stream()
                .map(row -> new QuestionLabelRow(row.get("question", Question.class),
                        row.get("kid", String.class), row.get("role", KnowledgeLabelRole.class)))
                .toList();
    }

    public Optional<String> kidOfLexeme(long lexemeId) {
        List<?> rows = entityManager.createNativeQuery("""
                SELECT kp.code FROM learning.lexemes x
                JOIN learning.knowledge_points kp ON kp.id = x.knowledge_point_id
                WHERE x.id = :lexemeId
                """, String.class)
                .setParameter("lexemeId", lexemeId).getResultList();
        return rows.stream().map(String.class::cast).findFirst();
    }

    public Optional<String> kidOfTopic(long topicId) {
        List<?> rows = entityManager.createNativeQuery("""
                SELECT kp.code FROM learning.topics t
                JOIN learning.knowledge_points kp ON kp.id = t.knowledge_point_id
                WHERE t.id = :topicId
                """, String.class)
                .setParameter("topicId", topicId).getResultList();
        return rows.stream().map(String.class::cast).findFirst();
    }

    public List<Long> approvedQuestionIds(Set<String> kids, Integer hskLevel, int limit) {
        return entityManager.createQuery("""
                SELECT q.id FROM Question q
                JOIN QuestionKnowledgePoint label ON label.questionId = q.id
                JOIN KnowledgePoint kp ON kp.id = label.knowledgePointId
                WHERE q.status = com.cnhsk.learning.knowledge.entity.QuestionStatus.APPROVED
                  AND label.role = com.cnhsk.learning.knowledge.entity.KnowledgeLabelRole.PRIMARY
                  AND kp.code IN :kids
                  AND (:hskLevel IS NULL OR q.hskLevel = :hskLevel)
                ORDER BY q.id
                """, Long.class)
                .setParameter("kids", kids)
                .setParameter("hskLevel", hskLevel == null ? null : hskLevel.shortValue())
                .setMaxResults(limit).getResultList();
    }

    public List<TopicRow> topics(long nodeId) {
        List<?> rows = entityManager.createNativeQuery("""
                SELECT id, code, name FROM learning.topics
                WHERE knowledge_point_id = :nodeId ORDER BY id
                """, Tuple.class).setParameter("nodeId", nodeId).getResultList();
        return rows.stream().map(value -> {
            Tuple row = (Tuple) value;
            return new TopicRow(((Number) row.get("id")).longValue(),
                    row.get("code", String.class), row.get("name", String.class));
        }).toList();
    }

    public long lexemeCount(long nodeId) {
        return ((Number) entityManager.createNativeQuery("""
                SELECT count(*) FROM learning.lexemes WHERE knowledge_point_id = :nodeId
                """).setParameter("nodeId", nodeId).getSingleResult()).longValue();
    }

    public long approvedQuestionCount(long nodeId) {
        return entityManager.createQuery("""
                SELECT count(DISTINCT q.id) FROM Question q
                JOIN QuestionKnowledgePoint label ON label.questionId = q.id
                WHERE label.knowledgePointId = :nodeId
                  AND q.status = com.cnhsk.learning.knowledge.entity.QuestionStatus.APPROVED
                """, Long.class).setParameter("nodeId", nodeId).getSingleResult();
    }

    public record QuestionLabelRow(Question question, String kid, KnowledgeLabelRole role) { }

    public record TopicRow(long id, String code, String name) { }
}
