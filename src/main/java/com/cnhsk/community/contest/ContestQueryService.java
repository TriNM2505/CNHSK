package com.cnhsk.community.contest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.community.contest.Contest.ScheduleState;
import com.cnhsk.community.contest.web.ContestViews.ContestDetail;
import com.cnhsk.community.contest.web.ContestViews.ContestPage;
import com.cnhsk.community.contest.web.ContestViews.ContestSummary;
import com.cnhsk.community.contest.web.ContestViews.ContestResults;
import com.cnhsk.community.contest.web.ContestViews.MyResult;
import com.cnhsk.community.contest.web.ContestViews.RankingPage;
import com.cnhsk.community.contest.web.ContestViews.RankingRow;
import com.cnhsk.learning.api.ContestAttemptApi;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ContestQueryService {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private final ContestRepository contests;
    private final ContestAttemptApi attempts;
    private final Clock clock;
    private final AuthApi auth;

    public ContestQueryService(ContestRepository contests, ContestAttemptApi attempts, Clock clock, AuthApi auth) {
        this.contests = contests;
        this.attempts = attempts;
        this.clock = clock;
        this.auth = auth;
    }

    public ContestPage list(ScheduleState state, int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Phân trang không hợp lệ.", Map.of("details", List.of(
                            Map.of("field", "page/size", "message", "Trang phải từ 0, kích thước từ 1 đến 50."))));
        }
        Instant now = clock.instant();
        long total = contests.countPublished(state, now);
        if ((long) page * size >= total) return new ContestPage(List.of(), page, size, total);
        List<ContestSummary> items = contests.findPublished(state, now, page, size).stream()
                .map(contest -> summary(contest, now)).toList();
        return new ContestPage(items, page, size, total);
    }

    public ContestDetail detail(long id) {
        Contest contest = contests.findPublished(id).orElseThrow(ContestQueryService::notFound);
        var exam = attempts.exam(contest.getExamId()).orElseThrow(ContestQueryService::notFound);
        ContestSummary summary = summary(contest, clock.instant());
        return new ContestDetail(summary.id(), summary.name(), summary.startsAt(), summary.endsAt(), summary.state(),
                summary.prizes(), summary.maxParticipants(), summary.participantCount(), summary.rankingPublished(),
                contest.getDescription(), exam.title(), exam.durationMinutes(), exam.questionCount());
    }

    public ContestResults results(long id, int page, int size) {
        var user = auth.currentUser();
        if (!user.roles().contains(RoleCode.LEARNER)) {
            throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Bạn không có quyền xem kết quả.", Map.of());
        }
        if (page < 0 || size < 1 || size > 50) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Phân trang không hợp lệ.", Map.of("details", List.of(
                            Map.of("field", "page/size", "message", "Trang phải từ 0, kích thước từ 1 đến 50."))));
        }
        Contest contest = contests.findPublished(id).orElseThrow(ContestQueryService::notFound);
        boolean published = contest.getRankingPublishedAt() != null;
        MyResult mine = attempts.myAttempt(user.userId(), id).map(attempt -> new MyResult(attempt.status(),
                attempt.submittedAt() == null ? null : attempt.submittedAt().atZone(VIETNAM).toOffsetDateTime(),
                published ? attempt.score() : null, published ? attempt.maxScore() : null,
                published ? attempt.durationMs() : null,
                published ? attempts.rankOf(user.userId(), id).orElse(null) : null,
                published ? attempt.suspicious() : null)).orElse(null);
        RankingPage ranking = null;
        if (published) {
            var ranked = attempts.ranking(id, page, size);
            Set<Long> ids = ranked.items().stream().map(ContestAttemptApi.RankingRow::userId).collect(Collectors.toSet());
            var profiles = ids.isEmpty() ? Map.<Long, com.cnhsk.auth.api.PublicProfile>of() : auth.publicProfiles(ids);
            var rows = ranked.items().stream().map(row -> new RankingRow(row.rank(), row.userId(),
                    profiles.containsKey(row.userId()) ? profiles.get(row.userId()).displayName() : null,
                    row.score(), row.maxScore(), row.durationMs())).toList();
            ranking = new RankingPage(rows, ranked.page(), ranked.size(), ranked.total());
        }
        return new ContestResults(summary(contest, clock.instant()), published, mine, ranking);
    }

    private ContestSummary summary(Contest contest, Instant now) {
        return new ContestSummary(contest.getId(), contest.getName(), contest.getStartsAt().atZone(VIETNAM).toOffsetDateTime(),
                contest.getEndsAt().atZone(VIETNAM).toOffsetDateTime(), contest.stateAt(now), contest.getPrizes(),
                contest.getMaxParticipants(), attempts.countAttempts(contest.getId()), contest.getRankingPublishedAt() != null);
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.CONTEST_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy cuộc thi.", Map.of());
    }
}
