package com.cnhsk.learning.credit.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "PlanView")
public record PlanView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PlanStatus.PlanCode code,
        @Schema(type = "boolean", types = {"boolean"}, requiredMode = Schema.RequiredMode.REQUIRED) boolean exists,
        @Schema(types = {"string", "null"}) String name,
        @Schema(types = {"string", "null"}) String description,
        @Schema(type = "integer", types = {"integer", "null"}) Integer durationDays,
        @Schema(types = {"string", "null"}, pattern = "^[0-9]{1,10}(\\.[0-9]{1,2})?$",
                description = "Giá niêm yết (NUMERIC 12,2), không phải tiền đã thu") String price,
        @Schema(implementation = Benefits.CatalogSchema.class) Benefits benefits,
        @Schema(type = "boolean", types = {"boolean", "null"}) Boolean isActive,
        @Schema(type = "integer", types = {"integer"}, format = "int64",
                requiredMode = Schema.RequiredMode.REQUIRED) long activeUsers) {
}
