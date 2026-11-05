package com.cnhsk.community.moderation.web;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public record ModerationPostPage(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ModerationPostSummary> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements) {
    public ModerationPostPage { items = List.copyOf(items); }
}
