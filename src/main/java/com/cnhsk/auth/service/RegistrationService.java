package com.cnhsk.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import com.cnhsk.auth.dto.RegisterRequest;
import com.cnhsk.auth.dto.RegisterResponse;
import com.cnhsk.auth.entity.AuthToken;
import com.cnhsk.auth.entity.RoleCode;
import com.cnhsk.auth.entity.TokenType;
import com.cnhsk.auth.entity.UserRole;
import com.cnhsk.auth.event.EmailVerificationRequestedEvent;
import com.cnhsk.auth.repository.AuthTokenRepository;
import com.cnhsk.auth.repository.UserRoleRepository;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.SecureTokenGenerator;
import com.cnhsk.shared.security.TokenHasher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private static final Duration VERIFY_TOKEN_TTL = Duration.ofHours(24);
    private final JdbcTemplate jdbcTemplate;
    private final UserRoleRepository userRoleRepository;
    private final AuthTokenRepository authTokenRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SecureTokenGenerator tokenGenerator;
    private final TokenHasher tokenHasher = new TokenHasher();
    private final Clock clock;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

    public RegistrationService(JdbcTemplate jdbcTemplate, UserRoleRepository userRoleRepository,
            AuthTokenRepository authTokenRepository, ApplicationEventPublisher eventPublisher,
            SecureTokenGenerator tokenGenerator, Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository);
        this.authTokenRepository = Objects.requireNonNull(authTokenRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        Long userId = jdbcTemplate.query(
                "INSERT INTO auth.users (email, password_hash, display_name) "
                        + "VALUES (?, ?, ?) ON CONFLICT (email) DO NOTHING RETURNING id",
                resultSet -> resultSet.next() ? resultSet.getLong(1) : null,
                request.email(), passwordEncoder.encode(request.password()), request.displayName());
        if (userId == null) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS, HttpStatus.CONFLICT,
                    "Email đã được sử dụng.", Map.of());
        }

        userRoleRepository.saveAndFlush(new UserRole(userId, RoleCode.LEARNER, null));

        String rawToken = tokenGenerator.generate();
        Instant expiresAt = clock.instant().plus(VERIFY_TOKEN_TTL);
        authTokenRepository.saveAndFlush(new AuthToken(
                userId, TokenType.EMAIL_VERIFY, tokenHasher.hash(rawToken), expiresAt));
        eventPublisher.publishEvent(new EmailVerificationRequestedEvent(request.email(), rawToken));

        return new RegisterResponse(userId, request.email(), false);
    }
}
