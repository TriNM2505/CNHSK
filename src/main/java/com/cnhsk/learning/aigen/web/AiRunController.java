package com.cnhsk.learning.aigen.web;

import com.cnhsk.learning.aigen.AiReviewService;
import com.cnhsk.learning.aigen.AiRunQueryService;
import com.cnhsk.learning.aigen.AiRunQueryService.RunPage;
import com.cnhsk.learning.aigen.AiRunTrigger;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/admin/ai-generation-runs", produces = MediaType.APPLICATION_JSON_VALUE)
public class AiRunController {
    private final AiRunQueryService runs;
    private final AiRunTrigger trigger;

    public AiRunController(AiRunQueryService runs, AiRunTrigger trigger) {
        this.runs = runs;
        this.trigger = trigger;
    }

    @GetMapping
    @Operation(operationId = "listAiGenerationRuns", tags = "ai-runs")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Không có quyền"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")})
    public RunPage list(
            @Parameter(schema = @Schema(allowableValues = {"RUNNING", "COMPLETED", "FAILED"})) @RequestParam(required = false) String status,
            @Parameter(schema = @Schema(type = "integer", minimum = "0", defaultValue = "0")) @RequestParam(required = false) String page,
            @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "50", defaultValue = "20")) @RequestParam(required = false) String size) {
        runs.requireAdmin();
        return runs.list(status, page == null ? 0 : integer(page, "page"), size == null ? 20 : integer(size, "size"));
    }

    @PostMapping("/trigger")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(operationId = "triggerAiGeneration", tags = "ai-runs")
    @ApiResponses({@ApiResponse(responseCode = "202", description = "Đã bắt đầu"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Không có quyền hoặc CSRF"),
            @ApiResponse(responseCode = "409", description = "AI_GENERATION_BUSY hoặc AI_GENERATION_DISABLED")})
    public TriggerResult trigger(@RequestHeader(name = "X-XSRF-TOKEN", required = false) String xsrfToken) {
        runs.requireAdmin();
        trigger.trigger();
        return new TriggerResult(true);
    }

    private static int integer(String value, String field) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) { throw AiReviewService.validation(field); }
    }

    @Schema(name = "TriggerResult", requiredProperties = "started")
    public record TriggerResult(boolean started) { }
}
