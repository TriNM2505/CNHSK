package com.cnhsk.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;

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

/** Runtime /v3/api-docs against the spec 010 v1.1 appendix contract for tree review counts. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true",
        "springdoc.packages-to-scan=com.cnhsk.learning.knowledge.web"})
class KnowledgeQuestionCountContractTest {

    private static final String PATH = "/api/admin/knowledge-tree/question-counts";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;

    @Test
    void FR029_appendixContractParsesAndRuntimeOperationMatches() throws Exception {
        OpenAPI source = contract();
        var expected = source.getPaths().get(PATH).getGet();
        JsonNode actual = runtime().path("paths").path(PATH).path("get");

        assertThat(actual.path("operationId").asText()).isEqualTo(expected.getOperationId());
        assertThat(actual.path("responses").fieldNames()).toIterable()
                .containsExactlyInAnyOrderElementsOf(expected.getResponses().keySet());
        assertThat(actual.path("responses").path("200").path("content").path("application/json")
                .path("schema").path("$ref").asText())
                .isEqualTo(expected.getResponses().get("200").getContent().get("application/json").getSchema().get$ref());
        assertThat(actual.path("parameters").isMissingNode() || actual.path("parameters").isEmpty()).isTrue();
    }

    @Test
    void FR029_runtimeSchemasMatchAppendix() throws Exception {
        OpenAPI source = contract();
        JsonNode schemas = runtime().path("components").path("schemas");
        for (String name : new String[] {"KnowledgeQuestionCounts", "KnowledgeQuestionCount"}) {
            assertThat(schemas.path(name).path("properties").fieldNames()).toIterable()
                    .containsExactlyInAnyOrderElementsOf(source.getComponents().getSchemas().get(name).getProperties().keySet());
        }
        assertThat(schemas.path("KnowledgeQuestionCounts").path("properties").path("counts").path("items").path("$ref").asText())
                .isEqualTo("#/components/schemas/KnowledgeQuestionCount");
        assertThat(schemas.path("KnowledgeQuestionCount").path("required")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("kid", "total", "approved");
    }

    private JsonNode runtime() throws Exception {
        return mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
    }

    private OpenAPI contract() {
        Path source = Path.of("specs", "010-knowledge-tree", "contracts", "openapi-knowledge-v1-1.yaml");
        var parsed = new OpenAPIV3Parser().readLocation(source.toAbsolutePath().toUri().toString(), null, null);
        assertThat(parsed.getMessages()).isEmpty();
        assertThat(parsed.getOpenAPI()).isNotNull();
        return parsed.getOpenAPI();
    }
}
