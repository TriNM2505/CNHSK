package com.cnhsk.community.moderation.web;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public record ReportCasePage(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ReportCaseSummary> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements) {
    public ReportCasePage { items = List.copyOf(items); }
}
