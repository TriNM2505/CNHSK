package com.cnhsk.learning.grading;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.cnhsk.learning.api.Consumption;
import com.cnhsk.learning.api.EntitlementApi;
import com.cnhsk.learning.grading.GradingRepository.RequestRow;
import com.cnhsk.learning.grading.web.TeacherGradingViews.*;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TeacherGradingService {
    private final GradingRepository repository;
    private final GradingConfig config;
    private final EntitlementApi entitlement;
    private final ObjectMapper mapper;
    private final Clock clock;

    public TeacherGradingService(GradingRepository repository, GradingConfig config, EntitlementApi entitlement,
            ObjectMapper mapper, Clock clock) {
        this.repository = repository; this.config = config; this.entitlement = entitlement;
        this.mapper = mapper; this.clock = clock;
    }

    public QueuePage queue(long teacherId, int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw error(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Phân trang không hợp lệ.");
        }
        Instant now = clock.instant();
        long total = repository.countQueue(teacherId, now);
        List<QueueItem> items = repository.findQueue(teacherId, now, page, size).stream().map(row ->
                new QueueItem(row.id(), reference(row.id()), row.hanziCount(), row.hskLevel(), row.createdAt(),
                        row.deadline(), config.getTeacherPayoutCredits())).toList();
        return new QueuePage(items, page, size, total);
    }

    public List<AssignedItem> mine(long teacherId) {
        return repository.findAssigned(teacherId).stream().map(row -> new AssignedItem(row.id(), reference(row.id()),
                row.hanziCount(), row.assignedAt(), row.deadline())).toList();
    }

    @Transactional
    public Detail claim(long teacherId, long id) {
        repository.lockTeacherClaim(teacherId);
        RequestRow row = repository.find(id).orElseThrow(TeacherGradingService::alreadyClaimed);
        if (row.userId() == teacherId) {
            throw error(ErrorCode.GRADING_SELF_GRADING, HttpStatus.FORBIDDEN, "Không thể nhận chấm bài của chính mình.");
        }
        if (repository.countActiveClaims(teacherId) >= config.getMaxActiveClaims()) {
            throw error(ErrorCode.GRADING_CLAIM_LIMIT, HttpStatus.UNPROCESSABLE_ENTITY, "Bạn đang giữ đủ số bài được phép.");
        }
        Instant now = clock.instant();
        if (!repository.claim(id, teacherId, now, now.plus(config.getGradingHours(), ChronoUnit.HOURS))) throw alreadyClaimed();
        return detail(assigned(teacherId, id));
    }

    public Detail get(long teacherId, long id) { return detail(assigned(teacherId, id)); }

    @Transactional
    public void release(long teacherId, long id) {
        RequestRow row = assigned(teacherId, id);
        if (!repository.release(id, teacherId, clock.instant())) throw notAssignee();
        RequestRow released = repository.find(id).orElseThrow(TeacherGradingService::invalidStoredRequest);
        if ("EXPIRED".equals(released.status())) {
            Consumption receipt;
            try { receipt = mapper.treeToValue(row.payload().get("consumption"), Consumption.class); }
            catch (JsonProcessingException | IllegalArgumentException exception) { throw invalidStoredRequest(); }
            if (receipt == null) throw invalidStoredRequest();
            entitlement.refund(row.userId(), receipt);
        }
    }

    private RequestRow assigned(long teacherId, long id) {
        return repository.find(id).filter(row -> "ASSIGNED".equals(row.status())
                && row.assigneeId() != null && row.assigneeId() == teacherId).orElseThrow(TeacherGradingService::notAssignee);
    }

    private Detail detail(RequestRow row) {
        var payload = row.payload();
        if (!payload.path("essay").isTextual() || !payload.path("hanziCount").canConvertToInt()) throw invalidStoredRequest();
        return new Detail(row.id(), reference(row.id()), row.status(), payload.get("essay").textValue(),
                payload.path("prompt").isNull() || payload.path("prompt").isMissingNode() ? null : payload.path("prompt").asText(),
                payload.path("hskLevel").isNull() || payload.path("hskLevel").isMissingNode() ? null : payload.path("hskLevel").intValue(),
                payload.get("hanziCount").intValue(), row.assignedAt(), row.deadline(), config.getTeacherPayoutCredits(), null);
    }

    private String reference(long id) { return String.format(Locale.ROOT, "GR-%06d", id); }
    private static IllegalStateException invalidStoredRequest() { return new IllegalStateException("Stored grading request is invalid."); }
    private static ApiException alreadyClaimed() { return error(ErrorCode.GRADING_ALREADY_CLAIMED, HttpStatus.CONFLICT, "Bài đã có người nhận hoặc không còn chờ nhận."); }
    private static ApiException notAssignee() { return error(ErrorCode.GRADING_NOT_ASSIGNEE, HttpStatus.FORBIDDEN, "Không có quyền truy cập bài đang chấm."); }
    private static ApiException error(ErrorCode code, HttpStatus status, String message) { return new ApiException(code, status, message, Map.of()); }
}
