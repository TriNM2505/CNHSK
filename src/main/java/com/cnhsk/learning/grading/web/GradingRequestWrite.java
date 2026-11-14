package com.cnhsk.learning.grading.web;

import java.util.Map;

import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

@Schema(name = "GradingRequestWrite")
public record GradingRequestWrite(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 3000) String essay,
        @Schema(types = {"string", "null"}, maxLength = 500) String prompt,
        @Schema(type = "integer", types = {"integer", "null"}, minimum = "1", maximum = "9") Integer hskLevel) {
    public GradingRequestWrite {
        if (essay == null || (prompt != null && prompt.codePointCount(0, prompt.length()) > 500)
                || (hskLevel != null && (hskLevel < 1 || hskLevel > 9))) throw invalid();
    }

    /** Parse locally so wrong JSON types follow the 422 contract without changing global mappers. */
    public static GradingRequestWrite parse(String body, ObjectMapper mapper) {
        try {
            JsonNode root = body == null ? null : mapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(body);
            if (root == null || !root.isObject() || !root.path("essay").isTextual()) throw invalid();
            JsonNode prompt = root.get("prompt");
            JsonNode level = root.get("hskLevel");
            if (prompt != null && !prompt.isNull() && !prompt.isTextual()) throw invalid();
            if (level != null && !level.isNull() && (!level.isIntegralNumber() || !level.canConvertToInt())) throw invalid();
            return new GradingRequestWrite(root.get("essay").textValue(),
                    prompt == null || prompt.isNull() ? null : prompt.textValue(),
                    level == null || level.isNull() ? null : level.intValue());
        } catch (JsonProcessingException exception) { throw invalid(); }
    }

    static ApiException invalid() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Bài viết, đề bài hoặc cấp HSK không hợp lệ.", Map.of());
    }

    @Override public String toString() { return "GradingRequestWrite[content=REDACTED]"; }
}
