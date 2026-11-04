package com.cnhsk.learning.knowledge;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.cnhsk.learning.knowledge.entity.KnowledgeLabelRole;
import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import com.cnhsk.learning.knowledge.repository.QuestionAdminQueryRepository;
import com.cnhsk.learning.knowledge.repository.QuestionAdminQueryRepository.Filters;
import com.cnhsk.learning.knowledge.repository.QuestionAdminQueryRepository.LabelRow;
import com.cnhsk.learning.knowledge.repository.QuestionAdminQueryRepository.QuestionRows;
import com.cnhsk.learning.knowledge.repository.QuestionRepository;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuestionAdminServiceTest {
    @Mock private QuestionRepository questions;
    @Mock private QuestionAdminQueryRepository queries;
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

    private static Question question(long id) {
        Question question = new Question("T11_UNIT_" + id, QuestionType.ESSAY, (short) 1, "Nội dung tự soạn");
        ReflectionTestUtils.setField(question, "id", id);
        return question;
    }
}
