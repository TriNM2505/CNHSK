package com.cnhsk.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
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
        "springdoc.packages-to-scan=com.cnhsk.learning.knowledge.web"})
class KnowledgeContractTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;

    @Test
    void FR015_FR016_sourceKnowledgeContractParses() throws Exception {
        OpenAPI source = contract();
        assertThat(source.getOpenapi()).startsWith("3.1.");
        assertThat(source.getPaths()).containsKeys("/api/knowledge-tree", "/api/knowledge-tree/nodes/{kid}");
    }

    @Test
    void FR015_FR016_runtimeOperationsResponsesAndParametersMatchContract() throws Exception {
        OpenAPI source = contract();
        JsonNode runtime = runtime();
        // T-10 covers tree reads; T-11/T-12/T-13 extend this file for question administration.
        for (String path : Set.of("/api/knowledge-tree", "/api/knowledge-tree/nodes/{kid}")) {
            var expected = source.getPaths().get(path).getGet();
            JsonNode actual = runtime.path("paths").path(path).path("get");
            assertThat(actual.path("operationId").asText()).isEqualTo(expected.getOperationId());
            assertThat(actual.path("responses").fieldNames()).toIterable()
                    .containsExactlyInAnyOrderElementsOf(expected.getResponses().keySet());
            assertThat(actual.path("responses").path("200").path("content").path("application/json")
                    .path("schema").path("$ref").asText())
                    .isEqualTo(expected.getResponses().get("200").getContent().get("application/json").getSchema().get$ref());
        }
        JsonNode list = runtime.path("paths").path("/api/knowledge-tree").path("get");
        assertThat(list.path("parameters")).extracting(value -> value.path("name").asText())
                .containsExactlyInAnyOrder("domain", "hskLevel");
        for (JsonNode parameter : list.path("parameters")) {
            assertThat(parameter.path("required").asBoolean()).isFalse();
            JsonNode schema = resolve(runtime, parameter.path("schema"));
            if (parameter.path("name").asText().equals("hskLevel")) {
                assertThat(schema.path("type").asText()).isEqualTo("integer");
                assertThat(schema.path("minimum").asInt()).isEqualTo(1);
                assertThat(schema.path("maximum").asInt()).isEqualTo(7);
            } else {
                assertThat(schema.path("enum")).extracting(JsonNode::asText)
                        .containsExactly("NA", "CH", "TV", "NP", "NL", "VB");
            }
        }
        JsonNode kid = runtime.path("paths").path("/api/knowledge-tree/nodes/{kid}").path("get").path("parameters").get(0);
        assertThat(kid.path("name").asText()).isEqualTo("kid");
        assertThat(kid.path("in").asText()).isEqualTo("path");
        assertThat(kid.path("required").asBoolean()).isTrue();
        assertThat(kid.path("schema").path("type").asText()).isEqualTo("string");
    }

    @Test
    void FR015_FR016_runtimeResponseFieldsMatchTreeAndFlattenedDetail() throws Exception {
        OpenAPI source = contract();
        JsonNode runtime = runtime();
        JsonNode schemas = runtime.path("components").path("schemas");
        Set<String> nodeFields = new HashSet<>(source.getComponents().getSchemas().get("KnowledgeNode").getProperties().keySet());
        assertThat(schemas.path("KnowledgeNode").path("properties").fieldNames()).toIterable()
                .containsExactlyInAnyOrderElementsOf(nodeFields);
        Schema<?> detail = source.getComponents().getSchemas().get("KnowledgeNodeDetail");
        Schema<?> extension = detail.getAllOf().get(1);
        nodeFields.addAll(extension.getProperties().keySet());
        assertThat(schemas.path("KnowledgeNodeDetail").path("properties").fieldNames()).toIterable()
                .containsExactlyInAnyOrderElementsOf(nodeFields);
        assertThat(schemas.path("KnowledgeTree").path("properties").fieldNames()).toIterable().containsExactly("nodes");
        assertThat(schemas.path("KnowledgeTree").path("properties").path("nodes").path("items").path("$ref").asText())
                .isEqualTo("#/components/schemas/KnowledgeNode");
        JsonNode topics = schemas.path("KnowledgeNodeDetail").path("properties").path("topics").path("items");
        assertThat(resolve(runtime, topics).path("properties").fieldNames()).toIterable()
                .containsExactlyInAnyOrder("id", "code", "name");
        assertThat(nodeFields).doesNotContain("id", "node", "questions", "options", "correctKey");
    }

    private JsonNode runtime() throws Exception {
        return mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
    }

    private JsonNode resolve(JsonNode runtime, JsonNode schema) {
        return schema.has("$ref") ? runtime.at(schema.path("$ref").asText().substring(1)) : schema;
    }

    private OpenAPI contract() throws Exception {
        Path source = Path.of("specs", "010-knowledge-tree", "contracts", "openapi-knowledge.yaml");
        var parsed = new OpenAPIV3Parser().readLocation(source.toAbsolutePath().toUri().toString(), null, null);
        assertThat(parsed.getMessages()).isEmpty();
        assertThat(parsed.getOpenAPI()).isNotNull();
        return parsed.getOpenAPI();
    }
}
