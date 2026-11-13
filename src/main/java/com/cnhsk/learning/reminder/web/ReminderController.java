package com.cnhsk.learning.reminder.web;

import com.cnhsk.learning.reminder.TodayReminderService;
import com.cnhsk.learning.reminder.TodayReminderService.TodayReminder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/reminders", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "reminder")
public class ReminderController {

    private final TodayReminderService reminders;

    public ReminderController(TodayReminderService reminders) {
        this.reminders = reminders;
    }

    @GetMapping("/today")
    @Operation(operationId = "getTodayReminder", summary = "Banner nhắc trên trang học")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trạng thái banner"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "403", description = "Không đủ quyền")
    })
    public TodayReminder today() {
        return reminders.today();
    }
}
