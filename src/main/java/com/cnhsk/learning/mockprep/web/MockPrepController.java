package com.cnhsk.learning.mockprep.web;

import java.util.List;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.mockprep.MockPaperBuilder;
import com.cnhsk.learning.mockprep.MockPaperBuilder.BuildResult;
import com.cnhsk.learning.mockprep.MockPaperBuilder.Pool;
import com.cnhsk.learning.mockprep.WordImageLibrary;
import com.cnhsk.learning.mockprep.WordImageLibrary.UploadSignature;
import com.cnhsk.learning.mockprep.WordImageLibrary.WordImage;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Content editors prepare the HSK-format mock exams: word pictures, hand-imported questions per part, then three papers per level. */
@RestController
@RequestMapping(value = "/api/admin/mock-prep", produces = MediaType.APPLICATION_JSON_VALUE)
public class MockPrepController {
    private final WordImageLibrary images;
    private final MockPaperBuilder papers;
    private final AuthApi auth;

    public MockPrepController(WordImageLibrary images, MockPaperBuilder papers, AuthApi auth) {
        this.images = images;
        this.papers = papers;
        this.auth = auth;
    }

    @GetMapping("/pool")
    @Operation(operationId = "getMockPool", tags = "mock-prep")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "Không có quyền"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")})
    public Pool pool(@RequestParam int level) {
        return papers.pool(level(level));
    }

    @PostMapping(value = "/papers", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "buildMockPapers", tags = "mock-prep")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Đề đã tạo hoặc giữ nguyên"),
            @ApiResponse(responseCode = "403", description = "Không có quyền hoặc CSRF"),
            @ApiResponse(responseCode = "422", description = "MOCK_BANK_INSUFFICIENT")})
    public BuildResult build(@RequestBody LevelRequest request,
            @RequestHeader(name = "X-XSRF-TOKEN", required = false) String xsrfToken) {
        return papers.build(auth.currentUser().userId(), level(request == null ? 0 : request.level()));
    }

    private static int level(int level) {
        if (level < 1 || level > 3) throw invalid("level");
        return level;
    }

    @GetMapping("/word-images")
    @Operation(operationId = "listWordImages", tags = "mock-prep")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "Không có quyền"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")})
    public List<WordImage> words(@RequestParam int level) {
        return images.words(level(level));
    }

    @PostMapping("/word-images/signature")
    @Operation(operationId = "signWordImageUpload", tags = "mock-prep")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "Không có quyền hoặc CSRF"),
            @ApiResponse(responseCode = "503", description = "MEDIA_NOT_CONFIGURED")})
    public UploadSignature sign(@RequestHeader(name = "X-XSRF-TOKEN", required = false) String xsrfToken) {
        return images.signUpload();
    }

    @PutMapping(value = "/word-images/{lexemeId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "setWordImage", tags = "mock-prep")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "Không có quyền hoặc CSRF"),
            @ApiResponse(responseCode = "404", description = "LEXEME_NOT_FOUND"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")})
    public WordImage attach(@PathVariable long lexemeId, @RequestBody AttachRequest request,
            @RequestHeader(name = "X-XSRF-TOKEN", required = false) String xsrfToken) {
        return images.attach(auth.currentUser().userId(), lexemeId, request.imageUrl(), request.publicId());
    }

    @DeleteMapping("/word-images/{lexemeId}")
    @Operation(operationId = "removeWordImage", tags = "mock-prep")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Đã gỡ ảnh"),
            @ApiResponse(responseCode = "403", description = "Không có quyền hoặc CSRF"),
            @ApiResponse(responseCode = "404", description = "LEXEME_NOT_FOUND")})
    public ResponseEntity<Void> detach(@PathVariable long lexemeId,
            @RequestHeader(name = "X-XSRF-TOKEN", required = false) String xsrfToken) {
        images.detach(auth.currentUser().userId(), lexemeId);
        return ResponseEntity.noContent().build();
    }

    private static ApiException invalid(String field) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Dữ liệu không hợp lệ.",
                Map.of("details", List.of(Map.of("field", field, "message", "Giá trị không hợp lệ."))));
    }

    @Schema(name = "WordImageAttachRequest", requiredProperties = {"imageUrl", "publicId"})
    public record AttachRequest(String imageUrl, String publicId) {}

    @Schema(name = "MockLevelRequest", requiredProperties = {"level"})
    public record LevelRequest(@Schema(minimum = "1", maximum = "3") int level) {}
}
