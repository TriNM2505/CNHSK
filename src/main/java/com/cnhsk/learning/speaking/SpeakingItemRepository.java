package com.cnhsk.learning.speaking;

import java.util.List;

import com.cnhsk.learning.speaking.web.SpeakingViews.SpeakingItem;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reading material comes from content that already exists: dictionary words (with their recorded audio when there
 * is one) and the example sentences stored on grammar points. Nothing is written here.
 */
@Repository
class SpeakingItemRepository {
    private final NamedParameterJdbcTemplate jdbc;

    SpeakingItemRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Words with a recorded voice come first; the rest are read aloud by the browser's Chinese voice. */
    List<SpeakingItem> words(int level, int count) {
        return jdbc.query("""
                SELECT text, pinyin, meaning_vi, audio_url FROM learning.lexemes
                WHERE kind = 'WORD' AND hsk_level = :level
                ORDER BY (audio_url IS NULL), random() LIMIT :count
                """, new MapSqlParameterSource("level", level).addValue("count", count),
                (row, index) -> new SpeakingItem("WORD", row.getString("text"), row.getString("pinyin"),
                        row.getString("meaning_vi"), row.getString("audio_url"), level));
    }

    List<SpeakingItem> sentences(int level, int count) {
        return jdbc.query("""
                SELECT example->>'cn' AS text, example->>'pinyin' AS pinyin, example->>'vi' AS meaning
                FROM learning.lexemes grammar
                CROSS JOIN LATERAL jsonb_array_elements(CASE WHEN jsonb_typeof(grammar.extra->'examples') = 'array'
                    THEN grammar.extra->'examples' ELSE '[]'::jsonb END) AS example
                WHERE grammar.kind = 'GRAMMAR' AND grammar.hsk_level = :level
                  AND coalesce(example->>'cn', '') <> '' AND char_length(example->>'cn') <= 60
                ORDER BY random() LIMIT :count
                """, new MapSqlParameterSource("level", level).addValue("count", count),
                (row, index) -> new SpeakingItem("SENTENCE", row.getString("text"), row.getString("pinyin"),
                        row.getString("meaning"), null, level));
    }
}
