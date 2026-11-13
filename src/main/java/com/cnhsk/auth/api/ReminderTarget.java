package com.cnhsk.auth.api;

/** Email reminder candidate; the address stays inside auth. */
public record ReminderTarget(long userId) {
}
