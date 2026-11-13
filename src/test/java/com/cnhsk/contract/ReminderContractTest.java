package com.cnhsk.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.cnhsk.auth.controller.ReminderSettingsController;
import com.cnhsk.auth.controller.ReminderUnsubscribeController;
import com.cnhsk.learning.reminder.web.ReminderController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.packages-to-scan=com.cnhsk.auth.controller,com.cnhsk.learning.reminder.web"
})
class ReminderContractTest {

    private static final Path CONTRACT = Path.of("specs/020-study-reminder/contracts/openapi-reminder.yaml");
    private static final String HOUR_PATTERN = "^([01]\\d|2[0-3]):00$";
    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace");
    private static final Set<String> LOCKED_OPERATIONS = Set.of("get /api/me/reminder", "put /api/me/reminder",
            "post /api/public/reminder/unsubscribe", "get /api/reminders/today");
    private static final Set<Class<?>> CONTROLLERS = Set.of(ReminderSettingsController.class,
            ReminderUnsubscribeController.class, ReminderController.class);

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlers;

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    private final Map<Path, Document> documents = new HashMap<>();

    @Test
    void AT20_generatedOperationsSchemasParametersAndSecurityMatchTheLockedContractBothWays() throws Exception {
        Document source = source();
        Document runtime = new Document(mapper.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()), null);
        List<String> mismatches = audit(source, runtime);
        Set<String> registered = new LinkedHashSet<>();
        handlers.getHandlerMethods().forEach((mapping, handler) -> {
            if (CONTROLLERS.contains(handler.getBeanType())) {
                mapping.getPatternValues().forEach(path -> mapping.getMethodsCondition().getMethods().forEach(method ->
                        registered.add(method.name().toLowerCase(Locale.ROOT) + " " + path)));
            }
        });
        compare(LOCKED_OPERATIONS, registered, "registered reminder operations", mismatches);
        Path directory = Path.of("target", "020-t07-contract-evidence");
        Files.createDirectories(directory);
        String runId = UUID.randomUUID().toString();
        mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve(runId + "-generated.json").toFile(), runtime.root());
        mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve(runId + "-audit.json").toFile(), Map.of(
                "expectedOperations", operations(source.root()).keySet(), "actualOperations", operations(runtime.root()).keySet(),
                "registeredOperations", registered, "mismatchCount", mismatches.size(), "mismatches", mismatches));
        assertThat(mismatches).as("AT-20 reminder contract mismatches; evidence=%s", directory).isEmpty();
    }

    @Test
    void matcherRejectsMissingAndExtraPathsMethodsAndStatuses() throws Exception {
        Document source = source();
        ObjectNode changed = source.root().deepCopy();
        ObjectNode paths = (ObjectNode) changed.path("paths");
        paths.remove("/api/reminders/today");
        paths.set("/api/reminders/extra", source.root().at("/paths/~1api~1reminders~1today").deepCopy());
        ObjectNode settings = (ObjectNode) paths.path("/api/me/reminder");
        settings.remove("put");
        settings.set("delete", settings.path("get").deepCopy());
        ObjectNode responses = (ObjectNode) settings.path("get").path("responses");
        responses.remove("403");
        responses.set("418", responses.path("200").deepCopy());

        List<String> mismatches = audit(source, new Document(changed, source.path()));

        assertThat(mismatches).anyMatch(message -> message.startsWith("reminder paths "));
        assertThat(mismatches).anyMatch(message -> message.startsWith("reminder operations "));
        assertThat(mismatches).anyMatch(message -> message.startsWith("get /api/me/reminder statuses "));
        assertThat(mismatches.toString()).contains("/api/reminders/extra", "delete /api/me/reminder", "418", "403");
    }

    @Test
    void matcherRejectsMissingExtraFieldsRequiredTypesAndTimePatterns() throws Exception {
        Document source = source();
        JsonNode expected = source.root().at("/components/schemas/ReminderUpdate");
        ObjectNode changed = expected.deepCopy();
        ((ObjectNode) changed.path("properties")).remove("email");
        ((ObjectNode) changed.path("properties")).putObject("extra").put("type", "string");
        ((ObjectNode) changed.path("properties").path("web")).put("type", "string");
        ((ObjectNode) changed.path("properties").path("reminderTime")).put("pattern", ".*");
        changed.putArray("required").add("reminderTime").add("extra");
        List<String> mismatches = new ArrayList<>();

        schema(source, expected, source, changed, "ReminderUpdate", mismatches);

        assertThat(mismatches).anyMatch(message -> message.startsWith("ReminderUpdate properties "));
        assertThat(mismatches).anyMatch(message -> message.startsWith("ReminderUpdate required "));
        assertThat(mismatches).anyMatch(message -> message.startsWith("ReminderUpdate.web type "));
        assertThat(mismatches).anyMatch(message -> message.startsWith("ReminderUpdate.reminderTime pattern "));
        assertThat(mismatches.toString()).contains("email", "extra");
    }

    @Test
    void securityMatcherDistinguishesOrAndScopesInheritanceAndPublicAccess() throws Exception {
        Document source = source();
        JsonNode expected = source.root().at("/paths/~1api~1me~1reminder/get/security");
        assertThat(security(expected)).isEqualTo(security(mapper.readTree("[{\"bearerAuth\":[]},{\"cookieAuth\":[]}]")));
        for (String invalid : List.of("[]", "[{\"cookieAuth\":[]}]",
                "[{\"cookieAuth\":[],\"bearerAuth\":[]}]",
                "[{\"cookieAuth\":[]},{\"bearerAuth\":[\"unexpected\"]}]",
                "[{\"cookieAuth\":[]},{\"bearerAuth\":[]},{\"otherAuth\":[]}]")) {
            assertThat(security(mapper.readTree(invalid))).isNotEqualTo(security(expected));
        }
        assertThat(security(mapper.readTree("[]"))).isEqualTo(security(mapper.readTree("[{}]")));
        ObjectNode inherited = mapper.createObjectNode();
        inherited.set("security", expected);
        assertThat(effectiveSecurity(inherited, mapper.createObjectNode())).isEqualTo(security(expected));
        ObjectNode publicOperation = mapper.createObjectNode();
        publicOperation.putArray("security");
        assertThat(effectiveSecurity(inherited, publicOperation)).isEqualTo(Set.of(Map.of()));
    }

    private Document source() throws Exception {
        Path path = CONTRACT.toAbsolutePath().normalize();
        var parsed = new OpenAPIV3Parser().readLocation(path.toUri().toString(), null, null);
        assertThat(parsed.getMessages()).as("Locked reminder YAML parser messages").isEmpty();
        assertThat(parsed.getOpenAPI()).isNotNull();
        Document result = load(path);
        assertThat(operations(result.root()).keySet()).isEqualTo(LOCKED_OPERATIONS);
        assertThat(fields(result.root().path("components").path("schemas")))
                .contains("ReminderSettings", "ReminderUpdate", "UnsubscribeRequest", "TodayReminder");
        for (String name : List.of("ReminderSettings", "ReminderUpdate", "TodayReminder")) {
            assertThat(result.root().path("components").path("schemas").path(name)
                    .path("properties").path("reminderTime").path("pattern").asText()).isEqualTo(HOUR_PATTERN);
        }
        return result;
    }

    private List<String> audit(Document source, Document runtime) throws Exception {
        Map<String, JsonNode> expected = operations(source.root());
        Map<String, JsonNode> actual = operations(runtime.root());
        List<String> mismatches = new ArrayList<>();
        compare(paths(expected), paths(actual), "reminder paths", mismatches);
        compare(expected.keySet(), actual.keySet(), "reminder operations", mismatches);
        for (var entry : expected.entrySet()) {
            String name = entry.getKey();
            JsonNode wanted = entry.getValue();
            JsonNode found = actual.get(name);
            if (found == null) continue;
            compare(wanted.path("operationId"), found.path("operationId"), name + " operationId", mismatches);
            compare(fields(wanted.path("responses")), fields(found.path("responses")), name + " statuses", mismatches);
            compare(effectiveSecurity(source.root(), wanted), effectiveSecurity(runtime.root(), found), name + " security", mismatches);
            Map<String, JsonNode> expectedParameters = parameters(source, wanted);
            Map<String, JsonNode> actualParameters = parameters(runtime, found);
            compare(expectedParameters.keySet(), actualParameters.keySet(), name + " parameters", mismatches);
            for (var parameter : expectedParameters.entrySet()) {
                JsonNode runtimeParameter = actualParameters.get(parameter.getKey());
                if (runtimeParameter == null) continue;
                String location = name + " " + parameter.getKey();
                compare(parameter.getValue().path("required").asBoolean(), runtimeParameter.path("required").asBoolean(), location + " required", mismatches);
                schema(source, parameter.getValue().path("schema"), runtime, runtimeParameter.path("schema"), location, mismatches);
            }
            compare(wanted.path("requestBody").path("required").asBoolean(), found.path("requestBody").path("required").asBoolean(),
                    name + " requestBody required", mismatches);
            content(source, wanted.path("requestBody"), runtime, found.path("requestBody"), name + " requestBody", mismatches);
            for (String code : fields(wanted.path("responses"))) {
                JsonNode expectedResponse = resolve(source, wanted.path("responses").path(code)).node();
                JsonNode actualResponse = resolve(runtime, found.path("responses").path(code)).node();
                content(source, expectedResponse, runtime, actualResponse, name + " response " + code, mismatches);
                if (name.equals("post /api/public/reminder/unsubscribe") && Set.of("400", "429").contains(code)) {
                    compare(expectedResponse.path("description"), actualResponse.path("description"), name + " response " + code + " error code", mismatches);
                }
            }
        }
        return mismatches;
    }

    private void content(Document source, JsonNode expected, Document runtime, JsonNode actual,
            String location, List<String> mismatches) throws Exception {
        compare(fields(expected.path("content")), fields(actual.path("content")), location + " media types", mismatches);
        for (String mediaType : fields(expected.path("content"))) {
            schema(source, expected.path("content").path(mediaType).path("schema"), runtime,
                    actual.path("content").path(mediaType).path("schema"), location + " " + mediaType, mismatches);
        }
    }

    private void schema(Document source, JsonNode expectedSchema, Document runtime, JsonNode actualSchema,
            String location, List<String> mismatches) throws Exception {
        Resolved wanted = resolve(source, expectedSchema);
        Resolved found = resolve(runtime, actualSchema);
        JsonNode expected = wanted.node();
        JsonNode actual = found.node();
        if (actual.isMissingNode()) {
            mismatches.add(location + " missing/unresolved schema " + actualSchema);
            return;
        }
        compare(types(expected), types(actual), location + " type", mismatches);
        compare(strings(expected.path("required")), strings(actual.path("required")), location + " required", mismatches);
        compare(fields(expected.path("properties")), fields(actual.path("properties")), location + " properties", mismatches);
        for (String constraint : List.of("pattern", "minimum", "maximum", "minLength", "maxLength", "format", "const", "enum")) {
            if (expected.has(constraint)) {
                JsonNode left = expected.path(constraint);
                JsonNode right = actual.path(constraint);
                Object leftValue = left.isNumber() ? left.decimalValue().stripTrailingZeros() : left;
                Object rightValue = right.isNumber() ? right.decimalValue().stripTrailingZeros() : right;
                compare(leftValue, rightValue, location + " " + constraint, mismatches);
            }
        }
        if (location.endsWith(".List-Unsubscribe")) {
            compare(expected.path("example"), actual.path("example"), location + " RFC 8058 example", mismatches);
        }
        for (String property : fields(expected.path("properties"))) {
            schema(wanted.document(), expected.path("properties").path(property), found.document(),
                    actual.path("properties").path(property), location + "." + property, mismatches);
        }
        if (expected.has("items")) schema(wanted.document(), expected.path("items"), found.document(), actual.path("items"), location + "[]", mismatches);
    }

    private Resolved resolve(Document document, JsonNode schema) throws Exception {
        for (int depth = 0; depth < 32 && schema.has("$ref"); depth++) {
            String ref = schema.path("$ref").asText();
            int fragment = ref.indexOf('#');
            if (fragment < 0) throw new IllegalArgumentException("Unsupported contract reference: " + ref);
            if (fragment > 0) {
                if (document.path() == null) throw new IllegalArgumentException("Unexpected external runtime reference: " + ref);
                document = load(document.path().getParent().resolve(ref.substring(0, fragment)).normalize());
            }
            schema = document.root().at(ref.substring(fragment + 1));
        }
        if (schema.has("$ref")) throw new IllegalArgumentException("Cyclic contract schema reference");
        return new Resolved(document, schema);
    }

    private Document load(Path path) throws Exception {
        Document document = documents.get(path);
        if (document == null) {
            document = new Document(yaml.readTree(Files.readString(path)), path);
            documents.put(path, document);
        }
        return document;
    }

    private Map<String, JsonNode> parameters(Document document, JsonNode operation) throws Exception {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        for (JsonNode value : operation.path("parameters")) {
            JsonNode parameter = resolve(document, value).node();
            String key = parameter.path("in").asText() + ":" + parameter.path("name").asText();
            assertThat(result.put(key, parameter)).as("Duplicate operation parameter %s", key).isNull();
        }
        return result;
    }

    private static Map<String, JsonNode> operations(JsonNode document) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        document.path("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(method -> {
            if (METHODS.contains(method.getKey()) && (inScope(path.getKey()) || strings(method.getValue().path("tags")).contains("reminder"))) {
                result.put(method.getKey() + " " + path.getKey(), method.getValue());
            }
        }));
        return result;
    }

    private static boolean inScope(String path) {
        return path.equals("/api/me/reminder") || path.startsWith("/api/me/reminder/")
                || path.equals("/api/reminders") || path.startsWith("/api/reminders/")
                || path.equals("/api/public/reminder") || path.startsWith("/api/public/reminder/");
    }

    private static Set<String> paths(Map<String, JsonNode> operations) {
        Set<String> result = new LinkedHashSet<>();
        operations.keySet().forEach(operation -> result.add(operation.substring(operation.indexOf(' ') + 1)));
        return result;
    }

    private static Set<Map<String, Set<String>>> effectiveSecurity(JsonNode document, JsonNode operation) {
        return security(operation.has("security") ? operation.path("security") : document.path("security"));
    }

    private static Set<Map<String, Set<String>>> security(JsonNode requirements) {
        if (requirements.isMissingNode() || requirements.isArray() && requirements.isEmpty()) return Set.of(Map.of());
        assertThat(requirements.isArray()).as("Security must be an array").isTrue();
        Set<Map<String, Set<String>>> result = new LinkedHashSet<>();
        for (JsonNode requirement : requirements) {
            assertThat(requirement.isObject()).as("Security requirement must be an object").isTrue();
            Map<String, Set<String>> schemes = new LinkedHashMap<>();
            requirement.fields().forEachRemaining(scheme -> {
                assertThat(scheme.getValue().isArray()).as("Security scopes must be an array").isTrue();
                schemes.put(scheme.getKey(), strings(scheme.getValue()));
            });
            result.add(schemes);
        }
        return result;
    }

    private static void compare(Object expected, Object actual, String location, List<String> mismatches) {
        if (!Objects.equals(expected, actual)) mismatches.add(location + " expected=" + expected + " actual=" + actual);
    }

    private static Set<String> fields(JsonNode node) {
        Set<String> result = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(result::add);
        return result;
    }

    private static Set<String> strings(JsonNode node) {
        Set<String> result = new LinkedHashSet<>();
        node.forEach(value -> result.add(value.asText()));
        return result;
    }

    private static Set<String> types(JsonNode schema) {
        return schema.path("type").isArray() ? strings(schema.path("type"))
                : schema.has("type") ? Set.of(schema.path("type").asText()) : Set.of();
    }

    private record Document(JsonNode root, Path path) { }
    private record Resolved(Document document, JsonNode node) { }
}
