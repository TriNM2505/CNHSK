package com.cnhsk.community.notification.web;

import com.cnhsk.community.notification.NotificationService;
import com.cnhsk.community.notification.web.NotificationViews.NotificationPage;
import com.cnhsk.community.notification.web.NotificationViews.NotificationSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The bell for every signed-in role. */
@RestController
public class NotificationController {
    private static final String ERROR = "#/components/schemas/ErrorResponse";
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping(value = "/api/me/notifications/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "getNotificationSummary", tags = "notifications")
    @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = NotificationSummary.class)))
    @ApiResponse(responseCode = "401", description = "Chưa xác thực", content = @Content(schema = @Schema(ref = ERROR)))
    @SecurityRequirement(name = "cookieAuth")
    @SecurityRequirement(name = "bearerAuth")
    public NotificationSummary summary() {
        return service.summary();
    }

    @GetMapping(value = "/api/me/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "listNotifications", tags = "notifications")
    @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = NotificationPage.class)))
    @ApiResponse(responseCode = "401", description = "Chưa xác thực", content = @Content(schema = @Schema(ref = ERROR)))
    @SecurityRequirement(name = "cookieAuth")
    @SecurityRequirement(name = "bearerAuth")
    public NotificationPage list() {
        return service.list();
    }

    @PostMapping("/api/me/notifications/seen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "markNotificationsSeen", tags = "notifications")
    @ApiResponse(responseCode = "204", description = "Đã đánh dấu đã xem")
    @ApiResponse(responseCode = "401", description = "Chưa xác thực", content = @Content(schema = @Schema(ref = ERROR)))
    @SecurityRequirement(name = "cookieAuth")
    @SecurityRequirement(name = "bearerAuth")
    public void seen() {
        service.markSeen();
    }
}
