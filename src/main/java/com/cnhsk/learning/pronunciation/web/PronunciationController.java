package com.cnhsk.learning.pronunciation.web;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.pronunciation.PronStageQueryService;
import com.cnhsk.learning.pronunciation.PronStageSessionService;
import com.cnhsk.learning.pronunciation.StageSummary;
import com.cnhsk.learning.practice.PracticeService.SessionView;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@RestController
@RequestMapping("/api/pronunciation/stages")
public class PronunciationController {

    private final PronStageQueryService query;
    private final PronStageSessionService sessions;
    private final AuthApi auth;

    public PronunciationController(PronStageQueryService query, PronStageSessionService sessions, AuthApi auth) {
        this.query = query;
        this.sessions = sessions;
        this.auth = auth;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "listPronunciationStages", tags = "pronunciation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Danh sách tầng phát âm"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Chỉ LEARNER được truy cập",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public List<StageSummary> list() {
        return query.list(auth.currentUser().userId());
    }

    @PostMapping(value = "/{stageIndex}/sessions", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "startPronunciationStage", tags = "pronunciation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tiếp tục lượt đang làm"),
            @ApiResponse(responseCode = "201", description = "Lượt mới"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Tầng khóa, sai role hoặc CSRF không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không có tầng phát âm",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Tầng chưa có audio hoặc dữ liệu không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Vượt giới hạn lượt luyện",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SessionView> start(
            @Parameter(schema = @Schema(type = "integer")) @PathVariable String stageIndex) {
        var result = sessions.start(auth.currentUser().userId(), parseStageIndex(stageIndex));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.session());
    }

    private static int parseStageIndex(String value) {
        try {
            return new BigInteger(value).intValueExact();
        } catch (ArithmeticException exception) {
            // The service returns the same 404 for every integer outside the supported range.
            return 0;
        } catch (NumberFormatException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Số tầng phải là số nguyên.", Map.of("details", List.of(
                            Map.of("field", "stageIndex", "message", "Số tầng phải là số nguyên."))));
        }
    }

    /** Documents the shared security error body; security filters produce the response. */
    public record ErrorResponse(
            @JsonProperty("error_code") @Schema(requiredMode = REQUIRED) String errorCode,
            @Schema(requiredMode = REQUIRED) String message,
            @JsonProperty("request_id") @Schema(requiredMode = REQUIRED) String requestId) {
    }
}
