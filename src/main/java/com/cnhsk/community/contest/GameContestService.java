package com.cnhsk.community.contest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.PublicProfile;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.community.common.PostCategory;
import com.cnhsk.community.contest.web.GameContestViews.Excluded;
import com.cnhsk.community.contest.web.GameContestViews.Exclusion;
import com.cnhsk.community.contest.web.GameContestViews.GameContestCreated;
import com.cnhsk.community.contest.web.GameContestViews.GameContestWrite;
import com.cnhsk.community.contest.web.GameContestViews.Live;
import com.cnhsk.community.contest.web.GameContestViews.Phase;
import com.cnhsk.community.contest.web.GameContestViews.RankingRow;
import com.cnhsk.community.contest.web.GameContestViews.Results;
import com.cnhsk.community.contest.web.GameContestViews.Winner;
import com.cnhsk.community.game.GameContestGate;
import com.cnhsk.community.game.GameDefinition;
import com.cnhsk.community.game.GameRegistry;
import com.cnhsk.community.post.ModeratorPostService;
import com.cnhsk.community.post.web.ModeratorPostWrite;
import com.cnhsk.learning.api.GameCode;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Game contests run by moderators: a time window for one game, a live ranking everyone can watch, and once the last
 * rounds are in, the winners with contact emails for the moderator who hands out the prizes.
 */
@Service
public class GameContestService implements GameContestGate {
    /** Rows shown on the public live ranking. */
    static final int LIVE_SIZE = 50;
    /** A round started just before the close may still be finished up to 3 + 15 seconds after its play time. */
    private static final Duration SUBMIT_GRACE = Duration.ofSeconds(20);
    private static final Duration MAX_LENGTH = Duration.ofDays(31);
    /** The guide stays pinned for a day after the close so people can find the results. */
    private static final Duration PINNED_AFTER = Duration.ofDays(1);

    private final ContestRepository contests;
    private final GameContestRepository ranking;
    private final ModeratorPostService posts;
    private final GameRegistry games;
    private final AuthApi auth;
    private final AuditService audit;
    private final Clock clock;

    public GameContestService(ContestRepository contests, GameContestRepository ranking, ModeratorPostService posts,
            GameRegistry games, AuthApi auth, AuditService audit, Clock clock) {
        this.contests = contests;
        this.ranking = ranking;
        this.posts = posts;
        this.games = games;
        this.auth = auth;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public GameContestCreated create(GameContestWrite request) {
        long moderatorId = moderatorId();
        Instant now = clock.instant();
        GameCode game = gameCode(request.gameCode());
        if (games.find(game).isEmpty()) throw invalid("gameCode", "Trò chơi này chưa mở.");
        if (!request.endsAt().isAfter(request.startsAt())) throw invalid("endsAt", "Giờ đóng phải sau giờ mở.");
        if (!request.endsAt().isAfter(now)) throw invalid("endsAt", "Giờ đóng phải ở tương lai.");
        if (Duration.between(request.startsAt(), request.endsAt()).compareTo(MAX_LENGTH) > 0) {
            throw invalid("endsAt", "Cuộc thi kéo dài tối đa 31 ngày.");
        }
        Contest contest = Contest.game(request.title(), request.content(), game.name(), request.startsAt(),
                request.endsAt(), request.prizeCount(), moderatorId, now);
        contests.create(contest);
        var post = posts.create(new ModeratorPostWrite(request.title(), request.content(), PostCategory.CONTEST,
                contest.getId(), request.endsAt().plus(PINNED_AFTER), request.media(), null));
        audit.recordDetail(moderatorId, "GAME_CONTEST_CREATE", "CONTEST", contest.getId(),
                Map.of("gameCode", game.name(), "postId", post.id()));
        return new GameContestCreated(contest.getId(), post.id());
    }

    @Override
    @Transactional(readOnly = true)
    public void requireOpen(long contestId, GameCode code) {
        Contest contest = gameContest(contestId);
        if (phase(contest, clock.instant()) != Phase.OPEN) {
            throw new ApiException(ErrorCode.CONTEST_NOT_OPEN, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Cuộc thi chưa mở hoặc đã kết thúc.", Map.of());
        }
        if (!code.name().equals(contest.getGameCode())) {
            throw invalid("gameCode", "Cuộc thi này dùng trò chơi khác.");
        }
    }

    /** Public view; the ranking is withheld while the last rounds are still being submitted. */
    @Transactional(readOnly = true)
    public Live live(long id) {
        Contest contest = gameContest(id);
        Instant now = clock.instant();
        Phase phase = phase(contest, now);
        if (phase == Phase.WAITING || phase == Phase.TALLYING) return view(contest, now, phase, 0, List.of());
        var result = ranking.ranking(contest, auth.bannedUserIds(), LIVE_SIZE);
        var names = names(result.rows().stream().map(GameContestRepository.Row::userId).collect(Collectors.toSet()));
        return view(contest, now, phase, result.total(), result.rows().stream()
                .map(row -> new RankingRow(row.rank(), row.userId(), names.get(row.userId()), row.points(),
                        row.durationMs(), row.achievedAt())).toList());
    }

    /** Winners with contact emails; every read is audited because it exposes personal data. */
    @Transactional
    public Results results(long id) {
        long moderatorId = moderatorId();
        Contest contest = gameContest(id);
        Instant now = clock.instant();
        Phase phase = phase(contest, now);
        if (phase != Phase.FINISHED) {
            throw new ApiException(ErrorCode.CONTEST_LOCKED, HttpStatus.CONFLICT,
                    "Cuộc thi chưa có kết quả.", Map.of());
        }
        var top = ranking.ranking(contest, auth.bannedUserIds(), contest.getPrizeCount());
        var excludedRows = ranking.exclusions(id);
        Set<Long> ids = new java.util.HashSet<>();
        top.rows().forEach(row -> ids.add(row.userId()));
        excludedRows.forEach(row -> ids.add(row.userId()));
        var names = names(ids);
        Set<Long> winnerIds = top.rows().stream().map(GameContestRepository.Row::userId).collect(Collectors.toSet());
        Map<Long, String> emails = winnerIds.isEmpty() ? Map.of() : auth.contactEmails(winnerIds);
        var winners = top.rows().stream().map(row -> new Winner(row.rank(), row.userId(), names.get(row.userId()),
                emails.getOrDefault(row.userId(), ""), row.points(), row.durationMs(), row.achievedAt())).toList();
        var excluded = excludedRows.stream().map(row -> new Excluded(row.userId(), names.get(row.userId()),
                row.note(), row.decidedAt())).toList();
        audit.recordDetail(moderatorId, "GAME_CONTEST_WINNERS_VIEW", "CONTEST", id,
                Map.of("winnerCount", winners.size()));
        var live = view(contest, now, phase, top.total(), winners.stream().map(winner -> new RankingRow(winner.rank(),
                winner.userId(), winner.displayName(), winner.points(), winner.durationMs(), winner.achievedAt())).toList());
        return new Results(live, winners, excluded);
    }

    /** Excluding someone removes every round they played in this contest from the ranking. */
    @Transactional
    public void decide(long id, long userId, Exclusion request) {
        long moderatorId = moderatorId();
        gameContest(id);
        if (request.excluded()) ranking.exclude(id, userId, request.note().strip(), moderatorId, clock.instant());
        else ranking.include(id, userId);
        audit.recordDetail(moderatorId, request.excluded() ? "CONTEST_PARTICIPANT_EXCLUDE" : "CONTEST_PARTICIPANT_INCLUDE",
                "CONTEST", id, Map.of("userId", userId, "note", request.note().strip()));
    }

    Phase phase(Contest contest, Instant now) {
        if (now.isBefore(contest.getStartsAt())) return Phase.WAITING;
        if (now.isBefore(contest.getEndsAt())) return Phase.OPEN;
        return now.isBefore(resultsAt(contest)) ? Phase.TALLYING : Phase.FINISHED;
    }

    /** Results wait until a round started at the last moment has had time to finish and be submitted. */
    private Instant resultsAt(Contest contest) {
        int seconds = games.find(GameCode.valueOf(contest.getGameCode())).map(GameDefinition::timeLimitSeconds).orElse(0);
        return contest.getEndsAt().plusSeconds(seconds).plus(SUBMIT_GRACE);
    }

    private Live view(Contest contest, Instant now, Phase phase, long participants, List<RankingRow> rows) {
        return new Live(contest.getId(), contest.getName(), contest.getDescription(), contest.getGameCode(),
                contest.getStartsAt(), contest.getEndsAt(), resultsAt(contest), contest.getPrizeCount(), now, phase,
                participants, rows);
    }

    private Contest gameContest(long id) {
        return contests.findPublished(id).filter(contest -> contest.getGameCode() != null)
                .orElseThrow(() -> new ApiException(ErrorCode.CONTEST_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Không tìm thấy cuộc thi.", Map.of()));
    }

    private Map<Long, String> names(Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, PublicProfile> profiles = auth.publicProfiles(ids);
        return profiles.entrySet().stream().filter(entry -> entry.getValue().displayName() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().displayName()));
    }

    private long moderatorId() {
        var user = auth.currentUser();
        if (!user.roles().contains(RoleCode.MODERATOR)) {
            throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "Bạn không có quyền thực hiện thao tác này.", Map.of());
        }
        return user.userId();
    }

    private static GameCode gameCode(String value) {
        try {
            return GameCode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalid("gameCode", "Trò chơi không hợp lệ.");
        }
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Dữ liệu không hợp lệ.",
                Map.of("details", List.of(Map.of("field", field, "message", message))));
    }
}
