package com.cnhsk.learning.reference.web;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import com.cnhsk.learning.reference.ReferenceQueryService;
import com.cnhsk.learning.reference.ReferenceQueryService.PublicReference;
import com.cnhsk.learning.reference.ReferenceQueryService.PublicReferencePage;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.AuthenticatedUser;
import com.cnhsk.shared.security.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/references")
@SecurityRequirements
public class PublicReferenceController {

    private static final Logger log = LoggerFactory.getLogger(PublicReferenceController.class);
    private static final int GUEST_LIMIT = 120;
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
            .withZone(ZoneOffset.UTC);
    private final ReferenceQueryService service;
    private final RateLimiter counters;
    private final Clock clock;

    public PublicReferenceController(ReferenceQueryService service, RateLimiter counters, Clock clock) {
        this.service = service;
        this.counters = counters;
        this.clock = clock;
    }

    @ModelAttribute
    public void countGuestRequest(HttpServletRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        if (user != null) return;
        var now = clock.instant();
        long retry = Math.max(1, (Duration.between(now, now.truncatedTo(ChronoUnit.MINUTES)
                .plus(1, ChronoUnit.MINUTES)).toMillis() + 999) / 1000);
        long count;
        try {
            count = counters.increment("reference:public:guest:" + request.getRemoteAddr() + ":" + MINUTE.format(now),
                    Duration.ofSeconds(retry));
        } catch (RuntimeException exception) {
            log.warn("Reference public rate limit unavailable; allowing request");
            return;
        }
        if (count > GUEST_LIMIT) {
            throw new ApiException(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS,
                    "Bạn đã xem quá giới hạn. Vui lòng thử lại sau.", Map.of("retryAfterSeconds", retry));
        }
    }

    @GetMapping
    @Operation(operationId = "listPublicReferences", tags = "references-public")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Trang tài nguyên", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PublicReferencePage.class))),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED", content = @Content(mediaType = "application/json", schema = @Schema(type = "object", requiredProperties = {"error_code", "message", "request_id"}), schemaProperties = {
                    @SchemaProperty(name = "error_code", schema = @Schema(type = "string")),
                    @SchemaProperty(name = "message", schema = @Schema(type = "string")),
                    @SchemaProperty(name = "request_id", schema = @Schema(type = "string")),
                    @SchemaProperty(name = "retryAfterSeconds", schema = @Schema(type = "integer")),
                    @SchemaProperty(name = "learnedWords", schema = @Schema(type = "integer")),
                    @SchemaProperty(name = "requiredWords", schema = @Schema(type = "integer"))} )),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED", content = @Content(mediaType = "application/json", schema = @Schema(type = "object", requiredProperties = {"error_code", "message", "request_id"}), schemaProperties = {
                    @SchemaProperty(name = "error_code", schema = @Schema(type = "string")),
                    @SchemaProperty(name = "message", schema = @Schema(type = "string")),
                    @SchemaProperty(name = "request_id", schema = @Schema(type = "string")),
                    @SchemaProperty(name = "retryAfterSeconds", schema = @Schema(type = "integer")),
                    @SchemaProperty(name = "learnedWords", schema = @Schema(type = "integer")),
                    @SchemaProperty(name = "requiredWords", schema = @Schema(type = "integer"))} ))})
    public PublicReferencePage list(
            @Parameter(required = true, schema = @Schema(allowableValues = {"YOUTUBE_CHANNEL", "PODCAST", "BOOK"}))
            @RequestParam(required = false) String kind,
            @Parameter(schema = @Schema(type = "integer", types = {"integer"}, minimum = "1", maximum = "9"))
            @RequestParam(required = false) String hsk,
            @Parameter(schema = @Schema(maxLength = 30)) @RequestParam(required = false) String tag,
            @Parameter(schema = @Schema(type = "integer", minimum = "0", defaultValue = "0"))
            @RequestParam(required = false) String page,
            @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "50", defaultValue = "20"))
            @RequestParam(required = false) String size) {
        return service.list(kind, hsk == null ? null : integer(hsk, 0, "hsk"), tag,
                integer(page, 0, "page"), integer(size, 20, "size"));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getPublicReference", tags = "references-public")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Tài nguyên", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PublicReference.class))),
            @ApiResponse(responseCode = "404", description = "REFERENCE_NOT_FOUND", content = @Content(mediaType = "application/json", schema = @Schema(type = "object", requiredProperties = {"error_code", "message", "request_id"}), schemaProperties = {
                    @SchemaProperty(name = "error_code", schema = @Schema(type = "string")),
                    @SchemaProperty(name = "message", schema = @Schema(type = "string")),
                    @SchemaProperty(name = "request_id", schema = @Schema(type = "string")),
                    @SchemaProperty(name = "retryAfterSeconds", schema = @Schema(type = "integer")),
                    @SchemaProperty(name = "learnedWords", schema = @Schema(type = "integer")),
                    @SchemaProperty(name = "requiredWords", schema = @Schema(type = "integer"))} )),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED", content = @Content(mediaType = "application/json", schema = @Schema(type = "object", requiredProperties = {"error_code", "message", "request_id"}), schemaProperties = {
                    @SchemaProperty(name = "error_code", schema = @Schema(type = "string")),
                    @SchemaProperty(name = "message", schema = @Schema(type = "string")),
                    @SchemaProperty(name = "request_id", schema = @Schema(type = "string")),
                    @SchemaProperty(name = "retryAfterSeconds", schema = @Schema(type = "integer")),
                    @SchemaProperty(name = "learnedWords", schema = @Schema(type = "integer")),
                    @SchemaProperty(name = "requiredWords", schema = @Schema(type = "integer"))} ))})
    public PublicReference detail(
            @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1"))
            @PathVariable String id) {
        return service.detail(id);
    }

    private static int integer(String value, int fallback, String field) {
        if (value == null) return fallback;
        try {
            if (!value.matches("-?[0-9]+")) throw new NumberFormatException();
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw ReferenceQueryService.invalid(field);
        }
    }
}
