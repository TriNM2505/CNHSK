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
}
