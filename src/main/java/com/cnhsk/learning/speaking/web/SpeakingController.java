package com.cnhsk.learning.speaking.web;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.grading.web.GradingRequestViews.Detail;
import com.cnhsk.learning.speaking.SpeakingService;
import com.cnhsk.learning.speaking.web.SpeakingViews.SpeakingItems;
import com.cnhsk.learning.speaking.web.SpeakingViews.SpeakingUploadSignature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SpeakingController {
    private final SpeakingService service;
    private final AuthApi auth;
    private final ObjectMapper mapper;

    public SpeakingController(SpeakingService service, AuthApi auth, ObjectMapper mapper) {
        this.service = service;
        this.auth = auth;
        this.mapper = mapper;
    }

    /** The public path lets visitors hear models and practise; only sending a recording needs an account. */
    @GetMapping({"/api/speaking/items", "/api/public/speaking/items"})
    @Operation(operationId = "listSpeakingItems", tags = "speaking")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Bài đọc to"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")})
    public SpeakingItems items(@RequestParam(defaultValue = "1") int hskLevel,
            @RequestParam(defaultValue = "WORD") String kind, @RequestParam(defaultValue = "5") int count) {
        return service.items(hskLevel, kind, count);
    }

    @PostMapping("/api/speaking/upload-signature")
    @Operation(operationId = "signSpeakingUpload", tags = "speaking")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Chữ ký tải bản ghi âm"),
            @ApiResponse(responseCode = "503", description = "MEDIA_NOT_CONFIGURED")})
    public SpeakingUploadSignature signature() {
        return service.signUpload();
    }

    @PostMapping("/api/grading-requests/speaking")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "submitSpeaking", tags = "speaking")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(schema = @Schema(implementation = SpeakingSubmissionWrite.class)))
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Đã gửi bài nói"),
            @ApiResponse(responseCode = "402", description = "INSUFFICIENT_CREDITS"),
            @ApiResponse(responseCode = "409", description = "GRADING_DUPLICATE_ACTIVE"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED"),
            @ApiResponse(responseCode = "503", description = "MEDIA_NOT_CONFIGURED")})
    public Detail submit(@RequestBody(required = false) String body) {
        return service.submit(auth.currentUser().userId(), SpeakingSubmissionWrite.parse(body, mapper));
    }
}
