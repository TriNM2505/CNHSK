package com.cnhsk.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true",
        "springdoc.packages-to-scan=com.cnhsk.community.contest"})
class ContestContractTest extends AbstractIntegrationTest {
    private static final Path CONTRACT = Path.of("specs", "021-contests", "contracts", "openapi-contests.yaml");
    private static final Path EVIDENCE = Path.of("target", "021-contest-contract");
    private static final String PACKAGE = "com.cnhsk.community.contest";
    private static final String PUBLIC = "/api/public/community/contests";
    private static final String LEARNER = "/api/community/contests";
    private static final String ADMIN = "/api/admin/contests";
    private static final String WINNERS = ADMIN + "/{id}/winners";
    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace");
    private static final Set<String> OPERATIONS = Set.of(
            "get " + PUBLIC, "get " + PUBLIC + "/{id}",
            "post " + LEARNER + "/{id}/attempts", "post " + LEARNER + "/{id}/attempts/submit",
            "post " + LEARNER + "/{id}/attempts/events", "get " + LEARNER + "/{id}/results",
            "get " + ADMIN, "post " + ADMIN, "get " + ADMIN + "/{id}", "put " + ADMIN + "/{id}",
            "post " + ADMIN + "/{id}/publish", "post " + ADMIN + "/{id}/cancel",
            "get " + ADMIN + "/{id}/participants", "patch " + ADMIN + "/{id}/participants/{userId}",
            "post " + ADMIN + "/{id}/ranking/publish", "get " + WINNERS);

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlers;

    @Test
    void AT22_yamlDocumentationAndHandlersMatchPathsMethodsAndStatusesInBothDirections() throws Exception {
        Inventory source = read(sourceDocument(), false);
        JsonNode document = mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        Inventory documented = read(document, true);
        Inventory registered = registered();
        List<String> differences = mismatches(source, documented, registered);
        Files.createDirectories(EVIDENCE);
        String run = UUID.randomUUID().toString();
        mapper.writerWithDefaultPrettyPrinter().writeValue(EVIDENCE.resolve(run + "-runtime.json").toFile(), document);
        mapper.writerWithDefaultPrettyPrinter().writeValue(EVIDENCE.resolve(run + "-audit.json").toFile(), Map.of(
                "sourcePaths", source.paths(), "sourceOperations", source.operations(),
                "documentedPaths", documented.paths(), "documentedOperations", documented.operations(),
                "registeredPaths", registered.paths(), "registeredOperations", registered.operations(),
                "mismatchCount", differences.size(), "mismatches", differences));
        System.out.printf("AT-22 source=%d/%d documented=%d/%d registered=%d/%d mismatches=%d evidence=%s%n",
                source.paths().size(), source.operations().size(), documented.paths().size(), documented.operations().size(),
                registered.paths().size(), registered.operations().size(), differences.size(), EVIDENCE.resolve(run + "-audit.json"));
        assertThat(differences).as("AT-22 contest contract mismatches; evidence=%s", EVIDENCE).isEmpty();
    }

    @Test
    void AT22_missingDocumentedPathIncludingWinnersIsRejected() throws Exception {
        JsonNode source = sourceDocument();
        ObjectNode runtime = source.deepCopy();
        ((ObjectNode) runtime.path("paths")).remove(WINNERS);
        assertThat(mismatches(read(source, false), read(runtime, true), read(source, false)))
                .anySatisfy(difference -> assertThat(difference).contains("Documented paths missing=", WINNERS));
    }

    @Test
    void AT22_extraDocumentedPathIsRejected() throws Exception {
        JsonNode source = sourceDocument();
        ObjectNode runtime = source.deepCopy();
        ((ObjectNode) runtime.path("paths")).putObject(ADMIN + "/unexpected").putObject("get")
                .putObject("responses").putObject("200");
        assertThat(mismatches(read(source, false), read(runtime, true), read(source, false)))
                .anySatisfy(difference -> assertThat(difference).contains("Documented paths unexpected=", ADMIN + "/unexpected"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void AT22_missingOrExtraDocumentedHttpMethodIsRejected(boolean extra) throws Exception {
        JsonNode source = sourceDocument();
        ObjectNode runtime = source.deepCopy();
        ObjectNode path = (ObjectNode) runtime.path("paths").path(ADMIN);
        if (extra) path.putObject("delete").putObject("responses").putObject("204");
        else path.remove("post");
        assertThat(mismatches(read(source, false), read(runtime, true), read(source, false)))
                .anySatisfy(difference -> assertThat(difference).contains("Documented operations " + (extra ? "unexpected=" : "missing="),
                        (extra ? "delete " : "post ") + ADMIN));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void AT22_missingOrExtraDocumentedStatusIsRejected(boolean extra) throws Exception {
        JsonNode source = sourceDocument();
        ObjectNode runtime = source.deepCopy();
        ObjectNode responses = (ObjectNode) runtime.path("paths").path(WINNERS).path("get").path("responses");
        if (extra) responses.putObject("500");
        else responses.remove("409");
        assertThat(mismatches(read(source, false), read(runtime, true), read(source, false)))
                .anySatisfy(difference -> assertThat(difference).contains("get " + WINNERS + " documented statuses",
                        extra ? "unexpected=[500]" : "missing=[409]"));
    }

    @Test
    void AT22_missingWinnerFromYamlAndBothRuntimeInventoriesStillFailsTheLockedInventory() throws Exception {
        ObjectNode source = sourceDocument().deepCopy();
        ((ObjectNode) source.path("paths")).remove(WINNERS);
        Inventory incomplete = read(source, false);
        assertThat(mismatches(incomplete, incomplete, incomplete))
                .anySatisfy(difference -> assertThat(difference).contains("Locked YAML operations missing=", "get " + WINNERS));
    }

    @Test
    void AT22_emptyDocumentationAndHandlersCannotPass() throws Exception {
        Inventory source = read(sourceDocument(), false);
        Inventory empty = read(mapper.createObjectNode(), true);
        assertThat(mismatches(source, empty, empty))
                .anySatisfy(difference -> assertThat(difference).contains("Documented operations missing="))
                .anySatisfy(difference -> assertThat(difference).contains("Registered operations missing="));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void AT22_missingOrExtraRegisteredHandlerIsRejected(boolean extra) throws Exception {
        Inventory source = read(sourceDocument(), false);
        Map<String, Set<String>> operations = new LinkedHashMap<>(source.operations());
        Set<String> paths = new LinkedHashSet<>(source.paths());
        if (extra) addMapping(paths, operations, Set.of(ADMIN + "/unregistered"), Set.of(RequestMethod.GET), Set.of("200"));
        else { operations.remove("get " + WINNERS); paths.remove(WINNERS); }
        assertThat(mismatches(source, source, new Inventory(paths, operations)))
                .anySatisfy(difference -> assertThat(difference).contains("Registered operations " + (extra ? "unexpected=" : "missing="),
                        extra ? "get " + ADMIN + "/unregistered" : "get " + WINNERS));
    }

    @Test
    void AT22_handlerWithoutAnExplicitHttpMethodIsRejected() throws Exception {
        Inventory source = read(sourceDocument(), false);
        Map<String, Set<String>> operations = new LinkedHashMap<>(source.operations());
        operations.remove("get " + WINNERS);
        Set<String> paths = new LinkedHashSet<>(source.paths());
        addMapping(paths, operations, Set.of(WINNERS), Set.of(), source.operations().get("get " + WINNERS));
        assertThat(mismatches(source, source, new Inventory(paths, operations)))
                .anySatisfy(difference -> assertThat(difference).contains("Registered operations unexpected=", "* " + WINNERS));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void AT22_missingOrExtraHandlerResponseStatusIsRejected(boolean extra) throws Exception {
        Inventory source = read(sourceDocument(), false);
        Map<String, Set<String>> operations = new LinkedHashMap<>(source.operations());
        Set<String> statuses = new LinkedHashSet<>(operations.get("get " + WINNERS));
        if (extra) statuses.add("500"); else statuses.remove("409");
        operations.put("get " + WINNERS, statuses);
        assertThat(mismatches(source, source, new Inventory(source.paths(), operations)))
                .anySatisfy(difference -> assertThat(difference).contains("get " + WINNERS + " registered statuses",
                        extra ? "unexpected=[500]" : "missing=[409]"));
    }

    @Test
    void AT22_scopeUsesExactNamespaceAndPackageBoundaries() throws Exception {
        ObjectNode runtime = sourceDocument().deepCopy();
        for (String root : List.of(PUBLIC, LEARNER, ADMIN)) {
            assertThat(inNamespace(root, root)).isTrue();
            assertThat(inNamespace(root + "/child", root)).isTrue();
            assertThat(inNamespace(root + "-other", root)).isFalse();
            ((ObjectNode) runtime.path("paths")).putObject(root + "-other").putObject("get")
                    .putObject("responses").putObject("500");
        }
        assertThat(read(runtime, true)).isEqualTo(read(sourceDocument(), false));
        assertThat(inPackage(PACKAGE)).isTrue();
        assertThat(inPackage(PACKAGE + ".web")).isTrue();
        assertThat(inPackage("com.cnhsk.community.contests")).isFalse();
        assertThat(inPackage("com.cnhsk.community")).isFalse();
    }

    private JsonNode sourceDocument() throws Exception {
        return new ObjectMapper(new YAMLFactory()).readTree(Files.readString(CONTRACT, StandardCharsets.UTF_8));
    }

    private Inventory registered() {
        Set<String> paths = new LinkedHashSet<>();
        Map<String, Set<String>> operations = new LinkedHashMap<>();
        handlers.getHandlerMethods().forEach((mapping, handler) -> {
            if (!inPackage(ClassUtils.getUserClass(handler.getBeanType()).getPackageName())) return;
            Set<String> responses = Arrays.stream(handler.getMethod().getAnnotationsByType(ApiResponse.class))
                    .map(ApiResponse::responseCode).collect(Collectors.toCollection(LinkedHashSet::new));
            addMapping(paths, operations, mapping.getPatternValues(), mapping.getMethodsCondition().getMethods(), responses);
        });
        return new Inventory(paths, operations);
    }

    private static void addMapping(Set<String> paths, Map<String, Set<String>> operations,
            Set<String> mappingPaths, Set<RequestMethod> methods, Set<String> responses) {
        paths.addAll(mappingPaths);
        mappingPaths.forEach(path -> {
            if (methods.isEmpty()) operations.put("* " + path, Set.copyOf(responses));
            else methods.forEach(method -> operations.put(method.name().toLowerCase(Locale.ROOT) + " " + path, Set.copyOf(responses)));
        });
    }

    private static Inventory read(JsonNode document, boolean scoped) {
        Set<String> paths = new LinkedHashSet<>();
        Map<String, Set<String>> operations = new LinkedHashMap<>();
        document.path("paths").fields().forEachRemaining(path -> {
            if (scoped && List.of(PUBLIC, LEARNER, ADMIN).stream().noneMatch(root -> inNamespace(path.getKey(), root))) return;
            paths.add(path.getKey());
            path.getValue().fields().forEachRemaining(method -> {
                if (METHODS.contains(method.getKey())) operations.put(method.getKey() + " " + path.getKey(), fields(method.getValue().path("responses")));
            });
        });
        return new Inventory(paths, operations);
    }

    private static List<String> mismatches(Inventory source, Inventory documented, Inventory registered) {
        List<String> differences = new ArrayList<>();
        Set<String> lockedPaths = OPERATIONS.stream().map(operation -> operation.substring(operation.indexOf(' ') + 1)).collect(Collectors.toSet());
        compare(differences, "Locked YAML paths", lockedPaths, source.paths());
        compare(differences, "Locked YAML operations", OPERATIONS, source.operations().keySet());
        compare(differences, "Documented paths", source.paths(), documented.paths());
        compare(differences, "Documented operations", source.operations().keySet(), documented.operations().keySet());
        compare(differences, "Registered paths", source.paths(), registered.paths());
        compare(differences, "Registered operations", source.operations().keySet(), registered.operations().keySet());
        source.operations().forEach((operation, statuses) -> {
            if (statuses.isEmpty()) differences.add(operation + " YAML responses must not be empty");
            compare(differences, operation + " documented statuses", statuses, documented.operations().getOrDefault(operation, Set.of()));
            compare(differences, operation + " registered statuses", statuses, registered.operations().getOrDefault(operation, Set.of()));
        });
        return differences;
    }

    private static void compare(List<String> differences, String location, Set<String> expected, Set<String> actual) {
        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(actual);
        Set<String> unexpected = new TreeSet<>(actual);
        unexpected.removeAll(expected);
        if (!missing.isEmpty()) differences.add(location + " missing=" + missing);
        if (!unexpected.isEmpty()) differences.add(location + " unexpected=" + unexpected);
    }

    private static boolean inNamespace(String path, String root) {
        return path.equals(root) || path.startsWith(root + "/");
    }

    private static boolean inPackage(String packageName) {
        return packageName.equals(PACKAGE) || packageName.startsWith(PACKAGE + ".");
    }

    private static Set<String> fields(JsonNode object) {
        Set<String> values = new LinkedHashSet<>();
        object.fieldNames().forEachRemaining(values::add);
        return values;
    }

    private record Inventory(Set<String> paths, Map<String, Set<String>> operations) { }
}
