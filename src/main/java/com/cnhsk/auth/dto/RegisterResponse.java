package com.cnhsk.auth.dto;

public record RegisterResponse(long userId, String email, boolean emailVerified) {
}
