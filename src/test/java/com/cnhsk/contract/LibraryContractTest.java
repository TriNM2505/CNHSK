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

import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true",
        "springdoc.packages-to-scan=com.cnhsk.learning.library.web"})
class LibraryContractTest extends AbstractIntegrationTest {
    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete");
    private static final Path CONTRACT = Path.of("specs", "014-library", "contracts", "openapi-library.yaml");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlers;

    @Test
    void AT29_allFifteenPathsTwentyTwoOperationsAndStatusSetsMatchTheLockedContract() throws Exception {
        JsonNode source = new ObjectMapper(new YAMLFactory()).readTree(Files.readString(CONTRACT));
        JsonNode runtime = mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        Map<String, JsonNode> expected = operations(source);
        Map<String, JsonNode> actual = operations(runtime);
        assertThat(libraryPaths(source)).as("Locked library paths").hasSize(15);
        assertThat(expected).as("Locked library operations").hasSize(22);

        List<String> mismatches = new ArrayList<>();
        if (!libraryPaths(runtime).equals(libraryPaths(source))) {
            mismatches.add("Paths expected=" + libraryPaths(source) + " actual=" + libraryPaths(runtime));
        }
        if (!actual.keySet().equals(expected.keySet())) {
            mismatches.add("Operations expected=" + expected.keySet() + " actual=" + actual.keySet());
        }
        Set<String> registered = new LinkedHashSet<>();
        handlers.getHandlerMethods().forEach((mapping, handler) -> mapping.getPatternValues().stream()
                .filter(LibraryContractTest::isLibraryPath).forEach(path ->
                        mapping.getMethodsCondition().getMethods().forEach(method ->
                                registered.add(method.name().toLowerCase(java.util.Locale.ROOT) + " " + path))));
        if (!registered.equals(expected.keySet())) {
            mismatches.add("Registered operations expected=" + expected.keySet() + " actual=" + registered);
        }
        expected.forEach((operation, definition) -> {
            Set<String> expectedStatuses = fields(definition.path("responses"));
            JsonNode generated = actual.get(operation);
            Set<String> actualStatuses = generated == null ? Set.of() : fields(generated.path("responses"));
            if (!actualStatuses.equals(expectedStatuses)) {
                mismatches.add(operation + " statuses expected=" + expectedStatuses + " actual=" + actualStatuses);
            }
        });
        String output = System.getProperty("cnhsk.library.contract-audit-output");
        if (output != null) {
            Files.writeString(Path.of(output), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(
                    Map.of("expected", expected, "actual", actual, "mismatches", mismatches)));
        }
        assertThat(mismatches).as("AT-29 runtime contract mismatches").isEmpty();
    }

    private static Map<String, JsonNode> operations(JsonNode document) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        document.path("paths").fields().forEachRemaining(path -> {
            if (isLibraryPath(path.getKey())) path.getValue().fields().forEachRemaining(method -> {
                if (METHODS.contains(method.getKey())) result.put(method.getKey() + " " + path.getKey(), method.getValue());
            });
        });
        return result;
    }

    private static Set<String> libraryPaths(JsonNode document) {
        Set<String> result = new LinkedHashSet<>();
        document.path("paths").fieldNames().forEachRemaining(path -> {
            if (isLibraryPath(path)) result.add(path);
        });
        return result;
    }

    private static boolean isLibraryPath(String path) {
        return path.equals("/api/translate") || path.startsWith("/api/public/dictionary/")
                || path.equals("/api/notes") || path.startsWith("/api/notes/")
                || path.equals("/api/flashcard-decks") || path.startsWith("/api/flashcard-decks/")
                || path.startsWith("/api/flashcards/");
    }

    private static Set<String> fields(JsonNode object) {
        Set<String> result = new LinkedHashSet<>();
        object.fieldNames().forEachRemaining(result::add);
        return result;
    }
}
