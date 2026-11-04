package com.cnhsk.learning.content.importer.write;

import java.util.Map;
import java.util.Optional;

import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
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
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class NodeLinkRowWriterTest {
    private NodeLinkRepository targets;
    private KnowledgePointRepository points;
    private NodeLinkRowWriter writer;

    @BeforeEach
    void setup() {
        targets = mock(NodeLinkRepository.class); points = mock(KnowledgePointRepository.class);
        writer = new NodeLinkRowWriter(targets, points);
        when(targets.lockTopic(any())).thenReturn(Optional.of(new Target(11, "示例", null, null)));
        when(targets.lockLexeme(any(), any())).thenReturn(Optional.of(new Target(12, "示例", null, null)));
        when(points.findByCode(any())).thenAnswer(call -> {
            String kid = call.getArgument(0);
            var point = new KnowledgePoint(kid, "Nút tự soạn", KnowledgeDomain.valueOf(kid.substring(0, 2)), "示例", (short) 1);
            ReflectionTestUtils.setField(point, "id", 21L);
            return Optional.of(point);
        });
    }

    @ParameterizedTest
    @CsvSource({"TOPIC,TV1-801", "WORD,TV1-801", "CHAR,CH1-002", "GRAMMAR,NP1-803"})
    void FR014_AT14_locksTheExistingTargetThenLinksItWithoutCreatingContent(String type, String kid) {
        assertThat(writer.dataType()).isEqualTo(ImportDataType.NODE_LINK);
        assertThat(writer.write(row(type, kid))).isEqualTo(RowAction.UPDATE);
        var order = inOrder(targets, points);
        if (type.equals("TOPIC")) order.verify(targets).lockTopic("示例");
        else order.verify(targets).lockLexeme(LexemeKind.valueOf(type), "示例");
        order.verify(points).findByCode(kid);
        if (type.equals("TOPIC")) order.verify(targets).updateTopic(11, 21);
        else order.verify(targets).updateLexeme(12, 21);
        verify(points, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOPIC", "WORD"})
    void FR014_AT14_sameLinkReadUnderTheLockIsUnchangedWithoutAnyUpdate(String type) {
        when(targets.lockTopic(any())).thenReturn(Optional.of(new Target(11, "示例", 21L, null)));
        when(targets.lockLexeme(any(), any())).thenReturn(Optional.of(new Target(12, "示例", 21L, null)));
        assertThat(writer.write(row(type, "TV1-801"))).isEqualTo(RowAction.UNCHANGED);
        verify(targets, never()).updateTopic(anyLong(), anyLong()); verify(targets, never()).updateLexeme(anyLong(), anyLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOPIC", "WORD"})
    void FR014_AT14_differentExistingLinkIsOverwritten(String type) {
        when(targets.lockTopic(any())).thenReturn(Optional.of(new Target(11, "示例", 99L, null)));
        when(targets.lockLexeme(any(), any())).thenReturn(Optional.of(new Target(12, "示例", 99L, null)));
        assertThat(writer.write(row(type, "TV1-801"))).isEqualTo(RowAction.UPDATE);
    }

    @Test
    void FR014_missingTargetRejectsBeforeNodeLookupEvenIfItsDomainWouldMismatch() {
        when(targets.lockTopic(any())).thenReturn(Optional.empty());
        assertRejected("TOPIC", "CH1-002", "REFERENCE_NOT_FOUND", "target_key");
        verifyNoInteractions(points);
    }

    @Test
    void FR014_missingNodeIsAReferenceErrorWithoutWriting() {
        doReturn(Optional.empty()).when(points).findByCode(any());
        assertRejected("WORD", "TV1-999", "REFERENCE_NOT_FOUND", "kid");
        verify(targets, never()).updateLexeme(anyLong(), anyLong());
    }

    @ParameterizedTest
    @CsvSource({"TOPIC,CH1-002", "WORD,NP1-803", "CHAR,TV1-801", "GRAMMAR,TV1-801"})
    void FR014_rechecksTheRequiredDomainBeforeWriting(String type, String kid) {
        assertRejected(type, kid, "DOMAIN_MISMATCH", "kid");
        verify(targets, never()).updateTopic(anyLong(), anyLong()); verify(targets, never()).updateLexeme(anyLong(), anyLong());
    }

    @Test
    void FR014_databaseFailurePropagatesToTheCallerTransaction() {
        doThrow(new IllegalStateException("synthetic failure")).when(targets).updateTopic(11, 21);
        assertThatThrownBy(() -> writer.write(row("TOPIC", "TV1-801"))).isInstanceOf(IllegalStateException.class);
    }

    private void assertRejected(String type, String kid, String code, String field) {
        assertThatThrownBy(() -> writer.write(row(type, kid))).isInstanceOfSatisfying(RowWriteRejectedException.class, error -> {
            assertThat(error.code()).isEqualTo(code); assertThat(error.field()).isEqualTo(field);
        });
    }

    private static ValidatedRow row(String type, String kid) {
        return new ValidatedRow(2, Map.of("target_type", type, "target_key", "示例", "kid", kid), null, null);
    }
}
