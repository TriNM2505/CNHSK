package com.cnhsk.community.contest.web;

import java.util.List;
import java.util.Map;

import com.cnhsk.community.contest.ContestAdminService;
import com.cnhsk.community.contest.web.ContestViews.AdminContest;
import com.cnhsk.community.contest.web.ContestViews.AdminContestPage;
import com.cnhsk.community.contest.web.ContestViews.ContestStatus;
import com.cnhsk.community.contest.web.ContestViews.ContestWrite;
import com.cnhsk.community.contest.web.ContestViews.Participant;
import com.cnhsk.community.contest.web.ContestViews.ParticipantDecision;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.exception.GlobalExceptionHandler;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping(value = "/api/admin/contests", produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = "cookieAuth")
@SecurityRequirement(name = "bearerAuth")
public class ContestAdminController {
    private final ContestAdminService service;
    private final GlobalExceptionHandler errors;

    public ContestAdminController(ContestAdminService service, GlobalExceptionHandler errors) {
        this.service = service;
        this.errors = errors;
    }

    @GetMapping
    @Operation(operationId = "adminListContests", tags = "contest-admin")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AdminContestPage.class))),
            @ApiResponse(responseCode = "401", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "422", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public AdminContestPage list(
            @RequestParam(required = false) ContestStatus status,
            @Parameter(schema = @Schema(type = "integer", minimum = "0", defaultValue = "0")) @RequestParam(defaultValue = "0") int page,
            @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "50", defaultValue = "20")) @RequestParam(defaultValue = "20") int size) {
        return service.list(status, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "adminCreateContest", tags = "contest-admin")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "OK", content = @Content(schema = @Schema(implementation = AdminContest.class))),
            @ApiResponse(responseCode = "401", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "422", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public AdminContest create(@Valid @RequestBody ContestWrite input) {
        return service.create(input);
    }

    @GetMapping("/{id}")
    @Operation(operationId = "adminGetContest", tags = "contest-admin")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AdminContest.class))),
            @ApiResponse(responseCode = "401", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "404", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public AdminContest detail(@PathVariable @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) String id) {
        return service.detail(id(id));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "adminUpdateContest", tags = "contest-admin")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AdminContest.class))),
            @ApiResponse(responseCode = "401", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "404", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "409", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "422", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public AdminContest update(@PathVariable @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) String id,
            @Valid @RequestBody ContestWrite input) {
        return service.update(id(id), input);
    }

    @PostMapping("/{id}/publish")
    @Operation(operationId = "adminPublishContest", tags = "contest-admin")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AdminContest.class))),
            @ApiResponse(responseCode = "401", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "404", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "409", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "422", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public AdminContest publish(@PathVariable @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) String id) {
        return service.publish(id(id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(operationId = "adminCancelContest", tags = "contest-admin")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AdminContest.class))),
            @ApiResponse(responseCode = "401", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "404", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "409", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public AdminContest cancel(@PathVariable @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) String id) {
        return service.cancel(id(id));
    }

    @GetMapping("/{id}/participants")
    @Operation(operationId = "adminListParticipants", tags = "contest-admin")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Participant.class)))),
            @ApiResponse(responseCode = "401", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "404", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "409", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public List<Participant> participants(@PathVariable @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) String id) {
        return service.participants(id(id));
    }

    @PatchMapping("/{id}/participants/{userId}")
    @Operation(operationId = "adminDecideParticipant", tags = "contest-admin")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = Participant.class))),
            @ApiResponse(responseCode = "401", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "404", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "409", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "422", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public Participant decide(@PathVariable @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) String id,
            @PathVariable @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) String userId,
            @Valid @RequestBody ParticipantDecision input) {
        return service.decide(id(id), id(userId), input);
    }

    @PostMapping("/{id}/ranking/publish")
    @Operation(operationId = "adminPublishRanking", tags = "contest-admin")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AdminContest.class))),
            @ApiResponse(responseCode = "401", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "404", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "409", description = "Lỗi yêu cầu", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public AdminContest publishRanking(@PathVariable @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) String id) {
        return service.publishRanking(id(id));
    }

    @Hidden
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, Object>> invalidRequest(Exception exception, HttpServletRequest request) {
        String field = exception instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName() : "request";
        return errors.handleApiException(new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Dữ liệu yêu cầu không hợp lệ.", Map.of("details", List.of(Map.of("field", field,
                "message", "Giá trị không hợp lệ.")))), request);
    }

    private static long id(String value) {
        try {
            long id = Long.parseLong(value);
            if (id > 0) return id;
        } catch (NumberFormatException ignored) {
            // Invalid identifiers use the same response as missing contests or participants.
        }
        throw new ApiException(ErrorCode.CONTEST_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Không tìm thấy cuộc thi hoặc thí sinh.", Map.of());
    }
}
