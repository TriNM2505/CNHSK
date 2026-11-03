package com.cnhsk.learning.exam;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.entity.QuestionOption;
import com.cnhsk.learning.knowledge.entity.QuestionStatus;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.Skill;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionItemFactoryTest {

    private final QuestionItemFactory factory = new QuestionItemFactory();

    @Test
    void FR004_AT04_multipleChoiceMapsServerSnapshot() {
        Question question = question(QuestionType.MULTIPLE_CHOICE);
        question.setCorrectKey("B");
        ServedItem item = factory.create(question, 7).orElseThrow();

        assertThat(item.no()).isEqualTo(7);
        assertThat(item.type()).isEqualTo(ItemType.QUESTION_CHOICE);
        assertThat(item.skill()).isEqualTo(Skill.READING);
        assertThat(item.lexemeId()).isNull();
        assertThat(item.questionId()).isEqualTo(123L);
        assertThat(item.prompt().text()).isEqualTo(question.getStem());
        assertThat(item.prompt().audioUrl()).isNull();
        assertThat(item.options()).containsExactly(new ServedItem.Option("B", "Phương án hai"),
                new ServedItem.Option("A", "Phương án một"));
        assertThat(item.answer()).isEqualTo("A");
        assertThat(item.accept()).isNull();
        assertThat(item.explanation()).isEqualTo(question.getExplanation());
        assertThat(item.rules()).isEqualTo(new ServedItem.Rules(1500, 0, false));
    }

    @Test
    void FR004_AT04_trueFalseUsesFixedLabelsAndFlaggedAnswer() {
        Question question = question(QuestionType.TRUE_FALSE);
        question.setOptions(List.of(option("B", true), option("A", false)));

        ServedItem item = factory.create(question, 1).orElseThrow();

        assertThat(item.options()).containsExactly(new ServedItem.Option("A", "Đúng"), new ServedItem.Option("B", "Sai"));
        assertThat(item.answer()).isEqualTo("B");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void FR004_AT04_nullOrBlankMediaIsUsable(String media) {
        Question question = question(QuestionType.MULTIPLE_CHOICE);
        question.setAudioUrl(media);
        question.setImageUrl(media);
        assertThat(factory.isUsable(question)).isTrue();
        assertThat(factory.create(question, 1)).isPresent();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void FR004_AT04_mediaQuestionIsExcluded(boolean audio) {
        Question question = question(QuestionType.MULTIPLE_CHOICE);
        if (audio) {
            question.setAudioUrl("/test/audio.mp3");
        } else {
            question.setImageUrl("/test/image.png");
        }
        assertExcluded(question);
    }

    @ParameterizedTest
    @EnumSource(value = QuestionType.class, names = {"MULTIPLE_CHOICE", "TRUE_FALSE"}, mode = EnumSource.Mode.EXCLUDE)
    void FR004_AT04_unsupportedTypesAreExcluded(QuestionType type) {
        assertExcluded(question(type));
    }

    @ParameterizedTest
    @EnumSource(value = QuestionStatus.class, names = "APPROVED", mode = EnumSource.Mode.EXCLUDE)
    void FR004_AT04_unapprovedQuestionsAreExcluded(QuestionStatus status) {
        Question question = question(QuestionType.MULTIPLE_CHOICE);
        question.setStatus(status);
        assertExcluded(question);
    }

    @ParameterizedTest
    @MethodSource("invalidOptions")
    void FR004_AT04_invalidOptionKeysOrAnswerCountAreExcluded(List<QuestionOption> options) {
        for (QuestionType type : List.of(QuestionType.MULTIPLE_CHOICE, QuestionType.TRUE_FALSE)) {
            Question question = question(type);
            question.setOptions(options);
            assertExcluded(question);
        }
    }

    @ParameterizedTest
    @MethodSource("invalidTrueFalseOptions")
    void FR004_AT04_trueFalseRequiresExactlyAAndB(List<QuestionOption> options) {
        Question question = question(QuestionType.TRUE_FALSE);
        question.setOptions(options);
        assertExcluded(question);
    }

    @Test
    void FR004_AT04_missingQuestionIsExcluded() {
        assertExcluded(null);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void FR004_AT04_warningContainsOnlyIdAndReason(CapturedOutput output) {
        Question question = question(QuestionType.FILL_BLANK);
        question.setStem("PRIVATE_STEM_MARKER");
        question.setExplanation("PRIVATE_EXPLANATION_MARKER");
        question.setOptions(List.of(new QuestionOption("PRIVATE_ANSWER_MARKER", "PRIVATE_OPTION_MARKER", true, null, null)));

        assertThat(factory.create(question, 1)).isEmpty();

        assertThat(output.getOut()).contains("questionId=123, reason=UNSUPPORTED_TYPE")
                .doesNotContain("PRIVATE_STEM_MARKER", "PRIVATE_EXPLANATION_MARKER", "PRIVATE_ANSWER_MARKER", "PRIVATE_OPTION_MARKER");
    }

    private static Stream<List<QuestionOption>> invalidOptions() {
        return Stream.of(List.of(), List.of(option("A", false), option("B", false)),
                List.of(option("A", true), option("B", true)),
                List.of(option("A", true), option("A", false)),
                List.of(option("", true), option("B", false)),
                List.of(option(" ", true), option("B", false)),
                List.of(option(null, true), option("B", false)));
    }

    private static Stream<List<QuestionOption>> invalidTrueFalseOptions() {
        return Stream.of(List.of(option("A", true)),
                List.of(option("A", true), option("B", false), option("C", false)),
                List.of(option("A", true), option("C", false)));
    }

    private void assertExcluded(Question question) {
        assertThat(factory.isUsable(question)).isFalse();
        assertThat(factory.create(question, 1)).isEmpty();
    }

    private Question question(QuestionType type) {
        Question question = new Question("TEST-" + UUID.randomUUID(), type, (short) 1, "Câu hỏi tự soạn");
        ReflectionTestUtils.setField(question, "id", 123L);
        question.setStatus(QuestionStatus.APPROVED);
        question.setOptions(List.of(new QuestionOption("B", "Phương án hai", false, null, null),
                new QuestionOption("A", "Phương án một", true, null, null)));
        question.setExplanation("Lời giải tự soạn");
        return question;
    }

    private static QuestionOption option(String key, boolean correct) {
        return new QuestionOption(key, "Nhãn trong DB", correct, null, null);
    }
}
