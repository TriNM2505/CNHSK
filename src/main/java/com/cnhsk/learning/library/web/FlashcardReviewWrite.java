package com.cnhsk.learning.library.web;

import com.cnhsk.learning.library.DictionaryService;
import com.cnhsk.learning.review.FsrsScheduler.Rating;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ReviewRequest")
public record FlashcardReviewWrite(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"AGAIN", "HARD", "GOOD", "EASY"}) String rating) {

    /** Validate before counting; malformed JSON also follows the existing 422 error format. */
    public static Rating validate(String body, ObjectMapper mapper) {
        try {
            JsonNode root = body == null ? null : mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(body);
            if (root == null || !root.isObject() || !root.path("rating").isTextual()) throw invalid();
            try { return Rating.valueOf(root.path("rating").textValue()); }
            catch (IllegalArgumentException exception) { throw invalid(); }
        } catch (JsonProcessingException exception) { throw invalid(); }
    }

    private static com.cnhsk.shared.exception.ApiException invalid() {
        return DictionaryService.invalid("rating", "Mức tự đánh giá phải là AGAIN, HARD, GOOD hoặc EASY.");
    }
}
