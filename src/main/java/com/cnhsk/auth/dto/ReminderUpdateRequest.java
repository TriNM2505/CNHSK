package com.cnhsk.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Reminder settings supplied by the authenticated learner. */
public record ReminderUpdateRequest(
        @NotBlank(message = "Giờ nhắc là bắt buộc.")
        @Pattern(regexp = "^([01]\\d|2[0-3]):00$", message = "Giờ nhắc phải là giờ tròn từ 00:00 đến 23:00.")
        String reminderTime,
        @NotNull(message = "Vui lòng chọn trạng thái nhắc trên web.") Boolean web,
        @NotNull(message = "Vui lòng chọn trạng thái nhắc qua email.") Boolean email) {
}
