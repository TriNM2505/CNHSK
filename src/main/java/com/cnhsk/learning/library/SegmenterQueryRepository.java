package com.cnhsk.learning.library;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cnhsk.learning.library.Segmenter.Lexeme;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;

@Repository
public class SegmenterQueryRepository {
    private final EntityManager entityManager;

    public SegmenterQueryRepository(EntityManager entityManager) { this.entityManager = entityManager; }

    public Map<String, Lexeme> lookup(Set<String> texts) {
        if (texts.isEmpty()) return Map.of();
        String[] values = texts.toArray(String[]::new);
        List<?> rows = entityManager.createNativeQuery("""
                SELECT DISTINCT ON (x.text) x.id,x.kind,x.text,x.pinyin
                FROM learning.lexemes x
                WHERE x.kind IN ('WORD','GRAMMAR','CHAR') AND char_length(x.text) BETWEEN 1 AND 8
                  AND x.text = ANY(:texts)
                ORDER BY x.text,CASE x.kind WHEN 'WORD' THEN 0 WHEN 'GRAMMAR' THEN 1 ELSE 2 END,x.id ASC
                """, Tuple.class).unwrap(NativeQuery.class).setParameter("texts", values, String[].class).getResultList();
        Map<String, Lexeme> result = new LinkedHashMap<>();
        for (Object value : rows) {
            Tuple row = (Tuple) value;
            result.put(row.get("text", String.class), new Lexeme(((Number) row.get("id")).longValue(), row.get("pinyin", String.class)));
        }
        return Map.copyOf(result);
    }
}
