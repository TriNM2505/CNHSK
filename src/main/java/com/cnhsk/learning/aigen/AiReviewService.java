package com.cnhsk.learning.aigen;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.learning.knowledge.QuestionAdminService;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AiReviewService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuthApi auth;
    private final AiReviewViewedStore viewed;
    private final AuditService audit;
    private final Clock clock;
    private final TransactionTemplate perQuestion;

    public AiReviewService(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper, AuthApi auth,
            AiReviewViewedStore viewed, AuditService audit, Clock clock, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.auth = auth;
        this.viewed = viewed;
        this.audit = audit;
        this.clock = clock;
        this.perQuestion = new TransactionTemplate(transactions);
        perQuestion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public AiQuestionPage list(String kid, Integer hsk, Instant from, Instant to, int page, int size) {
        return list(kid, hsk, null, from, to, page, size);
    }

    /** {@code qtype} narrows the queue to MULTIPLE_CHOICE or TRUE_FALSE drafts; null keeps every type. */
    @Transactional(readOnly = true)
    public AiQuestionPage list(String kid, Integer hsk, String qtype, Instant from, Instant to, int page, int size) {
        CurrentUser caller = reviewer();
        if (page < 0) throw validation("page");
        if (size < 1 || size > 50) throw validation("size");
        if (hsk != null && (hsk < 1 || hsk > 9)) throw validation("hsk");
        if (kid != null && kid.codePointCount(0, kid.length()) > 20) throw validation("kid");
        if (qtype != null && !Set.of("MULTIPLE_CHOICE", "TRUE_FALSE").contains(qtype)) throw validation("qtype");
        if (from != null && to != null && from.isAfter(to)) throw validation("from/to");
        StringBuilder where = new StringBuilder(" WHERE q.source='AI' AND q.status='PENDING_REVIEW'");
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (kid != null) {
            where.append(" AND EXISTS (SELECT 1 FROM learning.question_knowledge_points l JOIN learning.knowledge_points k"
                    + " ON k.id=l.knowledge_point_id WHERE l.question_id=q.id AND k.code=:kid)");
            params.addValue("kid", kid);
        }
        if (hsk != null) { where.append(" AND q.hsk_level=:hsk"); params.addValue("hsk", hsk); }
        if (qtype != null) { where.append(" AND q.qtype=:qtype"); params.addValue("qtype", qtype); }
        if (from != null) { where.append(" AND q.created_at>=:from"); params.addValue("from", at(from)); }
        if (to != null) { where.append(" AND q.created_at<=:to"); params.addValue("to", at(to)); }
        long count = Objects.requireNonNull(jdbc.queryForObject("SELECT count(*) FROM learning.questions q" + where,
                params, Long.class));
        params.addValue("limit", size).addValue("offset", (long) page * size);
        List<QueueRow> rows = jdbc.query("SELECT q.id,q.stem,q.qtype,q.hsk_level,q.created_at,q.created_by_request_id"
                        + " FROM learning.questions q" + where + " ORDER BY q.created_at,q.id LIMIT :limit OFFSET :offset",
                params, (rs, n) -> new QueueRow(rs.getLong("id"), rs.getString("stem"), rs.getString("qtype"), rs.getInt("hsk_level"),
                        rs.getTimestamp("created_at").toInstant(), rs.getObject("created_by_request_id", Long.class)));
        List<Long> ids = rows.stream().map(QueueRow::id).toList();
        Map<Long, List<Label>> labels = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            jdbc.query("SELECT l.question_id,k.code,l.role FROM learning.question_knowledge_points l"
                            + " JOIN learning.knowledge_points k ON k.id=l.knowledge_point_id"
                            + " WHERE l.question_id IN (:ids) ORDER BY l.question_id,l.role,k.code",
                    new MapSqlParameterSource("ids", ids), rs -> {
                        labels.computeIfAbsent(rs.getLong("question_id"), ignored -> new ArrayList<>())
                                .add(new Label(rs.getString("code"), rs.getString("role")));
                    });
        }
        Set<Long> markers = viewed.viewed(caller.userId(), ids);
        return new AiQuestionPage(rows.stream().map(row -> new AiQuestionSummary(row.id(), summary(row.stem()), row.qtype(),
                row.hskLevel(), List.copyOf(labels.getOrDefault(row.id(), List.of())), row.createdAt(), row.runId(),
                markers.contains(row.id()))).toList(), page, size, count);
    }

    @Transactional
    public ReviewResult approve(long id) {
        return review(id, reviewer(), null);
    }

    @Transactional
    public ReviewResult reject(long id, String reason) {
        CurrentUser caller = reviewer();
        if (reason == null) throw validation("reason");
        String normalized = reason.replaceAll("^[\\s\\p{Z}]+|[\\s\\p{Z}]+$", "");
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > 1000) throw validation("reason");
        return review(id, caller, normalized);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BulkResult bulkApprove(List<Long> ids) {
        CurrentUser caller = reviewer();
        if (ids == null || ids.isEmpty() || ids.size() > 50 || ids.stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(ids).size() != ids.size()) throw validation("ids");
        List<BulkItem> results = new ArrayList<>();
        Set<Long> markers = viewed.viewed(caller.userId(), ids);
        for (Long id : ids) {
            if (!markers.contains(id)) {
                results.add(new BulkItem(id, "NOT_VIEWED"));
                continue;
            }
            try {
                perQuestion.executeWithoutResult(status -> review(id, caller, null));
                results.add(new BulkItem(id, "APPROVED"));
            } catch (ApiException exception) {
                if (exception.getCode() == ErrorCode.QUESTION_NOT_FOUND || exception.getCode() == ErrorCode.QUESTION_NOT_PENDING) {
                    results.add(new BulkItem(id, "NOT_PENDING"));
                } else if (exception.getCode() == ErrorCode.MALFORMED_QUESTION) {
                    results.add(new BulkItem(id, "MALFORMED"));
                } else {
                    throw exception;
                }
            }
        }
        return new BulkResult(List.copyOf(results));
    }

    private ReviewResult review(long id, CurrentUser caller, String reason) {
        if (id <= 0) throw QuestionAdminService.notFound();
        List<ReviewRow> rows = jdbc.query("SELECT id,source,status,qtype,stem,explanation,options::text,correct_key"
                        + " FROM learning.questions WHERE id=:id FOR UPDATE", new MapSqlParameterSource("id", id),
                (rs, n) -> new ReviewRow(rs.getLong("id"), rs.getString("source"), rs.getString("status"),
                        rs.getString("qtype"), rs.getString("stem"), rs.getString("explanation"),
                        rs.getString("options"), rs.getString("correct_key")));
        if (rows.isEmpty()) throw QuestionAdminService.notFound();
        ReviewRow row = rows.get(0);
        if (!"AI".equals(row.source()) || !"PENDING_REVIEW".equals(row.status())) throw notPending();
        if (reason == null) validate(row);
        Instant now = clock.instant();
        String status = reason == null ? "APPROVED" : "REJECTED";
        int updated = jdbc.update("UPDATE learning.questions SET status=:status,reviewed_by=:actor,reviewed_at=:now,"
                        + "review_reason=:reason WHERE id=:id AND source='AI' AND status='PENDING_REVIEW'",
                new MapSqlParameterSource("id", id).addValue("status", status).addValue("actor", caller.userId())
                        .addValue("now", at(now)).addValue("reason", reason, java.sql.Types.VARCHAR));
        if (updated != 1) throw notPending();
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", status); after.put("reviewedBy", caller.userId()); after.put("reviewedAt", now);
        if (reason != null) after.put("reviewReason", reason);
        audit.record(caller.userId(), reason == null ? "AI_QUESTION_APPROVE" : "AI_QUESTION_REJECT", "question:" + id,
                Map.of("status", row.status()), after);
        return new ReviewResult(id, status, now);
    }

    private void validate(ReviewRow row) {
        List<Map<String, String>> errors = new ArrayList<>();
        if (blank(row.stem())) errors.add(detail("stem"));
        if (blank(row.explanation())) errors.add(detail("explanation"));
        Long primary = jdbc.queryForObject("SELECT count(*) FROM learning.question_knowledge_points"
                + " WHERE question_id=:id AND role='PRIMARY'", new MapSqlParameterSource("id", row.id()), Long.class);
        if (!Long.valueOf(1).equals(primary)) errors.add(detail("primaryKid"));
        if ("MULTIPLE_CHOICE".equals(row.qtype()) || "TRUE_FALSE".equals(row.qtype())) {
            try {
                JsonNode options = mapper.readTree(row.options());
                List<JsonNode> correct = new ArrayList<>();
                if (options != null && options.isArray()) {
                    for (JsonNode option : options) {
                        if (option.isObject() && option.path("is_correct").isBoolean() && option.path("is_correct").booleanValue())
                            correct.add(option);
                    }
                }
                if (correct.size() != 1 || !correct.get(0).path("key").isTextual()
                        || blank(row.correctKey()) || !row.correctKey().equals(correct.get(0).path("key").textValue())) {
                    errors.add(detail("options/correctKey"));
                }
            } catch (JsonProcessingException exception) {
                errors.add(detail("options"));
            }
        }
        if (!errors.isEmpty()) throw QuestionAdminService.malformed(errors);
    }

    private CurrentUser reviewer() {
        CurrentUser caller = auth.currentUser();
        if (!caller.roles().contains(RoleCode.TEACHER)) {
            throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Không có quyền duyệt câu hỏi.", Map.of());
        }
        return caller;
    }

    public static ApiException validation(String field) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Dữ liệu không hợp lệ.", Map.of("details", List.of(detail(field))));
    }

    private static ApiException notPending() {
        return new ApiException(ErrorCode.QUESTION_NOT_PENDING, HttpStatus.CONFLICT,
                "Câu hỏi không còn chờ duyệt.", Map.of());
    }

    private static Map<String, String> detail(String field) { return Map.of("field", field, "message", "Giá trị không hợp lệ."); }
    private static boolean blank(String text) { return text == null || text.replaceAll("[\\s\\p{Z}]", "").isEmpty(); }
    private static OffsetDateTime at(Instant value) { return value.atOffset(ZoneOffset.UTC); }
    private static String summary(String stem) {
        return stem.codePointCount(0, stem.length()) <= 80 ? stem : stem.substring(0, stem.offsetByCodePoints(0, 80));
    }

    private record QueueRow(long id, String stem, String qtype, int hskLevel, Instant createdAt, Long runId) { }
    private record ReviewRow(long id, String source, String status, String qtype, String stem, String explanation,
                             String options, String correctKey) { }
    @Schema(name = "AiQuestionPage", requiredProperties = {"items", "page", "size", "totalElements"})
    public record AiQuestionPage(List<AiQuestionSummary> items, int page, int size, long totalElements) { }
    @Schema(name = "AiQuestionSummary", requiredProperties = {"id", "stemSummary", "qtype", "hskLevel", "labels", "generatedAt", "runId", "viewedByMe"})
    public record AiQuestionSummary(long id, @Schema(maxLength = 80) String stemSummary,
            @Schema(allowableValues = {"MULTIPLE_CHOICE", "TRUE_FALSE"}) String qtype, int hskLevel,
            List<Label> labels, Instant generatedAt, @Schema(nullable = true) Long runId, boolean viewedByMe) { }
    @Schema(name = "Label", requiredProperties = {"kid", "role"})
    public record Label(String kid, @Schema(allowableValues = {"PRIMARY", "SECONDARY"}) String role) { }
    @Schema(name = "ReviewResult", requiredProperties = {"id", "status", "reviewedAt"})
    public record ReviewResult(long id, @Schema(allowableValues = {"APPROVED", "REJECTED"}) String status, Instant reviewedAt) { }
    @Schema(name = "BulkResult", requiredProperties = "results")
    public record BulkResult(List<BulkItem> results) { }
    @Schema(name = "BulkItem", requiredProperties = {"id", "outcome"})
    public record BulkItem(long id, @Schema(allowableValues = {"APPROVED", "NOT_VIEWED", "NOT_PENDING", "MALFORMED"}) String outcome) { }
}
