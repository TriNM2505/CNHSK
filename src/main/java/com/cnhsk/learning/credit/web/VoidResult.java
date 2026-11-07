package com.cnhsk.learning.credit.web;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "VoidResult")
public record VoidResult(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String batchId,
        @Schema(type = "integer", types = {"integer"}, minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        int voided) { }
