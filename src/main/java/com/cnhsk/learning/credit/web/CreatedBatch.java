package com.cnhsk.learning.credit.web;

import java.util.List;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CreatedBatch")
public record CreatedBatch(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String batchId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @ArraySchema(schema = @Schema(type = "string", pattern = "^[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}$"))
        List<String> codes) {
    public CreatedBatch { codes = List.copyOf(codes); }

    @Override
    public String toString() { return "CreatedBatch[batchId=" + batchId + ", codes=[REDACTED]]"; }
}
