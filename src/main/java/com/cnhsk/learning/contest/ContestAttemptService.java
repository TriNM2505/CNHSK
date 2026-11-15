package com.cnhsk.learning.contest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.cnhsk.learning.api.ContestAttemptApi.ContestSession;
import com.cnhsk.learning.api.ContestAttemptApi.ExamSummary;
import com.cnhsk.learning.api.PracticeApi;
import com.cnhsk.learning.contest.ContestRepository.ContestAttempt;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeService.StartPracticeCommand;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.settings.SettingKey;
import com.cnhsk.shared.settings.SettingsApi;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ContestAttemptService {
    private final ContestRepository contests;
    private final PracticeService practice;
    private final PracticeApi practiceApi;
    private final SettingsApi settings;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public ContestAttemptService(ContestRepository contests, PracticeService practice, PracticeApi practiceApi,
            SettingsApi settings, Clock clock, PlatformTransactionManager transactionManager) {
        this.contests = contests;
        this.practice = practice;
        this.practiceApi = practiceApi;
        this.settings = settings;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public ContestSession start(long userId, long contestId, long examId, Instant deadline) {
        int grace = settings.getInt(SettingKey.CONTEST_SUBMIT_GRACE_SECONDS);
        return transactions.execute(status -> {
            contests.lockStart(contestId);
            Optional<ContestAttempt> existing = contests.attempt(userId, contestId);
            if (existing.isPresent()) {
                ContestAttempt attempt = existing.get();
                if (!"IN_PROGRESS".equals(attempt.status()) || attempt.expiresAt() == null
                        || attempt.expiresAt().isBefore(clock.instant())) {
                    throw alreadyAttempted();
                }
                return session(userId, attempt.id(), attempt.deadline(), true);
            }

            var exam = contests.exam(examId).orElseThrow(ContestAttemptService::invalidExam);
            if (!List.of("DRAFT", "PUBLISHED").contains(exam.status())) throw invalidExam();
            List<ServedItem> items = contests.items(examId);
            if (items.isEmpty()) throw invalidExam();
            Instant expiry = deadline.plusSeconds(grace);
            Duration lifetime = Duration.between(clock.instant(), expiry);
            if (lifetime.isNegative() || lifetime.isZero()) throw notOpen();
            Map<String, Object> snapshot = Map.of("contest", Map.of("contestId", contestId, "examId", examId,
                    "deadline", deadline.toString(), "submitGraceSeconds", grace));
            var created = practice.start(new StartPracticeCommand(userId, PracticeSource.CONTEST, contestId,
                    null, items, List.of(), lifetime, snapshot));
            // The engine reads the clock again; keep the absolute server deadline exact.
            contests.setExpiry(created.sessionId(), expiry);
            return session(userId, created.sessionId(), deadline, false);
        });
    }

    @Transactional(readOnly = true)
    public long countAttempts(long contestId) {
        return contests.countAttempts(contestId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Instant submit(long userId, long contestId) {
        ContestAttempt attempt = contests.attempt(userId, contestId).orElseThrow(() -> new ApiException(
                ErrorCode.CONTEST_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy lượt thi.", Map.of()));
        if ("EXPIRED".equals(attempt.status())) throw notOpen();
        if (!"IN_PROGRESS".equals(attempt.status())) throw alreadyAttempted();
        try {
            practice.finish(userId, attempt.id(), List.of());
        } catch (ApiException exception) {
            if (exception.getCode() == ErrorCode.SESSION_EXPIRED) throw notOpen();
            if (exception.getCode() == ErrorCode.SESSION_ALREADY_SUBMITTED) throw alreadyAttempted();
            throw exception;
        }
        return contests.attempt(userId, contestId).orElseThrow().submittedAt();
    }

    @Transactional(readOnly = true)
    public Optional<ExamSummary> exam(long examId) {
        return contests.exam(examId).map(exam -> new ExamSummary(exam.id(), exam.title(), exam.status(),
                exam.durationMinutes(), contests.items(examId).size()));
    }

    private ContestSession session(long userId, long sessionId, Instant deadline, boolean resumed) {
        return new ContestSession(sessionId, deadline, resumed, practiceApi.view(userId, sessionId).items());
    }

    private static ApiException invalidExam() {
        return new ApiException(ErrorCode.CONTEST_EXAM_INVALID, HttpStatus.UNPROCESSABLE_ENTITY,
                "Đề thi không có câu hỏi dùng được hoặc đã lưu trữ.", Map.of());
    }

    private static ApiException alreadyAttempted() {
        return new ApiException(ErrorCode.CONTEST_ALREADY_ATTEMPTED, HttpStatus.CONFLICT,
                "Bạn đã có một lượt thi cuộc thi này.", Map.of());
    }

    private static ApiException notOpen() {
        return new ApiException(ErrorCode.CONTEST_NOT_OPEN, HttpStatus.UNPROCESSABLE_ENTITY,
                "Đã hết thời gian nộp bài.", Map.of());
    }
}
