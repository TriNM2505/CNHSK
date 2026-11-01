package com.cnhsk.community.post.web;

import java.util.List;
import java.util.Map;

import com.cnhsk.community.post.PostService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/community/posts")
public class MyPostController {

    private static final String INVALID_REQUEST_MESSAGE = "Dữ liệu yêu cầu không hợp lệ.";

    private final PostService service;
    private final GlobalExceptionHandler errors;

    public MyPostController(PostService service, GlobalExceptionHandler errors) {
        this.service = service;
        this.errors = errors;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createPost", tags = "posts", summary = "Tạo bài DRAFT hoặc PENDING")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = OwnPost.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền hoặc chưa xác minh email",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Dữ liệu không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Đã đạt giới hạn tạo bài trong ngày",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public OwnPost create(@Valid @RequestBody PostWrite request) {
        return service.create(request);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updatePost", tags = "posts", summary = "Tác giả sửa bài viết")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = OwnPost.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền hoặc chưa xác minh email",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Bài viết không thể chỉnh sửa",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Dữ liệu không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public OwnPost update(@PathVariable long id, @Valid @RequestBody PostWrite request) {
        return service.update(id, request);
    }

    @Hidden
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> invalidRequest(Exception exception, HttpServletRequest request) {
        ApiException validation = new ApiException(ErrorCode.VALIDATION_FAILED,
                HttpStatus.UNPROCESSABLE_ENTITY, INVALID_REQUEST_MESSAGE,
                Map.of("details", List.of(Map.of("field", "request", "message", INVALID_REQUEST_MESSAGE))));
        return errors.handleApiException(validation, request);
    }

    @Schema(name = "ErrorResponse")
    private record ErrorResponse(
            @JsonProperty("error_code") @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String errorCode,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,
            @JsonProperty("request_id") @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String requestId,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long retryAfterSeconds,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) List<ErrorDetail> details) {
    }

    private record ErrorDetail(
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String field,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String message) {
    }
}
