package com.cnhsk.learning.leaderboard;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

/** Public representations; internal totals and eligibility snapshots never cross this boundary. */
public final class LeaderboardDtos {
    private LeaderboardDtos() { }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(name = "LeaderboardEntry", requiredProperties = {"rank", "userId", "displayName", "avatarUrl", "score", "durationMs"})
    public record LeaderboardEntry(long rank, long userId, String displayName,
            @Schema(types = {"string", "null"}) String avatarUrl, long score,
            @Schema(types = {"integer", "null"}, minimum = "0") Integer durationMs) { }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(name = "Leaderboard", requiredProperties = {"type", "ref", "period", "periodStart", "periodEnd", "entries", "myEntry"})
    public record Leaderboard(String type, String ref, String period,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Ho_Chi_Minh")
            @Schema(types = {"string", "null"}, format = "date-time") OffsetDateTime periodStart,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Ho_Chi_Minh")
            @Schema(types = {"string", "null"}, format = "date-time") OffsetDateTime periodEnd,
            List<LeaderboardEntry> entries, @Schema(nullable = true) LeaderboardEntry myEntry) {
        public Leaderboard { entries = List.copyOf(entries); }
    }

    @Schema(name = "TopicOption", requiredProperties = {"id", "name", "hskLevel"})
    public record TopicOption(long id, String name, int hskLevel) { }

    @Schema(name = "GameOption", requiredProperties = {"code", "name"})
    public record GameOption(String code, String name) { }
}
