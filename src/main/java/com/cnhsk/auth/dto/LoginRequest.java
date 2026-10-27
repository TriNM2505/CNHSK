package com.cnhsk.auth.dto;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Credentials submitted to the shared web and mobile login endpoint. */
public record LoginRequest(
        @NotBlank(message = "Email là bắt buộc.")
        @Email(message = "Email không đúng định dạng.")
        @Size(max = 255, message = "Email không được vượt quá 255 ký tự.")
        String email,
        @NotBlank(message = "Mật khẩu là bắt buộc.")
        @Size(max = 72, message = "Mật khẩu không được vượt quá 72 ký tự.")
        String password) {

    public LoginRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
