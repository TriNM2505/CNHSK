package com.cnhsk.auth.controller;

import java.io.IOException;
import java.util.Map;

import com.cnhsk.auth.dto.ReminderUnsubscribeRequest;
import com.cnhsk.auth.reminder.ReminderUnsubscribeRateLimiter;
import com.cnhsk.auth.reminder.ReminderUnsubscribeService;
import com.cnhsk.auth.reminder.UnsubscribeTokens;
import com.cnhsk.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReminderUnsubscribeController {

    private final ReminderUnsubscribeService service;
    private final ReminderUnsubscribeRateLimiter limiter;
    private final ObjectMapper json;
    private final GlobalExceptionHandler errors;

    public ReminderUnsubscribeController(ReminderUnsubscribeService service, ReminderUnsubscribeRateLimiter limiter,
            ObjectMapper json, GlobalExceptionHandler errors) {
        this.service = service;
        this.limiter = limiter;
        this.json = json;
        this.errors = errors;
    }

    @PostMapping("/api/public/reminder/unsubscribe")
    @Operation(operationId = "unsubscribeReminderEmail", tags = "reminder",
            summary = "Tắt nhắc email bằng token ký, idempotent, miễn CSRF",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ReminderUnsubscribeRequest.class)),
                    @Content(mediaType = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                            schema = @Schema(type = "object"), schemaProperties = {
                                    @SchemaProperty(name = "List-Unsubscribe", schema = @Schema(type = "string", example = "One-Click")),
                                    @SchemaProperty(name = "token", schema = @Schema(type = "string", maxLength = 512))
                            })}))
    @Parameter(name = "token", in = ParameterIn.QUERY, schema = @Schema(type = "string", maxLength = 512))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã tắt hoặc đã tắt từ trước"),
            @ApiResponse(responseCode = "400", description = "INVALID_UNSUBSCRIBE_TOKEN",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "object", requiredProperties = {"error_code", "message", "request_id"}),
                            schemaProperties = {
                                    @SchemaProperty(name = "error_code", schema = @Schema(type = "string")),
                                    @SchemaProperty(name = "message", schema = @Schema(type = "string")),
                                    @SchemaProperty(name = "request_id", schema = @Schema(type = "string")),
                                    @SchemaProperty(name = "retryAfterSeconds", schema = @Schema(type = "integer")),
                                    @SchemaProperty(name = "learnedWords", schema = @Schema(type = "integer")),
                                    @SchemaProperty(name = "requiredWords", schema = @Schema(type = "integer"))
                            })),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "object", requiredProperties = {"error_code", "message", "request_id"}),
                            schemaProperties = {
                                    @SchemaProperty(name = "error_code", schema = @Schema(type = "string")),
                                    @SchemaProperty(name = "message", schema = @Schema(type = "string")),
                                    @SchemaProperty(name = "request_id", schema = @Schema(type = "string")),
                                    @SchemaProperty(name = "retryAfterSeconds", schema = @Schema(type = "integer")),
                                    @SchemaProperty(name = "learnedWords", schema = @Schema(type = "integer")),
                                    @SchemaProperty(name = "requiredWords", schema = @Schema(type = "integer"))
                            }))})
    public ResponseEntity<Void> unsubscribe(HttpServletRequest request) throws IOException {
        limiter.check(request.getRemoteAddr());
        String selected = null;
        String[] parameters = request.getParameterValues("token");
        if (parameters != null) {
            for (String token : parameters) {
                selected = select(selected, token);
            }
        }
        if (request.getContentType() != null
                && MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(request.getContentType()))
                && request.getContentLengthLong() != 0) {
            ReminderUnsubscribeRequest body = ReminderUnsubscribeRequest.parse(json.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readTree(request.getInputStream()));
            if (body.token() != null) {
                selected = select(selected, body.token());
            }
        }
        if (selected == null) {
            throw UnsubscribeTokens.invalid();
        }
        service.unsubscribe(selected);
        return ResponseEntity.noContent().build();
    }

    private static String select(String selected, String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.length() > 512
                || selected != null && !selected.equals(candidate)) {
            throw UnsubscribeTokens.invalid();
        }
        return candidate;
    }

    @Hidden
    @ExceptionHandler({HttpMessageNotReadableException.class, JsonProcessingException.class})
    public ResponseEntity<Map<String, Object>> malformed(Exception ignored, HttpServletRequest request) {
        return errors.handleApiException(UnsubscribeTokens.invalid(), request);
    }
}
