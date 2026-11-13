package com.cnhsk.auth.dto;

import com.cnhsk.auth.reminder.UnsubscribeTokens;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;

@SchemaProperty(name = "token", schema = @Schema(type = "string", minLength = 20, maxLength = 512))
public record ReminderUnsubscribeRequest(String token) {

    /** Do not allow Jackson to coerce a number, boolean or collection into a token string. */
    public static ReminderUnsubscribeRequest parse(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw UnsubscribeTokens.invalid();
        }
        JsonNode token = body.get("token");
        if (token != null && !token.isTextual()) {
            throw UnsubscribeTokens.invalid();
        }
        return new ReminderUnsubscribeRequest(token == null ? null : token.textValue());
    }
}
