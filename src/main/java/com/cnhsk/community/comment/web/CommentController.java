package com.cnhsk.community.comment.web;

import java.util.List;
import java.util.Map;

import com.cnhsk.community.comment.CommentService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

@RestController
@RequestMapping("/api/community")
@SecurityRequirement(name = "cookieAuth")
@SecurityRequirement(name = "bearerAuth")
public class CommentController {

    private static final String INVALID_REQUEST_MESSAGE = "Dữ liệu yêu cầu không hợp lệ.";

    private final CommentService service;
    private final GlobalExceptionHandler errors;

    public CommentController(CommentService service, GlobalExceptionHandler errors) {
        this.service = service;
        this.errors = errors;
    }

    @PostMapping(value = "/posts/{id}/comments", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createComment", tags = "comments")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created",
                    content = @Content(schema = @Schema(implementation = CommentResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Không có quyền hoặc chưa xác minh email",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy bài viết",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "422", description = "Dữ liệu không hợp lệ",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "429", description = "Vượt giới hạn bình luận",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public CommentResponse createComment(@PathVariable("id") long postId, @Valid @RequestBody CommentWrite request) {
        return service.create(postId, request);
    }

    @PostMapping(value = "/comments/{id}/replies", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "replyToComment", tags = "comments")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created",
                    content = @Content(schema = @Schema(implementation = CommentResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Không có quyền hoặc chưa xác minh email",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy bình luận",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "422", description = "Dữ liệu không hợp lệ",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "429", description = "Vượt giới hạn bình luận",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public CommentResponse replyToComment(@PathVariable("id") long parentId, @Valid @RequestBody CommentWrite request) {
        return service.reply(parentId, request);
    }

    @Hidden
    @ModelAttribute
    public void authorizeMutation(HttpServletRequest request) {
        Object selected = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (selected instanceof HandlerMethod handler
                && (handler.hasMethodAnnotation(PutMapping.class) || handler.hasMethodAnnotation(DeleteMapping.class))) {
            service.authorizeMutation();
        }
    }

    @PutMapping(value = "/comments/{id}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateComment", tags = "comments")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = CommentResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Không có quyền hoặc chưa xác minh email",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "409", description = "Bình luận không thể sửa hoặc đã hết thời hạn",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "422", description = "Dữ liệu không hợp lệ",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public CommentResponse updateComment(
            @Parameter(required = true, schema = @Schema(type = "integer", format = "int64"))
            @PathVariable("id") String id, @Valid @RequestBody CommentWrite request) {
        // Keep path parsing after body validation, including when both inputs are invalid.
        return service.update(commentId(id), request);
    }

    @DeleteMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteComment", tags = "comments")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã xoá hoặc đã ở trạng thái xoá/ẩn", content = @Content),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Không có quyền hoặc chưa xác minh email",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public void deleteComment(
            @Parameter(required = true, schema = @Schema(type = "integer", format = "int64"))
            @PathVariable("id") String id) {
        service.delete(commentId(id));
    }

    private static long commentId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException invalid) {
            throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "Bạn không có quyền thực hiện thao tác này.", Map.of());
        }
    }

    @Hidden
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> invalidRequest(Exception exception, HttpServletRequest request) {
        return errors.handleApiException(new ApiException(ErrorCode.VALIDATION_FAILED,
                HttpStatus.UNPROCESSABLE_ENTITY, INVALID_REQUEST_MESSAGE,
                Map.of("details", List.of(Map.of("field", "request", "message", INVALID_REQUEST_MESSAGE)))), request);
    }
}
