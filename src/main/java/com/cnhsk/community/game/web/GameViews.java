package com.cnhsk.community.game.web;

import java.time.Instant;

import com.cnhsk.learning.api.GameCode;
import com.cnhsk.learning.api.PracticeApi;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/** Public catalog projections contain no served items or answer keys. */
public final class GameViews {
    private GameViews() { }

    public record CommonGame(GameCode code, String name, String description, PracticeApi.Skill skill,
            String howToPlay, int timeLimitSeconds, int pointsPerCorrect, boolean playable, String unavailableReason) { }

    @Schema(name = "GameInfo")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record GameInfo(GameCode code, String name, String description, PracticeApi.Skill skill,
            String howToPlay, int timeLimitSeconds, boolean playable, String unavailableReason, MyStats myStats) { }

    @Schema(name = "GameMyStats")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MyStats(long bestPoints, long roundsPlayed, Instant lastPlayedAt) { }
}
