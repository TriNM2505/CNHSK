package com.cnhsk.auth.controller;

import com.cnhsk.auth.dto.RegisterRequest;
import com.cnhsk.auth.dto.RegisterResponse;
import com.cnhsk.auth.dto.LoginRequest;
import com.cnhsk.auth.dto.LoginResponse;
import com.cnhsk.auth.dto.CurrentUserResponse;
import com.cnhsk.auth.dto.ForgotPasswordRequest;
import com.cnhsk.auth.dto.ForgotPasswordResponse;
import com.cnhsk.auth.dto.ResetPasswordRequest;
import com.cnhsk.auth.service.EmailVerificationService;
import com.cnhsk.auth.service.CurrentUserService;
import com.cnhsk.auth.service.LoginService;
import com.cnhsk.auth.service.LogoutService;
import com.cnhsk.auth.service.RefreshTokenService;
import com.cnhsk.auth.service.RegistrationService;
import com.cnhsk.auth.service.PasswordResetService;
import com.cnhsk.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final EmailVerificationService emailVerificationService;
    private final LoginService loginService;
    private final CurrentUserService currentUserService;
    private final RefreshTokenService refreshTokenService;
    private final LogoutService logoutService;
    private final com.cnhsk.auth.service.CookieFactory cookieFactory;
    private final PasswordResetService passwordResetService;

    public AuthController(RegistrationService registrationService, EmailVerificationService emailVerificationService,
            LoginService loginService, CurrentUserService currentUserService,
            RefreshTokenService refreshTokenService, LogoutService logoutService,
            com.cnhsk.auth.service.CookieFactory cookieFactory, PasswordResetService passwordResetService) {
        this.registrationService = registrationService;
        this.emailVerificationService = emailVerificationService;
        this.loginService = loginService;
        this.currentUserService = currentUserService;
        this.refreshTokenService = refreshTokenService;
        this.logoutService = logoutService;
        this.cookieFactory = cookieFactory;
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/me")
    @Operation(summary = "Người dùng hiện tại")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thông tin người dùng hiện tại"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực")
    })
    public CurrentUserResponse currentUser(@AuthenticationPrincipal AuthenticatedUser user) {
        return currentUserService.getCurrentUser(user.userId());
    }

    @PostMapping("/register")
    @Operation(summary = "Đăng ký tài khoản")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tài khoản đã được tạo"),
            @ApiResponse(responseCode = "409", description = "Email đã được sử dụng"),
            @ApiResponse(responseCode = "422", description = "Dữ liệu đăng ký không hợp lệ")
    })
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registrationService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập ba client")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đăng nhập thành công"),
            @ApiResponse(responseCode = "401", description = "Email hoặc mật khẩu không đúng"),
            @ApiResponse(responseCode = "403", description = "Tài khoản bị cấm hoặc tạm khóa"),
            @ApiResponse(responseCode = "422", description = "Dữ liệu đăng nhập không hợp lệ"),
            @ApiResponse(responseCode = "423", description = "Nguồn đăng nhập đang bị khóa")
    })
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(name = "X-Client-Type", required = false)
            String clientType, HttpServletRequest servletRequest) {
        LoginService.LoginResult result = loginService.login(request, clientType, servletRequest.getRemoteAddr());
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        for (ResponseCookie cookie : result.cookies()) {
            response.header(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        return response.body(result.response());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Làm mới token, xoay vòng")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token đã được làm mới"),
            @ApiResponse(responseCode = "401", description = "Refresh token không hợp lệ hoặc đã bị dùng lại")
    })
    public ResponseEntity<LoginResponse> refresh(
            @RequestHeader(name = "X-Client-Type", required = false) String clientType,
            @CookieValue(name = "refresh_token", required = false) String refreshCookie,
            @RequestBody(required = false) RefreshTokenRequest request) {
        String rawRefreshToken = "MOBILE".equals(clientType)
                ? request == null ? null : request.refreshToken()
                : refreshCookie;
        RefreshTokenService.RefreshResult result = refreshTokenService.refresh(rawRefreshToken, clientType);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        for (ResponseCookie cookie : result.cookies()) {
            response.header(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        return response.body(result.response());
    }

    @PostMapping("/logout")
    @Operation(summary = "ÄÄƒng xuáº¥t phiÃªn hiá»‡n táº¡i")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "ÄÃ£ Ä‘Äƒng xuáº¥t"),
            @ApiResponse(responseCode = "401", description = "ChÆ°a xÃ¡c thá»±c")
    })
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser user,
            @CookieValue(name = "refresh_token", required = false) String refreshCookie,
            @RequestBody(required = false) RefreshTokenRequest request, HttpServletRequest servletRequest) {
        boolean cookieClient = hasCookie(servletRequest, "access_token");
        String rawRefreshToken = cookieClient
                ? refreshCookie
                : request == null ? null : request.refreshToken();
        logoutService.logout(user.userId(), rawRefreshToken);

        ResponseEntity.HeadersBuilder<?> response = ResponseEntity.noContent();
        if (cookieClient) {
            response.header(HttpHeaders.SET_COOKIE, cookieFactory.clearAccessTokenCookie().toString());
            response.header(HttpHeaders.SET_COOKIE, cookieFactory.clearRefreshTokenCookie().toString());
        }
        return response.build();
    }

    @PostMapping("/logout-all")
    @Operation(summary = "ÄÄƒng xuáº¥t má»�i thiáº¿t bá»‹")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "ÄÃ£ Ä‘Äƒng xuáº¥t"),
            @ApiResponse(responseCode = "401", description = "ChÆ°a xÃ¡c thá»±c"),
            @ApiResponse(responseCode = "500", description = "KhÃ´ng thể ghi marker Redis")
    })
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal AuthenticatedUser user) {
        logoutService.logoutAll(user.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Xác thực email")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email đã được xác thực"),
            @ApiResponse(responseCode = "400", description = "Token xác thực không hợp lệ")
    })
    public EmailVerifiedResponse verifyEmail(@RequestBody VerifyEmailRequest request) {
        boolean verified = emailVerificationService.verify(request == null ? null : request.token());
        return new EmailVerifiedResponse(verified);
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Quên mật khẩu — trả thông báo chung")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Đã nhận yêu cầu"),
            @ApiResponse(responseCode = "422", description = "Email không hợp lệ")
    })
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest servletRequest) {
        return ResponseEntity.accepted().body(
                passwordResetService.requestReset(request.email(), servletRequest.getRemoteAddr()));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Đặt lại mật khẩu")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã đặt lại"),
            @ApiResponse(responseCode = "400", description = "Token đặt lại không hợp lệ"),
            @ApiResponse(responseCode = "422", description = "Dữ liệu đặt lại không hợp lệ"),
            @ApiResponse(responseCode = "500", description = "Không thể hoàn tất vô hiệu phiên")
    })
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email/resend")
    @Operation(summary = "Gửi lại email xác thực")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Yêu cầu đã được nhận"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "409", description = "Email đã được xác thực"),
            @ApiResponse(responseCode = "429", description = "Vượt giới hạn gửi lại"),
            @ApiResponse(responseCode = "500", description = "Không thể kiểm tra giới hạn gửi lại")
    })
    public ResponseEntity<Void> resendVerificationEmail(@AuthenticationPrincipal AuthenticatedUser user) {
        emailVerificationService.resend(user.userId());
        return ResponseEntity.accepted().build();
    }

    public record VerifyEmailRequest(String token) {
    }

    public record EmailVerifiedResponse(boolean emailVerified) {
    }

    public record RefreshTokenRequest(String refreshToken) {
    }

    private static boolean hasCookie(HttpServletRequest request, String name) {
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (jakarta.servlet.http.Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return true;
            }
        }
        return false;
    }
}
