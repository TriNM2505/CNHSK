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
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true",
        "springdoc.packages-to-scan=com.cnhsk.learning.quiz.web"})
class QuizContractTest extends AbstractIntegrationTest {
    private static final String CONTROLLER_PACKAGE = "com.cnhsk.learning.quiz.web";
    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace");
    private static final Path CONTRACT = Path.of("specs", "019-topic-quiz", "contracts", "openapi-quiz.yaml");
    private static final Set<String> LOCKED_OPERATIONS = Set.of("get /api/quizzes",
            "post /api/quizzes/{topicId}/attempts", "get /api/quiz-attempts/{sessionId}/result");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlers;

    @Test
    void AT18_pathsMethodsOperationIdsAndStatusSetsMatchInBothDirections() throws Exception {
        JsonNode source = new ObjectMapper(new YAMLFactory()).readTree(Files.readString(CONTRACT, StandardCharsets.UTF_8));
        JsonNode runtime = mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(source.path("paths").isObject()).isTrue();
        Set<String> expectedPaths = fields(source.path("paths"));
        Map<String, JsonNode> expected = operations(source);
        Map<String, JsonNode> actual = operations(runtime);
        assertThat(expectedPaths).as("Locked quiz paths").containsExactlyInAnyOrder("/api/quizzes",
                "/api/quizzes/{topicId}/attempts", "/api/quiz-attempts/{sessionId}/result");
        assertThat(expected.keySet()).as("Locked three quiz operations").isEqualTo(LOCKED_OPERATIONS);
        List<String> operationIds = expected.values().stream().map(operation -> operation.path("operationId").asText()).toList();
        assertThat(operationIds).allSatisfy(id -> assertThat(id).isNotBlank()).doesNotHaveDuplicates();

        Set<String> registeredPaths = new LinkedHashSet<>();
        Set<String> registeredOperations = new LinkedHashSet<>();
        handlers.getHandlerMethods().forEach((mapping, handler) -> {
            String packageName = handler.getBeanType().getPackageName();
            if (!packageName.equals(CONTROLLER_PACKAGE) && !packageName.startsWith(CONTROLLER_PACKAGE + ".")) return;
            mapping.getPatternValues().forEach(path -> {
                registeredPaths.add(path);
                var methods = mapping.getMethodsCondition().getMethods();
                if (methods.isEmpty()) {
                    // An unrestricted mapping accepts more than the three locked operations.
                    METHODS.forEach(method -> registeredOperations.add(method + " " + path));
                } else {
                    methods.forEach(method -> registeredOperations.add(method.name().toLowerCase(Locale.ROOT) + " " + path));
                }
            });
        });

        List<String> differences = new ArrayList<>();
        compare(differences, "Runtime paths", expectedPaths, fields(runtime.path("paths")));
        compare(differences, "Runtime operations", expected.keySet(), actual.keySet());
        compare(differences, "Registered quiz controller paths", expectedPaths, registeredPaths);
        compare(differences, "Registered quiz controller operations", expected.keySet(), registeredOperations);
        expected.forEach((operation, definition) -> {
            JsonNode generated = actual.get(operation);
            String expectedId = definition.path("operationId").asText();
            String actualId = generated == null ? "" : generated.path("operationId").asText();
            if (!expectedId.equals(actualId)) differences.add(operation + " operationId expected=" + expectedId + " actual=" + actualId);
            Set<String> expectedStatuses = fields(definition.path("responses"));
            assertThat(expectedStatuses).as(operation + " contract statuses").isNotEmpty();
            compare(differences, operation + " statuses", expectedStatuses,
                    generated == null ? Set.of() : fields(generated.path("responses")));
        });
        assertThat(differences).as("AT-18 quiz contract mismatches").isEmpty();
    }

    private static Map<String, JsonNode> operations(JsonNode document) {
        Map<String, JsonNode> operations = new LinkedHashMap<>();
        // Springdoc scans only quiz.web; do not filter paths here and hide extra runtime routes.
        document.path("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(method -> {
            if (METHODS.contains(method.getKey())) operations.put(method.getKey() + " " + path.getKey(), method.getValue());
        }));
        return operations;
    }

    private static Set<String> fields(JsonNode object) {
        Set<String> fields = new LinkedHashSet<>();
        object.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private static void compare(List<String> differences, String label, Set<String> expected, Set<String> actual) {
        if (!expected.equals(actual)) differences.add(label + " expected=" + expected + " actual=" + actual);
    }
}
