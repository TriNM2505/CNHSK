package com.cnhsk.community.contest.web;

import java.time.OffsetDateTime;
import java.util.List;

import com.cnhsk.community.contest.Contest.Prize;
import com.cnhsk.community.contest.Contest.ScheduleState;
import com.cnhsk.learning.api.PracticeApi;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public final class ContestViews {
    private static final String TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX";
    private static final String TIME_ZONE = "Asia/Ho_Chi_Minh";
    private ContestViews() { }

    @Schema(name = "ContestSummary", requiredProperties = {"id", "name", "startsAt", "endsAt", "state", "prizes",
            "maxParticipants", "participantCount", "rankingPublished"})
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ContestSummary(long id, String name,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX", timezone = "Asia/Ho_Chi_Minh") OffsetDateTime startsAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX", timezone = "Asia/Ho_Chi_Minh") OffsetDateTime endsAt,
            ScheduleState state, List<Prize> prizes,
            @Schema(types = {"integer", "null"}) Integer maxParticipants,
            long participantCount, boolean rankingPublished) {
        public ContestSummary { prizes = List.copyOf(prizes); }
    }

    @Schema(name = "ContestPage", requiredProperties = {"items", "page", "size", "totalElements"})
    public record ContestPage(List<ContestSummary> items, int page, int size, long totalElements) {
        public ContestPage { items = List.copyOf(items); }
    }

    @Schema(name = "ContestDetail", requiredProperties = {"id", "name", "startsAt", "endsAt", "state", "prizes",
            "maxParticipants", "participantCount", "rankingPublished", "description", "examTitle", "durationMinutes",
            "questionCount"})
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ContestDetail(long id, String name,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX", timezone = "Asia/Ho_Chi_Minh") OffsetDateTime startsAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX", timezone = "Asia/Ho_Chi_Minh") OffsetDateTime endsAt,
            ScheduleState state, List<Prize> prizes,
            @Schema(types = {"integer", "null"}) Integer maxParticipants,
            long participantCount, boolean rankingPublished,
            @Schema(types = {"string", "null"}) String description,
            String examTitle, int durationMinutes, int questionCount) {
        public ContestDetail { prizes = List.copyOf(prizes); }
    }

    @Schema(name = "ContestSession", requiredProperties = {"sessionId", "contestId", "deadline", "serverTime", "resumed", "items"})
    public record ContestSession(long sessionId, long contestId,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TIME_PATTERN, timezone = TIME_ZONE) OffsetDateTime deadline,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TIME_PATTERN, timezone = TIME_ZONE) OffsetDateTime serverTime,
            boolean resumed, List<PracticeApi.ServedItemView> items) {
        public ContestSession { items = List.copyOf(items); }
    }

    @Schema(name = "SubmitResult", requiredProperties = "submittedAt")
    public record SubmitResult(
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TIME_PATTERN, timezone = TIME_ZONE) OffsetDateTime submittedAt) { }

    @Schema(name = "CheatEvent", requiredProperties = "type")
    public record CheatEvent(@NotNull EventType type) { }

    public enum EventType { TAB_HIDDEN }

    @Schema(name = "MyResult", requiredProperties = {"status", "submittedAt", "score", "maxScore", "durationMs", "rank", "excluded"})
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MyResult(String status,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TIME_PATTERN, timezone = TIME_ZONE)
            @Schema(types = {"string", "null"}, format = "date-time") OffsetDateTime submittedAt,
            @Schema(types = {"integer", "null"}, format = "int64") Long score,
            @Schema(types = {"integer", "null"}, format = "int64") Long maxScore,
            @Schema(types = {"integer", "null"}) Integer durationMs,
            @Schema(types = {"integer", "null"}, format = "int64") Long rank,
            @Schema(types = {"boolean", "null"}) Boolean excluded) { }

    @Schema(name = "RankingRow", requiredProperties = {"rank", "userId", "displayName", "score", "maxScore", "durationMs"})
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RankingRow(long rank, long userId, @Schema(types = {"string", "null"}) String displayName,
            long score, long maxScore, @Schema(types = {"integer", "null"}) Integer durationMs) { }

    @Schema(name = "RankingPage", requiredProperties = {"items", "page", "size", "totalElements"})
    public record RankingPage(List<RankingRow> items, int page, int size, long totalElements) {
        public RankingPage { items = List.copyOf(items); }
    }

    @Schema(name = "ContestResults", requiredProperties = {"contest", "published", "mine", "ranking"})
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ContestResults(ContestSummary contest, boolean published, MyResult mine, RankingPage ranking) { }
}
