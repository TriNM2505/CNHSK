package com.cnhsk.learning.aigen;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiRunQueryService {
    private static final Logger LOG = LoggerFactory.getLogger(AiRunQueryService.class);
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> STATUSES = Set.of("RUNNING", "COMPLETED", "FAILED");
    private static final Set<String> ERRORS = Set.of("INVALID_OUTPUT", "BUDGET_EXCEEDED", "PROVIDER_ERROR",
            "NO_VALID_QUESTION", "STALE", "EMBEDDING_UNAVAILABLE");
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuthApi auth;
    private final AiGenerateConfig config;
    private final Clock clock;
    private final SemanticDeduplicator semantic;

    public AiRunQueryService(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper, AuthApi auth,
            AiGenerateConfig config, Clock clock, SemanticDeduplicator semantic) {
        this.semantic = semantic;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.auth = auth;
        this.config = config;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RunPage list(String status, int page, int size) {
        requireAdmin();
        if (page < 0) throw AiReviewService.validation("page");
        if (size < 1 || size > 50) throw AiReviewService.validation("size");
        if (status != null && !STATUSES.contains(status)) throw AiReviewService.validation("status");
        String where = " WHERE kind='AI_GENERATE' AND status IN ('RUNNING','COMPLETED','FAILED')";
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (status != null) {
            where += " AND status=:status";
            params.addValue("status", status);
        }
        long total = Objects.requireNonNull(jdbc.queryForObject("SELECT count(*) FROM learning.service_requests" + where,
                params, Long.class));
        params.addValue("limit", size).addValue("offset", (long) page * size);
        List<Run> items = jdbc.query("SELECT id,payload::text,status,created_at,completed_at,result::text,error_message"
                        + " FROM learning.service_requests" + where + " ORDER BY created_at DESC,id DESC LIMIT :limit OFFSET :offset",
                params, (rs, row) -> {
                    JsonNode payload = parse(rs.getString("payload"));
                    JsonNode result = parse(rs.getString("result"));
                    var completed = rs.getTimestamp("completed_at");
                    return new Run(rs.getLong("id"), payload.path("kid").asText(""), rs.getString("status"),
                            rs.getTimestamp("created_at").toInstant(), completed == null ? null : completed.toInstant(),
                            count(result, "questionIds"), count(result, "dropped"), tokens(result, "inputTokens"),
                            tokens(result, "outputTokens"), safeError(rs.getString("error_message")));
                });
        int systemUserId = config.systemUserId();
        boolean enabled = systemUserId > 0 && auth.isActiveAdmin(systemUserId);
        return new RunPage(enabled, semantic.ready(), monthTokens(), List.copyOf(items), page, size, total);
    }

    public void requireAdmin() {
        if (!auth.currentUser().roles().contains(RoleCode.ADMIN)) {
            throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "Không có quyền xem hoặc chạy lô sinh câu hỏi.", Map.of());
        }
    }

    private long monthTokens() {
        var start = clock.instant().atZone(VIETNAM).withDayOfMonth(1).toLocalDate().atStartOfDay(VIETNAM);
        var params = new MapSqlParameterSource("from", start.toInstant().atOffset(ZoneOffset.UTC))
                .addValue("to", start.plusMonths(1).toInstant().atOffset(ZoneOffset.UTC));
        List<String> results = jdbc.query("SELECT result::text FROM learning.service_requests"
                        + " WHERE kind='AI_GENERATE' AND created_at>=:from AND created_at<:to",
                params, (rs, row) -> rs.getString("result"));
        long total = 0;
        for (String json : results) {
            JsonNode result = parse(json);
            total = Math.addExact(total, Math.addExact(tokens(result, "inputTokens"), tokens(result, "outputTokens")));
        }
        return total;
    }

    private JsonNode parse(String json) {
        if (json == null) return mapper.missingNode();
        try {
            JsonNode node = mapper.readTree(json);
            return node == null ? mapper.missingNode() : node;
        } catch (JsonProcessingException exception) {
            LOG.warn("Generation history contains unreadable metadata");
            return mapper.missingNode();
        }
    }

    private static int count(JsonNode result, String field) {
        JsonNode value = result.path(field);
        return value.isArray() ? value.size() : 0;
    }

    private static long tokens(JsonNode result, String field) {
        JsonNode value = result.path(field);
        return value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0 ? value.longValue() : 0;
    }

    private static String safeError(String error) {
        if (error == null || ERRORS.contains(error)) return error;
        LOG.warn("Generation history contains an unsupported error code; omitted from response");
        return null;
    }

    @Schema(name = "RunPage", requiredProperties = {"enabled", "embeddingReady", "monthTokens", "items", "page", "size", "totalElements"})
    public record RunPage(boolean enabled, boolean embeddingReady, long monthTokens, List<Run> items, int page, int size, long totalElements) { }
    @Schema(name = "Run", requiredProperties = {"id", "kid", "status", "createdAt", "completedAt", "questionCount",
            "droppedCount", "inputTokens", "outputTokens", "error"})
    public record Run(long id, String kid, @Schema(allowableValues = {"RUNNING", "COMPLETED", "FAILED"}) String status,
            Instant createdAt, @Schema(nullable = true) Instant completedAt, int questionCount, int droppedCount,
            long inputTokens, long outputTokens,
            @Schema(nullable = true, allowableValues = {"INVALID_OUTPUT", "BUDGET_EXCEEDED", "PROVIDER_ERROR", "NO_VALID_QUESTION", "STALE", "EMBEDDING_UNAVAILABLE"}) String error) { }
}
