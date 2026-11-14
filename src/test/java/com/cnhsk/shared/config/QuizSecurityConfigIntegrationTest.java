package com.cnhsk.shared.config;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.learning.quiz.QuizCatalog;
import com.cnhsk.learning.quiz.QuizSessionService;
import com.cnhsk.learning.quiz.QuizResultService;
import com.cnhsk.learning.quiz.QuizCatalog.QuizListingPage;
import com.cnhsk.learning.quiz.web.QuizController;
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
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = QuizController.class)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import({SecurityConfig.class, SharedConfiguration.class, JwtService.class,
        QuizSecurityConfigIntegrationTest.Probes.class})
class QuizSecurityConfigIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @MockitoBean private QuizCatalog catalog;
    @MockitoBean private QuizSessionService sessions;
    @MockitoBean private QuizResultService results;
    @MockitoBean private AuthApi auth;
    @MockitoBean private SessionInvalidationService invalidation;
    @MockitoBean private Clock clock;

    @BeforeEach
    void setup() {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-14T04:00:00Z"));
        when(invalidation.findMarker(anyLong())).thenReturn(Optional.empty());
        when(auth.currentUser()).thenReturn(new CurrentUser(42, Set.of(RoleCode.LEARNER), false));
        when(catalog.list(42, null, 0, 20)).thenReturn(new QuizListingPage(List.of(), 0, 20, 0));
    }

    @Test
    void missingAndMalformedTokensReturn401BeforeCatalogAccess() throws Exception {
        mvc.perform(get("/api/quizzes")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.request_id").isNotEmpty());
        mvc.perform(get("/api/quizzes").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/quizzes").cookie(new Cookie("access_token", "invalid-token")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(catalog, auth);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "ACCOUNTANT", "TEACHER", "MODERATOR", "CONTENT_EDITOR"})
    void bothQuizNamespacesRequireLearnerWithBearerAndCookie(String role) throws Exception {
        String token = token(List.of(role));
        for (String path : List.of("/api/quizzes", "/api/quiz-attempts/probe/result")) {
            mvc.perform(get(path).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
            mvc.perform(get(path).cookie(new Cookie("access_token", token)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/quizzes/probe/attempts").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        verifyNoInteractions(catalog, auth);
    }

    @Test
    void learnerBearerKeepsCallerIdentityAndAcceptsAdditionalRoles() throws Exception {
        String token = token(List.of("LEARNER", "ADMIN"));
        mvc.perform(get("/api/quizzes").param("userId", "999").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        verify(catalog).list(42, null, 0, 20);
        mvc.perform(get("/api/quiz-attempts/probe/result").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void learnerCookieGetNeedsNoCsrfAndQuizWritesKeepCsrfProtection() throws Exception {
        Cookie access = new Cookie("access_token", token(List.of("LEARNER")));
        mvc.perform(get("/api/quizzes").cookie(access)).andExpect(status().isOk());
        mvc.perform(post("/api/quizzes/probe/attempts").cookie(access))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        mvc.perform(post("/api/quizzes/probe/attempts").cookie(access, new Cookie("XSRF-TOKEN", "quiz-csrf"))
                        .header("X-XSRF-TOKEN", "wrong"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/quizzes/probe/attempts").cookie(access, new Cookie("XSRF-TOKEN", "quiz-csrf"))
                        .header("X-XSRF-TOKEN", "quiz-csrf"))
                .andExpect(status().isOk());
    }

    @Test
    void realStartRouteAcceptsLearnerBearerAndRequiresCsrfForCookie() throws Exception {
        when(sessions.start(42, 9)).thenReturn(new QuizSessionService.Started(true,
                new QuizSessionService.QuizSession(7, 9, true, Instant.parse("2026-11-14T04:00:00Z"), List.of())));
        String token = token(List.of("LEARNER"));
        mvc.perform(post("/api/quizzes/9/attempts").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sessionId").value(7));
        Cookie access = new Cookie("access_token", token);
        mvc.perform(post("/api/quizzes/9/attempts").cookie(access)).andExpect(status().isForbidden());
        mvc.perform(post("/api/quizzes/9/attempts").cookie(access, new Cookie("XSRF-TOKEN", "quiz-csrf"))
                        .header("X-XSRF-TOKEN", "quiz-csrf"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/quizzes/9/attempts")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/quizzes-extra", "/api/quiz-attempts-extra/probe", "/api/quiz", "/api/other"})
    void matcherDoesNotGrantSimilarOrUnrelatedPaths(String path) throws Exception {
        mvc.perform(get(path).header("Authorization", "Bearer " + token(List.of("LEARNER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(catalog, auth);
    }

    private String token(List<String> roles) { return jwt.issueAccessToken(42, roles, false, UUID.randomUUID()); }

    @Test
    void realResultRouteAcceptsLearnerCookieWithoutCsrfAndBearerKeepsCallerIdentity() throws Exception {
        String token = token(List.of("LEARNER"));
        mvc.perform(get("/api/quiz-attempts/7/result").param("userId", "999")
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/quiz-attempts/8/result").cookie(new Cookie("access_token", token)))
                .andExpect(status().isOk());
        verify(results).result(42, 7);
        verify(results).result(42, 8);
        mvc.perform(get("/api/quiz-attempts/7/result")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/quiz-attempts/7/result")
                        .header("Authorization", "Bearer " + token(List.of("ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Probes {
        @Bean ProbeController quizProbeController() { return new ProbeController(); }
    }

    @TestComponent
    @RestController
    static class ProbeController {
        @GetMapping("/api/quiz-attempts/probe/result")
        Map<String, Boolean> result() { return Map.of("ok", true); }
        @PostMapping("/api/quizzes/probe/attempts")
        Map<String, Boolean> start() { return Map.of("ok", true); }
    }
}
