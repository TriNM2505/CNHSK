package com.cnhsk.auth.api;

import java.time.Instant;
import java.util.Map;

/** Immutable snapshot of the plan and usage stored on a user. */
public record PlanState(String planCode, Instant planExpiresAt,
        Map<String, Integer> freeUsage, Instant usageResetAt) {

    public PlanState {
        if (planCode == null || freeUsage == null || usageResetAt == null
                || freeUsage.entrySet().stream().anyMatch(entry -> entry.getKey() == null
                        || entry.getValue() == null)) {
            throw new IllegalArgumentException("Tham số không hợp lệ.");
        }
        freeUsage = Map.copyOf(freeUsage);
    }
}
