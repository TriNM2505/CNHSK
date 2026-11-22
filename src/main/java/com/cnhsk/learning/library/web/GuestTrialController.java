package com.cnhsk.learning.library.web;

import com.cnhsk.learning.library.GuestLookupTrial;
import com.cnhsk.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reading the remaining trial is not a lookup, so it stays outside the counted dictionary controller. */
@RestController
public class GuestTrialController {

    private final GuestLookupTrial trial;

    public GuestTrialController(GuestLookupTrial trial) {
        this.trial = trial;
    }

    @GetMapping("/api/public/dictionary/guest-trial")
    @SecurityRequirements
    @Operation(operationId = "getGuestTrial", tags = "dictionary", summary = "Số lượt tra thử còn lại của khách hôm nay")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Hạn mức và số lượt còn lại"),
            @ApiResponse(responseCode = "204", description = "Đã đăng nhập hoặc chưa đọc được số lượt",
                    content = @io.swagger.v3.oas.annotations.media.Content)})
    public ResponseEntity<GuestLookupTrial.Status> status(@AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(hidden = true) @CookieValue(name = GuestLookupTrial.COOKIE, required = false) String guestId) {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok().cacheControl(CacheControl.noStore());
        if (user != null) return ResponseEntity.noContent().build();
        return trial.status(guestId).map(response::body).orElseGet(() -> ResponseEntity.noContent().build());
    }
}
