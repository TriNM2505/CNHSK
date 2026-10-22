package com.cnhsk.auth.dto;

/** Public profile response matching the auth OpenAPI contract. */
public record ProfileResponse(
        String displayName,
        String avatarUrl,
        Short hskLevel,
        String email,
        boolean emailVerified) {
}
