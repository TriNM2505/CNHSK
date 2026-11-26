package com.cnhsk.learning.examadmin;

import static com.cnhsk.learning.examadmin.ExamAdminModels.*;
import static com.cnhsk.learning.examadmin.ExamAdminModels.PublicationErrorCode.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.entity.QuestionOption;
import com.cnhsk.learning.knowledge.entity.QuestionStatus;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ExamPublicationValidatorTest {
    private final ExamPublicationValidator validator = new ExamPublicationValidator();
    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @EnumSource(value = QuestionType.class, names = {"MULTIPLE_CHOICE", "TRUE_FALSE"})
    void supportedApprovedQuestionsWithPrimaryKidAndExactPointsHaveNoErrors(QuestionType type) throws Exception {
        Question question = question();
        question.setQtype(type);
        assertThat(validator.validate(exam(1, "READING"), List.of(data(question, "NP3-901")))).isEmpty();
    }

    @Test
    void collectsAllFourIndependentPublicationFailures() throws Exception {
        var sections = mapper.readTree("""
                [{"index":0,"name":"Đọc","type":"READING","duration_min":0},
                 {"index":1,"name":"Nghe","type":"LISTENING","duration_min":0},
                 {"index":2,"name":"Viết","type":"WRITING","duration_min":0}]
                """);
        Question pending = question();
        pending.setStatus(QuestionStatus.PENDING_REVIEW);
        var exam = new ExamRow(1, "EXAM", "Đề tự soạn", 3, 99, sections, ExamStatus.DRAFT, null);
        var errors = validator.validate(exam, List.of(data(pending, "NP3-901"),
                new QuestionData(new Link(2, 1, 1, 1), question(), "NP3-901")));
        assertThat(errors).extracting(PublicationError::code)
                .containsExactly(EMPTY_SECTION, QUESTION_NOT_APPROVED, LISTENING_AUDIO_MISSING, SCORE_MISMATCH);
        assertThat(errors.get(0).sectionIndex()).isEqualTo(2);
        assertThat(errors.get(1).questionId()).isEqualTo(1L);
        assertThat(errors.get(2).sectionIndex()).isEqualTo(1);
        assertThat(errors.get(3).questionId()).isNull();
        assertThat(errors).allSatisfy(error -> assertThat(error.message()).isNotBlank());
    }

    @ParameterizedTest
    @EnumSource(value = QuestionStatus.class, names = {"APPROVED"}, mode = EnumSource.Mode.EXCLUDE)
    void unapprovedQuestionsHaveTheSpecificStatusError(QuestionStatus status) throws Exception {
        Question question = question();
        question.setStatus(status);
        assertThat(validator.validate(exam(1, "READING"), List.of(data(question, "NP3-901"))))
                .extracting(PublicationError::code).containsExactly(QUESTION_NOT_APPROVED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void missingPrimaryKidIsReported(String kid) throws Exception {
        assertThat(validator.validate(exam(1, "READING"), List.of(data(question(), kid))))
                .extracting(PublicationError::code).containsExactly(QUESTION_WITHOUT_KID);
        assertThat(validator.validate(exam(1, "READING"), List.of(data(question(), null))))
                .extracting(PublicationError::code).containsExactly(QUESTION_WITHOUT_KID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"options", "type"})
    void factoryLimitationsAreReportedWithoutChangingTheEngine(String kind) throws Exception {
        Question question = question();
        switch (kind) {
            case "options" -> question.setOptions(List.of());
            case "type" -> question.setQtype(QuestionType.FILL_BLANK);
            default -> throw new AssertionError();
        }
        assertThat(validator.validate(exam(1, "READING"), List.of(data(question, "NP3-901"))))
                .extracting(PublicationError::code).containsExactly(QUESTION_UNSUPPORTED);
    }

    @Test
    void everySectionWithUnknownTypeIsReportedWithoutGuessingItsSkill() throws Exception {
        var sections = mapper.readTree("""
                [{"index":0,"name":"Cũ một"},{"index":1,"name":"Cũ hai","type":null},
                 {"index":2,"name":"Đọc","type":"READING"}]
                """);
        var exam = new ExamRow(1, "EXAM", "Đề tự soạn", 3, 3, sections, ExamStatus.DRAFT, null);
        var questions = java.util.stream.IntStream.range(0, 3).mapToObj(index ->
                new QuestionData(new Link(index + 1, index, index, 1), question(), "NP3-901")).toList();
        var errors = validator.validate(exam, questions);
        assertThat(errors).extracting(PublicationError::code).containsExactly(SECTION_TYPE_MISSING, SECTION_TYPE_MISSING);
        assertThat(errors).extracting(PublicationError::sectionIndex).containsExactly(0, 1);
    }

    @Test
    void questionsWithPicturesOrAudioArePublishable() throws Exception {
        Question question = question();
        question.setImageUrl("https://example.test/image.png");
        assertThat(validator.validate(exam(1, "READING"), List.of(data(question, "NP3-901")))).isEmpty();
    }

    @Test
    void listeningNeedsAudioOrTextTheBrowserCanSpeak() throws Exception {
        Question question = question();
        question.setAudioUrl(" ");
        assertThat(validator.validate(exam(1, "LISTENING"), List.of(data(question, "NP3-901"))))
                .extracting(PublicationError::code).containsExactly(LISTENING_AUDIO_MISSING);
        question.setAudioUrl("https://example.test/audio.mp3");
        assertThat(validator.validate(exam(1, "LISTENING"), List.of(data(question, "NP3-901")))).isEmpty();
        question.setAudioUrl(null);
        question.setSkill("LISTENING");
        assertThat(validator.validate(exam(1, "LISTENING"), List.of(data(question, "NP3-901")))).isEmpty();
    }

    private ExamRow exam(int score, String type) throws Exception {
        return new ExamRow(1, "EXAM", "Đề tự soạn", 3, score,
                mapper.readTree("[{\"index\":0,\"name\":\"Đọc\",\"type\":\"" + type + "\",\"duration_min\":0}]"),
                ExamStatus.DRAFT, null);
    }

    static Question question() {
        Question question = new Question("QUESTION", QuestionType.MULTIPLE_CHOICE, (short) 3, "Câu tự soạn");
        question.setStatus(QuestionStatus.APPROVED);
        question.setOptions(List.of(new QuestionOption("A", "Một", true, null, null),
                new QuestionOption("B", "Hai", false, null, null)));
        return question;
    }

    private QuestionData data(Question question, String kid) { return new QuestionData(new Link(1, 0, 0, 1), question, kid); }
}
