package com.cnhsk.shared.audit;

import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private static final Pattern ENTITY_PATTERN = Pattern.compile("^([a-z][a-z0-9_]{0,29}):([1-9][0-9]*)$");
    private static final Pattern TARGET_TYPE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{0,29}$");

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
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
