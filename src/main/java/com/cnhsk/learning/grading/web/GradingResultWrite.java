package com.cnhsk.learning.grading.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

@Schema(name = "GradingResultWrite", requiredProperties = {"criteria", "feedback", "corrections"})
public record GradingResultWrite(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Criteria criteria,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 5000) String feedback,
        @ArraySchema(maxItems = 50, schema = @Schema(implementation = Correction.class)) List<Correction> corrections) {
    public GradingResultWrite {
        if (criteria == null || !scoreInRange(criteria.grammar()) || !scoreInRange(criteria.vocabulary())
                || !scoreInRange(criteria.coherence()) || !scoreInRange(criteria.writing())) throw invalid();
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
            if (root == null || !root.isObject() || !root.path("criteria").isObject()
                    || !root.path("feedback").isTextual() || !root.path("corrections").isArray()) throw invalid();
            var values = root.get("criteria");
            var criteria = new Criteria(integer(values, "grammar"), integer(values, "vocabulary"),
                    integer(values, "coherence"), integer(values, "writing"));
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
            return new GradingResultWrite(criteria, root.get("feedback").textValue(), corrections);
        } catch (JsonProcessingException exception) { throw invalid(); }
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
    static ApiException invalid() {
        return new ApiException(ErrorCode.GRADING_INVALID_RESULT, HttpStatus.UNPROCESSABLE_ENTITY,
                "Điểm, nhận xét hoặc nội dung sửa lỗi không hợp lệ.", Map.of());
    }
    @Override public String toString() { return "GradingResultWrite[content=REDACTED]"; }
}
