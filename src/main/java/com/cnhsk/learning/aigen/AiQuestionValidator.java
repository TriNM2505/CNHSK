package com.cnhsk.learning.aigen;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.cnhsk.learning.knowledge.entity.QuestionOption;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Checks provider output before persistence, keeping failures local to each question. */
@Service
public class AiQuestionValidator {
    private static final Pattern EDGE_SPACE = Pattern.compile("^[\\s\\p{Z}]+|[\\s\\p{Z}]+$",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern SPACE = Pattern.compile("[\\s\\p{Z}]+", Pattern.UNICODE_CHARACTER_CLASS);
    /** Vietnamese/Chinese punctuation and every kind of space are ignored when looking for near duplicates. */
    private static final Pattern PUNCTUATION_OR_SPACE = Pattern.compile("[\\p{P}\\s\\p{Z}]+",
            Pattern.UNICODE_CHARACTER_CLASS);
    /** Jaccard similarity over character bigrams at or above this value counts as the same stem. */
    static final double NEAR_DUPLICATE_THRESHOLD = 0.8;
    /** A stem with at most this many Chinese characters names the tested item rather than a sentence. */
    private static final int SHORT_ITEM_HANZI = 4;
    /** Short stems share bigrams by chance, so only stems this long (code points, after loosening) are compared. */
    static final int NEAR_DUPLICATE_MIN_LENGTH = 8;
    private static final Set<String> KEYS = Set.of("A", "B", "C", "D");
    /** A is "Đúng" and B is "Sai"; learners always see those two labels. */
    private static final Set<String> TRUE_FALSE_KEYS = Set.of("A", "B");
    private final WeakKnowledgeQueryRepository repository;
    private final ObjectMapper mapper;

    public AiQuestionValidator(WeakKnowledgeQueryRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    /** Text rules only; generation always passes a prepared {@link SemanticIndex}. */
    @Transactional(readOnly = true)
    public ValidationResult validate(String output, AiPromptBuilder.Prompt prompt) {
        return validate(output, prompt, SemanticIndex.off(), Integer.MAX_VALUE);
    }

    /**
     * Applies every rule, then keeps at most {@code limit} questions: rows valid beyond the number the batch
     * was allotted are dropped as {@code OVER_QUOTA}, so a provider answering with extra rows cannot exceed
     * the daily cap.
     */
    @Transactional(readOnly = true)
    public ValidationResult validate(String output, AiPromptBuilder.Prompt prompt, SemanticIndex semantic, int limit) {
        if (prompt == null || prompt.knowledgePointId() <= 0 || prompt.hskLevel() < 1 || prompt.hskLevel() > 9) {
            throw new IllegalArgumentException("Valid knowledge point context is required");
        }
        JsonNode root;
        try {
            root = output == null ? null : mapper.readTree(output);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new InvalidOutputException();
        }
        if (root == null || !root.isObject() || !root.path("questions").isArray()) throw new InvalidOutputException();
        List<Dropped> dropped = new ArrayList<>();
        List<IndexedQuestion> candidates = new ArrayList<>();
        int index = 0;
        for (JsonNode question : root.get("questions")) {
            try {
                candidates.add(new IndexedQuestion(index, question(question)));
            } catch (InvalidQuestion exception) {
                dropped.add(new Dropped(index, exception.getMessage()));
            }
            index++;
        }
        if (candidates.isEmpty()) return new ValidationResult(List.of(), dropped);
        Set<String> errorKids = new HashSet<>();
        Set<String> characters = new HashSet<>();
        for (IndexedQuestion candidate : candidates) {
            for (QuestionOption option : candidate.value().options()) {
                if (option.errorKid() != null) errorKids.add(option.errorKid());
                hanzi(option.text(), characters);
            }
            hanzi(candidate.value().stem(), characters);
        }
        Set<String> stems = new HashSet<>();
        List<LooseStem> looseStems = new ArrayList<>();
        repository.existingStems(prompt.knowledgePointId()).forEach(existing -> {
            stems.add(fingerprint(existing.stem()));
            looseStems.add(new LooseStem(loose(existing.stem()), loose(storedCorrectText(existing.optionsJson()))));
        });
        Set<String> existingKids = errorKids.isEmpty() ? Set.of() : repository.existingKidCodes(errorKids);
        Set<String> allowed = characters.isEmpty() ? Set.of()
                : repository.allowedCharacters(characters, prompt.hskLevel(), prompt.knowledgePointId());
        List<Meaning> meanings = new ArrayList<>();
        if (semantic.enabled()) {
            semantic.existing().forEach(stored -> meanings.add(new Meaning(stored.vector(),
                    loose(storedCorrectText(stored.optionsJson())), hanziOf(stored.stem()))));
        }
        List<ValidatedQuestion> accepted = new ArrayList<>();
        // At most a third of a batch may be true/false; the share is counted over the rows the batch may keep.
        int rows = index;
        int trueFalse = 0;
        for (IndexedQuestion candidate : candidates) {
            ValidatedQuestion value = candidate.value();
            String reason = null;
            if (value.options().stream().anyMatch(option -> option.errorKid() != null
                    && !existingKids.contains(option.errorKid()))) reason = "UNKNOWN_ERROR_KID";
            String stem = fingerprint(value.stem());
            if (reason == null && stems.contains(stem)) reason = "DUPLICATE_STEM";
            LooseStem looseStem = new LooseStem(loose(value.stem()), loose(correctText(value)));
            if (reason == null && nearDuplicate(looseStem, looseStems)) reason = "NEAR_DUPLICATE_STEM";
            Set<String> used = new HashSet<>();
            hanzi(value.stem(), used);
            value.options().forEach(option -> hanzi(option.text(), used));
            if (reason == null && !allowed.containsAll(used)) reason = "HANZI_ABOVE_LEVEL";
            Meaning meaning = reason == null && semantic.enabled()
                    ? new Meaning(semantic.vectorOf(value.stem()), looseStem.answer(), hanziOf(value.stem())) : null;
            if (meaning != null && sameMeaning(meaning, meanings, semantic.threshold())) reason = "SEMANTIC_DUPLICATE_STEM";
            if (reason == null && accepted.size() >= limit) reason = "OVER_QUOTA";
            if (reason == null && value.type() == QuestionType.TRUE_FALSE
                    && trueFalse >= Math.min(limit, rows) / 3) reason = "TYPE_QUOTA";
            if (reason == null) {
                stems.add(stem);
                looseStems.add(looseStem);
                if (meaning != null) meanings.add(meaning);
                if (value.type() == QuestionType.TRUE_FALSE) trueFalse++;
                accepted.add(value);
            } else dropped.add(new Dropped(candidate.index(), reason));
        }
        dropped.sort(Comparator.comparingInt(Dropped::index));
        return new ValidationResult(accepted, dropped);
    }

    private ValidatedQuestion question(JsonNode question) {
        if (!question.isObject()) throw invalid("INVALID_QUESTION_SHAPE");
        String stem = required(question.get("stem"), 500, "INVALID_STEM");
        QuestionType type = type(question.get("type"));
        Set<String> allowedKeys = type == QuestionType.TRUE_FALSE ? TRUE_FALSE_KEYS : KEYS;
        JsonNode rawOptions = question.get("options");
        if (rawOptions == null || !rawOptions.isArray() || rawOptions.size() != allowedKeys.size()) {
            throw invalid("INVALID_OPTIONS");
        }
        List<QuestionOption> options = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        Set<String> texts = new HashSet<>();
        for (JsonNode option : rawOptions) {
            if (!option.isObject() || !option.path("key").isTextual() || !option.path("isCorrect").isBoolean()) {
                throw invalid("INVALID_OPTIONS");
            }
            String key = option.get("key").textValue();
            String text = required(option.get("text"), 200, "INVALID_OPTIONS");
            if (!allowedKeys.contains(key) || !keys.add(key) || !texts.add(fingerprint(text))) throw invalid("INVALID_OPTIONS");
            options.add(new QuestionOption(key, text, option.get("isCorrect").booleanValue(), null, null));
        }
        if (type == QuestionType.TRUE_FALSE) {
            // Learners always see A as "Đúng" and B as "Sai", so a swapped or foreign label would grade backwards.
            for (int i = 0; i < options.size(); i++) {
                QuestionOption option = options.get(i);
                String expected = option.key().equals("A") ? TRUE_LABEL : FALSE_LABEL;
                if (!sameLabel(option.text(), expected)) throw invalid("INVALID_OPTIONS");
                options.set(i, new QuestionOption(option.key(), expected, option.isCorrect(), null, null));
            }
        }
        if (options.stream().filter(QuestionOption::isCorrect).count() != 1) throw invalid("INVALID_CORRECT_ANSWER");
        String explanation = required(question.get("explanation"), 1000, "INVALID_EXPLANATION");
        JsonNode difficulty = question.get("difficulty");
        if (difficulty == null || !difficulty.isIntegralNumber() || !difficulty.canConvertToInt()
                || difficulty.intValue() < 1 || difficulty.intValue() > 3) throw invalid("INVALID_DIFFICULTY");
        List<QuestionOption> checked = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            JsonNode raw = rawOptions.get(i);
            QuestionOption option = options.get(i);
            String kid = optional(raw.get("errorKid"));
            if (kid != null) kid = kid.toUpperCase(Locale.ROOT);
            String note = optional(raw.get("errorNote"));
            if ((kid != null && option.isCorrect()) || (note != null && (kid == null || length(note) > 200))) {
                throw invalid("INVALID_ERROR_METADATA");
            }
            checked.add(new QuestionOption(option.key(), option.text(), option.isCorrect(), kid, note));
        }
        checked.sort(Comparator.comparing(QuestionOption::key));
        String correctKey = checked.stream().filter(QuestionOption::isCorrect).findFirst().orElseThrow().key();
        return new ValidatedQuestion(stem, checked, correctKey, explanation, difficulty.intValue(), type);
    }

    /** Missing type means a four-option question, as before true/false items were allowed. */
    private static QuestionType type(JsonNode value) {
        if (value == null || value.isNull()) return QuestionType.MULTIPLE_CHOICE;
        if (!value.isTextual()) throw invalid("INVALID_QUESTION_TYPE");
        return switch (value.textValue()) {
            case "MULTIPLE_CHOICE" -> QuestionType.MULTIPLE_CHOICE;
            case "TRUE_FALSE" -> QuestionType.TRUE_FALSE;
            default -> throw invalid("INVALID_QUESTION_TYPE");
        };
    }

    private static String required(JsonNode value, int maximum, String reason) {
        if (value == null || !value.isTextual()) throw invalid(reason);
        String result = text(value.textValue());
        if (result.isEmpty() || length(result) > maximum) throw invalid(reason);
        return result;
    }

    private static String optional(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw invalid("INVALID_ERROR_METADATA");
        String result = text(value.textValue());
        return result.isEmpty() ? null : result;
    }

    private static String text(String value) {
        return value == null ? "" : EDGE_SPACE.matcher(Normalizer.normalize(value, Normalizer.Form.NFC)).replaceAll("");
    }
    static String fingerprint(String value) { return SPACE.matcher(text(value)).replaceAll(" "); }
    /** Drops punctuation and spaces but keeps letter case, matching the exact-duplicate rule. */
    private static String loose(String value) { return PUNCTUATION_OR_SPACE.matcher(text(value)).replaceAll(""); }

    /** Correct option text of a stored question, or null when the options are absent or legacy-shaped. */
    private String storedCorrectText(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) return null;
        try {
            JsonNode options = mapper.readTree(optionsJson);
            if (options == null || !options.isArray()) return null;
            for (JsonNode option : options) {
                if (option.path("is_correct").asBoolean(false) && option.path("text").isTextual()) {
                    return option.get("text").textValue();
                }
            }
        } catch (JsonProcessingException exception) {
            return null;
        }
        return null;
    }

    private static String correctText(ValidatedQuestion question) {
        return question.options().stream().filter(QuestionOption::isCorrect).findFirst().orElseThrow().text();
    }

    /**
     * Stems equal once punctuation and spaces are ignored are always duplicates. Merely similar stems are
     * duplicates only with the same correct answer, so drills that change just the tested word stay apart.
     */
    private static boolean nearDuplicate(LooseStem candidate, List<LooseStem> others) {
        if (candidate.stem().isEmpty()) return false;
        boolean comparable = length(candidate.stem()) >= NEAR_DUPLICATE_MIN_LENGTH && !candidate.answer().isEmpty();
        for (LooseStem other : others) {
            if (candidate.stem().equals(other.stem())) return true;
            if (comparable && candidate.answer().equals(other.answer())
                    && length(other.stem()) >= NEAR_DUPLICATE_MIN_LENGTH
                    && digits(candidate.stem()).equals(digits(other.stem()))
                    && sameItem(hanziOf(candidate.stem()), hanziOf(other.stem()))
                    && similarity(candidate.stem(), other.stem()) >= NEAR_DUPLICATE_THRESHOLD) return true;
        }
        return false;
    }

    /** Numbers usually carry meaning (7点 vs 8点), so stems with different digit runs are never merged. */
    private static String digits(String value) {
        StringBuilder result = new StringBuilder();
        value.codePoints().filter(Character::isDigit).forEach(result::appendCodePoint);
        return result.toString();
    }

    private record LooseStem(String stem, String answer) { }

    /** Sentence vector of a stem with its loosened correct answer and the Chinese characters of the stem. */
    private record Meaning(float[] vector, String answer, String hanzi) { }

    /**
     * Same meaning counts as a duplicate only with the same correct answer: a drill that keeps the sentence
     * but tests another word is a new question, while a reworded sentence testing the same answer is not.
     * When the Chinese part of either stem is short it is the tested item itself, so it must also match:
     * "chữ 件 thuộc bộ nào?" and "chữ 今 thuộc bộ nào?" share their answer and wording but are two questions.
     */
    private static boolean sameMeaning(Meaning candidate, List<Meaning> others, double threshold) {
        if (candidate.answer() == null || candidate.answer().isEmpty()) return false;
        for (Meaning other : others) {
            if (candidate.answer().equals(other.answer()) && sameItem(candidate.hanzi(), other.hanzi())
                    && SemanticIndex.cosine(candidate.vector(), other.vector()) >= threshold) return true;
        }
        return false;
    }

    /** Jaccard similarity of the two strings' sets of adjacent code-point pairs. */
    static double similarity(String first, String second) {
        Set<String> left = bigrams(first);
        Set<String> right = bigrams(second);
        if (left.isEmpty() || right.isEmpty()) return first.equals(second) ? 1 : 0;
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        left.retainAll(right);
        return (double) left.size() / union.size();
    }

    private static Set<String> bigrams(String value) {
        int[] points = value.codePoints().toArray();
        Set<String> result = new HashSet<>();
        for (int i = 0; i + 1 < points.length; i++) result.add(new String(points, i, 2));
        return result;
    }
    private static int length(String value) { return value.codePointCount(0, value.length()); }
    private static void hanzi(String text, Set<String> result) {
        text.codePoints().filter(code -> Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN)
                .forEach(code -> result.add(new String(Character.toChars(code))));
    }
    private static boolean sameItem(String first, String second) {
        return first.equals(second) || Math.max(length(first), length(second)) > SHORT_ITEM_HANZI;
    }

    /** The stem's Chinese characters in reading order; empty when the stem has none. */
    private static String hanziOf(String text) {
        StringBuilder result = new StringBuilder();
        if (text != null) text.codePoints().filter(code -> Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN)
                .forEach(result::appendCodePoint);
        return result.toString();
    }
    private static InvalidQuestion invalid(String reason) { return new InvalidQuestion(reason); }

    private static final String TRUE_LABEL = "Đúng";
    private static final String FALSE_LABEL = "Sai";

    /** Ignores case, surrounding spaces and a trailing full stop or exclamation mark. */
    private static boolean sameLabel(String text, String expected) {
        String label = Normalizer.normalize(text, Normalizer.Form.NFC).strip().replaceAll("[.!。！]+$", "").strip();
        return label.toLowerCase(Locale.ROOT).equals(expected.toLowerCase(Locale.ROOT));
    }
    private static class InvalidQuestion extends RuntimeException {
        InvalidQuestion(String reason) { super(reason); }
    }
    private record IndexedQuestion(int index, ValidatedQuestion value) { }

    public record ValidatedQuestion(String stem, List<QuestionOption> options, String correctKey,
            String explanation, int difficulty, QuestionType type) {
        public ValidatedQuestion { options = List.copyOf(options); }
        @Override public String toString() { return "ValidatedQuestion[options=" + options.size() + "]"; }
    }
    public record Dropped(int index, String reason) { }
    public record ValidationResult(List<ValidatedQuestion> accepted, List<Dropped> dropped) {
        public ValidationResult { accepted = List.copyOf(accepted); dropped = List.copyOf(dropped); }
    }
    public static class InvalidOutputException extends RuntimeException {
        public InvalidOutputException() { super("INVALID_OUTPUT"); }
    }
}
