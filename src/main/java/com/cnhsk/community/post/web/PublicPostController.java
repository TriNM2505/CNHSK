package com.cnhsk.community.post.web;

import java.util.List;
import java.util.Map;

import com.cnhsk.community.common.PostCategory;
import com.cnhsk.community.post.PostQueryService;
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
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping(value = "/api/public/community/posts", produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirements
public class PublicPostController {
    private final PostQueryService service;
    private final GlobalExceptionHandler errors;

    public PublicPostController(PostQueryService service, GlobalExceptionHandler errors) {
        this.service = service;
        this.errors = errors;
    }

    @GetMapping
    @Operation(operationId = "listPublishedPosts", tags = "public-posts")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = PostSummaryPage.class))),
            @ApiResponse(responseCode = "422", description = "Dữ liệu không hợp lệ",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public PostSummaryPage list(
            @Parameter(schema = @Schema(minimum = "0", defaultValue = "0"))
            @RequestParam(defaultValue = "0") int page,
            @Parameter(schema = @Schema(minimum = "1", maximum = "50", defaultValue = "20"))
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) PostCategory category) {
        return service.publishedList(page, size, category);
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getPublishedPost", tags = "public-posts")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = PostDetail.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy bài viết",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public PostDetail detail(@PathVariable long id) {
        return service.publishedDetail(id);
    }

    @Hidden
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> invalidParameter(MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        if ("id".equals(exception.getName())) {
            return errors.handleApiException(new ApiException(ErrorCode.POST_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Không tìm thấy bài viết.", Map.of()), request);
        }
        String message = "Tham số yêu cầu không hợp lệ.";
        return errors.handleApiException(new ApiException(ErrorCode.VALIDATION_FAILED,
                HttpStatus.UNPROCESSABLE_ENTITY, message,
                Map.of("details", List.of(Map.of("field", exception.getName(), "message", message)))), request);
    }
}
