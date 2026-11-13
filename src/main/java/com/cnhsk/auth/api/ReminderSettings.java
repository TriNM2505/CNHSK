package com.cnhsk.auth.api;

import java.time.LocalTime;

/** Reminder preferences without exposing authentication-owned user data. */
public record ReminderSettings(LocalTime reminderTime, boolean web, boolean email,
        boolean emailVerified, boolean configured) {
}
