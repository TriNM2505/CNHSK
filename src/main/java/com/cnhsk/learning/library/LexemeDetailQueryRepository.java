package com.cnhsk.learning.library;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import org.springframework.stereotype.Repository;

/** Public fields only; linked lexemes are fetched together rather than one query per reference. */
@Repository
public class LexemeDetailQueryRepository {

    private final EntityManager entityManager;

    public LexemeDetailQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Optional<DetailRow> find(long id) {
        List<?> rows = entityManager.createNativeQuery("""
                SELECT id, kind, text, pinyin, sino_viet, meaning_vi, hsk_level, audio_url, radical, stroke_count,
                       cast(extra -> 'examples' AS text) AS examples, extra ->> 'structure' AS structure
                FROM learning.lexemes WHERE id = :id
                """, Tuple.class).setParameter("id", id).getResultList();
        return rows.stream().findFirst().map(value -> {
            Tuple row = (Tuple) value;
            return new DetailRow(((Number) row.get("id")).longValue(), row.get("kind", String.class),
                    row.get("text", String.class), row.get("pinyin", String.class), row.get("sino_viet", String.class),
                    row.get("meaning_vi", String.class), integer(row.get("hsk_level")), row.get("audio_url", String.class),
                    row.get("radical", String.class), integer(row.get("stroke_count")), row.get("examples", String.class),
                    row.get("structure", String.class));
        });
    }

    public List<ReferenceRow> references(long id) {
        List<?> rows = entityManager.createNativeQuery("""
                WITH edges AS (
                    SELECT 'components' AS bucket, child_id AS id FROM learning.lexeme_links
                    WHERE parent_id = :id AND link_type = 'CONTAINS'
                    UNION ALL
                    SELECT 'containedIn', parent_id FROM learning.lexeme_links
                    WHERE child_id = :id AND link_type = 'CONTAINS'
                    UNION ALL
                    SELECT 'related', child_id FROM learning.lexeme_links
                    WHERE parent_id = :id AND link_type IN ('RELATED', 'SYNONYM')
                    UNION ALL
                    SELECT 'related', parent_id FROM learning.lexeme_links
                    WHERE child_id = :id AND link_type IN ('RELATED', 'SYNONYM')
                ), ranked AS (
                    SELECT bucket, id, row_number() OVER (PARTITION BY bucket ORDER BY id) AS position
                    FROM (SELECT DISTINCT bucket, id FROM edges) unique_edges
                )
                SELECT r.bucket, x.id, x.text, x.pinyin, x.meaning_vi
                FROM ranked r JOIN learning.lexemes x ON x.id = r.id
                WHERE r.bucket = 'components' OR r.position <= 10
                ORDER BY r.bucket, x.id
                """, Tuple.class).setParameter("id", id).getResultList();
        return rows.stream().map(value -> {
            Tuple row = (Tuple) value;
            return new ReferenceRow(row.get("bucket", String.class), new LexemeDetailService.LexemeRef(
                    ((Number) row.get("id")).longValue(), row.get("text", String.class), row.get("pinyin", String.class),
                    row.get("meaning_vi", String.class)));
        }).toList();
    }

    private static Integer integer(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    public record DetailRow(long id, String kind, String text, String pinyin, String sinoViet, String meaningVi,
                            Integer hskLevel, String audioUrl, String radical, Integer strokeCount,
                            String examples, String structure) {
    }

    public record ReferenceRow(String bucket, LexemeDetailService.LexemeRef lexeme) {
    }
}
