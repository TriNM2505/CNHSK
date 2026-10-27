package com.cnhsk.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import com.cnhsk.auth.entity.AuthToken;
import com.cnhsk.auth.entity.TokenType;
import com.cnhsk.auth.repository.AuthTokenRepository;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.shared.security.SessionInvalidationService;
import com.cnhsk.shared.security.TokenHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Coordinates single-session and all-session logout across PostgreSQL and Redis. */
@Service
public class LogoutService {

    private static final TokenHasher TOKEN_HASHER = new TokenHasher();

    private final AuthTokenRepository authTokenRepository;
    private final UserRepository userRepository;
    private final SessionInvalidationService invalidationService;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public LogoutService(AuthTokenRepository authTokenRepository, UserRepository userRepository,
            SessionInvalidationService invalidationService, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.authTokenRepository = Objects.requireNonNull(authTokenRepository,
                "authTokenRepository must not be null.");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null.");
        this.invalidationService = Objects.requireNonNull(invalidationService,
                "invalidationService must not be null.");
        this.clock = Objects.requireNonNull(clock, "clock must not be null.");
        this.transactionTemplate = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager must not be null."));
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** Revokes only a matching active refresh token owned by the authenticated user. */
    public void logout(long userId, String rawRefreshToken) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive.");
        }
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        String tokenHash = TOKEN_HASHER.hash(rawRefreshToken);
        Long tokenUserId = authTokenRepository
                .findUserIdByTokenHashAndTokenType(tokenHash, TokenType.REFRESH)
                .orElse(null);
        if (!Long.valueOf(userId).equals(tokenUserId)) {
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            userRepository.findByIdForUpdate(userId);
            AuthToken token = authTokenRepository.findByTokenHashForUpdate(tokenHash, TokenType.REFRESH)
                    .orElse(null);
            if (token != null && Long.valueOf(userId).equals(token.getUserId())
                    && token.getRevokedAt() == null) {
                token.setRevokedAt(clock.instant());
                authTokenRepository.save(token);
            }
        });
    }

    /** Commits database revocation before writing the shared access-token invalidation marker. */
    public void logoutAll(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive.");
        }

        transactionTemplate.executeWithoutResult(status -> {
            userRepository.findByIdForUpdate(userId);
            authTokenRepository.findActiveByUserIdAndTokenTypeForUpdate(userId, TokenType.REFRESH);
            authTokenRepository.revokeAllActiveByUserIdAndTokenType(
                    userId, TokenType.REFRESH, clock.instant());
        });

        invalidationService.markRevoked(userId, clock.instant().toEpochMilli() + 1);
    }
}
