package com.cnhsk.community.moderation.web;

import java.time.Instant;
import com.cnhsk.community.common.CommentStatus;
import com.cnhsk.community.common.UserRef;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ModerationComment(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long postId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, types = {"integer", "null"}, format = "int64") Long parentId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "integer", types = {"integer"}, minimum = "0", maximum = "3") short depth,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true) CommentStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String content,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UserRef author,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "date-time") Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UserRef moderatedBy,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, types = {"string", "null"}) String moderationReason) { }
