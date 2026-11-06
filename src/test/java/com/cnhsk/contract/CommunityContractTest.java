package com.cnhsk.contract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

import com.cnhsk.auth.service.BootstrapAdminRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.packages-to-scan=com.cnhsk.community",
        "spring.datasource.url=jdbc:h2:mem:community-contract;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
class CommunityContractTest {

    private static final Path CONTRACT_FILE = Path.of(
            "specs", "009-community-blog-moderation", "contracts", "openapi-community.yaml");
    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "put", "post", "delete", "options", "head", "patch", "trace");
    private static final Map<OperationKey, ContractOperation> EXPECTED = expectedOperations();

    // Documentation needs no bootstrap account or database writes, regardless of local credentials.
    @MockitoBean
    private BootstrapAdminRunner bootstrapAdminRunner;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void AC01_sourceContractLocksAllPathsMethodsOperationIdsAndResponses() throws IOException {
        JsonNode document = readSource();
        Map<OperationKey, ContractOperation> source = readOperations(document);

        assertThat(document.path("paths").size()).isEqualTo(21);
        assertThat(source).hasSize(24).containsExactlyInAnyOrderEntriesOf(EXPECTED);
        JsonNode deleted = document.path("paths").path("/api/community/comments/{id}")
                .path("delete").path("responses").path("204");
        assertThat(deleted.has("content")).as("deleteComment 204 must have no content").isFalse();
    }

    @Test
    void AC01_registeredOperationsAndRuntimeDocumentationMatchTheSource() throws Exception {
        Map<OperationKey, ContractOperation> source = readOperations(readSource());
        Set<OperationKey> registered = registeredOperations();
        JsonNode document = runtimeDocument();
        Map<OperationKey, ContractOperation> documented = readOperations(document);

        assertRuntimeContract(source, registered, documented);
        System.out.println(coverageSummary(source, registered, documented));
        missingOperationKeys(source, registered).forEach(key -> System.out.println("missing operation: " + key));
    }

    @Test
    void publishedPostPaginationSchemasMatchTheLockedContract() throws Exception {
        assertPaginationSchemas(runtimeDocument(), "/api/public/community/posts");
    }

    @Test
    void ownPostPaginationSchemasMatchTheLockedContract() throws Exception {
        assertPaginationSchemas(runtimeDocument(), "/api/community/posts/mine");
    }

    @Test
    void commentDepthRetainsTheContractIntegerRange() throws Exception {
        JsonNode expected = readSource().path("components").path("schemas")
                .path("Comment").path("properties").path("depth");
        JsonNode actual = runtimeDocument().path("components").path("schemas")
                .path("Comment").path("properties").path("depth");
        for (String constraint : List.of("type", "minimum", "maximum")) {
            assertThat(actual.path(constraint)).as("Comment.depth %s", constraint)
                    .isEqualTo(expected.path(constraint));
        }
    }

    private void assertPaginationSchemas(JsonNode document, String path) throws IOException {
        JsonNode expected = readSource().path("components").path("parameters");
        JsonNode parameters = document.path("paths").path(path).path("get").path("parameters");
        for (String name : List.of("page", "size")) {
            List<JsonNode> matches = StreamSupport.stream(parameters.spliterator(), false)
                    .filter(parameter -> name.equals(parameter.path("name").asText())
                            && "query".equals(parameter.path("in").asText()))
                    .map(parameter -> parameter.path("schema")).toList();
            assertThat(matches).as("%s query parameter %s", path, name).hasSize(1);
            assertThat(matches.get(0)).as("%s query schema %s", path, name)
                    .isEqualTo(expected.path("page".equals(name) ? "Page" : "Size").path("schema"));
        }
    }

    private JsonNode runtimeDocument() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String snapshot = System.getProperty("community.openapi.snapshot");
        if (snapshot != null) {
            Files.writeString(Path.of(snapshot), json);
        }
        JsonNode document = objectMapper.readTree(json);
        assertValidOpenApi(json, document);
        return document;
    }

    @Test
    void AC01_extraHttpMethodIsRejected() {
        OperationKey extra = key("PATCH", "/api/community/posts");
        Set<OperationKey> registered = new HashSet<>(EXPECTED.keySet());
        registered.add(extra);
        Map<OperationKey, ContractOperation> documented = new LinkedHashMap<>(EXPECTED);
        documented.put(extra, operation("unexpected", "200"));
        assertThatThrownBy(() -> assertRuntimeContract(EXPECTED, registered, documented))
                .isInstanceOf(AssertionError.class).hasMessageContaining("Registered operations");
    }

    @Test
    void AC01_registeredOperationHiddenFromDocumentationIsRejected() {
        Map<OperationKey, ContractOperation> documented = new LinkedHashMap<>(EXPECTED);
        documented.remove(firstKey());
        assertThatThrownBy(() -> assertRuntimeContract(EXPECTED, EXPECTED.keySet(), documented))
                .isInstanceOf(AssertionError.class).hasMessageContaining("Documented operations");
    }

    @Test
    void AC01_extraResponseStatusIsRejected() {
        Map<OperationKey, ContractOperation> documented = new LinkedHashMap<>(EXPECTED);
        documented.put(firstKey(), operation("listPublishedPosts", "200", "422", "500"));
        assertThatThrownBy(() -> assertRuntimeContract(EXPECTED, EXPECTED.keySet(), documented))
                .isInstanceOf(AssertionError.class).hasMessageContaining("Responses for");
    }

    @Test
    void AC01_missingResponseStatusIsRejected() {
        Map<OperationKey, ContractOperation> documented = new LinkedHashMap<>(EXPECTED);
        documented.put(firstKey(), operation("listPublishedPosts", "200"));
        assertThatThrownBy(() -> assertRuntimeContract(EXPECTED, EXPECTED.keySet(), documented))
                .isInstanceOf(AssertionError.class).hasMessageContaining("Responses for");
    }

    @Test
    void AC01_wrongOperationIdIsRejected() {
        Map<OperationKey, ContractOperation> documented = new LinkedHashMap<>(EXPECTED);
        documented.put(firstKey(), operation("incorrectListPublishedPosts", "200", "422"));
        assertThatThrownBy(() -> assertRuntimeContract(EXPECTED, EXPECTED.keySet(), documented))
                .isInstanceOf(AssertionError.class).hasMessageContaining("Operation ID for");
    }

    @Test
    void AC01_documentedOperationWithoutARegisteredHandlerIsRejected() {
        OperationKey extra = key("GET", "/api/community/unregistered");
        Map<OperationKey, ContractOperation> documented = new LinkedHashMap<>(EXPECTED);
        documented.put(extra, operation("unregisteredOperation", "200"));
        assertThatThrownBy(() -> assertRuntimeContract(EXPECTED, EXPECTED.keySet(), documented))
                .isInstanceOf(AssertionError.class).hasMessageContaining("Documented operations");
    }

    @Test
    void AC01_duplicateMappingCannotOverwriteAnExistingOperation() {
        Map<OperationKey, String> mappings = new LinkedHashMap<>();
        addMappings(mappings, Set.of(RequestMethod.GET), Set.of(firstKey().path()), "first handler");

        assertThatThrownBy(() -> addMappings(mappings, Set.of(RequestMethod.GET),
                Set.of(firstKey().path()), "second handler"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("Duplicate or ambiguous mapping");
        assertThat(mappings).containsEntry(firstKey(), "first handler").hasSize(1);
    }

    @Test
    void AC01_unrestrictedHttpMethodMappingIsRejected() {
        assertThatThrownBy(() -> addMappings(new LinkedHashMap<>(), Set.of(),
                Set.of(firstKey().path()), "unrestricted handler"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("Explicit HTTP methods");
    }

    @Test
    void AC01_emptyRuntimeIsRejectedAndMissingOperationsHaveStableSortedCoverage() {
        assertThatThrownBy(() -> assertRuntimeContract(EXPECTED, Set.of(), Map.of()))
                .isInstanceOf(AssertionError.class).hasMessageContaining("Registered operations");
        assertThat(coverageSummary(EXPECTED, Set.of(), Map.of()))
                .isEqualTo("source=24, runtime=0, documented=0, missing=24");
        assertThat(missingOperationKeys(EXPECTED, Set.of())).hasSize(24).isSorted()
                .containsExactly(
                        "DELETE /api/community/comments/{id}",
                        "DELETE /api/community/posts/{id}/like",
                        "GET /api/community/moderation/posts",
                        "GET /api/community/moderation/posts/{id}",
                        "GET /api/community/moderation/posts/{id}/comments",
                        "GET /api/community/moderation/reports",
                        "GET /api/community/moderation/reports/{id}",
                        "GET /api/community/posts/mine",
                        "GET /api/community/posts/mine/{id}",
                        "GET /api/community/posts/{id}/viewer-state",
                        "GET /api/public/community/posts",
                        "GET /api/public/community/posts/{id}",
                        "GET /api/public/community/posts/{id}/comments",
                        "PATCH /api/community/moderation/comments/{id}",
                        "PATCH /api/community/moderation/posts/{id}/approve",
                        "PATCH /api/community/moderation/posts/{id}/reject",
                        "PATCH /api/community/moderation/reports/{id}",
                        "POST /api/community/comments/{id}/replies",
                        "POST /api/community/posts",
                        "POST /api/community/posts/{id}/comments",
                        "POST /api/community/posts/{id}/like",
                        "POST /api/community/reports",
                        "PUT /api/community/comments/{id}",
                        "PUT /api/community/posts/{id}");
    }

    @Test
    void AC01_oneMissingRegisteredAndDocumentedOperationIsRejected() {
        Map<OperationKey, ContractOperation> incomplete = new LinkedHashMap<>(EXPECTED);
        incomplete.remove(key("PATCH", "/api/community/moderation/comments/{id}"));
        assertThat(incomplete).hasSize(23);
        assertThatThrownBy(() -> assertRuntimeContract(EXPECTED, incomplete.keySet(), incomplete))
                .isInstanceOf(AssertionError.class).hasMessageContaining("Registered operations");
    }

    @Test
    void AC01_completeRuntimeWithAllTwentyFourOperationsIsAccepted() {
        assertRuntimeContract(EXPECTED, EXPECTED.keySet(), EXPECTED);
        assertThat(coverageSummary(EXPECTED, EXPECTED.keySet(), EXPECTED))
                .isEqualTo("source=24, runtime=24, documented=24, missing=0");
    }

    @Test
    void AC01_missingNullAndEmptyPathsAreRejected() throws IOException {
        for (String json : List.of("{}", "{\"paths\":null}", "{\"paths\":{}}")) {
            JsonNode document = objectMapper.readTree(json);
            assertThatThrownBy(() -> readOperations(document)).isInstanceOf(AssertionError.class)
                    .hasMessageContaining("OpenAPI paths");
        }
    }

    @Test
    void AC01_mappingSelectionUsesPackageAndRouteBoundaries() {
        assertThat(isCommunityPackage("com.cnhsk.community")).isTrue();
        assertThat(isCommunityPackage("com.cnhsk.community.post.web")).isTrue();
        assertThat(isCommunityPackage("com.cnhsk.communityOther")).isFalse();
        assertThat(isCommunityPath("/api/community")).isTrue();
        assertThat(isCommunityPath("/api/public/community/posts")).isTrue();
        assertThat(isCommunityPath("/api/communityOther/posts")).isFalse();
        assertThat(isCommunityPath("/api/public/communityOther")).isFalse();
    }

    @Test
    void AC01_mappingCollectorExpandsEveryExplicitMethodAndPath() {
        Map<OperationKey, String> mappings = new LinkedHashMap<>();
        addMappings(mappings, Set.of(RequestMethod.GET, RequestMethod.POST),
                Set.of("/api/community/posts", "/incorrect-prefix/posts"), "community handler");

        assertThat(mappings.keySet()).containsExactlyInAnyOrder(
                key("GET", "/api/community/posts"), key("POST", "/api/community/posts"),
                key("GET", "/incorrect-prefix/posts"), key("POST", "/incorrect-prefix/posts"));
    }

    private JsonNode readSource() throws IOException {
        String yaml = Files.readString(CONTRACT_FILE);
        JsonNode document = new ObjectMapper(new YAMLFactory()).readTree(yaml);
        assertValidOpenApi(yaml, document);
        return document;
    }

    private static void assertValidOpenApi(String contents, JsonNode document) {
        assertThat(document).isNotNull();
        assertThat(document.isObject()).as("OpenAPI document must be an object").isTrue();
        assertThat(document.path("openapi").asText()).startsWith("3.1.");
        assertLocalReferences(document, document);
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        var parsed = new OpenAPIV3Parser().readContents(contents, null, options);
        assertThat(parsed.getMessages()).as("OpenAPI parser messages").isEmpty();
        assertThat(parsed.getOpenAPI()).as("Parsed OpenAPI document").isNotNull();
    }

    private static void assertLocalReferences(JsonNode root, JsonNode node) {
        if (node.isObject() && node.has("$ref")) {
            JsonNode reference = node.get("$ref");
            assertThat(reference.isTextual()).as("Reference must be text").isTrue();
            String value = reference.asText();
            assertThat(value).as("Only local references are permitted").startsWith("#/");
            assertThat(root.at(value.substring(1)).isMissingNode())
                    .as("Reference %s must resolve in the original document", value).isFalse();
        }
        node.forEach(child -> assertLocalReferences(root, child));
    }

    private static Map<OperationKey, ContractOperation> readOperations(JsonNode document) {
        JsonNode paths = document.path("paths");
        assertThat(paths.isObject()).as("OpenAPI paths must be an object").isTrue();
        assertThat(paths.isEmpty()).as("OpenAPI paths must be nonempty").isFalse();
        Map<OperationKey, ContractOperation> operations = new LinkedHashMap<>();
        Set<String> operationIds = new HashSet<>();
        paths.properties().forEach(pathEntry -> {
            assertThat(pathEntry.getValue().isObject()).as("Path item %s", pathEntry.getKey()).isTrue();
            pathEntry.getValue().properties().forEach(methodEntry -> {
                if (HTTP_METHODS.contains(methodEntry.getKey())) {
                    OperationKey key = key(methodEntry.getKey(), pathEntry.getKey());
                    JsonNode operation = methodEntry.getValue();
                    assertThat(operation.isObject()).as("Operation %s", key).isTrue();
                    JsonNode id = operation.path("operationId");
                    assertThat(id.isTextual()).as("Operation ID for %s", key).isTrue();
                    assertThat(id.asText()).as("Operation ID for %s", key).isNotBlank();
                    assertThat(operationIds.add(id.asText())).as("Unique operation ID %s", id.asText()).isTrue();
                    JsonNode responses = operation.path("responses");
                    assertThat(responses.isObject()).as("Responses for %s", key).isTrue();
                    assertThat(responses.isEmpty()).as("Nonempty responses for %s", key).isFalse();
                    Set<String> statuses = new HashSet<>();
                    responses.fieldNames().forEachRemaining(statuses::add);
                    assertThat(operations).as("Duplicate operation %s", key).doesNotContainKey(key);
                    operations.put(key, new ContractOperation(id.asText(), Set.copyOf(statuses)));
                }
            });
        });
        return operations;
    }

    private Set<OperationKey> registeredOperations() {
        Map<OperationKey, String> operations = new LinkedHashMap<>();
        handlerMapping.getHandlerMethods().forEach((mapping, handler) -> {
            String packageName = ClassUtils.getUserClass(handler.getBeanType()).getPackageName();
            Set<String> paths = mapping.getPatternValues();
            if (isCommunityPackage(packageName) || paths.stream().anyMatch(CommunityContractTest::isCommunityPath)) {
                addMappings(operations, mapping.getMethodsCondition().getMethods(), paths, handler.toString());
            }
        });
        return Set.copyOf(operations.keySet());
    }

    private static boolean isCommunityPackage(String packageName) {
        return packageName.equals("com.cnhsk.community") || packageName.startsWith("com.cnhsk.community.");
    }

    private static boolean isCommunityPath(String path) {
        return path.equals("/api/community") || path.startsWith("/api/community/")
                || path.equals("/api/public/community") || path.startsWith("/api/public/community/");
    }

    private static void addMappings(Map<OperationKey, String> operations, Collection<RequestMethod> methods,
            Collection<String> paths, String handler) {
        assertThat(methods).as("Explicit HTTP methods for %s", handler).isNotEmpty();
        assertThat(paths).as("Explicit paths for %s", handler).isNotEmpty();
        for (RequestMethod method : methods) {
            for (String path : paths) {
                OperationKey key = key(method.name(), path);
                assertThat(operations).as("Duplicate or ambiguous mapping %s: %s and %s",
                        key, operations.get(key), handler).doesNotContainKey(key);
                operations.put(key, handler);
            }
        }
    }

    private static void assertRuntimeContract(Map<OperationKey, ContractOperation> source,
            Set<OperationKey> registered, Map<OperationKey, ContractOperation> documented) {
        assertThat(source).as("Source operations must match the locked twenty-four-operation inventory")
                .hasSize(24).containsExactlyInAnyOrderEntriesOf(EXPECTED);
        assertThat(registered).as("Registered operations must exactly match the source")
                .containsExactlyInAnyOrderElementsOf(source.keySet());
        assertThat(documented.keySet()).as("Documented operations must exactly match the source")
                .containsExactlyInAnyOrderElementsOf(source.keySet());
        documented.forEach((key, operation) -> {
            assertThat(operation.operationId()).as("Operation ID for %s", key)
                    .isEqualTo(source.get(key).operationId());
            assertThat(operation.responses()).as("Responses for %s", key)
                    .containsExactlyInAnyOrderElementsOf(source.get(key).responses());
        });
    }

    private static String coverageSummary(Map<OperationKey, ContractOperation> source,
            Set<OperationKey> registered, Map<OperationKey, ContractOperation> documented) {
        return "source=" + source.size() + ", runtime=" + registered.size() + ", documented=" + documented.size()
                + ", missing=" + missingOperationKeys(source, registered).size();
    }

    private static List<String> missingOperationKeys(Map<OperationKey, ContractOperation> source,
            Set<OperationKey> registered) {
        return source.keySet().stream().filter(key -> !registered.contains(key))
                .map(OperationKey::toString).sorted().toList();
    }

    private static OperationKey firstKey() {
        return key("GET", "/api/public/community/posts");
    }

    private static OperationKey key(String method, String path) {
        return new OperationKey(method.toUpperCase(Locale.ROOT), path);
    }

    private static ContractOperation operation(String id, String... statuses) {
        return new ContractOperation(id, Set.of(statuses));
    }

    private static Map<OperationKey, ContractOperation> expectedOperations() {
        return Map.ofEntries(
                Map.entry(key("GET", "/api/public/community/posts"),
                        operation("listPublishedPosts", "200", "422")),
                Map.entry(key("GET", "/api/public/community/posts/{id}"),
                        operation("getPublishedPost", "200", "404")),
                Map.entry(key("GET", "/api/public/community/posts/{id}/comments"),
                        operation("listPostComments", "200", "404")),
                Map.entry(key("POST", "/api/community/posts"),
                        operation("createPost", "201", "401", "403", "422", "429")),
                Map.entry(key("PUT", "/api/community/posts/{id}"),
                        operation("updatePost", "200", "401", "403", "409", "422")),
                Map.entry(key("GET", "/api/community/posts/mine"),
                        operation("listMyPosts", "200", "401", "403", "422")),
                Map.entry(key("GET", "/api/community/posts/mine/{id}"),
                        operation("getMyPost", "200", "401", "403")),
                Map.entry(key("GET", "/api/community/posts/{id}/viewer-state"),
                        operation("getViewerState", "200", "401", "403", "404")),
                Map.entry(key("POST", "/api/community/posts/{id}/like"),
                        operation("likePost", "200", "401", "403", "404")),
                Map.entry(key("DELETE", "/api/community/posts/{id}/like"),
                        operation("unlikePost", "200", "401", "403", "404")),
                Map.entry(key("POST", "/api/community/posts/{id}/comments"),
                        operation("createComment", "201", "401", "403", "404", "422", "429")),
                Map.entry(key("POST", "/api/community/comments/{id}/replies"),
                        operation("replyToComment", "201", "401", "403", "404", "422", "429")),
                Map.entry(key("PUT", "/api/community/comments/{id}"),
                        operation("updateComment", "200", "401", "403", "409", "422")),
                Map.entry(key("DELETE", "/api/community/comments/{id}"),
                        operation("deleteComment", "204", "401", "403")),
                Map.entry(key("POST", "/api/community/reports"),
                        operation("reportContent", "201", "401", "403", "404", "409", "422", "429")),
                Map.entry(key("GET", "/api/community/moderation/posts"),
                        operation("listPendingPosts", "200", "401", "403", "422")),
                Map.entry(key("GET", "/api/community/moderation/posts/{id}"),
                        operation("getModerationPost", "200", "401", "403", "404")),
                Map.entry(key("GET", "/api/community/moderation/posts/{id}/comments"),
                        operation("listPostCommentsForModeration", "200", "401", "403", "404")),
                Map.entry(key("PATCH", "/api/community/moderation/posts/{id}/approve"),
                        operation("approvePost", "200", "401", "403", "404", "409")),
                Map.entry(key("PATCH", "/api/community/moderation/posts/{id}/reject"),
                        operation("rejectPost", "200", "401", "403", "404", "409", "422")),
                Map.entry(key("GET", "/api/community/moderation/reports"),
                        operation("listPendingReportCases", "200", "401", "403", "422")),
                Map.entry(key("GET", "/api/community/moderation/reports/{id}"),
                        operation("getReportCase", "200", "401", "403", "404")),
                Map.entry(key("PATCH", "/api/community/moderation/reports/{id}"),
                        operation("decideReportCase", "200", "401", "403", "404", "409", "422")),
                Map.entry(key("PATCH", "/api/community/moderation/comments/{id}"),
                        operation("moderateComment", "200", "401", "403", "404", "409", "422")));
    }

    private record OperationKey(String method, String path) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    private record ContractOperation(String operationId, Set<String> responses) {
    }
}
