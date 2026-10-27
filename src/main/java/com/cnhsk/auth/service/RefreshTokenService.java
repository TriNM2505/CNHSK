package com.cnhsk.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.cnhsk.auth.dto.LoginResponse;
import com.cnhsk.auth.entity.AuthToken;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Rotates persisted refresh tokens and commits reuse-response side effects before returning an API error. */
@Service
public class RefreshTokenService {

    private static final String INVALID_TOKEN_MESSAGE = "Refresh token is invalid.";
    private static final String REUSE_MESSAGE = "Refresh token reuse was detected.";
    private static final TokenHasher TOKEN_HASHER = new TokenHasher();

    private final AuthTokenRepository authTokenRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final JwtService jwtService;
    private final SecureTokenGenerator tokenGenerator;
    private final CookieFactory cookieFactory;
    private final AuditService auditService;
    private final CnhskProperties.Jwt jwtProperties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public RefreshTokenService(AuthTokenRepository authTokenRepository, UserRepository userRepository,
            UserRoleRepository userRoleRepository, JwtService jwtService, SecureTokenGenerator tokenGenerator,
            CookieFactory cookieFactory, AuditService auditService, CnhskProperties properties, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.authTokenRepository = Objects.requireNonNull(authTokenRepository, "authTokenRepository must not be null.");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null.");
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository, "userRoleRepository must not be null.");
        this.jwtService = Objects.requireNonNull(jwtService, "jwtService must not be null.");
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator, "tokenGenerator must not be null.");
        this.cookieFactory = Objects.requireNonNull(cookieFactory, "cookieFactory must not be null.");
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null.");
        this.jwtProperties = Objects.requireNonNull(properties, "properties must not be null.").getJwt();
        this.clock = Objects.requireNonNull(clock, "clock must not be null.");
        this.transactionTemplate = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager must not be null."));
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public RefreshResult refresh(String rawRefreshToken, String clientTypeHeader) {
        String clientType = "MOBILE".equals(clientTypeHeader) ? "MOBILE" : "WEB";
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw invalidToken();
        }

        RefreshOutcome outcome = transactionTemplate.execute(status -> rotateInTransaction(
                TOKEN_HASHER.hash(rawRefreshToken), clientType));
        if (outcome == null) {
            throw new IllegalStateException("Refresh transaction returned no outcome.");
        }
        if (outcome.reuseDetected()) {
            throw new ApiException(ErrorCode.TOKEN_REUSE_DETECTED, HttpStatus.UNAUTHORIZED,
                    REUSE_MESSAGE, Map.of());
        }
        if (outcome.result() == null) {
            throw invalidToken();
        }
        return outcome.result();
    }

    private RefreshOutcome rotateInTransaction(String tokenHash, String clientType) {
        Long userId = authTokenRepository.findUserIdByTokenHashAndTokenType(tokenHash, TokenType.REFRESH)
                .orElse(null);
        if (userId == null) {
            return RefreshOutcome.invalid();
        }
        User user = userRepository.findByIdForUpdate(userId).orElse(null);
        if (user == null) {
            return RefreshOutcome.invalid();
        }
        AuthToken current = authTokenRepository.findByTokenHashForUpdate(tokenHash, TokenType.REFRESH)
                .orElse(null);
        if (current == null || !userId.equals(current.getUserId())) {
            return RefreshOutcome.invalid();
        }

        Instant now = clock.instant();
        if (current.getRevokedAt() != null) {
            if (current.getSessionId() == null || !authTokenRepository.existsNewerRefreshInSession(
                    current.getUserId(), current.getSessionId(), current.getId())) {
                return RefreshOutcome.invalid();
            }
            authTokenRepository.revokeAllActiveByUserIdAndTokenType(
                    current.getUserId(), TokenType.REFRESH, now);
            auditService.record(current.getUserId(), "TOKEN_REUSE",
                    "user:" + current.getUserId(), null, null);
            return RefreshOutcome.reuse();
        }
        if (!current.getExpiresAt().isAfter(now)) {
            return RefreshOutcome.invalid();
        }

        List<com.cnhsk.auth.entity.RoleCode> roles = userRoleRepository
                .findAllByUserIdOrderByRoleCodeAsc(user.getId()).stream()
                .map(UserRole::getRoleCode)
                .toList();
        String accessToken = jwtService.issueAccessToken(
                user.getId(), roles.stream().map(Enum::name).toList(), user.getEmailVerifiedAt() != null,
                current.getSessionId());
        String nextRefreshToken = tokenGenerator.generate();

        current.setRevokedAt(now);
        authTokenRepository.save(current);
        authTokenRepository.saveAndFlush(new AuthToken(user.getId(), TokenType.REFRESH,
                TOKEN_HASHER.hash(nextRefreshToken), now.plus(jwtProperties.getRefreshTokenTtl()),
                current.getSessionId()));

        LoginResponse.CurrentUser responseUser = new LoginResponse.CurrentUser(
                user.getId(), user.getEmail(), user.getDisplayName(), user.getEmailVerifiedAt() != null,
                roles, user.getHskLevel(), user.getAvatarUrl());
        if ("MOBILE".equals(clientType)) {
            LoginResponse response = new LoginResponse(responseUser, accessToken, nextRefreshToken,
                    jwtProperties.getAccessTokenTtl().toSeconds());
            return RefreshOutcome.success(new RefreshResult(response, List.of()));
        }

        LoginResponse response = new LoginResponse(responseUser, null, null, null);
        List<ResponseCookie> cookies = List.of(cookieFactory.createAccessTokenCookie(accessToken),
                cookieFactory.createRefreshTokenCookie(nextRefreshToken));
        return RefreshOutcome.success(new RefreshResult(response, cookies));
    }

    private static ApiException invalidToken() {
        return new ApiException(ErrorCode.INVALID_REFRESH_TOKEN, HttpStatus.UNAUTHORIZED,
                INVALID_TOKEN_MESSAGE, Map.of());
    }

    public record RefreshResult(LoginResponse response, List<ResponseCookie> cookies) {
        public RefreshResult {
            Objects.requireNonNull(response, "response must not be null.");
            cookies = List.copyOf(cookies);
        }
    }

    private record RefreshOutcome(RefreshResult result, boolean reuseDetected) {
        private static RefreshOutcome invalid() {
            return new RefreshOutcome(null, false);
        }

        private static RefreshOutcome reuse() {
            return new RefreshOutcome(null, true);
        }

        private static RefreshOutcome success(RefreshResult result) {
            return new RefreshOutcome(result, false);
        }
    }
}
