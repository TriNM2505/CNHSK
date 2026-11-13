package com.cnhsk.auth.dto;

import com.cnhsk.auth.reminder.UnsubscribeTokens;
import com.fasterxml.jackson.databind.JsonNode;

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
