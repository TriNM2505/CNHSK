package com.cnhsk.learning.credit.web;

import com.cnhsk.learning.credit.RedeemService;
import com.cnhsk.learning.credit.WalletQueryService;
import com.cnhsk.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@SecurityRequirement(name = "cookieAuth")
@SecurityRequirement(name = "bearerAuth")
public class WalletController {
    private final RedeemService service;
    private final WalletQueryService queries;
    private final GlobalExceptionHandler errors;
    private final ObjectReader json;

    public WalletController(RedeemService service, WalletQueryService queries, GlobalExceptionHandler errors, ObjectMapper mapper) {
        this.service = service;
        this.queries = queries;
        this.errors = errors;
        this.json = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    }

    @GetMapping(value = "/api/me/credits", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "getMyWallet", tags = "wallet")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ví", content = @Content(schema = @Schema(implementation = Wallet.class))),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public Wallet wallet() {
        return queries.wallet();
    }

    @GetMapping(value = "/api/me/credit-transactions", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "listMyTransactions", tags = "wallet")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trang giao dịch", content = @Content(schema = @Schema(implementation = TransactionPage.class))),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public TransactionPage history(
            @Parameter(schema = @Schema(type = "integer", types = {"integer"}, minimum = "0", defaultValue = "0"))
            @RequestParam(defaultValue = "0") int page,
            @Parameter(schema = @Schema(type = "integer", types = {"integer"}, minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") int size) {
        return queries.history(page, size);
    }

    @Hidden
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<java.util.Map<String, Object>> invalidPagination(MethodArgumentTypeMismatchException ignored,
            HttpServletRequest request) {
        return errors.handleApiException(WalletQueryService.invalidPagination(), request);
    }

    @PostMapping(value = "/api/credits/redeem", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "redeemCard", tags = "wallet",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = RedeemRequest.class))))
    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER,
            description = "Bắt buộc khi xác thực bằng cookie (spec 001)", schema = @Schema(type = "string"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã nạp", content = @Content(schema = @Schema(implementation = RedeemResult.class))),
            @ApiResponse(responseCode = "400", description = "INVALID_CARD_CODE", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN · CSRF_TOKEN_INVALID", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED", content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public RedeemResult redeem(@RequestBody String body, HttpServletRequest request) {
        RedeemRequest validated;
        try {
            validated = RedeemRequest.parse(json.readTree(body));
        } catch (JsonProcessingException exception) {
            throw RedeemRequest.invalid();
        }
        return service.redeem(validated, request.getRemoteAddr());
    }

    @Hidden
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<java.util.Map<String, Object>> malformed(HttpMessageNotReadableException ignored,
            HttpServletRequest request) {
        return errors.handleApiException(RedeemRequest.invalid(), request);
    }
}
