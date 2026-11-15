package com.cnhsk.shared.config;

import static com.cnhsk.learning.vocab.TopicPrerequisiteModels.*;
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

import com.cnhsk.learning.vocab.TopicPrerequisiteService;
import com.cnhsk.learning.vocab.web.TopicAdminController;
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

@WebMvcTest(controllers = TopicAdminController.class)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import({SecurityConfig.class, SharedConfiguration.class, JwtService.class})
class TopicAdminSecurityConfigIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @MockitoBean private TopicPrerequisiteService service;
    @MockitoBean private SessionInvalidationService invalidation;
    @MockitoBean private Clock clock;

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-15T00:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(invalidation.findMarker(anyLong())).thenReturn(Optional.empty());
        when(service.list(any(), any(), anyInt(), anyInt())).thenReturn(new TopicPage(List.of(), 0, 20, 0));
        when(service.setPrerequisites(anyLong(), anyLong(), any())).thenReturn(new AdminTopic(7, "Chủ đề", 1, List.of(), true, List.of()));
    }

    @Test void bothOperationsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/admin/topics")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/admin/topics/7/prerequisites").contentType(MediaType.APPLICATION_JSON).content("{\"prereqIds\":[]}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/topics").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"LEARNER", "TEACHER", "MODERATOR", "ACCOUNTANT", "ADMIN"})
    void otherRolesIncludingAdminCannotManagePrerequisites(String role) throws Exception {
        String token = token(List.of(role));
        mvc.perform(get("/api/admin/topics").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/topics/7/prerequisites").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"prereqIds\":[]}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"CONTENT_EDITOR", "CONTENT_EDITOR,ADMIN"})
    void editorBearerCanReadAndWriteWithoutCsrfAndUsesPrincipal(String roles) throws Exception {
        String token = "Bearer " + token(List.of(roles.split(",")));
        mvc.perform(get("/api/admin/topics").header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(put("/api/admin/topics/7/prerequisites").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"prereqIds\":[],\"actorId\":999}"))
                .andExpect(status().isOk());
        verify(service).setPrerequisites(7, 42, List.of());
    }

    @Test void cookieReadsNeedNoCsrfButWritesRequireMatchingToken() throws Exception {
        Cookie access = new Cookie("access_token", token(List.of("CONTENT_EDITOR")));
        mvc.perform(get("/api/admin/topics").cookie(access)).andExpect(status().isOk());
        clearInvocations(service);
        mvc.perform(put("/api/admin/topics/7/prerequisites").cookie(access)
                .contentType(MediaType.APPLICATION_JSON).content("{\"prereqIds\":[]}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        mvc.perform(put("/api/admin/topics/7/prerequisites").cookie(access, new Cookie("XSRF-TOKEN", "topic-csrf"))
                .header("X-XSRF-TOKEN", "wrong").contentType(MediaType.APPLICATION_JSON).content("{\"prereqIds\":[]}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
        mvc.perform(put("/api/admin/topics/7/prerequisites").cookie(access, new Cookie("XSRF-TOKEN", "topic-csrf"))
                .header("X-XSRF-TOKEN", "topic-csrf").contentType(MediaType.APPLICATION_JSON).content("{\"prereqIds\":[]}"))
                .andExpect(status().isOk());
    }
    private String token(List<String> roles) { return jwt.issueAccessToken(42, roles, true, UUID.randomUUID()); }
}
