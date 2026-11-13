package com.cnhsk.auth.dto;

/** Reminder settings matching spec 020's public response. */
public record ReminderSettingsResponse(
        String reminderTime,
        Channels channels,
        boolean emailVerified,
        boolean configured,
        String timezone) {

    public record Channels(boolean web, boolean email) {
    }
}
