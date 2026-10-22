package com.cnhsk.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.cnhsk.auth.dto.ForgotPasswordResponse;
import com.cnhsk.auth.dto.ResetPasswordRequest;
import com.cnhsk.auth.entity.AuthToken;
import com.cnhsk.auth.entity.TokenType;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.event.PasswordResetRequestedEvent;
import com.cnhsk.auth.repository.AuthTokenRepository;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.RateLimiter;
import com.cnhsk.shared.security.SecureTokenGenerator;
import com.cnhsk.shared.security.SessionInvalidationService;
import com.cnhsk.shared.security.TokenHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Implements privacy-preserving password-reset request and transactional token consumption. */
@Service
public class PasswordResetService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration FORGOT_WINDOW = Duration.ofHours(1);
    private static final long EMAIL_LIMIT = 3;
    private static final long IP_LIMIT = 10;
    private static final Duration RESET_TOKEN_TTL = Duration.ofHours(1);
    private static final String FORGOT_MESSAGE =
            "Nếu email có tài khoản trong hệ thống, hướng dẫn đặt lại mật khẩu sẽ được gửi.";
    private static final String INVALID_RESET_MESSAGE = "Token đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.";
    private static final String PASSWORD_UNCHANGED_MESSAGE = "Mật khẩu mới phải khác mật khẩu hiện tại.";
    private static final String PASSWORD_EMAIL_MESSAGE = "Mật khẩu không được trùng email.";
    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(12);
    private static final TokenHasher TOKEN_HASHER = new TokenHasher();

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final RateLimiter rateLimiter;
    private final LoginAttemptService loginAttemptService;
    private final SessionInvalidationService invalidationService;
    private final SecureTokenGenerator tokenGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public PasswordResetService(UserRepository userRepository, AuthTokenRepository authTokenRepository,
            RateLimiter rateLimiter, LoginAttemptService loginAttemptService,
            SessionInvalidationService invalidationService, SecureTokenGenerator tokenGenerator,
            ApplicationEventPublisher eventPublisher, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null.");
        this.authTokenRepository = Objects.requireNonNull(authTokenRepository,
                "authTokenRepository must not be null.");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter must not be null.");
        this.loginAttemptService = Objects.requireNonNull(loginAttemptService,
                "loginAttemptService must not be null.");
        this.invalidationService = Objects.requireNonNull(invalidationService,
                "invalidationService must not be null.");
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator, "tokenGenerator must not be null.");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null.");
        this.clock = Objects.requireNonNull(clock, "clock must not be null.");
        this.transactionTemplate = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager must not be null."));
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public ForgotPasswordResponse requestReset(String email, String remoteAddress) {
        String normalizedEmail = normalizeEmail(email);
        if (!withinForgotLimits(normalizedEmail, remoteAddress)) {
            return new ForgotPasswordResponse(FORGOT_MESSAGE);
        }

        transactionTemplate.executeWithoutResult(status -> {
            User user = userRepository.findByEmailForUpdate(normalizedEmail).orElse(null);
            if (user == null || user.getBannedAt() != null) {
                return;
            }

            Instant now = clock.instant();
            List<AuthToken> oldTokens = authTokenRepository.findActiveByUserIdAndTokenTypeForUpdate(
                    user.getId(), TokenType.RESET_PASSWORD);
            oldTokens.forEach(token -> token.setRevokedAt(now));
            authTokenRepository.saveAll(oldTokens);

            String rawToken = tokenGenerator.generate();
            authTokenRepository.saveAndFlush(new AuthToken(user.getId(), TokenType.RESET_PASSWORD,
                    TOKEN_HASHER.hash(rawToken), now.plus(RESET_TOKEN_TTL)));
            eventPublisher.publishEvent(new PasswordResetRequestedEvent(user.getEmail(), rawToken));
        });
        return new ForgotPasswordResponse(FORGOT_MESSAGE);
    }

    public void resetPassword(ResetPasswordRequest request) {
        String tokenHash = TOKEN_HASHER.hash(request.token());
        ResetOutcome outcome = transactionTemplate.execute(status -> resetInTransaction(tokenHash, request));
        if (outcome == null) {
            throw new IllegalStateException("Password reset transaction returned no outcome.");
        }
        if (!outcome.valid()) {
            throw invalidResetToken();
        }

        invalidationService.markRevoked(outcome.userId(), clock.instant().toEpochMilli() + 1);
        loginAttemptService.clearAllAfterPasswordReset(outcome.email());
    }

    private ResetOutcome resetInTransaction(String tokenHash, ResetPasswordRequest request) {
        Long userId = authTokenRepository.findUserIdByTokenHashAndTokenType(tokenHash, TokenType.RESET_PASSWORD)
                .orElse(null);
        if (userId == null) {
            return ResetOutcome.invalid();
        }

        User user = userRepository.findByIdForUpdate(userId).orElse(null);
        if (user == null) {
            return ResetOutcome.invalid();
        }
        AuthToken resetToken = authTokenRepository
                .findByTokenHashForUpdate(tokenHash, TokenType.RESET_PASSWORD).orElse(null);
        Instant now = clock.instant();
        if (resetToken == null || !userId.equals(resetToken.getUserId())
                || resetToken.getRevokedAt() != null || !resetToken.getExpiresAt().isAfter(now)) {
            return ResetOutcome.invalid();
        }
        if (request.newPassword().equalsIgnoreCase(user.getEmail())) {
            throw validationFailure("newPassword", PASSWORD_EMAIL_MESSAGE);
        }
        if (PASSWORD_ENCODER.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.PASSWORD_UNCHANGED, HttpStatus.UNPROCESSABLE_ENTITY,
                    PASSWORD_UNCHANGED_MESSAGE, Map.of());
        }

        user.setPasswordHash(PASSWORD_ENCODER.encode(request.newPassword()));
        userRepository.save(user);
        resetToken.setRevokedAt(now);
        authTokenRepository.save(resetToken);

        List<AuthToken> refreshTokens = authTokenRepository.findActiveByUserIdAndTokenTypeForUpdate(
                userId, TokenType.REFRESH);
        refreshTokens.forEach(token -> token.setRevokedAt(now));
        authTokenRepository.saveAll(refreshTokens);
        authTokenRepository.flush();
        return ResetOutcome.success(userId, user.getEmail());
    }

    private boolean withinForgotLimits(String email, String remoteAddress) {
        String emailHash = TOKEN_HASHER.hash(email);
        if (remoteAddress == null || remoteAddress.isBlank()) {
            LOGGER.warn("Forgot-password rate limit unavailable; failing closed, cause_type=MissingClientAddress");
            return false;
        }
        try {
            long emailCount = rateLimiter.increment("auth:forgot:email:" + emailHash, FORGOT_WINDOW);
            long ipCount = rateLimiter.increment("auth:forgot:ip:" + remoteAddress, FORGOT_WINDOW);
            return emailCount <= EMAIL_LIMIT && ipCount <= IP_LIMIT;
        } catch (DataAccessException exception) {
            LOGGER.warn("Forgot-password rate limit unavailable; failing closed, cause_type={}",
                    exception.getClass().getSimpleName());
            return false;
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Validated email must not be blank.");
        }
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private ApiException validationFailure(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Dữ liệu không hợp lệ.", Map.of("details", List.of(Map.of("field", field, "message", message))));
    }

    private ApiException invalidResetToken() {
        return new ApiException(ErrorCode.INVALID_RESET_TOKEN, HttpStatus.BAD_REQUEST, INVALID_RESET_MESSAGE, Map.of());
    }

    private record ResetOutcome(boolean valid, long userId, String email) {
        private static ResetOutcome invalid() {
            return new ResetOutcome(false, 0L, null);
        }

        private static ResetOutcome success(long userId, String email) {
            return new ResetOutcome(true, userId, email);
        }
    }
}
