package com.cnhsk.learning.trial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import com.cnhsk.learning.trial.web.TrialViews.TrialQuestion;
import com.cnhsk.learning.trial.web.TrialViews.TrialSet;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sample questions for visitors without an account. The answer travels with each question and the browser marks
 * it, because nothing about a trial is stored: no session, attempt, progress or mastery is ever written.
 */
@Service
public class TrialService {
    static final int QUESTIONS = 5;
    private static final int POOL = 24;
    private final NamedParameterJdbcTemplate jdbc;

    public TrialService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** A candidate row: what is asked, what answers it, and how it is explained afterwards. */
    private record Row(String text, String pinyin, String answer, String audioUrl, String explanation) { }

    @Transactional(readOnly = true)
    public TrialSet questions(String feature, int level) {
        if (level < 1 || level > 9) throw invalid();
        List<Row> pool = switch (feature == null ? "" : feature) {
            case "VOCAB" -> words(level, "meaning_vi");
            case "LISTENING" -> words(level, "pinyin");
            case "HANZI" -> characters(level);
            case "GRAMMAR" -> sentences(level);
            default -> throw invalid();
        };
        return new TrialSet(feature, level, build(pool, "LISTENING".equals(feature)));
    }

    private List<Row> words(int level, String answerColumn) {
        return jdbc.query("""
                SELECT text, pinyin, %s AS answer, audio_url, meaning_vi FROM learning.lexemes
                WHERE kind = 'WORD' AND hsk_level = :level AND coalesce(%s, '') <> ''
                ORDER BY random() LIMIT :pool
                """.formatted(answerColumn, answerColumn), parameters(level), (row, index) -> new Row(row.getString("text"),
                row.getString("pinyin"), row.getString("answer"), row.getString("audio_url"),
                row.getString("text") + " · " + row.getString("pinyin") + " · " + row.getString("meaning_vi")));
    }

    /** Characters are asked by their Sino-Vietnamese reading, the way Vietnamese learners remember them. */
    private List<Row> characters(int level) {
        return jdbc.query("""
                SELECT text, pinyin, sino_viet AS answer, meaning_vi FROM learning.lexemes
                WHERE kind = 'CHAR' AND hsk_level = :level AND coalesce(sino_viet, '') <> ''
                ORDER BY random() LIMIT :pool
                """, parameters(level), (row, index) -> new Row(row.getString("text"), row.getString("pinyin"),
                row.getString("answer"), null, row.getString("text") + " · Hán Việt: " + row.getString("answer")
                        + " · " + row.getString("meaning_vi")));
    }

    /** Grammar is tried through its example sentences: read the sentence, pick what it means. */
    private List<Row> sentences(int level) {
        return jdbc.query("""
                SELECT example->>'cn' AS text, example->>'pinyin' AS pinyin, example->>'vi' AS answer,
                       grammar.text AS point
                FROM learning.lexemes grammar
                CROSS JOIN LATERAL jsonb_array_elements(CASE WHEN jsonb_typeof(grammar.extra->'examples') = 'array'
                    THEN grammar.extra->'examples' ELSE '[]'::jsonb END) AS example
                WHERE grammar.kind = 'GRAMMAR' AND grammar.hsk_level = :level
                  AND coalesce(example->>'cn', '') <> '' AND coalesce(example->>'vi', '') <> ''
                ORDER BY random() LIMIT :pool
                """, parameters(level), (row, index) -> new Row(row.getString("text"), row.getString("pinyin"),
                row.getString("answer"), null, "Điểm ngữ pháp: " + row.getString("point")));
    }

    /** The first rows become questions; wrong options are other answers from the same random pool. */
    private static List<TrialQuestion> build(List<Row> pool, boolean listening) {
        List<TrialQuestion> questions = new ArrayList<>();
        for (Row row : pool) {
            if (questions.size() == QUESTIONS) break;
            Set<String> options = new LinkedHashSet<>();
            options.add(row.answer());
            for (Row other : pool) {
                if (options.size() == 4) break;
                options.add(other.answer());
            }
            if (options.size() < 2) continue;
            List<String> shuffled = new ArrayList<>(options);
            Collections.shuffle(shuffled, ThreadLocalRandom.current());
            questions.add(new TrialQuestion(row.text(), listening ? null : row.pinyin(), row.audioUrl(),
                    shuffled, shuffled.indexOf(row.answer()), row.explanation()));
        }
        return questions;
    }

    private static MapSqlParameterSource parameters(int level) {
        return new MapSqlParameterSource("level", level).addValue("pool", POOL);
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Phần học hoặc cấp HSK không hợp lệ.", Map.of());
    }
}
