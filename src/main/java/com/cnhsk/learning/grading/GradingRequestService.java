package com.cnhsk.learning.grading;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cnhsk.learning.api.Consumption;
import com.cnhsk.learning.api.EntitlementApi;
import com.cnhsk.learning.api.PaidFeature;
import com.cnhsk.learning.grading.GradingRepository.RequestRow;
import com.cnhsk.learning.grading.web.GradingRequestViews;
import com.cnhsk.learning.grading.web.GradingRequestViews.*;
import com.cnhsk.learning.grading.web.GradingRequestWrite;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.settings.SettingKey;
import com.cnhsk.shared.settings.SettingsApi;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.cnhsk.learning.knowledge.entity.LearningTask;
import com.cnhsk.learning.knowledge.repository.LearningTaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GradingRequestService {
    private static final Set<String> STATUSES = Set.of("PENDING", "ASSIGNED", "COMPLETED", "FAILED", "EXPIRED");
    private static final String REFUND_TERMS = "Được hoàn lượt nếu bạn huỷ trước khi có giáo viên nhận, hoặc nếu bài không được nhận/chấm đúng hạn";
    private final GradingRepository repository;
    private final EntitlementApi entitlement;
    private final GradingConfig config;
    private final SettingsApi settings;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final LearningTaskRepository tasks;

    public GradingRequestService(GradingRepository repository, EntitlementApi entitlement, GradingConfig config, SettingsApi settings,
            ObjectMapper mapper, Clock clock, LearningTaskRepository tasks) {
        this.tasks = tasks;
        this.repository = repository;
        this.entitlement = entitlement;
        this.config = config;
        this.settings = settings;
        this.mapper = mapper;
        this.clock = clock;
    }

    public Policy policy() {
        return new Policy(settings.getInt(SettingKey.GRADING_CLAIM_HOURS), settings.getInt(SettingKey.GRADING_GRADING_HOURS), settings.getInt(SettingKey.GRADING_MIN_HANZI), settings.getInt(SettingKey.GRADING_MAX_HANZI),
                config.getMaxChars(), 1, REFUND_TERMS);
    }

    @Transactional
    public Detail submit(long userId, GradingRequestWrite request) {
        var essay = EssayRules.validate(request.essay(), new EssayRules.LengthLimits(
                settings.getInt(SettingKey.GRADING_MIN_HANZI), settings.getInt(SettingKey.GRADING_MAX_HANZI), config.getMaxChars()));
        repository.lockSubmit(userId);
        if (repository.hasActiveEssay(userId, essay.essayHash())) {
            throw error(ErrorCode.GRADING_DUPLICATE_ACTIVE, HttpStatus.CONFLICT, "Bài này đang chờ chấm.");
        }
        Instant now = clock.instant();
        Consumption consumption = entitlement.consume(userId, PaidFeature.GRADING, "grading:" + UUID.randomUUID());
        var payload = mapper.createObjectNode().put("essay", essay.essay()).put("essayHash", essay.essayHash())
                .put("hanziCount", essay.hanziCount());
        payload.put("prompt", request.prompt());
        if (request.hskLevel() == null) payload.putNull("hskLevel");
        else payload.put("hskLevel", request.hskLevel());
        attachTask(payload, request.taskId(), LearningTask.Kind.WRITING);
        payload.set("consumption", mapper.valueToTree(consumption));
        long id = repository.createPending(userId, payload, now, now.plus(settings.getInt(SettingKey.GRADING_CLAIM_HOURS), ChronoUnit.HOURS));
        return detail(owned(userId, id));
    }

    public Page list(long userId, String status, int page, int size) {
        if ((status != null && !STATUSES.contains(status)) || page < 0 || size < 1 || size > 50) {
            throw error(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Bộ lọc hoặc phân trang không hợp lệ.");
        }
        long total = repository.countMine(userId, status);
        List<Summary> items = repository.findMine(userId, status, page, size).stream().map(row -> {
            String essay = essay(row);
            String preview = essay.substring(0, essay.offsetByCodePoints(0, Math.min(100, essay.codePointCount(0, essay.length()))));
            return new Summary(row.id(), reference(row.id()), preview, row.status(), row.createdAt(),
                    row.deadline(), row.completedAt(), "COMPLETED".equals(row.status()) ? row.score() : null,
                    GradingRequestViews.mode(row.payload()));
        }).toList();
        return new Page(items, page, size, total);
    }

    public Detail get(long userId, long id) { return detail(owned(userId, id)); }

    /**
     * Ties a submission to an active task of the competency tree: the teacher sees the task as the prompt and
     * the rubric grade then feeds the task's nodes. Called before the request is stored.
     */
    public void attachTask(ObjectNode payload, Long taskId, LearningTask.Kind kind) {
        if (taskId == null) return;
        LearningTask task = tasks.findById(taskId).filter(found -> found.isActive() && found.getKind() == kind)
                .orElseThrow(() -> error(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                        "Nhiệm vụ không tồn tại hoặc không đúng loại bài."));
        String prompt = task.getTitleVi() + ": " + task.getPromptVi();
        payload.put("taskId", task.getId());
        payload.put("prompt", prompt.codePointCount(0, prompt.length()) <= 500 ? prompt
                : prompt.substring(0, prompt.offsetByCodePoints(0, 500)));
        if (payload.path("hskLevel").isNull() || payload.path("hskLevel").isMissingNode()) {
            payload.put("hskLevel", (int) task.getHskLevel());
        }
    }

    @Transactional
    public Detail cancel(long userId, long id) {
        RequestRow row = owned(userId, id);
        if (!repository.cancel(id, userId, clock.instant())) {
            throw error(ErrorCode.GRADING_NOT_CANCELLABLE, HttpStatus.CONFLICT, "Bài không còn chờ nhận nên không thể huỷ.");
        }
        Consumption receipt;
        try {
            receipt = mapper.treeToValue(row.payload().get("consumption"), Consumption.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) { throw invalidStoredRequest(); }
        if (receipt == null) throw invalidStoredRequest();
        entitlement.refund(userId, receipt);
        return detail(owned(userId, id));
    }

    private RequestRow owned(long userId, long id) {
        return repository.find(id).filter(row -> row.userId() == userId).orElseThrow(() ->
                error(ErrorCode.GRADING_REQUEST_NOT_OWNED, HttpStatus.FORBIDDEN, "Không có quyền truy cập yêu cầu chấm bài."));
    }

    private Detail detail(RequestRow row) {
        JsonNode payload = row.payload();
        Result result = null;
        if ("COMPLETED".equals(row.status())) {
            if (row.score() == null || row.feedback() == null || row.result() == null) throw invalidStoredRequest();
            try {
                result = GradingRequestViews.storedResult(row.score(), row.feedback(), row.result(), mapper);
            } catch (IllegalArgumentException exception) { throw invalidStoredRequest(); }
        }
        boolean refunded = "EXPIRED".equals(row.status())
                || ("FAILED".equals(row.status()) && "CANCELLED_BY_LEARNER".equals(row.errorMessage()));
        return new Detail(row.id(), reference(row.id()), row.status(), essay(row),
                payload.path("prompt").isNull() || payload.path("prompt").isMissingNode() ? null : payload.path("prompt").asText(),
                payload.path("hskLevel").isNull() || payload.path("hskLevel").isMissingNode() ? null : payload.path("hskLevel").intValue(),
                row.createdAt(), row.deadline(), row.completedAt(), result, refunded,
                GradingRequestViews.mode(payload), GradingRequestViews.audioUrl(payload));
    }

    private String essay(RequestRow row) {
        if (!row.payload().path("essay").isTextual()) throw invalidStoredRequest();
        return row.payload().get("essay").textValue();
    }

    private String reference(long id) { return String.format(Locale.ROOT, "GR-%06d", id); }
    private static IllegalStateException invalidStoredRequest() { return new IllegalStateException("Stored grading request is invalid."); }
    private ApiException error(ErrorCode code, HttpStatus status, String message) { return new ApiException(code, status, message, Map.of()); }
}
