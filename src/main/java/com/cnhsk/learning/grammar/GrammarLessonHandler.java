package com.cnhsk.learning.grammar;

import java.util.LinkedHashMap;
import java.util.Map;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeExtra;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class GrammarLessonHandler implements PracticeSourceHandler {

    private static final int READ_TOO_FAST_MS = 5_000;
    private static final int LONG_EXPLANATION_CHARS = 300;

    private final LexemeRepository lexemes;
    private final AttemptRepository attempts;
    private final NamedParameterJdbcTemplate jdbc;

    public GrammarLessonHandler(LexemeRepository lexemes, AttemptRepository attempts, NamedParameterJdbcTemplate jdbc) {
        this.lexemes = lexemes;
        this.attempts = attempts;
        this.jdbc = jdbc;
    }

    @Override
    public PracticeSource source() {
        return PracticeSource.GRAMMAR_LESSON;
    }

    @Override
    public Map<String, Object> enrichMeta(ServedItem item, Answer answer) {
        // Reading evidence is derived on the server; caller-supplied clientMeta is never stored.
        if (item.type() != ItemType.LESSON_READ || answer.durationMs() >= READ_TOO_FAST_MS) return Map.of();
        String explanation = lexemes.findById(item.lexemeId()).map(Lexeme::getExtra)
                .map(LexemeExtra::explanation).orElse(null);
        boolean longExplanation = explanation != null
                && explanation.codePointCount(0, explanation.length()) >= LONG_EXPLANATION_CHARS;
        return longExplanation ? Map.of("readTooFast", true) : Map.of();
    }

    @Override
    public Map<String, Object> onFinish(FinishContext context) {
        // The engine owns the finish row lock and transaction. Never acquire a start advisory lock here.
        int total = 0;
        for (JsonNode served : attempts.findById(context.sessionId()).orElseThrow().getServedItems().path("items")) {
            if (ItemType.GRAMMAR_CHOICE.name().equals(served.path("type").asText())) total++;
        }
        int answered = 0;
        int correct = 0;
        for (GradedItem graded : context.items()) {
            if (graded.item().type() != ItemType.GRAMMAR_CHOICE || !graded.counted()) continue;
            answered++;
            if (Boolean.TRUE.equals(graded.correct())) correct++;
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("exercisesTotal", total);
        extra.put("answered", answered);
        extra.put("correct", correct);
        extra.put("percent", answered == 0 ? null : correct * 100 / answered);
        extra.put("skippedExercises", answered < total);
        extra.put("firstTimeLearned", !learnedBefore(context));
        return extra;
    }

    /** History excludes the current row even if JPA already flushed its SUBMITTED status. */
    private boolean learnedBefore(FinishContext context) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM learning.attempts
                    WHERE user_id = :userId AND ref_type = 'GRAMMAR_LESSON' AND ref_id = :grammarId
                      AND status = 'SUBMITTED' AND id <> :sessionId)
                """, new MapSqlParameterSource().addValue("userId", context.userId())
                        .addValue("grammarId", context.refId()).addValue("sessionId", context.sessionId()),
                Boolean.class));
    }
}
