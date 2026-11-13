package com.cnhsk.shared.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cnhsk.auth.controller.ReminderSettingsController;
import com.cnhsk.auth.dto.ReminderSettingsResponse;
import com.cnhsk.auth.dto.ReminderSettingsResponse.Channels;
import com.cnhsk.auth.dto.ReminderUpdateRequest;
import com.cnhsk.auth.reminder.ReminderSettingsService;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.security.SessionInvalidationService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ReminderSettingsController.class)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import({SecurityConfig.class, SharedConfiguration.class, JwtService.class})
class ReminderSettingsSecurityConfigIntegrationTest {

    private static final String ROUTE = "/api/me/reminder";
    private static final String BODY = "{\"reminderTime\":\"20:00\",\"web\":true,\"email\":false}";
    private static final ReminderSettingsResponse SETTINGS = new ReminderSettingsResponse(
            "20:00", new Channels(true, false), false, true, "Asia/Ho_Chi_Minh");

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @MockitoBean private ReminderSettingsService settingsService;
    @MockitoBean private SessionInvalidationService invalidationService;
    @MockitoBean private Clock clock;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(Instant.parse("2031-11-15T10:00:00Z"));
        when(invalidationService.findMarker(anyLong())).thenReturn(Optional.empty());
    }

    @Test
    void FR001_missingOrInvalidAuthenticationReturns401BeforeCallingTheService() throws Exception {
        mockMvc.perform(get(ROUTE)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
        mockMvc.perform(put(ROUTE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
        mockMvc.perform(get(ROUTE).header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(ROUTE).cookie(new Cookie("access_token", "invalid-token")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(settingsService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "ACCOUNTANT", "TEACHER", "MODERATOR", "CONTENT_EDITOR"})
    void FR001_everyNonLearnerIsDeniedDespiteTheGenericMeMatcher(String role) throws Exception {
        String token = token(List.of(role));
        mockMvc.perform(get(ROUTE).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        mockMvc.perform(put(ROUTE).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        mockMvc.perform(get(ROUTE).cookie(new Cookie("access_token", token)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        mockMvc.perform(put(ROUTE).cookie(new Cookie("access_token", token), new Cookie("XSRF-TOKEN", "reminder-csrf"))
                        .header("X-XSRF-TOKEN", "reminder-csrf").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        verifyNoInteractions(settingsService);
    }

    @Test
    void FR001_cookiePutRequiresMatchingCsrfBeforeCallingTheService() throws Exception {
        Cookie access = new Cookie("access_token", token(List.of("LEARNER")));
        mockMvc.perform(put(ROUTE).cookie(access).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"))
                .andExpect(jsonPath("$.request_id").isNotEmpty());
        mockMvc.perform(put(ROUTE).cookie(access, new Cookie("XSRF-TOKEN", "reminder-csrf"))
                        .header("X-XSRF-TOKEN", "wrong").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        verifyNoInteractions(settingsService);

        when(settingsService.updateSettings(anyLong(), any())).thenReturn(SETTINGS);
        mockMvc.perform(put(ROUTE).cookie(access, new Cookie("XSRF-TOKEN", "reminder-csrf"))
                        .header("X-XSRF-TOKEN", "reminder-csrf").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
        verify(settingsService).updateSettings(42L, new ReminderUpdateRequest("20:00", true, false));
    }

    @Test
    void FR001_unverifiedMixedRoleLearnerCanUseBearerPutWithoutCsrf() throws Exception {
        when(settingsService.getSettings(anyLong())).thenReturn(SETTINGS);
        when(settingsService.updateSettings(anyLong(), any())).thenReturn(SETTINGS);
        String bearer = "Bearer " + token(List.of("LEARNER", "ADMIN"));

        mockMvc.perform(get(ROUTE).header("Authorization", bearer).param("userId", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(false));
        mockMvc.perform(put(ROUTE).header("Authorization", bearer).param("userId", "999")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());

        verify(settingsService).getSettings(42L);
        verify(settingsService).updateSettings(42L, new ReminderUpdateRequest("20:00", true, false));
    }

    @Test
    void FR001_cookieGetDoesNotRequireCsrf() throws Exception {
        when(settingsService.getSettings(42L)).thenReturn(SETTINGS);
        mockMvc.perform(get(ROUTE).cookie(new Cookie("access_token", token(List.of("LEARNER")))))
                .andExpect(status().isOk());
        verify(settingsService).getSettings(42L);
    }

    private String token(List<String> roles) {
        return jwtService.issueAccessToken(42L, roles, false, UUID.randomUUID());
    }
}
