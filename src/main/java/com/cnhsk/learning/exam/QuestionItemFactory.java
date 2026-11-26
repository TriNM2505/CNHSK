package com.cnhsk.learning.exam;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.cnhsk.learning.knowledge.EmojiText;
import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.entity.QuestionOption;
import com.cnhsk.learning.knowledge.entity.QuestionStatus;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.Skill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns bank questions into served items. The plain factory serves text-only choice questions, which is all
 * quizzes, contests, reading practice and the entrance test show. The full-format factory, used by mock exams,
 * also serves the HSK paper's listening, picture, ordering and fill-in questions.
 */
public final class QuestionItemFactory {

    private static final Logger log = LoggerFactory.getLogger(QuestionItemFactory.class);
    /** Listening questions are read aloud by the browser; a learner may play each one this many times. */
    static final int LISTENING_PLAYS = 2;
    /** Marks the statement of a listening question that is printed for the learner to judge. */
    static final String PRINTED = "★";
    private static final Set<QuestionType> CHOICE = Set.of(QuestionType.MULTIPLE_CHOICE, QuestionType.TRUE_FALSE,
            QuestionType.IMAGE_MATCH, QuestionType.SENTENCE_MATCH);

    private final boolean fullFormat;

    public QuestionItemFactory() {
        this(false);
    }

    private QuestionItemFactory(boolean fullFormat) {
        this.fullFormat = fullFormat;
    }

    /** Serves every machine-graded question type of the HSK paper, with pictures and listening. */
    public static QuestionItemFactory fullFormat() {
        return new QuestionItemFactory(true);
    }

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
        boolean listening = fullFormat && "LISTENING".equals(question.getSkill());
        // A listening stem is heard, not shown; only its lines starting with ★ are printed, as on the paper.
        String shown = listening ? printed(question.getStem()) : question.getStem();
        var prompt = new ServedItem.Prompt(shown, null, null, null,
                fullFormat ? blankToNull(question.getAudioUrl()) : null, null, null, null, null, null,
                listening ? spoken(question.getStem()) : null, fullFormat ? blankToNull(question.getImageUrl()) : null);
        var rules = new ServedItem.Rules(1500, listening ? LISTENING_PLAYS : 0, listening);
        Skill skill = skillOf(question);
        return Optional.of(switch (question.getQtype()) {
            case FILL_BLANK -> new ServedItem(no, ItemType.QUESTION_FILL, skill, null, prompt, null,
                    question.getCorrectKey().strip(), accepted(question.getCorrectKey()), question.getExplanation(), rules,
                    question.getId());
            case SENTENCE_ORDER -> new ServedItem(no, ItemType.QUESTION_ORDER, skill, null, prompt, served(question),
                    question.getCorrectKey().strip(), null, question.getExplanation(), rules, question.getId());
            default -> new ServedItem(no, ItemType.QUESTION_CHOICE, skill, null, prompt, served(question),
                    question.getOptions().stream().filter(QuestionOption::isCorrect).map(QuestionOption::key)
                            .findFirst().orElseThrow(),
                    null, question.getExplanation(), rules, question.getId());
        });
    }

    private List<ServedItem.Option> served(Question question) {
        if (question.getQtype() == QuestionType.TRUE_FALSE) {
            return List.of(new ServedItem.Option("A", "Đúng"), new ServedItem.Option("B", "Sai"));
        }
        // A picture option's text only describes the picture for editors; showing it would give the answer away.
        return question.getOptions().stream().map(option -> fullFormat && hasText(option.imageUrl())
                ? new ServedItem.Option(option.key(), "", option.imageUrl().strip())
                : new ServedItem.Option(option.key(), option.text())).toList();
    }

    private Skill skillOf(Question question) {
        if (!fullFormat || question.getSkill() == null) {
            return Skill.READING;
        }
        return switch (question.getSkill()) {
            case "LISTENING" -> Skill.LISTENING;
            case "WRITING" -> Skill.WRITING;
            default -> Skill.READING;
        };
    }

    /**
     * Splits a listening stem into what is printed and what is spoken. Printed: an emoji picture opening a line and
     * everything from ★ on (the statement to judge); the browser voice would read an emoji's name aloud. Imported
     * stems arrive on one line, so both work without line breaks, e.g. "🐱 我喜欢猫。★ 他喜欢狗。".
     */
    private static String[] split(String stem) {
        StringBuilder printed = new StringBuilder();
        StringBuilder spoken = new StringBuilder();
        for (String raw : stem.lines().toList()) {
            String line = raw.strip();
            int picture = EmojiText.leadingPictureLength(line);
            if (picture > 0) {
                append(printed, line.substring(0, picture));
                line = line.substring(picture).strip();
            }
            int star = line.indexOf(PRINTED);
            if (star >= 0) {
                append(printed, line.substring(star).strip());
                line = line.substring(0, star).strip();
            }
            append(spoken, line);
        }
        return new String[] {printed.toString(), spoken.toString()};
    }

    private static void append(StringBuilder into, String line) {
        if (line.isEmpty()) return;
        if (!into.isEmpty()) into.append('\n');
        into.append(line);
    }

    static String printed(String stem) {
        String printed = split(stem)[0];
        return printed.isEmpty() ? null : printed;
    }

    static String spoken(String stem) {
        return split(stem)[1];
    }

    /** Alternatives of a fill-in answer are separated by |. */
    private static List<String> accepted(String correctKey) {
        return Arrays.stream(correctKey.split("\\|")).map(String::strip).filter(value -> !value.isEmpty()).toList();
    }

    private String unusableReason(Question question) {
        if (question == null) {
            return "MISSING_QUESTION";
        }
        if (question.getStatus() != QuestionStatus.APPROVED) {
            return "NOT_APPROVED";
        }
        QuestionType type = question.getQtype();
        if (fullFormat ? type == null || type == QuestionType.ESSAY : !Set.of(QuestionType.MULTIPLE_CHOICE,
                QuestionType.TRUE_FALSE).contains(type)) {
            return "UNSUPPORTED_TYPE";
        }
        if (!fullFormat && (hasText(question.getAudioUrl()) || hasText(question.getImageUrl()))) {
            return "MEDIA_PRESENT";
        }
        if (fullFormat && !hasText(question.getStem())) {
            return "MISSING_STEM";
        }
        if (type == QuestionType.FILL_BLANK) {
            return hasText(question.getCorrectKey()) && !accepted(question.getCorrectKey()).isEmpty()
                    ? null : "MISSING_ANSWER";
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
        if (type == QuestionType.SENTENCE_ORDER) {
            return orderUsesEveryKeyOnce(question.getCorrectKey(), keys) ? null : "INVALID_ORDER";
        }
        if (type == QuestionType.TRUE_FALSE && !keys.equals(Set.of("A", "B"))) {
            return "INVALID_TRUE_FALSE_KEYS";
        }
        if (CHOICE.contains(type) && correct != 1) {
            return "INVALID_CORRECT_OPTION_COUNT";
        }
        return null;
    }

    private static boolean orderUsesEveryKeyOnce(String order, Set<String> keys) {
        if (order == null || keys.size() < 2) {
            return false;
        }
        List<String> used = order.strip().chars().mapToObj(Character::toString).toList();
        return used.size() == keys.size() && new HashSet<>(used).equals(keys);
    }

    private static String blankToNull(String value) {
        return hasText(value) ? value.strip() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
