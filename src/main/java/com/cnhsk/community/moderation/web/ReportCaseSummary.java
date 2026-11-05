package com.cnhsk.community.moderation.web;

import java.time.Instant;
import com.cnhsk.community.common.ReportReason;
import io.swagger.v3.oas.annotations.media.Schema;

public record ReportCaseSummary(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true) ReportWrite.ReportTargetType targetType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long targetId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int reportCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true) ReportReason latestReason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 120) String targetPreview) { }
