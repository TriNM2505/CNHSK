package com.cnhsk.auth.dto;

import java.time.Instant;
import java.util.List;

import com.cnhsk.auth.entity.RoleCode;
import com.cnhsk.auth.entity.UserStatus;

public record AdminUserSummaryResponse(
        Long id,
        String emailMasked,
        String displayName,
        List<RoleCode> roles,
        UserStatus status,
        Instant createdAt) {

    public AdminUserSummaryResponse {
        roles = List.copyOf(roles);
    }
}
