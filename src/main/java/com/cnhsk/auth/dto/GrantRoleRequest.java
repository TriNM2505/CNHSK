package com.cnhsk.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record GrantRoleRequest(@NotBlank String roleCode) {
}
