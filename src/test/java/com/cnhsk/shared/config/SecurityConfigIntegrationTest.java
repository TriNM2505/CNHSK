package com.cnhsk.shared.config;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import com.cnhsk.auth.controller.CsrfController;
import com.cnhsk.shared.config.CnhskProperties;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.security.SessionInvalidationMarker;
import com.cnhsk.shared.security.SessionInvalidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@WebMvcTest(controllers = CsrfController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, SharedConfiguration.class, JwtService.class,
        SecurityConfigIntegrationTest.ProbeConfiguration.class})
class SecurityConfigIntegrationTest {

    private static final String ACCESS_COOKIE = "access_token";
    private static final Instant FIXED_NOW = Instant.parse("2026-10-21T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CnhskProperties properties;

    @MockitoBean
    private SessionInvalidationService invalidationService;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void allowUnmarkedSessions() {
        when(invalidationService.findMarker(anyLong())).thenReturn(Optional.empty());
        when(clock.instant()).thenReturn(FIXED_NOW);
    }

    @Test
    void missingAuthenticationReturnsContractJsonAndRequestId() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("X-Request-Id", "test-request-42"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "test-request-42"))
                .andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.request_id").value("test-request-42"));
    }

    @Test
    void malformedAccessTokenReturnsUnauthenticatedJson() throws Exception {
        mockMvc.perform(get("/api/auth/me").cookie(new jakarta.servlet.http.Cookie(ACCESS_COOKIE, "not-a-jwt")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.request_id").isNotEmpty());
    }

    @Test
    void expiredAccessTokenCodeIsPreservedAs401() throws Exception {
        String token = Jwts.builder()
                .subject("42")
                .claim("roles", java.util.List.of("LEARNER"))
                .claim("ev", true)
                .issuedAt(Date.from(FIXED_NOW.minusSeconds(1200)))
                .expiration(Date.from(FIXED_NOW.minusSeconds(600)))
                .id("expired-token")
                .signWith(Keys.hmacShaKeyFor(properties.getJwt().getSecret()
                        .getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("TOKEN_EXPIRED"));
    }

    @Test
    void revokedSessionMetadataIsPreservedByEntryPoint() throws Exception {
        when(invalidationService.findMarker(42L)).thenReturn(Optional.of(
                SessionInvalidationMarker.revoked(FIXED_NOW.toEpochMilli() + 1)));

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer("LEARNER")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("SESSION_REVOKED"))
                .andExpect(jsonPath("$.request_id").isNotEmpty());
    }

    @Test
    void bannedSessionMetadataIsPreservedByAccessDeniedHandler() throws Exception {
        when(invalidationService.findMarker(42L)).thenReturn(Optional.of(
                SessionInvalidationMarker.banned(System.currentTimeMillis() + 1, "abuse")));

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer("LEARNER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("ACCOUNT_BANNED"))
                .andExpect(jsonPath("$.banReason").value("abuse"));
    }

    @Test
    void authenticatedUserWithoutAdminRoleGetsForbiddenJson() throws Exception {
        mockMvc.perform(get("/api/admin/test").header("Authorization", bearer("LEARNER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.request_id").isNotEmpty());
    }

    @Test
    void adminRoleCanAccessAdminRoute() throws Exception {
        mockMvc.perform(get("/api/admin/test").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void cookieMutationWithoutCsrfGetsCsrfErrorButBearerMutationDoesNotRequireCsrf() throws Exception {
        String accessToken = jwtService.issueAccessToken(42, java.util.List.of("LEARNER"), true, UUID.randomUUID());
        mockMvc.perform(post("/api/me/test").cookie(new jakarta.servlet.http.Cookie(ACCESS_COOKIE, accessToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));

        mockMvc.perform(post("/api/me/test").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void validDoubleSubmitTokenAllowsCookieMutation() throws Exception {
        String token = "csrf-test-token";
        mockMvc.perform(post("/api/me/test")
                        .cookie(new jakarta.servlet.http.Cookie(ACCESS_COOKIE,
                                jwtService.issueAccessToken(42, java.util.List.of("LEARNER"), true, UUID.randomUUID())),
                                new jakarta.servlet.http.Cookie("XSRF-TOKEN", token))
                        .header("X-XSRF-TOKEN", token))
                .andExpect(status().isOk());
    }

    @Test
    void csrfBootstrapSetsReadableCookieAndReturnsNoContent() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("Path=/")))
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("Domain=localhost")))
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("HttpOnly"))));
    }

    @Test
    void corsAllowsConfiguredOriginWithCredentialsAndOmitsUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/me")
                        .header("Origin", "http://localhost:5174")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5174"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        mockMvc.perform(options("/api/auth/me")
                        .header("Origin", "https://attacker.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void unknownApiRouteIsNotPublicByDefault() throws Exception {
        mockMvc.perform(get("/api/not-configured"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
    }

    private String bearer(String role) {
        return "Bearer " + jwtService.issueAccessToken(42, java.util.List.of(role), true, UUID.randomUUID());
    }

    @TestConfiguration
    static class ProbeConfiguration {
        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }

        @Bean
        AdminProbeController adminProbeController() {
            return new AdminProbeController();
        }
    }

    @RestController
    @RequestMapping("/api/me")
    static class ProbeController {
        @PostMapping("/test")
        String mutate() {
            return "ok";
        }
    }

    @RestController
    @RequestMapping("/api/admin")
    static class AdminProbeController {
        @GetMapping("/test")
        String read() {
            return "admin";
        }
    }
}
