package com.cnhsk.learning.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.entity.QuestionOption;
import com.cnhsk.learning.knowledge.entity.QuestionStatus;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class QuizQuestionPickerTest {
    private final QuizQuestionPicker picker = new QuizQuestionPicker(mock(QuizRepository.class));

    @Test
    void balancedDistinctRandomQuestionsHaveSnapshotOrdinalsAndNoClientTimingThreshold() {
        List<Question> bank = bank(8, 8);
        var items = picker.pick(bank, 10, new Random(7));
        assertThat(items).hasSize(10).extracting(ServedItem::questionId).doesNotHaveDuplicates();
        assertThat(items.stream().filter(i -> i.questionId() <= 8)).hasSize(5);
        assertThat(items).extracting(ServedItem::no).containsExactly(1,2,3,4,5,6,7,8,9,10);
        assertThat(items).allSatisfy(item -> {
            assertThat(item.rules().minDurationMs()).isZero();
            assertThat(item.options()).hasSize(2);
        });
        assertThat(picker.pick(bank, 10, new Random(17))).isNotEqualTo(items);
    }

    @Test
    void sparseTypeIsRefilledFromOtherTypeAndConfiguredCountIsHonored() {
        var items = picker.pick(bank(14, 1), 12, new Random(4));
        assertThat(items).hasSize(12);
        assertThat(items.stream().filter(i -> i.questionId() > 14)).hasSize(1);
        assertThat(picker.pick(bank(10, 0), 10, new Random(4))).hasSize(10);
    }

    @Test
    void unsupportedTypesMediaUnapprovedAndMalformedChoicesCannotBeServed() {
        List<Question> bank = bank(8, 0);
        Question archived = question(20, QuestionType.MULTIPLE_CHOICE);
        archived.setStatus(QuestionStatus.ARCHIVED);
        Question rejected = question(21, QuestionType.MULTIPLE_CHOICE);
        rejected.setStatus(QuestionStatus.REJECTED);
        Question media = question(22, QuestionType.MULTIPLE_CHOICE);
        media.setAudioUrl("https://example.test/fixture.mp3");
        Question image = question(23, QuestionType.TRUE_FALSE);
        image.setImageUrl("https://example.test/fixture.png");
        Question invalid = question(24, QuestionType.MULTIPLE_CHOICE);
        invalid.setOptions(List.of(new QuestionOption("A", "tự soạn", false, null, null)));
        bank.addAll(List.of(archived, rejected, media, image, invalid, question(25, QuestionType.ESSAY)));
        assertThatThrownBy(() -> picker.pick(bank, 10, new Random(1)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.QUIZ_NOT_AVAILABLE));
    }

    private static List<Question> bank(int multiple, int trueFalse) {
        List<Question> result = new ArrayList<>();
        for (int i=1; i<=multiple+trueFalse; i++) result.add(question(i,
                i<=multiple ? QuestionType.MULTIPLE_CHOICE : QuestionType.TRUE_FALSE));
        return result;
    }

    private static Question question(long id, QuestionType type) {
        Question q = new Question("fixture-"+id, type, (short) 1, "Câu tự soạn " + id);
        ReflectionTestUtils.setField(q, "id", id);
        q.setStatus(QuestionStatus.APPROVED);
        q.setOptions(List.of(new QuestionOption("A", "nước", true, null, null),
                new QuestionOption("B", "lửa", false, null, null)));
        return q;
    }
}
