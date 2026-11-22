package com.cnhsk.auth.dto;

public record RegistrationCodeResponse(String email, long expiresInSeconds, long resendAfterSeconds) {
}
