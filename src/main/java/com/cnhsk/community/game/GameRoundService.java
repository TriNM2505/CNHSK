package com.cnhsk.community.game;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.cnhsk.community.game.web.GameViews.RoundStart;
import com.cnhsk.learning.api.ContentLookup;
import com.cnhsk.learning.api.GameCode;
import com.cnhsk.learning.api.GameRoundApi;
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

    public GameRoundService(GameRegistry registry, GameRoundApi rounds, GameStatsApi stats,
            ContentLookup content, PracticeApi practice, Clock clock) {
        this.registry = registry;
        this.rounds = rounds;
        this.stats = stats;
        this.content = content;
        this.practice = practice;
        this.clock = clock;
    }

    @Transactional
    public RoundStart start(long userId, GameCode code, Integer hskLevel) {
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
        var uiConfig = definition.uiConfig();
        rounds.abandonActive(userId, code, clock.instant());
        // Abandon commits independently by design, even if the following start fails.
        var session = practice.start(new PracticeApi.StartPracticeCommand(userId, PracticeApi.PracticeSource.GAME_ROUND,
                null, code.name(), items, List.of(), Duration.ofSeconds(3L + duration + 15),
                Map.of("timeLimitSeconds", duration, "pointsPerCorrect", points, "uiConfig", uiConfig)));
        return new RoundStart(session.sessionId(), code, clock.instant(), session.servedAt(), duration,
                session.servedAt().plusSeconds(3L + duration), points, uiConfig, session.items());
    }

    private static ApiException unavailable() {
        return new ApiException(ErrorCode.GAME_UNAVAILABLE, HttpStatus.UNPROCESSABLE_ENTITY,
                "Game chưa có đủ nội dung để chơi.", Map.of());
    }
}
