package com.cnhsk.learning.knowledge;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.cnhsk.learning.knowledge.entity.QuestionType;

/** Pure validation shared by CSV import and question editing; callers supply existing KIDs. */
public final class QuestionRules {
    private static final Pattern EDGE_SPACE = Pattern.compile("^[\\s\\p{Z}]+|[\\s\\p{Z}]+$");
    private static final Pattern SPACE = Pattern.compile("[\\s\\p{Z}]+");

    private QuestionRules() { }

    public record Option(String key, String text, boolean isCorrect, String errorKid, String errorNote) { }

    public record Draft(QuestionType qtype, int hskLevel, int difficulty, String stem, List<Option> options,
            String correctKey, String explanation, String audioUrl, String imageUrl,
            String primaryKid, List<String> secondaryKids) {
        public Draft {
            options = options == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(options));
            secondaryKids = secondaryKids == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(secondaryKids));
        }
    }

    public record Violation(String field, String code, String message) { }
    public record Validation(Draft value, List<Violation> errors) {
        public Validation { errors = List.copyOf(errors); }
    }
    public record GradingSignature(QuestionType qtype, int optionCount, Set<String> optionKeys,
            Set<String> correctOptionKeys, String correctKey) {
        public GradingSignature {
            optionKeys = Set.copyOf(optionKeys);
            correctOptionKeys = Set.copyOf(correctOptionKeys);
        }
    }

    public static Validation validate(Draft candidate, Set<String> existingKids) {
        Draft value = normalized(candidate);
        Map<String, Violation> errors = new LinkedHashMap<>();
        required(errors, "stem", value.stem(), 2000);
        required(errors, "explanation", value.explanation(), 3000);
        if (value.hskLevel() < 1 || value.hskLevel() > 9) {
            error(errors, "hskLevel", "INVALID_HSK_LEVEL", "Cấp HSK phải từ 1 đến 9");
        }
        if (value.difficulty() < 1 || value.difficulty() > 3) {
            error(errors, "difficulty", "INVALID_VALUE", "Độ khó phải từ 1 đến 3");
        }
        url(errors, "audioUrl", value.audioUrl());
        url(errors, "imageUrl", value.imageUrl());
        if (value.primaryKid().isEmpty()) error(errors, "primaryKid", "REQUIRED_MISSING", "Thiếu KID chính");
        else if (!existingKids.contains(value.primaryKid())) {
            error(errors, "primaryKid", "REFERENCE_NOT_FOUND", "KID chính chưa tồn tại — nhập mẫu Nút cây năng lực trước");
        }
        if (value.secondaryKids().size() > 2) {
            error(errors, "secondaryKids", "TOO_MANY_SECONDARY_KIDS", "Một câu có tối đa 2 KID phụ");
        }
        Set<String> labels = new HashSet<>();
        labels.add(value.primaryKid());
        for (String kid : value.secondaryKids()) {
            if (!labels.add(kid)) error(errors, "secondaryKids", "DUPLICATE_KID", "Nút bị lặp hoặc nút phụ trùng nút chính");
            if (!existingKids.contains(kid)) error(errors, "secondaryKids", "REFERENCE_NOT_FOUND", "KID phụ chưa tồn tại");
        }

        if (value.qtype() == null || value.qtype() == QuestionType.ESSAY) {
            error(errors, "qtype", "INVALID_VALUE", value.qtype() == QuestionType.ESSAY
                    ? "Chưa hỗ trợ câu tự luận" : "Loại câu hỏi không hợp lệ");
            return new Validation(value, List.copyOf(errors.values()));
        }
        List<Option> options = value.options();
        boolean choice = choice(value.qtype());
        if (value.qtype() == QuestionType.FILL_BLANK) {
            if (!value.stem().contains("___")) error(errors, "stem", "INVALID_VALUE", "Câu điền chỗ trống phải có ___");
            if (!options.isEmpty()) error(errors, "options", "INVALID_VALUE", "Câu điền chỗ trống không có phương án");
            if (value.correctKey() == null || length(value.correctKey()) > 50) {
                error(errors, "correctKey", "INVALID_ANSWER", "Đáp án điền phải từ 1 đến 50 ký tự");
            }
        } else {
            int minimum = value.qtype() == QuestionType.SENTENCE_ORDER ? 3 : 2;
            int maximum = value.qtype() == QuestionType.TRUE_FALSE ? 2 : 6;
            if (options.size() < minimum || options.size() > maximum) {
                error(errors, "options", "INVALID_VALUE", "Số phương án phải từ " + minimum + " đến " + maximum);
            }
            Set<String> texts = new HashSet<>();
            for (int index = 0; index < options.size(); index++) {
                Option option = options.get(index);
                if (option == null || !option.key().equals(Character.toString('A' + index)) || index >= 6) {
                    error(errors, "options", "INVALID_VALUE", "Khoá phương án phải liên tiếp từ A đến F");
                }
                if (option != null && !texts.add(SPACE.matcher(option.text()).replaceAll(" "))) {
                    error(errors, "options", "INVALID_VALUE", "Nội dung phương án hoặc mảnh câu không được trùng sau chuẩn hoá");
                }
            }
            long correct = options.stream().filter(Objects::nonNull).filter(Option::isCorrect).count();
            if (value.qtype() == QuestionType.SENTENCE_ORDER) {
                String keys = options.stream().filter(Objects::nonNull).map(Option::key).sorted().collect(Collectors.joining());
                String answer = value.correctKey() == null ? "" : value.correctKey().chars().sorted()
                        .mapToObj(character -> Character.toString((char) character)).collect(Collectors.joining());
                if (!keys.equals(answer) || correct != 0) {
                    error(errors, "correctKey", "INVALID_ANSWER", "Thứ tự đúng phải dùng mỗi khoá phương án đúng một lần");
                }
            } else if (correct != 1) error(errors, "options", "INVALID_ANSWER", "Câu hỏi phải có đúng một đáp án đúng");
            if (value.qtype() == QuestionType.TRUE_FALSE && (options.size() != 2 || options.get(0) == null
                    || options.get(1) == null || !options.get(0).text().equals("Đúng") || !options.get(1).text().equals("Sai"))) {
                error(errors, "options", "INVALID_VALUE", "Câu đúng/sai dùng A: Đúng và B: Sai");
            }
        }
        if (value.qtype() == QuestionType.IMAGE_MATCH && value.imageUrl() == null) {
            error(errors, "imageUrl", "REQUIRED_MISSING", "Câu ghép hình cần đường dẫn ảnh");
        }
        for (int index = 0; index < options.size(); index++) {
            Option option = options.get(index);
            if (option == null) {
                error(errors, "options[" + index + "]", "INVALID_VALUE", "Phương án không hợp lệ");
                continue;
            }
            String field = "options[" + index + "]";
            required(errors, field + ".text", option.text(), 300);
            if (option.errorKid() != null && (!choice || option.isCorrect() || !existingKids.contains(option.errorKid()))) {
                error(errors, field + ".errorKid", "INVALID_VALUE", "Chỉ gắn KID lỗi có trong cây cho phương án sai của câu chọn đáp án");
            }
            if (option.errorNote() != null && (option.errorKid() == null || length(option.errorNote()) > 200)) {
                error(errors, field + ".errorNote", "INVALID_VALUE", "Ghi chú lỗi cần KID lỗi và tối đa 200 ký tự");
            }
        }
        return new Validation(value, List.copyOf(errors.values()));
    }

    public static GradingSignature gradingSignature(Draft candidate) {
        Draft value = normalized(candidate);
        Set<String> keys = value.options().stream().filter(Objects::nonNull).map(Option::key).collect(Collectors.toSet());
        Set<String> correct = value.options().stream().filter(Objects::nonNull).filter(Option::isCorrect)
                .map(Option::key).collect(Collectors.toSet());
        return new GradingSignature(value.qtype(), value.options().size(), keys, correct, value.correctKey());
    }

    private static Draft normalized(Draft value) {
        List<Option> options = value.options().stream().map(option -> option == null ? null : new Option(
                text(option.key()), text(option.text()), option.isCorrect(), optionalKid(option.errorKid()), optional(option.errorNote()))).toList();
        boolean keyed = value.qtype() == QuestionType.FILL_BLANK || value.qtype() == QuestionType.SENTENCE_ORDER;
        return new Draft(value.qtype(), value.hskLevel(), value.difficulty(), text(value.stem()), options,
                keyed ? optional(value.correctKey()) : null, text(value.explanation()), optional(value.audioUrl()),
                optional(value.imageUrl()), text(value.primaryKid()).toUpperCase(Locale.ROOT),
                value.secondaryKids().stream().map(kid -> text(kid).toUpperCase(Locale.ROOT)).toList());
    }

    private static boolean choice(QuestionType type) {
        return type == QuestionType.MULTIPLE_CHOICE || type == QuestionType.SENTENCE_MATCH || type == QuestionType.IMAGE_MATCH;
    }
    private static String text(String value) {
        return value == null ? "" : EDGE_SPACE.matcher(Normalizer.normalize(value, Normalizer.Form.NFC)).replaceAll("");
    }
    private static String optional(String value) { String text = text(value); return text.isEmpty() ? null : text; }
    private static String optionalKid(String value) { String text = optional(value); return text == null ? null : text.toUpperCase(Locale.ROOT); }
    private static int length(String value) { return value.codePointCount(0, value.length()); }
    private static void required(Map<String, Violation> errors, String field, String value, int maximum) {
        if (value.isEmpty()) error(errors, field, "REQUIRED_MISSING", "Thiếu giá trị bắt buộc");
        else if (length(value) > maximum) error(errors, field, "TOO_LONG", "Giá trị dài quá " + maximum + " ký tự");
    }
    private static void url(Map<String, Violation> errors, String field, String value) {
        if (value != null && (length(value) > 500 || (!value.startsWith("https://") && !value.startsWith("http://"))
                || SPACE.matcher(value).find())) error(errors, field, "INVALID_URL", "URL phải bắt đầu http:// hoặc https://, tối đa 500 ký tự, không khoảng trắng");
    }
    private static void error(Map<String, Violation> errors, String field, String code, String message) {
        errors.putIfAbsent(field, new Violation(field, code, message));
    }
}
