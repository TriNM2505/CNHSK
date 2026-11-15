package com.cnhsk.community.contest.web;

import java.time.OffsetDateTime;
import java.util.List;

import com.cnhsk.community.contest.Contest.Prize;
import com.cnhsk.community.contest.Contest.ScheduleState;
import com.cnhsk.learning.api.PracticeApi;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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

    public enum ContestStatus { DRAFT, PUBLISHED, CANCELLED }

    @Schema(name = "ContestWrite", requiredProperties = {"name", "startsAt", "endsAt"})
    public record ContestWrite(@NotBlank @Size(max = 200) String name,
            @Size(max = 5000) @Schema(types = {"string", "null"}) String description,
            @Schema(types = {"integer", "null"}, format = "int64") Long examId,
            @NotNull OffsetDateTime startsAt, @NotNull OffsetDateTime endsAt,
            @Min(1) @Max(100000) @Schema(types = {"integer", "null"}) Integer maxParticipants,
            @Size(max = 10) List<@NotNull @Valid Prize> prizes) { }

    @Schema(name = "AdminContest", requiredProperties = {"id", "name", "description", "examId", "examTitle",
            "startsAt", "endsAt", "maxParticipants", "prizes", "status", "state", "rankingPublishedAt", "participantCount"})
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AdminContest(long id, String name, @Schema(types = {"string", "null"}) String description,
            @Schema(types = {"integer", "null"}, format = "int64") Long examId,
            @Schema(types = {"string", "null"}) String examTitle,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TIME_PATTERN, timezone = TIME_ZONE) OffsetDateTime startsAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TIME_PATTERN, timezone = TIME_ZONE) OffsetDateTime endsAt,
            @Schema(types = {"integer", "null"}) Integer maxParticipants, List<Prize> prizes,
            ContestStatus status, ScheduleState state,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TIME_PATTERN, timezone = TIME_ZONE)
            @Schema(types = {"string", "null"}, format = "date-time") OffsetDateTime rankingPublishedAt,
            long participantCount) {
        public AdminContest { prizes = List.copyOf(prizes); }
    }

    @Schema(name = "AdminContestPage", requiredProperties = {"items", "page", "size", "totalElements"})
    public record AdminContestPage(List<AdminContest> items, int page, int size, long totalElements) {
        public AdminContestPage { items = List.copyOf(items); }
    }

    @Schema(name = "Participant", requiredProperties = {"userId", "displayName", "status", "score", "maxScore",
            "durationMs", "submittedAt", "suspicious", "cheatEventCount"})
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Participant(long userId, @Schema(types = {"string", "null"}) String displayName, String status,
            @Schema(types = {"integer", "null"}, format = "int64") Long score,
            @Schema(types = {"integer", "null"}, format = "int64") Long maxScore,
            @Schema(types = {"integer", "null"}) Integer durationMs,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TIME_PATTERN, timezone = TIME_ZONE)
            @Schema(types = {"string", "null"}, format = "date-time") OffsetDateTime submittedAt,
            boolean suspicious, long cheatEventCount) { }

    @Schema(name = "ParticipantDecision", requiredProperties = {"excluded", "note"})
    public record ParticipantDecision(@NotNull Boolean excluded, @NotBlank @Size(max = 500) String note) { }
}
