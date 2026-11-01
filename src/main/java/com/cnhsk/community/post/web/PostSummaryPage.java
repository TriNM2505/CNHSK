package com.cnhsk.community.post.web;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public record PostSummaryPage(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PostSummary> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements) {
    public PostSummaryPage {
        items = List.copyOf(items);
    }
}
