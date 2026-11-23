package com.cnhsk.community.contest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.community.contest.Contest.ScheduleState;
import com.cnhsk.community.contest.web.ContestViews.CheatEvent;
import com.cnhsk.community.contest.web.ContestViews.ContestSession;
import com.cnhsk.community.contest.web.ContestViews.SubmitResult;
import com.cnhsk.learning.api.ContestAttemptApi;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.RateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContestService {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private final ContestRepository contests;
    private final ContestAttemptApi attempts;
    private final AuthApi auth;
    private final AuditService audit;
    private final RateLimiter limiter;
    private final Clock clock;

    public ContestService(ContestRepository contests, ContestAttemptApi attempts, AuthApi auth,
            AuditService audit, RateLimiter limiter, Clock clock) {
        this.contests = contests;
        this.attempts = attempts;
        this.auth = auth;
        this.audit = audit;
        this.limiter = limiter;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ContestSession enter(long id) {
        long userId = learnerId();
        // Use the engine's exact transaction lock before reading capacity or starting/resuming.
        contests.lockStart(id);
        Contest contest = contests.findPublished(id).filter(found -> found.getExamId() != null)
                .orElseThrow(ContestService::notFound);
        auth.requireVerifiedEmail(userId);
        if (contest.getCreatedBy() == userId) throw forbidden();
        Instant now = clock.instant();
        if (contest.stateAt(now) != ScheduleState.ONGOING) {
            throw new ApiException(ErrorCode.CONTEST_NOT_OPEN, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Cuộc thi chưa mở hoặc đã kết thúc.", Map.of());
        }
        boolean existing = attempts.myAttempt(userId, id).isPresent();
        if (!existing && contest.getMaxParticipants() != null
                && attempts.countAttempts(id) >= contest.getMaxParticipants()) {
            throw new ApiException(ErrorCode.CONTEST_FULL, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Cuộc thi đã đủ số người tham gia.", Map.of());
        }
        Instant deadline = contest.getEndsAt();
        if (!existing) {
            var exam = attempts.exam(contest.getExamId()).orElseThrow(() -> new ApiException(
                    ErrorCode.CONTEST_EXAM_INVALID, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Đề thi không có câu hỏi dùng được hoặc đã lưu trữ.", Map.of()));
            if (exam.durationMinutes() > 0) {
                Instant durationEnd = now.plus(Duration.ofMinutes(exam.durationMinutes()));
                if (durationEnd.isBefore(deadline)) deadline = durationEnd;
            }
        }
        var session = attempts.start(userId, id, contest.getExamId(), deadline);
        return new ContestSession(session.sessionId(), id, session.deadline().atZone(VIETNAM).toOffsetDateTime(),
                clock.instant().atZone(VIETNAM).toOffsetDateTime(), session.resumed(), session.items());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SubmitResult submit(long id) {
        long userId = learnerId();
        contests.findPublished(id).orElseThrow(ContestService::notFound);
        return new SubmitResult(attempts.submit(userId, id).atZone(VIETNAM).toOffsetDateTime());
    }

    @Transactional
    public void event(long id, CheatEvent event) {
        long userId = learnerId();
        contests.findPublished(id).orElseThrow(ContestService::notFound);
        attempts.myAttempt(userId, id).filter(attempt -> "IN_PROGRESS".equals(attempt.status()))
                .orElseThrow(ContestService::notFound);
        if (limiter.increment("contest:tab-hidden:" + userId, Duration.ofSeconds(10)) > 1) return;
        audit.recordDetail(userId, "CONTEST_CHEAT_EVENT", "CONTEST", id, Map.of("type", event.type().name()));
    }

    private long learnerId() {
        var user = auth.currentUser();
        if (!user.roles().contains(RoleCode.LEARNER)) throw forbidden();
        return user.userId();
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.CONTEST_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy cuộc thi hoặc lượt thi.", Map.of());
    }

    private static ApiException forbidden() {
        return new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này.", Map.of());
    }
}
