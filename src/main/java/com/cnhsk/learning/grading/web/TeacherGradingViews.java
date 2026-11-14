package com.cnhsk.learning.grading.web;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/** Explicit teacher projections never expose a learner identity or a billing receipt. */
public final class TeacherGradingViews {
    private TeacherGradingViews() { }

    @Schema(name = "QueueItem")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record QueueItem(long id, String reference, int hanziCount, Integer hskLevel,
            Instant sentAt, Instant claimDeadline, int payoutCredits) { }

    @Schema(name = "QueuePage")
    public record QueuePage(List<QueueItem> items, int page, int size, long totalElements) {
        public QueuePage { items = List.copyOf(items); }
    }

    @Schema(name = "AssignedItem")
    public record AssignedItem(long id, String reference, int hanziCount, Instant assignedAt, Instant gradingDeadline) { }

    @Schema(name = "TeacherGradingDetail")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Detail(long id, String reference, String status, String essay, String prompt, Integer hskLevel,
            int hanziCount, Instant assignedAt, Instant gradingDeadline, int payoutCredits, GradingRequestViews.Result result) {
        @Override public String toString() { return "TeacherGradingDetail[id=" + id + ", status=" + status + "]"; }
    }
}
