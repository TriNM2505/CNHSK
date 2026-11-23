package com.cnhsk.community.game;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.cnhsk.community.game.web.GameViews.RoundStart;
import com.cnhsk.community.game.web.GameViews;
import com.cnhsk.community.game.web.GameRoundRequest;
import com.cnhsk.learning.api.ContentLookup;
import com.cnhsk.learning.api.GameCode;
import com.cnhsk.learning.api.GameRoundApi;
import com.cnhsk.learning.api.GameRoundHooks;
import com.cnhsk.learning.api.GameStatsApi;
import com.cnhsk.learning.api.PracticeApi;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameRoundService {
    private final GameRegistry registry;
    private final GameRoundApi rounds;
    private final GameStatsApi stats;
    private final ContentLookup content;
    private final PracticeApi practice;
    private final Clock clock;
    private final GameContestGate contestGate;

    public GameRoundService(GameRegistry registry, GameRoundApi rounds, GameStatsApi stats,
            ContentLookup content, PracticeApi practice, Clock clock, GameContestGate contestGate) {
        this.contestGate = contestGate;
        this.registry = registry;
        this.rounds = rounds;
        this.stats = stats;
        this.content = content;
        this.practice = practice;
        this.clock = clock;
    }

    @Transactional
    public RoundStart start(long userId, GameCode code, int hskLevel) {
        return begin(userId, code, hskLevel, null);
    }

    /** A contest round mixes HSK 1–3 and is only served while the contest window is open. */
    @Transactional
    public RoundStart startContest(long userId, GameCode code, long contestId) {
        contestGate.requireOpen(contestId, code);
        return begin(userId, code, null, contestId);
    }

    private RoundStart begin(long userId, GameCode code, Integer hskLevel, Long contestId) {
        rounds.lockStart(userId);
        var now = clock.instant();
        var recent = stats.countRecentGameRounds(userId, now.minusSeconds(3600));
        if (recent.count() >= 30) {
            var remaining = Duration.between(now, recent.oldestServedAt().plusSeconds(3600));
            long retry = Math.max(1, remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1));
            throw new ApiException(ErrorCode.ROUND_LIMIT_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS,
                    "Bạn đã chơi đủ 30 ván trong một giờ, vui lòng thử lại sau.", Map.of("retryAfterSeconds", retry));
        }
        var definition = registry.find(code).orElseThrow(GameRoundService::unavailable);
        var context = new GameContext(userId, hskLevel, content);
        if (!definition.isAvailable(context)) throw unavailable();
        var items = definition.buildRound(context);
        if (items == null || items.isEmpty()) throw unavailable();
        int duration = definition.timeLimitSeconds();
        int points = definition.pointsPerCorrect();
        var uiConfig = withLives(definition.uiConfig(items), definition.maxLives());
        rounds.abandonActive(userId, code, clock.instant());
        // Abandon commits independently by design, even if the following start fails.
        var session = practice.start(new PracticeApi.StartPracticeCommand(userId, PracticeApi.PracticeSource.GAME_ROUND,
                null, code.name(), items, List.of(), Duration.ofSeconds(3L + duration + 15),
                Map.of("timeLimitSeconds", duration, "pointsPerCorrect", points, "uiConfig", uiConfig)));
        if (contestId != null) rounds.recordContest(session.sessionId(), contestId);
        else rounds.recordHskLevel(session.sessionId(), hskLevel);
        return new RoundStart(session.sessionId(), code, clock.instant(), session.servedAt(), duration,
                session.servedAt().plusSeconds(3L + duration), points, uiConfig, session.items());
    }

    public GameViews.RoundView view(long userId, long roundId) {
        var round = owned(userId, roundId);
        try {
            var session = practice.view(userId, roundId);
            var totals = rounds.totals(roundId);
            var answered = session.answered().stream().map(answer -> {
                var item = round.items().stream().filter(candidate -> candidate.no() == answer.no()).findFirst().orElseThrow();
                return new GameViews.Answered(answer.no(), answer.correct(), answer.correct() == null ? null : canonical(item));
            }).toList();
            return new GameViews.RoundView(roundId, round.gameCode(), clock.instant(), round.servedAt(),
                    round.timeLimitSeconds(), round.deadline(), round.pointsPerCorrect(), round.uiConfig(),
                    session.items(), session.status(), answered, Math.multiplyExact(totals.correct(), round.pointsPerCorrect()));
        } catch (ApiException exception) { throw translated(exception); }
    }

    public GameViews.RoundAnswerResult answer(long userId, long roundId, GameRoundRequest request) {
        var round = owned(userId, roundId);
        if (clock.instant().isAfter(round.deadline().plusSeconds(2))) {
            throw new ApiException(ErrorCode.ROUND_TIME_OVER, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Ván đã hết thời gian trả lời.", Map.of());
        }
        // GameRoundHandler refuses answers past the last life, inside the engine's locked transaction.
        int maxLives = registry.find(round.gameCode()).map(GameDefinition::maxLives).orElse(0);
        try {
            var result = practice.answer(userId, roundId, new PracticeApi.Answer(request.no(), request.answer(),
                    request.durationMs(), 0, false, false, Map.of()));
            var totals = rounds.totals(roundId);
            String correct = result.correctAnswer() != null ? result.correctAnswer()
                    : result.accept() == null || result.accept().isEmpty() ? null : result.accept().get(0);
            if (result.correct() == null || correct == null) throw new IllegalStateException("Game item was not gradable.");
            Integer livesLeft = maxLives > 0 ? (int) Math.max(0, maxLives - totals.wrong()) : null;
            return new GameViews.RoundAnswerResult(result.no(), result.correct(), correct,
                    result.correct() ? round.pointsPerCorrect() : 0,
                    Math.multiplyExact(totals.correct(), round.pointsPerCorrect()), totals.answeredCount(), livesLeft);
        } catch (ApiException exception) { throw translated(exception); }
    }

    public GameViews.RoundResult finish(long userId, long roundId) {
        owned(userId, roundId);
        try {
            var result = practice.finish(userId, roundId, List.of());
            var extra = result.extra();
            return new GameViews.RoundResult(roundId, number(extra, "points"), number(extra, "maxPoints"),
                    number(extra, "correct"), number(extra, "wrong"), number(extra, "missed"),
                    extra.get("accuracy") == null ? null : Math.toIntExact(number(extra, "accuracy")),
                    Math.toIntExact(number(extra, "durationMs")), (String) extra.get("skill"),
                    number(extra, "bestPoints"), Boolean.TRUE.equals(extra.get("newBest")), result.suspicious());
        } catch (ApiException exception) { throw translated(exception); }
    }

    public void abandon(long userId, long roundId) {
        owned(userId, roundId);
        try { rounds.abandon(userId, roundId); }
        catch (ApiException exception) { throw translated(exception); }
    }

    private GameRoundHooks.Round owned(long userId, long roundId) {
        GameRoundHooks.Round round;
        try {
            round = rounds.findOwned(userId, roundId).orElseThrow(() -> new ApiException(ErrorCode.ROUND_NOT_FOUND,
                    HttpStatus.NOT_FOUND, "Không tìm thấy ván chơi.", Map.of()));
        } catch (ApiException exception) { throw translated(exception); }
        if (round.userId() != userId) throw new ApiException(ErrorCode.ROUND_NOT_OWNED, HttpStatus.FORBIDDEN,
                "Bạn không có quyền truy cập ván chơi này.", Map.of());
        return round;
    }

    /** The client draws the lives from the served round, so the limit travels with the UI config. */
    private static Map<String, Object> withLives(Map<String, Object> uiConfig, int maxLives) {
        if (maxLives <= 0) return uiConfig;
        var merged = new java.util.LinkedHashMap<String, Object>(uiConfig);
        merged.put("maxLives", maxLives);
        return Map.copyOf(merged);
    }

    private static String canonical(PracticeApi.ServedItem item) {
        return item.answer() != null ? item.answer()
                : item.accept() == null || item.accept().isEmpty() ? null : item.accept().get(0);
    }

    private static long number(Map<String, Object> values, String key) {
        if (!(values.get(key) instanceof Number number)) throw new IllegalStateException("Invalid server game result.");
        return number.longValue();
    }

    private static ApiException translated(ApiException exception) {
        ErrorCode code = switch (exception.getCode()) {
            case ATTEMPT_NOT_OWNED -> ErrorCode.ROUND_NOT_OWNED;
            case SESSION_EXPIRED -> ErrorCode.ROUND_EXPIRED;
            case SESSION_ALREADY_SUBMITTED -> ErrorCode.ROUND_ALREADY_SUBMITTED;
            case ITEM_NOT_IN_SESSION -> ErrorCode.ITEM_NOT_IN_ROUND;
            default -> exception.getCode();
        };
        return code == exception.getCode() ? exception
                : new ApiException(code, exception.getStatus(), exception.getMessage(), exception.getExtra());
    }

    private static ApiException unavailable() {
        return new ApiException(ErrorCode.GAME_UNAVAILABLE, HttpStatus.UNPROCESSABLE_ENTITY,
                "Game chưa có đủ nội dung để chơi.", Map.of());
    }
}
