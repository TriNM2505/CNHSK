package com.cnhsk.community.post.web;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "LikeState")
public record LikeState(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean liked,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long likeCount) {
}
