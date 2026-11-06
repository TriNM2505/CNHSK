package com.cnhsk.learning.exam.web;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.exam.TargetedPracticeService;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import com.cnhsk.learning.practice.PracticeService.SessionView;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TargetedPracticeController {
    private static final String KID_PATTERN = "^(NA|CH|TV|NP|NL|VB)[1-7]-[0-9]{3}$";
    private static final Pattern KID = Pattern.compile(KID_PATTERN);

    private final TargetedPracticeService practice;
    private final AuthApi auth;
    private final ObjectMapper mapper;

    public TargetedPracticeController(TargetedPracticeService practice, AuthApi auth, ObjectMapper mapper) {
        this.practice = practice;
        this.auth = auth;
        this.mapper = mapper;
    }

    @PostMapping("/api/targeted-practice/sessions")
    @Operation(operationId = "startTargetedPractice", tags = "targeted-practice",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TargetedPracticeRequest.class))))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Resource not found"),
            @ApiResponse(responseCode = "422", description = "Request cannot be processed"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<?> start(@RequestBody(required = false) String request) {
        JsonNode body = readBody(request);
        JsonNode kid = body == null ? null : body.path("kid");
        if (kid == null || !kid.isTextual() || !KID.matcher(kid.textValue()).matches()) throw validation("kid");
        QuestionType type = switch (body.path("qtype").asText()) {
            case "MULTIPLE_CHOICE" -> QuestionType.MULTIPLE_CHOICE;
            case "TRUE_FALSE" -> QuestionType.TRUE_FALSE;
            default -> {
                if (body.has("qtype")) throw validation("qtype");
                yield null;
            }
        };
        JsonNode exclude = body.path("excludeQuestionId");
        Long excludeId = null;
        if (!exclude.isMissingNode() && !exclude.isNull()) {
            if (!exclude.isIntegralNumber() || !exclude.canConvertToLong()) throw validation("excludeQuestionId");
            excludeId = exclude.longValue();
        }
        SessionView session = practice.start(auth.currentUser().userId(), kid.textValue(), type, excludeId);
        return session == null ? ResponseEntity.ok(new NoQuestions(null, "NO_QUESTIONS"))
                : ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    private JsonNode readBody(String request) {
        if (request == null) throw validation("kid");
        try {
            return mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(request);
        } catch (JsonProcessingException exception) {
            throw validation("body");
        }
    }

    private static ApiException validation(String field) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Dữ liệu không hợp lệ.", Map.of("details", List.of(Map.of("field", field,
                        "message", "Giá trị không hợp lệ."))));
    }

    public record NoQuestions(@JsonInclude(JsonInclude.Include.ALWAYS) SessionView session, String reason) {}

    @Schema(requiredProperties = "kid")
    private record TargetedPracticeRequest(@Schema(pattern = KID_PATTERN) String kid,
            @Schema(allowableValues = {"MULTIPLE_CHOICE", "TRUE_FALSE"}) String qtype,
            @Schema(types = {"integer", "null"}, format = "int64") Long excludeQuestionId) {}
}
