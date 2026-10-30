package com.cnhsk.learning.vocab;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.vocab.TopicQueryRepository.TopicRow;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TopicQueryService {

    private static final Duration RETRY_DELAY = Duration.ofMinutes(10);
    private final TopicQueryRepository repository;
    private final Clock clock;

    public TopicQueryService(TopicQueryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public List<TopicSummary> list(long userId, Integer hskLevel) {
        return repository.summaries(userId, hskLevel, null).stream()
                .map(TopicQueryService::summary).toList();
    }

    public TopicDetail detail(long userId, long id) {
        Instant now = clock.instant();
        TopicRow row = repository.summaries(userId, null, id).stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.TOPIC_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Không tìm thấy chủ đề hoặc chủ đề chưa có từ.", Map.of()));
        TopicSummary header = summary(row);
        return new TopicDetail(header.id(), header.code(), header.name(), header.hskLevel(), header.totalWords(),
                header.learnedWords(), header.percent(), header.status(), header.bestTestPercent(),
                repository.words(userId, id), actions(row, now));
    }

    private static TopicSummary summary(TopicRow row) {
        String status = row.completed() ? "COMPLETED"
                : row.learnedWords() > 0 || row.hasAttempt() ? "IN_PROGRESS" : "NOT_STARTED";
        return new TopicSummary(row.id(), row.code(), row.name(), row.hskLevel(), row.totalWords(),
                row.learnedWords(), (int) (row.learnedWords() * 100 / row.totalWords()), status, row.bestPercent());
    }

    private static TopicActions actions(TopicRow row, Instant now) {
        String reason = null;
        Instant retryAt = null;
        if (row.activeTestExpiresAt() == null || row.activeTestExpiresAt().isBefore(now)) {
            if (row.totalWords() < 4) {
                reason = "TOO_FEW_WORDS";
            } else if (row.learnedWords() * 100 < 80 * row.totalWords()) {
                reason = "NOT_ENOUGH_LEARNED";
            } else if (row.failedAt() != null && now.isBefore(row.failedAt().plus(RETRY_DELAY))) {
                reason = "RETRY_TOO_SOON";
                retryAt = row.failedAt().plus(RETRY_DELAY);
            }
        }
        return new TopicActions(row.learnedWords() < row.totalWords(), row.learnedWords() >= 1,
                row.learnedAudio() >= 1, reason == null, reason, (row.totalWords() * 80 + 99) / 100, retryAt);
    }

    @Schema(name = "TopicSummary")
    public record TopicSummary(long id, String code, String name, int hskLevel, long totalWords,
                               long learnedWords, int percent,
                               @Schema(allowableValues = {"NOT_STARTED", "IN_PROGRESS", "COMPLETED"}) String status,
                               Integer bestTestPercent) {
    }

    @Schema(name = "TopicDetail")
    public record TopicDetail(long id, String code, String name, int hskLevel, long totalWords,
                              long learnedWords, int percent,
                              @Schema(allowableValues = {"NOT_STARTED", "IN_PROGRESS", "COMPLETED"}) String status,
                              Integer bestTestPercent, List<TopicWord> words, TopicActions actions) {
    }

    public record TopicWord(long lexemeId, String text, String pinyin, String sinoViet, String meaning,
                            Integer hskLevel, String audioUrl, boolean learned) {
    }

    public record TopicActions(boolean canStudy, boolean canPracticeRecognition, boolean canPracticeListening,
                               boolean canTakeTest, String testBlockedReason, long requiredWords,
                               Instant retryAvailableAt) {
    }
}
