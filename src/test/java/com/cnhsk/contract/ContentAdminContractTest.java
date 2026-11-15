package com.cnhsk.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cnhsk.learning.vocab.web.TopicAdminController;
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
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.packages-to-scan=com.cnhsk.shared.settings,com.cnhsk.learning.examadmin,com.cnhsk.learning.vocab.web"
})
class ContentAdminContractTest extends AbstractIntegrationTest {
    private static final Path CONTRACT = Path.of("specs", "023-content-admin-settings", "contracts",
            "openapi-content-admin.yaml");
    private static final Path REPORT = Path.of("target", "content-admin-contract-mismatches.json");
    private static final Set<String> METHODS = Set.of("get", "put", "post", "delete", "patch", "head", "options", "trace");
    private static final Set<String> ROOTS = Set.of("/api/admin/settings", "/api/admin/exams", "/api/admin/topics");
    private static final Set<String> LOCKED_OPERATIONS = Set.of(
            "GET /api/admin/settings",
            "PUT /api/admin/settings/{key}",
            "DELETE /api/admin/settings/{key}",
            "GET /api/admin/settings/{key}/history",
            "GET /api/admin/exams",
            "POST /api/admin/exams",
            "GET /api/admin/exams/{id}",
            "PUT /api/admin/exams/{id}",
            "POST /api/admin/exams/{id}/publish",
            "POST /api/admin/exams/{id}/archive",
            "POST /api/admin/exams/{id}/clone",
            "GET /api/admin/topics",
            "PUT /api/admin/topics/{id}/prerequisites");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlers;

    @Test
    void AT21_tenPathsThirteenOperationsAndResponseStatusesMatchBothWays() throws Exception {
        var parsed = new OpenAPIV3Parser().readLocation(CONTRACT.toAbsolutePath().toUri().toString(), null, null);
        assertThat(parsed.getMessages()).as("Content admin YAML parser messages").isEmpty();
        assertThat(parsed.getOpenAPI()).isNotNull();
        JsonNode source = new ObjectMapper(new YAMLFactory()).readTree(Files.readString(CONTRACT));
        JsonNode runtime = mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        Map<String, Set<String>> expected = operations(source);
        Map<String, Set<String>> documented = operations(runtime);
        List<String> mismatches = new ArrayList<>();
        Set<String> lockedPaths = new TreeSet<>();
        LOCKED_OPERATIONS.forEach(operation -> lockedPaths.add(operation.substring(operation.indexOf(' ') + 1)));
        assertThat(lockedPaths).hasSize(10);
        assertThat(LOCKED_OPERATIONS).hasSize(13);
        compare("YAML locked paths", lockedPaths, fields(source.path("paths")), mismatches);
        compare("YAML locked operations", LOCKED_OPERATIONS, expected.keySet(), mismatches);
        compareDocuments(source, runtime, mismatches);

        Map<String, String> registered = new TreeMap<>();
        handlers.getHandlerMethods().forEach((mapping, handler) -> {
            Class<?> type = ClassUtils.getUserClass(handler.getBeanType());
            register(type.getName(), isOwnedController(type.getName()), mapping.getPatternValues(),
                    mapping.getMethodsCondition().getMethods(), registered, mismatches);
        });
        compare("Registered operations", expected.keySet(), registered.keySet(), mismatches);
        if (!mismatches.isEmpty()) {
            Files.createDirectories(REPORT.getParent());
            Files.writeString(REPORT, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                    "contract", CONTRACT.toString(), "expected", expected, "documented", documented,
                    "registered", registered, "mismatches", mismatches)), StandardCharsets.UTF_8);
        }
        assertThat(mismatches).as("Content admin contract mismatches; report: %s", REPORT).isEmpty();
    }

    private static boolean isContentAdminPath(String path) {
        return ROOTS.stream().anyMatch(root -> path.equals(root) || path.startsWith(root + "/"));
    }

    private static boolean isOwnedController(String name) {
        return inPackage(name, "com.cnhsk.shared.settings")
                || inPackage(name, "com.cnhsk.learning.examadmin")
                || name.equals(TopicAdminController.class.getName());
    }

    private static boolean inPackage(String className, String packageName) {
        return className.startsWith(packageName + ".");
    }

    private static Set<String> fields(JsonNode object) {
        Set<String> result = new TreeSet<>();
        object.fieldNames().forEachRemaining(result::add);
        return result;
    }

    private static Set<String> paths(JsonNode document) {
        Set<String> result = fields(document.path("paths"));
        result.removeIf(path -> !isContentAdminPath(path));
        return result;
    }

    private static Map<String, Set<String>> operations(JsonNode document) {
        Map<String, Set<String>> result = new TreeMap<>();
        document.path("paths").fields().forEachRemaining(path -> {
            if (!isContentAdminPath(path.getKey())) return;
            path.getValue().fields().forEachRemaining(method -> {
                if (METHODS.contains(method.getKey())) {
                    result.put(method.getKey().toUpperCase(Locale.ROOT) + " " + path.getKey(),
                            fields(method.getValue().path("responses")));
                }
            });
        });
        return result;
    }

    private static void compareDocuments(JsonNode expected, JsonNode actual, List<String> mismatches) {
        compare("Documented paths", paths(expected), paths(actual), mismatches);
        compareOperations(operations(expected), operations(actual), mismatches);
    }

    private static void compareOperations(Map<String, Set<String>> expected, Map<String, Set<String>> actual,
            List<String> mismatches) {
        compare("Documented operations", expected.keySet(), actual.keySet(), mismatches);
        expected.forEach((operation, statuses) -> {
            if (actual.containsKey(operation)) compare(operation + " statuses", statuses, actual.get(operation), mismatches);
        });
    }

    private static void compare(String label, Collection<String> expected, Collection<String> actual,
            List<String> mismatches) {
        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(actual);
        Set<String> extra = new TreeSet<>(actual);
        extra.removeAll(expected);
        if (!missing.isEmpty() || !extra.isEmpty()) mismatches.add(label + ": missing=" + missing + ", extra=" + extra);
    }

    private static void register(String owner, boolean owned, Collection<String> paths, Collection<RequestMethod> methods,
            Map<String, String> operations, List<String> mismatches) {
        for (String path : new TreeSet<>(paths)) {
            if (!owned && !isContentAdminPath(path)) continue;
            if (!isContentAdminPath(path)) mismatches.add("Controller mapping outside namespace: " + owner + " " + path);
            if (methods.isEmpty()) mismatches.add("Mapping has no explicit HTTP method: " + owner + " " + path);
            for (RequestMethod method : methods) {
                String operation = method.name() + " " + path;
                String previous = operations.putIfAbsent(operation, owner);
                if (previous != null) mismatches.add("Duplicate mapping: " + operation + " owners=" + previous + ", " + owner);
            }
        }
    }

    @Test
    void namespaceAndOwnerBoundariesExcludeSiblingPrefixesAndLearnerTopics() {
        for (String root : ROOTS) {
            assertThat(isContentAdminPath(root)).as(root).isTrue();
            assertThat(isContentAdminPath(root + "/{id}")).as(root).isTrue();
            assertThat(isContentAdminPath(root + "-extra")).as(root).isFalse();
            assertThat(isContentAdminPath(root + "Extra/{id}")).as(root).isFalse();
        }
        assertThat(isContentAdminPath("/api/admin")).isFalse();
        assertThat(isContentAdminPath("/api/topics/{id}")).isFalse();
        assertThat(isOwnedController("com.cnhsk.shared.settings.SettingsController")).isTrue();
        assertThat(isOwnedController("com.cnhsk.learning.examadmin.web.ExamAdminController")).isTrue();
        assertThat(isOwnedController(TopicAdminController.class.getName())).isTrue();
        assertThat(isOwnedController("com.cnhsk.learning.vocab.web.TopicController")).isFalse();
        assertThat(isOwnedController("com.cnhsk.shared.settingsExtra.OtherController")).isFalse();
        assertThat(isOwnedController("com.cnhsk.learning.examadminExtra.OtherController")).isFalse();
    }

    @Test
    void inMemoryComparisonDetectsMissingAndExtraPathsAndMethods() throws Exception {
        JsonNode expected = mapper.readTree("""
                {"paths":{"/api/admin/topics":{"get":{"responses":{"200":{}}}}}}
                """);
        for (JsonNode actual : List.of(
                mapper.readTree("{\"paths\":{}}"),
                mapper.readTree("""
                        {"paths":{"/api/admin/topics":{"get":{"responses":{"200":{}}}},
                        "/api/admin/topics/unknown":{"get":{"responses":{"200":{}}}}}}
                        """),
                mapper.readTree("""
                        {"paths":{"/api/admin/topics":{"post":{"responses":{"200":{}}}}}}
                        """),
                mapper.readTree("""
                        {"paths":{"/api/admin/topics":{"get":{"responses":{"200":{}}},
                        "post":{"responses":{"201":{}}}}}}
                        """))) {
            List<String> mismatches = new ArrayList<>();
            compareDocuments(expected, actual, mismatches);
            assertThat(mismatches).as("Changed paths or methods must fail: %s", actual).isNotEmpty();
        }
    }

    @Test
    void inMemoryComparisonDetectsMissingAndExtraStatusesWithoutIgnoringDefaultOrWildcardCodes() {
        String operation = "GET /api/admin/topics";
        Map<String, Set<String>> expected = Map.of(operation, Set.of("200", "401", "403", "422"));
        for (Set<String> statuses : List.of(Set.of("200", "401", "403"), Set.of("200", "401", "403", "422", "500"),
                Set.of("200", "401", "403", "422", "default"), Set.of("200", "401", "403", "422", "5XX"))) {
            List<String> mismatches = new ArrayList<>();
            compareOperations(expected, Map.of(operation, statuses), mismatches);
            assertThat(mismatches).hasSize(1);
            assertThat(mismatches.get(0)).startsWith(operation + " statuses:");
        }
    }

    @Test
    void inMemoryComparisonIgnoresOnlyPathMetadataAndOtherNamespaces() throws Exception {
        JsonNode expected = mapper.readTree("""
                {"paths":{"/api/admin/topics":{"get":{"responses":{"200":{}}}}}}
                """);
        JsonNode actual = mapper.readTree("""
                {"paths":{"/api/admin/topics":{"summary":"Metadata","parameters":[],
                "get":{"responses":{"200":{}}}},
                "/api/admin/topics-extra":{"post":{"responses":{"201":{}}}},
                "/api/topics":{"get":{"responses":{"200":{}}}}}}
                """);
        List<String> mismatches = new ArrayList<>();
        compareDocuments(expected, actual, mismatches);
        assertThat(mismatches).isEmpty();
        JsonNode allMethods = mapper.readTree("""
                {"paths":{"/api/admin/topics":{"get":{},"post":{},"put":{},"delete":{},
                "patch":{},"head":{},"options":{},"trace":{}}}}
                """);
        assertThat(operations(allMethods)).hasSize(8);
    }

    @Test
    void inMemoryMappingsCatchForeignOwnersHiddenRoutesMissingMethodsAndDuplicates() {
        Map<String, String> registered = new TreeMap<>();
        List<String> mismatches = new ArrayList<>();
        register("other.Controller", false, Set.of("/api/admin/topics/unknown"), Set.of(RequestMethod.GET), registered, mismatches);
        register("owned.Controller", true, Set.of("/api/unexpected"), Set.of(RequestMethod.POST), registered, mismatches);
        register("owned.Controller", true, Set.of("/api/admin/settings"), Set.of(), registered, mismatches);
        register("duplicate.Controller", false, Set.of("/api/admin/topics/unknown"), Set.of(RequestMethod.GET), registered, mismatches);
        register("learner.Controller", false, Set.of("/api/topics"), Set.of(RequestMethod.GET), registered, mismatches);
        assertThat(registered).containsOnlyKeys("GET /api/admin/topics/unknown", "POST /api/unexpected");
        assertThat(mismatches).anyMatch(message -> message.startsWith("Controller mapping outside namespace:"))
                .anyMatch(message -> message.startsWith("Mapping has no explicit HTTP method:"))
                .anyMatch(message -> message.startsWith("Duplicate mapping:"));
        compare("Registered operations", LOCKED_OPERATIONS, registered.keySet(), mismatches);
        assertThat(mismatches).anyMatch(message -> message.startsWith("Registered operations:")
                && message.contains("GET /api/admin/topics/unknown") && message.contains("POST /api/unexpected"));
    }
}
