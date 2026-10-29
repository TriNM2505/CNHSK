package com.cnhsk.learning.practice.web;

import java.util.List;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeService.SessionView;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/practice/sessions")
public class PracticeController {

    private final PracticeService practice;
    private final AuthApi auth;

    public PracticeController(PracticeService practice, AuthApi auth) {
        this.practice = practice;
        this.auth = auth;
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getSession", tags = "practice")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lượt luyện"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập"),
            @ApiResponse(responseCode = "422", description = "Dữ liệu không hợp lệ")
    })
    public SessionView view(@Parameter(schema = @Schema(type = "integer", format = "int64")) @PathVariable String id) {
        return practice.view(auth.currentUser().userId(), identifier(id, "id"));
    }

    @GetMapping("/active")
    @Operation(operationId = "findActiveSession", tags = "practice")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lượt luyện đang làm"),
            @ApiResponse(responseCode = "204", description = "Không có lượt đang làm"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Chỉ LEARNER được truy cập"),
            @ApiResponse(responseCode = "422", description = "Dữ liệu không hợp lệ")
    })
    public ResponseEntity<SessionView> active(
            @Parameter(required = true, schema = @Schema(implementation = PracticeSource.class))
            @RequestParam(required = false) String source,
            @Parameter(schema = @Schema(type = "integer", format = "int64"))
            @RequestParam(required = false) String refId) {
        PracticeSource parsed;
        try {
            parsed = PracticeSource.valueOf(source == null ? "" : source);
        } catch (IllegalArgumentException exception) {
            throw validation("source");
        }
        SessionView view = practice.findActive(auth.currentUser().userId(), parsed,
                refId == null ? null : identifier(refId, "refId"));
        return view == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(view);
    }

    private static long identifier(String value, String field) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw validation(field);
        }
    }

    private static ApiException validation(String field) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Dữ liệu không hợp lệ.", Map.of("details", List.of(Map.of("field", field,
                        "message", "Giá trị không hợp lệ."))));
    }
}
