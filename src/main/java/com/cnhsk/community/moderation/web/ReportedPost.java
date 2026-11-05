package com.cnhsk.community.moderation.web;

import com.cnhsk.community.common.PostCategory;
import com.cnhsk.community.common.PostStatus;
import com.cnhsk.community.common.UserRef;
import io.swagger.v3.oas.annotations.media.Schema;

public record ReportedPost(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, _const = "POST") String kind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String content,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true) PostCategory category,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true) PostStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UserRef author) implements ReportCaseDetail.Target { }
