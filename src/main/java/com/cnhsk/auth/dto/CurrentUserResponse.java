package com.cnhsk.auth.dto;

import java.util.List;

import com.cnhsk.auth.entity.RoleCode;

/** Public current-user representation defined by the authentication OpenAPI contract. */
public record CurrentUserResponse(
        Long id,
        String email,
        String displayName,
        boolean emailVerified,
        List<RoleCode> roles,
        Short hskLevel,
        String avatarUrl) {

    public CurrentUserResponse {
        roles = List.copyOf(roles);
    }
}
