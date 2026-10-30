package com.cnhsk.learning.content.importer.validate;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.entity.Topic;
import com.cnhsk.learning.content.importer.CsvReader;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.template.ImportTemplate;
import com.cnhsk.learning.content.importer.validate.RowValidationSupport.Context;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.content.repository.TopicRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TopicItemRowValidator implements RowValidator {

    private final TopicRepository topics;
    private final LexemeRepository lexemes;

    public TopicItemRowValidator(TopicRepository topics, LexemeRepository lexemes) {
        this.topics = topics;
        this.lexemes = lexemes;
    }

    @Override
    public ImportDataType dataType() {
        return ImportDataType.TOPIC_ITEM;
    }

    @Override
    @Transactional(readOnly = true)
    public RowValidationResult validate(CsvReader.ReadResult input) {
        ImportTemplate template = ImportTemplate.forType(dataType());
        List<Context> rows = RowValidationSupport.rows(template, input);
        for (Context row : rows) {
            row.requiredAndBroken(Set.of("text"));
            row.lengths();
            row.kind();
            row.integer("order_index", 0, 100000, false);
        }
        Set<String> codes = rows.stream().filter(row -> row.eligible("topic_code"))
                .map(row -> row.value("topic_code")).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> existingTopics = codes.isEmpty() ? Set.of()
                : topics.findByCodeIn(codes).stream().map(Topic::getCode).collect(Collectors.toSet());
        Set<List<String>> existingLexemes = existingLexemes(rows);
        for (Context row : rows) {
            if (row.eligible("topic_code") && !existingTopics.contains(row.value("topic_code"))) {
                row.error("topic_code", "REFERENCE_NOT_FOUND", "Không tìm thấy chủ đề '"
                        + RowValidationSupport.limitedValue(row.value("topic_code"))
                        + "' — nhập mẫu Chủ đề từ vựng trước");
            }
            if (row.eligible("kind") && row.eligible("text")
                    && !existingLexemes.contains(List.of(row.value("kind"), row.value("text")))) {
                row.error("text", "REFERENCE_NOT_FOUND", "Không tìm thấy chữ/từ '"
                        + RowValidationSupport.limitedValue(row.value("text"))
                        + "' — nhập mẫu Chữ và từ vựng trước");
            }
        }
        return RowValidationSupport.finish(template, input, rows);
    }

    private Set<List<String>> existingLexemes(List<Context> rows) {
        Set<List<String>> existing = new HashSet<>();
        for (LexemeKind kind : List.of(LexemeKind.CHAR, LexemeKind.WORD)) {
            Set<String> texts = rows.stream().filter(row -> row.eligible("kind") && row.eligible("text"))
                    .filter(row -> row.value("kind").equals(kind.name())).map(row -> row.value("text"))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!texts.isEmpty()) {
                for (Lexeme lexeme : lexemes.findByKindAndTextIn(kind, texts)) {
                    existing.add(List.of(lexeme.getKind().name(), lexeme.getText()));
                }
            }
        }
        return existing;
    }
}
