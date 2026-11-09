package com.cnhsk.learning.credit.web;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

public record PlanStatus(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PlanCode code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, types = {"string", "null"}, format = "date-time") Instant expiresAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "boolean", types = {"boolean"}) boolean active) {
    @Schema(name = "PlanCode", enumAsRef = true)
    public enum PlanCode { FREE, PREMIUM, PREMIUM_PLUS }
}
