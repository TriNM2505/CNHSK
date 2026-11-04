package com.cnhsk.learning.content.importer.validate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import com.cnhsk.learning.content.importer.CsvReader;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.knowledge.entity.KnowledgeDomain;
import com.cnhsk.learning.knowledge.entity.KnowledgePoint;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class KnowledgeEdgeRowValidatorTest {
    private KnowledgePointRepository points;
    private KnowledgeEdgeRowValidator validator;

    @BeforeEach
    void setup() {
        points = mock(KnowledgePointRepository.class);
        validator = new KnowledgeEdgeRowValidator(points);
        when(points.findAll()).thenReturn(List.of(node(1), node(2), node(3), node(4), node(5), node(6)));
    }

    @Test
    void AT05_normalizesKidsAndReportsBothMissingReferences() {
        var result = validate(row(2, " tv1-001 ", "tv1-002", "PREREQ"), row(3, "missing", "absent", "SUPPORT"));
        assertThat(validator.dataType()).isEqualTo(ImportDataType.KNOWLEDGE_EDGE);
        assertThat(result.validRows()).singleElement().satisfies(value -> assertThat(value.values())
                .containsEntry("from_kid", "TV1-001").containsEntry("to_kid", "TV1-002"));
        assertThat(result.errors()).extracting(CsvReader.RowError::code).containsExactly("REFERENCE_NOT_FOUND", "REFERENCE_NOT_FOUND");
        assertThat(result.errors()).extracting(CsvReader.RowError::column).containsExactly("from_kid", "to_kid");
    }

    @ParameterizedTest
    @ValueSource(strings = {"from_kid", "to_kid", "edge_type"})
    void requiredFieldsAreCheckedBeforeGraphRules(String field) {
        Map<String, String> values = new LinkedHashMap<>(row(2, "TV1-001", "TV1-002", "PREREQ").values());
        values.put(field, "  ");
        assertThat(validate(new CsvReader.Row(2, values)).errors()).singleElement()
                .extracting(CsvReader.RowError::code).isEqualTo("REQUIRED_MISSING");
    }

    @ParameterizedTest
    @ValueSource(strings = {"prereq", "OTHER"})
    void unknownTypesAreInvalidValues(String type) {
        assertCode(validate(row(2, "TV1-001", "TV1-002", type)), "INVALID_VALUE");
    }

    @ParameterizedTest
    @CsvSource({"PREREQ,PREREQ", "PREREQ,SUPPORT", "SUPPORT,PREREQ", "SUPPORT,SUPPORT"})
    void repeatedPairIsAlwaysDuplicateInFileBeforeCheckingItsType(String first, String second) {
        var result = validate(row(8, "TV1-001", "TV1-002", second), row(3, "TV1-001", "TV1-002", first));
        assertThat(result.validRows()).singleElement().extracting(RowValidationResult.ValidatedRow::line).isEqualTo(3);
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.code()).isEqualTo("DUPLICATE_IN_FILE");
            assertThat(error.line()).isEqualTo(8);
            assertThat(error.message()).contains("3");
        });
    }

    @Test
    void invalidEarlierRowsDoNotReserveKeysOrContributePaths() {
        var result = validate(row(2, "TV1-001", "TV1-002", "BAD"), row(7, "TV1-002", "TV1-001", "PREREQ"),
                row(9, "TV1-001", "TV1-002", "SUPPORT"));
        assertThat(result.validRows()).extracting(RowValidationResult.ValidatedRow::line).containsExactly(7, 9);
        assertCode(result, "INVALID_VALUE");
    }

    @Test
    void AT06_onlyAcceptedEarlierRowsParticipateInCyclesAndRedundancy() {
        var result = validate(row(2, "TV1-001", "TV1-002", "PREREQ"), row(3, "TV1-002", "TV1-001", "PREREQ"),
                row(4, "TV1-002", "TV1-003", "PREREQ"), row(5, "TV1-001", "TV1-003", "PREREQ"));
        assertThat(result.validRows()).extracting(RowValidationResult.ValidatedRow::line).containsExactly(2, 4);
        assertThat(result.errors()).extracting(CsvReader.RowError::code).containsExactly("EDGE_CYCLE", "EDGE_REDUNDANT");
    }

    @Test
    void AT06_fourEarlierPrerequisitesConsumeAllSlots() {
        var rows = IntStream.rangeClosed(1, 5).mapToObj(id -> row(id + 1, "TV1-00" + id, "TV1-006", "PREREQ"))
                .toArray(CsvReader.Row[]::new);
        assertThat(validate(rows).validRows()).hasSize(4);
        assertCode(validate(rows), "TOO_MANY_PREREQS");
    }

    @Test
    void AT08_conflictComesFromTheDatabaseAndARepeatedExistingTypeIsValid() {
        KnowledgePoint target = node(2);
        target.setSupportIds(new Long[]{1L});
        when(points.findAll()).thenReturn(List.of(node(1), target));
        assertCode(validate(row(2, "TV1-001", "TV1-002", "PREREQ")), "EDGE_TYPE_CONFLICT");
        assertThat(validate(row(2, "TV1-001", "TV1-002", "SUPPORT")).errors()).isEmpty();
    }

    @Test
    void oneDatabaseSnapshotServesEveryRowAndRetainsCsvErrors() {
        List<CsvReader.Row> rows = IntStream.range(0, 2000)
                .mapToObj(i -> row(i + 2, "TV1-001", "TV1-002", "SUPPORT")).toList();
        var csvError = new CsvReader.RowError(3000, null, null, "INVALID_VALUE", "Lỗi cấu trúc tự soạn");
        var result = validator.validate(new CsvReader.ReadResult(2001, rows, List.of(csvError)));
        assertThat(result.validRows()).hasSize(1);
        assertThat(result.errors()).hasSize(2000).last().isEqualTo(csvError);
        verify(points, times(1)).findAll();
        verifyNoMoreInteractions(points);
    }

    private RowValidationResult validate(CsvReader.Row... rows) {
        return validator.validate(new CsvReader.ReadResult(rows.length, List.of(rows), List.of()));
    }
    private static void assertCode(RowValidationResult result, String code) {
        assertThat(result.errors()).singleElement().extracting(CsvReader.RowError::code).isEqualTo(code);
    }
    private static CsvReader.Row row(int line, String from, String to, String type) {
        return new CsvReader.Row(line, Map.of("from_kid", from, "to_kid", to, "edge_type", type));
    }
    private static KnowledgePoint node(long id) {
        KnowledgePoint point = new KnowledgePoint("TV1-%03d".formatted(id), "Nút tự soạn", KnowledgeDomain.TV, "示例", (short) 1);
        ReflectionTestUtils.setField(point, "id", id);
        return point;
    }
}
