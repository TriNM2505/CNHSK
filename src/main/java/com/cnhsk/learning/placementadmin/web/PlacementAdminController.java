package com.cnhsk.learning.placementadmin.web;

import java.util.List;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.aigen.AiRunTrigger;
import com.cnhsk.learning.exam.PlacementFormBuilder;
import com.cnhsk.learning.exam.PlacementFormBuilder.BuildResult;
import com.cnhsk.learning.exam.PlacementFormBuilder.Pool;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Content editors prepare the placement test: AI drafts per level, then five forms from approved drafts. */
@RestController
@RequestMapping(value = "/api/admin/placement", produces = MediaType.APPLICATION_JSON_VALUE)
public class PlacementAdminController {
    private static final int MAX_DRAFTS = 60;

    private final PlacementFormBuilder forms;
    private final AiRunTrigger trigger;
    private final AuthApi auth;
    private final ObjectMapper mapper;

    public PlacementAdminController(PlacementFormBuilder forms, AiRunTrigger trigger, AuthApi auth, ObjectMapper mapper) {
        this.forms = forms;
        this.trigger = trigger;
        this.auth = auth;
        this.mapper = mapper;
    }

    @GetMapping("/pool")
    @Operation(operationId = "getPlacementPool", tags = "placement-admin")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Không có quyền")})
    public Pool pool() {
        return forms.pool();
    }

    @PostMapping("/drafts")
    @Operation(operationId = "draftPlacementQuestions", tags = "placement-admin")
    @ApiResponses({@ApiResponse(responseCode = "202", description = "Đã nhận yêu cầu soạn câu"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Không có quyền hoặc CSRF"),
            @ApiResponse(responseCode = "409", description = "AI_GENERATION_BUSY hoặc AI_GENERATION_DISABLED"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED hoặc PLACEMENT_BANK_INSUFFICIENT")})
    public ResponseEntity<Map<String, Boolean>> draft(@RequestBody(required = false) String body,
            @RequestHeader(name = "X-XSRF-TOKEN", required = false) String xsrfToken) {
        JsonNode request = read(body);
        int level = integer(request, "level", 1, 3);
        int count = integer(request, "count", 1, MAX_DRAFTS);
        trigger.triggerPlacement(level, count);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("started", true));
    }

    @PostMapping("/forms")
    @Operation(operationId = "buildPlacementForms", tags = "placement-admin")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Đề đã tạo hoặc giữ nguyên"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Không có quyền hoặc CSRF"),
            @ApiResponse(responseCode = "422", description = "PLACEMENT_BANK_INSUFFICIENT")})
    public BuildResult build(@RequestHeader(name = "X-XSRF-TOKEN", required = false) String xsrfToken) {
        return forms.build(auth.currentUser().userId());
    }

    private JsonNode read(String body) {
        if (body == null) throw invalid("body");
        try {
            JsonNode node = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(body);
            if (node == null || !node.isObject()) throw invalid("body");
            return node;
        } catch (JsonProcessingException exception) {
            throw invalid("body");
        }
    }

    private static int integer(JsonNode request, String field, int min, int max) {
        JsonNode value = request.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < min || value.intValue() > max) {
            throw invalid(field);
        }
        return value.intValue();
    }

    private static ApiException invalid(String field) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Dữ liệu không hợp lệ.",
                Map.of("details", List.of(Map.of("field", field, "message", "Giá trị không hợp lệ."))));
    }

    @Schema(name = "PlacementDraftRequest", requiredProperties = {"level", "count"})
    record DraftRequest(@Schema(minimum = "1", maximum = "3") int level, @Schema(minimum = "1", maximum = "60") int count) {}
}
