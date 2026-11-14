package com.cnhsk.learning.credit.web;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReconciliationIssue(
        @Schema(type = "integer", types = {"integer"}, format = "int64", requiredMode = Schema.RequiredMode.REQUIRED) long userId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String maskedEmail,
        @Schema(type = "integer", types = {"integer"}, requiredMode = Schema.RequiredMode.REQUIRED) int seq,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Kind kind) {
    public enum Kind { SEQ_GAP, BALANCE_CHAIN_BROKEN, FIRST_BALANCE_NOT_ZERO }
}
