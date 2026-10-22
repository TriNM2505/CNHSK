package com.cnhsk.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import com.cnhsk.shared.config.CnhskProperties;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void FR016_issuesHs256TokenWithRequiredClaimsAndFifteenMinuteLifetime() {
        JwtService service = service(Clock.fixed(NOW, ZoneOffset.UTC));

        UUID sessionId = UUID.randomUUID();
        String token = service.issueAccessToken(42, List.of("LEARNER", "ADMIN"), true, sessionId);
        Claims claims = service.verifyAccessToken(token);

        assertEquals("42", claims.getSubject());
        assertEquals(List.of("LEARNER", "ADMIN"), claims.get("roles"));
        assertEquals(Boolean.TRUE, claims.get("ev"));
        assertEquals(Date.from(NOW), claims.getIssuedAt());
        assertEquals(Date.from(NOW.plus(Duration.ofMinutes(15))), claims.getExpiration());
        assertTrue(claims.getId() != null && !claims.getId().isBlank());
        assertEquals(sessionId.toString(), claims.get("sid", String.class));
        assertEquals(NOW.toEpochMilli(), ((Number) claims.get("iatMs")).longValue());
        assertEquals(Jwts.SIG.HS256.getId(), Jwts.parser()
                .verifyWith(signingKey())
                .clock(() -> Date.from(NOW))
                .build()
                .parseSignedClaims(token)
                .getHeader().getAlgorithm());
    }

    @Test
    void FR016_generatesUniqueTokenIds() {
        JwtService service = service(Clock.fixed(NOW, ZoneOffset.UTC));

        String first = service.verifyAccessToken(
                service.issueAccessToken(42, List.of("LEARNER"), false, UUID.randomUUID())).getId();
        String second = service.verifyAccessToken(
                service.issueAccessToken(42, List.of("LEARNER"), false, UUID.randomUUID())).getId();

        assertNotEquals(first, second);
    }

    @Test
    void FR061_rejectsMalformedAndBadSignatureTokensAsUnauthenticated() {
        JwtService service = service(Clock.fixed(NOW, ZoneOffset.UTC));
        String token = service(Clock.fixed(NOW, ZoneOffset.UTC))
                .issueAccessToken(42, List.of("LEARNER"), true, UUID.randomUUID());
        String[] tokenParts = token.split("\\.", -1);
        byte[] signature = Base64.getUrlDecoder().decode(tokenParts[2]);
        signature[0] ^= 0x01;
        String badSignature = tokenParts[0] + "." + tokenParts[1] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);

        assertError(service, "not-a-jwt", ErrorCode.UNAUTHENTICATED);
        assertError(service, badSignature, ErrorCode.UNAUTHENTICATED);
        assertError(service, null, ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void FR061_distinguishesExpiredTokenFromInvalidToken() {
        String token = service(Clock.fixed(NOW, ZoneOffset.UTC))
                .issueAccessToken(42, List.of("LEARNER"), true, UUID.randomUUID());
        JwtService afterExpiry = service(Clock.fixed(NOW.plus(Duration.ofMinutes(15)).plusSeconds(1), ZoneOffset.UTC));

        assertError(afterExpiry, token, ErrorCode.TOKEN_EXPIRED);
    }

    @Test
    void FR016_rejectsTokensSignedWithAnOtherKey() {
        SecretKey otherKey = Keys.hmacShaKeyFor("abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("42")
                .claim("roles", List.of("LEARNER"))
                .claim("ev", true)
                .claim("sid", UUID.randomUUID().toString())
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plus(Duration.ofMinutes(15))))
                .id("valid-id")
                .signWith(otherKey, Jwts.SIG.HS256)
                .compact();

        assertError(service(Clock.fixed(NOW, ZoneOffset.UTC)), token, ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void protectedAccessTokenWithoutSessionClaimIsRejected() {
        String token = Jwts.builder()
                .subject("42")
                .claim("roles", List.of("LEARNER"))
                .claim("ev", true)
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plus(Duration.ofMinutes(15))))
                .id("legacy-token-id")
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();

        assertError(service(Clock.fixed(NOW, ZoneOffset.UTC)), token, ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void FR092_rejectsAccessTokenWithoutMillisecondIssueClaim() {
        String token = Jwts.builder()
                .subject("42")
                .claim("roles", List.of("LEARNER"))
                .claim("ev", true)
                .claim("sid", UUID.randomUUID().toString())
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plus(Duration.ofMinutes(15))))
                .id("legacy-token-id")
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();

        assertError(service(Clock.fixed(NOW, ZoneOffset.UTC)), token, ErrorCode.UNAUTHENTICATED);
    }

    private static JwtService service(Clock clock) {
        CnhskProperties properties = new CnhskProperties();
        properties.getJwt().setSecret(SECRET);
        return new JwtService(properties, clock);
    }

    private static SecretKey signingKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertError(JwtService service, String token, ErrorCode expected) {
        ApiException exception = assertThrows(ApiException.class, () -> service.verifyAccessToken(token));
        assertEquals(expected, exception.getCode());
        assertEquals(401, exception.getStatus().value());
        assertTrue(exception.getMessage() != null && !exception.getMessage().isBlank());
    }
}
