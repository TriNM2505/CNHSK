package com.cnhsk.auth.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import com.cnhsk.auth.dto.LoginRequest;
import com.cnhsk.auth.dto.LoginResponse;
import com.cnhsk.auth.entity.AuthToken;
import com.cnhsk.auth.entity.RoleCode;
import com.cnhsk.auth.entity.TokenType;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.entity.UserRole;
import com.cnhsk.auth.repository.AuthTokenRepository;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.auth.repository.UserRoleRepository;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.config.CnhskProperties;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.security.SecureTokenGenerator;
import com.cnhsk.shared.security.TokenHasher;
import com.cnhsk.shared.util.Masking;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates credential verification and token issuance for all supported clients. */
@Service
public class LoginService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Email hoặc mật khẩu không đúng";
    private static final String BANNED_MESSAGE = "Tài khoản đã bị cấm.";
    private static final String SUSPENDED_MESSAGE = "Tài khoản đang tạm khóa.";
    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(12);
    private static final String DUMMY_PASSWORD_HASH = createDummyPasswordHash();
    private static final TokenHasher TOKEN_HASHER = new TokenHasher();

    private final UserRepository userRepository;
    private final EntityManager entityManager;
    private final UserRoleRepository userRoleRepository;
    private final AuthTokenRepository authTokenRepository;
    private final LoginAttemptService loginAttemptService;
    private final JwtService jwtService;
    private final SecureTokenGenerator tokenGenerator;
    private final CookieFactory cookieFactory;
    private final AuditService auditService;
    private final CnhskProperties.Jwt jwtProperties;
    private final Clock clock;

    public LoginService(UserRepository userRepository, EntityManager entityManager,
            UserRoleRepository userRoleRepository,
            AuthTokenRepository authTokenRepository, LoginAttemptService loginAttemptService,
            JwtService jwtService, SecureTokenGenerator tokenGenerator, CookieFactory cookieFactory,
            AuditService auditService, CnhskProperties properties, Clock clock) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null.");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null.");
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository, "userRoleRepository must not be null.");
        this.authTokenRepository = Objects.requireNonNull(authTokenRepository, "authTokenRepository must not be null.");
        this.loginAttemptService = Objects.requireNonNull(loginAttemptService, "loginAttemptService must not be null.");
        this.jwtService = Objects.requireNonNull(jwtService, "jwtService must not be null.");
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator, "tokenGenerator must not be null.");
        this.cookieFactory = Objects.requireNonNull(cookieFactory, "cookieFactory must not be null.");
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null.");
        this.jwtProperties = Objects.requireNonNull(properties, "properties must not be null.").getJwt();
        this.clock = Objects.requireNonNull(clock, "clock must not be null.");
    }

    @Transactional
    public LoginResult login(LoginRequest request, String clientTypeHeader, String remoteAddress) {
        String email = request.email();
        String clientType = "MOBILE".equals(clientTypeHeader) ? "MOBILE" : "WEB";

        loginAttemptService.ensureNotBlocked(email, remoteAddress);

        User user = userRepository.findByEmail(email).orElse(null);
        String candidateHash = user == null ? DUMMY_PASSWORD_HASH : user.getPasswordHash();
        boolean passwordMatches = PASSWORD_ENCODER.matches(request.password(), candidateHash);
        if (user == null || !passwordMatches) {
            loginAttemptService.recordFailure(email, remoteAddress);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED,
                    INVALID_CREDENTIALS_MESSAGE, Map.of());
        }

        User lockedUser = userRepository.findByIdForUpdate(user.getId()).orElse(null);
        if (lockedUser == null) {
            throw invalidCredentials();
        }
        entityManager.refresh(lockedUser, LockModeType.PESSIMISTIC_WRITE);
        if (!candidateHash.equals(lockedUser.getPasswordHash())) {
            loginAttemptService.recordFailure(email, remoteAddress);
            throw invalidCredentials();
        }
        user = lockedUser;

        if (user.getBannedAt() != null) {
            throw new ApiException(ErrorCode.ACCOUNT_BANNED, HttpStatus.FORBIDDEN,
                    BANNED_MESSAGE, Collections.singletonMap("banReason", user.getBanReason()));
        }
        Instant now = clock.instant();
        if (user.getSuspendedUntil() != null && user.getSuspendedUntil().isAfter(now)) {
            throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED, HttpStatus.FORBIDDEN,
                    SUSPENDED_MESSAGE, Map.of("suspendedUntil", user.getSuspendedUntil()));
        }

        List<RoleCode> roles = userRoleRepository.findAllByUserIdOrderByRoleCodeAsc(user.getId()).stream()
                .map(UserRole::getRoleCode)
                .toList();
        loginAttemptService.clearAfterSuccess(email, remoteAddress);

        UUID sessionId = UUID.randomUUID();
        String refreshToken = tokenGenerator.generate();
        authTokenRepository.saveAndFlush(new AuthToken(user.getId(), TokenType.REFRESH,
                TOKEN_HASHER.hash(refreshToken), now.plus(jwtProperties.getRefreshTokenTtl()), sessionId));
        String accessToken = jwtService.issueAccessToken(
                user.getId(), roles.stream().map(Enum::name).toList(), user.getEmailVerifiedAt() != null, sessionId);

        LoginResponse.CurrentUser responseUser = new LoginResponse.CurrentUser(
                user.getId(), user.getEmail(), user.getDisplayName(), user.getEmailVerifiedAt() != null,
                roles, user.getHskLevel(), user.getAvatarUrl());
        List<ResponseCookie> cookies = List.of();
        LoginResponse response;
        if ("MOBILE".equals(clientType)) {
            response = new LoginResponse(responseUser, accessToken, refreshToken,
                    jwtProperties.getAccessTokenTtl().toSeconds());
        } else {
            String xsrfToken = tokenGenerator.generate();
            cookies = List.of(cookieFactory.createAccessTokenCookie(accessToken),
                    cookieFactory.createRefreshTokenCookie(refreshToken),
                    cookieFactory.createXsrfTokenCookie(xsrfToken));
            response = new LoginResponse(responseUser, null, null, null);
        }

        auditService.record(user.getId(), "LOGIN_SUCCESS", "user:" + user.getId(), null,
                Map.of("clientType", clientType, "ip", Masking.ip(remoteAddress)));
        return new LoginResult(response, cookies);
    }

    private ApiException invalidCredentials() {
        return new ApiException(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED,
                INVALID_CREDENTIALS_MESSAGE, Map.of());
    }

    private static String createDummyPasswordHash() {
        byte[] randomPassword = new byte[32];
        new SecureRandom().nextBytes(randomPassword);
        String generatedPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(randomPassword);
        return PASSWORD_ENCODER.encode(generatedPassword);
    }

    public record LoginResult(LoginResponse response, List<ResponseCookie> cookies) {
        public LoginResult {
            cookies = List.copyOf(cookies);
        }
    }
}
