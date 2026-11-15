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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true",
        "springdoc.packages-to-scan=com.cnhsk.learning.leaderboard.web"})
class LeaderboardContractTest extends AbstractIntegrationTest {
    private static final Set<String> METHODS = Set.of(
            "get", "post", "put", "patch", "delete", "head", "options", "trace");
    private static final Set<String> EXPECTED_PATHS = Set.of(
            "/api/public/leaderboards/topics",
            "/api/public/leaderboards/topics/{topicId}",
            "/api/public/leaderboards/games",
            "/api/public/leaderboards/games/{gameCode}");
    private static final Set<String> EXPECTED_OPERATIONS = Set.of(
            "get /api/public/leaderboards/topics",
            "get /api/public/leaderboards/topics/{topicId}",
            "get /api/public/leaderboards/games",
            "get /api/public/leaderboards/games/{gameCode}");
    private static final Path CONTRACT = Path.of(
            "specs", "017-leaderboard", "contracts", "openapi-leaderboard.yaml");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlers;

    @Test
    void AT18_leaderboardPathsMethodsAndStatusesMatchTheContractInBothDirections() throws Exception {
        JsonNode source = new ObjectMapper(new YAMLFactory())
                .readTree(Files.readString(CONTRACT, StandardCharsets.UTF_8));
        JsonNode runtime = mapper.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());

        Set<String> contractPaths = fields(source.path("paths"));
        Map<String, JsonNode> contractOperations = operations(source);
        Set<String> runtimePaths = fields(runtime.path("paths"));
        Map<String, JsonNode> runtimeOperations = operations(runtime);
        Set<String> registeredOperations = registeredLeaderboardOperations();

        List<String> mismatches = new ArrayList<>();
        compare(mismatches, "Contract paths", EXPECTED_PATHS, contractPaths);
        compare(mismatches, "Contract operations", EXPECTED_OPERATIONS, contractOperations.keySet());
        compare(mismatches, "Runtime paths", contractPaths, runtimePaths);
        compare(mismatches, "Runtime operations", contractOperations.keySet(), runtimeOperations.keySet());
        compare(mismatches, "Registered leaderboard operations", contractOperations.keySet(), registeredOperations);

        contractOperations.forEach((operation, definition) -> {
            Set<String> expectedStatuses = fields(definition.path("responses"));
            JsonNode generated = runtimeOperations.get(operation);
            Set<String> actualStatuses = generated == null
                    ? Set.of() : fields(generated.path("responses"));
            compare(mismatches, operation + " response statuses", expectedStatuses, actualStatuses);
        });

        assertThat(mismatches).as("AT-18 leaderboard contract mismatches").isEmpty();
    }

    private static Map<String, JsonNode> operations(JsonNode document) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        document.path("paths").fields().forEachRemaining(path ->
                path.getValue().fields().forEachRemaining(method -> {
                    if (METHODS.contains(method.getKey())) {
                        result.put(method.getKey() + " " + path.getKey(), method.getValue());
                    }
                }));
        return result;
    }

    private Set<String> registeredLeaderboardOperations() {
        Set<String> result = new LinkedHashSet<>();
        handlers.getHandlerMethods().forEach((mapping, handler) -> {
            String packageName = handler.getBeanType().getPackageName();
            if (!packageName.equals("com.cnhsk.learning.leaderboard.web")
                    && !packageName.startsWith("com.cnhsk.learning.leaderboard.web.")) {
                return;
            }
            mapping.getPatternValues().forEach(path -> {
                var methods = mapping.getMethodsCondition().getMethods();
                if (methods.isEmpty()) {
                    METHODS.forEach(method -> result.add(method + " " + path));
                } else {
                    methods.forEach(method -> result.add(
                            method.name().toLowerCase(Locale.ROOT) + " " + path));
                }
            });
        });
        return result;
    }

    private static Set<String> fields(JsonNode object) {
        Set<String> result = new LinkedHashSet<>();
        object.fieldNames().forEachRemaining(result::add);
        return result;
    }

    private static void compare(List<String> mismatches, String label,
            Set<String> expected, Set<String> actual) {
        if (!expected.equals(actual)) {
            mismatches.add(label + " expected=" + expected + " actual=" + actual);
        }
    }
}
