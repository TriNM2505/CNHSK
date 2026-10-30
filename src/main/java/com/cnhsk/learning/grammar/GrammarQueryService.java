package com.cnhsk.learning.grammar;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeExtra;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.grammar.GrammarQueryRepository.GrammarRow;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GrammarQueryService {

    private final GrammarQueryRepository queries;
    private final LexemeRepository lexemes;

    public GrammarQueryService(GrammarQueryRepository queries, LexemeRepository lexemes) {
        this.queries = queries;
        this.lexemes = lexemes;
    }

    public List<GrammarSummary> list(long userId, List<Integer> hskLevels) {
        return queries.summaries(userId, hskLevels, null).stream().map(GrammarQueryService::summary).toList();
    }

    public GrammarLesson detail(long userId, long id) {
        Lexeme grammar = lexemes.findById(id)
                .filter(lexeme -> lexeme.getKind() == LexemeKind.GRAMMAR)
                .orElseThrow(GrammarQueryService::notFound);
        GrammarRow row = queries.summaries(userId, List.of(), id).stream().findFirst()
                .orElseThrow(GrammarQueryService::notFound);
        LexemeExtra extra = grammar.getExtra();
        List<Example> examples = extra.examples() == null ? List.of()
                : extra.examples().stream().map(example -> new Example(example.cn(), example.pinyin(), example.vi()))
                        .toList();
        GrammarSummary summary = summary(row);
        return new GrammarLesson(summary.id(), summary.text(), summary.titleVi(), summary.hskLevel(),
                summary.structure(), extra.explanation(), extra.commonMistake(), examples, summary.exerciseCount(),
                summary.learned(), summary.bestPercent(), summary.lastStudiedAt());
    }

    private static GrammarSummary summary(GrammarRow row) {
        return new GrammarSummary(row.id(), row.text(), row.titleVi(), row.hskLevel(), row.structure(), row.exerciseCount(),
                row.learned(), row.bestPercent(), row.lastStudiedAt());
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.GRAMMAR_POINT_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Không tìm thấy điểm ngữ pháp.", Map.of());
    }

    @Schema(name = "GrammarSummary")
    public record GrammarSummary(long id, String text, String titleVi, int hskLevel, String structure, int exerciseCount,
                                 boolean learned, Integer bestPercent, Instant lastStudiedAt) {
    }

    @Schema(name = "GrammarLesson")
    public record GrammarLesson(long id, String text, String titleVi, int hskLevel, String structure,
                                String explanation, String commonMistake, List<Example> examples,
                                int exerciseCount, boolean learned, Integer bestPercent, Instant lastStudiedAt) {
    }

    public record Example(String cn, String pinyin, String vi) {
    }
}
