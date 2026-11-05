package com.cnhsk.community.moderation.web;

import java.time.Instant;
import com.cnhsk.community.common.ReportReason;
import com.cnhsk.community.common.UserRef;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "ReportEntry")
public record ReportEntryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UserRef reporter,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true) ReportReason reason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, types = {"string", "null"}) String detail,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant reportedAt) { }
