package com.cnhsk.shared.settings;

import static com.cnhsk.shared.settings.SettingsAdminModels.*;
import static com.cnhsk.shared.settings.SettingsAdminService.invalid;
import static com.cnhsk.shared.settings.SettingsAdminService.outOfRange;

import java.io.IOException;
import java.util.List;

import com.cnhsk.shared.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/admin/settings", produces = MediaType.APPLICATION_JSON_VALUE)
public class SettingsController {
    private final SettingsAdminService service;
    private final ObjectMapper mapper;

    public SettingsController(SettingsAdminService service, ObjectMapper mapper) { this.service = service; this.mapper = mapper; }

    @GetMapping
    @Operation(operationId = "listSettings", tags = "settings")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Cài đặt theo nhóm"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN")})
    public List<SettingItem> list() { return service.list(); }

    @PutMapping("/{key}")
    @Operation(operationId = "updateSetting", tags = "settings",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = SettingWrite.class))))
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Đã đổi cài đặt"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN hoặc CSRF_TOKEN_INVALID"),
            @ApiResponse(responseCode = "404", description = "SETTING_NOT_FOUND"),
            @ApiResponse(responseCode = "422", description = "SETTING_OUT_OF_RANGE hoặc VALIDATION_FAILED")})
    public SettingItem change(@PathVariable @Parameter(schema = @Schema(maxLength = 100)) String key,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request,
            @RequestHeader(name = "X-XSRF-TOKEN", required = false) String xsrf) {
        return service.change(key, user.userId(), () -> {
            JsonNode body = read(request);
            JsonNode value = body.path("value");
            if (!value.isIntegralNumber() || !value.canConvertToInt()) throw outOfRange();
            return new SettingWrite(value.intValue(), reason(body));
        });
    }

    @DeleteMapping("/{key}")
    @Operation(operationId = "resetSetting", tags = "settings",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = SettingReset.class))))
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Đã về mặc định"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN hoặc CSRF_TOKEN_INVALID"),
            @ApiResponse(responseCode = "404", description = "SETTING_NOT_FOUND"),
            @ApiResponse(responseCode = "422", description = "SETTING_OUT_OF_RANGE hoặc VALIDATION_FAILED")})
    public SettingItem reset(@PathVariable @Parameter(schema = @Schema(maxLength = 100)) String key,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request,
            @RequestHeader(name = "X-XSRF-TOKEN", required = false) String xsrf) {
        return service.reset(key, user.userId(), () -> new SettingReset(reason(read(request))));
    }

    @GetMapping("/{key}/history")
    @Operation(operationId = "getSettingHistory", tags = "settings")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Lịch sử cài đặt"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
            @ApiResponse(responseCode = "404", description = "SETTING_NOT_FOUND"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")})
    public SettingHistoryPage history(@PathVariable @Parameter(schema = @Schema(maxLength = 100)) String key,
            @RequestParam(required = false) @Parameter(schema = @Schema(type = "integer", minimum = "0", defaultValue = "0")) String page,
            @RequestParam(required = false) @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "50", defaultValue = "20")) String size) {
        return service.history(key, page == null ? 0 : integer(page, "page"), size == null ? 20 : integer(size, "size"));
    }

    private JsonNode read(HttpServletRequest request) {
        try {
            JsonNode body = mapper.readerFor(JsonNode.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(request.getInputStream());
            if (body == null || !body.isObject()) throw invalid("body");
            return body;
        } catch (IOException exception) { throw invalid("body"); }
    }

    private String reason(JsonNode body) {
        if (!body.path("reason").isTextual()) throw invalid("reason");
        return body.get("reason").textValue();
    }

    private int integer(String raw, String field) {
        try { return Integer.parseInt(raw); }
        catch (NumberFormatException exception) { throw invalid(field); }
    }
}
