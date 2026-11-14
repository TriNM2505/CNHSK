package com.cnhsk.learning.grading.web;

import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.grading.GradingRequestService;
import com.cnhsk.learning.grading.web.GradingRequestViews.*;
import com.cnhsk.shared.config.RequestIdFilter;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GradingRequestController {
    private final GradingRequestService service;
    private final AuthApi auth;
    private final ObjectMapper mapper;

    public GradingRequestController(GradingRequestService service, AuthApi auth, ObjectMapper mapper) {
        this.service = service; this.auth = auth; this.mapper = mapper;
    }

    @GetMapping("/api/grading/policy")
    @Operation(operationId = "getGradingPolicy", tags = "grading")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Chính sách chấm bài"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN")})
    public Policy policy() { return service.policy(); }

    @PostMapping("/api/grading-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createGradingRequest", tags = "grading")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(schema = @Schema(implementation = GradingRequestWrite.class)))
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Đã gửi bài"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "402", description = "INSUFFICIENT_CREDITS"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN hoặc CSRF_TOKEN_INVALID"),
            @ApiResponse(responseCode = "409", description = "GRADING_DUPLICATE_ACTIVE"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED hoặc GRADING_INVALID_ESSAY")})
    public Detail submit(@RequestBody(required = false) String body) {
        GradingRequestWrite request = GradingRequestWrite.parse(body, mapper);
        return service.submit(auth.currentUser().userId(), request);
    }

    @GetMapping("/api/grading-requests")
    @Operation(operationId = "listMyGradingRequests", tags = "grading")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Yêu cầu của tôi"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")})
    public Page list(@RequestParam(required = false) String status,
            @RequestParam(required = false) @Parameter(schema = @Schema(type = "integer", minimum = "0", defaultValue = "0")) String page,
            @RequestParam(required = false) @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "50", defaultValue = "20")) String size) {
        return service.list(auth.currentUser().userId(), status, integer(page, 0), integer(size, 20));
    }

    @GetMapping("/api/grading-requests/{id}")
    @Operation(operationId = "getMyGradingRequest", tags = "grading")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Chi tiết yêu cầu"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN hoặc GRADING_REQUEST_NOT_OWNED")})
    public Detail get(@PathVariable @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) String id) {
        return service.get(auth.currentUser().userId(), id(id));
    }

    @PostMapping("/api/grading-requests/{id}/cancel")
    @Operation(operationId = "cancelGradingRequest", tags = "grading")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Đã huỷ và hoàn lượt"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN, CSRF_TOKEN_INVALID hoặc GRADING_REQUEST_NOT_OWNED"),
            @ApiResponse(responseCode = "409", description = "GRADING_NOT_CANCELLABLE")})
    public Detail cancel(@PathVariable @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) String id) {
        return service.cancel(auth.currentUser().userId(), id(id));
    }

    private long id(String value) {
        try { long id = Long.parseLong(value); if (id > 0) return id; }
        catch (NumberFormatException exception) { /* Same response as an unowned request. */ }
        throw new ApiException(ErrorCode.GRADING_REQUEST_NOT_OWNED, HttpStatus.FORBIDDEN,
                "Không có quyền truy cập yêu cầu chấm bài.", Map.of());
    }

    private int integer(String value, int fallback) {
        if (value == null) return fallback;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Bộ lọc hoặc phân trang không hợp lệ.", Map.of());
        }
    }

    @Hidden
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadableBody() {
        return ResponseEntity.unprocessableEntity().body(Map.of("error_code", ErrorCode.VALIDATION_FAILED.name(),
                "message", "Bài viết, đề bài hoặc cấp HSK không hợp lệ.", "request_id", RequestIdFilter.currentRequestId()));
    }
}
