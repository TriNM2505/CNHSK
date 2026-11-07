package com.cnhsk.learning.credit.web;

import java.util.Map;
import com.cnhsk.learning.credit.CardBatchService;
import com.cnhsk.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping(value = "/api/admin/card-batches", produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = "cookieAuth")
@SecurityRequirement(name = "bearerAuth")
@SecurityScheme(name = "cookieAuth", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.COOKIE, paramName = "access_token")
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class CardBatchController {
    private final CardBatchService service;
    private final GlobalExceptionHandler errors;

    public CardBatchController(CardBatchService service, GlobalExceptionHandler errors) {
        this.service = service;
        this.errors = errors;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createCardBatch", tags = "card-admin",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = CreateBatchRequest.class))))
    @Parameter(name = "X-XSRF-TOKEN", in = io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER,
            description = "Bắt buộc khi xác thực bằng cookie (spec 001)", schema = @Schema(type = "string"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lô đã sinh",
                    content = @Content(schema = @Schema(implementation = CreatedBatch.class))),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN · CSRF_TOKEN_INVALID",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED · PLAN_NOT_AVAILABLE",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public ResponseEntity<CreatedBatch> create(@RequestBody JsonNode body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(CreateBatchRequest.parse(body)));
    }

    @GetMapping
    @Operation(operationId = "listCardBatches", tags = "card-admin")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trang lô",
                    content = @Content(schema = @Schema(implementation = BatchPage.class))),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public BatchPage list(
            @Parameter(schema = @Schema(type = "integer", minimum = "0", defaultValue = "0"))
            @RequestParam(defaultValue = "0") int page,
            @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }

    @GetMapping("/{batchId}")
    @Operation(operationId = "getCardBatch", tags = "card-admin")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lô",
                    content = @Content(schema = @Schema(implementation = BatchSummary.class))),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "404", description = "CARD_BATCH_NOT_FOUND",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public BatchSummary detail(
            @Parameter(schema = @Schema(type = "string", pattern = "^B[0-9]{8}-[0-9A-Z]{6}$"))
            @PathVariable String batchId) {
        return service.detail(batchId);
    }

    @PatchMapping("/{batchId}/void")
    @Operation(operationId = "voidCardBatch", tags = "card-admin")
    @Parameter(name = "X-XSRF-TOKEN", in = io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER,
            description = "Bắt buộc khi xác thực bằng cookie (spec 001)", schema = @Schema(type = "string"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Số mã vừa khoá",
                    content = @Content(schema = @Schema(implementation = VoidResult.class))),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN · CSRF_TOKEN_INVALID",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "404", description = "CARD_BATCH_NOT_FOUND",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public VoidResult voidBatch(
            @Parameter(schema = @Schema(type = "string", pattern = "^B[0-9]{8}-[0-9A-Z]{6}$"))
            @PathVariable String batchId) {
        return service.voidBatch(batchId);
    }

    @Hidden
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> invalidRequest(Exception exception, HttpServletRequest request) {
        return errors.handleApiException(CreateBatchRequest.invalid(), request);
    }
}
