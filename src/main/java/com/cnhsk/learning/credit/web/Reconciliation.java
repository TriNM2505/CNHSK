package com.cnhsk.learning.credit.web;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public record Reconciliation(
        @Schema(type = "integer", types = {"integer"}, format = "int64", requiredMode = Schema.RequiredMode.REQUIRED) long users,
        @Schema(type = "integer", types = {"integer"}, format = "int64", requiredMode = Schema.RequiredMode.REQUIRED) long rows,
        @Schema(type = "integer", types = {"integer"}, format = "int64", requiredMode = Schema.RequiredMode.REQUIRED) long totalBalance,
        @Schema(type = "integer", types = {"integer"}, format = "int64", requiredMode = Schema.RequiredMode.REQUIRED) long totalAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ReconciliationIssue> issues) {
    public Reconciliation { issues = List.copyOf(issues); }
}
