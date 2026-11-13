package com.cnhsk.auth.controller;

import com.cnhsk.auth.dto.ReminderSettingsResponse;
import com.cnhsk.auth.dto.ReminderUpdateRequest;
import com.cnhsk.auth.reminder.ReminderSettingsService;
import com.cnhsk.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reminder settings scoped to the authenticated learner. */
@RestController
@RequestMapping(value = "/api/me/reminder", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "reminder")
@SecurityRequirement(name = "cookieAuth")
@SecurityRequirement(name = "bearerAuth")
public class ReminderSettingsController {

    private final ReminderSettingsService reminderSettingsService;

    public ReminderSettingsController(ReminderSettingsService reminderSettingsService) {
        this.reminderSettingsService = reminderSettingsService;
    }

    @GetMapping
    @Operation(operationId = "getReminderSettings", summary = "Cài đặt nhắc của tôi hoặc mặc định")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cài đặt"),
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
    public ReminderSettingsResponse getSettings(@AuthenticationPrincipal AuthenticatedUser user) {
        return reminderSettingsService.getSettings(user.userId());
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateReminderSettings", summary = "Lưu giờ nhắc và kênh")
    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER,
            description = "Bắt buộc khi xác thực bằng cookie (spec 001)", schema = @Schema(type = "string"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã lưu"),
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
            @ApiResponse(responseCode = "403", description = "Không đủ quyền hoặc CSRF không hợp lệ", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "object", requiredProperties = {"error_code", "message", "request_id"}),
                            schemaProperties = {
                                    @SchemaProperty(name = "error_code", schema = @Schema(type = "string")),
                                    @SchemaProperty(name = "message", schema = @Schema(type = "string")),
                                    @SchemaProperty(name = "request_id", schema = @Schema(type = "string")),
                                    @SchemaProperty(name = "retryAfterSeconds", schema = @Schema(type = "integer")),
                                    @SchemaProperty(name = "learnedWords", schema = @Schema(type = "integer")),
                                    @SchemaProperty(name = "requiredWords", schema = @Schema(type = "integer"))
                            })),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED · EMAIL_NOT_VERIFIED", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
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
    public ReminderSettingsResponse updateSettings(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ReminderUpdateRequest request) {
        return reminderSettingsService.updateSettings(user.userId(), request);
    }
}
