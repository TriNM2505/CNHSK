package com.cnhsk.learning.speaking.web;

import java.util.Map;

import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

/** A learner's recording of one word or sentence, already uploaded to the speaking folder. */
@Schema(name = "SpeakingSubmissionWrite", requiredProperties = {"text", "audioUrl", "publicId"})
public record SpeakingSubmissionWrite(
        @Schema(minLength = 1, maxLength = 200) String text,
        @Schema(types = {"string", "null"}, maxLength = 300) String pinyin,
        @Schema(types = {"string", "null"}, maxLength = 300) String meaning,
        @Schema(maxLength = 2000) String audioUrl,
        @Schema(maxLength = 255) String publicId,
        @Schema(type = "integer", types = {"integer", "null"}, minimum = "1", maximum = "9") Integer hskLevel) {

    public SpeakingSubmissionWrite {
        if (blank(text) || length(text) > 200 || tooLong(pinyin, 300) || tooLong(meaning, 300)
                || blank(audioUrl) || audioUrl.length() > 2000 || blank(publicId) || publicId.length() > 255
                || (hskLevel != null && (hskLevel < 1 || hskLevel > 9))) throw invalid();
        text = text.strip();
    }

    /** Strict local parsing so wrong JSON types follow the 422 contract. */
    public static SpeakingSubmissionWrite parse(String body, ObjectMapper mapper) {
        try {
            JsonNode root = body == null ? null : mapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(body);
            if (root == null || !root.isObject() || !root.path("text").isTextual()
                    || !root.path("audioUrl").isTextual() || !root.path("publicId").isTextual()) throw invalid();
            JsonNode level = root.get("hskLevel");
            if (level != null && !level.isNull() && (!level.isIntegralNumber() || !level.canConvertToInt())) throw invalid();
            return new SpeakingSubmissionWrite(root.get("text").textValue(), optional(root, "pinyin"),
                    optional(root, "meaning"), root.get("audioUrl").textValue(), root.get("publicId").textValue(),
                    level == null || level.isNull() ? null : level.intValue());
        } catch (JsonProcessingException exception) { throw invalid(); }
    }

    private static String optional(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw invalid();
        return value.textValue();
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static int length(String value) { return value.codePointCount(0, value.length()); }
    private static boolean tooLong(String value, int max) { return value != null && length(value) > max; }

    public static ApiException invalid() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Bài nói không hợp lệ, hãy ghi âm và gửi lại.", Map.of());
    }

    @Override public String toString() { return "SpeakingSubmissionWrite[content=REDACTED]"; }
}
