package com.cnhsk.auth.controller;

import com.cnhsk.auth.dto.ReminderSettingsResponse;
import com.cnhsk.auth.dto.ReminderUpdateRequest;
import com.cnhsk.auth.reminder.ReminderSettingsService;
import com.cnhsk.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
public class ReminderSettingsController {

    private final ReminderSettingsService reminderSettingsService;

    public ReminderSettingsController(ReminderSettingsService reminderSettingsService) {
        this.reminderSettingsService = reminderSettingsService;
    }

    @GetMapping
    @Operation(operationId = "getReminderSettings", summary = "Cài đặt nhắc của tôi hoặc mặc định")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cài đặt"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "403", description = "Không đủ quyền")
    })
    public ReminderSettingsResponse getSettings(@AuthenticationPrincipal AuthenticatedUser user) {
        return reminderSettingsService.getSettings(user.userId());
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateReminderSettings", summary = "Lưu giờ nhắc và kênh")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã lưu"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "403", description = "Không đủ quyền hoặc CSRF không hợp lệ"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED · EMAIL_NOT_VERIFIED")
    })
    public ReminderSettingsResponse updateSettings(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ReminderUpdateRequest request) {
        return reminderSettingsService.updateSettings(user.userId(), request);
    }
}
