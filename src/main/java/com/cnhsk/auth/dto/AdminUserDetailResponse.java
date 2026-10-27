package com.cnhsk.auth.dto;

import java.time.Instant;
import java.util.List;

import com.cnhsk.auth.entity.UserStatus;

public record AdminUserDetailResponse(
        Long id,
        String email,
        String displayName,
        List<AdminUserRoleResponse> roles,
        UserStatus status,
        Instant bannedAt,
        String banReason,
        Instant emailVerifiedAt,
        Instant createdAt) {

    public AdminUserDetailResponse {
        roles = List.copyOf(roles);
    }
}
