package com.cnhsk.learning.content.importer.write;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.validate.QuestionRowValidatorTest;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.knowledge.entity.KnowledgeLabelRole;
import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.entity.QuestionKnowledgePoint;
import com.cnhsk.learning.knowledge.entity.QuestionOption;
import com.cnhsk.learning.knowledge.entity.QuestionSource;
import com.cnhsk.learning.knowledge.entity.QuestionStatus;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import com.cnhsk.learning.knowledge.repository.QuestionKnowledgePointRepository;
import com.cnhsk.learning.knowledge.repository.QuestionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QuestionRowWriterTest {
    private QuestionRepository questions;
    private QuestionKnowledgePointRepository labels;
    private KnowledgePointRepository points;
    private EntityManager entities;
    private QuestionRowWriter writer;

    @BeforeEach
    void setup() {
        questions = mock(QuestionRepository.class); labels = mock(QuestionKnowledgePointRepository.class); points = mock(KnowledgePointRepository.class);
        entities = mock(EntityManager.class);
        writer = new QuestionRowWriter(questions, labels, points, entities);
        when(points.findByCodeIn(any())).thenReturn(List.of(QuestionRowValidatorTest.node("TV1-001", 1),
                QuestionRowValidatorTest.node("TV1-002", 2), QuestionRowValidatorTest.node("NP2-003", 3)));
        when(questions.save(any())).thenAnswer(call -> {
            Question question = call.getArgument(0);
            if (question.getId() == null) ReflectionTestUtils.setField(question, "id", 50L);
            return question;
        });
    }

    @ParameterizedTest
    @EnumSource(value = QuestionType.class, names = "ESSAY", mode = EnumSource.Mode.EXCLUDE)
    void AT09_AT11_newQuestionsHaveCanonicalOptionsAndExactlyOnePrimary(QuestionType type) {
        var cells = QuestionRowValidatorTest.values(type); cells.put("kid_secondary", "TV1-002|NP2-003");
        assertThat(writer.dataType()).isEqualTo(com.cnhsk.learning.content.importer.template.ImportDataType.QUESTION);
        assertThat(writer.write(row(cells))).isEqualTo(RowAction.CREATE);
        verify(questions).save(org.mockito.ArgumentMatchers.argThat(question -> {
            assertThat(question.getSource()).isEqualTo(QuestionSource.IMPORT);
            assertThat(question.getStatus()).isEqualTo(QuestionStatus.APPROVED);
            assertThat(question.isReusable()).isTrue(); assertThat(question.getDifficulty()).isEqualTo((short) 2);
            if (type == QuestionType.FILL_BLANK) { assertThat(question.getOptions()).isEmpty(); assertThat(question.getCorrectKey()).isEqualTo("在"); }
            else if (type == QuestionType.SENTENCE_ORDER) {
                assertThat(question.getCorrectKey()).isEqualTo("CAB"); assertThat(question.getOptions()).noneMatch(QuestionOption::isCorrect);
            } else {
                assertThat(question.getCorrectKey()).isNull(); assertThat(question.getOptions()).filteredOn(QuestionOption::isCorrect).hasSize(1);
            }
            return true;
        }));
        var persisted = ArgumentCaptor.forClass(QuestionKnowledgePoint.class);
        verify(entities, times(3)).persist(persisted.capture());
        assertThat(persisted.getAllValues()).allSatisfy(label -> assertThat(label.getQuestionId()).isEqualTo(50L));
        assertThat(persisted.getAllValues()).filteredOn(label -> label.getRole() == KnowledgeLabelRole.PRIMARY).singleElement()
                .extracting(QuestionKnowledgePoint::getKnowledgePointId).isEqualTo(1L);
        verify(labels, never()).saveAll(any()); verify(labels, never()).flush();
        verify(questions, never()).saveAndFlush(any()); verify(questions, never()).usedQuestionIds(any());
        verify(questions).findByCodeForUpdate("Q_IMPORT"); verify(questions, never()).findByCode(any());
    }

    @ParameterizedTest
    @EnumSource(QuestionStatus.class)
    void AT12_updatePreservesEveryStatusSourceReuseAndReviewMetadata(QuestionStatus status) {
        Question previous = existing(); previous.setStatus(status); previous.setSource(QuestionSource.AI); previous.setReusable(false);
        previous.setReviewedBy(7L); previous.setReviewReason("Ghi chú duyệt cũ");
        var cells = QuestionRowValidatorTest.values(QuestionType.MULTIPLE_CHOICE); cells.put("explanation", "Lời giải mới");
        assertThat(writer.write(row(cells))).isEqualTo(RowAction.UPDATE);
        assertThat(previous.getStatus()).isEqualTo(status); assertThat(previous.getSource()).isEqualTo(QuestionSource.AI);
        assertThat(previous.isReusable()).isFalse(); assertThat(previous.getReviewedBy()).isEqualTo(7L);
        assertThat(previous.getReviewReason()).isEqualTo("Ghi chú duyệt cũ");
    }

    @Test
    void AT09_falseMapsToTheFixedBOptionRatherThanAFreeTextAnswer() {
        var cells = QuestionRowValidatorTest.values(QuestionType.TRUE_FALSE); cells.put("correct", "FALSE");
        assertThat(writer.write(row(cells))).isEqualTo(RowAction.CREATE);
        verify(questions).save(org.mockito.ArgumentMatchers.argThat(question -> {
            assertThat(question.getOptions()).filteredOn(QuestionOption::isCorrect).singleElement().extracting(QuestionOption::key).isEqualTo("B");
            assertThat(question.getCorrectKey()).isNull(); return true;
        }));
    }

    @ParameterizedTest
    @CsvSource({"correct,B", "option_c,他", "qtype,SENTENCE_MATCH"})
    void AT12_usedQuestionsRejectChangesToAnswerCountOrType(String field, String value) {
        existing(); when(questions.usedQuestionIds(any())).thenReturn(Set.of(50L));
        var cells = QuestionRowValidatorTest.values(QuestionType.MULTIPLE_CHOICE); cells.put(field, value);
        assertThatThrownBy(() -> writer.write(row(cells))).isInstanceOfSatisfying(RowWriteRejectedException.class,
                error -> assertThat(error.code()).isEqualTo("GRADING_CHANGE_NOT_ALLOWED"));
        verify(questions, never()).save(any()); verify(labels, never()).deleteAll(any());
        verifyNoInteractions(entities);
    }

    @ParameterizedTest
    @CsvSource({"stem,Nội dung mới", "explanation,Lời giải mới", "option_b,他", "hsk_level,3", "difficulty,3", "audio_url,https://example.test/audio.mp3"})
    void AT12_usedQuestionsStillAllowEditorialChanges(String field, String value) {
        existing(); when(questions.usedQuestionIds(any())).thenReturn(Set.of(50L));
        var cells = QuestionRowValidatorTest.values(QuestionType.MULTIPLE_CHOICE); cells.put(field, value);
        assertThat(writer.write(row(cells))).isEqualTo(RowAction.UPDATE);
        verify(questions, never()).usedQuestionIds(any()); verify(labels, never()).deleteAll(any());
        verifyNoInteractions(entities);
    }

    @Test
    void AT12_deletionIsFlushedBeforeReinsertingSwappedPrimaryAndSecondary() {
        existing();
        List<QuestionKnowledgePoint> old = List.of(new QuestionKnowledgePoint(50L, 1L, KnowledgeLabelRole.PRIMARY),
                new QuestionKnowledgePoint(50L, 2L, KnowledgeLabelRole.SECONDARY));
        when(labels.findByQuestionIdIn(any())).thenReturn(old);
        var cells = QuestionRowValidatorTest.values(QuestionType.MULTIPLE_CHOICE);
        cells.put("kid_primary", "TV1-002"); cells.put("kid_secondary", "TV1-001");
        assertThat(writer.write(row(cells))).isEqualTo(RowAction.UPDATE);
        var order = inOrder(labels, entities);
        order.verify(labels).findByQuestionIdIn(List.of(50L));
        order.verify(labels).deleteAll(old); order.verify(labels).flush();
        order.verify(entities, times(2)).persist(any(QuestionKnowledgePoint.class));
        verify(labels, times(1)).flush(); verify(labels, never()).saveAll(any());
    }

    @Test
    void AT12_blankStructureClearsOldOptionsSecondaryKidsAndDistractorsButKeepsScalars() {
        Question previous = existing(); previous.setDifficulty((short) 3); previous.setAudioUrl("https://example.test/old.mp3");
        previous.setImageUrl("https://example.test/old.png"); previous.setOptions(List.of(new QuestionOption("A", "我", true, null, null),
                new QuestionOption("B", "你", false, "TV1-002", "Ghi chú cũ"), new QuestionOption("C", "他", false, null, null)));
        when(labels.findByQuestionIdIn(any())).thenReturn(List.of(new QuestionKnowledgePoint(50L, 1L, KnowledgeLabelRole.PRIMARY),
                new QuestionKnowledgePoint(50L, 2L, KnowledgeLabelRole.SECONDARY)));
        var cells = QuestionRowValidatorTest.values(QuestionType.MULTIPLE_CHOICE);
        cells.put("difficulty", ""); cells.put("audio_url", ""); cells.put("image_url", ""); cells.put("kid_secondary", ""); cells.put("distractor_errors", "");
        assertThat(writer.write(row(cells))).isEqualTo(RowAction.UPDATE);
        assertThat(previous.getOptions()).hasSize(2).allSatisfy(option -> { assertThat(option.errorKid()).isNull(); assertThat(option.errorNote()).isNull(); });
        assertThat(previous.getDifficulty()).isEqualTo((short) 3); assertThat(previous.getAudioUrl()).endsWith("old.mp3"); assertThat(previous.getImageUrl()).endsWith("old.png");
    }

    @Test
    void AT12_unchangedRowsDoNotSaveOrRewriteLabelsAndNewMissingReferencesAreRejected() {
        existing();
        assertThat(writer.write(row(QuestionRowValidatorTest.values(QuestionType.MULTIPLE_CHOICE)))).isEqualTo(RowAction.UNCHANGED);
        verify(questions, never()).save(any()); verify(questions, never()).usedQuestionIds(any());
        verify(labels, never()).deleteAll(any()); verifyNoInteractions(entities);
        when(points.findByCodeIn(any())).thenReturn(List.of());
        assertThatThrownBy(() -> writer.write(row(QuestionRowValidatorTest.values(QuestionType.MULTIPLE_CHOICE))))
                .isInstanceOfSatisfying(RowWriteRejectedException.class, error -> {
                    assertThat(error.code()).isEqualTo("REFERENCE_NOT_FOUND"); assertThat(error.field()).isEqualTo("kid_primary");
                });
    }

    @Test
    void AT12_staleInvalidInputIsRejectedBeforeSaving() {
        var cells = QuestionRowValidatorTest.values(QuestionType.MULTIPLE_CHOICE); cells.put("distractor_errors", "A:TV1-001");
        assertThatThrownBy(() -> writer.write(row(cells))).isInstanceOfSatisfying(RowWriteRejectedException.class,
                error -> assertThat(error.code()).isEqualTo("INVALID_VALUE"));
        verify(questions, never()).save(any()); verifyNoInteractions(entities);
    }

    private Question existing() {
        Question question = QuestionRowValidatorTest.existing("Q_IMPORT", 50);
        when(questions.findByCodeForUpdate("Q_IMPORT")).thenReturn(Optional.of(question));
        when(labels.findByQuestionIdIn(any())).thenReturn(List.of(new QuestionKnowledgePoint(50L, 1L, KnowledgeLabelRole.PRIMARY)));
        return question;
    }
    private static ValidatedRow row(Map<String, String> values) { return new ValidatedRow(2, values, null, null); }
}
