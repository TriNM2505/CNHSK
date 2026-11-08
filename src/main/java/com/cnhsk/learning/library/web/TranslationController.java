package com.cnhsk.learning.library.web;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.library.TranslationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TranslationController {
    private final TranslationService service;
    private final AuthApi auth;
    private final ObjectMapper mapper;

    public TranslationController(TranslationService service, AuthApi auth, ObjectMapper mapper) {
        this.service = service;
        this.auth = auth;
        this.mapper = mapper;
    }

    @PostMapping("/api/translate")
    @Operation(operationId = "translate", tags = "translation")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(schema = @Schema(implementation = TranslateRequest.class)))
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Bản dịch"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "402", description = "INSUFFICIENT_CREDITS"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN hoặc CSRF_TOKEN_INVALID"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED"),
            @ApiResponse(responseCode = "503", description = "TRANSLATE_UNAVAILABLE — lượt đã hoàn")})
    public TranslateResult translate(@RequestBody(required = false) String body) {
        TranslateRequest request = TranslateRequest.validate(body, mapper);
        return service.translate(auth.currentUser().userId(), request);
    }
}
