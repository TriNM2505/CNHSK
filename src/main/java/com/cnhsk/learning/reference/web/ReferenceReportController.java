package com.cnhsk.learning.reference.web;

import java.util.Map;

import com.cnhsk.learning.reference.ReferenceReportService;
import com.cnhsk.learning.reference.ReferenceReportService.Reason;
import com.cnhsk.learning.reference.ReferenceReportService.ReportCommand;
import com.cnhsk.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/references")
public class ReferenceReportController {

    private final ReferenceReportService service;
    private final ObjectReader reader;
    private final GlobalExceptionHandler errors;

    public ReferenceReportController(ReferenceReportService service, ObjectMapper mapper, GlobalExceptionHandler errors) {
        this.service = service;
        this.reader = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.errors = errors;
    }

    @PostMapping("/{id}/report")
    @Operation(operationId = "reportReference", tags = "references-public", security = {@SecurityRequirement(name = "cookieAuth"), @SecurityRequirement(name = "bearerAuth")})
    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = false, schema = @Schema(type = "string"))
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Đã ghi nhận", content = @Content),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED", content = @Content(mediaType = "application/json", schema = @Schema(type = "object", requiredProperties = {"error_code", "message", "request_id"}), schemaProperties = {
                    @SchemaProperty(name = "error_code", schema = @Schema(type = "string")),
                    @SchemaProperty(name = "message", schema = @Schema(type = "string")),
                    @SchemaProperty(name = "request_id", schema = @Schema(type = "string")),
                    @SchemaProperty(name = "retryAfterSeconds", schema = @Schema(type = "integer")),
                    @SchemaProperty(name = "learnedWords", schema = @Schema(type = "integer")),
                    @SchemaProperty(name = "requiredWords", schema = @Schema(type = "integer"))} )),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN · CSRF_TOKEN_INVALID", content = @Content(mediaType = "application/json", schema = @Schema(type = "object", requiredProperties = {"error_code", "message", "request_id"}), schemaProperties = {
                    @SchemaProperty(name = "error_code", schema = @Schema(type = "string")),
                    @SchemaProperty(name = "message", schema = @Schema(type = "string")),
                    @SchemaProperty(name = "request_id", schema = @Schema(type = "string")),
                    @SchemaProperty(name = "retryAfterSeconds", schema = @Schema(type = "integer")),
                    @SchemaProperty(name = "learnedWords", schema = @Schema(type = "integer")),
                    @SchemaProperty(name = "requiredWords", schema = @Schema(type = "integer"))} )),
            @ApiResponse(responseCode = "404", description = "REFERENCE_NOT_FOUND", content = @Content(mediaType = "application/json", schema = @Schema(type = "object", requiredProperties = {"error_code", "message", "request_id"}), schemaProperties = {
                    @SchemaProperty(name = "error_code", schema = @Schema(type = "string")),
                    @SchemaProperty(name = "message", schema = @Schema(type = "string")),
                    @SchemaProperty(name = "request_id", schema = @Schema(type = "string")),
                    @SchemaProperty(name = "retryAfterSeconds", schema = @Schema(type = "integer")),
                    @SchemaProperty(name = "learnedWords", schema = @Schema(type = "integer")),
                    @SchemaProperty(name = "requiredWords", schema = @Schema(type = "integer"))} )),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED", content = @Content(mediaType = "application/json", schema = @Schema(type = "object", requiredProperties = {"error_code", "message", "request_id"}), schemaProperties = {
                    @SchemaProperty(name = "error_code", schema = @Schema(type = "string")),
                    @SchemaProperty(name = "message", schema = @Schema(type = "string")),
                    @SchemaProperty(name = "request_id", schema = @Schema(type = "string")),
                    @SchemaProperty(name = "retryAfterSeconds", schema = @Schema(type = "integer")),
                    @SchemaProperty(name = "learnedWords", schema = @Schema(type = "integer")),
                    @SchemaProperty(name = "requiredWords", schema = @Schema(type = "integer"))} )),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED", content = @Content(mediaType = "application/json", schema = @Schema(type = "object", requiredProperties = {"error_code", "message", "request_id"}), schemaProperties = {
                    @SchemaProperty(name = "error_code", schema = @Schema(type = "string")),
                    @SchemaProperty(name = "message", schema = @Schema(type = "string")),
                    @SchemaProperty(name = "request_id", schema = @Schema(type = "string")),
                    @SchemaProperty(name = "retryAfterSeconds", schema = @Schema(type = "integer")),
                    @SchemaProperty(name = "learnedWords", schema = @Schema(type = "integer")),
                    @SchemaProperty(name = "requiredWords", schema = @Schema(type = "integer"))} ))})
    public ResponseEntity<Void> report(
            @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) @PathVariable String id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = ReportCommand.class)))
            @RequestBody(required = false) String body) {
        service.report(id, command(body));
        return ResponseEntity.noContent().build();
    }

    @Hidden
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> malformedBody(HttpServletRequest request) {
        return errors.handleApiException(ReferenceReportService.invalid("request"), request);
    }

    private ReportCommand command(String rawBody) {
        if (rawBody == null) throw ReferenceReportService.invalid("request");
        JsonNode body;
        try {
            body = reader.readTree(rawBody);
        } catch (JsonProcessingException exception) {
            throw ReferenceReportService.invalid("request");
        }
        if (body == null || !body.isObject()) throw ReferenceReportService.invalid("request");
        JsonNode reason = body.get("reason");
        if (reason == null || !reason.isTextual()) throw ReferenceReportService.invalid("reason");
        Reason parsed;
        try {
            parsed = Reason.valueOf(reason.textValue());
        } catch (IllegalArgumentException exception) {
            throw ReferenceReportService.invalid("reason");
        }
        JsonNode note = body.get("note");
        if (note != null && !note.isNull() && !note.isTextual()) throw ReferenceReportService.invalid("note");
        return new ReportCommand(parsed, note == null || note.isNull() ? null : note.textValue());
    }
}
