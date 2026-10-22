package com.cnhsk.auth.dto;

import java.time.Instant;

import com.cnhsk.auth.entity.RoleCode;

public record AdminUserRoleResponse(
        RoleCode code,
        Long grantedBy,
        Instant grantedAt) {
}
