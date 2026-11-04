package com.cnhsk.learning.content.importer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.cnhsk.learning.content.text.TextNormalizer;
import com.cnhsk.learning.knowledge.QuestionRules.Draft;
import com.cnhsk.learning.knowledge.QuestionRules.Option;
import com.cnhsk.learning.knowledge.QuestionRules.Violation;
import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.entity.QuestionType;

/** CSV conversion only; domain validation remains in QuestionRules. No persistence is performed here. */
public final class QuestionImportSupport {
    private QuestionImportSupport() { }

    public record Parsed(Draft value, List<Violation> errors) {
        public Parsed { errors = List.copyOf(errors); }
    }

    public static Map<String, String> cells(Map<String, String> input) {
        Map<String, String> values = new LinkedHashMap<>();
        input.forEach((field, value) -> values.put(field, TextNormalizer.normalizeCell(field, value)));
        if (values.containsKey("kid_primary")) values.put("kid_primary", kid(values.get("kid_primary")));
        if (values.containsKey("kid_secondary")) values.put("kid_secondary", String.join("|", secondary(values.get("kid_secondary"))));
        return values;
    }

    public static Set<String> references(Map<String, String> values) {
        Set<String> kids = new LinkedHashSet<>();
        kids.add(kid(values.get("kid_primary")));
        kids.addAll(secondary(values.get("kid_secondary")));
        for (String item : text(values.get("distractor_errors")).split("\\|", -1)) {
            String[] parts = item.split(":", 3);
            if (parts.length >= 2) kids.add(kid(parts[1]));
        }
        kids.remove("");
        return kids;
    }

    public static Parsed parse(Map<String, String> values, Question existing) {
        List<Violation> errors = new ArrayList<>();
        QuestionType type = null;
        try { type = QuestionType.valueOf(text(values.get("qtype"))); }
        catch (IllegalArgumentException exception) { errors.add(new Violation("qtype", "INVALID_VALUE", "Loại câu hỏi không hợp lệ")); }
        String answer = text(values.get("correct"));
        List<Option> options = new ArrayList<>();
        if (type == QuestionType.TRUE_FALSE || type == QuestionType.FILL_BLANK) {
            for (char key = 'a'; key <= 'f'; key++) {
                if (present(values.get("option_" + key))) errors.add(new Violation("option_" + key,
                        "INVALID_VALUE", "Loại câu này không nhận cột phương án"));
            }
            if (type == QuestionType.TRUE_FALSE) {
                options.add(new Option("A", "Đúng", answer.equals("TRUE"), null, null));
                options.add(new Option("B", "Sai", answer.equals("FALSE"), null, null));
            }
        } else {
            for (char key = 'A'; key <= 'F'; key++) {
                String value = values.get("option_" + Character.toLowerCase(key));
                if (present(value)) options.add(new Option(Character.toString(key), value,
                        type != QuestionType.SENTENCE_ORDER && answer.equals(Character.toString(key)), null, null));
            }
        }
        Map<String, String[]> distractors = new HashMap<>();
        if (present(values.get("distractor_errors"))) {
            boolean choice = type == QuestionType.MULTIPLE_CHOICE || type == QuestionType.IMAGE_MATCH || type == QuestionType.SENTENCE_MATCH;
            String[] items = values.get("distractor_errors").split("\\|", -1);
            if (!choice || items.length > 6) errors.add(distractorError());
            for (String item : items) {
                String[] parts = item.split(":", 3);
                String key = parts.length == 0 ? "" : text(parts[0]);
                Option option = options.stream().filter(value -> value.key().equals(key)).findFirst().orElse(null);
                if (parts.length < 2 || kid(parts[1]).isEmpty() || option == null || option.isCorrect()
                        || distractors.putIfAbsent(key, parts) != null) errors.add(distractorError());
            }
        }
        options = options.stream().map(option -> {
            String[] parts = distractors.get(option.key());
            return parts == null ? option : new Option(option.key(), option.text(), option.isCorrect(), kid(parts[1]),
                    parts.length == 3 ? optional(parts[2]) : null);
        }).toList();
        String difficulty = values.get("difficulty");
        Draft draft = new Draft(type, integer(values.get("hsk_level"), 0), integer(difficulty,
                existing == null ? 2 : existing.getDifficulty()), text(values.get("stem")), options,
                type == QuestionType.FILL_BLANK || type == QuestionType.SENTENCE_ORDER ? optional(answer) : null,
                values.getOrDefault("explanation", ""), scalar(values.get("audio_url"), existing == null ? null : existing.getAudioUrl()),
                scalar(values.get("image_url"), existing == null ? null : existing.getImageUrl()),
                kid(values.get("kid_primary")), secondary(values.get("kid_secondary")));
        return new Parsed(draft, errors);
    }

    public static Draft stored(Question question, String primary, List<String> secondary) {
        List<Option> options = question.getOptions().stream().map(option -> new Option(option.key(),
                TextNormalizer.normalizeCell("option_a", option.text()), option.isCorrect(), optionalKid(option.errorKid()),
                optional(TextNormalizer.normalizeCell("distractor_errors", option.errorNote()))))
                .sorted(Comparator.comparing(Option::key)).toList();
        return new Draft(question.getQtype(), question.getHskLevel(), question.getDifficulty(),
                TextNormalizer.normalizeCell("stem", question.getStem()), options, optional(question.getCorrectKey()),
                TextNormalizer.normalizeCell("explanation", question.getExplanation()), optional(question.getAudioUrl()),
                optional(question.getImageUrl()), kid(primary), secondary);
    }

    public static boolean sameQuestion(Question question, Draft candidate) {
        Draft old = stored(question, "", List.of());
        return old.qtype() == candidate.qtype() && old.hskLevel() == candidate.hskLevel() && old.difficulty() == candidate.difficulty()
                && Objects.equals(old.stem(), candidate.stem()) && Objects.equals(old.options(), candidate.options())
                && Objects.equals(old.correctKey(), candidate.correctKey()) && Objects.equals(old.explanation(), candidate.explanation())
                && Objects.equals(old.audioUrl(), candidate.audioUrl()) && Objects.equals(old.imageUrl(), candidate.imageUrl());
    }

    public static String column(Violation error) {
        return switch (error.field()) {
            case "hskLevel" -> "hsk_level";
            case "audioUrl" -> "audio_url";
            case "imageUrl" -> "image_url";
            case "primaryKid" -> "kid_primary";
            case "secondaryKids" -> "kid_secondary";
            case "correctKey" -> "correct";
            case "options" -> error.code().equals("INVALID_ANSWER") ? "correct" : "option_a";
            default -> {
                if (error.field().startsWith("options[")) {
                    if (error.field().endsWith(".errorKid") || error.field().endsWith(".errorNote")) yield "distractor_errors";
                    int index = Integer.parseInt(error.field().substring(8, error.field().indexOf(']')));
                    yield "option_" + (char) ('a' + index);
                }
                yield error.field();
            }
        };
    }

    private static Violation distractorError() {
        return new Violation("distractor_errors", "INVALID_VALUE", "Tối đa 6 mục KEY:KID[:ghi chú], mỗi khoá sai có nội dung chỉ xuất hiện một lần");
    }
    private static List<String> secondary(String value) {
        return present(value) ? java.util.Arrays.stream(value.split("\\|", -1)).map(QuestionImportSupport::kid).toList() : List.of();
    }
    private static int integer(String value, int fallback) {
        if (!present(value)) return fallback;
        try { return Integer.parseInt(value); } catch (NumberFormatException exception) { return 0; }
    }
    private static String scalar(String incoming, String previous) { return present(incoming) ? incoming : optional(previous); }
    private static String text(String value) { return value == null ? "" : TextNormalizer.normalizeCell("value", value); }
    private static String optional(String value) { String result = text(value); return result.isEmpty() ? null : result; }
    private static String kid(String value) { return text(value).toUpperCase(Locale.ROOT); }
    private static String optionalKid(String value) { String result = kid(value); return result.isEmpty() ? null : result; }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
}
