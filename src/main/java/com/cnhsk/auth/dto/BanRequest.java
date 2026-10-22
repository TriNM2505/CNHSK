package com.cnhsk.auth.dto;

import jakarta.validation.constraints.NotNull;

public record BanRequest(@NotNull String reason) {
}
