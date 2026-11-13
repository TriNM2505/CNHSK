package com.cnhsk.shared.config;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cnhsk.auth.controller.ReminderUnsubscribeController;
import com.cnhsk.auth.reminder.ReminderUnsubscribeRateLimiter;
import com.cnhsk.auth.reminder.ReminderUnsubscribeService;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.security.SessionInvalidationService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ReminderUnsubscribeController.class)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import({SecurityConfig.class, SharedConfiguration.class, JwtService.class})
class ReminderUnsubscribeSecurityConfigIntegrationTest {

    private static final String ROUTE = "/api/public/reminder/unsubscribe";
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @MockitoBean private ReminderUnsubscribeService service;
    @MockitoBean private ReminderUnsubscribeRateLimiter limiter;
    @MockitoBean private SessionInvalidationService invalidation;
    @MockitoBean private Clock clock;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(Instant.parse("2031-11-15T13:00:20Z"));
        when(invalidation.findMarker(anyLong())).thenReturn(Optional.empty());
    }

    @Test
    void FR002_publicPostDoesNotRequireAuthenticationOrCsrf() throws Exception {
        mvc.perform(post(ROUTE).queryParam("token", "fixture-token"))
                .andExpect(status().isNoContent());
        verify(service).unsubscribe("fixture-token");
    }

    @Test
    void FR002_oneClickFormWithoutCookiesOrCsrfIsAllowed() throws Exception {
        mvc.perform(post(ROUTE).queryParam("token", "fixture-token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("List-Unsubscribe=One-Click"))
                .andExpect(status().isNoContent());
        verify(service).unsubscribe("fixture-token");
    }

    @Test
    void FR002_exactPostIsCsrfExemptEvenWhenAnAccessCookieIsPresent() throws Exception {
        mvc.perform(post(ROUTE).queryParam("token", "fixture-token").cookie(accessCookie()))
                .andExpect(status().isNoContent());
        verify(service).unsubscribe("fixture-token");
    }

    @Test
    void FR002_otherMethodsAndNeighboringPathsDoNotBecomePublicOrCsrfExempt() throws Exception {
        mvc.perform(get(ROUTE).queryParam("token", "fixture-token")).andExpect(status().isUnauthorized());
        mvc.perform(put(ROUTE).queryParam("token", "fixture-token")).andExpect(status().isUnauthorized());
        mvc.perform(post(ROUTE + "/extra").queryParam("token", "fixture-token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(ROUTE).cookie(accessCookie()).queryParam("token", "fixture-token"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        mvc.perform(post(ROUTE + "/extra").cookie(accessCookie()).queryParam("token", "fixture-token"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        verifyNoInteractions(service, limiter);
    }

    @Test
    void FR002_rateLimitUsesTheRemoteAddressRatherThanAForwardedHeader() throws Exception {
        mvc.perform(post(ROUTE).queryParam("token", "fixture-token").header("X-Forwarded-For", "192.0.2.99")
                        .with(req -> { req.setRemoteAddr("192.0.2.10"); return req; }))
                .andExpect(status().isNoContent());
        verify(limiter).check("192.0.2.10");
    }

    @Test
    void FR017_invalidJsonIsHandledLocallyWithTheUniform400() throws Exception {
        mvc.perform(post(ROUTE).contentType(MediaType.APPLICATION_JSON).content("{\"token\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_UNSUBSCRIBE_TOKEN"))
                .andExpect(jsonPath("$.message").value("Liên kết tắt nhắc không hợp lệ hoặc đã hết hạn."))
                .andExpect(jsonPath("$.request_id").isNotEmpty());
        verifyNoInteractions(service);
    }

    private Cookie accessCookie() {
        return new Cookie("access_token", jwt.issueAccessToken(42, List.of("LEARNER"), true, UUID.randomUUID()));
    }
}
