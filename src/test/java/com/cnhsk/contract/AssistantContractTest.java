package com.cnhsk.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
        "springdoc.packages-to-scan=com.cnhsk.learning.assistant.web"})
class AssistantContractTest extends AbstractIntegrationTest {
    private static final Path CONTRACT = Path.of("specs", "015-system-assistant", "contracts",
            "openapi-assistant.yaml");
    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlers;

    @Test
    void AT16_assistantPathsMethodsAndStatusesMatchTheLockedContractInBothDirections() throws Exception {
        JsonNode source = new ObjectMapper(new YAMLFactory()).readTree(Files.readString(CONTRACT));
        JsonNode runtime = mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        Map<String, JsonNode> expected = operations(source);
        Map<String, JsonNode> actual = operations(runtime);

        assertThat(paths(source)).containsExactly("/api/assistant/ask");
        assertThat(expected.keySet()).containsExactly("post /api/assistant/ask");
        assertThat(paths(runtime)).as("Runtime assistant paths").isEqualTo(paths(source));
        assertThat(actual.keySet()).as("Runtime assistant operations").isEqualTo(expected.keySet());

        Set<String> registered = new LinkedHashSet<>();
        handlers.getHandlerMethods().forEach((mapping, handler) -> mapping.getPatternValues().stream()
                .filter(AssistantContractTest::isAssistantPath).forEach(path ->
                        mapping.getMethodsCondition().getMethods().forEach(method ->
                                registered.add(method.name().toLowerCase(Locale.ROOT) + " " + path))));
        assertThat(registered).as("Registered assistant operations").isEqualTo(expected.keySet());
        for (String operation : expected.keySet()) {
            Set<String> expectedStatuses = fields(expected.get(operation).path("responses"));
            assertThat(expectedStatuses).containsExactlyInAnyOrder("200", "401", "403", "422", "429", "503");
            assertThat(fields(actual.get(operation).path("responses")))
                    .as("%s statuses", operation).isEqualTo(expectedStatuses);
        }
    }

    private static Map<String, JsonNode> operations(JsonNode document) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        document.path("paths").fields().forEachRemaining(path -> {
            if (isAssistantPath(path.getKey())) path.getValue().fields().forEachRemaining(method -> {
                if (METHODS.contains(method.getKey())) result.put(method.getKey() + " " + path.getKey(), method.getValue());
            });
        });
        return result;
    }

    private static Set<String> paths(JsonNode document) {
        Set<String> result = new LinkedHashSet<>();
        document.path("paths").fieldNames().forEachRemaining(path -> {
            if (isAssistantPath(path)) result.add(path);
        });
        return result;
    }

    private static boolean isAssistantPath(String path) {
        return path.equals("/api/assistant") || path.startsWith("/api/assistant/");
    }

    private static Set<String> fields(JsonNode object) {
        Set<String> result = new LinkedHashSet<>();
        object.fieldNames().forEachRemaining(result::add);
        return result;
    }
}