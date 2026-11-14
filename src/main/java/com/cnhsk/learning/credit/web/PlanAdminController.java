package com.cnhsk.learning.credit.web;

import java.util.List;
import java.util.Map;
import com.cnhsk.learning.credit.PlanAdminService;
import com.cnhsk.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping(value = "/api/admin/plans", produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = "cookieAuth")
@SecurityRequirement(name = "bearerAuth")
public class PlanAdminController {
    private final PlanAdminService service;
    private final GlobalExceptionHandler errors;
    private final ObjectReader json;

    public PlanAdminController(PlanAdminService service, GlobalExceptionHandler errors, ObjectMapper mapper) {
        this.service = service;
        this.errors = errors;
        this.json = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    }

    @GetMapping
    @Operation(operationId = "listPlans", tags = "plan-admin")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Gói",
                    content = @Content(array = @ArraySchema(minItems = 3, maxItems = 3,
                            schema = @Schema(implementation = PlanView.class)))),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public List<PlanView> listPlans() {
        return service.listPlans();
    }

    @PutMapping(value = "/{code}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updatePlan", tags = "plan-admin",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = PlanUpdateRequest.class))))
    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER,
            description = "Bắt buộc khi xác thực bằng cookie (spec 001)", schema = @Schema(type = "string"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Gói sau khi lưu",
                    content = @Content(schema = @Schema(implementation = PlanView.class))),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN · CSRF_TOKEN_INVALID",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "409", description = "PLAN_IN_USE",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED · PLAN_NOT_AVAILABLE",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public PlanView updatePlan(@PathVariable PlanStatus.PlanCode code,
            @Parameter(hidden = true) @RequestBody String body) {
        PlanUpdateRequest validated;
        try {
            validated = PlanUpdateRequest.parse(json.readTree(body), code);
        } catch (JsonProcessingException exception) {
            throw PlanUpdateRequest.invalid();
        }
        validated.validate(code);
        return service.updatePlan(code, validated);
    }

    @Hidden
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> invalidRequest(Exception ignored, HttpServletRequest request) {
        return errors.handleApiException(PlanUpdateRequest.invalid(), request);
    }
}
