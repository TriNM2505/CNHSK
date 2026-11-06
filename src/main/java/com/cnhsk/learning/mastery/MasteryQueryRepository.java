package com.cnhsk.learning.mastery;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.cnhsk.learning.knowledge.entity.QuestionOption;
import com.cnhsk.learning.practice.PracticeService.ServedSnapshot;
import com.cnhsk.learning.practice.ServedItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** Batched read projection for stored evidence and its current labels. */
@Repository
public class MasteryQueryRepository {

    private static final TypeReference<List<QuestionOption>> OPTIONS = new TypeReference<>() { };

    private final EntityManager entityManager;
    private final ObjectMapper mapper;

    public MasteryQueryRepository(EntityManager entityManager, ObjectMapper mapper) {
        this.entityManager = entityManager;
        this.mapper = mapper;
    }

    /** Returns KIDs with at least one approved primary question in one batched query. */
    public Set<String> kidsWithApprovedQuestions(Set<String> kids) {
        if (kids.isEmpty()) {
            return Set.of();
        }
        List<String> result = entityManager.createQuery("""
                SELECT DISTINCT kp.code FROM Question q
                JOIN QuestionKnowledgePoint label ON label.questionId = q.id
                JOIN KnowledgePoint kp ON kp.id = label.knowledgePointId
                WHERE q.status = com.cnhsk.learning.knowledge.entity.QuestionStatus.APPROVED
                  AND label.role = com.cnhsk.learning.knowledge.entity.KnowledgeLabelRole.PRIMARY
                  AND kp.code IN :kids
                ORDER BY kp.code
                """, String.class)
                .setParameter("kids", kids)
                .getResultList();
        return Set.copyOf(result);
    }

    /** Reads every requested evidence item, including its attempt snapshot and current labels, in one statement. */
    public List<EvidenceRow> evidence(Set<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return List.of();
        }
        List<?> rows = entityManager.createNativeQuery("""
                SELECT ai.id, ai.attempt_id, a.user_id, ai.question_id, ai.lexeme_id, ai.answer,
                       ai.is_correct, ai.order_index, ai.answered_at, ai.item_type,
                       a.ref_type, a.ref_id, a.suspicious, a.served_items::text,
                       q.options::text, qkp_point.code, qkp.role,
                       lexeme_point.code, topic_point.code
                FROM learning.attempt_items ai
                JOIN learning.attempts a ON a.id = ai.attempt_id
                LEFT JOIN learning.questions q ON q.id = ai.question_id
                LEFT JOIN learning.question_knowledge_points qkp ON qkp.question_id = ai.question_id
                LEFT JOIN learning.knowledge_points qkp_point ON qkp_point.id = qkp.knowledge_point_id
                LEFT JOIN learning.lexemes lexeme ON lexeme.id = ai.lexeme_id
                LEFT JOIN learning.knowledge_points lexeme_point ON lexeme_point.id = lexeme.knowledge_point_id
                LEFT JOIN learning.topics topic ON topic.id = a.ref_id AND a.ref_type LIKE 'TOPIC_%'
                LEFT JOIN learning.knowledge_points topic_point ON topic_point.id = topic.knowledge_point_id
                WHERE ai.id IN (:itemIds)
                ORDER BY ai.id, qkp.role, qkp_point.code
                """).setParameter("itemIds", itemIds).getResultList();

        List<EvidenceRow> result = new ArrayList<>();
        for (Object value : rows) {
            Object[] row = (Object[]) value;
            try {
                ServedSnapshot snapshot = mapper.readValue(json(row[13]), ServedSnapshot.class);
                ServedItem served = snapshot.items().stream()
                        .filter(item -> item.no() == number(row[7]).intValue())
                        .findFirst().orElseThrow(() -> new IllegalStateException("Evidence differs from served snapshot"));
                List<QuestionOption> options = row[14] == null ? List.of()
                        : mapper.readValue(json(row[14]), OPTIONS);
                if (!java.util.Objects.equals(row[3] == null ? null : number(row[3]).longValue(), served.questionId())
                        || !java.util.Objects.equals(row[4] == null ? null : number(row[4]).longValue(), served.lexemeId())) {
                    throw new IllegalStateException("Evidence differs from served snapshot");
                }
                result.add(new EvidenceRow(number(row[0]).longValue(), number(row[1]).longValue(),
                        number(row[2]).longValue(), nullableLong(row[3]), nullableLong(row[4]),
                        (String) row[5], (Boolean) row[6], number(row[7]).intValue(), instant(row[8]),
                        (String) row[9], (String) row[10], nullableLong(row[11]), (Boolean) row[12], served,
                        options, (String) row[15], (String) row[16], (String) row[17], (String) row[18]));
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot read evidence snapshot", exception);
            }
        }
        return List.copyOf(result);
    }

    private static String json(Object value) {
        return value == null ? null : value.toString();
    }

    private static Number number(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalStateException("Unexpected numeric value in evidence projection");
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : number(value).longValue();
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof OffsetDateTime dateTime) return dateTime.toInstant();
        throw new IllegalStateException("Unexpected timestamp in evidence projection");
    }

    public record EvidenceRow(long itemId, long attemptId, long userId, Long questionId, Long lexemeId,
            String answer, Boolean correct, int orderIndex, Instant answeredAt, String itemType,
            String source, Long refId, boolean suspicious, ServedItem served, List<QuestionOption> options,
            String labelKid, String labelRole, String lexemeKid, String topicKid) {
        public EvidenceRow {
            options = List.copyOf(options);
        }
    }
}
