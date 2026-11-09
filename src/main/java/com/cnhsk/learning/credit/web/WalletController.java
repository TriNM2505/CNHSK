package com.cnhsk.learning.credit.web;

import com.cnhsk.learning.credit.RedeemService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "cookieAuth")
@SecurityRequirement(name = "bearerAuth")
public class WalletController {
    private final RedeemService service;
    private final GlobalExceptionHandler errors;
    private final ObjectReader json;

    public WalletController(RedeemService service, GlobalExceptionHandler errors, ObjectMapper mapper) {
        this.service = service;
        this.errors = errors;
        this.json = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
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
