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

    /** Pronunciation units keyed "stage:code" (e.g. "1:BP_01"); {@code kid} is the NA node already linked, if any. */
    public List<Target> pronUnits(Collection<String> keys) {
        if (keys.isEmpty()) return List.of();
        return select("""
                SELECT s.id, s.stage_index || ':' || (u->>'code') AS target_key, NULL::bigint AS knowledge_point_id,
                       u->>'kid' AS kid
                FROM learning.pron_stages s CROSS JOIN LATERAL jsonb_array_elements(s.units) AS u
                WHERE s.stage_index || ':' || (u->>'code') IN (:keys)
                """, Map.of("keys", keys));
    }

    public Optional<Target> lockPronUnit(String key) {
        return select("""
                SELECT s.id, s.stage_index || ':' || (u->>'code') AS target_key, NULL::bigint AS knowledge_point_id,
                       u->>'kid' AS kid
                FROM learning.pron_stages s CROSS JOIN LATERAL jsonb_array_elements(s.units) AS u
                WHERE s.stage_index || ':' || (u->>'code') = :key
                FOR UPDATE OF s
                """, Map.of("key", key)).stream().findFirst();
    }

    /** Sets {@code kid} on one unit and keeps every other unit and the order unchanged. */
    public void updatePronUnit(long stageId, String unitCode, String kid) {
        entityManager.createNativeQuery("""
                UPDATE learning.pron_stages SET updated_at = now(), units = (
                    SELECT jsonb_agg(CASE WHEN e.u->>'code' = :code THEN jsonb_set(e.u, '{kid}', to_jsonb(CAST(:kid AS text)))
                                          ELSE e.u END ORDER BY e.ord)
                    FROM jsonb_array_elements(units) WITH ORDINALITY AS e(u, ord))
                WHERE id = :id
                """).setParameter("code", unitCode).setParameter("kid", kid).setParameter("id", stageId).executeUpdate();
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
