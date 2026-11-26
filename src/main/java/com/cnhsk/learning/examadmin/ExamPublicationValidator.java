package com.cnhsk.learning.examadmin;

import static com.cnhsk.learning.examadmin.ExamAdminModels.*;
import static com.cnhsk.learning.examadmin.ExamAdminModels.PublicationErrorCode.*;

import java.util.ArrayList;
import java.util.List;

import com.cnhsk.learning.exam.QuestionItemFactory;
import com.cnhsk.learning.knowledge.entity.QuestionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class ExamPublicationValidator {
    private final QuestionItemFactory factory = QuestionItemFactory.fullFormat();

    List<PublicationError> validate(ExamRow exam, List<QuestionData> questions) {
        List<PublicationError> errors = new ArrayList<>();
        for (JsonNode section : exam.sections()) {
            int index = section.path("index").asInt();
            if (!section.hasNonNull("type")) {
                errors.add(new PublicationError(SECTION_TYPE_MISSING, "Phần thi chưa có loại; cần bổ sung qua màn sửa đề.", index, null));
            }
            if (questions.stream().noneMatch(item -> item.link().sectionIndex() == index)) {
                errors.add(new PublicationError(EMPTY_SECTION, "Phần thi chưa có câu hỏi.", index, null));
            }
        }
        for (QuestionData item : questions) {
            var question = item.question();
            Link link = item.link();
            // A listening question is heard either from its audio file or from the text the browser speaks aloud.
            boolean spoken = false;
            if (question == null || question.getStatus() != QuestionStatus.APPROVED) {
                errors.add(error(QUESTION_NOT_APPROVED, "Câu hỏi chưa được duyệt.", link));
            } else {
                var served = factory.create(question, link.orderIndex());
                if (served.isEmpty()) {
                    errors.add(error(QUESTION_UNSUPPORTED, "Câu hỏi chưa được engine hỗ trợ.", link));
                } else {
                    String speakText = served.get().prompt().speakText();
                    spoken = speakText != null && !speakText.isBlank();
                }
            }
            if (item.kid() == null || item.kid().isBlank()) {
                errors.add(error(QUESTION_WITHOUT_KID, "Câu hỏi chưa có KID chính.", link));
            }
            boolean listening = false;
            for (JsonNode section : exam.sections()) {
                if (section.path("index").asInt() == link.sectionIndex()
                        && "LISTENING".equals(section.path("type").asText())) {
                    listening = true;
                }
            }
            if (listening && !spoken
                    && (question == null || question.getAudioUrl() == null || question.getAudioUrl().isBlank())) {
                errors.add(error(LISTENING_AUDIO_MISSING, "Câu hỏi phần nghe chưa có audio.", link));
            }
        }
        long sum = questions.stream().mapToLong(item -> item.link().points()).sum();
        if (sum != exam.totalScore()) {
            errors.add(new PublicationError(SCORE_MISMATCH, "Tổng điểm câu hỏi không khớp tổng điểm đề.", null, null));
        }
        return List.copyOf(errors);
    }

    private PublicationError error(PublicationErrorCode code, String message, Link link) {
        return new PublicationError(code, message, link.sectionIndex(), link.questionId());
    }
}
