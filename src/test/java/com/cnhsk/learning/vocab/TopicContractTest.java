package com.cnhsk.learning.vocab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true",
        "springdoc.packages-to-scan=com.cnhsk.learning.vocab.web"})
class TopicContractTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;

    @Test
    void FR013_FR018_runtimePathsResponsesAndParametersMatchTheApprovedContract() throws Exception {
        OpenAPI source = contract();
        JsonNode runtime = mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(runtime.path("paths").fieldNames()).toIterable().containsExactlyInAnyOrder(
                "/api/topics", "/api/topics/{id}");
        for (String path : Set.of("/api/topics", "/api/topics/{id}")) {
            var operation = source.getPaths().get(path).getGet();
            JsonNode documented = runtime.path("paths").path(path).path("get");
            assertThat(documented.path("operationId").asText()).isEqualTo(operation.getOperationId());
            assertThat(documented.path("responses").fieldNames()).toIterable()
                    .containsExactlyInAnyOrderElementsOf(operation.getResponses().keySet());
        }
        JsonNode hsk = runtime.path("paths").path("/api/topics").path("get").path("parameters").get(0).path("schema");
        assertThat(hsk.path("type").asText()).isEqualTo("integer");
        assertThat(hsk.path("minimum").asInt()).isEqualTo(1);
        assertThat(hsk.path("maximum").asInt()).isEqualTo(9);
        JsonNode id = runtime.path("paths").path("/api/topics/{id}").path("get").path("parameters").get(0).path("schema");
        assertThat(id.path("type").asText()).isEqualTo("integer");
        assertThat(id.path("format").asText()).isEqualTo("int64");
    }

    @Test
    void FR013_FR018_runtimeDtoFieldsMatchSummaryAndFlattenedDetailSchemas() throws Exception {
        OpenAPI source = contract();
        JsonNode runtime = mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("components").path("schemas");
        Map<?, ?> summaryFields = source.getComponents().getSchemas().get("TopicSummary").getProperties();
        Set<String> expected = new HashSet<>();
        summaryFields.keySet().forEach(key -> expected.add(key.toString()));
        assertThat(runtime.path("TopicSummary").path("properties").fieldNames()).toIterable()
                .containsExactlyInAnyOrderElementsOf(expected);
        expected.addAll(Set.of("words", "actions"));
        assertThat(runtime.path("TopicDetail").path("properties").fieldNames()).toIterable()
                .containsExactlyInAnyOrderElementsOf(expected);
        assertThat(runtime.path("TopicActions").path("properties").fieldNames()).toIterable()
                .containsExactlyInAnyOrder("canStudy", "canPracticeRecognition", "canPracticeListening",
                        "canTakeTest", "testBlockedReason", "requiredWords", "retryAvailableAt");
    }

    private static OpenAPI contract() throws Exception {
        var parsed = new OpenAPIV3Parser().readContents(Files.readString(Path.of(
                "specs", "003-vocab-topics", "contracts", "openapi-practice-vocab.yaml")));
        assertThat(parsed.getMessages()).isEmpty();
        assertThat(parsed.getOpenAPI()).isNotNull();
        return parsed.getOpenAPI();
    }
}
