package com.cnhsk.community.follow.web;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "FollowState")
public record FollowState(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean following,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long followerCount) {
}
