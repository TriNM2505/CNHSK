package com.cnhsk.learning.content.importer.validate;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.importer.CsvReader;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.repository.NodeLinkRepository;
import com.cnhsk.learning.content.repository.NodeLinkRepository.Target;
import com.cnhsk.learning.knowledge.entity.KnowledgeDomain;
import com.cnhsk.learning.knowledge.entity.KnowledgePoint;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NodeLinkRowValidatorTest {
    private NodeLinkRepository targets;
    private KnowledgePointRepository points;
    private NodeLinkRowValidator validator;

    @BeforeEach
    void setup() {
        targets = mock(NodeLinkRepository.class);
        points = mock(KnowledgePointRepository.class);
        validator = new NodeLinkRowValidator(targets, points);
        when(targets.topics(any())).thenAnswer(call -> ((Collection<String>) call.getArgument(0)).stream()
                .filter(key -> !key.equals("MISSING")).map(key -> new Target(1, key, null, null)).toList());
        when(targets.lexemes(any(), any())).thenAnswer(call -> ((Collection<String>) call.getArgument(1)).stream()
                .filter(key -> !key.equals("MISSING")).map(key -> new Target(2, key, null, null)).toList());
        when(points.findByCodeIn(any())).thenAnswer(call -> ((Collection<String>) call.getArgument(0)).stream()
                .filter(kid -> List.of("TV1-801", "TV1-802", "CH1-002", "NP2-803").contains(kid))
                .map(kid -> new KnowledgePoint(kid, "Nút tự soạn", KnowledgeDomain.valueOf(kid.substring(0, 2)), "示例",
                        (short) Character.digit(kid.charAt(2), 10))).toList());
    }

    @ParameterizedTest
    @CsvSource({"TOPIC,T09_TOPIC,TV1-801", "WORD,水杯,TV1-801", "CHAR,木,CH1-002", "GRAMMAR,是……的,NP2-803"})
    void FR013_AT13_fourTargetTypesResolveToTheirRequiredDomain(String type, String key, String kid) {
        var result = validate(row(2, type, key, kid));
        assertThat(validator.dataType()).isEqualTo(ImportDataType.NODE_LINK);
        assertThat(result.errors()).isEmpty();
        assertThat(result.validRows()).hasSize(1);
        if (!type.equals("TOPIC")) verify(targets).lexemes(eq(LexemeKind.valueOf(type)), eq(java.util.Set.of(key)));
    }

    @ParameterizedTest
    @CsvSource({"TOPIC,CH1-002", "WORD,NP2-803", "CHAR,TV1-801", "GRAMMAR,TV1-801"})
    void FR013_AT13_wrongDomainIsReportedOnlyAfterResolvingBothReferences(String type, String kid) {
        assertThat(validate(row(2, type, "示例", kid)).errors()).singleElement()
                .satisfies(error -> { assertThat(error.code()).isEqualTo("DOMAIN_MISMATCH"); assertThat(error.column()).isEqualTo("kid"); });
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOPIC", "WORD", "CHAR", "GRAMMAR"})
    void FR013_missingTargetTakesPrecedenceOverDomainMismatch(String type) {
        assertThat(validate(row(2, type, "MISSING", "CH1-002")).errors()).singleElement()
                .satisfies(error -> { assertThat(error.code()).isEqualTo("REFERENCE_NOT_FOUND"); assertThat(error.column()).isEqualTo("target_key"); });
    }

    @ParameterizedTest
    @ValueSource(strings = {"TV1-999", "abc", "TV8-001"})
    void FR013_missingOrMalformedKidCannotBeLinked(String kid) {
        assertThat(validate(row(2, "WORD", "水杯", kid)).errors()).singleElement()
                .satisfies(error -> { assertThat(error.code()).isEqualTo("REFERENCE_NOT_FOUND"); assertThat(error.column()).isEqualTo("kid"); });
    }

    @Test
    void FR013_twoMissingReferencesKeepColumnOrderWithoutAddingDomainMismatch() {
        assertThat(validate(row(2, "GRAMMAR", "MISSING", "TV1-999")).errors())
                .extracting(CsvReader.RowError::code).containsExactly("REFERENCE_NOT_FOUND", "REFERENCE_NOT_FOUND");
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEXEME", "word", "OTHER"})
    void FR013_onlyTheFourDeclaredTypeValuesAreAccepted(String type) {
        assertThat(validate(row(2, type, "水杯", "TV1-801")).errors()).singleElement()
                .satisfies(error -> assertThat(error.code()).isEqualTo("INVALID_VALUE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"target_type", "target_key", "kid"})
    void FR013_allThreeCellsAreRequired(String field) {
        var cells = new LinkedHashMap<>(row(2, "WORD", "水杯", "TV1-801").values());
        cells.put(field, " \t ");
        assertThat(validate(new CsvReader.Row(2, cells)).errors()).singleElement()
                .satisfies(error -> { assertThat(error.code()).isEqualTo("REQUIRED_MISSING"); assertThat(error.column()).isEqualTo(field); });
    }

    @Test
    void FR013_normalizesWhitespaceUnicodeAndKidCaseBeforeLookup() {
        var result = validate(row(2, " GRAMMAR ", " 是  +  O ", " np2-803 "));
        assertThat(result.errors()).isEmpty();
        assertThat(result.validRows().get(0).values()).containsEntry("target_key", "是 + O").containsEntry("kid", "NP2-803");
    }

    @ParameterizedTest
    @ValueSource(strings = {"TV1-801", "TV1-802"})
    void FR013_duplicateNaturalKeyDoesNotIncludeKidAndUsesPhysicalLineNumbers(String secondKid) {
        var result = validate(row(7, "WORD", "水杯", secondKid), row(3, "WORD", "水杯", "tv1-801"));
        assertThat(result.validRows()).singleElement().satisfies(row -> assertThat(row.line()).isEqualTo(3));
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.line()).isEqualTo(7); assertThat(error.code()).isEqualTo("DUPLICATE_IN_FILE");
            assertThat(error.message()).contains("3");
        });
    }

    @Test
    void FR013_invalidFirstOccurrenceDoesNotReserveTheNaturalKeyAndKindsRemainDistinct() {
        var result = validate(row(3, "WORD", "木", "CH1-002"), row(7, "WORD", "木", "TV1-801"), row(9, "CHAR", "木", "CH1-002"));
        assertThat(result.validRows()).extracting(RowValidationResult.ValidatedRow::line).containsExactly(7, 9);
        assertThat(result.errors()).singleElement().satisfies(error -> assertThat(error.code()).isEqualTo("DOMAIN_MISMATCH"));
    }

    @Test
    void FR013_referenceCollectionsAreBoundedToFiveHundredWithoutPerRowLookups() {
        var rows = IntStream.range(0, 1201).mapToObj(index -> row(index + 2, "WORD", "词" + index, "TV1-801")).toList();
        assertThat(validator.validate(new CsvReader.ReadResult(rows.size(), rows, List.of())).validRows()).hasSize(1201);
        var keys = org.mockito.ArgumentCaptor.forClass(Collection.class);
        verify(targets, times(3)).lexemes(eq(LexemeKind.WORD), keys.capture());
        assertThat(keys.getAllValues()).extracting(Collection::size).containsExactly(500, 500, 201);
        verify(points, times(3)).findByCodeIn(any());
        verify(points, never()).findByCode(any());
        verify(targets, never()).lockTopic(any()); verify(targets, never()).lockLexeme(any(), any());
    }

    @Test
    void FR013_preservesCsvErrorsAndBrokenCellsWithoutReferenceQueriesForEmptyInput() {
        var error = new CsvReader.RowError(4, null, null, "INVALID_VALUE", "Dòng CSV tự soạn bị lỗi");
        assertThat(validator.validate(new CsvReader.ReadResult(1, List.of(), List.of(error))).errors()).containsExactly(error);
        verifyNoInteractions(targets, points);
        assertThat(validate(row(2, "WORD", "水\uFFFD杯", "TV1-801")).errors()).singleElement()
                .satisfies(value -> assertThat(value.code()).isEqualTo("BROKEN_TEXT"));
    }

    private RowValidationResult validate(CsvReader.Row... rows) {
        return validator.validate(new CsvReader.ReadResult(rows.length, List.of(rows), List.of()));
    }

    private static CsvReader.Row row(int line, String type, String key, String kid) {
        return new CsvReader.Row(line, Map.of("target_type", type, "target_key", key, "kid", kid));
    }
}
