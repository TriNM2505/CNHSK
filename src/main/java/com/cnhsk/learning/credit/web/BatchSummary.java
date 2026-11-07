package com.cnhsk.learning.credit.web;

import java.time.Instant;
import com.cnhsk.learning.credit.web.CreateBatchRequest.CardType;
import com.cnhsk.learning.credit.web.CreateBatchRequest.Channel;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "BatchSummary")
public record BatchSummary(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String batchId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CardType cardType,
        @Schema(type = "integer", types = {"integer", "null"}) Integer value,
        @Schema(type = "integer", types = {"integer", "null"}) Integer planDays,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Channel channel,
        @Schema(types = {"string", "null"}) String partnerCode,
        @Schema(types = {"string", "null"}) String campaign,
        @Schema(types = {"string", "null"}) String issuedTo,
        @Schema(types = {"string", "null"}, format = "date-time") Instant expiresAt,
        @Schema(type = "integer", types = {"integer"}, requiredMode = Schema.RequiredMode.REQUIRED) int total,
        @Schema(type = "integer", types = {"integer"}, requiredMode = Schema.RequiredMode.REQUIRED) int unused,
        @Schema(type = "integer", types = {"integer"}, requiredMode = Schema.RequiredMode.REQUIRED) int used,
        @Schema(type = "integer", types = {"integer"}, requiredMode = Schema.RequiredMode.REQUIRED) int voided,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(types = {"string", "null"}) String createdByName) { }
