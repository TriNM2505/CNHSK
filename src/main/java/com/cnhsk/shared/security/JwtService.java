package com.cnhsk.shared.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;

import com.cnhsk.shared.config.CnhskProperties;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Issues and verifies the access-token contract shared by all clients. */
@Service
public class JwtService {

    private static final String INVALID_TOKEN_MESSAGE = "The access token is invalid.";
    private static final String EXPIRED_TOKEN_MESSAGE = "The access token has expired.";

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;
    private final Clock clock;

    public JwtService(CnhskProperties properties, Clock clock) {
        String secret = properties.getJwt().getSecret();
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = properties.getJwt().getAccessTokenTtl();
        this.clock = clock;
    }

    /** Issues a 15-minute HS256 access token with the claims defined in FR-016. */
    public String issueAccessToken(long userId, Collection<String> roles, boolean emailVerified, UUID sessionId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive.");
        }
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null.");
        }
        List<String> roleClaims = List.copyOf(roles);
        if (roleClaims.stream().anyMatch(role -> role == null || role.isBlank())) {
            throw new IllegalArgumentException("roles must contain non-blank values.");
        }

        var issuedAt = clock.instant();
        return Jwts.builder()
                .subject(Long.toString(userId))
                .claim("roles", roleClaims)
                .claim("ev", emailVerified)
                .claim("sid", sessionId.toString())
                .claim("iatMs", issuedAt.toEpochMilli())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(accessTokenTtl)))
                .id(UUID.randomUUID().toString())
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /** Verifies signature, algorithm, expiry, and the required access-token claims. */
    public Claims verifyAccessToken(String token) {
        if (token == null || token.isBlank()) {
            throw unauthenticated();
        }
        try {
            Jws<Claims> parsed = Jwts.parser()
                    .verifyWith(signingKey)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token);
            if (!Jwts.SIG.HS256.getId().equals(parsed.getHeader().getAlgorithm())) {
                throw new IllegalArgumentException("Unexpected JWT algorithm.");
            }
            Claims claims = parsed.getPayload();
            validateRequiredClaims(claims);
            return claims;
        } catch (ExpiredJwtException exception) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED, HttpStatus.UNAUTHORIZED,
                    EXPIRED_TOKEN_MESSAGE, Map.of());
        } catch (JwtException | IllegalArgumentException exception) {
            throw unauthenticated();
        }
    }

    private static void validateRequiredClaims(Claims claims) {
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank() || Long.parseLong(subject) <= 0
                || claims.getIssuedAt() == null || claims.getExpiration() == null
                || claims.getId() == null || claims.getId().isBlank()
                || claims.get("sid") == null
                || !(claims.get("iatMs") instanceof Number issuedAtMs) || issuedAtMs.longValue() < 0
                || !(claims.get("ev") instanceof Boolean)
                || !(claims.get("roles") instanceof Collection<?> roles)
                || roles.stream().anyMatch(role -> !(role instanceof String value) || value.isBlank())) {
            throw new IllegalArgumentException("Required access-token claims are missing or invalid.");
        }
        UUID.fromString(claims.get("sid", String.class));
    }

    private static ApiException unauthenticated() {
        return new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED,
                INVALID_TOKEN_MESSAGE, Map.of());
    }
}
