package com.cnhsk.learning.credit.web;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import com.cnhsk.learning.credit.LedgerFilter;
import com.cnhsk.learning.credit.LedgerQueryService;
import com.cnhsk.learning.credit.ReconciliationService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.GlobalExceptionHandler;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/admin/credit-transactions")
@SecurityRequirement(name = "cookieAuth")
@SecurityRequirement(name = "bearerAuth")
public class LedgerAdminController {
    private final LedgerQueryService query;
    private final ReconciliationService reconciliation;
    private final GlobalExceptionHandler errors;

    public LedgerAdminController(LedgerQueryService query, ReconciliationService reconciliation,
            GlobalExceptionHandler errors) {
        this.query = query;
        this.reconciliation = reconciliation;
        this.errors = errors;
    }

    @GetMapping
    @Operation(operationId = "listLedger", tags = "ledger-admin")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ledger page and full filtered totals",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LedgerPage.class))),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public ResponseEntity<LedgerPage> listLedger(
            @Parameter(required = true, schema = @Schema(type = "string", format = "date"))
            @RequestParam(required = false) String from,
            @Parameter(required = true, schema = @Schema(type = "string", format = "date"))
            @RequestParam(required = false) String to,
            @Parameter(schema = @Schema(ref = "#/components/schemas/TransactionType"))
            @RequestParam(required = false) String type,
            @Parameter(schema = @Schema(type = "integer", minimum = "0", defaultValue = "0"))
            @RequestParam(defaultValue = "0") int page,
            @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") int size) {
        LedgerFilter filter = LedgerFilter.parse(from, to, type);
        if (page < 0 || size < 1 || size > 100) throw LedgerFilter.invalid();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(query.list(filter, page, size));
    }

    @GetMapping("/export")
    @Operation(operationId = "exportLedger", tags = "ledger-admin")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "CSV UTF-8",
                    content = @Content(mediaType = "text/csv", schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED · EXPORT_TOO_LARGE",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public ResponseEntity<byte[]> exportLedger(
            @Parameter(required = true, schema = @Schema(type = "string", format = "date"))
            @RequestParam(required = false) String from,
            @Parameter(required = true, schema = @Schema(type = "string", format = "date"))
            @RequestParam(required = false) String to,
            @Parameter(schema = @Schema(ref = "#/components/schemas/TransactionType"))
            @RequestParam(required = false) String type) {
        byte[] bytes = query.export(LedgerFilter.parse(from, to, type));
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("credit-ledger.csv").build().toString())
                .body(bytes);
    }

    @GetMapping("/reconciliation")
    @Operation(operationId = "reconcileLedger", tags = "ledger-admin")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Global ledger reconciliation",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Reconciliation.class))),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public ResponseEntity<Reconciliation> reconcileLedger() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(reconciliation.reconcile());
    }

    @Hidden
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, Object>> invalidRequest(Exception ignored, HttpServletRequest request) {
        return json(errors.handleApiException(LedgerFilter.invalid(), request));
    }

    @Hidden
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> apiFailure(ApiException exception, HttpServletRequest request) {
        return json(errors.handleApiException(exception, request));
    }

    @Hidden
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpectedFailure(Exception exception, HttpServletRequest request) {
        return json(errors.handleUnexpectedException(exception, request));
    }

    private static ResponseEntity<Map<String, Object>> json(ResponseEntity<Map<String, Object>> response) {
        return ResponseEntity.status(response.getStatusCode()).contentType(MediaType.APPLICATION_JSON)
                .body(response.getBody());
    }
}
