package com.cnhsk.learning.content.importer.write;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.knowledge.entity.KnowledgeDomain;
import com.cnhsk.learning.knowledge.entity.KnowledgeExamples;
import com.cnhsk.learning.knowledge.entity.KnowledgePoint;
import com.cnhsk.learning.knowledge.entity.KnowledgeSkillType;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeNodeRowWriterTest {
    private KnowledgePointRepository points;
    private KnowledgeNodeRowWriter writer;

    @BeforeEach
    void setup() {
        points = mock(KnowledgePointRepository.class);
        writer = new KnowledgeNodeRowWriter(points);
    }

    @Test
    void FR004_AT03_createsOneNodeAndDerivesDomainAndLevelFromTheValidatedKid() {
        assertThat(writer.dataType()).isEqualTo(ImportDataType.KNOWLEDGE_NODE);
        assertThat(writer.write(row(values()))).isEqualTo(RowAction.CREATE);
        ArgumentCaptor<KnowledgePoint> saved = ArgumentCaptor.forClass(KnowledgePoint.class);
        verify(points).saveAndFlush(saved.capture());
        KnowledgePoint point = saved.getValue();
        assertThat(point.getCode()).isEqualTo("TV2-001");
        assertThat(point.getDomain()).isEqualTo(KnowledgeDomain.TV);
        assertThat(point.getHskLevel()).isEqualTo((short) 2);
        assertThat(point.getTitle()).isEqualTo("Nút tự soạn");
        assertThat(point.getNameZh()).isEqualTo("测试节点");
        assertThat(point.getCanDo()).isEqualTo("Dùng từ trong câu");
        assertThat(point.getSkillType()).isEqualTo(KnowledgeSkillType.VOCAB);
        assertThat(point.getLexemeId()).isNull();
        assertThat(point.getPrereqIds()).isEmpty();
        assertThat(point.getSupportIds()).isEmpty();
        verify(points).findByCodeForUpdate("TV2-001");
        verify(points, never()).findByCode(any());
    }

    @ParameterizedTest
    @CsvSource({"name_vi,Tên mới", "name_zh,新名称", "can_do,Năng lực mới", "skill_type,READING"})
    void FR004_AT04_changesOnlyImportedFieldsAndPreservesEdgesReadUnderTheLock(String column, String value) {
        KnowledgePoint existing = existing();
        Long[] prereqs = {11L, 12L};
        Long[] supports = {13L};
        existing.setPrereqIds(prereqs);
        existing.setSupportIds(supports);
        when(points.findByCodeForUpdate("TV2-001")).thenReturn(Optional.of(existing));
        Map<String, String> values = values();
        values.put(column, value);
        assertThat(writer.write(row(values))).isEqualTo(RowAction.UPDATE);
        verify(points).saveAndFlush(existing);
        assertThat(existing.getPrereqIds()).isSameAs(prereqs).containsExactly(11L, 12L);
        assertThat(existing.getSupportIds()).isSameAs(supports).containsExactly(13L);
        assertThat(existing.getCode()).isEqualTo("TV2-001");
        assertThat(existing.getDomain()).isEqualTo(KnowledgeDomain.TV);
        assertThat(existing.getHskLevel()).isEqualTo((short) 2);
    }

    @Test
    void FR004_unchangedRowDoesNotSaveOrTouchTimestamps() {
        KnowledgePoint existing = existing();
        when(points.findByCodeForUpdate("TV2-001")).thenReturn(Optional.of(existing));
        assertThat(writer.write(row(values()))).isEqualTo(RowAction.UNCHANGED);
        verify(points, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void FR004_optionalBlankOrMissingCellsKeepTheExistingValues(boolean omit) {
        KnowledgePoint existing = existing();
        when(points.findByCodeForUpdate("TV2-001")).thenReturn(Optional.of(existing));
        Map<String, String> values = values();
        for (String column : new String[]{"can_do", "skill_type"}) {
            if (omit) values.remove(column);
            else values.put(column, " ");
        }
        assertThat(writer.write(row(values))).isEqualTo(RowAction.UNCHANGED);
        assertThat(existing.getCanDo()).isEqualTo("Dùng từ trong câu");
        assertThat(existing.getSkillType()).isEqualTo(KnowledgeSkillType.VOCAB);
        verify(points, never()).saveAndFlush(any());
    }

    @Test
    void FR004_creatingWithOnlyRequiredColumnsLeavesOptionalFieldsNull() {
        Map<String, String> values = values();
        values.remove("can_do");
        values.remove("skill_type");
        writer.write(row(values));
        ArgumentCaptor<KnowledgePoint> saved = ArgumentCaptor.forClass(KnowledgePoint.class);
        verify(points).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getCanDo()).isNull();
        assertThat(saved.getValue().getSkillType()).isNull();
    }

    @Test
    void writesSkillsDetailAndExamplesFromTheNewColumns() {
        Map<String, String> values = values();
        values.put("skills", "READING|LISTENING");
        values.put("cluster", "Từ vựng cấp 2 · đi lại");
        values.put("pattern", "S + 把 + O + V + kết quả");
        values.put("teaching_note", "Nhấn mạnh phải có kết quả");
        values.put("source", "Đại cương HSK 3.0, ngữ pháp cấp 3");
        values.put("examples_ok", "[{\"cn\":\"我是学生。\",\"pinyin\":\"Wǒ shì xuéshēng.\",\"vi\":\"Tôi là học sinh.\"}]");
        values.put("examples_err", "[{\"cn\":\"我是很高兴。\",\"error_code\":\"E-NP-01\"}]");
        writer.write(row(values));
        ArgumentCaptor<KnowledgePoint> saved = ArgumentCaptor.forClass(KnowledgePoint.class);
        verify(points).saveAndFlush(saved.capture());
        KnowledgePoint point = saved.getValue();
        assertThat(point.getSkills()).containsExactly("READING", "LISTENING");
        assertThat(point.getCluster()).isEqualTo("Từ vựng cấp 2 · đi lại");
        assertThat(point.getPattern()).isEqualTo("S + 把 + O + V + kết quả");
        assertThat(point.getTeachingNote()).isEqualTo("Nhấn mạnh phải có kết quả");
        assertThat(point.getSource()).isEqualTo("Đại cương HSK 3.0, ngữ pháp cấp 3");
        assertThat(point.getExamples().ok()).singleElement()
                .isEqualTo(new KnowledgeExamples.Correct("我是学生。", "Wǒ shì xuéshēng.", "Tôi là học sinh."));
        assertThat(point.getExamples().err()).singleElement()
                .isEqualTo(new KnowledgeExamples.Wrong("我是很高兴。", "E-NP-01", null));
    }

    @Test
    void omittedNewColumnsKeepExistingSkillsAndExamples() {
        KnowledgePoint existing = existing();
        existing.setSkills(new String[]{"READING"});
        existing.setExamples(new KnowledgeExamples(List.of(new KnowledgeExamples.Correct("我。", null, "Tôi.")), List.of()));
        when(points.findByCodeForUpdate("TV2-001")).thenReturn(Optional.of(existing));
        assertThat(writer.write(row(values()))).isEqualTo(RowAction.UNCHANGED);
        assertThat(existing.getSkills()).containsExactly("READING");
        assertThat(existing.getExamples().ok()).hasSize(1);
    }

    @Test
    void sameSkillsInAnotherOrderAreNotAChange() {
        KnowledgePoint existing = existing();
        existing.setSkills(new String[]{"WRITING", "READING"});
        when(points.findByCodeForUpdate("TV2-001")).thenReturn(Optional.of(existing));
        Map<String, String> values = values();
        values.put("skills", "READING|WRITING");
        assertThat(writer.write(row(values))).isEqualTo(RowAction.UNCHANGED);
    }

    @Test
    void FR004_databaseFailurePropagatesToThePerRowTransaction() {
        when(points.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("synthetic constraint failure"));
        assertThatThrownBy(() -> writer.write(row(values()))).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static KnowledgePoint existing() {
        KnowledgePoint point = new KnowledgePoint("TV2-001", "Nút tự soạn", KnowledgeDomain.TV, "测试节点", (short) 2);
        point.setCanDo("Dùng từ trong câu");
        point.setSkillType(KnowledgeSkillType.VOCAB);
        return point;
    }

    private static Map<String, String> values() {
        return new LinkedHashMap<>(Map.of("kid", "TV2-001", "name_vi", "Nút tự soạn", "name_zh", "测试节点",
                "can_do", "Dùng từ trong câu", "skill_type", "VOCAB"));
    }

    private static ValidatedRow row(Map<String, String> values) {
        return new ValidatedRow(2, values, null, null);
    }
}
