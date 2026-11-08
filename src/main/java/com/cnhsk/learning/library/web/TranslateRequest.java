package com.cnhsk.learning.library.web;

import java.util.Map;

import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

@Schema(name = "TranslateRequest")
public record TranslateRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 2000) String text,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Language from,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Language to) {
    public enum Language { ZH, VI }

    public TranslateRequest {
        if (text == null || from == null || to == null || from == to) throw invalid();
        text = text.strip();
        if (text.isEmpty() || text.codePointCount(0, text.length()) > 2000) throw invalid();
    }

    /** Parse manually so malformed JSON and enums also follow the 422 contract. */
    public static TranslateRequest validate(String body, ObjectMapper mapper) {
        try {
            JsonNode root = body == null ? null : mapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(body);
            if (root == null || !root.isObject() || !root.path("text").isTextual()
                    || !root.path("from").isTextual() || !root.path("to").isTextual()) throw invalid();
            try {
                return new TranslateRequest(root.path("text").textValue(),
                        Language.valueOf(root.path("from").textValue()), Language.valueOf(root.path("to").textValue()));
            } catch (IllegalArgumentException exception) { throw invalid(); }
        } catch (JsonProcessingException exception) { throw invalid(); }
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Nhập nội dung 1–2.000 ký tự và chọn hai ngôn ngữ ZH, VI khác nhau.", Map.of());
    }

    @Override public String toString() { return "TranslateRequest[text=[REDACTED], from=" + from + ", to=" + to + "]"; }
}
