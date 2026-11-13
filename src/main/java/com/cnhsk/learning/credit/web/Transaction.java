package com.cnhsk.learning.credit.web;

import java.time.Instant;
import com.cnhsk.learning.api.PaidFeature;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record Transaction(
        @Schema(type = "integer", types = {"integer"}, minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED) int seq,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TransactionType type,
        @Schema(type = "integer", types = {"integer"}, requiredMode = Schema.RequiredMode.REQUIRED) int amount,
        @Schema(type = "integer", types = {"integer"}, minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) int balanceBefore,
        @Schema(type = "integer", types = {"integer"}, minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) int balanceAfter,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, enumAsRef = true, nullable = true) PaidFeature featureCode,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, types = {"string", "null"}) String reason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "date-time") Instant createdAt) {
    @Schema(name = "TransactionType", enumAsRef = true)
    public enum TransactionType { TOPUP, DEDUCT, REFUND, ADJUSTMENT, TEACHER_PAYOUT, SUBSCRIPTION }
}
