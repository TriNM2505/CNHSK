package com.cnhsk.learning.quiz;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cnhsk.learning.leaderboard.LeaderboardService;
import com.cnhsk.learning.practice.PracticeService.ServedSnapshot;
import com.cnhsk.learning.practice.entity.Attempt;
import com.cnhsk.learning.practice.entity.AttemptItem;
import com.cnhsk.learning.practice.entity.AttemptItemRepository;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.learning.quiz.QuizSessionService.QuizOption;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class QuizResultService {
    private static final Logger log = LoggerFactory.getLogger(QuizResultService.class);
    private final AttemptRepository attempts;
    private final AttemptItemRepository evidence;
    private final QuizRepository quizzes;
    private final ObjectMapper mapper;
    private final ObjectProvider<LeaderboardService> leaderboards;
    private final TransactionTemplate reads;

    public QuizResultService(AttemptRepository attempts, AttemptItemRepository evidence, QuizRepository quizzes,
            ObjectMapper mapper, ObjectProvider<LeaderboardService> leaderboards, PlatformTransactionManager transactions) {
        this.attempts = attempts;
        this.evidence = evidence;
        this.quizzes = quizzes;
        this.mapper = mapper;
        this.leaderboards = leaderboards;
        reads = new TransactionTemplate(transactions);
        reads.setReadOnly(true);
        reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Suspend any caller transaction; a failed ranking lookup must not poison result reads or the caller. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public QuizResult result(long userId, long sessionId) {
        Loaded loaded = Objects.requireNonNull(reads.execute(status -> read(userId, sessionId)));
        QuizResult result = loaded.result();
        if (!loaded.lookupRank()) return result;
        // Only immutable DTOs cross the read transaction boundary. Ranking may own its own transaction.
        try {
            LeaderboardService leaderboard = leaderboards.getIfAvailable();
            if (leaderboard != null) {
                var rank = leaderboard.rankOf(userId, result.topicId());
                if (rank.isPresent()) {
                    FirstAttemptRank first = result.firstAttempt();
                    return new QuizResult(result.sessionId(), result.topicId(), result.topicName(), result.score(),
                            result.correctCount(), result.wrongCount(), result.durationMs(), result.rankingEligible(),
                            result.suspicious(), result.items(), new FirstAttemptRank(first.score(), first.durationMs(),
                                    rank.get().rank(), rank.get().total()));
                }
            }
        } catch (RuntimeException exception) {
            log.warn("Quiz ranking unavailable: sessionId={}, topicId={}", sessionId, result.topicId());
        }
        return result;
    }

    private Loaded read(long userId, long sessionId) {
        Attempt attempt = attempts.findById(sessionId).orElseThrow(QuizResultService::notOwned);
        if (!Objects.equals(attempt.getUserId(), userId)) throw notOwned();
        boolean first = "QUIZ".equals(attempt.getRefType());
        if (!first && !"QUIZ_RETRY".equals(attempt.getRefType())) {
            throw new ApiException(ErrorCode.QUIZ_ATTEMPT_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Không tìm thấy lượt quiz.", Map.of());
        }
        if (!"SUBMITTED".equals(attempt.getStatus())) {
            throw new ApiException(ErrorCode.ATTEMPT_NOT_SUBMITTED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Lượt quiz chưa được nộp.", Map.of());
        }
        ServedSnapshot snapshot = mapper.convertValue(attempt.getServedItems(), ServedSnapshot.class);
        Map<Integer, AttemptItem> byNo = evidence.findByAttemptIdOrderByOrderIndexAsc(sessionId).stream()
                .collect(Collectors.toMap(AttemptItem::getOrderIndex, Function.identity()));
        List<QuizResultItem> items = snapshot.items().stream().map(item -> {
            AttemptItem answer = byNo.get(item.no());
            String chosen = answer == null || Boolean.TRUE.equals(answer.getMeta().get("skipped"))
                    || Boolean.TRUE.equals(answer.getMeta().get("missing")) ? null : answer.getAnswer();
            return new QuizResultItem(item.no(), item.prompt().text(), item.options().stream()
                    .map(option -> new QuizOption(option.key(), option.text())).toList(),
                    chosen, item.answer(), item.explanation());
        }).toList();
        int correct = (int) snapshot.items().stream().filter(item -> {
            AttemptItem answer = byNo.get(item.no());
            return answer != null && Boolean.TRUE.equals(answer.getCorrect());
        }).count();
        var firstSubmission = first
                ? new QuizRepository.FirstSubmission(attempt.getScore(), attempt.getDurationMs(), attempt.isSuspicious())
                : quizzes.firstSubmission(userId, attempt.getRefId()).orElse(null);
        FirstAttemptRank summary = firstSubmission == null ? null
                : new FirstAttemptRank(firstSubmission.score(), firstSubmission.durationMs(), null, null);
        String topicName = quizzes.topicName(attempt.getRefId())
                .orElseThrow(() -> new IllegalStateException("Missing topic for submitted quiz."));
        var result = new QuizResult(sessionId, attempt.getRefId(), topicName, attempt.getScore(), correct,
                snapshot.items().size() - correct, attempt.getDurationMs(), first, attempt.isSuspicious(), items, summary);
        return new Loaded(result, firstSubmission != null && !firstSubmission.suspicious());
    }

    private static ApiException notOwned() {
        return new ApiException(ErrorCode.ATTEMPT_NOT_OWNED, HttpStatus.FORBIDDEN,
                "Không có quyền truy cập lượt làm bài này.", Map.of());
    }

    private record Loaded(QuizResult result, boolean lookupRank) { }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(name = "QuizResult")
    public record QuizResult(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sessionId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long topicId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String topicName,
            @Schema(minimum = "0", maximum = "100", requiredMode = Schema.RequiredMode.REQUIRED) long score,
            @Schema(minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) int correctCount,
            @Schema(minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) int wrongCount,
            @Schema(minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) int durationMs,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean rankingEligible,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean suspicious,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<QuizResultItem> items,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) FirstAttemptRank firstAttempt) {
        public QuizResult { items = List.copyOf(items); }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(name = "QuizResultItem")
    public record QuizResultItem(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long itemId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String prompt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<QuizOption> options,
            @Schema(types = {"string", "null"}, requiredMode = Schema.RequiredMode.REQUIRED) String chosen,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String correct,
            @Schema(types = {"string", "null"}, requiredMode = Schema.RequiredMode.REQUIRED) String explanation) { }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(name = "FirstAttemptRank")
    public record FirstAttemptRank(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long score,
            @Schema(types = {"integer", "null"}, requiredMode = Schema.RequiredMode.REQUIRED) Integer durationMs,
            @Schema(types = {"integer", "null"}, requiredMode = Schema.RequiredMode.REQUIRED) Long rank,
            @Schema(types = {"integer", "null"}, requiredMode = Schema.RequiredMode.REQUIRED) Long total) { }
}
