package com.cnhsk.learning.content.importer.validate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.content.importer.CsvReader;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.knowledge.entity.KnowledgeSkillType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeNodeRowValidatorTest {
    private final KnowledgeNodeRowValidator validator = new KnowledgeNodeRowValidator();

    @ParameterizedTest
    @ValueSource(strings = {"NA1-001", "CH2-002", "TV2-001", "NP3-004", "NL7-005", "VB1-006"})
    void FR003_AT03_allDomainsAndTheApprovedKidLevelRangeAreAccepted(String kid) {
        RowValidationResult result = validate(Map.of("kid", kid));
        assertThat(validator.dataType()).isEqualTo(ImportDataType.KNOWLEDGE_NODE);
        assertThat(result.errors()).isEmpty();
        assertThat(result.validRows()).singleElement().satisfies(row -> {
            assertThat(row.values()).containsEntry("kid", kid);
            assertThat(row.line()).isEqualTo(2);
            assertThat(row.pinyinNorm()).isNull();
            assertThat(row.meaningNorm()).isNull();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"NP10-001", "XX1-001", "NP3-04", "NP0-001", "NP8-001", "NP3-0001", "NP3-汉字"})
    void FR003_AT03_malformedKidsHaveTheSpecificRowError(String kid) {
        assertError(validate(Map.of("kid", kid)), "kid", "INVALID_KID");
    }

    @Test
    void FR003_AT03_E10_normalizesCaseWhitespaceAndUnicodeBeforeChecking() {
        RowValidationResult result = validate(Map.of("kid", "  np3-004  ", "name_vi", "  Nga\u0303 pha\u0301p  ",
                "name_zh", "  语法  ", "skill_type", " GRAMMAR "));
        assertThat(result.errors()).isEmpty();
        assertThat(result.validRows().get(0).values()).containsEntry("kid", "NP3-004")
                .containsEntry("name_vi", "Ngã pháp").containsEntry("name_zh", "语法").containsEntry("skill_type", "GRAMMAR");
    }

    @ParameterizedTest
    @ValueSource(strings = {"kid", "name_vi", "name_zh"})
    void FR003_requiredCellsCannotBeBlank(String column) {
        assertError(validate(Map.of(column, "  ")), column, "REQUIRED_MISSING");
    }

    @ParameterizedTest
    @CsvSource({"name_vi,200", "name_zh,200", "can_do,1000"})
    void FR003_lengthsAcceptTheBoundaryAndRejectOneMoreCodePoint(String column, int limit) {
        assertThat(validate(Map.of(column, "文".repeat(limit))).errors()).isEmpty();
        assertError(validate(Map.of(column, "文".repeat(limit + 1))), column, "TOO_LONG");
    }

    @Test
    void FR003_AT03_chineseNameRequiresAtLeastOneApprovedCjkCharacter() {
        assertError(validate(Map.of("name_zh", "abc")), "name_zh", "NOT_CJK");
        assertThat(validate(Map.of("name_zh", "abc字123")).errors()).isEmpty();
        assertThat(validate(Map.of("name_zh", "㐀")).errors()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"名称?", "名\uFFFD称"})
    void FR003_brokenChineseIsReportedBeforeOtherColumnChecks(String value) {
        assertError(validate(Map.of("name_zh", value)), "name_zh", "BROKEN_TEXT");
    }

    @ParameterizedTest
    @EnumSource(KnowledgeSkillType.class)
    void FR003_acceptsExactlyTheExistingSkillEnum(KnowledgeSkillType skill) {
        assertThat(validate(Map.of("skill_type", skill.name())).errors()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SPEAKING", "vocab", "OTHER"})
    void FR003_unknownSkillIsInvalidValue(String skill) {
        assertError(validate(Map.of("skill_type", skill)), "skill_type", "INVALID_VALUE");
    }

    @Test
    void FR003_optionalCellsCanBeOmittedWithoutInventingValues() {
        Map<String, String> values = valid();
        values.remove("can_do");
        values.remove("skill_type");
        RowValidationResult result = validateRows(new CsvReader.Row(2, values));
        assertThat(result.errors()).isEmpty();
        assertThat(result.validRows().get(0).values()).doesNotContainKeys("can_do", "skill_type");
    }

    @Test
    void FR003_duplicateDetectionUsesCanonicalKidAndPhysicalLineOrder() {
        Map<String, String> lower = valid();
        lower.put("kid", "np3-004");
        RowValidationResult result = validateRows(new CsvReader.Row(7, lower), new CsvReader.Row(3, valid()));
        assertThat(result.validRows()).singleElement().satisfies(row -> assertThat(row.line()).isEqualTo(3));
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.line()).isEqualTo(7);
            assertThat(error.code()).isEqualTo("DUPLICATE_IN_FILE");
            assertThat(error.message()).contains("3");
        });
    }

    @Test
    void FR003_invalidFirstOccurrenceDoesNotReserveTheNaturalKey() {
        Map<String, String> invalid = valid();
        invalid.put("name_zh", "abc");
        RowValidationResult result = validateRows(new CsvReader.Row(3, invalid), new CsvReader.Row(7, valid()));
        assertThat(result.errors()).singleElement().satisfies(error -> assertThat(error.code()).isEqualTo("NOT_CJK"));
        assertThat(result.validRows()).singleElement().satisfies(row -> assertThat(row.line()).isEqualTo(7));
    }

    @Test
    void FR003_multipleColumnErrorsRetainTemplateOrderAndTruncateTheReportedValue() {
        RowValidationResult result = validate(Map.of("kid", "x".repeat(101), "name_vi", "", "name_zh", "abc", "skill_type", "OTHER"));
        assertThat(result.errors()).extracting(CsvReader.RowError::column).containsExactly("kid", "name_vi", "name_zh", "skill_type");
        assertThat(result.errors().get(0).value()).hasSize(100);
    }

    private RowValidationResult validate(Map<String, String> replacements) {
        Map<String, String> values = valid();
        values.putAll(replacements);
        return validateRows(new CsvReader.Row(2, values));
    }

    private RowValidationResult validateRows(CsvReader.Row... rows) {
        return validator.validate(new CsvReader.ReadResult(rows.length, List.of(rows), List.of()));
    }

    private static Map<String, String> valid() {
        return new LinkedHashMap<>(Map.of("kid", "NP3-004", "name_vi", "Cấu trúc tự soạn", "name_zh", "句式",
                "can_do", "Đặt câu ngắn", "skill_type", "GRAMMAR"));
    }

    private static void assertError(RowValidationResult result, String column, String code) {
        assertThat(result.validRows()).isEmpty();
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.column()).isEqualTo(column);
            assertThat(error.code()).isEqualTo(code);
        });
    }
}
