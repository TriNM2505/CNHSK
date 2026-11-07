package com.cnhsk.learning.credit.web;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

@Schema(name = "CreateBatchRequest")
public record CreateBatchRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CardType cardType,
        @Schema(type = "integer", types = {"integer", "null"}, minimum = "1", maximum = "10000") Integer value,
        @Schema(type = "integer", types = {"integer", "null"}, minimum = "1", maximum = "365") Integer planDays,
        @Schema(type = "integer", types = {"integer"}, requiredMode = Schema.RequiredMode.REQUIRED,
                minimum = "1", maximum = "100") Integer quantity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Channel channel,
        @Schema(types = {"string", "null"}, maxLength = 50) String partnerCode,
        @Schema(types = {"string", "null"}, maxLength = 50) String campaign,
        @Schema(types = {"string", "null"}, maxLength = 255) String issuedTo,
        @Schema(types = {"string", "null"}, format = "date-time") Instant expiresAt) {

    @Schema(name = "CardType", enumAsRef = true)
    public enum CardType { CREDIT, SUBSCRIPTION }

    @Schema(name = "Channel", enumAsRef = true)
    public enum Channel { ECOSYSTEM_GIFT, PARTNER_BATCH, DIRECT }

    public static CreateBatchRequest parse(JsonNode body) {
        if (body == null || !body.isObject()) throw invalid();
        try {
            String type = text(body, "cardType");
            String channel = text(body, "channel");
            String expiry = text(body, "expiresAt");
            return new CreateBatchRequest(type == null ? null : CardType.valueOf(type),
                    integer(body, "value"), integer(body, "planDays"), integer(body, "quantity"),
                    channel == null ? null : Channel.valueOf(channel), text(body, "partnerCode"),
                    text(body, "campaign"), text(body, "issuedTo"),
                    expiry == null ? null : Instant.parse(expiry));
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw invalid();
        }
    }

    public void validate(Instant now) {
        if (cardType == null || channel == null || !range(quantity, 100)
                || (cardType == CardType.CREDIT && (!range(value, 10_000) || planDays != null))
                || (cardType == CardType.SUBSCRIPTION && (!range(planDays, 365) || value != null))
                || (channel == Channel.PARTNER_BATCH && blank(partnerCode))
                || (channel == Channel.ECOSYSTEM_GIFT && blank(issuedTo))
                || tooLong(partnerCode, 50) || tooLong(campaign, 50) || tooLong(issuedTo, 255)
                || (expiresAt != null && !expiresAt.isAfter(now))) throw invalid();
    }

    private static boolean range(Integer value, int max) {
        return value != null && value >= 1 && value <= max;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private static boolean tooLong(String value, int max) {
        return value != null && (value.indexOf('\0') >= 0 || value.codePointCount(0, value.length()) > max);
    }

    private static String text(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw invalid();
        return value.textValue();
    }

    private static Integer integer(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isIntegralNumber() || !value.canConvertToInt()) throw invalid();
        return value.intValue();
    }

    public static ApiException invalid() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Dữ liệu không hợp lệ.", Map.of());
    }
}
