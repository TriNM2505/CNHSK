package com.cnhsk.learning.content.importer.validate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.importer.CsvReader;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.template.ImportTemplate;
import com.cnhsk.learning.content.importer.validate.RowValidationSupport.Context;
import com.cnhsk.learning.content.repository.LexemeRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GrammarExerciseRowValidator implements RowValidator {

    private final LexemeRepository lexemes;

    public GrammarExerciseRowValidator(LexemeRepository lexemes) {
        this.lexemes = lexemes;
    }

    @Override
    public ImportDataType dataType() {
        return ImportDataType.GRAMMAR_EXERCISE;
    }

    @Override
    @Transactional(readOnly = true)
    public RowValidationResult validate(CsvReader.ReadResult input) {
        ImportTemplate template = ImportTemplate.forType(dataType());
        List<Context> rows = RowValidationSupport.rows(template, input);
        for (Context row : rows) {
            row.requiredAndBroken(Set.of("grammar_text"));
            if (row.present("option_d")) {
                row.require("option_c");
            }
            row.lengths();
            row.integer("exercise_no", 1, 20, false);
            row.canonicalInteger("exercise_no");
            row.answer();
        }
        Set<String> texts = rows.stream().filter(row -> row.eligible("grammar_text"))
                .map(row -> row.value("grammar_text")).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> existing = texts.isEmpty() ? Set.of()
                : lexemes.findByKindAndTextIn(LexemeKind.GRAMMAR, texts).stream()
                        .map(Lexeme::getText).collect(Collectors.toSet());
        for (Context row : rows) {
            if (row.eligible("grammar_text") && !existing.contains(row.value("grammar_text"))) {
                row.error("grammar_text", "REFERENCE_NOT_FOUND", "Không tìm thấy điểm ngữ pháp '"
                        + RowValidationSupport.limitedValue(row.value("grammar_text"))
                        + "' — nhập mẫu Điểm ngữ pháp trước");
            }
        }
        return RowValidationSupport.finish(template, input, rows);
    }
}
