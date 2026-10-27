package com.cnhsk.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.SecretKey;

import com.cnhsk.shared.config.CnhskProperties;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

class JwtAuthenticationFilterTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mockValueOperations();
    private final AuthenticationEntryPoint entryPoint = mock(AuthenticationEntryPoint.class);
    private final AccessDeniedHandler deniedHandler = mock(AccessDeniedHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private JwtService jwtService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        when(redisTemplate.opsForValue()).thenReturn(values);
        jwtService = new JwtService(properties(), Clock.fixed(NOW, ZoneOffset.UTC));
        filter = new JwtAuthenticationFilter(jwtService,
                new SessionInvalidationService(redisTemplate, objectMapper), entryPoint, deniedHandler);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void FR023_cookieOnlyRequestAuthenticates() throws Exception {
        String token = token(15, NOW, NOW.plusSeconds(900));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie("access_token", token));
        AtomicBoolean chainCalled = run(request);

        assertTrue(chainCalled.get());
        assertEquals(15L, principal().userId());
        verify(values).get("auth:invalid-before:15");
    }

    @Test
    void FR023_bearerHeaderOnlyRequestAuthenticates() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token(16, NOW, NOW.plusSeconds(900)));

        assertTrue(run(request).get());
        assertEquals(16L, principal().userId());
    }

    @Test
    void FR023_cookieWinsWhenBothCookieAndBearerHeaderArePresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie("access_token", token(17, NOW, NOW.plusSeconds(900))));
        request.addHeader("Authorization", "Bearer " + token(18, NOW, NOW.plusSeconds(900)));

        assertTrue(run(request).get());
        assertEquals(17L, principal().userId());
        verify(values).get("auth:invalid-before:17");
        verify(values, never()).get("auth:invalid-before:18");
    }

    @Test
    void AT64_oneHundredAuthenticatedRequestsCheckRedisWithoutAnyDatabaseDependency() throws Exception {
        String accessToken = token(181, NOW, NOW.plusSeconds(900));

        for (int requestNumber = 0; requestNumber < 100; requestNumber++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + accessToken);
            assertTrue(run(request).get());
            assertEquals(181L, principal().userId());
        }

        verify(values, times(100)).get("auth:invalid-before:181");
    }

    @Test
    void FR092_bannedMarkerDeniesWithBanReason() throws Exception {
        when(values.get("auth:invalid-before:19")).thenReturn(objectMapper.writeValueAsString(
                SessionInvalidationMarker.banned(NOW.toEpochMilli() + 1, "spam")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token(19, NOW, NOW.plusSeconds(900)));

        assertFalse(run(request).get());
        var exception = org.mockito.ArgumentCaptor.forClass(AccessDeniedApiException.class);
        verify(deniedHandler).handle(any(), any(), exception.capture());
        assertEquals(ErrorCode.ACCOUNT_BANNED, exception.getValue().getErrorCode());
        assertEquals(Map.of("banReason", "spam"), exception.getValue().getExtra());
        verify(entryPoint, never()).commence(any(), any(), any());
    }

    @Test
    void FR092_revokedMarkerReturnsSessionRevoked() throws Exception {
        when(values.get("auth:invalid-before:20")).thenReturn(objectMapper.writeValueAsString(
                SessionInvalidationMarker.revoked(NOW.toEpochMilli() + 1)));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token(20, NOW, NOW.plusSeconds(900)));

        assertFalse(run(request).get());
        var exception = org.mockito.ArgumentCaptor.forClass(AuthenticationApiException.class);
        verify(entryPoint).commence(any(), any(), exception.capture());
        assertEquals(ErrorCode.SESSION_REVOKED, exception.getValue().getErrorCode());
    }

    @Test
    void FR092_tokenIssuedAtEqualToMarkerIsAllowed() throws Exception {
        when(values.get("auth:invalid-before:21")).thenReturn(objectMapper.writeValueAsString(
                SessionInvalidationMarker.revoked(NOW.toEpochMilli())));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token(21, NOW, NOW.plusSeconds(900)));

        assertTrue(run(request).get());
        assertEquals(21L, principal().userId());
    }

    @Test
    void FR092_millisecondMarkerDistinguishesTokensIssuedWithinTheSameSecond() throws Exception {
        Instant beforeRevocation = NOW.plusMillis(100);
        Instant revocation = NOW.plusMillis(200);
        Instant afterRevocation = NOW.plusMillis(300);
        when(values.get("auth:invalid-before:24")).thenReturn(objectMapper.writeValueAsString(
                SessionInvalidationMarker.revoked(revocation.toEpochMilli())));

        MockHttpServletRequest oldRequest = new MockHttpServletRequest();
        oldRequest.addHeader("Authorization", "Bearer "
                + token(24, beforeRevocation, NOW.plusSeconds(900)));
        assertFalse(run(oldRequest).get());

        MockHttpServletRequest newRequest = new MockHttpServletRequest();
        newRequest.addHeader("Authorization", "Bearer "
                + token(24, afterRevocation, NOW.plusSeconds(900)));
        assertTrue(run(newRequest).get());
    }

    @Test
    void NFR_A02_redisFailureDoesNotBlockValidToken() throws Exception {
        when(values.get(anyString())).thenThrow(new IllegalStateException("redis unavailable"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token(22, NOW, NOW.plusSeconds(900)));

        assertTrue(run(request).get());
        assertEquals(22L, principal().userId());
    }

    @Test
    void FR061_expiredAndMalformedTokensUseAuthenticationEntryPoint() throws Exception {
        MockHttpServletRequest expiredRequest = new MockHttpServletRequest();
        expiredRequest.addHeader("Authorization", "Bearer " + token(23, NOW.minusSeconds(901), NOW.minusSeconds(1)));
        assertFalse(run(expiredRequest).get());
        MockHttpServletRequest malformedRequest = new MockHttpServletRequest();
        malformedRequest.addHeader("Authorization", "Bearer broken");
        assertFalse(run(malformedRequest).get());
        var exceptions = org.mockito.ArgumentCaptor.forClass(AuthenticationApiException.class);
        verify(entryPoint, times(2)).commence(any(), any(), exceptions.capture());
        assertEquals(ErrorCode.TOKEN_EXPIRED, exceptions.getAllValues().get(0).getErrorCode());
        assertEquals(ErrorCode.UNAUTHENTICATED, exceptions.getAllValues().get(1).getErrorCode());
    }

    @Test
    void requestWithoutTokenRemainsGuest() throws Exception {
        assertTrue(run(new MockHttpServletRequest()).get());
        assertTrue(SecurityContextHolder.getContext().getAuthentication() == null);
        verify(values, never()).get(anyString());
    }

    private AtomicBoolean run(MockHttpServletRequest request) throws IOException, ServletException {
        AtomicBoolean chainCalled = new AtomicBoolean();
        FilterChain chain = (servletRequest, servletResponse) -> chainCalled.set(true);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chainCalled;
    }

    private static AuthenticatedUser principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertInstanceOf(AuthenticatedUser.class, authentication.getPrincipal());
        return (AuthenticatedUser) authentication.getPrincipal();
    }

    private String token(long userId, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .subject(Long.toString(userId))
                .claim("roles", List.of("LEARNER", "ADMIN"))
                .claim("ev", true)
                .claim("sid", UUID.nameUUIDFromBytes(("session-" + userId).getBytes(StandardCharsets.UTF_8)).toString())
                .claim("iatMs", issuedAt.toEpochMilli())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .id("jwt-id-" + userId)
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();
    }

    private static CnhskProperties properties() {
        CnhskProperties properties = new CnhskProperties();
        properties.getJwt().setSecret(SECRET);
        return properties;
    }

    private static SecretKey signingKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static ValueOperations<String, String> mockValueOperations() {
        return mock(ValueOperations.class);
    }
}
