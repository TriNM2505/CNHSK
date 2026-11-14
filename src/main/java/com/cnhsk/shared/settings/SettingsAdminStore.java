package com.cnhsk.shared.settings;

import static com.cnhsk.shared.settings.SettingsAdminModels.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SettingsAdminStore {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public SettingsAdminStore(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    void lock() {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(:key,0))", Map.of("key", "settings"), rs -> null);
    }

    Map<SettingKey, OverrideRow> overrides() {
        Map<String, SettingKey> catalog = new HashMap<>();
        for (SettingKey key : SettingKey.values()) catalog.put(key.key(), key);
        return jdbc.query("SELECT key,value,updated_by,updated_at FROM shared.app_settings WHERE key IN (:keys)",
                Map.of("keys", Arrays.stream(SettingKey.values()).map(SettingKey::key).toList()), rs -> {
                    Map<SettingKey, OverrideRow> rows = new java.util.EnumMap<>(SettingKey.class);
                    while (rs.next()) rows.put(catalog.get(rs.getString("key")), new OverrideRow(rs.getString("value"),
                            rs.getLong("updated_by"), rs.getObject("updated_at", OffsetDateTime.class).toInstant()));
                    return rows;
                });
    }

    void put(SettingKey key, int value, long actorId) {
        jdbc.update("""
                INSERT INTO shared.app_settings(key,value,updated_by) VALUES (:key,:value,:actor)
                ON CONFLICT (key) DO UPDATE SET value=EXCLUDED.value,updated_by=EXCLUDED.updated_by,updated_at=now()
                """, Map.of("key", key.key(), "value", Integer.toString(value), "actor", actorId));
    }

    void reset(SettingKey key) {
        jdbc.update("DELETE FROM shared.app_settings WHERE key=:key", Map.of("key", key.key()));
    }

    AuditPage history(SettingKey key, int page, int size) {
        var args = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("id", key.auditId()).addValue("size", size).addValue("offset", (long) page * size);
        String filter = " WHERE target_type='SETTING' AND target_id=:id AND action IN ('SETTING_CHANGE','SETTING_RESET')";
        long count = jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs" + filter, args, Long.class);
        var rows = jdbc.query("SELECT actor_id,detail,created_at FROM shared.audit_logs" + filter
                + " ORDER BY created_at DESC,id DESC LIMIT :size OFFSET :offset", args, (rs, row) -> audit(rs));
        return new AuditPage(rows, count);
    }

    private AuditRow audit(ResultSet rs) throws SQLException {
        try {
            JsonNode detail = mapper.readTree(rs.getString("detail"));
            return new AuditRow(integer(detail.get("oldValue")), integer(detail.get("newValue")),
                    detail.path("reason").asText(), rs.getLong("actor_id"), rs.getObject("created_at", OffsetDateTime.class).toInstant());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid stored settings audit", exception);
        }
    }

    private Integer integer(JsonNode node) { return node == null || node.isNull() ? null : node.intValue(); }
}
