package com.cnhsk.learning.hanzi;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.practice.FeedbackMode;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeService.FinishResult;
import com.cnhsk.learning.practice.PracticeService.StartPracticeCommand;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler.Answer;
import com.cnhsk.learning.practice.Skill;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WritingService {
    private static final Logger log = LoggerFactory.getLogger(WritingService.class);
    private static final int MAX_ATTEMPTS_PER_HOUR = 300;
    private static final Duration COUNTER_WINDOW = Duration.ofHours(1);
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZoneOffset.UTC);

    private final CharacterQueryRepository characters;
    private final PracticeService practice;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public WritingService(CharacterQueryRepository characters, PracticeService practice, RateLimiter rateLimiter,
            Clock clock) {
        this.characters = characters;
        this.practice = practice;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @Transactional
    public WritingResult record(long userId, WritingAttemptRequest request) {
        RequestValues values = validateRequest(request);
        Lexeme lexeme = characters.findCharacter(values.lexemeId());
        if (lexeme == null) {
            throw failure(ErrorCode.CHARACTER_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy chữ.");
        }
        if (lexeme.getStrokeCount() == null) {
            throw failure(ErrorCode.STROKE_DATA_MISSING, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Chữ chưa có dữ liệu số nét.");
        }

        if (values.accuracy().compareTo(BigDecimal.ZERO) < 0 || values.accuracy().compareTo(BigDecimal.ONE) > 0) {
            throw failure(ErrorCode.INVALID_ACCURACY, HttpStatus.BAD_REQUEST, "Độ chính xác không hợp lệ.");
        }
        if (values.strokeMisses() > (long) lexeme.getStrokeCount() * 10) {
            throw validation("strokeMisses");
        }

        validateImpossibleDuration(values, userId, lexeme);
        validateMode(values, userId, lexeme);
        validateStrokeEvidence(values);

        Instant now = clock.instant();
        enforceRateLimit(userId, now);
        boolean repeatWithin10Min = characters.repeatWithin10Min(userId, lexeme.getId(), values.mode().name(),
                now);
        Map<String, Object> clientMeta = metadata(values, lexeme, repeatWithin10Min);
        boolean hasAudio = lexeme.getAudioUrl() != null && !lexeme.getAudioUrl().isBlank();
        var prompt = new ServedItem.Prompt(lexeme.getText(), null, null, lexeme.getMeaningVi(), null,
                null, null, null, "CHARACTER");
        var item = new ServedItem(1, ItemType.WRITING_RESULT, Skill.WRITING, lexeme.getId(), prompt,
                null, null, null, null, new ServedItem.Rules(0, 3, hasAudio));
        String answerText = values.completed() ? "COMPLETED" : "INCOMPLETE";
        Answer answer = new Answer(1, answerText, values.durationMs(), values.replayCount(), false, false, clientMeta);
        var command = new StartPracticeCommand(userId, PracticeSource.CHAR_WRITING, lexeme.getId(), null,
                List.of(item), List.of(answer), null, Map.of());
        FinishResult finished = practice.recordAndFinish(command);
        boolean counted = values.completed() && !repeatWithin10Min;
        return new WritingResult(finished.sessionId(), true, counted,
                characters.recallUnlocked(userId, lexeme.getId()));
    }

    private RequestValues validateRequest(WritingAttemptRequest request) {
        if (request == null || request.lexemeId() == null || request.mode() == null || request.completed() == null
                || request.accuracy() == null || request.strokeMisses() == null || request.durationMs() == null) {
            throw validation("request");
        }
        if (request.lexemeId() <= 0) {
            throw validation("lexemeId");
        }
        WritingMode mode;
        try {
            mode = WritingMode.valueOf(request.mode());
        } catch (IllegalArgumentException exception) {
            throw validation("mode");
        }
        int previewCount = request.previewCount() == null ? 0 : request.previewCount();
        int previewMs = request.previewMs() == null ? 0 : request.previewMs();
        int replayCount = request.replayCount() == null ? 0 : request.replayCount();
        boolean usedAnimation = Boolean.TRUE.equals(request.usedAnimation());
        if (request.strokeMisses() < 0 || request.durationMs() < 0 || request.durationMs() > 3_600_000
                || previewCount < 0 || previewMs < 0 || replayCount < 0) {
            throw validation("request");
        }
        return new RequestValues(request.lexemeId(), mode, request.completed(), request.accuracy(),
                request.strokeMisses(), request.durationMs(), usedAnimation, previewCount, previewMs, replayCount);
    }

    private void validateMode(RequestValues values, long userId, Lexeme lexeme) {
        switch (values.mode()) {
            case STROKE -> { }
            case RECALL -> {
                if (!characters.recallUnlocked(userId, lexeme.getId())) {
                    throw failure(ErrorCode.MODE_LOCKED, HttpStatus.FORBIDDEN,
                            "Hãy hoàn thành chế độ viết theo nét trước.");
                }
                if (values.usedAnimation()) {
                    throw failure(ErrorCode.HINT_USED_IN_RECALL, HttpStatus.BAD_REQUEST,
                            "Không được dùng hoạt ảnh trong chế độ nhớ rồi viết.");
                }
                if (values.previewCount() > 2 || values.previewMs() > 11_000) {
                    throw failure(ErrorCode.PREVIEW_TOO_LONG, HttpStatus.BAD_REQUEST,
                            "Đã vượt giới hạn xem mẫu.");
                }
            }
            case DICTATION -> {
                if (lexeme.getAudioUrl() == null || lexeme.getAudioUrl().isBlank()) {
                    throw failure(ErrorCode.AUDIO_NOT_AVAILABLE, HttpStatus.UNPROCESSABLE_ENTITY,
                            "Chữ chưa có âm thanh.");
                }
                if (values.replayCount() > 3) {
                    throw failure(ErrorCode.TOO_MANY_REPLAYS, HttpStatus.BAD_REQUEST,
                            "Đã vượt số lần nghe cho phép.");
                }
                if (values.usedAnimation()) {
                    throw failure(ErrorCode.CHARACTER_REVEALED, HttpStatus.BAD_REQUEST,
                            "Không được hiện chữ trong chế độ nghe chép.");
                }
            }
        }
    }

    private void validateStrokeEvidence(RequestValues values) {
        if (values.mode() == WritingMode.STROKE
                && (values.previewCount() != 0 || values.replayCount() != 0)) {
            throw validation("previewCount");
        }
    }

    private void validateImpossibleDuration(RequestValues values, long userId, Lexeme lexeme) {
        if (!values.completed()) {
            return;
        }
        int minimum = lexeme.getStrokeCount() * 200;
        if (values.mode() == WritingMode.DICTATION) {
            minimum = Math.max(minimum, 1500);
        }
        if (values.durationMs() < minimum) {
            log.warn("Impossible writing duration userId={} lexemeId={}", userId, lexeme.getId());
            throw failure(ErrorCode.IMPOSSIBLE_DURATION, HttpStatus.BAD_REQUEST,
                    "Thời gian viết không hợp lý.");
        }
    }

    private void enforceRateLimit(long userId, Instant now) {
        long retryAfterSeconds = Math.max(1, (Duration.between(now,
                now.truncatedTo(ChronoUnit.HOURS).plus(1, ChronoUnit.HOURS)).toMillis() + 999) / 1000);
        long count;
        try {
            count = rateLimiter.increment("writing:" + userId + ":" + HOUR.format(now), COUNTER_WINDOW);
        } catch (DataAccessException | IllegalStateException exception) {
            log.warn("Writing rate limit unavailable; allowing request");
            return;
        }
        if (count > MAX_ATTEMPTS_PER_HOUR) {
            throw new ApiException(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS,
                    "Bạn đã ghi quá nhiều lượt viết. Vui lòng thử lại sau.",
                    Map.of("retryAfterSeconds", retryAfterSeconds));
        }
    }

    private Map<String, Object> metadata(RequestValues values, Lexeme lexeme, boolean repeatWithin10Min) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("mode", values.mode().name());
        metadata.put("completed", values.completed());
        metadata.put("accuracy", values.accuracy());
        metadata.put("strokeMisses", values.strokeMisses());
        metadata.put("strokeCount", lexeme.getStrokeCount());
        metadata.put("usedAnimation", values.usedAnimation());
        metadata.put("previewCount", values.previewCount());
        metadata.put("previewMs", values.previewMs());
        metadata.put("replayCount", values.replayCount());
        metadata.put("repeatWithin10Min", repeatWithin10Min);
        metadata.put("coefficientProposal", coefficient(values));
        return metadata;
    }

    private BigDecimal coefficient(RequestValues values) {
        if (!values.completed()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal result = switch (values.mode()) {
            case STROKE -> values.usedAnimation() ? new BigDecimal("0.25") : new BigDecimal("0.50");
            case RECALL -> new BigDecimal("1.00").subtract(new BigDecimal("0.25")
                    .multiply(BigDecimal.valueOf(values.previewCount())));
            case DICTATION -> new BigDecimal("1.20").subtract(new BigDecimal("0.20")
                    .multiply(BigDecimal.valueOf(Math.max(0, values.replayCount() - 1))));
        };
        return result.setScale(2, RoundingMode.HALF_UP);
    }

    private ApiException validation(String field) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Dữ liệu không hợp lệ.", Map.of("details", List.of(Map.of("field", field,
                        "message", "Giá trị không hợp lệ."))));
    }

    private ApiException failure(ErrorCode code, HttpStatus status, String message) {
        return new ApiException(code, status, message, Map.of());
    }

    public record WritingAttemptRequest(Long lexemeId, String mode, Boolean completed, BigDecimal accuracy,
            Integer strokeMisses, Integer durationMs, Boolean usedAnimation, Integer previewCount, Integer previewMs,
            Integer replayCount) {
    }

    public record WritingResult(long attemptId, boolean recorded, boolean counted, boolean recallUnlocked) {
    }

    private record RequestValues(long lexemeId, WritingMode mode, boolean completed, BigDecimal accuracy,
            int strokeMisses, int durationMs, boolean usedAnimation, int previewCount, int previewMs, int replayCount) {
    }

    private enum WritingMode {
        STROKE,
        RECALL,
        DICTATION
    }
}
