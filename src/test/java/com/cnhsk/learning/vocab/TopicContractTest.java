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
                "/api/topics", "/api/topics/{id}", "/api/topics/{id}/study-sessions", "/api/topics/{id}/practice-sessions",
                "/api/topics/{id}/tests");
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
    void FR019_startStudyRuntimeResponsesAndBodiesMatchApprovedContract() throws Exception {
        String path = "/api/topics/{id}/study-sessions";
        var source = contract().getPaths().get(path).getPost();
        JsonNode runtime = mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode documented = runtime.path("paths").path(path).path("post");
        assertThat(documented.path("operationId").asText()).isEqualTo(source.getOperationId());
        assertThat(documented.path("responses").fieldNames()).toIterable()
                .containsExactlyInAnyOrderElementsOf(source.getResponses().keySet());
        JsonNode created = documented.path("responses").path("201").path("content").path("*/*").path("schema");
        assertThat(created.path("$ref").asText()).isEqualTo("#/components/schemas/SessionView");
        JsonNode ok = documented.path("responses").path("200").path("content").path("*/*").path("schema").path("oneOf");
        assertThat(ok).hasSize(2);
        assertThat(runtime.path("components").path("schemas").path("NoNewWords").path("properties").fieldNames())
                .toIterable().containsExactlyInAnyOrder("session", "reason");
        JsonNode id = documented.path("parameters").get(0).path("schema");
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

    @Test
    void FR025_practicePostMatchesApprovedRequestAndResponses() throws Exception {
        String path = "/api/topics/{id}/practice-sessions";
        var source = contract().getPaths().get(path).getPost();
        JsonNode runtime = mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode operation = runtime.path("paths").path(path).path("post");
        assertThat(operation.path("operationId").asText()).isEqualTo(source.getOperationId());
        assertThat(operation.path("responses").fieldNames()).toIterable()
                .containsExactlyInAnyOrderElementsOf(source.getResponses().keySet());
        assertThat(operation.path("responses").path("201").path("content").path("application/json")
                .path("schema").path("$ref").asText()).isEqualTo("#/components/schemas/SessionView");
        assertThat(operation.path("requestBody").path("required").asBoolean()).isTrue();
        JsonNode request = runtime.path("components").path("schemas").path("TopicPracticeRequest");
        assertThat(request.path("properties").fieldNames()).toIterable().containsExactlyInAnyOrder("mode", "focusLexemeIds");
        assertThat(request.path("required")).extracting(JsonNode::asText).containsExactly("mode");
        Map<?, ?> sourceProperties = source.getRequestBody().getContent().get("application/json").getSchema().getProperties();
        Schema<?> mode = (Schema<?>) sourceProperties.get("mode");
        assertThat(request.path("properties").path("mode").path("enum")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrderElementsOf(mode.getEnum().stream().map(Object::toString).toList());
        JsonNode focus = request.path("properties").path("focusLexemeIds");
        assertThat(focus.path("maxItems").asInt()).isEqualTo(((Schema<?>) sourceProperties.get("focusLexemeIds")).getMaxItems());
        assertThat(focus.path("items").path("type").asText()).isEqualTo("integer");
        assertThat(focus.path("items").path("format").asText()).isEqualTo("int64");
    }

    @Test
    void FR031_testPostMatchesApprovedResponsesAndSessionSchema() throws Exception {
        String path = "/api/topics/{id}/tests";
        var source = contract().getPaths().get(path).getPost();
        JsonNode runtime = mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode operation = runtime.path("paths").path(path).path("post");
        assertThat(operation.path("operationId").asText()).isEqualTo(source.getOperationId());
        assertThat(operation.path("responses").fieldNames()).toIterable()
                .containsExactlyInAnyOrderElementsOf(source.getResponses().keySet());
        for (String status : Set.of("200", "201")) {
            assertThat(operation.path("responses").path(status).path("content").path("application/json")
                    .path("schema").path("$ref").asText()).isEqualTo("#/components/schemas/SessionView");
        }
        assertThat(operation.path("requestBody").isMissingNode()).isTrue();
        JsonNode id = operation.path("parameters").get(0).path("schema");
        assertThat(id.path("type").asText()).isEqualTo("integer");
        assertThat(id.path("format").asText()).isEqualTo("int64");
    }

    private static OpenAPI contract() throws Exception {
        var parsed = new OpenAPIV3Parser().readContents(Files.readString(Path.of(
                "specs", "003-vocab-topics", "contracts", "openapi-practice-vocab.yaml")));
        assertThat(parsed.getMessages()).isEmpty();
        assertThat(parsed.getOpenAPI()).isNotNull();
        return parsed.getOpenAPI();
    }
}
