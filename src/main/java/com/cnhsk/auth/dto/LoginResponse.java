package com.cnhsk.auth.dto;

import com.cnhsk.auth.entity.RoleCode;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Response uses only the optional token fields already defined by LoginResponse in OpenAPI. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        CurrentUser user,
        String accessToken,
        String refreshToken,
        Long accessTokenExpiresIn) {

    public record CurrentUser(
            Long id,
            String email,
            String displayName,
            boolean emailVerified,
            List<RoleCode> roles,
            Short hskLevel,
            String avatarUrl) {
    }
}
