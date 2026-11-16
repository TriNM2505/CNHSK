package com.cnhsk.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true",
        "springdoc.packages-to-scan=com.cnhsk.learning.aigen.web,com.cnhsk.learning.knowledge.web"})
class AiReviewContractTest {
    private static final Path APPENDIX = Path.of("specs", "024-ai-practice-generation", "contracts",
            "openapi-ai-practice-v1-1.yaml");
    private static final Path ORIGINAL = APPENDIX.resolveSibling("openapi-ai-practice.yaml");
    private static final Path KNOWLEDGE = Path.of("specs", "010-knowledge-tree", "contracts", "openapi-knowledge.yaml");
    private static final String ROOT = "/api/admin/ai-questions";
    private static final String CONTROLLER = "com.cnhsk.learning.aigen.web.AiReviewController";
    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlers;

    @Test
    void appendixParsesAndPreservesOriginalReviewContractsWithApprovedBulkFailureResponse() throws Exception {
        var parsed = new OpenAPIV3Parser().readLocation(APPENDIX.toAbsolutePath().toUri().toString(), null, null);
        assertThat(parsed.getMessages()).isEmpty();
        assertThat(parsed.getOpenAPI()).isNotNull();
        JsonNode source = document(APPENDIX);
        JsonNode original = document(ORIGINAL);
        assertThat(fields(source.path("paths"))).containsExactlyInAnyOrder(ROOT, ROOT + "/{id}/approve",
                ROOT + "/{id}/reject", ROOT + "/bulk-approve");
        source.path("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(method -> {
            if (!METHODS.contains(method.getKey())) return;
            JsonNode old = original.path("paths").path(path.getKey()).path(method.getKey());
            assertThat(method.getValue().path("operationId")).isEqualTo(old.path("operationId"));
            Set<String> statuses = fields(old.path("responses"));
            if (path.getKey().equals(ROOT + "/bulk-approve")) statuses.add("500");
            assertThat(fields(method.getValue().path("responses"))).isEqualTo(statuses);
            assertThat(method.getValue().path("parameters")).isEqualTo(old.path("parameters"));
            assertThat(method.getValue().path("requestBody")).isEqualTo(old.path("requestBody"));
        }));
        assertThat(source.path("info").path("x-existing-operations")).hasSize(2);
        assertThat(source.path("info").path("description").asText()).contains("24 hours", "CONTENT_EDITOR", "403");
    }

    @Test
    void reviewPathsMethodsAndStatusesMatchRuntimeAndRegisteredMappingsBothWays() throws Exception {
        JsonNode source = document(APPENDIX);
        JsonNode runtime = runtime();
        assertThat(operations(runtime)).containsExactlyInAnyOrderEntriesOf(operations(source));
        Map<String, Set<String>> registered = new LinkedHashMap<>();
        handlers.getHandlerMethods().forEach((mapping, handler) -> {
            boolean owned = handler.getBeanType().getName().equals(CONTROLLER);
            for (String path : mapping.getPatternValues()) {
                if (!owned && !inNamespace(path)) continue;
                assertThat(owned).as("Review namespace owner for %s", path).isTrue();
                assertThat(inNamespace(path)).as("Review controller mapping %s", path).isTrue();
                assertThat(mapping.getMethodsCondition().getMethods()).isNotEmpty();
                mapping.getMethodsCondition().getMethods().forEach(method -> {
                    String operation = method.name().toLowerCase(java.util.Locale.ROOT) + " " + path;
                    assertThat(registered.put(operation, Set.of())).as("Duplicate mapping %s", operation).isNull();
                });
            }
        });
        assertThat(registered.keySet()).containsExactlyInAnyOrderElementsOf(operations(source).keySet());
    }

    @Test
    void reviewOperationIdsParametersRequestsAndSuccessSchemasMatchAppendix() throws Exception {
        JsonNode source = document(APPENDIX);
        JsonNode runtime = runtime();
        for (String operation : operations(source).keySet()) {
            String[] key = operation.split(" ", 2);
            JsonNode expected = source.path("paths").path(key[1]).path(key[0]);
            JsonNode actual = runtime.path("paths").path(key[1]).path(key[0]);
            assertThat(actual.path("operationId")).isEqualTo(expected.path("operationId"));
            assertParameters(source, expected, runtime, actual);
            assertSchema(source, expected.path("responses").path("200").path("content").path("application/json").path("schema"),
                    runtime, actual.path("responses").path("200").path("content").path("application/json").path("schema"));
            if (expected.has("requestBody")) {
                assertThat(actual.path("requestBody").path("required").asBoolean()).isTrue();
                assertSchema(source, expected.path("requestBody").path("content").path("application/json").path("schema"),
                        runtime, actual.path("requestBody").path("content").path("application/json").path("schema"));
            } else {
                assertThat(actual.has("requestBody")).isFalse();
            }
        }
    }

    @Test
    void detailAndUpdateKeepOriginalKnowledgeParametersSchemasAndResponsesWithoutExposingCaller() throws Exception {
        JsonNode source = document(KNOWLEDGE);
        JsonNode runtime = runtime();
        String path = "/api/admin/questions/{id}";
        for (String method : Set.of("get", "put")) {
            JsonNode expected = source.path("paths").path(path).path(method);
            JsonNode actual = runtime.path("paths").path(path).path(method);
            assertThat(actual.path("operationId")).isEqualTo(expected.path("operationId"));
            assertThat(fields(actual.path("responses"))).isEqualTo(fields(expected.path("responses")));
            assertParameters(source, expected, runtime, actual);
            assertThat(actual.path("responses").path("200").path("content").path("application/json").path("schema").path("$ref").asText())
                    .isEqualTo("#/components/schemas/QuestionDetail");
        }
        JsonNode expected = source.at("/components/schemas/QuestionUpdate");
        JsonNode actual = resolve(runtime, runtime.path("paths").path(path).path("put")
                .path("requestBody").path("content").path("application/json").path("schema"));
        assertThat(fields(actual.path("properties"))).isEqualTo(fields(expected.path("properties")));
        assertThat(values(actual.path("required"))).isEqualTo(values(expected.path("required")));
        assertThat(fields(runtime.at("/components/schemas/QuestionDetail/properties")))
                .isEqualTo(fields(source.at("/components/schemas/QuestionDetail/properties")));
    }

    private static void assertParameters(JsonNode source, JsonNode expected, JsonNode runtime, JsonNode actual) {
        Map<String, JsonNode> left = parameters(source, expected);
        Map<String, JsonNode> right = parameters(runtime, actual);
        assertThat(right.keySet()).containsExactlyInAnyOrderElementsOf(left.keySet());
        left.forEach((name, parameter) -> {
            JsonNode counterpart = right.get(name);
            assertThat(counterpart.path("in")).isEqualTo(parameter.path("in"));
            assertThat(counterpart.path("required").asBoolean()).isEqualTo(parameter.path("required").asBoolean());
            assertSchema(source, parameter.path("schema"), runtime, counterpart.path("schema"));
        });
    }

    private static Map<String, JsonNode> parameters(JsonNode document, JsonNode operation) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        operation.path("parameters").forEach(value -> {
            JsonNode parameter = resolve(document, value);
            assertThat(result.put(parameter.path("name").asText(), parameter)).isNull();
        });
        return result;
    }

    private static void assertSchema(JsonNode source, JsonNode expected, JsonNode runtime, JsonNode actual) {
        expected = resolve(source, expected);
        actual = resolve(runtime, actual);
        assertThat(actual.isMissingNode()).as("Missing schema for %s", expected).isFalse();
        if (expected.path("type").isArray()) {
            Set<String> actualTypes = actual.path("type").isArray() ? values(actual.path("type"))
                    : new TreeSet<>(Set.of(actual.path("type").asText()));
            if (actual.path("nullable").asBoolean()) actualTypes.add("null");
            assertThat(actualTypes).isEqualTo(values(expected.path("type")));
        } else if (expected.has("type")) {
            assertThat(actual.path("type")).isEqualTo(expected.path("type"));
        }
        for (String facet : Set.of("format", "minimum", "maximum", "minLength", "maxLength", "minItems", "maxItems", "uniqueItems", "default")) {
            if (expected.has(facet)) {
                if (expected.path(facet).isNumber()) {
                    assertThat(actual.path(facet).isNumber()).as("Schema facet %s", facet).isTrue();
                    assertThat(actual.path(facet).decimalValue()).isEqualByComparingTo(expected.path(facet).decimalValue());
                } else assertThat(actual.path(facet)).as("Schema facet %s", facet).isEqualTo(expected.path(facet));
            }
        }
        if (expected.has("enum")) assertThat(values(actual.path("enum"))).isEqualTo(values(expected.path("enum")));
        if (expected.has("required")) assertThat(values(actual.path("required"))).isEqualTo(values(expected.path("required")));
        if (expected.has("properties")) {
            assertThat(fields(actual.path("properties"))).isEqualTo(fields(expected.path("properties")));
            for (String property : fields(expected.path("properties"))) {
                assertSchema(source, expected.path("properties").path(property), runtime, actual.path("properties").path(property));
            }
        }
        if (expected.has("items")) assertSchema(source, expected.path("items"), runtime, actual.path("items"));
    }

    private static JsonNode resolve(JsonNode document, JsonNode value) {
        return value.has("$ref") ? document.at(value.path("$ref").asText().substring(1)) : value;
    }

    private JsonNode runtime() throws Exception {
        return mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
    }

    private static JsonNode document(Path path) throws Exception {
        return new ObjectMapper(new YAMLFactory()).readTree(Files.readString(path));
    }

    private static Set<String> fields(JsonNode node) {
        Set<String> result = new TreeSet<>();
        node.fieldNames().forEachRemaining(result::add);
        return result;
    }

    private static Set<String> values(JsonNode node) {
        Set<String> result = new TreeSet<>();
        node.forEach(value -> result.add(value.asText()));
        return result;
    }

    private static boolean inNamespace(String path) { return path.equals(ROOT) || path.startsWith(ROOT + "/"); }

    private static Map<String, Set<String>> operations(JsonNode document) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        document.path("paths").fields().forEachRemaining(path -> {
            if (!inNamespace(path.getKey())) return;
            path.getValue().fields().forEachRemaining(method -> {
                if (METHODS.contains(method.getKey())) result.put(method.getKey() + " " + path.getKey(),
                        fields(method.getValue().path("responses")));
            });
        });
        return result;
    }
}
