package com.cnhsk.shared.settings;

import static com.cnhsk.shared.settings.SettingsAdminModels.*;
import static com.cnhsk.shared.settings.SettingKey.GRADING_MIN_HANZI;
import static com.cnhsk.shared.settings.SettingKey.GRADING_MAX_HANZI;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.cnhsk.shared.api.ActorNameLookup;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SettingsAdminService {
    private final SettingsAdminStore store;
    private final SettingsService settings;
    private final ActorNameLookup actors;
    private final AuditService audit;

    public SettingsAdminService(SettingsAdminStore store, SettingsService settings, ActorNameLookup actors, AuditService audit) {
        this.store = store;
        this.settings = settings;
        this.actors = actors;
        this.audit = audit;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<SettingItem> list() {
        var rows = store.overrides();
        var names = names(rows.values().stream().map(OverrideRow::updatedBy).collect(Collectors.toSet()));
        return Arrays.stream(SettingKey.values()).map(key -> item(key, rows.get(key), names)).toList();
    }

    @Transactional
    public SettingItem change(String rawKey, long actorId, Supplier<SettingWrite> body) {
        SettingKey key = key(rawKey);
        SettingWrite input = body.get();
        if (input == null || input.value() == null || !key.accepts(input.value())) throw outOfRange();
        validateReason(input.reason());
        return mutate(key, actorId, input.value(), input.reason());
    }

    @Transactional
    public SettingItem reset(String rawKey, long actorId, Supplier<SettingReset> body) {
        SettingKey key = key(rawKey);
        SettingReset input = body.get();
        if (input == null) throw invalid("body");
        validateReason(input.reason());
        return mutate(key, actorId, null, input.reason());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SettingHistoryPage history(String rawKey, int page, int size) {
        SettingKey key = key(rawKey);
        if (page < 0) throw invalid("page");
        if (size < 1 || size > 50) throw invalid("size");
        AuditPage result = store.history(key, page, size);
        var names = names(result.items().stream().map(AuditRow::actorId).collect(Collectors.toSet()));
        return new SettingHistoryPage(result.items().stream().map(row -> new SettingHistoryEntry(row.oldValue(), row.newValue(),
                row.reason(), new UserRef(row.actorId(), names.get(row.actorId())), row.at())).toList(), page, size, result.totalElements());
    }

    private SettingItem mutate(SettingKey key, long actorId, Integer value, String reason) {
        store.lock();
        var before = store.overrides();
        int oldValue = effective(key, before.get(key));
        int min = key == GRADING_MIN_HANZI ? replacement(key, value) : effective(GRADING_MIN_HANZI, before.get(GRADING_MIN_HANZI));
        int max = key == GRADING_MAX_HANZI ? replacement(key, value) : effective(GRADING_MAX_HANZI, before.get(GRADING_MAX_HANZI));
        if (min >= max) throw outOfRange();
        if (value == null) store.reset(key); else store.put(key, value, actorId);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("key", key.key());
        detail.put("oldValue", oldValue);
        detail.put("newValue", value);
        detail.put("reason", reason);
        audit.recordDetail(actorId, value == null ? "SETTING_RESET" : "SETTING_CHANGE", "SETTING", key.auditId(), detail);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { settings.invalidate(key); }
        });
        OverrideRow row = store.overrides().get(key);
        return item(key, row, row == null ? Map.of() : names(Set.of(row.updatedBy())));
    }

    private int replacement(SettingKey key, Integer value) { return value == null ? settings.defaultValue(key) : value; }
    private int effective(SettingKey key, OverrideRow row) { return settings.resolveValue(key, row == null ? null : row.value()); }

    private SettingItem item(SettingKey key, OverrideRow row, Map<Long, String> names) {
        return new SettingItem(key.key(), key.group(), key.description(), key.type(), effective(key, row), settings.defaultValue(key),
                key.min(), key.max(), row != null, row == null ? null : new UserRef(row.updatedBy(), names.get(row.updatedBy())),
                row == null ? null : row.updatedAt());
    }

    private Map<Long, String> names(Set<Long> ids) { return ids.isEmpty() ? Map.of() : actors.displayNames(ids); }

    private SettingKey key(String rawKey) {
        return Arrays.stream(SettingKey.values()).filter(key -> key.key().equals(rawKey)).findFirst().orElseThrow(() ->
                new ApiException(ErrorCode.SETTING_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy cài đặt.", Map.of()));
    }

    private void validateReason(String reason) {
        if (reason == null || reason.isBlank() || reason.codePointCount(0, reason.length()) > 500) throw invalid("reason");
    }

    public static ApiException outOfRange() {
        return new ApiException(ErrorCode.SETTING_OUT_OF_RANGE, HttpStatus.UNPROCESSABLE_ENTITY, "Giá trị cài đặt ngoài khoảng cho phép.", Map.of());
    }

    public static ApiException invalid(String field) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Dữ liệu không hợp lệ.",
                Map.of("details", List.of(Map.of("field", field, "message", "Giá trị không hợp lệ."))));
    }
}
