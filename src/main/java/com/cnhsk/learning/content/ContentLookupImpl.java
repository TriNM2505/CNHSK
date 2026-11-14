package com.cnhsk.learning.content;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.cnhsk.learning.api.ContentLookup;
import com.cnhsk.learning.content.text.PinyinUtils;
import com.cnhsk.learning.content.text.TextNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentLookupImpl implements ContentLookup {
    private static final Logger log = LoggerFactory.getLogger(ContentLookupImpl.class);
    // Unicode whitespace trimmed by TextNormalizer, bound as data for PostgreSQL btrim.
    private static final String BLANK_CHARACTERS = " \t\n\r\f" + (char) 11 + (char) 28 + (char) 29 + (char) 30 + (char) 31
            + "\u00A0\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A"
            + "\u2028\u2029\u202F\u205F\u3000";
    private final NamedParameterJdbcTemplate jdbc;

    public ContentLookupImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LexemeCard> pool(GameContentQuery query) {
        if (query == null) throw new IllegalArgumentException("Game content query is required.");
        var parameters = new MapSqlParameterSource().addValue("userId", query.preferLearnedBy(), Types.BIGINT)
                .addValue("kinds", query.kinds()).addValue("limit", query.limit());
        String sql = "WITH learned AS (SELECT DISTINCT ai.lexeme_id FROM learning.attempt_items ai "
                + "JOIN learning.attempts a ON a.id = ai.attempt_id "
                + "WHERE a.user_id = :userId AND a.ref_type = 'TOPIC_STUDY' AND ai.item_type = 'STUDY_CARD' "
                + "AND ai.lexeme_id IS NOT NULL) "
                + "SELECT x.id, x.kind, x.text, x.pinyin, x.meaning_vi, x.meaning_norm, x.radical, x.stroke_count, "
                + "x.audio_url, x.hsk_level FROM learning.lexemes x "
                + "LEFT JOIN learned ON learned.lexeme_id = x.id WHERE x.kind IN (:kinds)";
        if (query.hskLevels().isEmpty()) {
            sql += " AND (learned.lexeme_id IS NOT NULL OR x.hsk_level = 1)";
        } else {
            sql += " AND x.hsk_level IN (:levels)";
            parameters.addValue("levels", query.hskLevels());
        }
        if (query.requireAudio() || query.requireRadical()) parameters.addValue("blank", BLANK_CHARACTERS);
        if (query.requireAudio()) sql += " AND NULLIF(btrim(x.audio_url, :blank), '') IS NOT NULL";
        if (query.requireRadical()) sql += " AND NULLIF(btrim(x.radical, :blank), '') IS NOT NULL";
        if (query.requireStrokeCount()) sql += " AND x.stroke_count > 0";
        sql += " ORDER BY (learned.lexeme_id IS NOT NULL) DESC, random() LIMIT :limit";
        return List.copyOf(jdbc.query(sql, parameters, (row, index) -> card(row)));
    }

    private LexemeCard card(ResultSet row) throws SQLException {
        String meaning = row.getString("meaning_vi");
        String normalized = TextNormalizer.normalizeCell("meaning_vi", row.getString("meaning_norm"));
        if (normalized == null || normalized.isEmpty()) normalized = TextNormalizer.normalizeMeaning(meaning);
        Set<String> keys = Arrays.stream(normalized.split("[;,]"))
                .map(part -> TextNormalizer.normalizeCell("meaning_vi", part)).filter(part -> !part.isEmpty())
                .collect(Collectors.toSet());
        String pinyin = row.getString("pinyin");
        return new LexemeCard(row.getLong("id"), row.getString("kind"), row.getString("text"), pinyin,
                pinyinKey(pinyin), TextNormalizer.normalizeCell("meaning_vi", meaning.split(";", 2)[0]), keys,
                row.getString("radical"), row.getObject("stroke_count", Integer.class), row.getString("audio_url"),
                row.getObject("hsk_level", Integer.class));
    }

    private static String pinyinKey(String pinyin) {
        if (pinyin == null || pinyin.isBlank()) return null;
        try {
            String numbered = PinyinUtils.toNumbered(pinyin);
            return numbered == null || numbered.isBlank() ? null : numbered.replace(" ", "");
        } catch (IllegalArgumentException exception) {
            log.warn("Invalid stored pinyin in game content; pronunciation key unavailable.");
            return null;
        }
    }
}
