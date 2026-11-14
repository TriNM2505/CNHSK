package com.cnhsk.community.game;

import java.util.List;
import java.util.Map;

import com.cnhsk.community.game.web.GameViews.GameInfo;
import com.cnhsk.community.game.web.GameViews.MyStats;
import com.cnhsk.learning.api.GameCode;
import com.cnhsk.learning.api.GameStatsApi;
import com.cnhsk.learning.api.GameStatsApi.GameStats;
import org.springframework.stereotype.Service;

@Service
public class GameCatalogService {
    private final GameCatalogCache cache;
    private final GameStatsApi stats;

    public GameCatalogService(GameCatalogCache cache, GameStatsApi stats) {
        this.cache = cache;
        this.stats = stats;
    }

    public List<GameInfo> list(Long userId) {
        var games = cache.snapshot();
        if (games.isEmpty()) return List.of();
        Map<GameCode, GameStats> personal = userId == null ? Map.of() : stats.gameStats(userId);
        return games.stream().map(game -> {
            var recorded = personal.get(game.code());
            MyStats myStats = userId == null ? null : recorded == null ? new MyStats(0, 0, null)
                    : new MyStats(Math.multiplyExact(recorded.bestCorrect(), (long) game.pointsPerCorrect()),
                            recorded.roundsPlayed(), recorded.lastPlayedAt());
            return new GameInfo(game.code(), game.name(), game.description(), game.skill(), game.howToPlay(),
                    game.timeLimitSeconds(), game.playable(), game.unavailableReason(), myStats);
        }).toList();
    }
}
