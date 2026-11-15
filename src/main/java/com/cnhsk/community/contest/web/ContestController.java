package com.cnhsk.community.contest.web;

import java.util.List;
import java.util.Map;

import com.cnhsk.community.contest.ContestQueryService;
import com.cnhsk.community.contest.ContestService;
import com.cnhsk.community.contest.web.ContestViews.CheatEvent;
import com.cnhsk.community.contest.web.ContestViews.ContestResults;
import com.cnhsk.community.contest.web.ContestViews.ContestSession;
import com.cnhsk.community.contest.web.ContestViews.SubmitResult;
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
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping(value = "/api/community/contests", produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = "cookieAuth")
@SecurityRequirement(name = "bearerAuth")
public class ContestController {
    private final ContestService service;
    private final ContestQueryService queries;
    private final GlobalExceptionHandler errors;

    public ContestController(ContestService service, ContestQueryService queries, GlobalExceptionHandler errors) {
        this.service = service;
        this.queries = queries;
        this.errors = errors;
    }

    @PostMapping("/{id}/attempts")
    @Operation(operationId = "enterContest", tags = "contests")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tiếp tục lượt đang mở",
                    content = @Content(schema = @Schema(implementation = ContestSession.class))),
            @ApiResponse(responseCode = "201", description = "Lượt mới",
                    content = @Content(schema = @Schema(implementation = ContestSession.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Không có quyền", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy cuộc thi", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "409", description = "Đã có lượt thi", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "422", description = "Không thể vào thi", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public ResponseEntity<ContestSession> enter(@PathVariable long id) {
        ContestSession result = service.enter(id);
        return ResponseEntity.status(result.resumed() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }

    @PostMapping("/{id}/attempts/submit")
    @Operation(operationId = "submitContest", tags = "contests")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã nộp", content = @Content(schema = @Schema(implementation = SubmitResult.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Không có quyền", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy lượt thi", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "409", description = "Đã có lượt thi", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "422", description = "Đã hết hạn nộp", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public SubmitResult submit(@PathVariable long id) { return service.submit(id); }

    @PostMapping(value = "/{id}/attempts/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "reportContestEvent", tags = "contests")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã ghi hoặc bỏ qua do giới hạn tần suất", content = @Content),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Không có quyền", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy lượt đang thi", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "422", description = "Dữ liệu không hợp lệ", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public void event(@PathVariable long id, @Valid @RequestBody CheatEvent event) { service.event(id, event); }

    @GetMapping("/{id}/results")
    @Operation(operationId = "getContestResults", tags = "contests")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ContestResults.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Không có quyền", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy cuộc thi", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "422", description = "Phân trang không hợp lệ", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public ContestResults results(@PathVariable long id,
            @Parameter(schema = @Schema(type = "integer", minimum = "0", defaultValue = "0")) @RequestParam(defaultValue = "0") int page,
            @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "50", defaultValue = "20")) @RequestParam(defaultValue = "20") int size) {
        return queries.results(id, page, size);
    }

    @Hidden
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> invalidParameter(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        if ("id".equals(exception.getName())) {
            return errors.handleApiException(new ApiException(ErrorCode.CONTEST_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Không tìm thấy cuộc thi.", Map.of()), request);
        }
        return invalid(exception.getName(), request);
    }

    @Hidden
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> invalidBody(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return invalid("request", request);
    }

    private ResponseEntity<Map<String, Object>> invalid(String field, HttpServletRequest request) {
        String message = "Dữ liệu yêu cầu không hợp lệ.";
        return errors.handleApiException(new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                message, Map.of("details", List.of(Map.of("field", field, "message", message)))), request);
    }
}
