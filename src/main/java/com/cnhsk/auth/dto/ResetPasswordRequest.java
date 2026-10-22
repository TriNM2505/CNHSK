package com.cnhsk.auth.dto;

import com.cnhsk.auth.validation.ValidResetPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@ValidResetPassword
public record ResetPasswordRequest(
        @NotBlank(message = "Token đặt lại mật khẩu là bắt buộc.")
        String token,
        @NotBlank(message = "Mật khẩu mới là bắt buộc.")
        @Size(min = 8, max = 72, message = "Mật khẩu cần 8–72 ký tự, gồm chữ và số.")
        String newPassword,
        @NotBlank(message = "Vui lòng xác nhận mật khẩu.")
        String confirmPassword) {
}
