package com.cnhsk.community.game;

import com.cnhsk.learning.api.ContentLookup;

/** Server-created context. A null user is a guest and a null level means no level was selected. */
public record GameContext(Long userId, Integer hskLevel, ContentLookup contentLookup) {
    public GameContext {
        if (contentLookup == null || (userId != null && userId <= 0)
                || (hskLevel != null && (hskLevel < 1 || hskLevel > 9))) {
            throw new IllegalArgumentException("Game context is invalid.");
        }
    }

    /** Levels to draw content from; without a level (a contest round) HSK 1–3 are mixed. */
    public java.util.Set<Integer> levels() {
        return hskLevel == null ? java.util.Set.of(1, 2, 3) : java.util.Set.of(hskLevel);
    }
}
