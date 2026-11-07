package com.cnhsk.community.post.web;

import java.util.List;
import java.util.Map;

import com.cnhsk.community.post.PostService;
import com.cnhsk.community.post.PostQueryService;
import com.cnhsk.community.reaction.LikeService;
import com.cnhsk.community.common.PostStatus;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.annotation.JsonProperty;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/community/posts")
public class MyPostController {

    private static final String INVALID_REQUEST_MESSAGE = "Dữ liệu yêu cầu không hợp lệ.";

    private final PostService service;
    private final GlobalExceptionHandler errors;
    private final PostQueryService queries;
    private final LikeService likes;

    public MyPostController(PostService service, GlobalExceptionHandler errors, PostQueryService queries,
            LikeService likes) {
        this.service = service;
        this.errors = errors;
        this.queries = queries;
        this.likes = likes;
    }

    @GetMapping(value = "/mine", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "listMyPosts", tags = "posts")
    @SecurityRequirement(name = "cookieAuth")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = OwnPostPage.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Không có quyền",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "422", description = "Dữ liệu không hợp lệ",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public OwnPostPage listMyPosts(
            @Parameter(schema = @Schema(type = "integer", minimum = "0", defaultValue = "0"))
            @RequestParam(defaultValue = "0") int page,
            @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "50", defaultValue = "20"))
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) PostStatus status) {
        return queries.ownList(page, size, status);
    }

    @GetMapping(value = "/mine/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "getMyPost", tags = "posts")
    @SecurityRequirement(name = "cookieAuth")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = OwnPost.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Không có quyền",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public OwnPost getMyPost(@PathVariable long id) {
        return queries.ownDetail(id);
    }

    @GetMapping(value = "/{id}/viewer-state", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "getViewerState", tags = "posts")
    @SecurityRequirement(name = "cookieAuth")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = ViewerState.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Không có quyền",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy bài viết",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public ViewerState getViewerState(@PathVariable long id) {
        return queries.viewerState(id);
    }

    @PostMapping(value = "/{id}/like", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "likePost", tags = "posts", summary = "Thích bài, idempotent")
    @SecurityRequirement(name = "cookieAuth")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = LikeState.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Không có quyền",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy bài viết",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public LikeState likePost(@PathVariable long id) {
        return likes.like(id);
    }

    @DeleteMapping(value = "/{id}/like", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "unlikePost", tags = "posts", summary = "Bỏ thích, idempotent")
    @SecurityRequirement(name = "cookieAuth")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = LikeState.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Không có quyền",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy bài viết",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public LikeState unlikePost(@PathVariable long id) {
        return likes.unlike(id);
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
        if (exception instanceof MethodArgumentTypeMismatchException mismatch) {
            String method = mismatch.getParameter().getExecutable().getName();
            if ("getMyPost".equals(method)) {
                return errors.handleApiException(new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
                        "Bạn không có quyền thực hiện thao tác này.", Map.of()), request);
            }
            if ("getViewerState".equals(method) || "likePost".equals(method) || "unlikePost".equals(method)) {
                return errors.handleApiException(new ApiException(ErrorCode.POST_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Không tìm thấy bài viết.", Map.of()), request);
            }
        }
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
