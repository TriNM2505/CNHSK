package com.cnhsk.community.comment.web;

import java.time.Instant;

import com.cnhsk.community.common.CommentStatus;
import com.cnhsk.community.common.UserRef;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/** Public comment projection without parent content or moderation metadata. */
@Schema(name = "Comment")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CommentResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long postId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long parentId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", maximum = "3") short depth,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CommentStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String content,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UserRef author,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant editedAt) {
}
