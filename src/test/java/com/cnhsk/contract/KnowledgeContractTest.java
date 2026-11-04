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

    @Test
    void FR001_FR017_FR019_questionGetOperationsParametersAndResponsesMatchContract() throws Exception {
        OpenAPI source = contract(); JsonNode runtime = runtime();
        for (String path : Set.of("/api/admin/questions", "/api/admin/questions/{id}")) {
            var expected = source.getPaths().get(path).getGet();
            JsonNode actual = runtime.path("paths").path(path).path("get");
            assertThat(actual.path("operationId").asText()).isEqualTo(expected.getOperationId());
            assertThat(actual.path("responses").fieldNames()).toIterable()
                    .containsExactlyInAnyOrderElementsOf(expected.getResponses().keySet());
            assertThat(actual.path("responses").path("200").path("content").path("application/json").path("schema").path("$ref").asText())
                    .isEqualTo(expected.getResponses().get("200").getContent().get("application/json").getSchema().get$ref());
        }
        JsonNode parameters = runtime.path("paths").path("/api/admin/questions").path("get").path("parameters");
        assertThat(parameters).extracting(value -> value.path("name").asText())
                .containsExactlyInAnyOrder("hskLevel", "qtype", "status", "kid", "q", "page", "size");
        for (JsonNode parameter : parameters) {
            String name = parameter.path("name").asText();
            assertThat(parameter.path("required").asBoolean()).isFalse();
            var expected = source.getPaths().get("/api/admin/questions").getGet().getParameters().stream()
                    .filter(value -> value.getName().equals(name)).findFirst().orElseThrow().getSchema();
            JsonNode actual = resolve(runtime, parameter.path("schema"));
            if (expected.getMinimum() != null) assertThat(actual.path("minimum").decimalValue()).isEqualByComparingTo(expected.getMinimum());
            if (expected.getMaximum() != null) assertThat(actual.path("maximum").decimalValue()).isEqualByComparingTo(expected.getMaximum());
            if (expected.getDefault() != null) assertThat(actual.path("default").asInt()).isEqualTo(((Number) expected.getDefault()).intValue());
            if (name.equals("q")) {
                assertThat(actual.path("minLength").asInt()).isEqualTo(2); assertThat(actual.path("maxLength").asInt()).isEqualTo(100);
            }
            if (name.equals("qtype") || name.equals("status")) {
                String schema = name.equals("qtype") ? "QuestionType" : "QuestionStatus";
                assertThat(actual.path("enum")).extracting(JsonNode::asText)
                        .containsExactlyElementsOf(source.getComponents().getSchemas().get(schema).getEnum());
            }
        }
        JsonNode id = runtime.path("paths").path("/api/admin/questions/{id}").path("get").path("parameters").get(0);
        assertThat(id.path("name").asText()).isEqualTo("id"); assertThat(id.path("required").asBoolean()).isTrue();
        assertThat(id.path("schema").path("type").asText()).isEqualTo("integer");
        assertThat(id.path("schema").path("format").asText()).isEqualTo("int64");
    }

    @Test
    void FR018_FR019_questionSchemasMatchFrontendFieldsAndCamelCaseOptions() throws Exception {
        OpenAPI source = contract(); JsonNode runtime = runtime(); JsonNode schemas = runtime.path("components").path("schemas");
        for (String name : Set.of("QuestionPage", "QuestionSummary", "QuestionDetail", "QuestionOption", "QuestionLabel")) {
            assertThat(schemas.path(name).path("properties").fieldNames()).toIterable()
                    .containsExactlyInAnyOrderElementsOf(source.getComponents().getSchemas().get(name).getProperties().keySet());
        }
        assertThat(schemas.path("QuestionSummary").path("properties").path("stemSummary").path("maxLength").asInt()).isEqualTo(120);
        assertThat(schemas.path("QuestionOption").path("properties").has("is_correct")).isFalse();
        assertThat(schemas.path("QuestionPage").path("properties").path("items").path("items").path("$ref").asText())
                .isEqualTo("#/components/schemas/QuestionSummary");
        assertThat(schemas.path("QuestionDetail").path("properties").path("options").path("items").path("$ref").asText())
                .isEqualTo("#/components/schemas/QuestionOption");
        assertThat(schemas.path("QuestionDetail").path("properties").path("labels").path("items").path("$ref").asText())
                .isEqualTo("#/components/schemas/QuestionLabel");
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
