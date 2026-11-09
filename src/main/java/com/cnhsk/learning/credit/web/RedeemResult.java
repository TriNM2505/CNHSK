package com.cnhsk.learning.credit.web;

import com.cnhsk.learning.credit.web.CreateBatchRequest.CardType;
import io.swagger.v3.oas.annotations.media.Schema;

public record RedeemResult(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CardType cardType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "integer", types = {"integer"}) int amount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "integer", types = {"integer"}, minimum = "0") int balance,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PlanStatus plan) { }
