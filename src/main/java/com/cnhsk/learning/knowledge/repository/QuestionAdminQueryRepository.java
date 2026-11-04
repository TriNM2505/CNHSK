package com.cnhsk.learning.knowledge.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.cnhsk.learning.knowledge.entity.KnowledgeLabelRole;
import com.cnhsk.learning.knowledge.entity.KnowledgePoint;
import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.entity.QuestionKnowledgePoint;
import com.cnhsk.learning.knowledge.entity.QuestionStatus;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Repository;

/** Database filtering and pagination over learning-owned tables; JSONB is never filtered. */
@Repository
public class QuestionAdminQueryRepository {
    private final EntityManager entities;

    public QuestionAdminQueryRepository(EntityManager entities) {
        this.entities = entities;
    }

    public QuestionRows page(Filters filters, int page, int size) {
        CriteriaBuilder builder = entities.getCriteriaBuilder();
        var count = builder.createQuery(Long.class);
        Root<Question> counted = count.from(Question.class);
        count.select(builder.count(counted)).where(predicates(builder, count, counted, filters));
        long total = bind(entities.createQuery(count), filters).getSingleResult();
        long offset = (long) page * size;
        // Count first so even very large past-end page requests avoid an overflowing JPA offset.
        if (offset >= total) return new QuestionRows(List.of(), total);

        var content = builder.createQuery(Question.class);
        Root<Question> question = content.from(Question.class);
        content.select(question).where(predicates(builder, content, question, filters))
                .orderBy(builder.desc(question.get("id")));
        List<Question> items = bind(entities.createQuery(content), filters)
                .setFirstResult(Math.toIntExact(offset)).setMaxResults(size).getResultList();
        return new QuestionRows(items, total);
    }

    public List<LabelRow> labels(Set<Long> questionIds) {
        return entities.createQuery("""
                SELECT label.questionId AS questionId, point.code AS kid,
                       point.title AS nameVi, label.role AS role
                FROM QuestionKnowledgePoint label
                JOIN KnowledgePoint point ON point.id = label.knowledgePointId
                WHERE label.questionId IN :ids
                ORDER BY label.questionId, label.role, point.code
                """, Tuple.class).setParameter("ids", questionIds).getResultList().stream()
                .map(row -> new LabelRow(row.get("questionId", Long.class), row.get("kid", String.class),
                        row.get("nameVi", String.class), row.get("role", KnowledgeLabelRole.class))).toList();
    }

    private static Predicate[] predicates(CriteriaBuilder builder, AbstractQuery<?> query,
            Root<Question> question, Filters filters) {
        List<Predicate> conditions = new ArrayList<>();
        if (filters.hskLevel() != null) conditions.add(builder.equal(question.get("hskLevel"), builder.parameter(Short.class, "hskLevel")));
        if (filters.qtype() != null) conditions.add(builder.equal(question.get("qtype"), builder.parameter(QuestionType.class, "qtype")));
        if (filters.status() != null) conditions.add(builder.equal(question.get("status"), builder.parameter(QuestionStatus.class, "status")));
        if (filters.kid() != null) {
            var labeled = query.subquery(Integer.class);
            Root<QuestionKnowledgePoint> label = labeled.from(QuestionKnowledgePoint.class);
            Root<KnowledgePoint> point = labeled.from(KnowledgePoint.class);
            labeled.select(builder.literal(1)).where(builder.equal(label.get("questionId"), question.get("id")),
                    builder.equal(label.get("knowledgePointId"), point.get("id")),
                    builder.equal(point.get("code"), builder.parameter(String.class, "kid")));
            conditions.add(builder.exists(labeled));
        }
        if (filters.q() != null) {
            var pattern = builder.parameter(String.class, "pattern");
            conditions.add(builder.or(builder.like(builder.lower(question.get("code")), pattern, '!'),
                    builder.like(builder.lower(question.get("stem")), pattern, '!')));
        }
        return conditions.toArray(Predicate[]::new);
    }

    private static <T> TypedQuery<T> bind(TypedQuery<T> query, Filters filters) {
        if (filters.hskLevel() != null) query.setParameter("hskLevel", filters.hskLevel().shortValue());
        if (filters.qtype() != null) query.setParameter("qtype", filters.qtype());
        if (filters.status() != null) query.setParameter("status", filters.status());
        if (filters.kid() != null) query.setParameter("kid", filters.kid().toUpperCase(Locale.ROOT));
        if (filters.q() != null) query.setParameter("pattern", "%" + filters.q().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        return query;
    }

    public record Filters(Integer hskLevel, QuestionType qtype, QuestionStatus status, String kid, String q) { }
    public record QuestionRows(List<Question> items, long totalElements) { }
    public record LabelRow(long questionId, String kid, String nameVi, KnowledgeLabelRole role) { }
}
