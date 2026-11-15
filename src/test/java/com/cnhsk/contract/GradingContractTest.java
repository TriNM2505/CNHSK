package com.cnhsk.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.ClassUtils;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true",
        "springdoc.packages-to-scan=com.cnhsk.learning.grading"})
class GradingContractTest extends AbstractIntegrationTest {
    private static final Path CONTRACT = Path.of("specs", "022-teacher-grading", "contracts", "openapi-grading.yaml");
    private static final Path REPORT = Path.of("target", "grading-contract-mismatches.txt");
    private static final String PACKAGE = "com.cnhsk.learning.grading";
    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace");
    private static final Set<String> OPERATIONS = Set.of(
            "get /api/grading/policy",
            "get /api/grading-requests",
            "post /api/grading-requests",
            "get /api/grading-requests/{id}",
            "post /api/grading-requests/{id}/cancel",
            "get /api/teacher/grading-queue",
            "get /api/teacher/grading-requests/mine",
            "get /api/teacher/grading-requests/{id}",
            "post /api/teacher/grading-requests/{id}/claim",
            "post /api/teacher/grading-requests/{id}/release",
            "post /api/teacher/grading-requests/{id}/complete");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlers;

    @Test
    void AT19_gradingPathsMethodsAndStatusesMatchTheLockedContractInBothDirections() throws Exception {
        JsonNode source = new ObjectMapper(new YAMLFactory()).readTree(Files.readString(CONTRACT));
        JsonNode runtime = mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        Map<String, JsonNode> expected = operations(source, false);
        Map<String, JsonNode> actual = operations(runtime, true);
        Set<String> registered = new LinkedHashSet<>();
        handlers.getHandlerMethods().forEach((mapping, handler) -> {
            String packageName = ClassUtils.getUserClass(handler.getBeanType()).getPackageName();
            if (!packageName.equals(PACKAGE) && !packageName.startsWith(PACKAGE + ".")) return;
            mapping.getPatternValues().forEach(path -> {
                if (mapping.getMethodsCondition().getMethods().isEmpty()) {
                    registered.add("* " + path);
                } else {
                    mapping.getMethodsCondition().getMethods().forEach(method ->
                            registered.add(method.name().toLowerCase(Locale.ROOT) + " " + path));
                }
            });
        });

        List<String> mismatches = new ArrayList<>();
        compare(OPERATIONS, expected.keySet(), "Locked YAML operations (11)", mismatches);
        Set<String> expectedPaths = fields(source.path("paths"));
        if (expectedPaths.size() != 10) mismatches.add("Locked YAML paths expected=10 actual=" + expectedPaths.size());
        Set<String> actualPaths = new LinkedHashSet<>();
        runtime.path("paths").fieldNames().forEachRemaining(path -> {
            if (isGradingPath(path)) actualPaths.add(path);
        });
        compare(expectedPaths, actualPaths, "Documented paths", mismatches);
        compare(expected.keySet(), actual.keySet(), "Documented operations", mismatches);
        compare(expected.keySet(), registered, "Registered grading package operations", mismatches);
        expected.forEach((operation, definition) -> {
            JsonNode generated = actual.get(operation);
            if (generated != null) {
                compare(fields(definition.path("responses")), fields(generated.path("responses")),
                        operation + " statuses", mismatches);
            }
        });

        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, mismatches.isEmpty() ? "PASS: 10 paths / 11 operations; path, method and status sets match.\n"
                : String.join("\n", mismatches) + "\n", StandardCharsets.UTF_8);
        assertThat(mismatches).as("Grading contract mismatches; see %s", REPORT).isEmpty();
    }

    private static Map<String, JsonNode> operations(JsonNode document, boolean scoped) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        document.path("paths").fields().forEachRemaining(path -> {
            if (scoped && !isGradingPath(path.getKey())) return;
            path.getValue().fields().forEachRemaining(method -> {
                if (METHODS.contains(method.getKey())) {
                    result.put(method.getKey() + " " + path.getKey(), method.getValue());
                }
            });
        });
        return result;
    }

    private static boolean isGradingPath(String path) {
        return inNamespace(path, "/api/grading") || inNamespace(path, "/api/grading-requests")
                || path.equals("/api/teacher/grading-queue")
                || inNamespace(path, "/api/teacher/grading-requests");
    }

    private static boolean inNamespace(String path, String root) {
        return path.equals(root) || path.startsWith(root + "/");
    }

    private static Set<String> fields(JsonNode object) {
        Set<String> result = new LinkedHashSet<>();
        object.fieldNames().forEachRemaining(result::add);
        return result;
    }

    private static void compare(Set<String> expected, Set<String> actual, String location, List<String> mismatches) {
        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(actual);
        Set<String> unexpected = new TreeSet<>(actual);
        unexpected.removeAll(expected);
        if (!missing.isEmpty()) mismatches.add(location + " missing=" + missing);
        if (!unexpected.isEmpty()) mismatches.add(location + " unexpected=" + unexpected);
    }
}
