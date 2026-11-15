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
    private static final Set<String> KEYS = Set.of("A", "B", "C", "D");
    private final WeakKnowledgeQueryRepository repository;
    private final ObjectMapper mapper;

    public AiQuestionValidator(WeakKnowledgeQueryRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    @Transactional(readOnly = true)
    public ValidationResult validate(String output, AiPromptBuilder.Prompt prompt) {
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
        repository.existingStems(prompt.knowledgePointId()).forEach(stem -> stems.add(fingerprint(stem)));
        Set<String> existingKids = errorKids.isEmpty() ? Set.of() : repository.existingKidCodes(errorKids);
        Set<String> allowed = characters.isEmpty() ? Set.of() : repository.allowedCharacters(characters, prompt.hskLevel());
        List<ValidatedQuestion> accepted = new ArrayList<>();
        for (IndexedQuestion candidate : candidates) {
            ValidatedQuestion value = candidate.value();
            String reason = null;
            if (value.options().stream().anyMatch(option -> option.errorKid() != null
                    && !existingKids.contains(option.errorKid()))) reason = "UNKNOWN_ERROR_KID";
            String stem = fingerprint(value.stem());
            if (reason == null && stems.contains(stem)) reason = "DUPLICATE_STEM";
            Set<String> used = new HashSet<>();
            hanzi(value.stem(), used);
            value.options().forEach(option -> hanzi(option.text(), used));
            if (reason == null && !allowed.containsAll(used)) reason = "HANZI_ABOVE_LEVEL";
            if (reason == null) {
                stems.add(stem);
                accepted.add(value);
            } else dropped.add(new Dropped(candidate.index(), reason));
        }
        dropped.sort(Comparator.comparingInt(Dropped::index));
        return new ValidationResult(accepted, dropped);
    }

    private ValidatedQuestion question(JsonNode question) {
        if (!question.isObject()) throw invalid("INVALID_QUESTION_SHAPE");
        String stem = required(question.get("stem"), 500, "INVALID_STEM");
        JsonNode rawOptions = question.get("options");
        if (rawOptions == null || !rawOptions.isArray() || rawOptions.size() != 4) throw invalid("INVALID_OPTIONS");
        List<QuestionOption> options = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        Set<String> texts = new HashSet<>();
        for (JsonNode option : rawOptions) {
            if (!option.isObject() || !option.path("key").isTextual() || !option.path("isCorrect").isBoolean()) {
                throw invalid("INVALID_OPTIONS");
            }
            String key = option.get("key").textValue();
            String text = required(option.get("text"), 200, "INVALID_OPTIONS");
            if (!KEYS.contains(key) || !keys.add(key) || !texts.add(fingerprint(text))) throw invalid("INVALID_OPTIONS");
            options.add(new QuestionOption(key, text, option.get("isCorrect").booleanValue(), null, null));
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
        return new ValidatedQuestion(stem, checked, correctKey, explanation, difficulty.intValue());
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
    private static String fingerprint(String value) { return SPACE.matcher(text(value)).replaceAll(" "); }
    private static int length(String value) { return value.codePointCount(0, value.length()); }
    private static void hanzi(String text, Set<String> result) {
        text.codePoints().filter(code -> Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN)
                .forEach(code -> result.add(new String(Character.toChars(code))));
    }
    private static InvalidQuestion invalid(String reason) { return new InvalidQuestion(reason); }
    private static class InvalidQuestion extends RuntimeException {
        InvalidQuestion(String reason) { super(reason); }
    }
    private record IndexedQuestion(int index, ValidatedQuestion value) { }

    public record ValidatedQuestion(String stem, List<QuestionOption> options, String correctKey,
            String explanation, int difficulty) {
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
