package com.cnhsk.learning.grading.web;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;

/** Learner DTOs contain no billing receipts, assignment or payout fields. */
public final class GradingRequestViews {
    private GradingRequestViews() { }

    @Schema(name = "GradingPolicy")
    public record Policy(int claimHours, int gradingHours, int minHanzi, int maxHanzi,
            int maxChars, int cost, String refundTerms) { }

    @Schema(name = "GradingRequestSummary")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Summary(@Schema(type = "integer", format = "int64") long id, String reference,
            String essayPreview, String status, Instant createdAt, Instant deadline, Instant completedAt, Integer score,
            @Schema(allowableValues = {"ESSAY", "SPEAKING"}) String mode) { }

    @Schema(name = "GradingRequestPage")
    public record Page(List<Summary> items, int page, int size, long totalElements) {
        public Page { items = List.copyOf(items); }
    }

    @Schema(name = "GradingRequest")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Detail(@Schema(type = "integer", format = "int64") long id, String reference,
            String status, String essay, String prompt, Integer hskLevel, Instant createdAt,
            Instant deadline, Instant completedAt, Result result, boolean refunded,
            @Schema(allowableValues = {"ESSAY", "SPEAKING"}) String mode, String audioUrl) {
        @Override public String toString() { return "GradingRequest[id=" + id + ", status=" + status + "]"; }
    }

    /**
     * Four 0–25 marks. A speaking request reuses the same slots: grammar = tones, vocabulary = initials and finals,
     * coherence = fluency and intonation, writing = clarity.
     */
    @Schema(name = "Criteria")
    public record Criteria(int grammar, int vocabulary, int coherence, int writing) { }

    /** Requests created before speaking existed carry no mode and are essays. */
    public static String mode(com.fasterxml.jackson.databind.JsonNode payload) {
        return "SPEAKING".equals(payload.path("mode").asText()) ? "SPEAKING" : "ESSAY";
    }

    public static String audioUrl(com.fasterxml.jackson.databind.JsonNode payload) {
        return payload.path("audioUrl").isTextual() ? payload.get("audioUrl").textValue() : null;
    }

    @Schema(name = "Correction")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Correction(String original, String corrected, String note) { }

    /** An error code the teacher confirmed, with what the learner needs to fix it. */
    @Schema(name = "TaggedError")
    public record TaggedError(String code, String nameVi, int severity, String fixHint) { }

    /**
     * Legacy grades fill {@code criteria}; rubric grades (R_NOI / R_VIET) fill {@code rubric}, {@code scores} and
     * {@code errors} instead, and {@code score} is then the rubric total on a 0–100 scale.
     */
    @Schema(name = "GradingResult")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Result(int score, Criteria criteria, String feedback, List<Correction> corrections,
            @Schema(allowableValues = {"R_NOI", "R_VIET"}) String rubric, Map<String, Integer> scores,
            List<TaggedError> errors) {
        public Result(int score, Criteria criteria, String feedback, List<Correction> corrections) {
            this(score, criteria, feedback, corrections, null, null, null);
        }

        public Result {
            corrections = List.copyOf(corrections);
            scores = scores == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(scores));
            errors = errors == null ? null : List.copyOf(errors);
        }
    }

    /** Reads a stored result; throws IllegalArgumentException when the row is not a valid grade. */
    public static Result storedResult(int score, String feedback, JsonNode stored, ObjectMapper mapper) {
        if (stored == null || !stored.path("corrections").isArray()) throw new IllegalArgumentException("corrections");
        List<Correction> corrections = mapper.convertValue(stored.get("corrections"),
                mapper.getTypeFactory().constructCollectionType(List.class, Correction.class));
        if (stored.path("rubric").isTextual()) {
            if (!stored.path("scores").isObject()) throw new IllegalArgumentException("scores");
            Map<String, Integer> scores = mapper.convertValue(stored.get("scores"),
                    mapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Integer.class));
            List<TaggedError> errors = stored.path("errors").isArray()
                    ? mapper.convertValue(stored.get("errors"),
                            mapper.getTypeFactory().constructCollectionType(List.class, TaggedError.class))
                    : List.of();
            return new Result(score, null, feedback, corrections, stored.get("rubric").textValue(), scores, errors);
        }
        if (!stored.path("criteria").isObject()) throw new IllegalArgumentException("criteria");
        return new Result(score, mapper.convertValue(stored.get("criteria"), Criteria.class), feedback, corrections);
    }
}
