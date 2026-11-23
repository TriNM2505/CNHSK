package com.cnhsk.community.contest.web;

import java.time.Instant;
import java.util.List;

import com.cnhsk.community.media.web.MediaItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Payloads of a moderator-run game contest. */
public final class GameContestViews {
    private GameContestViews() { }

    /** WAITING before the start, OPEN while rounds are served, TALLYING while the last rounds finish, then FINISHED. */
    public enum Phase { WAITING, OPEN, TALLYING, FINISHED }

    @Schema(name = "GameContestWrite")
    public record GameContestWrite(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 50_000) String content,
            @NotBlank @Size(max = 30) String gameCode,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @NotNull @Min(1) @Max(50) Integer prizeCount,
            List<MediaItem> media) {
        public GameContestWrite {
            if (title != null) title = title.strip();
            if (content != null) content = content.strip();
        }
    }

    @Schema(name = "GameContestCreated")
    public record GameContestCreated(long contestId, long postId) { }

    @Schema(name = "GameContestRankingRow")
    public record RankingRow(long rank, long userId, @Schema(types = {"string", "null"}) String displayName,
            long points, @Schema(types = {"integer", "null"}) Integer durationMs, Instant achievedAt) { }

    @Schema(name = "GameContestLive")
    public record Live(long id, String title, @Schema(types = {"string", "null"}) String description, String gameCode,
            Instant startsAt, Instant endsAt, Instant resultsAt, int prizeCount, Instant serverTime, Phase phase,
            long participants, List<RankingRow> ranking) {
        public Live { ranking = List.copyOf(ranking); }
    }

    @Schema(name = "GameContestWinner")
    public record Winner(long rank, long userId, @Schema(types = {"string", "null"}) String displayName, String email,
            long points, @Schema(types = {"integer", "null"}) Integer durationMs, Instant achievedAt) { }

    @Schema(name = "GameContestExcluded")
    public record Excluded(long userId, @Schema(types = {"string", "null"}) String displayName, String note,
            Instant decidedAt) { }

    @Schema(name = "GameContestResults")
    public record Results(Live contest, List<Winner> winners, List<Excluded> excluded) {
        public Results {
            winners = List.copyOf(winners);
            excluded = List.copyOf(excluded);
        }
    }

    @Schema(name = "GameContestExclusion")
    public record Exclusion(@NotNull Boolean excluded, @NotBlank @Size(max = 500) String note) { }
}
