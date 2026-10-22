package com.cnhsk.shared.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.security.SessionInvalidationService;
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

@WebMvcTest(controllers = DevSwaggerSecurityTest.ProbeController.class)
@ActiveProfiles("dev")
@TestPropertySource(properties = "cnhsk.jwt.secret=test-only-signing-secret-at-least-32-bytes")
@Import({SecurityConfig.class, SharedConfiguration.class, JwtService.class,
        DevSwaggerSecurityTest.ProbeConfiguration.class})
class DevSwaggerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${springdoc.api-docs.enabled}")
    private boolean apiDocsEnabled;

    @Value("${springdoc.swagger-ui.enabled}")
    private boolean swaggerUiEnabled;

    @MockitoBean
    private SessionInvalidationService invalidationService;

    @Test
    void swaggerRoutesArePublicInDevProfile() throws Exception {
        org.assertj.core.api.Assertions.assertThat(apiDocsEnabled).isTrue();
        org.assertj.core.api.Assertions.assertThat(swaggerUiEnabled).isTrue();
        for (String path : new String[] {"/swagger-ui/index.html", "/swagger-ui/assets/swagger-ui.css",
                "/v3/api-docs", "/v3/api-docs/swagger-config", "/swagger-ui.html"}) {
            mockMvc.perform(get(path)).andExpect(status().isOk());
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
