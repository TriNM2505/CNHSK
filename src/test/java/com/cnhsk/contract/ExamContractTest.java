package com.cnhsk.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true",
        "springdoc.packages-to-scan=com.cnhsk.learning.exam.web"})
class ExamContractTest extends AbstractIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;

    @Test
    void AT29_runtimeExamOperationsAndStatusesMatchContract() throws Exception {
        Path source = Path.of("specs", "012-mock-exam-placement", "contracts", "openapi-exam.yaml");
        var parsed = new OpenAPIV3Parser().readLocation(source.toAbsolutePath().toUri().toString(), null, null);
        assertThat(parsed.getMessages()).isEmpty();
        var paths = parsed.getOpenAPI().getPaths();
        var response = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var runtime = mapper.readTree(response.getResponse().getContentAsByteArray()).path("paths");

        // T-16 adds the weak-points endpoint after MasteryReader is available.
        var expectedPaths = new TreeSet<>(paths.keySet());
        assertThat(expectedPaths.remove("/api/exam-attempts/{id}/weak-points")).isTrue();
        assertThat(expectedPaths).hasSize(8);
        assertThat(runtime.fieldNames()).toIterable().containsExactlyInAnyOrderElementsOf(expectedPaths);

        List<String> differences = new ArrayList<>();
        for (String path : expectedPaths) {
            paths.get(path).readOperationsMap().forEach((method, expected) -> {
                String verb = method.name().toLowerCase(java.util.Locale.ROOT);
                var actual = runtime.path(path).path(verb);
                if (!expected.getOperationId().equals(actual.path("operationId").asText())) {
                    differences.add(method + " " + path + " operationId: " + actual.path("operationId"));
                }
                var codes = new TreeSet<String>();
                actual.path("responses").fieldNames().forEachRemaining(codes::add);
                var expectedCodes = new TreeSet<>(expected.getResponses().keySet());
                if (!codes.equals(expectedCodes)) {
                    differences.add(method + " " + path + ": expected " + expectedCodes + ", runtime " + codes);
                }
            });
        }
        assertThat(differences).isEmpty();
    }

    @Test
    void AT29_malformedAttemptAndRunIdsAreForbiddenLikeMissingIds() throws Exception {
        String password = "Aa1" + UUID.randomUUID();
        var registration = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.test",
                        "password", password, "confirmPassword", password, "displayName", "Exam contract test",
                        "acceptTerms", true)))).andExpect(status().isCreated()).andReturn();
        long user = mapper.readTree(registration.getResponse().getContentAsByteArray()).path("userId").asLong();
        String bearer = "Bearer " + jwt.issueAccessToken(user, List.of("LEARNER"), false, UUID.randomUUID());
        for (String id : List.of("invalid", "0", "-1", "9223372036854775808", "9223372036854775807")) {
            for (var request : List.of(get("/api/exam-attempts/{id}/result", id),
                    post("/api/placement/runs/{runId}/next", id),
                    get("/api/placement/runs/{runId}/result", id))) {
                mvc.perform(request.header("Authorization", bearer)).andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.error_code").value("ATTEMPT_NOT_OWNED"));
            }
        }
        mvc.perform(post("/api/exams/{id}/attempts", "invalid").header("Authorization", bearer))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
    }
}
