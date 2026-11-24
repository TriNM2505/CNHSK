package com.cnhsk.learning.content.importer.validate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.content.importer.CsvReader;
import com.cnhsk.learning.knowledge.entity.KnowledgeDomain;
import com.cnhsk.learning.knowledge.entity.KnowledgePoint;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ErrorCodeAndTaskRowValidatorTest {
    private KnowledgePointRepository points;
    private ErrorCodeRowValidator errorCodes;
    private LearningTaskRowValidator tasks;

    @BeforeEach
    void setup() {
        points = mock(KnowledgePointRepository.class);
        when(points.findByCodeIn(any())).thenReturn(List.of(node("NP3-004", KnowledgeDomain.NP),
                node("NL1-001", KnowledgeDomain.NL), node("NA1-006", KnowledgeDomain.NA), node("TV1-001", KnowledgeDomain.TV),
                node("NP1-001", KnowledgeDomain.NP), node("CH1-003", KnowledgeDomain.CH)));
        errorCodes = new ErrorCodeRowValidator(points);
        tasks = new LearningTaskRowValidator(points);
    }

    @Test
    void errorCodeIsNormalisedAndAccepted() {
        RowValidationResult result = errorCodes.validate(read(Map.of("code", " e-np-07 ", "kid", "np3-004",
                "severity", "2", "name_vi", "Câu 把 thiếu thành phần sau động từ")));
        assertThat(result.errors()).isEmpty();
        assertThat(result.validRows().get(0).values()).containsEntry("code", "E-NP-07").containsEntry("kid", "NP3-004");
    }

    @Test
    void errorCodeMayPointAtANodeOfAnotherDomainAsAppendixDDoes() {
        // E-TV-01 (wrong classifier) points at NP1-010 in appendix D.
        assertThat(errorCodes.validate(read(Map.of("code", "E-TV-01", "kid", "NP1-001", "severity", "1",
                "name_vi", "Sai lượng từ"))).errors()).isEmpty();
    }

    @Test
    void communicativeCodesHaveNoFixedNodeAndOthersNeedOne() {
        assertThat(errorCodes.validate(read(Map.of("code", "E-NL-01", "severity", "3", "name_vi", "Lạc đề")))
                .errors()).isEmpty();
        assertThat(errorCodes.validate(read(Map.of("code", "E-NL-01", "kid", "NL1-001", "severity", "3", "name_vi", "Lạc đề")))
                .errors()).anySatisfy(e -> assertThat(e.code()).isEqualTo("INVALID_VALUE"));
        assertThat(errorCodes.validate(read(Map.of("code", "E-NP-01", "severity", "3", "name_vi", "Sai trật tự")))
                .errors()).anySatisfy(e -> assertThat(e.code()).isEqualTo("REQUIRED_MISSING"));
    }

    @ParameterizedTest
    @CsvSource({"E-NP-7,NP3-004,2,code,INVALID_VALUE", "E-XX-07,NP3-004,2,code,INVALID_VALUE",
            "E-NP-07,NP9-999,2,kid,REFERENCE_NOT_FOUND", "E-NP-07,NP3-004,4,severity,INVALID_VALUE"})
    void errorCodeRejectsBadCodesMissingKidsAndBadSeverity(String code, String kid, String severity, String column, String error) {
        RowValidationResult result = errorCodes.validate(read(Map.of("code", code, "kid", kid, "severity", severity,
                "name_vi", "Lỗi")));
        assertThat(result.validRows()).isEmpty();
        assertThat(result.errors()).anySatisfy(e -> {
            assertThat(e.column()).isEqualTo(column);
            assertThat(e.code()).isEqualTo(error);
        });
    }

    @Test
    void speakingTaskTargetsAreCanonicalised() {
        RowValidationResult result = tasks.validate(read(task("SPEAKING", "c4:np1-001 | C2:NA1-006|C3:TV1-001")));
        assertThat(result.errors()).isEmpty();
        assertThat(result.validRows().get(0).values()).containsEntry("targets", "C2:NA1-006|C3:TV1-001|C4:NP1-001");
    }

    @Test
    void writingTaskUsesTheWritingRubricDomains() {
        assertThat(tasks.validate(read(task("WRITING", "C2:NP1-001|C3:TV1-001|C4:CH1-003"))).errors()).isEmpty();
        assertThat(tasks.validate(read(task("WRITING", "C2:NA1-006"))).errors())
                .anySatisfy(e -> assertThat(e.code()).isEqualTo("DOMAIN_MISMATCH"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "targets;C1:NL1-001;INVALID_VALUE",
            "targets;C2:NA1-006|C2:NA1-006;INVALID_VALUE",
            "targets;C2;INVALID_VALUE",
            "targets;C2:NA2-099;REFERENCE_NOT_FOUND",
            "nl_kid;NP1-001;DOMAIN_MISMATCH",
            "kind;READING;INVALID_VALUE",
            "max_seconds;5;INVALID_VALUE",
            "is_active;yes;INVALID_VALUE"})
    void taskRejectsBrokenCells(String column, String value, String error) {
        Map<String, String> values = task("SPEAKING", "C2:NA1-006");
        values.put(column, value);
        RowValidationResult result = tasks.validate(read(values));
        assertThat(result.validRows()).isEmpty();
        assertThat(result.errors()).anySatisfy(e -> {
            assertThat(e.column()).isEqualTo(column);
            assertThat(e.code()).isEqualTo(error);
        });
    }

    private static Map<String, String> task(String kind, String targets) {
        return new LinkedHashMap<>(Map.of("code", "NOI_NL1_001_A", "kind", kind, "hsk_level", "1", "nl_kid", "NL1-001",
                "title_vi", "Tự giới thiệu", "prompt_vi", "Giới thiệu bản thân.", "targets", targets, "max_seconds", "60"));
    }

    private static CsvReader.ReadResult read(Map<String, String> values) {
        return new CsvReader.ReadResult(1, List.of(new CsvReader.Row(2, new LinkedHashMap<>(values))), List.of());
    }

    private static KnowledgePoint node(String kid, KnowledgeDomain domain) {
        return new KnowledgePoint(kid, kid, domain, "节点", Short.parseShort(kid.substring(2, 3)));
    }
}
