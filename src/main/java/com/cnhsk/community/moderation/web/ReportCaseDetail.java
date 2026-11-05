package com.cnhsk.community.moderation.web;

import java.time.Instant;
import java.util.List;
import com.cnhsk.community.common.CaseStatus;
import com.cnhsk.community.common.UserRef;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReportCaseDetail(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true) CaseStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int reportCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Target target,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ReportEntryResponse> reports,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) UserRef handledBy,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, types = {"string", "null"}, format = "date-time") Instant handledAt,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, types = {"string", "null"}) String decisionNote,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt) {
    public ReportCaseDetail { reports = List.copyOf(reports); }

    @Schema(name = "ReportTarget", oneOf = {ReportedPost.class, ReportedComment.class},
            discriminatorProperty = "kind")
    public sealed interface Target permits ReportedPost, ReportedComment { }
}
