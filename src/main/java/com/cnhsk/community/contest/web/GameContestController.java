package com.cnhsk.community.contest.web;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.cnhsk.community.contest.GameContestService;
import com.cnhsk.community.contest.web.GameContestViews.Exclusion;
import com.cnhsk.community.contest.web.GameContestViews.GameContestCreated;
import com.cnhsk.community.contest.web.GameContestViews.GameContestWrite;
import com.cnhsk.community.contest.web.GameContestViews.Live;
import com.cnhsk.community.contest.web.GameContestViews.Results;
import com.cnhsk.community.contest.web.GameContestViews.Winner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Moderators run game contests; everyone can watch the live ranking. */
@RestController
public class GameContestController {
    private static final String ERROR = "#/components/schemas/ErrorResponse";
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final GameContestService service;

    public GameContestController(GameContestService service) {
        this.service = service;
    }

    @PostMapping(value = "/api/community/moderation/contests", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createGameContest", tags = "moderation")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đã tạo cuộc thi và bài hướng dẫn",
                    content = @Content(schema = @Schema(implementation = GameContestCreated.class))),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập", content = @Content(schema = @Schema(ref = ERROR))),
            @ApiResponse(responseCode = "403", description = "Không phải kiểm duyệt viên", content = @Content(schema = @Schema(ref = ERROR))),
            @ApiResponse(responseCode = "422", description = "Dữ liệu không hợp lệ", content = @Content(schema = @Schema(ref = ERROR)))})
    @SecurityRequirement(name = "cookieAuth")
    @SecurityRequirement(name = "bearerAuth")
    public GameContestCreated create(@Valid @RequestBody GameContestWrite request) {
        return service.create(request);
    }

    @GetMapping(value = "/api/public/community/contests/{id}/live", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "getGameContestLive", tags = "public-contests")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = Live.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy cuộc thi game", content = @Content(schema = @Schema(ref = ERROR)))})
    @SecurityRequirements
    public Live live(@PathVariable long id) {
        return service.live(id);
    }

    @GetMapping(value = "/api/community/moderation/contests/{id}/results", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "getGameContestResults", tags = "moderation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = Results.class))),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập", content = @Content(schema = @Schema(ref = ERROR))),
            @ApiResponse(responseCode = "403", description = "Không phải kiểm duyệt viên", content = @Content(schema = @Schema(ref = ERROR))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy cuộc thi game", content = @Content(schema = @Schema(ref = ERROR))),
            @ApiResponse(responseCode = "409", description = "Cuộc thi chưa có kết quả", content = @Content(schema = @Schema(ref = ERROR)))})
    @SecurityRequirement(name = "cookieAuth")
    @SecurityRequirement(name = "bearerAuth")
    public Results results(@PathVariable long id) {
        return service.results(id);
    }

    @GetMapping(value = "/api/community/moderation/contests/{id}/results.csv", produces = "text/csv")
    @Operation(operationId = "exportGameContestResults", tags = "moderation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tệp CSV người đạt giải",
                    content = @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập", content = @Content(schema = @Schema(ref = ERROR))),
            @ApiResponse(responseCode = "403", description = "Không phải kiểm duyệt viên", content = @Content(schema = @Schema(ref = ERROR))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy cuộc thi game", content = @Content(schema = @Schema(ref = ERROR))),
            @ApiResponse(responseCode = "409", description = "Cuộc thi chưa có kết quả", content = @Content(schema = @Schema(ref = ERROR)))})
    @SecurityRequirement(name = "cookieAuth")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<byte[]> csv(@PathVariable long id) {
        Results results = service.results(id);
        StringBuilder csv = new StringBuilder("﻿Hạng,Tên,Email,Điểm,Thời gian (giây),Đạt lúc\r\n");
        for (Winner winner : results.winners()) {
            csv.append(winner.rank()).append(',')
                    .append(cell(winner.displayName())).append(',')
                    .append(cell(winner.email())).append(',')
                    .append(winner.points()).append(',')
                    .append(winner.durationMs() == null ? "" : String.format("%.1f", winner.durationMs() / 1000.0)
                            .replace(',', '.')).append(',')
                    .append(LOCAL.format(winner.achievedAt())).append("\r\n");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cuoc-thi-" + id + "-ket-qua.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    @PutMapping(value = "/api/community/moderation/contests/{id}/exclusions/{userId}",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "decideGameContestExclusion", tags = "moderation")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã lưu quyết định"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập", content = @Content(schema = @Schema(ref = ERROR))),
            @ApiResponse(responseCode = "403", description = "Không phải kiểm duyệt viên", content = @Content(schema = @Schema(ref = ERROR))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy cuộc thi game", content = @Content(schema = @Schema(ref = ERROR))),
            @ApiResponse(responseCode = "422", description = "Dữ liệu không hợp lệ", content = @Content(schema = @Schema(ref = ERROR)))})
    @SecurityRequirement(name = "cookieAuth")
    @SecurityRequirement(name = "bearerAuth")
    public void decide(@PathVariable long id, @PathVariable long userId, @Valid @RequestBody Exclusion request) {
        service.decide(id, userId, request);
    }

    /** Quotes a CSV cell and defuses spreadsheet formulas a player could put in their name. */
    static String cell(String value) {
        if (value == null) return "";
        String safe = !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        return '"' + safe.replace("\"", "\"\"") + '"';
    }
}
