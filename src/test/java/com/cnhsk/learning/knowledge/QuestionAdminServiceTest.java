package com.cnhsk.learning.knowledge;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.learning.knowledge.entity.KnowledgeDomain;
import com.cnhsk.learning.knowledge.entity.KnowledgeLabelRole;
import com.cnhsk.learning.knowledge.entity.KnowledgePoint;
import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.entity.QuestionOption;
import com.cnhsk.learning.knowledge.entity.QuestionStatus;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import com.cnhsk.learning.knowledge.QuestionAdminService.QuestionUpdate;
import com.cnhsk.learning.knowledge.QuestionAdminService.QuestionUpdateOption;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import com.cnhsk.learning.knowledge.repository.QuestionAdminQueryRepository.Filters;
import com.cnhsk.learning.knowledge.repository.QuestionAdminQueryRepository.LabelRow;
import com.cnhsk.learning.knowledge.repository.QuestionAdminQueryRepository.QuestionRows;
import com.cnhsk.learning.knowledge.repository.QuestionAdminQueryRepository;
import com.cnhsk.learning.knowledge.repository.QuestionKnowledgePointRepository;
import com.cnhsk.learning.knowledge.repository.QuestionRepository;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuestionAdminServiceTest {
    @Mock private QuestionRepository questions;
    @Mock private QuestionAdminQueryRepository queries;
    @Mock private KnowledgePointRepository points;
    @Mock private QuestionKnowledgePointRepository labels;
    @Mock private EntityManager entities;
    @Mock private AuditService audit;
    @Mock private AuthApi auth;
    @InjectMocks private QuestionAdminService service;
    private final Filters filters = new Filters(null, null, null, null, null);

    @Test
    void FR018_AT18_emptyPagesDoNotReadLabelsOrUsage() {
        when(queries.page(filters, 8, 10)).thenReturn(new QuestionRows(List.of(), 12));
        var result = service.list(filters, 8, 10);
        assertThat(result.items()).isEmpty(); assertThat(result.totalElements()).isEqualTo(12);
        assertThat(result.page()).isEqualTo(8); assertThat(result.size()).isEqualTo(10);
        verify(queries).page(filters, 8, 10); verifyNoMoreInteractions(queries); verifyNoInteractions(questions);
    }

    @Test
    void FR018_AT18_preservesPageOrderAndMapsUnlabeledQuestionsAndBatchedUsage() {
        Question newer = question(9); Question older = question(4);
        when(queries.page(filters, 0, 10)).thenReturn(new QuestionRows(List.of(newer, older), 2));
        LabelRow label = new LabelRow(9, "NP1-901", "Nút tự soạn", KnowledgeLabelRole.PRIMARY);
        when(queries.labels(Set.of(9L, 4L))).thenReturn(List.of(label));
        when(questions.usedQuestionIds(Set.of(9L, 4L))).thenReturn(Set.of(4L));
        var items = service.list(filters, 0, 10).items();
        assertThat(items).extracting(view -> view.question().getId()).containsExactly(9L, 4L);
        assertThat(items.get(0).labels()).containsExactly(label); assertThat(items.get(0).used()).isFalse();
        assertThat(items.get(1).labels()).isEmpty(); assertThat(items.get(1).used()).isTrue();
        verify(queries).labels(Set.of(9L, 4L)); verify(questions).usedQuestionIds(Set.of(9L, 4L));
    }

    @Test
    void FR019_AT19_missingQuestionReturns404WithoutReadingLabelsOrUsage() {
        when(questions.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.detail(99)).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(ErrorCode.QUESTION_NOT_FOUND);
            assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(error.getMessage()).isEqualTo("Không tìm thấy câu hỏi.");
        });
        verifyNoInteractions(queries); verify(questions).findById(99L); verifyNoMoreInteractions(questions);
    }

    @Test
    void AT20_missingQuestionPrecedesBodyValidation() {
        when(questions.findByIdForUpdate(99)).thenReturn(Optional.empty());
        assertError(() -> service.update(99, null), ErrorCode.QUESTION_NOT_FOUND, 404);
        verifyNoInteractions(points, labels, audit, auth, entities, queries);
    }

    @Test
    void AT22_archivePrecedesNullOrInvalidBodyAndDoesNotReadKidsOrUsed() {
        Question question = editable(); question.setStatus(QuestionStatus.ARCHIVED);
        when(questions.findByIdForUpdate(9)).thenReturn(Optional.of(question));
        assertError(() -> service.update(9, null), ErrorCode.QUESTION_ARCHIVED, 409);
        verifyNoInteractions(points, labels, audit, auth, entities, queries);
        verify(questions, never()).usedQuestionIds(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "　"})
    void AT21_missingPrimaryHasItsOwnCodeBeforeOtherContentErrors(String primary) {
        when(questions.findByIdForUpdate(9)).thenReturn(Optional.of(editable()));
        QuestionUpdate input = new QuestionUpdate("", List.of(), null, "", 0, 0, null, null, primary, List.of());
        assertError(() -> service.update(9, input), ErrorCode.KNOWLEDGE_NODE_REQUIRED, 422);
        verify(questions, never()).usedQuestionIds(any()); verifyNoInteractions(audit, entities);
    }

    @Test
    void AT21_nonexistentPrimaryIsMalformedAndValidationPrecedesUsedGuard() {
        when(questions.findByIdForUpdate(9)).thenReturn(Optional.of(editable()));
        assertError(() -> service.update(9, input(false)), ErrorCode.MALFORMED_QUESTION, 422);
        verify(points).findByCodeIn(Set.of("NP1-901"));
        verify(questions, never()).usedQuestionIds(any()); verifyNoInteractions(audit, labels, entities);
    }

    @Test
    void AT22_changedGradingChecksUsageAfterValidationAndDoesNotWriteWhenUsed() {
        when(questions.findByIdForUpdate(9)).thenReturn(Optional.of(editable())); stubPoint();
        when(questions.usedQuestionIds(Set.of(9L))).thenReturn(Set.of(9L));
        assertError(() -> service.update(9, input(false)), ErrorCode.GRADING_CHANGE_NOT_ALLOWED, 409);
        var order = inOrder(questions, points);
        order.verify(questions).findByIdForUpdate(9); order.verify(points).findByCodeIn(Set.of("NP1-901"));
        order.verify(questions).usedQuestionIds(Set.of(9L));
        verifyNoInteractions(labels, entities, audit);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void AT20_AT22_readsUsedExactlyOnceAndReusesTheGuardResult(boolean sameGrading) {
        when(questions.findByIdForUpdate(9)).thenReturn(Optional.of(editable())); stubPoint();
        when(auth.currentUser()).thenReturn(new CurrentUser(12, Set.of(RoleCode.TEACHER), true));
        when(questions.usedQuestionIds(Set.of(9L))).thenReturn(sameGrading ? Set.of(9L) : Set.of());
        assertThat(service.update(9, input(sameGrading)).used()).isEqualTo(sameGrading);
        verify(questions, times(1)).usedQuestionIds(Set.of(9L));
        var order = inOrder(questions, entities);
        order.verify(questions).findByIdForUpdate(9);
        if (!sameGrading) order.verify(questions).usedQuestionIds(Set.of(9L));
        order.verify(entities).persist(any()); order.verify(entities).flush();
        if (sameGrading) order.verify(questions).usedQuestionIds(Set.of(9L));
        verify(audit).record(eq(12L), eq("QUESTION_UPDATE"), eq("question:9"), any(), any());
    }

    private void stubPoint() {
        KnowledgePoint point = new KnowledgePoint("NP1-901", "Nút tự soạn", KnowledgeDomain.NP, "示例", (short) 1);
        ReflectionTestUtils.setField(point, "id", 1L);
        when(points.findByCodeIn(Set.of("NP1-901"))).thenReturn(List.of(point));
    }
    private static Question editable() {
        Question question = question(9); question.setQtype(QuestionType.MULTIPLE_CHOICE); question.setStatus(QuestionStatus.APPROVED);
        question.setOptions(List.of(new QuestionOption("A", "我", true, null, null), new QuestionOption("B", "你", false, null, null)));
        return question;
    }
    private static QuestionUpdate input(boolean sameGrading) {
        return new QuestionUpdate("Câu tự soạn", List.of(new QuestionUpdateOption("A", "我", sameGrading, null, null),
                new QuestionUpdateOption("B", "你", !sameGrading, null, null)), null, "Lời giải mới", 1, 2, null, null, "NP1-901", List.of());
    }
    private static void assertError(Runnable action, ErrorCode code, int status) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(code); assertThat(error.getStatus().value()).isEqualTo(status);
        });
    }

    private static Question question(long id) {
        Question question = new Question("T11_UNIT_" + id, QuestionType.ESSAY, (short) 1, "Nội dung tự soạn");
        ReflectionTestUtils.setField(question, "id", id);
        return question;
    }
}
