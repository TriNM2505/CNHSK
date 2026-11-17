package com.cnhsk.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cnhsk.learning.ai.ClaudeClient;
import com.cnhsk.learning.ai.StubClaudeClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
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
        "springdoc.packages-to-scan=com.cnhsk.learning.aigen.web"})
class AiPracticeContractTest {
    private static final Path ORIGINAL = Path.of("specs", "024-ai-practice-generation", "contracts",
            "openapi-ai-practice.yaml");
    private static final Path APPENDIX = ORIGINAL.resolveSibling("openapi-ai-practice-v1-1.yaml");
    private static final String REVIEW = "/api/admin/ai-questions";
    private static final String RUNS = "/api/admin/ai-generation-runs";
    private static final String REVIEW_CONTROLLER = "com.cnhsk.learning.aigen.web.AiReviewController";
    private static final String RUN_CONTROLLER = "com.cnhsk.learning.aigen.web.AiRunController";
    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace");
    private static final Map<String, Set<String>> STATUSES = Map.of(
            "get " + REVIEW, Set.of("200", "401", "403", "422"),
            "post " + REVIEW + "/{id}/approve", Set.of("200", "401", "403", "404", "409", "422"),
            "post " + REVIEW + "/{id}/reject", Set.of("200", "401", "403", "404", "409", "422"),
            "post " + REVIEW + "/bulk-approve", Set.of("200", "401", "403", "422", "500"),
            "get " + RUNS, Set.of("200", "401", "403", "422"),
            "post " + RUNS + "/trigger", Set.of("202", "401", "403", "409"));
    private static final Set<String> FACETS = Set.of("format", "minimum", "maximum", "exclusiveMinimum",
            "exclusiveMaximum", "multipleOf", "minLength", "maxLength", "pattern", "minItems", "maxItems",
            "uniqueItems", "minProperties", "maxProperties", "default", "const", "additionalProperties");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private ClaudeClient claude;
    @Autowired private StubClaudeClient stub;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlers;
    private int callsBefore;

    @BeforeEach
    void recordProviderCalls() {
        assertThat(claude).isSameAs(stub);
        callsBefore = stub.callCount();
    }

    @AfterEach
    void documentationInspectionNeverCallsClaude() {
        assertThat(stub.callCount()).isEqualTo(callsBefore);
    }

    @Test
    void sourceContractsParseAndSelectExactlyFourReviewAndTwoRunOperations() throws Exception {
        for (Path path : List.of(ORIGINAL, APPENDIX)) {
            var parsed = new OpenAPIV3Parser().readLocation(path.toAbsolutePath().toUri().toString(), null, null);
            assertThat(parsed.getMessages()).as("Parser messages for %s", path).isEmpty();
            assertThat(parsed.getOpenAPI()).isNotNull();
            assertThat(parsed.getOpenAPI().getOpenapi()).startsWith("3.1.");
        }
        JsonNode original = document(ORIGINAL);
        JsonNode appendix = document(APPENDIX);
        assertThat(operations(original).keySet()).isEqualTo(STATUSES.keySet());
        assertThat(operations(appendix).keySet()).containsExactlyInAnyOrder("get " + REVIEW,
                "post " + REVIEW + "/{id}/approve", "post " + REVIEW + "/{id}/reject", "post " + REVIEW + "/bulk-approve");
        assertThat(fields(original.path("paths").path(REVIEW + "/bulk-approve").path("post").path("responses")))
                .containsExactlyInAnyOrder("200", "401", "403", "422");
        Map<String, ContractOperation> canonical = canonical(original, appendix);
        assertThat(canonical.keySet()).isEqualTo(STATUSES.keySet());
        Set<String> ids = new HashSet<>();
        canonical.forEach((key, operation) -> {
            assertThat(operation.document()).isSameAs(key.contains(REVIEW) ? appendix : original);
            assertThat(fields(operation.operation().path("responses"))).as(key).isEqualTo(STATUSES.get(key));
            assertThat(operation.operation().path("operationId").isTextual()).as(key).isTrue();
            assertThat(ids.add(operation.operation().path("operationId").textValue())).as("Unique source operationId: %s", key).isTrue();
        });
    }

    @Test
    void registeredHandlersHaveExactlyTheSixCanonicalMethodsPathsAndOwners() {
        Map<String, String> registered = new LinkedHashMap<>();
        handlers.getHandlerMethods().forEach((mapping, handler) -> {
            String controller = handler.getBeanType().getName();
            boolean owned = Set.of(REVIEW_CONTROLLER, RUN_CONTROLLER).contains(controller);
            for (String path : mapping.getPatternValues()) {
                if (!owned && !inNamespace(path)) continue;
                assertThat(inNamespace(path)).as("Controller escaped its namespace: %s %s", controller, path).isTrue();
                assertThat(controller).as("Namespace owner for %s", path)
                        .isEqualTo(under(path, REVIEW) ? REVIEW_CONTROLLER : RUN_CONTROLLER);
                assertThat(mapping.getMethodsCondition().getMethods()).as("Explicit HTTP methods for %s", path).isNotEmpty();
                mapping.getMethodsCondition().getMethods().forEach(method -> {
                    String key = method.name().toLowerCase(Locale.ROOT) + " " + path;
                    assertThat(registered.put(key, controller)).as("Duplicate registered operation %s", key).isNull();
                });
            }
        });
        assertThat(registered.keySet()).containsExactlyInAnyOrderElementsOf(STATUSES.keySet());
    }

    @Test
    void actualApiDocsHaveExactlyTheCanonicalOperationsIdsAndResponseStatuses() throws Exception {
        Map<String, ContractOperation> canonical = canonical(document(ORIGINAL), document(APPENDIX));
        Map<String, JsonNode> runtime = operations(runtime());
        assertThat(runtime.keySet()).containsExactlyInAnyOrderElementsOf(canonical.keySet());
        Set<String> ids = new HashSet<>();
        assertAll(canonical.entrySet().stream().map(entry -> (Executable) () -> {
            String key = entry.getKey();
            JsonNode actual = runtime.get(key);
            assertThat(fields(actual.path("responses"))).as("Runtime response statuses: %s", key).isEqualTo(STATUSES.get(key));
            assertThat(actual.path("operationId")).as("Runtime operationId: %s", key)
                    .isEqualTo(entry.getValue().operation().path("operationId"));
            assertThat(ids.add(actual.path("operationId").asText())).as("Unique runtime operationId: %s", key).isTrue();
        }));
    }

    @Test
    void actualApiDocsMatchParametersBodiesAndSuccessfulResponseSchemasWithoutHiddenFields() throws Exception {
        JsonNode runtime = runtime();
        Map<String, JsonNode> actualOperations = operations(runtime);
        Map<String, ContractOperation> canonical = canonical(document(ORIGINAL), document(APPENDIX));
        List<Executable> checks = new ArrayList<>();
        canonical.forEach((key, expected) -> checks.add(() -> SoftAssertions.assertSoftly(softly -> {
            JsonNode actual = actualOperations.get(key);
            softly.assertThat(actual).as("Runtime operation %s", key).isNotNull();
            if (actual == null) return;
            Map<String, JsonNode> left = parameters(expected.document(), expected.operation());
            Map<String, JsonNode> right = parameters(runtime, actual);
            softly.assertThat(right.keySet()).as("%s parameter identities (in,name)", key).isEqualTo(left.keySet());
            left.forEach((identity, parameter) -> {
                JsonNode counterpart = right.get(identity);
                if (counterpart == null) return;
                softly.assertThat(counterpart.path("required").asBoolean()).as("%s %s required", key, identity)
                        .isEqualTo(parameter.path("required").asBoolean());
                assertSchema(softly, expected.document(), parameter.path("schema"), runtime, counterpart.path("schema"), key + " parameter " + identity);
            });
            JsonNode body = resolve(expected.document(), expected.operation().path("requestBody"));
            JsonNode actualBody = resolve(runtime, actual.path("requestBody"));
            softly.assertThat(actualBody.isMissingNode()).as("%s request body presence", key).isEqualTo(body.isMissingNode());
            if (!body.isMissingNode() && !actualBody.isMissingNode()) {
                softly.assertThat(actualBody.path("required").asBoolean()).as("%s request body required", key)
                        .isEqualTo(body.path("required").asBoolean());
                assertContent(softly, expected.document(), body, runtime, actualBody, key + " request body");
            }
            expected.operation().path("responses").fields().forEachRemaining(response -> {
                if (response.getKey().matches("2\\d{2}")) {
                    assertContent(softly, expected.document(), resolve(expected.document(), response.getValue()), runtime,
                            resolve(runtime, actual.path("responses").path(response.getKey())),
                            key + " response " + response.getKey());
                }
            });
        })));
        assertAll(checks);
    }

    private static void assertContent(SoftAssertions softly, JsonNode source, JsonNode expected,
            JsonNode runtime, JsonNode actual, String location) {
        softly.assertThat(fields(actual.path("content"))).as("%s media types", location).isEqualTo(fields(expected.path("content")));
        expected.path("content").fields().forEachRemaining(media -> assertSchema(softly, source, media.getValue().path("schema"),
                runtime, actual.path("content").path(media.getKey()).path("schema"), location + " " + media.getKey()));
    }

    private static void assertSchema(SoftAssertions softly, JsonNode source, JsonNode expectedReference,
            JsonNode runtime, JsonNode actualReference, String location) {
        JsonNode expected = resolve(source, expectedReference);
        JsonNode actual = resolve(runtime, actualReference);
        softly.assertThat(actual.isObject()).as("%s schema (expected %s, actual %s)", location, expected, actual).isTrue();
        if (!actual.isObject()) return;
        softly.assertThat(types(actual)).as("%s types/nullability", location).isEqualTo(types(expected));
        for (String facet : FACETS) {
            if (!expected.has(facet)) continue;
            JsonNode left = expected.path(facet);
            JsonNode right = actual.path(facet);
            if (left.isNumber() && right.isNumber()) {
                softly.assertThat(right.decimalValue()).as("%s %s", location, facet).isEqualByComparingTo(left.decimalValue());
            } else {
                softly.assertThat(right).as("%s %s", location, facet).isEqualTo(left);
            }
        }
        if (expected.has("enum")) {
            Set<JsonNode> expectedEnum = values(expected.path("enum"));
            Set<JsonNode> actualEnum = values(actual.path("enum"));
            if (types(expected).contains("null") && types(actual).contains("null")) {
                expectedEnum.removeIf(JsonNode::isNull);
                actualEnum.removeIf(JsonNode::isNull);
            }
            softly.assertThat(actualEnum).as("%s enum", location).isEqualTo(expectedEnum);
        }
        softly.assertThat(values(actual.path("required"))).as("%s required fields", location).isEqualTo(values(expected.path("required")));
        softly.assertThat(fields(actual.path("properties"))).as("%s properties/privacy allowlist", location).isEqualTo(fields(expected.path("properties")));
        expected.path("properties").fields().forEachRemaining(property -> assertSchema(softly, source, property.getValue(),
                runtime, actual.path("properties").path(property.getKey()), location + "." + property.getKey()));
        softly.assertThat(actual.has("items")).as("%s array items presence", location).isEqualTo(expected.has("items"));
        if (expected.has("items")) assertSchema(softly, source, expected.path("items"), runtime, actual.path("items"), location + "[]");
        for (String composition : Set.of("allOf", "anyOf", "oneOf", "not")) {
            softly.assertThat(actual.path(composition)).as("%s unsupported composition %s", location, composition)
                    .isEqualTo(expected.path(composition));
        }
    }

    private static Set<String> types(JsonNode schema) {
        Set<String> result = new TreeSet<>();
        JsonNode type = schema.path("type");
        if (type.isArray()) type.forEach(value -> result.add(value.textValue()));
        else if (type.isTextual()) result.add(type.textValue());
        if (schema.path("nullable").asBoolean()) result.add("null");
        return result;
    }

    private static JsonNode resolve(JsonNode document, JsonNode value) {
        Set<String> visited = new HashSet<>();
        while (value.has("$ref")) {
            String reference = value.path("$ref").asText();
            assertThat(reference).startsWith("#/");
            assertThat(visited.add(reference)).as("Cyclic reference %s", reference).isTrue();
            value = document.at(reference.substring(1));
            assertThat(value.isMissingNode()).as("Unresolved reference %s", reference).isFalse();
        }
        return value;
    }

    private static Map<String, JsonNode> parameters(JsonNode document, JsonNode operation) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        operation.path("parameters").forEach(reference -> {
            JsonNode parameter = resolve(document, reference);
            String identity = parameter.path("in").asText() + " " + parameter.path("name").asText();
            assertThat(result.put(identity, parameter)).as("Duplicate parameter %s", identity).isNull();
        });
        return result;
    }

    private static Map<String, ContractOperation> canonical(JsonNode original, JsonNode appendix) {
        Map<String, ContractOperation> result = new LinkedHashMap<>();
        for (String key : STATUSES.keySet()) {
            JsonNode source = key.contains(REVIEW) ? appendix : original;
            String[] parts = key.split(" ", 2);
            JsonNode operation = source.path("paths").path(parts[1]).path(parts[0]);
            assertThat(operation.isObject()).as("Canonical operation %s", key).isTrue();
            result.put(key, new ContractOperation(source, operation));
        }
        return result;
    }

    private static Map<String, JsonNode> operations(JsonNode document) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        document.path("paths").fields().forEachRemaining(path -> {
            if (!inNamespace(path.getKey())) return;
            path.getValue().fields().forEachRemaining(method -> {
                if (METHODS.contains(method.getKey())) {
                    assertThat(result.put(method.getKey() + " " + path.getKey(), method.getValue())).isNull();
                }
            });
        });
        return result;
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

    private static Set<JsonNode> values(JsonNode node) {
        Set<JsonNode> result = new HashSet<>();
        node.forEach(result::add);
        return result;
    }

    private static boolean under(String path, String root) { return path.equals(root) || path.startsWith(root + "/"); }
    private static boolean inNamespace(String path) { return under(path, REVIEW) || under(path, RUNS); }
    private record ContractOperation(JsonNode document, JsonNode operation) { }
}
