package com.cnhsk.shared.config;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cnhsk.learning.reminder.TodayReminderService;
import com.cnhsk.learning.reminder.TodayReminderService.TodayReminder;
import com.cnhsk.learning.reminder.web.ReminderController;
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
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ReminderController.class)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import({SecurityConfig.class, SharedConfiguration.class, JwtService.class})
class TodayReminderSecurityConfigIntegrationTest {

    private static final String ROUTE = "/api/reminders/today";

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @MockitoBean private TodayReminderService reminders;
    @MockitoBean private SessionInvalidationService invalidationService;
    @MockitoBean private Clock clock;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-14T13:30:00Z"));
        when(invalidationService.findMarker(anyLong())).thenReturn(Optional.empty());
    }

    @Test
    void FR019_missingOrInvalidTokensReturn401WithoutCallingService() throws Exception {
        mvc.perform(get(ROUTE)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.request_id").isNotEmpty());
        mvc.perform(get(ROUTE).header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
        mvc.perform(get(ROUTE).cookie(new Cookie("access_token", "invalid-token")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
        verifyNoInteractions(reminders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "ACCOUNTANT", "TEACHER", "MODERATOR", "CONTENT_EDITOR"})
    void FR019_nonLearnersAre403WithBearerAndCookie(String role) throws Exception {
        String token = token(List.of(role));
        mvc.perform(get(ROUTE).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        mvc.perform(get(ROUTE).cookie(new Cookie("access_token", token)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        verifyNoInteractions(reminders);
    }

    @Test
    void FR019_learnerBearerWithAdditionalRoleIsAccepted() throws Exception {
        when(reminders.today()).thenReturn(new TodayReminder(true, 4, "20:00"));
        mvc.perform(get(ROUTE).header("Authorization", "Bearer " + token(List.of("LEARNER", "ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.show").value(true))
                .andExpect(jsonPath("$.dueCount").value(4)).andExpect(jsonPath("$.reminderTime").value("20:00"));
        verify(reminders).today();
    }

    @Test
    void FR019_learnerCookieGetDoesNotRequireCsrf() throws Exception {
        when(reminders.today()).thenReturn(new TodayReminder(false, 4, "20:00"));
        mvc.perform(get(ROUTE).cookie(new Cookie("access_token", token(List.of("LEARNER")))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.show").value(false))
                .andExpect(jsonPath("$.dueCount").value(4));
        verify(reminders).today();
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE", "HEAD"})
    void FR019_onlyExactGetMethodIsGranted(String method) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), ROUTE)
                        .header("Authorization", "Bearer " + token(List.of("LEARNER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reminders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/reminders", "/api/reminders/today/extra", "/api/reminders/tomorrow"})
    void FR019_matcherDoesNotGrantOtherReminderPaths(String path) throws Exception {
        mvc.perform(get(path).header("Authorization", "Bearer " + token(List.of("LEARNER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reminders);
    }

    private String token(List<String> roles) {
        return jwt.issueAccessToken(42L, roles, false, UUID.randomUUID());
    }
}
