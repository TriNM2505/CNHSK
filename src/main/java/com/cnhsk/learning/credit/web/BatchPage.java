package com.cnhsk.learning.credit.web;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "BatchPage")
public record BatchPage(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<BatchSummary> items,
        @Schema(type = "integer", types = {"integer"}, requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(type = "integer", types = {"integer"}, requiredMode = Schema.RequiredMode.REQUIRED) int size,
        @Schema(type = "integer", types = {"integer"}, format = "int64", requiredMode = Schema.RequiredMode.REQUIRED) long total) {
    public BatchPage { items = List.copyOf(items); }
}
