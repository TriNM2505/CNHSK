package com.cnhsk.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cnhsk.learning.ai.web.AiUsageController;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true",
        "springdoc.packages-to-scan=com.cnhsk.learning.ai.web"})
class ContentAdminUsageContractTest extends AbstractIntegrationTest {
    private static final Path CONTRACT = Path.of("specs", "023-content-admin-settings", "contracts",
            "openapi-content-admin-v1-1.yaml");
    private static final Path REPORT = Path.of("target", "content-admin-usage-contract-mismatches.json");
    private static final String ROOT = "/api/admin/ai-usage";
    private static final String PACKAGE = AiUsageController.class.getPackageName();
    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace");
    private static final Set<String> STATUSES = Set.of("200", "401", "403");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlers;

    @Test
    void appendixMatchesDocumentedAndRegisteredPathsMethodsAndStatusesBothWays() throws Exception {
        var parsed = new OpenAPIV3Parser().readLocation(CONTRACT.toAbsolutePath().toUri().toString(), null, null);
        assertThat(parsed.getMessages()).as("Appendix parser messages").isEmpty();
        assertThat(parsed.getOpenAPI()).isNotNull();
        JsonNode source = new ObjectMapper(new YAMLFactory()).readTree(Files.readString(CONTRACT));
        assertThat(fields(source.path("paths"))).containsExactly(ROOT);
        assertThat(operations(source)).containsOnlyKeys("GET " + ROOT);
        assertThat(operations(source).get("GET " + ROOT)).isEqualTo(STATUSES);
        JsonNode runtime = mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        List<String> mismatches = new ArrayList<>();
        compareDocuments(source, runtime, mismatches);
        Map<String, String> registered = new TreeMap<>();
        handlers.getHandlerMethods().forEach((mapping, handler) -> {
            String owner = ClassUtils.getUserClass(handler.getBeanType()).getName();
            register(owner, owned(owner), mapping.getPatternValues(), mapping.getMethodsCondition().getMethods(),
                    registered, mismatches);
        });
        compare("Registered operations", operations(source).keySet(), registered.keySet(), mismatches);
        if (!mismatches.isEmpty()) {
            Files.createDirectories(REPORT.getParent());
            Files.writeString(REPORT, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                    "contract", CONTRACT.toString(), "expected", operations(source), "documented", operations(runtime),
                    "registered", registered, "mismatches", mismatches)), StandardCharsets.UTF_8);
        }
        assertThat(mismatches).as("Appendix contract mismatches; report: %s", REPORT).isEmpty();
    }

    @Test
    void appendixRequiresExactlyTheFourFrontendFieldsAndTheNullableLongCounter() throws Exception {
        JsonNode source = new ObjectMapper(new YAMLFactory()).readTree(Files.readString(CONTRACT));
        JsonNode schema = source.at("/components/schemas/AiUsageResponse");
        assertThat(fields(schema.path("properties"))).containsExactlyInAnyOrder("month", "used", "budget", "available");
        Set<String> required = new TreeSet<>();
        schema.path("required").forEach(field -> required.add(field.asText()));
        assertThat(required).containsExactlyInAnyOrder("month", "used", "budget", "available");
        assertThat(schema.at("/properties/month/type").asText()).isEqualTo("string");
        assertThat(schema.at("/properties/month/pattern").asText()).isEqualTo("^\\d{4}-(0[1-9]|1[0-2])$");
        assertThat(schema.at("/properties/used/format").asText()).isEqualTo("int64");
        Set<String> types = new TreeSet<>();
        schema.at("/properties/used/type").forEach(type -> types.add(type.asText()));
        assertThat(types).containsExactlyInAnyOrder("integer", "null");
        assertThat(schema.at("/properties/budget/format").asText()).isEqualTo("int32");
        assertThat(schema.at("/properties/available/type").asText()).isEqualTo("boolean");
    }

    private static boolean inNamespace(String path) {
        return path.equals(ROOT) || path.startsWith(ROOT + "/");
    }

    private static boolean owned(String owner) {
        return owner.startsWith(PACKAGE + ".");
    }

    private static Set<String> fields(JsonNode node) {
        Set<String> result = new TreeSet<>();
        node.fieldNames().forEachRemaining(result::add);
        return result;
    }

    private static Set<String> paths(JsonNode document) {
        Set<String> result = fields(document.path("paths"));
        result.removeIf(path -> !inNamespace(path));
        return result;
    }

    private static Map<String, Set<String>> operations(JsonNode document) {
        Map<String, Set<String>> result = new TreeMap<>();
        document.path("paths").fields().forEachRemaining(path -> {
            if (!inNamespace(path.getKey())) return;
            path.getValue().fields().forEachRemaining(method -> {
                if (METHODS.contains(method.getKey())) result.put(method.getKey().toUpperCase(Locale.ROOT) + " " + path.getKey(),
                        fields(method.getValue().path("responses")));
            });
        });
        return result;
    }

    private static void compareDocuments(JsonNode expected, JsonNode actual, List<String> mismatches) {
        compare("Documented paths", paths(expected), paths(actual), mismatches);
        Map<String, Set<String>> left = operations(expected);
        Map<String, Set<String>> right = operations(actual);
        compare("Documented operations", left.keySet(), right.keySet(), mismatches);
        left.forEach((operation, statuses) -> {
            if (right.containsKey(operation)) compare(operation + " statuses", statuses, right.get(operation), mismatches);
        });
    }

    private static void compare(String label, Collection<String> expected, Collection<String> actual, List<String> mismatches) {
        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(actual);
        Set<String> extra = new TreeSet<>(actual);
        extra.removeAll(expected);
        if (!missing.isEmpty() || !extra.isEmpty()) mismatches.add(label + ": missing=" + missing + ", extra=" + extra);
    }

    private static void register(String owner, boolean owned, Collection<String> paths, Collection<RequestMethod> methods,
            Map<String, String> registered, List<String> mismatches) {
        for (String path : paths) {
            if (!owned && !inNamespace(path)) continue;
            if (!owned) mismatches.add("Unexpected controller in namespace: " + owner + " " + path);
            if (!inNamespace(path)) mismatches.add("Controller mapping outside namespace: " + owner + " " + path);
            if (methods.isEmpty()) mismatches.add("Mapping has no explicit HTTP method: " + owner + " " + path);
            for (RequestMethod method : methods) {
                String operation = method.name() + " " + path;
                String previous = registered.putIfAbsent(operation, owner);
                if (previous != null) mismatches.add("Duplicate mapping: " + operation + " owners=" + previous + ", " + owner);
            }
        }
    }

    @Test
    void inMemoryComparisonRejectsMissingOrExtraPathsAndMethods() throws Exception {
        JsonNode expected = document(ROOT, "get", "200");
        for (JsonNode actual : List.of(mapper.readTree("{\"paths\":{}}"), document(ROOT + "/unexpected", "get", "200"),
                document(ROOT, "post", "200"), mapper.readTree("""
                        {"paths":{"/api/admin/ai-usage":{"get":{"responses":{"200":{}}},"post":{"responses":{"201":{}}}}}}
                        """))) {
            List<String> mismatches = new ArrayList<>();
            compareDocuments(expected, actual, mismatches);
            assertThat(mismatches).isNotEmpty();
        }
    }

    @Test
    void inMemoryComparisonRejectsMissingExtraDefaultAndWildcardStatuses() throws Exception {
        JsonNode expected = document(ROOT, "get", "200", "401", "403");
        for (String[] statuses : List.of(new String[] {"200", "401"}, new String[] {"200", "401", "403", "500"},
                new String[] {"200", "401", "403", "default"}, new String[] {"200", "401", "403", "5XX"})) {
            List<String> mismatches = new ArrayList<>();
            compareDocuments(expected, document(ROOT, "get", statuses), mismatches);
            assertThat(mismatches).hasSize(1);
            assertThat(mismatches.get(0)).startsWith("GET " + ROOT + " statuses:");
        }
    }

    @Test
    void namespaceAndOwnerChecksExcludeSiblingPrefixesButIncludeDescendants() throws Exception {
        assertThat(inNamespace(ROOT)).isTrue();
        assertThat(inNamespace(ROOT + "/unexpected")).isTrue();
        for (String path : List.of(ROOT + "-extra", ROOT + "Extra/path", "/api/admin/settings", "/api/assistant/ask"))
            assertThat(inNamespace(path)).isFalse();
        assertThat(owned(AiUsageController.class.getName())).isTrue();
        assertThat(owned(PACKAGE + "Extra.OtherController")).isFalse();
        JsonNode expected = document(ROOT, "get", "200");
        var actual = (com.fasterxml.jackson.databind.node.ObjectNode) document(ROOT, "get", "200");
        ((com.fasterxml.jackson.databind.node.ObjectNode) actual.path("paths")).set(ROOT + "-extra", mapper.createObjectNode());
        ((com.fasterxml.jackson.databind.node.ObjectNode) actual.at("/paths/~1api~1admin~1ai-usage")).put("summary", "metadata");
        List<String> mismatches = new ArrayList<>();
        compareDocuments(expected, actual, mismatches);
        assertThat(mismatches).isEmpty();
    }

    @Test
    void registrationRejectsUnexpectedOwnersPathsUnrestrictedMethodsAndDuplicateMappings() {
        Map<String, String> registered = new TreeMap<>();
        List<String> mismatches = new ArrayList<>();
        register("other.Controller", false, Set.of(ROOT + "/unexpected"), Set.of(RequestMethod.GET), registered, mismatches);
        register("owned.Controller", true, Set.of("/api/unexpected"), Set.of(RequestMethod.POST), registered, mismatches);
        register("owned.Controller", true, Set.of(ROOT), Set.of(), registered, mismatches);
        register("duplicate.Controller", false, Set.of(ROOT + "/unexpected"), Set.of(RequestMethod.GET), registered, mismatches);
        register("ignored.Controller", false, Set.of(ROOT + "-extra"), Set.of(RequestMethod.GET), registered, mismatches);
        compare("Registered operations", Set.of("GET " + ROOT), registered.keySet(), mismatches);
        assertThat(mismatches).anyMatch(message -> message.startsWith("Controller mapping outside namespace:"))
                .anyMatch(message -> message.startsWith("Unexpected controller in namespace:"))
                .anyMatch(message -> message.startsWith("Mapping has no explicit HTTP method:"))
                .anyMatch(message -> message.startsWith("Duplicate mapping:"))
                .anyMatch(message -> message.startsWith("Registered operations:"));
        assertThat(registered).doesNotContainKey("GET " + ROOT + "-extra");
    }

    private JsonNode document(String path, String method, String... statuses) {
        var document = mapper.createObjectNode();
        var responses = document.putObject("paths").putObject(path).putObject(method).putObject("responses");
        for (String code : statuses) responses.putObject(code);
        return document;
    }
}
