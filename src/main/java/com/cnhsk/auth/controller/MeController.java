package com.cnhsk.auth.controller;

import com.cnhsk.auth.dto.ChangePasswordRequest;
import com.cnhsk.auth.dto.ProfileResponse;
import com.cnhsk.auth.service.ProfileService;
import com.cnhsk.shared.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints scoped to the authenticated auth-module user. */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final ProfileService profileService;

    public MeController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/profile")
    public ProfileResponse getProfile(@AuthenticationPrincipal AuthenticatedUser user) {
        return profileService.getProfile(user.userId());
    }

    @PatchMapping(value = "/profile", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ProfileResponse updateProfile(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody JsonNode patch) {
        return profileService.updateProfile(user.userId(), patch);
    }

    @PostMapping(value = "/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Đổi mật khẩu")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã đổi mật khẩu"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "403", description = "Phiên hiện tại không hợp lệ"),
            @ApiResponse(responseCode = "422", description = "Dữ liệu mật khẩu không hợp lệ")
    })
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ChangePasswordRequest request) {
        profileService.changePassword(user.userId(), user.sessionId(), request);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
