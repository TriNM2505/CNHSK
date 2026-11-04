package com.cnhsk.learning.exam.web;

import java.util.List;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.exam.ExamAttemptService;
import com.cnhsk.learning.exam.ExamQueryService;
import com.cnhsk.learning.exam.ExamQueryService.ExamSummary;
import com.cnhsk.learning.practice.PracticeService.SessionView;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exams")
public class ExamController {
    private final ExamQueryService exams;
    private final ExamAttemptService attempts;
    private final AuthApi auth;

    public ExamController(ExamQueryService exams, ExamAttemptService attempts, AuthApi auth) {
        this.exams = exams;
        this.attempts = attempts;
        this.auth = auth;
    }

    @GetMapping
    @Operation(operationId = "listExams", tags = "exam")
    public List<ExamSummary> list(
            @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "9"))
            @RequestParam(required = false) String hskLevel) {
        Integer level = hskLevel == null ? null : (int) positiveNumber(hskLevel, "hskLevel", 9);
        return exams.list(auth.currentUser().userId(), level);
    }

    @PostMapping("/{id}/attempts")
    @Operation(operationId = "startExamAttempt", tags = "exam")
    public ResponseEntity<SessionView> start(
            @Parameter(schema = @Schema(type = "integer", format = "int64")) @PathVariable String id) {
        var result = attempts.start(auth.currentUser().userId(), positiveNumber(id, "id", Long.MAX_VALUE));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.session());
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
