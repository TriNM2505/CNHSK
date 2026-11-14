package com.cnhsk.learning.credit.web;

import io.swagger.v3.oas.annotations.media.Schema;

public record LedgerTotals(
        @Schema(type = "integer", types = {"integer"}, format = "int64", requiredMode = Schema.RequiredMode.REQUIRED) long count,
        @Schema(type = "integer", types = {"integer"}, format = "int64", requiredMode = Schema.RequiredMode.REQUIRED) long credited,
        @Schema(type = "integer", types = {"integer"}, format = "int64", requiredMode = Schema.RequiredMode.REQUIRED) long debited) {}
