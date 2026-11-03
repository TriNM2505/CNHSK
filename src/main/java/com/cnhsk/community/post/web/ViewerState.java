package com.cnhsk.community.post.web;

import io.swagger.v3.oas.annotations.media.Schema;

public record ViewerState(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean liked,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean reported) { }
