package com.cnhsk.community.game;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.cnhsk.community.game.web.GameViews.CommonGame;
import com.cnhsk.learning.api.ContentLookup;
import org.springframework.stereotype.Component;

/** One immutable, guest-context snapshot per application; user statistics never enter the cache. */
@Component
public class GameCatalogCache {
    private final GameRegistry registry;
    private final ContentLookup contentLookup;
    private final Clock clock;
    private Snapshot cached;

    public GameCatalogCache(GameRegistry registry, ContentLookup contentLookup, Clock clock) {
        this.registry = registry;
        this.contentLookup = contentLookup;
        this.clock = clock;
    }

    public synchronized List<CommonGame> snapshot() {
        if (cached != null && clock.instant().isBefore(cached.expiresAt())) return cached.games();
        var context = new GameContext(null, null, contentLookup);
        var games = registry.all().stream().map(definition -> {
            boolean playable = definition.isAvailable(context);
            return new CommonGame(definition.code(), definition.name(), definition.description(), definition.skill(),
                    definition.howToPlay(), definition.timeLimitSeconds(), definition.pointsPerCorrect(), playable,
                    playable ? null : "NOT_ENOUGH_CONTENT");
        }).toList();
        cached = new Snapshot(games, clock.instant().plusSeconds(60));
        return cached.games();
    }

    private record Snapshot(List<CommonGame> games, Instant expiresAt) { }
}
