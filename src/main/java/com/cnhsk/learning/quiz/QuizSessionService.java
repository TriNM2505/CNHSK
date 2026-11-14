package com.cnhsk.learning.quiz;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeService.SessionView;
import com.cnhsk.learning.practice.PracticeService.StartPracticeCommand;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.RateLimiter;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizSessionService {
    private final QuizRepository repository;
    private final QuizQuestionPicker picker;
    private final QuizConfig config;
    private final PracticeService practice;
    private final RateLimiter limiter;
    private final Clock clock;

    public QuizSessionService(QuizRepository repository, QuizQuestionPicker picker, QuizConfig config,
            PracticeService practice, RateLimiter limiter, Clock clock) {
        this.repository = repository;
        this.picker = picker;
        this.config = config;
        this.practice = practice;
        this.limiter = limiter;
        this.clock = clock;
    }

    @Transactional
    public Started start(long userId, long topicId) {
        if (topicId <= 0) throw QuizCatalog.invalid("topicId");
        repository.lockStart(userId);
        var active = repository.active(userId, topicId, clock.instant());
        if (active.isPresent()) return new Started(false, session(practice.view(userId, active.get())));
        if (!repository.topicExists(topicId)) {
            throw new ApiException(ErrorCode.TOPIC_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Không tìm thấy chủ đề.", Map.of());
        }
        List<ServedItem> items = picker.pick(topicId, config.getQuestionCount());
        // Shared Redis fixed-window policy; failures propagate just as RateLimiter defines.
        if (limiter.increment("quiz:start:" + userId, Duration.ofHours(1)) > 30) {
            throw new ApiException(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS,
                    "Bạn đã bắt đầu quá nhiều lượt quiz. Vui lòng thử lại sau.", Map.of());
        }
        PracticeSource source = repository.hasFirstSubmission(userId, topicId, -1)
                ? PracticeSource.QUIZ_RETRY : PracticeSource.QUIZ;
        return new Started(true, session(practice.start(new StartPracticeCommand(userId, source, topicId, null,
                items, List.of(), null, Map.of()))));
    }

    private static QuizSession session(SessionView view) {
        return new QuizSession(view.sessionId(), view.refId(), view.source() == PracticeSource.QUIZ,
                view.expiresAt(), view.items().stream().map(item -> new QuizItem(item.no(), item.prompt().text(),
                        item.options().stream().map(option -> new QuizOption(option.key(), option.text())).toList(),
                        item.prompt().audioUrl())).toList());
    }

    static ApiException unavailable() {
        return new ApiException(ErrorCode.QUIZ_NOT_AVAILABLE, HttpStatus.UNPROCESSABLE_ENTITY,
                "Chủ đề chưa có đủ câu hỏi quiz được hỗ trợ.", Map.of());
    }

    public record Started(boolean created, QuizSession session) { }

    @Schema(name = "QuizSession")
    public record QuizSession(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sessionId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long topicId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean rankingEligible,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<QuizItem> items) { }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(name = "QuizItem")
    public record QuizItem(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long itemId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String prompt,
            @ArraySchema(minItems = 2, arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED))
            List<QuizOption> options,
            @Schema(types = {"string", "null"}) String audioUrl) { }

    @Schema(name = "QuizOption")
    public record QuizOption(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String key,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String text) { }
}
