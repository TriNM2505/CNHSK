package com.cnhsk.community.contest.web;

import java.time.OffsetDateTime;
import java.util.List;

import com.cnhsk.community.contest.Contest.Prize;
import com.cnhsk.community.contest.Contest.ScheduleState;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

public final class ContestViews {
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
}
