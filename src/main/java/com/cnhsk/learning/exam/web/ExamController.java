package com.cnhsk.learning.exam.web;

import java.util.List;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.exam.ExamAttemptService;
import com.cnhsk.learning.exam.ExamQueryService;
import com.cnhsk.learning.exam.ExamQueryService.ExamSummary;
import com.cnhsk.learning.exam.ExamResultService;
import com.cnhsk.learning.practice.PracticeService.SessionView;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExamController {
    private final ExamQueryService exams;
    private final ExamAttemptService attempts;
    private final ExamResultService results;
    private final AuthApi auth;

    public ExamController(ExamQueryService exams, ExamAttemptService attempts, ExamResultService results, AuthApi auth) {
        this.exams = exams;
        this.attempts = attempts;
        this.results = results;
        this.auth = auth;
    }

    @GetMapping("/api/exams")
    @Operation(operationId = "listExams", tags = "exam")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "422", description = "Request cannot be processed")
    })
    public List<ExamSummary> list(
            @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "9"))
            @RequestParam(required = false) String hskLevel) {
        Integer level = hskLevel == null ? null : (int) positiveNumber(hskLevel, "hskLevel", 9);
        return exams.list(auth.currentUser().userId(), level);
    }

    @PostMapping("/api/exams/{id}/attempts")
    @Operation(operationId = "startExamAttempt", tags = "exam")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Resource not found"),
            @ApiResponse(responseCode = "422", description = "Request cannot be processed"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<SessionView> start(
            @Parameter(schema = @Schema(type = "integer", format = "int64")) @PathVariable String id) {
        var result = attempts.start(auth.currentUser().userId(), positiveNumber(id, "id", Long.MAX_VALUE));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.session());
    }

    @GetMapping("/api/exam-attempts/{id}/result")
    @Operation(operationId = "getExamResult", tags = "exam")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "409", description = "Attempt not submitted or run not concluded")
    })
    public ExamResultService.ExamResult result(
            @Parameter(schema = @Schema(type = "integer", format = "int64")) @PathVariable String id) {
        return results.result(auth.currentUser().userId(), attemptId(id));
    }

    private static long attemptId(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed > 0) return parsed;
        } catch (NumberFormatException ignored) {
            // Malformed and unknown attempt IDs share the same error.
        }
        throw new ApiException(ErrorCode.ATTEMPT_NOT_OWNED, HttpStatus.FORBIDDEN,
                "Không có quyền truy cập lượt làm bài này.", Map.of());
    }

    private static long positiveNumber(String value, String field, long max) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed > 0 && parsed <= max) return parsed;
        } catch (NumberFormatException ignored) {
            // Invalid numbers use the same error envelope as out-of-range values.
        }
        throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Dữ liệu không hợp lệ.", Map.of("details", List.of(Map.of("field", field,
                        "message", "Giá trị không hợp lệ."))));
    }
}
