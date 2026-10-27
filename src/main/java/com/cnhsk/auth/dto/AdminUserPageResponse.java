package com.cnhsk.auth.dto;

import java.util.List;

public record AdminUserPageResponse(
        List<AdminUserSummaryResponse> items,
        int page,
        int size,
        long totalElements) {

    public AdminUserPageResponse {
        items = List.copyOf(items);
    }
}
