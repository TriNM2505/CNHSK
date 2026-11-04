package com.cnhsk.learning.content.importer.validate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.cnhsk.learning.content.importer.CsvReader;
import com.cnhsk.learning.content.importer.ExamImportSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExamRowValidatorTest {
    private final ExamImportSupport exams = mock(ExamImportSupport.class);
    private final ExamRowValidator validator = new ExamRowValidator(exams);

    @ParameterizedTest
    @ValueSource(strings = {"2:A|1:B", "1:A|1:B", "1:A|2:B|3:C|4:D|5:E|6:F|7:G|8:H|9:I|10:J|11:K", "1:"})
    void FR006_AT06_rejectsInvalidSections(String sections) {
        assertThat(validate(row(2, "1", sections)).errors()).singleElement().satisfies(error -> {
            assertThat(error.code()).isEqualTo("INVALID_VALUE");
            assertThat(error.column()).isEqualTo("sections");
        });
    }

    @Test
    void FR006_AT06_rejectsHskSevenAndDuplicateCode() {
        assertThat(validate(row(2, "7", "1:Đọc")).errors()).singleElement()
                .satisfies(error -> assertThat(error.column()).isEqualTo("hsk_level"));
        assertThat(validate(row(2, "1", "1:Đọc"), row(3, "1", "1:Đọc")).errors()).singleElement()
                .satisfies(error -> assertThat(error.code()).isEqualTo("DUPLICATE_IN_FILE"));
    }

    @Test
    void FR007_reportsLinkedSectionAndLevelConflictsInPreview() {
        when(exams.linkedQuestions(List.of("EXAM_TEST")))
                .thenReturn(Map.of("EXAM_TEST", List.of(new ExamImportSupport.LinkedQuestion(1, 1))));
        assertThat(validate(row(2, "1", "2:Đọc")).errors()).singleElement()
                .satisfies(error -> assertThat(error.code()).isEqualTo("INVALID_VALUE"));
        assertThat(validate(row(2, "2", "1:Đọc")).errors()).singleElement()
                .satisfies(error -> assertThat(error.code()).isEqualTo("LEVEL_MISMATCH"));
        assertThat(validate(row(2, "1", "1:Đổi tên|3:Thêm phần")).validRows()).hasSize(1);
    }

    private RowValidationResult validate(CsvReader.Row... rows) {
        return validator.validate(new CsvReader.ReadResult(rows.length, List.of(rows), List.of()));
    }

    private static CsvReader.Row row(int line, String level, String sections) {
        return new CsvReader.Row(line, Map.of("code", "EXAM_TEST", "title", "Đề tự soạn",
                "hsk_level", level, "sections", sections, "status", "DRAFT"));
    }
}
