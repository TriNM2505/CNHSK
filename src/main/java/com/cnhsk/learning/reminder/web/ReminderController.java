package com.cnhsk.learning.reminder.web;

import com.cnhsk.learning.reminder.TodayReminderService;
import com.cnhsk.learning.reminder.TodayReminderService.TodayReminder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/reminders", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "reminder")
@SecurityRequirement(name = "cookieAuth")
@SecurityRequirement(name = "bearerAuth")
public class ReminderController {

    private final TodayReminderService reminders;

    public ReminderController(TodayReminderService reminders) {
        this.reminders = reminders;
    }

    @GetMapping("/today")
    @Operation(operationId = "getTodayReminder", summary = "Banner nhắc trên trang học")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trạng thái banner"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "object", requiredProperties = {"error_code", "message", "request_id"}),
                            schemaProperties = {
                                    @SchemaProperty(name = "error_code", schema = @Schema(type = "string")),
                                    @SchemaProperty(name = "message", schema = @Schema(type = "string")),
                                    @SchemaProperty(name = "request_id", schema = @Schema(type = "string")),
                                    @SchemaProperty(name = "retryAfterSeconds", schema = @Schema(type = "integer")),
                                    @SchemaProperty(name = "learnedWords", schema = @Schema(type = "integer")),
                                    @SchemaProperty(name = "requiredWords", schema = @Schema(type = "integer"))
                            })),
            @ApiResponse(responseCode = "403", description = "Không đủ quyền", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "object", requiredProperties = {"error_code", "message", "request_id"}),
                            schemaProperties = {
                                    @SchemaProperty(name = "error_code", schema = @Schema(type = "string")),
                                    @SchemaProperty(name = "message", schema = @Schema(type = "string")),
                                    @SchemaProperty(name = "request_id", schema = @Schema(type = "string")),
                                    @SchemaProperty(name = "retryAfterSeconds", schema = @Schema(type = "integer")),
                                    @SchemaProperty(name = "learnedWords", schema = @Schema(type = "integer")),
                                    @SchemaProperty(name = "requiredWords", schema = @Schema(type = "integer"))
                            }))
    })
    public TodayReminder today() {
        return reminders.today();
    }
}
