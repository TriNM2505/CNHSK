package com.cnhsk.learning.exam;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.entity.QuestionOption;
import com.cnhsk.learning.knowledge.entity.QuestionStatus;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.Skill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class QuestionItemFactory {

    private static final Logger log = LoggerFactory.getLogger(QuestionItemFactory.class);

    public boolean isUsable(Question question) {
        String reason = unusableReason(question);
        if (reason != null) {
            log.warn("Question excluded: questionId={}, reason={}", question == null ? null : question.getId(), reason);
            return false;
        }
        return true;
    }

    public Optional<ServedItem> create(Question question, int no) {
        if (!isUsable(question)) {
            return Optional.empty();
        }
        List<ServedItem.Option> options = question.getQtype() == QuestionType.TRUE_FALSE
                ? List.of(new ServedItem.Option("A", "Đúng"), new ServedItem.Option("B", "Sai"))
                : question.getOptions().stream().map(option -> new ServedItem.Option(option.key(), option.text())).toList();
        String answer = question.getOptions().stream().filter(QuestionOption::isCorrect)
                .map(QuestionOption::key).findFirst().orElseThrow();
        return Optional.of(new ServedItem(no, ItemType.QUESTION_CHOICE, Skill.READING, null,
                new ServedItem.Prompt(question.getStem(), null, null, null, null, null, null, null, null),
                options, answer, null, question.getExplanation(), new ServedItem.Rules(1500, 0, false), question.getId()));
    }

    private String unusableReason(Question question) {
        if (question == null) {
            return "MISSING_QUESTION";
        }
        if (question.getStatus() != QuestionStatus.APPROVED) {
            return "NOT_APPROVED";
        }
        if (question.getQtype() != QuestionType.MULTIPLE_CHOICE && question.getQtype() != QuestionType.TRUE_FALSE) {
            return "UNSUPPORTED_TYPE";
        }
        if (hasText(question.getAudioUrl()) || hasText(question.getImageUrl())) {
            return "MEDIA_PRESENT";
        }
        if (question.getOptions() == null) {
            return "MISSING_OPTIONS";
        }
        Set<String> keys = new HashSet<>();
        int correct = 0;
        for (QuestionOption option : question.getOptions()) {
            if (option == null || !hasText(option.key())) {
                return "EMPTY_OPTION_KEY";
            }
            if (!keys.add(option.key())) {
                return "DUPLICATE_OPTION_KEY";
            }
            if (option.isCorrect()) {
                correct++;
            }
        }
        if (question.getQtype() == QuestionType.TRUE_FALSE && !keys.equals(Set.of("A", "B"))) {
            return "INVALID_TRUE_FALSE_KEYS";
        }
        return correct == 1 ? null : "INVALID_CORRECT_OPTION_COUNT";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
