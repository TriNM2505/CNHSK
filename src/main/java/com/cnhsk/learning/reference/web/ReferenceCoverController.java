package com.cnhsk.learning.reference.web;

import com.cnhsk.learning.reference.ReferenceCoverService;
import com.cnhsk.learning.reference.ReferenceCoverSignature;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Signs one book-cover upload for the reference editor; kept apart from the locked reference API paths. */
@RestController
@RequestMapping("/api/admin/reference-covers")
public class ReferenceCoverController {
    private final ReferenceCoverService covers;

    public ReferenceCoverController(ReferenceCoverService covers) {
        this.covers = covers;
    }

    @PostMapping("/signature")
    @Operation(operationId = "signReferenceCoverUpload", tags = "reference-covers")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "Không có quyền hoặc CSRF"),
            @ApiResponse(responseCode = "503", description = "MEDIA_NOT_CONFIGURED")})
    public ReferenceCoverSignature sign(@RequestHeader(name = "X-XSRF-TOKEN", required = false) String xsrfToken) {
        return covers.signUpload();
    }
}