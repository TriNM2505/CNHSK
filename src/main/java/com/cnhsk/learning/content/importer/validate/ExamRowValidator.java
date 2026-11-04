package com.cnhsk.learning.content.importer.validate;

import java.util.List;
import java.util.Set;

import com.cnhsk.learning.content.importer.CsvReader;
import com.cnhsk.learning.content.importer.ExamImportSupport;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.template.ImportTemplate;
import org.springframework.stereotype.Component;

@Component
public class ExamRowValidator implements RowValidator {
    private final ExamImportSupport exams;

    public ExamRowValidator(ExamImportSupport exams) {
        this.exams = exams;
    }

    @Override
    public ImportDataType dataType() {
        return ImportDataType.EXAM;
    }

    @Override
    public RowValidationResult validate(CsvReader.ReadResult input) {
        var template = ImportTemplate.forType(dataType());
        var rows = RowValidationSupport.rows(template, input);
        var linked = exams.linkedQuestions(rows.stream().filter(row -> row.present("code"))
                .map(row -> row.value("code")).distinct().toList());
        for (var row : rows) {
            row.requiredAndBroken(Set.of());
            row.lengths();
            if (row.eligible("code") && !row.value("code").matches("[A-Z0-9_-]{3,50}")) {
                row.invalid("code", "phải có 3–50 ký tự A–Z, 0–9, _ hoặc -");
            }
            if (row.eligible("hsk_level") && !row.value("hsk_level").matches("[1-6]")) {
                row.invalid("hsk_level", "phải từ 1 đến 6");
            }
            if (row.eligible("status") && !Set.of("DRAFT", "PUBLISHED", "ARCHIVED").contains(row.value("status"))) {
                row.invalid("status", "chỉ nhận DRAFT, PUBLISHED hoặc ARCHIVED");
            }
            if (!row.eligible("sections")) continue;
            try {
                var sections = ExamImportSupport.sections(row.value("sections"));
                if (row.eligible("code") && row.eligible("hsk_level")) {
                    ExamImportSupport.conflict(linked.getOrDefault(row.value("code"), List.of()), sections,
                            Integer.parseInt(row.value("hsk_level")))
                            .ifPresent(error -> row.error(error.field(), error.code(), error.message()));
                }
            } catch (IllegalArgumentException exception) {
                row.error("sections", "INVALID_VALUE", exception.getMessage());
            }
        }
        return RowValidationSupport.finish(template, input, rows);
    }
}
