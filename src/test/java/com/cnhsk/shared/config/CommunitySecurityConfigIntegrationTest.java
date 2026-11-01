package com.cnhsk.shared.config;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cnhsk.auth.controller.CsrfController;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.security.SessionInvalidationService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = CsrfController.class)
@ActiveProfiles({"test", "community-security-probe"})
@Import({SecurityConfig.class, SharedConfiguration.class, JwtService.class,
        CommunitySecurityConfigIntegrationTest.ProbeController.class})
class CommunitySecurityConfigIntegrationTest {

    private static final String PUBLIC_PROBE = "/api/public/community/security-probe";
    private static final String PRIVATE_PROBE = "/api/community/security-probe";
    private static final String MODERATION_PROBE = "/api/community/moderation/security-probe";
    private static final String LOOKALIKE_PROBE = "/api/communityOther/security-probe";
    private static final String UNMATCHED_PROBE = "/api/unmatched/security-probe";
    private static final String ACCESS_COOKIE = "access_token";
    private static final String REQUEST_ID = "community-security-request";
    private static final String CSRF_TOKEN = "community-security-csrf";
    private static final Instant FIXED_NOW = Instant.parse("2026-11-01T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

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
    void FR001_guestCanReadPublicCommunityProbe() throws Exception {
        mockMvc.perform(get(PUBLIC_PROBE))
                .andExpect(status().isOk())
                .andExpect(content().string("public-community-security-probe"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEARNER", "TEACHER", "MODERATOR", "CONTENT_EDITOR", "ACCOUNTANT", "ADMIN"})
    void FR001_everyRoleCanReadPublicCommunityProbe(String role) throws Exception {
        mockMvc.perform(get(PUBLIC_PROBE).header("Authorization", bearer(role)))
                .andExpect(status().isOk())
                .andExpect(content().string("public-community-security-probe"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void FR001_publicCommunityMutationsRejectGuests(String method) throws Exception {
        expectError(request(HttpMethod.valueOf(method), PUBLIC_PROBE), 401, "UNAUTHENTICATED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void FR001_publicCommunityMutationsRejectAuthenticatedUsers(String method) throws Exception {
        expectError(request(HttpMethod.valueOf(method), PUBLIC_PROBE)
                .header("Authorization", bearer("LEARNER")), 403, "FORBIDDEN");
    }

    @Test
    void FR001_privateCommunityReadAndWriteRequireAuthentication() throws Exception {
        expectError(get(PRIVATE_PROBE), 401, "UNAUTHENTICATED");
        expectError(post(PRIVATE_PROBE), 401, "UNAUTHENTICATED");
    }

    @Test
    void FR001_learnerCanReadAndWritePrivateCommunityWithBearerToken() throws Exception {
        String authorization = bearer("LEARNER");
        mockMvc.perform(get(PRIVATE_PROBE).header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(content().string("private-community-security-probe-read"));
        mockMvc.perform(post(PRIVATE_PROBE).header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(content().string("private-community-security-probe-write"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"TEACHER", "MODERATOR", "CONTENT_EDITOR", "ACCOUNTANT", "ADMIN"})
    void FR001_rolesWithoutLearnerCannotReadOrWritePrivateCommunity(String role) throws Exception {
        String authorization = bearer(role);
        expectError(get(PRIVATE_PROBE).header("Authorization", authorization), 403, "FORBIDDEN");
        expectError(post(PRIVATE_PROBE).header("Authorization", authorization), 403, "FORBIDDEN");
    }

    @Test
    void FR001_moderationReadAndWriteRequireAuthentication() throws Exception {
        expectError(get(MODERATION_PROBE), 401, "UNAUTHENTICATED");
        expectError(patch(MODERATION_PROBE), 401, "UNAUTHENTICATED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEARNER", "TEACHER", "CONTENT_EDITOR", "ACCOUNTANT", "ADMIN"})
    void FR001_rolesWithoutModeratorCannotReadOrMutateModeration(String role) throws Exception {
        String authorization = bearer(role);
        expectError(get(MODERATION_PROBE).header("Authorization", authorization), 403, "FORBIDDEN");
        expectError(patch(MODERATION_PROBE).header("Authorization", authorization), 403, "FORBIDDEN");
    }

    @Test
    void FR001_moderatorOnlyCanReadAndMutateModeration() throws Exception {
        String authorization = bearer("MODERATOR");
        mockMvc.perform(get(MODERATION_PROBE).header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(content().string("moderation-security-probe-read"));
        mockMvc.perform(patch(MODERATION_PROBE).header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(content().string("moderation-security-probe-write"));
    }

    @Test
    void FR001_learnerAndModeratorRolesAllowBothCommunityNamespaces() throws Exception {
        String authorization = bearer("LEARNER", "MODERATOR");
        mockMvc.perform(get(PRIVATE_PROBE).header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(content().string("private-community-security-probe-read"));
        mockMvc.perform(get(MODERATION_PROBE).header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(content().string("moderation-security-probe-read"));
    }

    @Test
    void FR001_adminWithModeratorCanModerateButStillNeedsLearnerForPrivateCommunity() throws Exception {
        String authorization = bearer("ADMIN", "MODERATOR");
        mockMvc.perform(get(MODERATION_PROBE).header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(content().string("moderation-security-probe-read"));
        expectError(get(PRIVATE_PROBE).header("Authorization", authorization), 403, "FORBIDDEN");
    }

    @Test
    void FR001_learnerCookieMutationRejectsMissingOrMismatchedCsrf() throws Exception {
        Cookie accessCookie = accessCookie("LEARNER");
        expectError(post(PRIVATE_PROBE).cookie(accessCookie), 403, "CSRF_TOKEN_INVALID");
        expectError(post(PRIVATE_PROBE)
                .cookie(accessCookie, new Cookie("XSRF-TOKEN", CSRF_TOKEN))
                .header("X-XSRF-TOKEN", "different-csrf-token"), 403, "CSRF_TOKEN_INVALID");
    }

    @Test
    void FR001_validCookieCsrfAllowsLearnerMutationAndStillRejectsWrongRole() throws Exception {
        mockMvc.perform(withCookieCsrf(post(PRIVATE_PROBE), "LEARNER"))
                .andExpect(status().isOk())
                .andExpect(content().string("private-community-security-probe-write"));
        expectError(withCookieCsrf(post(PRIVATE_PROBE), "MODERATOR"), 403, "FORBIDDEN");
    }

    @Test
    void FR001_validCookieCsrfAllowsModeratorMutationAndStillRejectsLearner() throws Exception {
        mockMvc.perform(withCookieCsrf(patch(MODERATION_PROBE), "MODERATOR"))
                .andExpect(status().isOk())
                .andExpect(content().string("moderation-security-probe-write"));
        expectError(withCookieCsrf(patch(MODERATION_PROBE), "LEARNER"), 403, "FORBIDDEN");
    }

    @Test
    void FR001_malformedBearerOrCookieTokenIsRejectedEvenOnPublicGet() throws Exception {
        expectError(get(PUBLIC_PROBE).header("Authorization", "Bearer not-a-jwt"), 401, "UNAUTHENTICATED");
        expectError(get(PUBLIC_PROBE).cookie(new Cookie(ACCESS_COOKIE, "not-a-jwt")),
                401, "UNAUTHENTICATED");
    }

    @Test
    void FR001_lookalikeAndUnmatchedRoutesRemainDeniedByDefault() throws Exception {
        String authorization = bearer("LEARNER", "MODERATOR", "ADMIN");
        for (String path : List.of(LOOKALIKE_PROBE, UNMATCHED_PROBE)) {
            expectError(get(path), 401, "UNAUTHENTICATED");
            expectError(get(path).header("Authorization", authorization), 403, "FORBIDDEN");
        }
    }

    @Test
    void FR001_contractUrlsRejectGuestPostAndNonModeratorReads() throws Exception {
        expectError(post("/api/community/posts"), 401, "UNAUTHENTICATED");
        expectError(get("/api/community/moderation/posts").header("Authorization", bearer("ADMIN")),
                403, "FORBIDDEN");
        expectError(get("/api/community/moderation/posts").header("Authorization", bearer("LEARNER")),
                403, "FORBIDDEN");
    }

    private void expectError(MockHttpServletRequestBuilder requestBuilder, int expectedStatus, String errorCode)
            throws Exception {
        mockMvc.perform(requestBuilder.header("X-Request-Id", REQUEST_ID))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("X-Request-Id", REQUEST_ID))
                .andExpect(jsonPath("$.error_code").value(errorCode))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.request_id").value(REQUEST_ID));
    }

    private String bearer(String... roles) {
        return "Bearer " + jwtService.issueAccessToken(42L, List.of(roles), true, UUID.randomUUID());
    }

    private Cookie accessCookie(String... roles) {
        return new Cookie(ACCESS_COOKIE,
                jwtService.issueAccessToken(42L, List.of(roles), true, UUID.randomUUID()));
    }

    private MockHttpServletRequestBuilder withCookieCsrf(MockHttpServletRequestBuilder requestBuilder, String role) {
        return requestBuilder.cookie(accessCookie(role), new Cookie("XSRF-TOKEN", CSRF_TOKEN))
                .header("X-XSRF-TOKEN", CSRF_TOKEN);
    }

    @RestController
    @Profile("community-security-probe")
    static class ProbeController {

        @GetMapping(PUBLIC_PROBE)
        String publicRead() {
            return "public-community-security-probe";
        }

        @RequestMapping(path = PUBLIC_PROBE, method = {
                RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
        String publicMutation() {
            return "public-community-security-probe-write";
        }

        @GetMapping(PRIVATE_PROBE)
        String privateRead() {
            return "private-community-security-probe-read";
        }

        @PostMapping(PRIVATE_PROBE)
        String privateWrite() {
            return "private-community-security-probe-write";
        }

        @GetMapping(MODERATION_PROBE)
        String moderationRead() {
            return "moderation-security-probe-read";
        }

        @PatchMapping(MODERATION_PROBE)
        String moderationWrite() {
            return "moderation-security-probe-write";
        }

        @GetMapping({LOOKALIKE_PROBE, UNMATCHED_PROBE})
        String deniedByDefault() {
            return "unmatched-community-security-probe";
        }
    }
}
