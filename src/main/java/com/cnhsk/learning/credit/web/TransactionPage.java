package com.cnhsk.learning.credit.web;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public record TransactionPage(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Transaction> items,
        @Schema(type = "integer", types = {"integer"}, requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(type = "integer", types = {"integer"}, requiredMode = Schema.RequiredMode.REQUIRED) int size,
        @Schema(type = "integer", types = {"integer"}, format = "int64", requiredMode = Schema.RequiredMode.REQUIRED) long total) {
    public TransactionPage { items = List.copyOf(items); }
}
