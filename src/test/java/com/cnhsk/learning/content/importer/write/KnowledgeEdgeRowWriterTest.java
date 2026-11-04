package com.cnhsk.learning.content.importer.write;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.knowledge.entity.KnowledgeDomain;
import com.cnhsk.learning.knowledge.entity.KnowledgePoint;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KnowledgeEdgeRowWriterTest {
    private KnowledgePointRepository points;
    private NamedParameterJdbcTemplate jdbc;
    private KnowledgeEdgeRowWriter writer;
    private KnowledgePoint from;
    private KnowledgePoint to;

    @BeforeEach
    void setup() {
        points = mock(KnowledgePointRepository.class);
        jdbc = mock(NamedParameterJdbcTemplate.class);
        writer = new KnowledgeEdgeRowWriter(points, jdbc);
        from = node(1); to = node(2);
        when(points.findByCodeForUpdate(to.getCode())).thenReturn(Optional.of(to));
        when(points.findAll()).thenReturn(List.of(from, to));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PREREQ", "SUPPORT"})
    void AT08_locksGraphThenTargetThenReadsBeforeAppendingOnlyTheRequestedArray(String type) {
        Long[] oldPrereqs = {7L};
        Long[] oldSupports = {8L};
        to.setPrereqIds(oldPrereqs); to.setSupportIds(oldSupports);
        assertThat(writer.dataType()).isEqualTo(ImportDataType.KNOWLEDGE_EDGE);
        assertThat(writer.write(row(type))).isEqualTo(RowAction.CREATE);
        var order = inOrder(jdbc, points);
        order.verify(jdbc).query(eq("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))"),
                eq(Map.of("key", "knowledge:edge-graph")), any(ResultSetExtractor.class));
        order.verify(points).findByCodeForUpdate(to.getCode());
        order.verify(points).findAll();
        order.verify(points).saveAndFlush(to);
        if (type.equals("PREREQ")) {
            assertThat(to.getPrereqIds()).containsExactly(7L, 1L);
            assertThat(to.getSupportIds()).isSameAs(oldSupports);
        } else {
            assertThat(to.getSupportIds()).containsExactly(8L, 1L);
            assertThat(to.getPrereqIds()).isSameAs(oldPrereqs);
        }
        assertThat(to.getTitle()).isEqualTo("Nút tự soạn");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PREREQ", "SUPPORT"})
    void AT08_reimportDoesNotSaveOrTouchArrays(String type) {
        if (type.equals("PREREQ")) to.setPrereqIds(new Long[]{1L});
        else to.setSupportIds(new Long[]{1L});
        assertThat(writer.write(row(type))).isEqualTo(RowAction.UNCHANGED);
        verify(points, never()).saveAndFlush(any());
    }

    @Test
    void AT07_rechecksCurrentDatabasePathsAndRejectsWithoutSaving() {
        from.setPrereqIds(new Long[]{2L});
        assertRejection("PREREQ", "EDGE_CYCLE", "to_kid");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PREREQ", "SUPPORT"})
    void AT08_conflictingTypeNeverChangesAnArray(String type) {
        if (type.equals("PREREQ")) to.setSupportIds(new Long[]{1L});
        else to.setPrereqIds(new Long[]{1L});
        assertRejection(type, "EDGE_TYPE_CONFLICT", "edge_type");
    }

    @Test
    void missingTargetOrSourceIsAReusableTypedRowRejection() {
        when(points.findByCodeForUpdate(to.getCode())).thenReturn(Optional.empty());
        assertRejection("PREREQ", "REFERENCE_NOT_FOUND", "to_kid");
        verify(points, never()).findAll();
        when(points.findByCodeForUpdate(to.getCode())).thenReturn(Optional.of(to));
        when(points.findAll()).thenReturn(List.of(to));
        assertRejection("PREREQ", "REFERENCE_NOT_FOUND", "from_kid");
    }

    private void assertRejection(String type, String code, String field) {
        assertThatThrownBy(() -> writer.write(row(type))).isInstanceOfSatisfying(RowWriteRejectedException.class, error -> {
            assertThat(error.code()).isEqualTo(code);
            assertThat(error.field()).isEqualTo(field);
            assertThat(error.getMessage()).isNotBlank();
        });
        verify(points, never()).saveAndFlush(any());
    }
    private static ValidatedRow row(String type) {
        return new ValidatedRow(2, Map.of("from_kid", "TV1-001", "to_kid", "TV1-002", "edge_type", type), null, null);
    }
    private static KnowledgePoint node(long id) {
        KnowledgePoint point = new KnowledgePoint("TV1-%03d".formatted(id), "Nút tự soạn", KnowledgeDomain.TV, "示例", (short) 1);
        ReflectionTestUtils.setField(point, "id", id);
        return point;
    }
}
