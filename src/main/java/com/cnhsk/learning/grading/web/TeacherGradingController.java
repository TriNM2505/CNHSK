package com.cnhsk.learning.grading.web;

import java.util.List;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.grading.TeacherGradingService;
import com.cnhsk.learning.grading.web.TeacherGradingViews.Detail;
import com.cnhsk.learning.grading.web.TeacherGradingViews.AssignedItem;
import com.cnhsk.learning.grading.web.TeacherGradingViews.QueuePage;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TeacherGradingController {
    private final TeacherGradingService service;
    private final AuthApi auth;

    public TeacherGradingController(TeacherGradingService service, AuthApi auth) { this.service = service; this.auth = auth; }

    @GetMapping("/api/teacher/grading-queue")
    @Operation(operationId = "getGradingQueue", tags = "teacher-grading")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Hàng đợi chấm bài"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")})
    public QueuePage queue(
            @RequestParam(required = false) @Parameter(schema = @Schema(type = "integer", minimum = "0", defaultValue = "0")) String page,
            @RequestParam(required = false) @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "50", defaultValue = "20")) String size) {
        return service.queue(auth.currentUser().userId(), integer(page, 0), integer(size, 20));
    }

    @GetMapping("/api/teacher/grading-requests/mine")
    @Operation(operationId = "listMyAssignedGrading", tags = "teacher-grading")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Bài đang giữ"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN")})
    public List<AssignedItem> mine() { return service.mine(auth.currentUser().userId()); }

    @GetMapping("/api/teacher/grading-requests/{id}")
    @Operation(operationId = "getAssignedGrading", tags = "teacher-grading")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Bài đang chấm"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN hoặc GRADING_NOT_ASSIGNEE")})
    public Detail get(@PathVariable @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) String id) {
        return service.get(auth.currentUser().userId(), id(id, false));
    }

    @PostMapping("/api/teacher/grading-requests/{id}/claim")
    @Operation(operationId = "claimGrading", tags = "teacher-grading")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Đã nhận"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN, CSRF_TOKEN_INVALID hoặc GRADING_SELF_GRADING"),
            @ApiResponse(responseCode = "409", description = "GRADING_ALREADY_CLAIMED"),
            @ApiResponse(responseCode = "422", description = "GRADING_CLAIM_LIMIT")})
    public Detail claim(@PathVariable @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) String id) {
        return service.claim(auth.currentUser().userId(), id(id, true));
    }

    @PostMapping("/api/teacher/grading-requests/{id}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "releaseGrading", tags = "teacher-grading")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Đã trả lại"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN, CSRF_TOKEN_INVALID hoặc GRADING_NOT_ASSIGNEE")})
    public void release(@PathVariable @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) String id) {
        service.release(auth.currentUser().userId(), id(id, false));
    }

    private long id(String value, boolean claim) {
        try { long id = Long.parseLong(value); if (id > 0) return id; }
        catch (NumberFormatException exception) { /* Deliberately use the same response as an unavailable request. */ }
        throw new ApiException(claim ? ErrorCode.GRADING_ALREADY_CLAIMED : ErrorCode.GRADING_NOT_ASSIGNEE,
                claim ? HttpStatus.CONFLICT : HttpStatus.FORBIDDEN,
                claim ? "Bài đã có người nhận hoặc không còn chờ nhận." : "Không có quyền truy cập bài đang chấm.", Map.of());
    }

    private int integer(String value, int fallback) {
        if (value == null) return fallback;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Phân trang không hợp lệ.", Map.of());
        }
    }
}
