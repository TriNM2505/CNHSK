package com.cnhsk.community.post.web;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public record OwnPostPage(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<OwnPost> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements) {
    public OwnPostPage {
        items = List.copyOf(items);
    }
}
