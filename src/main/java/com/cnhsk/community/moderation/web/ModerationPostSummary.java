package com.cnhsk.community.moderation.web;

import java.time.Instant;
import com.cnhsk.community.common.PostCategory;
import com.cnhsk.community.common.UserRef;
import io.swagger.v3.oas.annotations.media.Schema;

public record ModerationPostSummary(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true) PostCategory category,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String excerpt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UserRef author,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt) { }
