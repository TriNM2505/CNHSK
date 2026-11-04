package com.cnhsk.learning.content.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.cnhsk.learning.content.entity.LexemeKind;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import org.springframework.stereotype.Repository;

/** Native projections keep the V5 link columns independent of the shared content entities. */
@Repository
public class NodeLinkRepository {
    private final EntityManager entityManager;

    public NodeLinkRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<Target> topics(Collection<String> codes) {
        if (codes.isEmpty()) return List.of();
        return select("""
                SELECT t.id, t.code AS target_key, t.knowledge_point_id, kp.code AS kid
                FROM learning.topics t
                LEFT JOIN learning.knowledge_points kp ON kp.id = t.knowledge_point_id
                WHERE t.code IN (:codes)
                """, Map.of("codes", codes));
    }

    public List<Target> lexemes(LexemeKind kind, Collection<String> texts) {
        if (texts.isEmpty()) return List.of();
        return select("""
                SELECT x.id, x.text AS target_key, x.knowledge_point_id, kp.code AS kid
                FROM learning.lexemes x
                LEFT JOIN learning.knowledge_points kp ON kp.id = x.knowledge_point_id
                WHERE x.kind = :kind AND x.text IN (:texts)
                """, Map.of("kind", kind.name(), "texts", texts));
    }

    public Optional<Target> lockTopic(String code) {
        return select("""
                SELECT id, code AS target_key, knowledge_point_id, NULL::text AS kid
                FROM learning.topics WHERE code = :code FOR UPDATE
                """, Map.of("code", code)).stream().findFirst();
    }

    public Optional<Target> lockLexeme(LexemeKind kind, String text) {
        return select("""
                SELECT id, text AS target_key, knowledge_point_id, NULL::text AS kid
                FROM learning.lexemes WHERE kind = :kind AND text = :text FOR UPDATE
                """, Map.of("kind", kind.name(), "text", text)).stream().findFirst();
    }

    public void updateTopic(long id, long pointId) {
        entityManager.createNativeQuery("UPDATE learning.topics SET knowledge_point_id = :kp WHERE id = :id")
                .setParameter("kp", pointId).setParameter("id", id).executeUpdate();
    }

    public void updateLexeme(long id, long pointId) {
        entityManager.createNativeQuery("UPDATE learning.lexemes SET knowledge_point_id = :kp WHERE id = :id")
                .setParameter("kp", pointId).setParameter("id", id).executeUpdate();
    }

    private List<Target> select(String sql, Map<String, ?> parameters) {
        var query = entityManager.createNativeQuery(sql, Tuple.class);
        parameters.forEach(query::setParameter);
        List<?> rows = query.getResultList();
        return rows.stream().map(value -> {
            Tuple row = (Tuple) value;
            Number pointId = (Number) row.get("knowledge_point_id");
            return new Target(((Number) row.get("id")).longValue(), row.get("target_key", String.class),
                    pointId == null ? null : pointId.longValue(), row.get("kid", String.class));
        }).toList();
    }

    public record Target(long id, String key, Long pointId, String kid) { }
}
