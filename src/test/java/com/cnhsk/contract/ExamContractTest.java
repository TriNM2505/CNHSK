package com.cnhsk.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.oas.models.media.Schema;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
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

        var expectedPaths = new TreeSet<>(paths.keySet());
        assertThat(expectedPaths).hasSize(9);
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
    void AT29_weakPointsAndDeferredExamSchemasMatchContract() throws Exception {
        var path = Path.of("specs", "012-mock-exam-placement", "contracts", "openapi-exam.yaml");
        var parsed = new OpenAPIV3Parser().readLocation(path.toUri().toString(), null, null);
        assertThat(parsed.getMessages()).isEmpty();
        var expected = parsed.getOpenAPI();
        var response = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        JsonNode runtime = mapper.readTree(response.getResponse().getContentAsByteArray());
        JsonNode paths = runtime.path("paths");
        String weakPath = "/api/exam-attempts/{id}/weak-points";
        JsonNode weak = paths.path(weakPath).path("get");
        var id = expected.getComponents().getParameters().get("SessionId").getSchema();
        assertPathId(weak, "id", schemaTypes(id), id.getFormat());
        var expectedList = expected.getPaths().get(weakPath).getGet().getResponses().get("200")
                .getContent().get("application/json").getSchema();
        JsonNode array = schema(weak.path("responses").path("200"));
        assertThat(types(array)).containsExactly("array");
        assertThat(array.path("maxItems").asInt()).isEqualTo(expectedList.getMaxItems());
        JsonNode point = resolve(runtime, array.path("items"));
        Schema<?> expectedPoint = expected.getComponents().getSchemas().get("WeakPoint");
        assertThat(texts(point.path("required"))).containsExactlyInAnyOrderElementsOf(expectedPoint.getRequired());
        var properties = point.path("properties");
        assertThat(properties.fieldNames()).toIterable().containsExactlyInAnyOrderElementsOf(expectedPoint.getProperties().keySet());
        for (String field : List.of("kid", "nameVi", "mastery", "state")) {
            var fieldSchema = expectedPoint.getProperties().get(field);
            assertThat(types(properties.path(field))).isEqualTo(schemaTypes(fieldSchema));
        }
        var mastery = expectedPoint.getProperties().get("mastery");
        assertThat(properties.path("mastery").path("minimum").decimalValue()).isEqualByComparingTo(mastery.getMinimum());
        assertThat(properties.path("mastery").path("maximum").decimalValue()).isEqualByComparingTo(mastery.getMaximum());
        assertThat(properties.path("mastery").has("format")).isFalse();
        for (String code : List.of("401", "403", "409")) {
            JsonNode error = resolve(runtime, schema(weak.path("responses").path(code)));
            assertThat(texts(error.path("required"))).containsExactlyInAnyOrder("error_code", "message", "request_id");
        }

        var runId = expected.getComponents().getParameters().get("RunId").getSchema();
        assertPathId(paths.path("/api/placement/runs/{runId}/next").path("post"), "runId",
                schemaTypes(runId), runId.getFormat());
        assertPathId(paths.path("/api/placement/runs/{runId}/result").path("get"), "runId",
                schemaTypes(runId), runId.getFormat());
        JsonNode placement = paths.path("/api/placement/runs").path("post").path("requestBody");
        assertThat(placement.path("required").asBoolean()).isFalse();
        JsonNode placementBody = resolve(runtime, schema(placement));
        assertThat(types(placementBody)).containsExactly("object");
        assertThat(placementBody.path("properties").fieldNames()).toIterable().containsExactly("startLevel");
        assertThat(texts(placementBody.path("required"))).isEmpty();
        JsonNode level = placementBody.path("properties").path("startLevel");
        Schema<?> expectedPlacement = expected.getComponents().getSchemas().get("PlacementStartRequest");
        var expectedLevel = expectedPlacement.getProperties().get("startLevel");
        assertThat(types(level)).isEqualTo(schemaTypes(expectedLevel));
        assertThat(level.path("minimum").decimalValue()).isEqualByComparingTo(expectedLevel.getMinimum());
        assertThat(level.path("maximum").decimalValue()).isEqualByComparingTo(expectedLevel.getMaximum());
        assertThat(level.has("format")).isFalse();

        JsonNode targeted = paths.path("/api/targeted-practice/sessions").path("post").path("requestBody");
        assertThat(targeted.path("required").asBoolean()).isTrue();
        JsonNode targetedBody = resolve(runtime, schema(targeted));
        assertThat(types(targetedBody)).containsExactly("object");
        Schema<?> expectedTargeted = expected.getComponents().getSchemas().get("TargetedPracticeRequest");
        assertThat(texts(targetedBody.path("required"))).containsExactlyInAnyOrderElementsOf(expectedTargeted.getRequired());
        JsonNode targetFields = targetedBody.path("properties");
        assertThat(targetFields.fieldNames()).toIterable().containsExactlyInAnyOrderElementsOf(expectedTargeted.getProperties().keySet());
        var kid = expectedTargeted.getProperties().get("kid");
        assertThat(types(targetFields.path("kid"))).isEqualTo(schemaTypes(kid));
        assertThat(targetFields.path("kid").path("pattern").asText()).isEqualTo(kid.getPattern());
        var expectedTypes = expected.getComponents().getSchemas().get("QuestionType");
        JsonNode qtype = resolve(runtime, targetFields.path("qtype"));
        assertThat(types(qtype)).isEqualTo(schemaTypes(expectedTypes));
        assertThat(texts(qtype.path("enum"))).containsExactlyInAnyOrderElementsOf(expectedTypes.getEnum());
        JsonNode exclude = targetFields.path("excludeQuestionId");
        var expectedExclude = expectedTargeted.getProperties().get("excludeQuestionId");
        assertThat(types(exclude)).isEqualTo(schemaTypes(expectedExclude));
        assertThat(exclude.path("format").asText()).isEqualTo(expectedExclude.getFormat());
    }

    private static JsonNode schema(JsonNode container) {
        return container.path("content").path("application/json").path("schema");
    }

    private static JsonNode resolve(JsonNode document, JsonNode schema) {
        return schema.has("$ref") ? document.at(schema.path("$ref").asText().substring(1)) : schema;
    }

    private static Set<String> texts(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).collect(Collectors.toSet());
    }

    private static Set<String> types(JsonNode schema) {
        JsonNode type = schema.path("type");
        return type.isTextual() ? Set.of(type.asText()) : texts(type);
    }

    private static Set<String> schemaTypes(io.swagger.v3.oas.models.media.Schema<?> schema) {
        return schema.getTypes() == null ? Set.of(schema.getType()) : schema.getTypes();
    }

    private static void assertPathId(JsonNode operation, String name, Set<String> type, String format) {
        var parameter = StreamSupport.stream(operation.path("parameters").spliterator(), false)
                .filter(node -> name.equals(node.path("name").asText())).findFirst().orElseThrow();
        assertThat(parameter.path("in").asText()).isEqualTo("path");
        assertThat(parameter.path("required").asBoolean()).isTrue();
        assertThat(types(parameter.path("schema"))).isEqualTo(type);
        assertThat(parameter.path("schema").path("format").asText()).isEqualTo(format);
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
