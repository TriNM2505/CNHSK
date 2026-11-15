package com.cnhsk.learning.vocab;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true", "springdoc.packages-to-scan=com.cnhsk.learning.vocab.web"})
class TopicAdminContractTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;

    @Test void bothAdminOperationsMatchContractMethodsStatusesAndResponseSchemas() throws Exception {
        var source = contract();
        var runtime = runtime();
        Set<String> paths = new HashSet<>();
        runtime.path("paths").fieldNames().forEachRemaining(path -> { if (path.startsWith("/api/admin/topics")) paths.add(path); });
        assertThat(paths).containsExactlyInAnyOrder("/api/admin/topics", "/api/admin/topics/{id}/prerequisites");
        for (String path : paths) {
            String method = path.endsWith("prerequisites") ? "put" : "get";
            var operation = method.equals("put") ? source.getPaths().get(path).getPut() : source.getPaths().get(path).getGet();
            JsonNode documented = runtime.path("paths").path(path);
            assertThat(documented.fieldNames()).toIterable().containsExactly(method);
            assertThat(documented.path(method).path("operationId").asText()).isEqualTo(operation.getOperationId());
            assertThat(documented.path(method).path("responses").fieldNames()).toIterable()
                    .containsExactlyInAnyOrderElementsOf(operation.getResponses().keySet());
            assertThat(documented.path(method).path("responses").path("200").path("content").path("application/json").path("schema").path("$ref").asText())
                    .isEqualTo(operation.getResponses().get("200").getContent().get("application/json").getSchema().get$ref());
        }
    }

    @Test void requestAndQueryConstraintsMatchTheApprovedContract() throws Exception {
        var source = contract();
        JsonNode runtime = runtime();
        JsonNode request = runtime.path("components").path("schemas").path("PrerequisiteWrite");
        assertThat(request.path("required")).extracting(JsonNode::asText).containsExactly("prereqIds");
        var expected = (io.swagger.v3.oas.models.media.Schema<?>) source.getComponents().getSchemas().get("PrerequisiteWrite").getProperties().get("prereqIds");
        var ids = request.path("properties").path("prereqIds");
        assertThat(ids.path("maxItems").asInt()).isEqualTo(expected.getMaxItems());
        assertThat(ids.path("uniqueItems").asBoolean()).isEqualTo(expected.getUniqueItems());
        assertThat(ids.path("items").path("format").asText()).isEqualTo("int64");
        var operation = runtime.path("paths").path("/api/admin/topics/{id}/prerequisites").path("put");
        assertThat(operation.path("requestBody").path("required").asBoolean()).isTrue();
        assertThat(operation.path("requestBody").path("content").path("application/json").path("schema").path("$ref").asText())
                .isEqualTo("#/components/schemas/PrerequisiteWrite");
        var parameters = runtime.path("paths").path("/api/admin/topics").path("get").path("parameters");
        for (JsonNode parameter : parameters) {
            JsonNode schema = parameter.path("schema");
            switch (parameter.path("name").asText()) {
                case "hsk" -> { assertThat(schema.path("minimum").asInt()).isEqualTo(1); assertThat(schema.path("maximum").asInt()).isEqualTo(9); }
                case "q" -> assertThat(schema.path("maxLength").asInt()).isEqualTo(100);
                case "page" -> { assertThat(schema.path("minimum").asInt()).isZero(); assertThat(schema.path("default").asInt()).isZero(); }
                case "size" -> { assertThat(schema.path("maximum").asInt()).isEqualTo(50); assertThat(schema.path("default").asInt()).isEqualTo(20); }
                default -> fail("Unexpected topic query parameter: " + parameter.path("name"));
            }
        }
        assertThat(parameters).hasSize(4);
    }

    @Test void uniqueErrorAndTopicSchemasMatchWithoutChangingTheSharedErrorSchema() throws Exception {
        var source = contract();
        var runtime = runtime();
        var schemas = runtime.path("components").path("schemas");
        for (String name : Set.of("TopicRef", "AdminTopic", "AdminTopicPage", "TopicPrerequisiteError",
                "TopicPrerequisiteCycleDetails", "TopicPrerequisiteFieldError")) {
            var expected = source.getComponents().getSchemas().get(name);
            assertThat(schemas.path(name).path("properties").fieldNames()).toIterable()
                    .containsExactlyInAnyOrderElementsOf(expected.getProperties().keySet());
            assertThat(schemas.path(name).path("required")).extracting(JsonNode::asText)
                    .containsExactlyInAnyOrderElementsOf(expected.getRequired());
        }
        var sharedDetails = (io.swagger.v3.oas.models.media.Schema<?>) source.getComponents().getSchemas().get("ErrorResponse").getProperties().get("details");
        assertThat(sharedDetails.getItems()).isNotNull();
        var errorRef = runtime.path("paths").path("/api/admin/topics/{id}/prerequisites").path("put")
                .path("responses").path("422").path("content").path("application/json").path("schema").path("$ref").asText();
        assertThat(errorRef).isEqualTo("#/components/schemas/TopicPrerequisiteError");
        var variants = schemas.path("TopicPrerequisiteError").path("properties").path("details").path("oneOf");
        assertThat(variants).hasSize(2);
        assertThat(variants.get(0).path("$ref").asText()).isEqualTo("#/components/schemas/TopicPrerequisiteCycleDetails");
        assertThat(variants.get(1).path("type").asText()).isEqualTo("array");
        assertThat(variants.get(1).path("items").path("$ref").asText()).isEqualTo("#/components/schemas/TopicPrerequisiteFieldError");
        assertThat(schemas.path("TopicPrerequisiteCycleDetails").path("properties").path("path").path("items").path("format").asText()).isEqualTo("int64");
    }

    private JsonNode runtime() throws Exception {
        return mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }
    private io.swagger.v3.oas.models.OpenAPI contract() throws Exception {
        var parsed = new OpenAPIV3Parser().readContents(Files.readString(Path.of("specs/023-content-admin-settings/contracts/openapi-content-admin.yaml")));
        assertThat(parsed.getMessages()).isEmpty();
        return parsed.getOpenAPI();
    }
}
