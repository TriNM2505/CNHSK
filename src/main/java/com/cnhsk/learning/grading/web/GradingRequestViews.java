package com.cnhsk.learning.grading.web;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
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
            String essayPreview, String status, Instant createdAt, Instant deadline, Instant completedAt, Integer score) { }

    @Schema(name = "GradingRequestPage")
    public record Page(List<Summary> items, int page, int size, long totalElements) {
        public Page { items = List.copyOf(items); }
    }

    @Schema(name = "GradingRequest")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Detail(@Schema(type = "integer", format = "int64") long id, String reference,
            String status, String essay, String prompt, Integer hskLevel, Instant createdAt,
            Instant deadline, Instant completedAt, Result result, boolean refunded) {
        @Override public String toString() { return "GradingRequest[id=" + id + ", status=" + status + "]"; }
    }

    @Schema(name = "Criteria")
    public record Criteria(int grammar, int vocabulary, int coherence, int writing) { }

    @Schema(name = "Correction")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Correction(String original, String corrected, String note) { }

    @Schema(name = "GradingResult")
    public record Result(int score, Criteria criteria, String feedback, List<Correction> corrections) {
        public Result { corrections = List.copyOf(corrections); }
    }
}
