package com.cnhsk.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Password change request; semantic password rules are enforced by the auth service. */
public record ChangePasswordRequest(
        @NotBlank(message = "Mật khẩu hiện tại là bắt buộc.") String currentPassword,
        @NotBlank(message = "Mật khẩu mới là bắt buộc.")
        @Size(min = 8, max = 72, message = "Mật khẩu cần 8–72 ký tự, gồm chữ và số.") String newPassword,
        @NotBlank(message = "Vui lòng xác nhận mật khẩu mới.") String confirmPassword) {
}
