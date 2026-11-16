package com.cnhsk.learning.aigen.web;

import com.cnhsk.learning.aigen.AiReviewService;
import com.cnhsk.learning.aigen.AiReviewService.AiQuestionPage;
import com.cnhsk.learning.aigen.AiReviewService.BulkResult;
import com.cnhsk.learning.aigen.AiReviewService.ReviewResult;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/admin/ai-questions", produces = MediaType.APPLICATION_JSON_VALUE)
public class AiReviewController {
    private final AiReviewService reviews;
    private final ObjectMapper mapper;

    public AiReviewController(AiReviewService reviews, ObjectMapper mapper) {
        this.reviews = reviews;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(operationId = "listAiQuestions", tags = "ai-review")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Không có quyền"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")})
    public AiQuestionPage list(
            @Parameter(schema = @Schema(maxLength = 20)) @RequestParam(required = false) String kid,
            @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "9")) @RequestParam(required = false) String hsk,
            @Parameter(schema = @Schema(type = "string", format = "date-time")) @RequestParam(required = false) String from,
            @Parameter(schema = @Schema(type = "string", format = "date-time")) @RequestParam(required = false) String to,
            @Parameter(schema = @Schema(type = "integer", minimum = "0", defaultValue = "0")) @RequestParam(required = false) String page,
            @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "50", defaultValue = "20")) @RequestParam(required = false) String size) {
        return reviews.list(kid, hsk == null ? null : integer(hsk, "hsk"), instant(from, "from"), instant(to, "to"),
                page == null ? 0 : integer(page, "page"), size == null ? 20 : integer(size, "size"));
    }

    @PostMapping("/{id}/approve")
    @Operation(operationId = "approveAiQuestion", tags = "ai-review")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "APPROVED"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Không có quyền hoặc CSRF"),
            @ApiResponse(responseCode = "404", description = "QUESTION_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "QUESTION_NOT_PENDING"),
            @ApiResponse(responseCode = "422", description = "MALFORMED_QUESTION")})
    public ReviewResult approve(
            @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) @PathVariable String id,
            @RequestHeader(name = "X-XSRF-TOKEN", required = false) String xsrfToken) {
        return reviews.approve(id(id));
    }

    @PostMapping("/{id}/reject")
    @Operation(operationId = "rejectAiQuestion", tags = "ai-review", requestBody =
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = RejectBody.class))))
    @ApiResponses({@ApiResponse(responseCode = "200", description = "REJECTED"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Không có quyền hoặc CSRF"),
            @ApiResponse(responseCode = "404", description = "QUESTION_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "QUESTION_NOT_PENDING"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")})
    public ReviewResult reject(
            @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) @PathVariable String id,
            HttpServletRequest request, @RequestHeader(name = "X-XSRF-TOKEN", required = false) String xsrfToken) {
        long questionId = id(id);
        JsonNode body = body(request);
        JsonNode reason = body.get("reason");
        if (reason == null || !reason.isTextual()) throw AiReviewService.validation("reason");
        return reviews.reject(questionId, reason.textValue());
    }

    @PostMapping("/bulk-approve")
    @Operation(operationId = "bulkApproveAiQuestions", tags = "ai-review", requestBody =
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = BulkApproveBody.class))))
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Kết quả từng câu"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Không có quyền hoặc CSRF"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED"),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR; ID trước đã commit được giữ nguyên")})
    public BulkResult bulkApprove(HttpServletRequest request,
            @RequestHeader(name = "X-XSRF-TOKEN", required = false) String xsrfToken) {
        JsonNode values = body(request).get("ids");
        if (values == null || !values.isArray() || values.isEmpty() || values.size() > 50) throw AiReviewService.validation("ids");
        List<Long> ids = new ArrayList<>();
        for (JsonNode value : values) {
            if (!value.isIntegralNumber() || !value.canConvertToLong()) throw AiReviewService.validation("ids");
            ids.add(value.longValue());
        }
        return reviews.bulkApprove(ids);
    }

    private JsonNode body(HttpServletRequest request) {
        try {
            JsonNode node = mapper.readerFor(JsonNode.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(request.getInputStream());
            if (node == null || !node.isObject()) throw AiReviewService.validation("body");
            return node;
        } catch (IOException exception) {
            throw AiReviewService.validation("body");
        }
    }

    private static int integer(String value, String field) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) { throw AiReviewService.validation(field); }
    }

    private static Instant instant(String value, String field) {
        if (value == null) return null;
        try { return Instant.parse(value); }
        catch (DateTimeParseException exception) { throw AiReviewService.validation(field); }
    }

    private static long id(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed > 0) return parsed;
        } catch (NumberFormatException exception) {
            throw AiReviewService.validation("id");
        }
        throw AiReviewService.validation("id");
    }

    @Schema(name = "RejectBody", requiredProperties = "reason")
    public record RejectBody(@Schema(minLength = 1, maxLength = 1000) String reason) { }
    @Schema(name = "BulkApproveBody", requiredProperties = "ids")
    public record BulkApproveBody(@ArraySchema(minItems = 1, maxItems = 50, uniqueItems = true,
            schema = @Schema(type = "integer", format = "int64", minimum = "1")) List<Long> ids) { }
}
