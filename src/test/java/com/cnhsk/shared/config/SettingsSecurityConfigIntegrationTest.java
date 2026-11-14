package com.cnhsk.shared.config;

import static com.cnhsk.shared.settings.SettingsAdminModels.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.security.SessionInvalidationService;
import com.cnhsk.shared.settings.SettingsAdminService;
import com.cnhsk.shared.settings.SettingsController;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(controllers = SettingsController.class)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import({SecurityConfig.class, SharedConfiguration.class, JwtService.class})
class SettingsSecurityConfigIntegrationTest {
    private static final String ROOT = "/api/admin/settings";
    private static final String KEY = ROOT + "/billing.free-monthly-limit";
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @MockitoBean private SettingsAdminService service;
    @MockitoBean private SessionInvalidationService invalidation;
    @MockitoBean private Clock clock;

    @BeforeEach
    void setup() {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-14T10:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(invalidation.findMarker(anyLong())).thenReturn(Optional.empty());
        var item = new SettingItem("billing.free-monthly-limit", "Thanh toán", "Lượt miễn phí", "INT", 13, 10, 0, 1000, true, new UserRef(42, "Quản trị"), Instant.EPOCH);
        when(service.list()).thenReturn(List.of(item));
        when(service.change(anyString(), anyLong(), any())).thenReturn(item);
        when(service.reset(anyString(), anyLong(), any())).thenReturn(item);
        when(service.history(anyString(), anyInt(), anyInt())).thenReturn(new SettingHistoryPage(List.of(), 0, 20, 0));
    }

    @Test
    void everySettingsOperationRequiresAuthentication() throws Exception {
        for (String operation : operations()) mvc.perform(call(operation)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEARNER", "CONTENT_EDITOR", "TEACHER", "MODERATOR", "ACCOUNTANT"})
    void onlyAdministratorsMayManageSettings(String role) throws Exception {
        for (String operation : operations()) mvc.perform(call(operation).header("Authorization", "Bearer " + token(List.of(role))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "ADMIN,CONTENT_EDITOR"})
    void administratorBearerDoesNotRequireCsrf(String roles) throws Exception {
        for (String operation : operations()) mvc.perform(call(operation).header("Authorization", "Bearer " + token(List.of(roles.split(",")))))
                .andExpect(status().isOk());
        verify(service).change(eq("billing.free-monthly-limit"), eq(42L), any());
        verify(service).reset(eq("billing.free-monthly-limit"), eq(42L), any());
    }

    @Test
    void cookieWritesRequireMatchingCsrfAndReadsRemainAvailable() throws Exception {
        Cookie access = new Cookie("access_token", token(List.of("ADMIN")));
        mvc.perform(get(ROOT).cookie(access)).andExpect(status().isOk());
        mvc.perform(get(KEY + "/history").cookie(access)).andExpect(status().isOk());
        clearInvocations(service);
        for (String operation : operations().stream().filter(value -> !value.startsWith("GET")).toList()) {
            mvc.perform(call(operation).cookie(access)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
            mvc.perform(call(operation).cookie(access, new Cookie("XSRF-TOKEN", "settings-csrf"))
                    .header("X-XSRF-TOKEN", "wrong")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
        for (String operation : operations().stream().filter(value -> !value.startsWith("GET")).toList()) {
            mvc.perform(call(operation).cookie(access, new Cookie("XSRF-TOKEN", "settings-csrf"))
                    .header("X-XSRF-TOKEN", "settings-csrf")).andExpect(status().isOk());
        }
    }

    @Test
    void invalidBearerIsRejectedBeforeTheController() throws Exception {
        mvc.perform(get(ROOT).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    private List<String> operations() { return List.of("GET " + ROOT, "PUT " + KEY, "DELETE " + KEY, "GET " + KEY + "/history"); }
    private MockHttpServletRequestBuilder call(String operation) {
        String[] parts = operation.split(" ", 2);
        return request(HttpMethod.valueOf(parts[0]), parts[1]).contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":13,\"reason\":\"Lý do kiểm thử\"}");
    }
    private String token(List<String> roles) { return jwt.issueAccessToken(42, roles, true, UUID.randomUUID()); }
}
