package com.cnhsk.shared.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.security.SessionInvalidationService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = ProdSwaggerSecurityTest.ProbeController.class)
@ActiveProfiles("prod")
@TestPropertySource(properties = "cnhsk.jwt.secret=test-only-signing-secret-at-least-32-bytes")
@Import({SecurityConfig.class, SharedConfiguration.class, JwtService.class,
        ProdSwaggerSecurityTest.ProbeConfiguration.class})
class ProdSwaggerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Value("${springdoc.api-docs.enabled}")
    private boolean apiDocsEnabled;

    @Value("${springdoc.swagger-ui.enabled}")
    private boolean swaggerUiEnabled;

    @MockitoBean
    private SessionInvalidationService invalidationService;

    @BeforeEach
    void allowUnmarkedSessions() {
        when(invalidationService.findMarker(anyLong())).thenReturn(Optional.empty());
    }

    @Test
    void swaggerRoutesAreDeniedInProductionEvenForAdmin() throws Exception {
        org.assertj.core.api.Assertions.assertThat(apiDocsEnabled).isFalse();
        org.assertj.core.api.Assertions.assertThat(swaggerUiEnabled).isFalse();
        String adminToken = jwtService.issueAccessToken(42, List.of("ADMIN"), true, UUID.randomUUID());
        for (String path : new String[] {"/swagger-ui/index.html", "/swagger-ui/assets/swagger-ui.css",
                "/v3/api-docs", "/v3/api-docs/swagger-config", "/swagger-ui.html"}) {
            mockMvc.perform(get(path).header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        }
    }

    @TestConfiguration
    static class ProbeConfiguration {
        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping({"/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs", "/v3/api-docs/**"})
        String swaggerResource() {
            return "swagger";
        }
    }
}
