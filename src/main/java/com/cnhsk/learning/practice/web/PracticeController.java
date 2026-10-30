package com.cnhsk.learning.practice.web;

import java.util.List;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeService.SessionView;
import com.cnhsk.learning.practice.PracticeService.AnswerResult;
import com.cnhsk.learning.practice.PracticeSourceHandler.Answer;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.config.RequestIdFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/practice/sessions")
public class PracticeController {

    private final PracticeService practice;
    private final AuthApi auth;

    public PracticeController(PracticeService practice, AuthApi auth) {
        this.practice = practice;
        this.auth = auth;
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getSession", tags = "practice")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lượt luyện"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập"),
            @ApiResponse(responseCode = "422", description = "Dữ liệu không hợp lệ")
    })
    public SessionView view(@Parameter(schema = @Schema(type = "integer", format = "int64")) @PathVariable String id) {
        return practice.view(auth.currentUser().userId(), identifier(id, "id"));
    }

    @GetMapping("/active")
    @Operation(operationId = "findActiveSession", tags = "practice")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lượt luyện đang làm"),
            @ApiResponse(responseCode = "204", description = "Không có lượt đang làm"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Chỉ LEARNER được truy cập"),
            @ApiResponse(responseCode = "422", description = "Dữ liệu không hợp lệ")
    })
    public ResponseEntity<SessionView> active(
            @Parameter(required = true, schema = @Schema(implementation = PracticeSource.class))
            @RequestParam(required = false) String source,
            @Parameter(schema = @Schema(type = "integer", format = "int64"))
            @RequestParam(required = false) String refId) {
        PracticeSource parsed;
        try {
            parsed = PracticeSource.valueOf(source == null ? "" : source);
        } catch (IllegalArgumentException exception) {
            throw validation("source");
        }
        SessionView view = practice.findActive(auth.currentUser().userId(), parsed,
                refId == null ? null : identifier(refId, "refId"));
        return view == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(view);
    }

    @PostMapping("/{id}/answers")
    @Operation(operationId = "answerItem", tags = "practice", description = "Trả lời một câu theo spec 003 §0.6")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Kết quả hoặc xác nhận lưu nháp"),
            @ApiResponse(responseCode = "400", description = "ITEM_NOT_IN_SESSION, OPTION_NOT_IN_ITEM, TOO_MANY_REPLAYS, AUDIO_NOT_IN_ITEM, SKIP_NOT_ALLOWED"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập hoặc CSRF không hợp lệ"),
            @ApiResponse(responseCode = "409", description = "SESSION_ALREADY_SUBMITTED, ANSWER_ALREADY_SUBMITTED"),
            @ApiResponse(responseCode = "422", description = "SESSION_EXPIRED, VALIDATION_FAILED"),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED")
    })
    public AnswerResult answer(
            @Parameter(schema = @Schema(type = "integer", format = "int64")) @PathVariable String id,
            @Valid @RequestBody AnswerRequest request) {
        return practice.answer(auth.currentUser().userId(), identifier(id, "id"), request.toAnswer());
    }

    public record AnswerRequest(@NotNull @Min(1) Integer no, @Size(max = 100) String answer,
            @NotNull @Min(0) @Max(3_600_000) Integer durationMs,
            @Min(0) Integer replayCount, Boolean skipped, Boolean audioFailed, Map<String, Object> clientMeta) {

        public Answer toAnswer() {
            return new Answer(no, answer, durationMs, replayCount == null ? 0 : replayCount,
                    Boolean.TRUE.equals(skipped), Boolean.TRUE.equals(audioFailed), clientMeta);
        }
    }

    @PostMapping("/{id}/finish")
    @Operation(operationId = "finishSession", tags = "practice", description = "Kết thúc lượt theo spec 003 §0.8")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Kết quả kết thúc lượt"),
            @ApiResponse(responseCode = "400", description = "ITEM_NOT_IN_SESSION, OPTION_NOT_IN_ITEM, TOO_MANY_REPLAYS, AUDIO_NOT_IN_ITEM, SKIP_NOT_ALLOWED"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "ATTEMPT_NOT_OWNED hoặc CSRF không hợp lệ"),
            @ApiResponse(responseCode = "409", description = "SESSION_ALREADY_SUBMITTED, ANSWER_ALREADY_SUBMITTED"),
            @ApiResponse(responseCode = "422", description = "SESSION_EXPIRED, VALIDATION_FAILED"),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED")
    })
    public PracticeService.FinishResult finish(
            @Parameter(schema = @Schema(type = "integer", format = "int64")) @PathVariable String id,
            @Valid @RequestBody(required = false) FinishRequest request) {
        List<Answer> answers = request == null || request.answers() == null ? List.of()
                : request.answers().stream().map(AnswerRequest::toAnswer).toList();
        return practice.finish(auth.currentUser().userId(), identifier(id, "id"), answers);
    }

    public record FinishRequest(List<@NotNull @Valid AnswerRequest> answers) {
    }

    @PostMapping("/{id}/abandon")
    @Operation(operationId = "abandonSession", tags = "practice", description = "Bỏ lượt theo spec 003 §0.11")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã bỏ lượt, giữ nguyên bằng chứng"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "ATTEMPT_NOT_OWNED hoặc CSRF không hợp lệ"),
            @ApiResponse(responseCode = "409", description = "SESSION_ALREADY_SUBMITTED"),
            @ApiResponse(responseCode = "422", description = "SESSION_EXPIRED, VALIDATION_FAILED")
    })
    public ResponseEntity<Void> abandon(
            @Parameter(schema = @Schema(type = "integer", format = "int64")) @PathVariable String id) {
        practice.abandon(auth.currentUser().userId(), identifier(id, "id"));
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> malformedAnswer() {
        return ResponseEntity.unprocessableEntity().body(Map.of("error_code", ErrorCode.VALIDATION_FAILED.name(),
                "message", "Dữ liệu không hợp lệ.", "request_id", RequestIdFilter.currentRequestId(),
                "details", List.of(Map.of("field", "request", "message", "Dữ liệu không hợp lệ."))));
    }

    private static long identifier(String value, String field) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw validation(field);
        }
    }

    private static ApiException validation(String field) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Dữ liệu không hợp lệ.", Map.of("details", List.of(Map.of("field", field,
                        "message", "Giá trị không hợp lệ."))));
    }
}
