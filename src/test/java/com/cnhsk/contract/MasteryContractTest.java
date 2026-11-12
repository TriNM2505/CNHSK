package com.cnhsk.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true",
        "springdoc.packages-to-scan=com.cnhsk.learning.mastery.web,com.cnhsk.learning.review.web"})
class MasteryContractTest {
    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace");
    private static final Path CONTRACT = Path.of("specs", "011-mastery-learning-frontier", "contracts", "openapi-mastery.yaml");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;

    @Test
    void AT27_runtimeMasteryAndReviewPathsMethodsAndStatusesMatchContractBothWays() throws Exception {
        JsonNode source = new ObjectMapper(new YAMLFactory()).readTree(Files.readString(CONTRACT));
        JsonNode runtime = mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        Map<String, JsonNode> expected = operations(source);
        Map<String, JsonNode> actual = operations(runtime);
        List<String> mismatches = new ArrayList<>();

        Set<String> expectedPaths = paths(source);
        Set<String> actualPaths = paths(runtime);
        if (!actualPaths.equals(expectedPaths)) {
            Set<String> missing = new LinkedHashSet<>(expectedPaths);
            missing.removeAll(actualPaths);
            Set<String> extra = new LinkedHashSet<>(actualPaths);
            extra.removeAll(expectedPaths);
            if (!missing.isEmpty()) mismatches.add("Missing paths: " + missing);
            if (!extra.isEmpty()) mismatches.add("Extra paths: " + extra);
        }

        Set<String> missingOperations = new LinkedHashSet<>(expected.keySet());
        missingOperations.removeAll(actual.keySet());
        Set<String> extraOperations = new LinkedHashSet<>(actual.keySet());
        extraOperations.removeAll(expected.keySet());
        if (!missingOperations.isEmpty()) mismatches.add("Missing methods/operations: " + missingOperations);
        if (!extraOperations.isEmpty()) mismatches.add("Extra methods/operations: " + extraOperations);

        expected.forEach((operation, definition) -> {
            Set<String> expectedStatuses = fields(definition.path("responses"));
            JsonNode generated = actual.get(operation);
            Set<String> actualStatuses = generated == null ? Set.of() : fields(generated.path("responses"));
            if (!actualStatuses.equals(expectedStatuses)) {
                Set<String> missing = new LinkedHashSet<>(expectedStatuses);
                missing.removeAll(actualStatuses);
                Set<String> extra = new LinkedHashSet<>(actualStatuses);
                extra.removeAll(expectedStatuses);
                mismatches.add(operation + " status mismatch; missing=" + missing + ", extra=" + extra);
            }
        });

        assertThat(mismatches).as("AT-27 OpenAPI path/method/status mismatches under /api/mastery and /api/reviews")
                .isEmpty();
    }

    private static Map<String, JsonNode> operations(JsonNode document) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        document.path("paths").fields().forEachRemaining(path -> {
            if (inScope(path.getKey())) path.getValue().fields().forEachRemaining(method -> {
                if (METHODS.contains(method.getKey())) {
                    result.put(method.getKey() + " " + path.getKey(), method.getValue());
                }
            });
        });
        return result;
    }

    private static Set<String> paths(JsonNode document) {
        Set<String> result = new LinkedHashSet<>();
        document.path("paths").fieldNames().forEachRemaining(path -> {
            if (inScope(path)) result.add(path);
        });
        return result;
    }

    private static boolean inScope(String path) {
        return path.equals("/api/mastery") || path.startsWith("/api/mastery/")
                || path.equals("/api/reviews") || path.startsWith("/api/reviews/");
    }

    private static Set<String> fields(JsonNode object) {
        Set<String> result = new LinkedHashSet<>();
        object.fieldNames().forEachRemaining(result::add);
        return result;
    }
}
