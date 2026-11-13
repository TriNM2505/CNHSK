package com.cnhsk.learning.reference.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.reference.ReferenceAdminService;
import com.cnhsk.learning.reference.ReferenceAdminService.Action;
import com.cnhsk.learning.reference.ReferenceAdminService.AdminReference;
import com.cnhsk.learning.reference.ReferenceAdminService.AdminReferencePage;
import com.cnhsk.learning.reference.ReferenceAdminService.ReferenceInput;
import com.cnhsk.learning.reference.ReferenceItem.Kind;
import com.cnhsk.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/references")
public class AdminReferenceController {

    private final ReferenceAdminService service;
    private final ObjectReader reader;
    private final GlobalExceptionHandler errors;

    public AdminReferenceController(ReferenceAdminService service, ObjectMapper mapper, GlobalExceptionHandler errors) {
        this.service = service;
        this.reader = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.errors = errors;
    }

    @GetMapping
    @Operation(operationId = "listAdminReferences", tags = "references-admin")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Trang quản trị"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")})
    public AdminReferencePage list(
            @Parameter(schema = @Schema(allowableValues = {"YOUTUBE_CHANNEL", "PODCAST", "BOOK"}))
            @RequestParam(required = false) String kind,
            @Parameter(schema = @Schema(allowableValues = {"DRAFT", "PUBLISHED", "HIDDEN"}))
            @RequestParam(required = false) String status,
            @Parameter(schema = @Schema(type = "boolean")) @RequestParam(required = false) String reported,
            @Parameter(schema = @Schema(type = "integer", minimum = "0", defaultValue = "0"))
            @RequestParam(required = false) String page,
            @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "50", defaultValue = "20"))
            @RequestParam(required = false) String size) {
        Boolean filter = null;
        if (reported != null) {
            if (!reported.equals("true") && !reported.equals("false")) throw ReferenceAdminService.invalid("reported");
            filter = Boolean.valueOf(reported);
        }
        return service.list(kind, status, filter, integer(page, 0, "page"), integer(size, 20, "size"));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getAdminReference", tags = "references-admin")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Tài nguyên quản trị"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
            @ApiResponse(responseCode = "404", description = "REFERENCE_NOT_FOUND")})
    public AdminReference detail(@Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1"))
            @PathVariable String id) {
        return service.detail(id);
    }

    @PostMapping
    @Operation(operationId = "createReference", tags = "references-admin")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Đã tạo DRAFT"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN · CSRF_TOKEN_INVALID"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED · MALFORMED_URL · NON_HTTPS_LINK · DOMAIN_NOT_ALLOWED")})
    public ResponseEntity<AdminReference> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = ReferenceInput.class)))
            @RequestBody(required = false) String body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(input(body)));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateReference", tags = "references-admin")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Đã sửa"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN · CSRF_TOKEN_INVALID"),
            @ApiResponse(responseCode = "404", description = "REFERENCE_NOT_FOUND"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED · PUBLISH_INVALID_RESOURCE · MALFORMED_URL · NON_HTTPS_LINK · DOMAIN_NOT_ALLOWED")})
    public AdminReference update(@Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1"))
            @PathVariable String id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = ReferenceInput.class)))
            @RequestBody(required = false) String body) {
        return service.update(id, input(body));
    }

    @PostMapping("/{id}/publish")
    @Operation(operationId = "publishReference", tags = "references-admin")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Đã công bố"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN · CSRF_TOKEN_INVALID"),
            @ApiResponse(responseCode = "404", description = "REFERENCE_NOT_FOUND"),
            @ApiResponse(responseCode = "422", description = "PUBLISH_INVALID_RESOURCE · MALFORMED_URL · NON_HTTPS_LINK · DOMAIN_NOT_ALLOWED")})
    public AdminReference publish(@Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1"))
            @PathVariable String id) {
        return service.transition(id, Action.PUBLISH);
    }

    @PostMapping("/{id}/hide")
    @Operation(operationId = "hideReference", tags = "references-admin")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Đã ẩn"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN · CSRF_TOKEN_INVALID"),
            @ApiResponse(responseCode = "404", description = "REFERENCE_NOT_FOUND")})
    public AdminReference hide(@Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1"))
            @PathVariable String id) {
        return service.transition(id, Action.HIDE);
    }

    @PostMapping("/{id}/unhide")
    @Operation(operationId = "unhideReference", tags = "references-admin")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Đã hiện lại về DRAFT hoặc giữ nguyên"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN · CSRF_TOKEN_INVALID"),
            @ApiResponse(responseCode = "404", description = "REFERENCE_NOT_FOUND")})
    public AdminReference unhide(@Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1"))
            @PathVariable String id) {
        return service.transition(id, Action.UNHIDE);
    }

    @Hidden
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpServletRequest request) {
        return errors.handleApiException(ReferenceAdminService.invalid("request"), request);
    }

    private ReferenceInput input(String raw) {
        if (raw == null) throw ReferenceAdminService.invalid("request");
        JsonNode body;
        try { body = reader.readTree(raw); }
        catch (JsonProcessingException exception) { throw ReferenceAdminService.invalid("request"); }
        if (body == null || !body.isObject()) throw ReferenceAdminService.invalid("request");
        Kind kind = ReferenceAdminService.enumValue(Kind.class, text(body, "kind", true), "kind");
        List<String> tags = new ArrayList<>();
        if (body.has("tags")) {
            JsonNode array = body.get("tags");
            if (!array.isArray()) throw ReferenceAdminService.invalid("tags");
            for (JsonNode tag : array) {
                if (!tag.isTextual()) throw ReferenceAdminService.invalid("tags");
                tags.add(tag.textValue());
            }
        }
        Integer order = number(body, "sortOrder", false);
        JsonNode affiliate = body.get("isAffiliate");
        if (affiliate != null && !affiliate.isBoolean()) throw ReferenceAdminService.invalid("isAffiliate");
        return new ReferenceInput(kind, text(body, "title", true), text(body, "description", false),
                text(body, "url", true), text(body, "thumbnailUrl", false), number(body, "hskMin", true),
                number(body, "hskMax", true), tags, order == null ? 0 : order, text(body, "author", false),
                text(body, "publisher", false), number(body, "publishedYear", true),
                affiliate != null && affiliate.booleanValue());
    }

    private String text(JsonNode body, String field, boolean required) {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) {
            if (required) throw ReferenceAdminService.invalid(field);
            return null;
        }
        if (!value.isTextual()) throw ReferenceAdminService.invalid(field);
        return value.textValue();
    }

    private Integer number(JsonNode body, String field, boolean nullable) {
        JsonNode value = body.get(field);
        if (value == null || (nullable && value.isNull())) return null;
        if (!value.isIntegralNumber() || !value.canConvertToInt()) throw ReferenceAdminService.invalid(field);
        return value.intValue();
    }

    private int integer(String value, int fallback, String field) {
        if (value == null) return fallback;
        try {
            if (!value.matches("-?[0-9]+")) throw new NumberFormatException();
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) { throw ReferenceAdminService.invalid(field); }
    }
}
