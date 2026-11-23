package com.cnhsk.auth.dto;

import java.time.Instant;

/**
 * Profile of the signed-in user. Besides the editable name, avatar and level it carries read-only account facts for
 * the profile page: the account id, when it was created, the plan and when it ends, and how many devices hold a live
 * session.
 */
public record ProfileResponse(
        String displayName,
        String avatarUrl,
        Short hskLevel,
        String email,
        boolean emailVerified,
        Long id,
        Instant createdAt,
        String planCode,
        Instant planExpiresAt,
        long deviceCount) {
}
