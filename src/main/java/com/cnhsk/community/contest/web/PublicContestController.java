package com.cnhsk.community.contest.web;

import java.util.List;
import java.util.Map;

import com.cnhsk.community.contest.Contest.ScheduleState;
import com.cnhsk.community.contest.ContestQueryService;
import com.cnhsk.community.contest.web.ContestViews.ContestDetail;
import com.cnhsk.community.contest.web.ContestViews.ContestPage;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.exception.GlobalExceptionHandler;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping(value = "/api/public/community/contests", produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirements
public class PublicContestController {
    private final ContestQueryService service;
    private final GlobalExceptionHandler errors;

    public PublicContestController(ContestQueryService service, GlobalExceptionHandler errors) {
        this.service = service;
        this.errors = errors;
    }

    @GetMapping
    @Operation(operationId = "listContests", tags = "contests", summary = "Danh sách cuộc thi công khai")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = ContestPage.class))),
            @ApiResponse(responseCode = "422", description = "Dữ liệu không hợp lệ",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public ContestPage list(@RequestParam(required = false) ScheduleState state,
            @Parameter(schema = @Schema(type = "integer", minimum = "0", defaultValue = "0"))
            @RequestParam(defaultValue = "0") int page,
            @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "50", defaultValue = "20"))
            @RequestParam(defaultValue = "20") int size) {
        return service.list(state, page, size);
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getContest", tags = "contests", summary = "Chi tiết cuộc thi công khai")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = ContestDetail.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy cuộc thi",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public ContestDetail detail(
            @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) @PathVariable long id) {
        return service.detail(id);
    }

    @Hidden
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> invalidParameter(MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        if ("id".equals(exception.getName())) {
            return errors.handleApiException(new ApiException(ErrorCode.CONTEST_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Không tìm thấy cuộc thi.", Map.of()), request);
        }
        String message = "Tham số yêu cầu không hợp lệ.";
        return errors.handleApiException(new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                message, Map.of("details", List.of(Map.of("field", exception.getName(), "message", message)))), request);
    }
}
