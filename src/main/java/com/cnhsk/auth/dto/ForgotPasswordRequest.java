package com.cnhsk.auth.dto;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(
        @NotBlank(message = "Email là bắt buộc.")
        @Email(message = "Email không đúng định dạng.")
        @Size(max = 255, message = "Email không được vượt quá 255 ký tự.")
        String email) {

    public ForgotPasswordRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
