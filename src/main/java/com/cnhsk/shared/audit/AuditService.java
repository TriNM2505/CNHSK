package com.cnhsk.shared.audit;

import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Service
public class AuditService {

    private static final Pattern ENTITY_PATTERN = Pattern.compile("^([a-z][a-z0-9_]{0,29}):([1-9][0-9]*)$");
    private static final Pattern TARGET_TYPE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{0,29}$");

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final NamedParameterJdbcTemplate jdbc;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper,
            NamedParameterJdbcTemplate jdbc) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    @Transactional
    public void record(Long actorId, String action, String entity, Object before, Object after) {
        Objects.requireNonNull(action, "action must not be null");
        EntityReference reference = parseEntity(entity);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", before == null ? null : objectMapper.convertValue(before, Object.class));
        detail.put("after", after == null ? null : objectMapper.convertValue(after, Object.class));

        auditLogRepository.save(new AuditLog(
                actorId,
                action,
                reference.type(),
                reference.id(),
                detail));
    }

    @Transactional
    public void recordDetail(Long actorId, String action, String targetType, long targetId, Map<String, ?> detail) {
        Objects.requireNonNull(action, "action must not be null");
        if (action.length() > 50) {
            throw new IllegalArgumentException("action must not exceed 50 characters");
        }
        Objects.requireNonNull(targetType, "targetType must not be null");
        if (!TARGET_TYPE_PATTERN.matcher(targetType).matches()) {
            throw new IllegalArgumentException("targetType must use uppercase letters, digits and underscores");
        }
        if (targetId <= 0) {
            throw new IllegalArgumentException("targetId must be positive");
        }
        Objects.requireNonNull(detail, "detail must not be null");

        Map<String, Object> convertedDetail = objectMapper.convertValue(detail, new TypeReference<Map<String, Object>>() {
        });
        auditLogRepository.save(new AuditLog(actorId, action, targetType, targetId, convertedDetail));
    }

    @Transactional
    public void recordDetail(Long actorId, String action, String targetType, Map<String, ?> detail) {
        Objects.requireNonNull(action, "action must not be null");
        if (action.length() > 50) {
            throw new IllegalArgumentException("action must not exceed 50 characters");
        }
        Objects.requireNonNull(targetType, "targetType must not be null");
        if (!TARGET_TYPE_PATTERN.matcher(targetType).matches()) {
            throw new IllegalArgumentException("targetType must use uppercase letters, digits and underscores");
        }
        Objects.requireNonNull(detail, "detail must not be null");
        Map<String, Object> convertedDetail = objectMapper.convertValue(detail, new TypeReference<Map<String, Object>>() {
        });
        auditLogRepository.save(new AuditLog(actorId, action, targetType, null, convertedDetail));
    }

    @Transactional(readOnly = true)
    public Map<Long, ReportStat> countByTargets(String action, String targetType, Set<Long> ids,
            Map<Long, Instant> since) {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(targetType, "targetType must not be null");
        Objects.requireNonNull(ids, "ids must not be null");
        if (ids.isEmpty()) return Map.of();

        Map<Long, ReportStat> result = new LinkedHashMap<>();
        List<String> requested = new ArrayList<>();
        MapSqlParameterSource parameters = new MapSqlParameterSource("action", action)
                .addValue("targetType", targetType);
        int index = 0;
        for (Long id : ids) {
            if (id == null || id <= 0) throw new IllegalArgumentException("target id must be positive");
            Instant cutoff = since == null ? null : since.get(id);
            requested.add("(:id" + index + ", CAST(:since" + index + " AS timestamptz))");
            parameters.addValue("id" + index, id).addValue("since" + index,
                    cutoff == null ? null : OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC));
            result.put(id, new ReportStat(0, null));
            index++;
        }
        String sql = "SELECT a.target_id, count(*) AS report_count, max(a.created_at) AS last_at "
                + "FROM shared.audit_logs a JOIN (VALUES " + String.join(",", requested)
                + ") AS requested(id, since_at) ON a.target_id = requested.id "
                + "WHERE a.target_type = :targetType AND a.action = :action "
                + "AND (requested.since_at IS NULL OR a.created_at >= requested.since_at) GROUP BY a.target_id";
        jdbc.query(sql, parameters, (row, rowNumber) -> Map.entry(row.getLong("target_id"),
                new ReportStat(row.getLong("report_count"), row.getTimestamp("last_at").toInstant())))
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return Map.copyOf(result);
    }

    public record ReportStat(long count, Instant lastAt) {
    }

    private EntityReference parseEntity(String entity) {
        Objects.requireNonNull(entity, "entity must not be null");
        Matcher matcher = ENTITY_PATTERN.matcher(entity);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("entity must use the '<type>:<positive-id>' format");
        }

        try {
            return new EntityReference(matcher.group(1), Long.valueOf(matcher.group(2)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("entity id must fit in a signed 64-bit integer", exception);
        }
    }

    private record EntityReference(String type, Long id) {
    }
}
