package com.cnhsk.community.moderation.web;

import com.cnhsk.community.common.CommentStatus;
import com.cnhsk.community.common.UserRef;
import io.swagger.v3.oas.annotations.media.Schema;

public record ReportedComment(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, _const = "COMMENT") String kind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String content,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true) CommentStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UserRef author,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long postId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String postTitle) implements ReportCaseDetail.Target { }
