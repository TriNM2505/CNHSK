package com.cnhsk.community.moderation.web;

import java.util.List;
import java.util.Map;
import com.cnhsk.community.moderation.PostModerationService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
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
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping(value = "/api/community/moderation/posts", produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = "cookieAuth")
@SecurityRequirement(name = "bearerAuth")
public class ModerationController {
    private final PostModerationService service;
    private final GlobalExceptionHandler errors;

    public ModerationController(PostModerationService service, GlobalExceptionHandler errors) {
        this.service = service;
        this.errors = errors;
    }

    @GetMapping
    @Operation(operationId = "listPendingPosts", tags = "moderation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = ModerationPostPage.class))),
            @ApiResponse(responseCode = "401", description = "Error",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Error",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "422", description = "Error",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public ModerationPostPage listPendingPosts(
            @Parameter(schema = @Schema(type = "integer", minimum = "0", defaultValue = "0")) @RequestParam(defaultValue = "0") int page,
            @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "50", defaultValue = "20")) @RequestParam(defaultValue = "20") int size) {
        return service.listPendingPosts(page, size);
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getModerationPost", tags = "moderation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = ModerationPost.class))),
            @ApiResponse(responseCode = "401", description = "Error",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Error",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "404", description = "Error",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public ModerationPost getModerationPost(
            @Parameter(schema = @Schema(type = "integer", format = "int64")) @PathVariable String id) {
        return service.getModerationPost(postId(id));
    }

    @PatchMapping("/{id}/approve")
    @Operation(operationId = "approvePost", tags = "moderation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = ModerationPost.class))),
            @ApiResponse(responseCode = "401", description = "Error",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Error",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "404", description = "Error",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "409", description = "Error",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public ModerationPost approvePost(
            @Parameter(schema = @Schema(type = "integer", format = "int64")) @PathVariable String id) {
        return service.approvePost(postId(id));
    }

    @PatchMapping(value = "/{id}/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "rejectPost", tags = "moderation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = ModerationPost.class))),
            @ApiResponse(responseCode = "401", description = "Error",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Error",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "404", description = "Error",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "409", description = "Error",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "422", description = "Error",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public ModerationPost rejectPost(
            @Parameter(schema = @Schema(type = "integer", format = "int64")) @PathVariable String id,
            @Valid @RequestBody RejectWrite request) {
        return service.rejectPost(postId(id), request);
    }

    @Hidden
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> invalidRequest(Exception exception, HttpServletRequest request) {
        String message = "Dữ liệu yêu cầu không hợp lệ.";
        return errors.handleApiException(new ApiException(ErrorCode.VALIDATION_FAILED,
                HttpStatus.UNPROCESSABLE_ENTITY, message,
                Map.of("details", List.of(Map.of("field", "request", "message", message)))), request);
    }

    private static long postId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new ApiException(ErrorCode.POST_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Không tìm thấy bài viết.", Map.of());
        }
    }
}
