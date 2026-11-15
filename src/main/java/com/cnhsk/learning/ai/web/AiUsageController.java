package com.cnhsk.learning.ai.web;

import com.cnhsk.learning.ai.TokenBudget;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/admin/ai-usage", produces = MediaType.APPLICATION_JSON_VALUE)
public class AiUsageController {
    private final TokenBudget budget;

    public AiUsageController(TokenBudget budget) {
        this.budget = budget;
    }

    @GetMapping
    @Operation(operationId = "getAdminAiUsage", tags = "admin-usage",
            summary = "Đọc số token đã dùng tháng này (ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Số liệu tháng VN; lỗi đọc trả available=false",
                    content = @Content(schema = @Schema(implementation = AiUsageResponse.class))),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED",
                    content = @Content(schema = @Schema(type = "object", requiredProperties = {"error_code", "message", "request_id"}),
                            schemaProperties = {
                                    @SchemaProperty(name = "error_code", schema = @Schema(type = "string")),
                                    @SchemaProperty(name = "message", schema = @Schema(type = "string")),
                                    @SchemaProperty(name = "request_id", schema = @Schema(type = "string"))})),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN",
                    content = @Content(schema = @Schema(type = "object", requiredProperties = {"error_code", "message", "request_id"}),
                            schemaProperties = {
                                    @SchemaProperty(name = "error_code", schema = @Schema(type = "string")),
                                    @SchemaProperty(name = "message", schema = @Schema(type = "string")),
                                    @SchemaProperty(name = "request_id", schema = @Schema(type = "string"))}))
    })
    public AiUsageResponse current() {
        TokenBudget.Usage usage = budget.currentUsage();
        return new AiUsageResponse(usage.month(), usage.used(), usage.budget(), usage.available());
    }

    @Schema(name = "AiUsageResponse", requiredProperties = {"month", "used", "budget", "available"})
    public record AiUsageResponse(
            @Schema(pattern = "^\\d{4}-(0[1-9]|1[0-2])$", example = "2026-11", description = "Tháng hiện tại theo Asia/Ho_Chi_Minh") String month,
            @Schema(types = {"integer", "null"}, format = "int64", minimum = "0", description = "null khi available=false") Long used,
            @Schema(format = "int32", description = "Ngân sách đọc từ SettingsApi tại thời điểm gọi") int budget,
            boolean available) {}
}
