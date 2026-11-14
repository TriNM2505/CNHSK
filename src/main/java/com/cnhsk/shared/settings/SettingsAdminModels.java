package com.cnhsk.shared.settings;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public final class SettingsAdminModels {
    private SettingsAdminModels() { }

    public record UserRef(long id, @Schema(nullable = true) String displayName) { }
    public record SettingItem(String key, String group, String description, String type, int currentValue,
            int defaultValue, int min, int max, boolean overridden, @Schema(nullable = true) UserRef updatedBy,
            @Schema(nullable = true) Instant updatedAt) { }
    public record SettingWrite(@NotNull Integer value, @NotBlank @Size(min = 1, max = 500) String reason) { }
    public record SettingReset(@NotBlank @Size(min = 1, max = 500) String reason) { }
    public record SettingHistoryEntry(@Schema(nullable = true) Integer oldValue, @Schema(nullable = true) Integer newValue,
            String reason, UserRef actor, Instant at) { }
    public record SettingHistoryPage(List<SettingHistoryEntry> items, int page, int size, long totalElements) { }

    record OverrideRow(String value, long updatedBy, Instant updatedAt) { }
    record AuditRow(Integer oldValue, Integer newValue, String reason, long actorId, Instant at) { }
    record AuditPage(List<AuditRow> items, long totalElements) { }
}
