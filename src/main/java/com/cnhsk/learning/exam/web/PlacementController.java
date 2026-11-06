package com.cnhsk.learning.exam.web;

import java.util.List;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.exam.PlacementService;
import com.cnhsk.learning.practice.PracticeService.SessionView;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlacementController {
    private final PlacementService placement;
    private final AuthApi auth;
    private final ObjectMapper mapper;

    public PlacementController(PlacementService placement, AuthApi auth, ObjectMapper mapper) {
        this.placement = placement;
        this.auth = auth;
        this.mapper = mapper;
    }

    @GetMapping("/api/placement/summary")
    @Operation(operationId = "getPlacementSummary", tags = "placement")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public PlacementService.Summary summary() {
        return placement.summary(auth.currentUser().userId());
    }

    @PostMapping("/api/placement/runs")
    @Operation(operationId = "startPlacementRun", tags = "placement",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PlacementStartRequest.class))))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "422", description = "Request cannot be processed"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<StartedRun> start(@RequestBody(required = false) String body) {
        var run = placement.start(auth.currentUser().userId(), startLevel(body));
        return ResponseEntity.status(run.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(new StartedRun(run.runId(), run.session()));
    }

    @PostMapping("/api/placement/runs/{runId}/next")
    @Operation(operationId = "nextPlacementBlock", tags = "placement")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<PlacementStep> next(
            @Parameter(schema = @Schema(type = "integer", format = "int64")) @PathVariable String runId) {
        var step = placement.next(auth.currentUser().userId(), runId(runId));
        return ResponseEntity.status(step.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(new PlacementStep(step.runId(), step.session(), step.session() == null ? true : null));
    }

    @GetMapping("/api/placement/runs/{runId}/result")
    @Operation(operationId = "getPlacementResult", tags = "placement")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "409", description = "Attempt not submitted or run not concluded")
    })
    public PlacementService.Result result(
            @Parameter(schema = @Schema(type = "integer", format = "int64")) @PathVariable String runId) {
        return placement.result(auth.currentUser().userId(), runId(runId));
    }

    private static long runId(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed > 0) return parsed;
        } catch (NumberFormatException ignored) {
            // Invalid and missing run identifiers have the same response.
        }
        throw new ApiException(ErrorCode.ATTEMPT_NOT_OWNED, HttpStatus.FORBIDDEN,
                "Bạn không có quyền truy cập lượt luyện này.", Map.of());
    }

    private Integer startLevel(String body) {
        if (body == null) return null;
        JsonNode request;
        try {
            request = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(body);
        } catch (JsonProcessingException exception) {
            throw validation();
        }
        if (request == null || !request.isObject()) throw validation();
        JsonNode level = request.path("startLevel");
        if (level.isMissingNode()) return null;
        if (!level.isIntegralNumber() || !level.canConvertToInt() || level.intValue() < 1 || level.intValue() > 3) {
            throw validation();
        }
        return level.intValue();
    }

    private static ApiException validation() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Dữ liệu không hợp lệ.", Map.of("details", List.of(Map.of("field", "startLevel",
                        "message", "Giá trị không hợp lệ."))));
    }

    public record PlacementStep(long runId, @JsonInclude(JsonInclude.Include.ALWAYS) SessionView session,
            @JsonInclude(JsonInclude.Include.NON_NULL) Boolean concluded) {}

    public record StartedRun(long runId, SessionView session) {}

    private record PlacementStartRequest(
            @Schema(type = "integer", types = {"integer"}, minimum = "1", maximum = "3") Integer startLevel) {}
}
