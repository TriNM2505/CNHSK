package com.cnhsk.community.contest;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.PublicProfile;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.community.contest.Contest.Prize;
import com.cnhsk.community.contest.Contest.ScheduleState;
import com.cnhsk.community.contest.web.ContestViews.AdminContest;
import com.cnhsk.community.contest.web.ContestViews.AdminContestPage;
import com.cnhsk.community.contest.web.ContestViews.ContestStatus;
import com.cnhsk.community.contest.web.ContestViews.ContestWrite;
import com.cnhsk.community.contest.web.ContestViews.Participant;
import com.cnhsk.community.contest.web.ContestViews.ParticipantDecision;
import com.cnhsk.community.contest.web.ContestViews.Winner;
import com.cnhsk.learning.api.ContestAttemptApi;
import com.cnhsk.learning.api.ContestAttemptApi.ContestParticipant;
import com.cnhsk.learning.api.ContestAttemptApi.ExamSummary;
import com.cnhsk.learning.api.ContestAttemptApi.RankingRow;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class ContestAdminService {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private final ContestRepository contests;
    private final ContestAttemptApi attempts;
    private final AuthApi auth;
    private final AuditService audit;
    private final Clock clock;

    public ContestAdminService(ContestRepository contests, ContestAttemptApi attempts, AuthApi auth,
            AuditService audit, Clock clock) {
        this.contests = contests;
        this.attempts = attempts;
        this.auth = auth;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminContestPage list(ContestStatus status, int page, int size) {
        editorId();
        if (page < 0) throw invalid("page");
        if (size < 1 || size > 50) throw invalid("size");
        String filter = status == null ? null : status.name();
        long total = contests.countAll(filter);
        Instant now = clock.instant();
        List<AdminContest> items = (long) page * size >= total ? List.of()
                : contests.findAll(filter, page, size).stream().map(contest -> view(contest, now)).toList();
        return new AdminContestPage(items, page, size, total);
    }

    @Transactional(readOnly = true)
    public AdminContest detail(long id) {
        editorId();
        return view(contests.find(id).orElseThrow(ContestAdminService::notFound), clock.instant());
    }

    public AdminContest create(ContestWrite input) {
        long actor = editorId();
        validateTimes(input);
        if (input.examId() != null) {
            contests.lockExamUsage(input.examId());
            if (attempts.exam(input.examId()).isEmpty()) throw invalid("examId");
        }
        Instant now = clock.instant();
        Contest contest = new Contest(input.name(), input.description(), input.examId(), input.startsAt().toInstant(),
                input.endsAt().toInstant(), input.maxParticipants(), prizes(input), actor, now);
        contests.create(contest);
        return view(contest, now);
    }

    public AdminContest update(long id, ContestWrite input) {
        editorId();
        Contest contest = lockForWrite(id, input.examId());
        Instant now = clock.instant();
        boolean published = "PUBLISHED".equals(contest.getStatus());
        if ("CANCELLED".equals(contest.getStatus()) || published && !now.isBefore(contest.getStartsAt())) {
            throw locked();
        }
        validateTimes(input);
        if (published) {
            if (input.startsAt().toInstant().isBefore(now)) throw invalid("startsAt");
            requireExam(input.examId());
        } else if (input.examId() != null && attempts.exam(input.examId()).isEmpty()) {
            throw invalid("examId");
        }
        contest.update(input.name(), input.description(), input.examId(), input.startsAt().toInstant(),
                input.endsAt().toInstant(), input.maxParticipants(), prizes(input));
        return view(contest, now);
    }

    public AdminContest publish(long id) {
        editorId();
        Contest contest = lockForWrite(id, null);
        if (!"DRAFT".equals(contest.getStatus())) throw locked();
        Instant now = clock.instant();
        requireExam(contest.getExamId());
        if (!contest.getStartsAt().isAfter(now)) throw invalid("startsAt");
        contest.publish();
        return view(contest, now);
    }

    public AdminContest cancel(long id) {
        editorId();
        contests.lockStart(id);
        Contest contest = contests.findForUpdate(id).orElseThrow(ContestAdminService::notFound);
        if (!List.of("DRAFT", "PUBLISHED").contains(contest.getStatus())) throw locked();
        if (attempts.countAttempts(id) > 0) {
            throw conflict(ErrorCode.CONTEST_HAS_ATTEMPTS, "Cuộc thi đã có người tham gia.");
        }
        contest.cancel();
        return view(contest, clock.instant());
    }

    @Transactional(readOnly = true)
    public List<Participant> participants(long id) {
        editorId();
        Contest contest = contests.find(id).orElseThrow(ContestAdminService::notFound);
        requireEnded(contest);
        List<ContestParticipant> rows = attempts.participants(id);
        var profiles = auth.publicProfiles(rows.stream().map(ContestParticipant::userId).collect(Collectors.toSet()));
        Map<Long, Long> events = audit.countByActors("CONTEST_CHEAT_EVENT", "CONTEST", id);
        return rows.stream().map(row -> participant(row,
                profiles.containsKey(row.userId()) ? profiles.get(row.userId()).displayName() : null,
                events.getOrDefault(row.userId(), 0L))).toList();
    }

    public Participant decide(long id, long userId, ParticipantDecision input) {
        long actor = editorId();
        Contest contest = contests.findForUpdate(id).orElseThrow(ContestAdminService::notFound);
        requireEnded(contest);
        if (contest.getRankingPublishedAt() != null) throw locked();
        attempts.setExcluded(id, userId, input.excluded());
        audit.recordDetail(actor, input.excluded() ? "CONTEST_PARTICIPANT_EXCLUDE" : "CONTEST_PARTICIPANT_INCLUDE",
                "CONTEST", id, Map.of("userId", userId, "excluded", input.excluded(), "note", input.note()));
        var row = attempts.participants(id).stream().filter(item -> item.userId() == userId)
                .findFirst().orElseThrow(ContestAdminService::notFound);
        var profile = auth.publicProfiles(java.util.Set.of(userId)).get(userId);
        return participant(row, profile == null ? null : profile.displayName(),
                audit.countByActors("CONTEST_CHEAT_EVENT", "CONTEST", id).getOrDefault(userId, 0L));
    }

    public AdminContest publishRanking(long id) {
        long actor = editorId();
        Contest contest = contests.findForUpdate(id).orElseThrow(ContestAdminService::notFound);
        if (!"PUBLISHED".equals(contest.getStatus()) || contest.getRankingPublishedAt() != null) throw locked();
        Instant now = clock.instant();
        if (now.isBefore(contest.getEndsAt())) {
            throw conflict(ErrorCode.CONTEST_RANKING_TOO_EARLY, "Cuộc thi chưa kết thúc.");
        }
        contest.publishRanking(now);
        audit.recordDetail(actor, "CONTEST_RANKING_PUBLISH", "CONTEST", id,
                Map.of("rankingPublishedAt", now.toString()));
        return view(contest, now);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public List<Winner> winners(long id) {
        long actor = editorId();
        Contest contest = contests.find(id).orElseThrow(ContestAdminService::notFound);
        if (!"PUBLISHED".equals(contest.getStatus()) || contest.getRankingPublishedAt() == null) throw locked();
        List<Prize> prizes = contest.getPrizes().stream().filter(prize -> prize.fromRank() <= prize.toRank()).toList();
        int lastRank = prizes.stream().mapToInt(Prize::toRank).max().orElse(0);
        Map<RankingRow, String> awarded = new LinkedHashMap<>();
        for (int page = 0; (long) page * 50 < lastRank; page++) {
            var ranking = attempts.ranking(id, page, 50);
            for (RankingRow row : ranking.items()) {
                if (row.rank() > lastRank) break;
                prizes.stream().filter(prize -> row.rank() >= prize.fromRank() && row.rank() <= prize.toRank())
                        .findFirst().ifPresent(prize -> awarded.put(row, prize.description()));
            }
            if (ranking.items().isEmpty() || ((long) page + 1) * 50 >= ranking.total()) break;
        }
        var ids = awarded.keySet().stream().map(RankingRow::userId).collect(Collectors.toSet());
        var profiles = ids.isEmpty() ? Map.<Long, PublicProfile>of() : auth.publicProfiles(ids);
        var emails = ids.isEmpty() ? Map.<Long, String>of() : auth.contactEmails(ids);
        List<Winner> result = new ArrayList<>();
        awarded.forEach((row, prize) -> {
            String email = emails.get(row.userId());
            if (email == null) throw new IllegalStateException("Không tìm thấy liên hệ người đạt giải.");
            PublicProfile profile = profiles.get(row.userId());
            result.add(new Winner(row.rank(), row.userId(), profile == null ? null : profile.displayName(), email,
                    row.score(), row.durationMs(), prize));
        });
        audit.recordDetail(actor, "CONTEST_WINNERS_VIEW", "CONTEST", id, Map.of("winnerCount", result.size()));
        return List.copyOf(result);
    }

    private Contest lockForWrite(long id, Long replacementExamId) {
        // All contest writers share the entrant's lock, so examId cannot change between reads.
        contests.lockStart(id);
        var reference = contests.reference(id).orElseThrow(ContestAdminService::notFound);
        var examIds = new TreeSet<Long>();
        if (reference.examId() != null) examIds.add(reference.examId());
        if (replacementExamId != null) examIds.add(replacementExamId);
        // Both old and new exams use the same order; archive takes this lock before its row lock.
        examIds.forEach(contests::lockExamUsage);
        return contests.findForUpdate(id).orElseThrow(ContestAdminService::notFound);
    }

    private ExamSummary requireExam(Long id) {
        if (id == null) throw invalidExam();
        var exam = attempts.exam(id).orElseThrow(ContestAdminService::invalidExam);
        if (!List.of("DRAFT", "PUBLISHED").contains(exam.status()) || exam.questionCount() == 0) throw invalidExam();
        return exam;
    }

    private void requireEnded(Contest contest) {
        if (contest.stateAt(clock.instant()) != ScheduleState.ENDED) throw locked();
    }

    private AdminContest view(Contest contest, Instant now) {
        String title = contest.getExamId() == null ? null : attempts.exam(contest.getExamId()).map(ExamSummary::title).orElse(null);
        return new AdminContest(contest.getId(), contest.getName(), contest.getDescription(), contest.getExamId(), title,
                time(contest.getStartsAt()), time(contest.getEndsAt()), contest.getMaxParticipants(), contest.getPrizes(),
                ContestStatus.valueOf(contest.getStatus()), contest.stateAt(now), time(contest.getRankingPublishedAt()),
                attempts.countAttempts(contest.getId()));
    }

    private static Participant participant(ContestParticipant row, String name, long events) {
        return new Participant(row.userId(), name, row.status(), row.score(), row.maxScore(), row.durationMs(),
                time(row.submittedAt()), row.suspicious(), events);
    }

    private long editorId() {
        var user = auth.currentUser();
        if (!user.roles().contains(RoleCode.CONTENT_EDITOR)) {
            throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Bạn không có quyền quản trị cuộc thi.", Map.of());
        }
        return user.userId();
    }

    private static List<Prize> prizes(ContestWrite input) {
        return input.prizes() == null ? List.of() : input.prizes();
    }

    private static void validateTimes(ContestWrite input) {
        if (!input.startsAt().toInstant().isBefore(input.endsAt().toInstant())) throw invalid("endsAt");
    }

    private static OffsetDateTime time(Instant instant) {
        return instant == null ? null : instant.atZone(VIETNAM).toOffsetDateTime();
    }

    private static ApiException invalid(String field) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Dữ liệu yêu cầu không hợp lệ.",
                Map.of("details", List.of(Map.of("field", field, "message", "Giá trị không hợp lệ."))));
    }

    private static ApiException invalidExam() {
        return new ApiException(ErrorCode.CONTEST_EXAM_INVALID, HttpStatus.UNPROCESSABLE_ENTITY,
                "Đề thi không có câu hỏi dùng được hoặc đã lưu trữ.", Map.of());
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.CONTEST_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy cuộc thi hoặc thí sinh.", Map.of());
    }

    private static ApiException locked() {
        return conflict(ErrorCode.CONTEST_LOCKED, "Cuộc thi đã bị khóa thao tác này.");
    }

    private static ApiException conflict(ErrorCode code, String message) {
        return new ApiException(code, HttpStatus.CONFLICT, message, Map.of());
    }
}
