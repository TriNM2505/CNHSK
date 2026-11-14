package com.cnhsk.learning.credit.web;

import java.time.Instant;
import java.util.regex.Pattern;
import com.cnhsk.learning.credit.web.Transaction.TransactionType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record LedgerRow(
        @Schema(type = "integer", types = {"integer"}, format = "int64", requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(type = "integer", types = {"integer"}, format = "int64", requiredMode = Schema.RequiredMode.REQUIRED) long userId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String maskedEmail,
        @Schema(type = "integer", types = {"integer"}, requiredMode = Schema.RequiredMode.REQUIRED) int seq,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TransactionType type,
        @Schema(type = "integer", types = {"integer"}, requiredMode = Schema.RequiredMode.REQUIRED) int amount,
        @Schema(type = "integer", types = {"integer"}, requiredMode = Schema.RequiredMode.REQUIRED) int balanceBefore,
        @Schema(type = "integer", types = {"integer"}, requiredMode = Schema.RequiredMode.REQUIRED) int balanceAfter,
        @Schema(types = {"string", "null"}, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String featureCode,
        @Schema(types = {"string", "null"}, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String reason,
        @Schema(type = "string", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt) {
    private static final Pattern SENSITIVE_TEXT = Pattern.compile(
            "(?i)[a-f0-9]{64}"
            + "|(?:[0-9a-hjkmnp-tv-zilo][\\s-]*){16}"
            + "|[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}"
            + "|(?<![0-9])(?:\\+84|0)[0-9 .-]{8,14}[0-9](?![0-9])"
            + "|eyJ[a-z0-9_-]+\\.[a-z0-9_-]+\\.[a-z0-9_-]+"
            + "|(?:password|secret|token|api[_ -]?key)\\s*[:=]");

    public LedgerRow {
        featureCode = safeText(featureCode);
        reason = safeText(reason);
    }

    private static String safeText(String text) {
        return text == null || !SENSITIVE_TEXT.matcher(text).find() ? text : null;
    }
}
