package com.cnhsk.learning.exam;

import java.util.List;
import java.util.Optional;

import com.cnhsk.learning.knowledge.entity.QuestionType;

/**
 * The layout of an HSK 2.0 paper for levels 1–3, part by part, as the mock exams reproduce it. Every part is
 * machine-graded: listening is read by the browser, pictures are emoji or photos, and the level 3 writing part is
 * put-in-order and fill-in-the-character. Content editors import the questions of each part by hand with codes
 * {@code MCK{level}-{part}-…}; papers are then built from them.
 */
public final class MockPaperFormat {
    /** Papers per level; each takes different questions. */
    public static final int PAPERS = 3;
    public static final String CODE_PREFIX = "MOCK-HSK";
    public static final String QUESTION_PREFIX = "MCK";

    private MockPaperFormat() {
    }

    public enum Picture { NONE, STEM, OPTIONS }

    public record Section(int index, String name, int minutes) {}

    /** One part of the paper; {@code guide} tells editors, in Vietnamese, how to write its import rows. */
    public record Part(String code, int section, String skill, QuestionType type, int count, Picture picture,
            int options, String guide) {}

    public record Layout(int level, List<Section> sections, List<Part> parts) {
        public int questions() {
            return parts.stream().mapToInt(Part::count).sum();
        }

        public Optional<Part> part(String code) {
            return parts.stream().filter(part -> part.code().equals(code)).findFirst();
        }
    }

    private static final String TF_PICTURE = "stem: dòng đầu là emoji (hoặc để image_url), dòng sau là từ/cụm từ; "
            + "correct TRUE nếu từ khớp hình.";
    private static final String CHOOSE_PICTURE = "stem: một câu hoặc hội thoại A:/B:; option_a…: mỗi phương án là một "
            + "emoji, hoặc mô tả ngắn kèm option_images; correct là khoá đúng.";
    private static final String DIALOGUE_QUESTION = "stem: hội thoại A:/B: rồi dòng 问：…; phương án là câu trả lời ngắn "
            + "bằng chữ Hán.";
    private static final String STATEMENT = "stem: đoạn nói/đoạn văn 1–3 câu, dòng cuối bắt đầu bằng ★ là nhận định "
            + "(câu nghe: chỉ dòng ★ được in); correct TRUE/FALSE.";
    private static final String MATCH = "stem: câu hỏi hoặc câu nói; phương án là câu đáp lại, chỉ một câu đúng.";
    private static final String FILL_WORD = "stem: câu tiếng Trung có một chỗ trống ___; phương án là các từ để điền.";
    private static final String READING = "stem: đoạn văn 2–4 câu rồi dòng 问：…; phương án là câu trả lời ngắn.";
    private static final String ORDER = "option_a…: 3–5 mảnh của câu, xáo trộn; correct là thứ tự khoá đúng, vd BCA.";
    private static final String FILL_CHAR = "stem: câu có ___ kèm pinyin chữ cần điền, vd 我们一起___（xué）习。; "
            + "correct là chữ Hán (nhiều cách viết ngăn bằng |).";

    private static final Layout HSK1 = new Layout(1, List.of(new Section(0, "Nghe", 15), new Section(1, "Đọc", 17)), List.of(
            new Part("L1", 0, "LISTENING", QuestionType.TRUE_FALSE, 5, Picture.STEM, 2, TF_PICTURE),
            new Part("L2", 0, "LISTENING", QuestionType.IMAGE_MATCH, 5, Picture.OPTIONS, 3, CHOOSE_PICTURE),
            new Part("L3", 0, "LISTENING", QuestionType.IMAGE_MATCH, 5, Picture.OPTIONS, 3, CHOOSE_PICTURE),
            new Part("L4", 0, "LISTENING", QuestionType.MULTIPLE_CHOICE, 5, Picture.NONE, 3, DIALOGUE_QUESTION),
            new Part("R1", 1, "READING", QuestionType.TRUE_FALSE, 5, Picture.STEM, 2, TF_PICTURE),
            new Part("R2", 1, "READING", QuestionType.IMAGE_MATCH, 5, Picture.OPTIONS, 3, CHOOSE_PICTURE),
            new Part("R3", 1, "READING", QuestionType.SENTENCE_MATCH, 5, Picture.NONE, 3, MATCH),
            new Part("R4", 1, "READING", QuestionType.MULTIPLE_CHOICE, 5, Picture.NONE, 3, FILL_WORD)));

    private static final Layout HSK2 = new Layout(2, List.of(new Section(0, "Nghe", 25), new Section(1, "Đọc", 22)), List.of(
            new Part("L1", 0, "LISTENING", QuestionType.TRUE_FALSE, 10, Picture.STEM, 2, TF_PICTURE),
            new Part("L2", 0, "LISTENING", QuestionType.IMAGE_MATCH, 10, Picture.OPTIONS, 3, CHOOSE_PICTURE),
            new Part("L3", 0, "LISTENING", QuestionType.MULTIPLE_CHOICE, 10, Picture.NONE, 3, DIALOGUE_QUESTION),
            new Part("L4", 0, "LISTENING", QuestionType.MULTIPLE_CHOICE, 5, Picture.NONE, 3, DIALOGUE_QUESTION),
            new Part("R1", 1, "READING", QuestionType.IMAGE_MATCH, 5, Picture.OPTIONS, 3, CHOOSE_PICTURE),
            new Part("R2", 1, "READING", QuestionType.MULTIPLE_CHOICE, 5, Picture.NONE, 4, FILL_WORD),
            new Part("R3", 1, "READING", QuestionType.TRUE_FALSE, 5, Picture.NONE, 2, STATEMENT),
            new Part("R4", 1, "READING", QuestionType.SENTENCE_MATCH, 10, Picture.NONE, 4, MATCH)));

    private static final Layout HSK3 = new Layout(3,
            List.of(new Section(0, "Nghe", 35), new Section(1, "Đọc", 30), new Section(2, "Viết", 15)), List.of(
            new Part("L1", 0, "LISTENING", QuestionType.IMAGE_MATCH, 10, Picture.OPTIONS, 3, CHOOSE_PICTURE),
            new Part("L2", 0, "LISTENING", QuestionType.TRUE_FALSE, 10, Picture.NONE, 2, STATEMENT),
            new Part("L3", 0, "LISTENING", QuestionType.MULTIPLE_CHOICE, 10, Picture.NONE, 3, DIALOGUE_QUESTION),
            new Part("L4", 0, "LISTENING", QuestionType.MULTIPLE_CHOICE, 10, Picture.NONE, 3, DIALOGUE_QUESTION),
            new Part("R1", 1, "READING", QuestionType.SENTENCE_MATCH, 10, Picture.NONE, 4, MATCH),
            new Part("R2", 1, "READING", QuestionType.MULTIPLE_CHOICE, 10, Picture.NONE, 4, FILL_WORD),
            new Part("R3", 1, "READING", QuestionType.MULTIPLE_CHOICE, 10, Picture.NONE, 3, READING),
            new Part("W1", 2, "WRITING", QuestionType.SENTENCE_ORDER, 5, Picture.NONE, 0, ORDER),
            new Part("W2", 2, "WRITING", QuestionType.FILL_BLANK, 5, Picture.NONE, 0, FILL_CHAR)));

    public static Layout of(int level) {
        return switch (level) {
            case 1 -> HSK1;
            case 2 -> HSK2;
            case 3 -> HSK3;
            default -> throw new IllegalArgumentException("Mock papers cover HSK 1 to 3");
        };
    }

    /** Code prefix of the imported questions of one part, e.g. {@code MCK2-L3-}. */
    public static String questionPrefix(int level, String part) {
        return QUESTION_PREFIX + level + "-" + part + "-";
    }

    public static String paperCode(int level, int paper) {
        return CODE_PREFIX + level + "-0" + paper;
    }
}
