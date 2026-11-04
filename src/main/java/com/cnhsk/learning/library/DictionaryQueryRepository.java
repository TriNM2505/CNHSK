package com.cnhsk.learning.library;

import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import org.springframework.stereotype.Repository;

/** Read-only projections of public dictionary fields; all query values are bound parameters. */
@Repository
public class DictionaryQueryRepository {

    private static final String FIELDS = "x.id, x.kind, x.text, x.pinyin, x.sino_viet, x.meaning_vi, x.hsk_level";
    private final EntityManager entityManager;

    public DictionaryQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Result exact(String text, int page, int size) {
        return search("x.text = :text", "CASE x.kind WHEN 'WORD' THEN 0 WHEN 'GRAMMAR' THEN 1 ELSE 2 END, x.id",
                Map.of("text", text), page, size);
    }

    public Result characters(List<String> characters, int page, int size) {
        return search("x.kind = 'CHAR' AND x.text IN (:characters)",
                "array_position(string_to_array(:sequence, ','), x.text), x.id",
                Map.of("characters", characters, "sequence", String.join(",", characters)), page, size);
    }

    public Result pinyin(PinyinQuery query, int page, int size) {
        String order = """
                CASE WHEN cast(:preferred AS text) IS NOT NULL
                     AND regexp_replace(lower(x.pinyin), '[ ''-]', '', 'g') = cast(:preferred AS text)
                     THEN 0 ELSE 1 END, x.frequency DESC, x.id
                """;
        // The normalized column is compact on import; tolerate spaces in older normalized values too.
        String where = "replace(x.pinyin_norm, ' ', '') = :normalized";
        long total = count(where, Map.of("normalized", query.normalized()));
        Query rows = entityManager.createNativeQuery("SELECT " + FIELDS + " FROM learning.lexemes x WHERE "
                        + where + " ORDER BY " + order + " LIMIT :limit OFFSET :offset", Tuple.class)
                .setParameter("normalized", query.normalized()).setParameter("preferred", query.preferredPinyin())
                .setParameter("limit", size).setParameter("offset", (long) page * size);
        return new Result(project(rows), total);
    }

    public Result meaning(String meaning, int page, int size) {
        String escaped = meaning.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return search("x.meaning_norm ILIKE :contains ESCAPE '!'",
                "CASE WHEN x.meaning_norm = :meaning THEN 0 WHEN x.meaning_norm ILIKE :prefix ESCAPE '!' THEN 1 ELSE 2 END, x.frequency DESC, x.id",
                Map.of("meaning", meaning, "prefix", escaped + "%", "contains", "%" + escaped + "%"), page, size);
    }

    public List<RadicalService.RadicalCount> radicals() {
        List<?> rows = entityManager.createNativeQuery("""
                SELECT radical, count(*) AS char_count FROM learning.lexemes
                WHERE kind = 'CHAR' AND radical IS NOT NULL AND radical <> ''
                GROUP BY radical ORDER BY char_count DESC, radical ASC
                """, Tuple.class).getResultList();
        return rows.stream().map(value -> {
            Tuple row = (Tuple) value;
            return new RadicalService.RadicalCount(row.get("radical", String.class),
                    ((Number) row.get("char_count")).longValue());
        }).toList();
    }

    public Result byRadical(String radical, Integer strokes, boolean approximate, int page, int size) {
        String where = "x.kind = 'CHAR'";
        Map<String, Object> values = new java.util.HashMap<>();
        if (radical != null) {
            where += " AND x.radical = :radical";
            values.put("radical", radical);
        }
        if (strokes != null) {
            where += approximate ? " AND x.stroke_count IN (:strokes)" : " AND x.stroke_count = :strokes";
            values.put("strokes", approximate
                    ? java.util.stream.IntStream.of(strokes - 1, strokes + 1)
                            .filter(value -> value >= 1 && value <= 64).boxed().toList()
                    : strokes);
        }
        return search(where, "x.stroke_count ASC NULLS LAST, x.frequency DESC, x.id ASC", values, page, size);
    }

    private Result search(String where, String order, Map<String, ?> values, int page, int size) {
        // where/order are fixed SQL fragments from the methods above, never caller-supplied text.
        Query rows = entityManager.createNativeQuery("SELECT " + FIELDS + " FROM learning.lexemes x WHERE "
                + where + " ORDER BY " + order + " LIMIT :limit OFFSET :offset", Tuple.class);
        values.forEach(rows::setParameter);
        rows.setParameter("limit", size).setParameter("offset", (long) page * size);
        Map<String, ?> countValues = values.entrySet().stream()
                .filter(entry -> where.contains(":" + entry.getKey()))
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return new Result(project(rows), count(where, countValues));
    }

    private long count(String where, Map<String, ?> values) {
        Query count = entityManager.createNativeQuery("SELECT count(*) FROM learning.lexemes x WHERE " + where);
        values.forEach(count::setParameter);
        return ((Number) count.getSingleResult()).longValue();
    }

    private static List<LexemeRow> project(Query query) {
        List<?> rows = query.getResultList();
        return rows.stream().map(value -> {
            Tuple row = (Tuple) value;
            Number level = (Number) row.get("hsk_level");
            return new LexemeRow(((Number) row.get("id")).longValue(), row.get("kind", String.class),
                    row.get("text", String.class), row.get("pinyin", String.class), row.get("sino_viet", String.class),
                    row.get("meaning_vi", String.class), level == null ? null : level.intValue());
        }).toList();
    }

    public record LexemeRow(long id, String kind, String text, String pinyin, String sinoViet, String meaningVi,
                            Integer hskLevel) {
    }

    public record Result(List<LexemeRow> items, long total) {
    }
}
