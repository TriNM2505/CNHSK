package com.cnhsk.learning.grading.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.cnhsk.learning.grading.Rubric;
import com.cnhsk.learning.grading.web.GradingRequestViews.Criteria;
import com.cnhsk.learning.grading.web.GradingRequestViews.Correction;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import org.springframework.http.HttpStatus;

/**
 * A teacher's grade. Either the legacy four 0–25 criteria, or a rubric of the competency tree (R_NOI 0–3 per
 * C1–C5, R_VIET 0–5 per C1–C4) with the error codes the teacher confirmed.
 */
@Schema(name = "GradingResultWrite", requiredProperties = {"feedback", "corrections"})
public record GradingResultWrite(
        Criteria criteria,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 5000) String feedback,
        @ArraySchema(maxItems = 50, schema = @Schema(implementation = Correction.class)) List<Correction> corrections,
        @Schema(allowableValues = {"R_NOI", "R_VIET"}) Rubric rubric,
        Map<String, Integer> scores,
        @ArraySchema(maxItems = 20, schema = @Schema(pattern = "^E-(NA|CH|TV|NP|NL|VB|DI)-\\d{2}$")) List<String> errors) {

    private static final Pattern ERROR_CODE = Pattern.compile("E-(NA|CH|TV|NP|NL|VB|DI)-\\d{2}");

    public GradingResultWrite(Criteria criteria, String feedback, List<Correction> corrections) {
        this(criteria, feedback, corrections, null, null, null);
    }

    public GradingResultWrite {
        if (rubric == Rubric.LEGACY) throw invalid();
        if (rubric == null) {
            if (criteria == null || !scoreInRange(criteria.grammar()) || !scoreInRange(criteria.vocabulary())
                    || !scoreInRange(criteria.coherence()) || !scoreInRange(criteria.writing())
                    || scores != null || errors != null && !errors.isEmpty()) throw invalid();
            scores = null;
            errors = List.of();
        } else {
            if (criteria != null || scores == null || scores.size() != rubric.criteria().size()) throw invalid();
            for (String criterion : rubric.criteria()) {
                Integer value = scores.get(criterion);
                if (value == null || value < 0 || value > rubric.max()) throw invalid();
            }
            scores = new LinkedHashMap<>(scores);
            errors = errors == null ? List.of() : List.copyOf(errors);
            if (errors.size() > 20 || new LinkedHashSet<>(errors).size() != errors.size()
                    || errors.stream().anyMatch(code -> code == null || !ERROR_CODE.matcher(code).matches())) {
                throw invalid();
            }
        }
        requireText(feedback, 5000);
        if (corrections == null || corrections.size() > 50) throw invalid();
        for (Correction correction : corrections) {
            if (correction == null) throw invalid();
            requireText(correction.original(), 500); requireText(correction.corrected(), 500);
            if (correction.note() != null && length(correction.note()) > 500) throw invalid();
        }
        corrections = List.copyOf(corrections);
    }

    /** Local strict type checks avoid coercion; fields not in the write contract are ignored. */
    public static GradingResultWrite parse(String body, ObjectMapper mapper) {
        try {
            JsonNode root = body == null ? null : mapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(body);
            if (root == null || !root.isObject() || !root.path("feedback").isTextual()
                    || !root.path("corrections").isArray()) throw invalid();
            Criteria criteria = null;
            Rubric rubric = null;
            Map<String, Integer> scores = null;
            List<String> errors = null;
            if (root.hasNonNull("rubric")) {
                rubric = rubric(root.get("rubric"));
                if (root.hasNonNull("criteria") || !root.path("scores").isObject()) throw invalid();
                scores = new LinkedHashMap<>();
                for (var field : root.get("scores").properties()) {
                    scores.put(field.getKey(), integer(root.get("scores"), field.getKey()));
                }
                JsonNode codes = root.get("errors");
                if (codes != null && !codes.isNull()) {
                    if (!codes.isArray() || codes.size() > 20) throw invalid();
                    errors = new ArrayList<>();
                    for (JsonNode code : codes) {
                        if (!code.isTextual()) throw invalid();
                        errors.add(code.textValue());
                    }
                }
            } else {
                if (!root.path("criteria").isObject()) throw invalid();
                var values = root.get("criteria");
                criteria = new Criteria(integer(values, "grammar"), integer(values, "vocabulary"),
                        integer(values, "coherence"), integer(values, "writing"));
            }
            if (root.get("corrections").size() > 50) throw invalid();
            List<Correction> corrections = new ArrayList<>();
            for (JsonNode correction : root.get("corrections")) {
                if (!correction.isObject() || !correction.path("original").isTextual()
                        || !correction.path("corrected").isTextual()) throw invalid();
                JsonNode note = correction.get("note");
                if (note != null && !note.isNull() && !note.isTextual()) throw invalid();
                corrections.add(new Correction(correction.get("original").textValue(), correction.get("corrected").textValue(),
                        note == null || note.isNull() ? null : note.textValue()));
            }
            return new GradingResultWrite(criteria, root.get("feedback").textValue(), corrections, rubric, scores, errors);
        } catch (JsonProcessingException exception) { throw invalid(); }
    }

    private static Rubric rubric(JsonNode value) {
        if (!value.isTextual()) throw invalid();
        return switch (value.textValue()) {
            case "R_NOI" -> Rubric.R_NOI;
            case "R_VIET" -> Rubric.R_VIET;
            default -> throw invalid();
        };
    }

    private static int integer(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) throw invalid();
        return value.intValue();
    }

    private static boolean scoreInRange(int score) { return score >= 0 && score <= 25; }
    private static int length(String value) { return value.codePointCount(0, value.length()); }
    private static void requireText(String value, int max) {
        if (value == null || length(value) > max
                || value.codePoints().allMatch(point -> Character.isWhitespace(point) || Character.isSpaceChar(point))) throw invalid();
    }
    public static ApiException invalid() {
        return new ApiException(ErrorCode.GRADING_INVALID_RESULT, HttpStatus.UNPROCESSABLE_ENTITY,
                "Điểm, nhận xét hoặc nội dung sửa lỗi không hợp lệ.", Map.of());
    }
    @Override public String toString() { return "GradingResultWrite[content=REDACTED]"; }
}
