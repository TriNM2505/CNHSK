package com.cnhsk.community.media.web;

import com.cnhsk.community.media.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MediaController {
    private final MediaService media;

    public MediaController(MediaService media) {
        this.media = media;
    }

    @PostMapping(value = "/api/community/media/signature", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "signMediaUpload", tags = "media")
    @SecurityRequirement(name = "cookieAuth")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = UploadSignature.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Không có quyền",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "503", description = "MEDIA_NOT_CONFIGURED",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public UploadSignature signMediaUpload(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String purpose) {
        return media.signUpload("avatar".equals(purpose));
    }
}
