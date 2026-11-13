package com.cnhsk.learning.credit.web;

import java.time.Instant;
import java.util.List;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

public record Wallet(
        @Schema(type = "integer", types = {"integer"}, minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) int balance,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PlanStatus plan,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @ArraySchema(minItems = 4, maxItems = 4) List<QuotaLine> quota,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "date-time") Instant resetAt) {
    public Wallet { quota = List.copyOf(quota); }
}
