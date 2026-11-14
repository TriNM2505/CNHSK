package com.cnhsk.shared.config;

import static com.cnhsk.learning.examadmin.ExamAdminModels.*;
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

import com.cnhsk.learning.examadmin.ExamAdminService;
import com.cnhsk.learning.examadmin.web.ExamAdminController;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(controllers = ExamAdminController.class)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import({SecurityConfig.class, SharedConfiguration.class, JwtService.class})
class ExamAdminSecurityConfigIntegrationTest {
    private static final String BODY = """
            {"code":"EXAM","title":"Đề tự soạn","hskLevel":3,"totalScore":1,
             "sections":[{"name":"Đọc","type":"READING","durationMin":0}],"questions":[],"etag":"test"}
            """;
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @MockitoBean private ExamAdminService service;
    @MockitoBean private SessionInvalidationService invalidation;
    @MockitoBean private Clock clock;

    @BeforeEach
    void setup() {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-14T10:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(invalidation.findMarker(anyLong())).thenReturn(Optional.empty());
        var detail = new ExamDetail(7, "EXAM", "Đề tự soạn", 3, 1, List.of(new SectionView("Đọc", SectionType.READING, 0)),
                ExamStatus.DRAFT, null, "test", List.of(), List.of());
        when(service.list(any(), any(), any(), anyInt(), anyInt())).thenReturn(new ExamPage(List.of(), 0, 20, 0));
        when(service.detail(anyLong())).thenReturn(detail);
        when(service.create(anyLong(), any())).thenReturn(detail);
        when(service.update(anyLong(), any())).thenReturn(detail);
        when(service.clone(anyLong(), anyLong(), any())).thenReturn(detail);
    }

    @Test
    void allExamEndpointsRequireAuthentication() throws Exception {
        for (String operation : operations()) {
            mvc.perform(call(operation)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
        }
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEARNER", "TEACHER", "MODERATOR", "ACCOUNTANT", "ADMIN"})
    void otherRolesCannotManageExamsEvenWhenTheyAreAdministrators(String role) throws Exception {
        String bearer = "Bearer " + token(List.of(role));
        for (String operation : operations()) {
            mvc.perform(call(operation).header("Authorization", bearer)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        }
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONTENT_EDITOR", "CONTENT_EDITOR,ADMIN"})
    void editorBearerCanUseEveryImplementedOperation(String roles) throws Exception {
        String bearer = "Bearer " + token(List.of(roles.split(",")));
        for (String operation : operations()) {
            mvc.perform(call(operation).header("Authorization", bearer))
                    .andExpect(status().is(operation.startsWith("POST") ? 201 : 200));
        }
        verify(service).create(eq(42L), any());
        verify(service).clone(eq(7L), eq(42L), any());
    }

    @Test
    void editorCookieWritesRequireMatchingCsrfWhileReadsDoNot() throws Exception {
        Cookie access = new Cookie("access_token", token(List.of("CONTENT_EDITOR")));
        mvc.perform(get("/api/admin/exams").cookie(access)).andExpect(status().isOk());
        clearInvocations(service);
        for (String operation : operations().stream().filter(value -> !value.startsWith("GET")).toList()) {
            mvc.perform(call(operation).cookie(access)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
            mvc.perform(call(operation).cookie(access, new Cookie("XSRF-TOKEN", "exam-csrf"))
                    .header("X-XSRF-TOKEN", "wrong")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
        for (String operation : operations().stream().filter(value -> !value.startsWith("GET")).toList()) {
            mvc.perform(call(operation).cookie(access, new Cookie("XSRF-TOKEN", "exam-csrf"))
                    .header("X-XSRF-TOKEN", "exam-csrf")).andExpect(status().is(operation.startsWith("POST") ? 201 : 200));
        }
    }

    @Test
    void invalidCredentialsCannotAccessTheContentEditorMatcher() throws Exception {
        mvc.perform(get("/api/admin/exams").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    private List<String> operations() {
        return List.of("GET /api/admin/exams", "POST /api/admin/exams", "GET /api/admin/exams/7",
                "PUT /api/admin/exams/7", "POST /api/admin/exams/7/clone");
    }

    private MockHttpServletRequestBuilder call(String operation) {
        String[] parts = operation.split(" ", 2);
        return request(HttpMethod.valueOf(parts[0]), parts[1]).contentType(MediaType.APPLICATION_JSON).content(BODY);
    }

    private String token(List<String> roles) { return jwt.issueAccessToken(42, roles, true, UUID.randomUUID()); }
}
