package com.cnhsk.community.moderation.web;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReportReceived(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, _const = "true") boolean received) {
}
