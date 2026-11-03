package com.cnhsk.learning.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cnhsk.learning.knowledge.KnowledgeTreeService.DistractorError;
import com.cnhsk.learning.knowledge.entity.KnowledgeLabelRole;
import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.entity.QuestionOption;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import com.cnhsk.learning.knowledge.repository.KnowledgeTreeQueryRepository;
import com.cnhsk.learning.knowledge.repository.KnowledgeTreeQueryRepository.QuestionLabelRow;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class KnowledgeTreeServiceTest {

    @Mock private KnowledgePointRepository points;
    @Mock private KnowledgeTreeQueryRepository queries;
    @InjectMocks private KnowledgeTreeService service;

    @Test
    void FR024_AT24_emptyQuestionSetDoesNotQuery() {
        assertThat(service.kidsOfQuestions(Set.of())).isEmpty();
        verifyNoInteractions(points, queries);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"abc", "np1-001", "NP0-001", "NP8-001", "NP10-001", "XX1-001", "NP3-04", "NP3-0001"})
    void FR016_FR024_invalidKidIsAbsentAndDetailReturns404WithoutQuery(String kid) {
        assertThat(service.node(kid)).isEmpty();
        assertThatThrownBy(() -> service.detail(kid)).isInstanceOf(ApiException.class)
                .extracting("code", "status").containsExactly(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND,
                        org.springframework.http.HttpStatus.NOT_FOUND);
        verifyNoInteractions(points, queries);
    }

    @Test
    void FR016_FR024_unknownKidIsAbsentAndDetailReturns404() {
        when(points.findAll()).thenReturn(List.of());
        assertThat(service.node("NP1-999")).isEmpty();
        assertThatThrownBy(() -> service.detail("NP1-999")).isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND);
        verifyNoInteractions(queries);
    }

    @Test
    void FR024_AT24_mapsLabelsAndOnlyWrongOptionsWithErrorKids() {
        Question question = new Question("TEST_LABELS", QuestionType.MULTIPLE_CHOICE, (short) 1, "Câu tự soạn.");
        ReflectionTestUtils.setField(question, "id", 42L);
        question.setOptions(List.of(new QuestionOption("A", "一", true, "NP1-001", "Không dùng"),
                new QuestionOption("B", "二", false, "TV1-001", "Lỗi tự soạn"),
                new QuestionOption("C", "三", false, null, null)));
        when(queries.questionLabels(Set.of(42L, 99L))).thenReturn(List.of(
                new QuestionLabelRow(question, "NP1-001", KnowledgeLabelRole.PRIMARY),
                new QuestionLabelRow(question, "TV1-001", KnowledgeLabelRole.SECONDARY)));
        var result = service.kidsOfQuestions(Set.of(42L, 99L));
        assertThat(result).containsOnlyKeys(42L);
        assertThat(result.get(42L).primaryKid()).isEqualTo("NP1-001");
        assertThat(result.get(42L).secondaryKids()).containsExactly("TV1-001");
        assertThat(result.get(42L).errorsByOption()).containsExactlyEntriesOf(
                Map.of("B", new DistractorError("TV1-001", "Lỗi tự soạn")));
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 201, Integer.MAX_VALUE})
    void FR025_AT25_rejectsInvalidLimitEvenForEmptyKids(int limit) {
        assertThatThrownBy(() -> service.approvedQuestionIds(Set.of(), null, limit))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(points, queries);
    }

    @Test
    void FR025_AT25_emptyKidsReturnNoQuestionsWithoutQuery() {
        assertThat(service.approvedQuestionIds(Set.of(), null, 1)).isEmpty();
        assertThat(service.approvedQuestionIds(Set.of(), 9, 200)).isEmpty();
        verifyNoInteractions(points, queries);
    }
}
