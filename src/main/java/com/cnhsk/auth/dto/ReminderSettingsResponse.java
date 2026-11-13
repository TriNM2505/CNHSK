package com.cnhsk.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Reminder settings matching spec 020's public response. */
@Schema(requiredProperties = {"reminderTime", "channels", "emailVerified", "configured", "timezone"})
public record ReminderSettingsResponse(
        @Schema(pattern = "^([01]\\d|2[0-3]):00$")
        String reminderTime,
        Channels channels,
        boolean emailVerified,
        boolean configured,
        @Schema(_const = "Asia/Ho_Chi_Minh")
        String timezone) {

    @Schema(requiredProperties = {"web", "email"})
    public record Channels(boolean web, boolean email) {
    }
}
