package com.cnhsk.auth.dto;

import java.util.Locale;

import com.cnhsk.auth.validation.ValidRegistration;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@ValidRegistration
public record RegisterRequest(
        @NotBlank(message = "Email là bắt buộc.")
        @Size(max = 255, message = "Email không được vượt quá 255 ký tự.")
        @Email(message = "Email không đúng định dạng.")
        String email,
        @NotBlank(message = "Mật khẩu là bắt buộc.")
        @Size(min = 8, max = 72, message = "Mật khẩu cần 8–72 ký tự, gồm chữ và số.")
        String password,
        @NotBlank(message = "Vui lòng xác nhận mật khẩu.")
        String confirmPassword,
        @NotBlank(message = "Tên hiển thị là bắt buộc.")
        @Size(min = 2, max = 100, message = "Tên hiển thị cần 2–100 ký tự.")
        String displayName,
        @AssertTrue(message = "Bạn cần chấp nhận điều khoản.")
        boolean acceptTerms) {

    public RegisterRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        displayName = displayName == null ? null : displayName.strip();
    }
}
