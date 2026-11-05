package com.cnhsk.community.moderation.web;

import java.time.Instant;
import com.cnhsk.community.common.PostCategory;
import com.cnhsk.community.common.PostStatus;
import com.cnhsk.community.common.UserRef;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ModerationPost(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String content,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true) PostCategory category,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true) PostStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UserRef author,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, types = {"string", "null"}) String reviewReason,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) UserRef reviewedBy,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, types = {"string", "null"}, format = "date-time") Instant reviewedAt,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, types = {"string", "null"}, format = "date-time") Instant publishedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt) { }
