package com.cnhsk.learning.pronunciation.web;

import java.util.List;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.pronunciation.PronStageQueryService;
import com.cnhsk.learning.pronunciation.StageSummary;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@RestController
@RequestMapping("/api/pronunciation/stages")
public class PronunciationController {

    private final PronStageQueryService query;
    private final AuthApi auth;

    public PronunciationController(PronStageQueryService query, AuthApi auth) {
        this.query = query;
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

    /** Documents the shared security error body; security filters produce the response. */
    public record ErrorResponse(
            @JsonProperty("error_code") @Schema(requiredMode = REQUIRED) String errorCode,
            @Schema(requiredMode = REQUIRED) String message,
            @JsonProperty("request_id") @Schema(requiredMode = REQUIRED) String requestId) {
    }
}
