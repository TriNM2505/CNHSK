package com.cnhsk.learning.credit.web;

import com.cnhsk.learning.api.PaidFeature;
import io.swagger.v3.oas.annotations.media.Schema;

public record QuotaLine(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true) PaidFeature feature,
        @Schema(type = "integer", types = {"integer"}, minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) int limit,
        @Schema(type = "integer", types = {"integer"}, minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) int used,
        @Schema(type = "integer", types = {"integer"}, minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) int remaining) {}
