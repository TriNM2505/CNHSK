package com.cnhsk.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
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

import com.cnhsk.learning.reference.web.AdminReferenceController;
import com.cnhsk.learning.reference.web.PublicReferenceController;
import com.cnhsk.learning.reference.web.ReferenceReportController;
import com.cnhsk.support.AbstractIntegrationTest;
import com.cnhsk.shared.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.junit.jupiter.api.Test;
import org.apache.commons.csv.CSVFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true",
        "springdoc.packages-to-scan=com.cnhsk.learning.reference.web"})
class ReferenceContractTest extends AbstractIntegrationTest {
    private static final Path CONTRACT = Path.of("specs/018-reference-resources/contracts/openapi-references.yaml");
    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace");
    private static final Set<String> ANNOTATIONS = Set.of("description", "title", "example", "examples", "externalDocs", "$comment");
    private static final Set<Class<?>> CONTROLLERS = Set.of(PublicReferenceController.class,
            AdminReferenceController.class, ReferenceReportController.class);
    private static final Set<String> OPERATIONS = Set.of("get /api/public/references", "get /api/public/references/{id}",
            "post /api/references/{id}/report", "get /api/admin/references", "post /api/admin/references",
            "get /api/admin/references/{id}", "put /api/admin/references/{id}",
            "post /api/admin/references/{id}/publish", "post /api/admin/references/{id}/hide",
            "post /api/admin/references/{id}/unhide");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlers;
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    private final Map<Path, Document> documents = new HashMap<>();

    @Test
    @Transactional
    void AT19_demoCsvCreatesAndPublishesTwelveResourcesThroughEditorHttpApi() throws Exception {
        Path csv = Path.of("docs/demo-data/09-references.csv");
        byte[] bytes = Files.readAllBytes(csv);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).doesNotStartWith("\uFEFF");
        long editor = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,?) RETURNING id",
                Long.class, UUID.randomUUID() + "@example.test",
                new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString()), "Reference demo editor");
        String access = jwt.issueAccessToken(editor, Set.of("CONTENT_EDITOR"), false, UUID.randomUUID());
        Map<String, Set<Long>> idsByKind = new LinkedHashMap<>();
        Set<String> uniqueUrls = new LinkedHashSet<>();
        try (var reader = Files.newBufferedReader(csv, StandardCharsets.UTF_8);
             var parser = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {
            assertThat(parser.getHeaderNames()).containsExactly("kind", "title", "description", "url", "thumbnailUrl",
                    "hskMin", "hskMax", "tags", "sortOrder", "author", "publisher", "publishedYear", "isAffiliate");
            var rows = parser.getRecords();
            assertThat(rows).hasSize(12);
            for (var row : rows) {
                assertThat(row.isConsistent()).isTrue();
                assertThat(uniqueUrls.add(row.get("url"))).isTrue();
                ObjectNode body = mapper.createObjectNode();
                for (String name : List.of("kind", "title", "description", "url", "thumbnailUrl", "author", "publisher")) {
                    if (row.get(name).isEmpty()) body.putNull(name); else body.put(name, row.get(name));
                }
                for (String name : List.of("hskMin", "hskMax", "sortOrder", "publishedYear")) {
                    if (row.get(name).isEmpty()) body.putNull(name); else body.put(name, Integer.parseInt(row.get(name)));
                }
                for (String name : List.of("hskMin", "hskMax")) {
                    if (!body.path(name).isNull()) assertThat(body.path(name).asInt()).isBetween(1, 3);
                }
                assertThat(row.get("isAffiliate")).isEqualTo("false");
                body.put("isAffiliate", false);
                body.set("tags", mapper.readTree(row.get("tags")));
                assertThat(body.path("tags")).anySatisfy(tag -> assertThat(tag.asText()).isEqualTo("demo-reference"));
                JsonNode created = mapper.readTree(mvc.perform(post("/api/admin/references")
                        .header("Authorization", "Bearer " + access).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(body))).andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsByteArray());
                assertThat(created.path("status").asText()).isEqualTo("DRAFT");
                long id = created.path("id").asLong();
                idsByKind.computeIfAbsent(row.get("kind"), ignored -> new LinkedHashSet<>()).add(id);
                JsonNode published = mapper.readTree(mvc.perform(post("/api/admin/references/{id}/publish", id)
                        .header("Authorization", "Bearer " + access)).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsByteArray());
                assertThat(published.path("status").asText()).isEqualTo("PUBLISHED");
                JsonNode publicItem = mapper.readTree(mvc.perform(get("/api/public/references/{id}", id))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
                for (String name : List.of("kind", "title", "description", "url", "thumbnailUrl", "hskMin", "hskMax", "tags")) {
                    assertThat(publicItem.path(name)).as(name).isEqualTo(body.path(name));
                }
                for (String name : List.of("createdBy", "status", "reportCount", "lastReportedAt", "sortOrder")) {
                    assertThat(publicItem.has(name)).as("private field %s", name).isFalse();
                }
                if (row.get("kind").equals("BOOK")) {
                    for (String name : List.of("author", "publisher", "publishedYear", "isAffiliate")) {
                        assertThat(publicItem.path(name)).as(name).isEqualTo(body.path(name));
                    }
                }
            }
        }
        assertThat(idsByKind.keySet()).containsExactlyInAnyOrder("YOUTUBE_CHANNEL", "PODCAST", "BOOK");
        for (var entry : idsByKind.entrySet()) {
            assertThat(entry.getValue()).hasSize(4);
            JsonNode page = mapper.readTree(mvc.perform(get("/api/public/references").param("kind", entry.getKey())
                    .param("tag", "demo-reference").param("size", "50").header("Authorization", "Bearer " + access))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
            assertThat(page.path("total").asLong()).isEqualTo(4);
            Set<Long> actualIds = new LinkedHashSet<>();
            page.path("items").forEach(item -> actualIds.add(item.path("id").asLong()));
            assertThat(actualIds).containsExactlyInAnyOrderElementsOf(entry.getValue());
        }
    }

    @Test
    void AT19_runtimePathsMethodsStatusesParametersSchemasAndSecurityMatchTheContractBothWays() throws Exception {
        var parsed = new OpenAPIV3Parser().readLocation(CONTRACT.toAbsolutePath().toUri().toString(), null, null);
        assertThat(parsed.getMessages()).as("Reference YAML parser messages").isEmpty();
        assertThat(parsed.getOpenAPI()).isNotNull();
        Document source = load(CONTRACT.toAbsolutePath().normalize());
        Document runtime = new Document(mapper.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()), null);
        Map<String, JsonNode> expected = operations(source.root());
        Map<String, JsonNode> actual = operations(runtime.root());
        List<String> mismatches = new ArrayList<>();
        compare(OPERATIONS, expected.keySet(), "locked operations", mismatches);
        compare(expected.keySet(), actual.keySet(), "runtime operations", mismatches);
        compare(paths(expected), paths(actual), "runtime paths", mismatches);
        Set<String> registered = new LinkedHashSet<>();
        handlers.getHandlerMethods().forEach((mapping, handler) -> {
            if (CONTROLLERS.contains(handler.getBeanType())) mapping.getPatternValues().forEach(path ->
                    mapping.getMethodsCondition().getMethods().forEach(method ->
                            registered.add(method.name().toLowerCase(Locale.ROOT) + " " + path)));
        });
        compare(OPERATIONS, registered, "registered controller operations", mismatches);
        for (var entry : expected.entrySet()) {
            String location = entry.getKey();
            JsonNode wanted = entry.getValue();
            JsonNode found = actual.get(location);
            if (found == null) continue;
            compare(wanted.path("operationId"), found.path("operationId"), location + " operationId", mismatches);
            compare(fields(wanted.path("responses")), fields(found.path("responses")), location + " statuses", mismatches);
            compare(security(source.root(), wanted), security(runtime.root(), found), location + " security", mismatches);
            Map<String, JsonNode> left = parameters(source, wanted);
            Map<String, JsonNode> right = parameters(runtime, found);
            compare(left.keySet(), right.keySet(), location + " parameters", mismatches);
            for (var parameter : left.entrySet()) {
                JsonNode target = right.get(parameter.getKey());
                if (target == null) continue;
                compare(parameter.getValue().path("required").asBoolean(), target.path("required").asBoolean(),
                        location + " " + parameter.getKey() + " required", mismatches);
                schema(source, parameter.getValue().path("schema"), runtime, target.path("schema"),
                        location + " " + parameter.getKey(), mismatches);
            }
            compare(wanted.path("requestBody").path("required").asBoolean(), found.path("requestBody").path("required").asBoolean(),
                    location + " requestBody required", mismatches);
            content(source, wanted.path("requestBody"), runtime, found.path("requestBody"), location + " requestBody", mismatches);
            for (String code : fields(wanted.path("responses"))) {
                Resolved a = resolve(source, wanted.path("responses").path(code));
                Resolved b = resolve(runtime, found.path("responses").path(code));
                content(a.document(), a.node(), b.document(), b.node(), location + " response " + code, mismatches);
            }
        }
        for (String name : List.of("cookieAuth", "bearerAuth")) {
            compare(semantic(source.root().path("components").path("securitySchemes").path(name)),
                    semantic(runtime.root().path("components").path("securitySchemes").path(name)),
                    "security scheme " + name, mismatches);
        }
        Path directory = Path.of("target", "018-contract-evidence");
        Files.createDirectories(directory);
        String id = UUID.randomUUID().toString();
        mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve(id + "-generated.json").toFile(), runtime.root());
        mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve(id + "-audit.json").toFile(), Map.of(
                "expectedOperations", expected.keySet(), "actualOperations", actual.keySet(),
                "registeredOperations", registered, "mismatchCount", mismatches.size(), "mismatches", mismatches));
        assertThat(mismatches).as("Reference contract mismatches; evidence=%s", directory).isEmpty();
    }

    private void content(Document source, JsonNode expected, Document runtime, JsonNode actual,
            String location, List<String> mismatches) throws Exception {
        compare(fields(expected.path("content")), fields(actual.path("content")), location + " media types", mismatches);
        for (String media : fields(expected.path("content"))) schema(source, expected.path("content").path(media).path("schema"),
                runtime, actual.path("content").path(media).path("schema"), location + " " + media, mismatches);
    }

    private void schema(Document source, JsonNode expected, Document runtime, JsonNode actual,
            String location, List<String> mismatches) throws Exception {
        JsonNode left = normalized(source, expected);
        JsonNode right = normalized(runtime, actual);
        compareSchema(left, right, location, mismatches);
    }

    private void compareSchema(JsonNode left, JsonNode right, String location, List<String> mismatches) {
        if (right.isMissingNode()) { mismatches.add(location + " missing/unresolved schema"); return; }
        Set<String> expected = fields(left), actual = fields(right);
        expected.removeAll(ANNOTATIONS); actual.removeAll(ANNOTATIONS);
        compare(expected, actual, location + " schema keywords", mismatches);
        Set<String> keys = new LinkedHashSet<>(expected); keys.addAll(actual);
        for (String key : keys) {
            switch (key) {
                case "properties" -> {
                    compare(fields(left.path(key)), fields(right.path(key)), location + " properties", mismatches);
                    left.path(key).fields().forEachRemaining(property -> compareSchema(property.getValue(),
                            right.path(key).path(property.getKey()), location + "." + property.getKey(), mismatches));
                }
                case "items" -> compareSchema(left.path(key), right.path(key), location + "[]", mismatches);
                case "type" -> compare(types(left), types(right), location + " type", mismatches);
                case "required", "enum" -> compare(strings(left.path(key)), strings(right.path(key)), location + " " + key, mismatches);
                default -> compare(semantic(left.path(key)), semantic(right.path(key)), location + " " + key, mismatches);
            }
        }
    }

    private JsonNode normalized(Document document, JsonNode schema) throws Exception {
        Resolved resolved = resolve(document, schema);
        JsonNode node = resolved.node();
        if (!node.isObject()) return node;
        ObjectNode result = mapper.createObjectNode();
        for (JsonNode part : node.path("allOf")) merge(result, normalized(resolved.document(), part));
        var fields = node.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (entry.getKey().equals("allOf") || entry.getKey().equals("$ref")) continue;
            JsonNode value = entry.getValue();
            if (entry.getKey().equals("properties")) {
                ObjectNode properties = mapper.createObjectNode();
                var entries = value.fields();
                while (entries.hasNext()) {
                    var property = entries.next();
                    properties.set(property.getKey(), normalized(resolved.document(), property.getValue()));
                }
                ObjectNode container = mapper.createObjectNode(); container.set("properties", properties); merge(result, container);
            } else if (entry.getKey().equals("items")) result.set("items", normalized(resolved.document(), value));
            else if (entry.getKey().equals("required")) {
                Set<String> required = strings(result.path("required")); required.addAll(strings(value));
                result.putArray("required").addAll(required.stream().map(mapper.getNodeFactory()::textNode).toList());
            } else result.set(entry.getKey(), value);
        }
        return result;
    }

    private void merge(ObjectNode target, JsonNode addition) {
        addition.fields().forEachRemaining(entry -> {
            if (entry.getKey().equals("properties")) {
                ObjectNode properties = target.has("properties") ? (ObjectNode) target.path("properties") : target.putObject("properties");
                entry.getValue().fields().forEachRemaining(property -> {
                    if (properties.has(property.getKey())) assertThat(properties.path(property.getKey())).isEqualTo(property.getValue());
                    properties.set(property.getKey(), property.getValue());
                });
            } else if (entry.getKey().equals("required")) {
                Set<String> required = strings(target.path("required")); required.addAll(strings(entry.getValue()));
                target.putArray("required").addAll(required.stream().map(mapper.getNodeFactory()::textNode).toList());
            } else {
                if (target.has(entry.getKey()) && !ANNOTATIONS.contains(entry.getKey())) assertThat(target.path(entry.getKey())).isEqualTo(entry.getValue());
                target.set(entry.getKey(), entry.getValue());
            }
        });
    }

    private Resolved resolve(Document document, JsonNode node) throws Exception {
        for (int depth = 0; depth < 32 && node.has("$ref"); depth++) {
            String ref = node.path("$ref").asText();
            int fragment = ref.indexOf('#');
            if (fragment < 0) throw new IllegalArgumentException("Unsupported schema reference");
            if (fragment > 0) {
                if (document.path() == null) throw new IllegalArgumentException("External runtime reference");
                document = load(document.path().getParent().resolve(ref.substring(0, fragment)).normalize());
            }
            node = document.root().at(ref.substring(fragment + 1));
        }
        if (node.has("$ref")) throw new IllegalArgumentException("Cyclic schema reference");
        return new Resolved(document, node);
    }
    private Document load(Path path) throws Exception {
        Document value = documents.get(path);
        if (value == null) { value = new Document(yaml.readTree(Files.readString(path)), path); documents.put(path, value); }
        return value;
    }
    private Map<String, JsonNode> parameters(Document document, JsonNode operation) throws Exception {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        for (JsonNode raw : operation.path("parameters")) {
            JsonNode parameter = resolve(document, raw).node();
            String key = parameter.path("in").asText() + ":" + parameter.path("name").asText();
            assertThat(result.put(key, parameter)).as("Duplicate parameter %s", key).isNull();
        }
        return result;
    }
    private static Map<String, JsonNode> operations(JsonNode document) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        document.path("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(method -> {
            if (METHODS.contains(method.getKey()) && (path.getKey().matches("/api/(public/|admin/)?references(?:/.*)?")
                    || strings(method.getValue().path("tags")).stream().anyMatch(tag -> tag.startsWith("references-"))))
                result.put(method.getKey() + " " + path.getKey(), method.getValue());
        }));
        return result;
    }
    private static Set<String> paths(Map<String, JsonNode> operations) {
        Set<String> result = new LinkedHashSet<>(); operations.keySet().forEach(key -> result.add(key.substring(key.indexOf(' ') + 1))); return result;
    }
    private static Set<Map<String, Set<String>>> security(JsonNode root, JsonNode operation) {
        JsonNode values = operation.has("security") ? operation.path("security") : root.path("security");
        Set<Map<String, Set<String>>> result = new LinkedHashSet<>();
        values.forEach(value -> { Map<String, Set<String>> term = new LinkedHashMap<>(); value.fields().forEachRemaining(entry -> term.put(entry.getKey(), strings(entry.getValue()))); result.add(term); });
        return result.isEmpty() ? Set.of(Map.of()) : result;
    }
    private static Object semantic(JsonNode node) {
        if (node.isObject()) { Map<String, Object> result = new LinkedHashMap<>(); node.fields().forEachRemaining(entry -> { if (!ANNOTATIONS.contains(entry.getKey())) result.put(entry.getKey(), semantic(entry.getValue())); }); return result; }
        if (node.isArray()) { List<Object> result = new ArrayList<>(); node.forEach(value -> result.add(semantic(value))); return result; }
        return node.isNumber() ? node.decimalValue().stripTrailingZeros() : node;
    }
    private static void compare(Object expected, Object actual, String location, List<String> mismatches) { if (!Objects.equals(expected, actual)) mismatches.add(location + " expected=" + expected + " actual=" + actual); }
    private static Set<String> fields(JsonNode node) { Set<String> result = new LinkedHashSet<>(); node.fieldNames().forEachRemaining(result::add); return result; }
    private static Set<String> strings(JsonNode node) { Set<String> result = new LinkedHashSet<>(); node.forEach(value -> result.add(value.asText())); return result; }
    private static Set<String> types(JsonNode node) { return node.path("type").isArray() ? strings(node.path("type")) : node.has("type") ? Set.of(node.path("type").asText()) : Set.of(); }
    private record Document(JsonNode root, Path path) { }
    private record Resolved(Document document, JsonNode node) { }
}
