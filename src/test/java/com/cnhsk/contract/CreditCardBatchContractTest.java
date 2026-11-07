package com.cnhsk.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cnhsk.auth.service.BootstrapAdminRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.packages-to-scan=com.cnhsk.learning.credit",
        "spring.datasource.url=jdbc:h2:mem:credit-card-contract;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
class CreditCardBatchContractTest {
    private static final String ROOT = "/api/admin/card-batches";
    private static final Path CONTRACT = Path.of("specs", "013-credit-cards", "contracts", "openapi-credit.yaml");
    private static final List<String> SCHEMAS = List.of("CreateBatchRequest", "CreatedBatch", "BatchSummary",
            "BatchPage", "VoidResult", "CardType", "Channel");

    @MockitoBean private BootstrapAdminRunner bootstrapAdminRunner;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlers;

    @Test
    void fourRegisteredOperationsMatchLockedPathsMethodsIdsAndStatuses() throws Exception {
        JsonNode source = source();
        JsonNode generated = generated();
        Map<String, JsonNode> expected = operations(source);
        Map<String, JsonNode> actual = operations(generated);
        assertThat(expected).hasSize(4);
        assertThat(actual.keySet()).containsExactlyInAnyOrderElementsOf(expected.keySet());
        Set<String> registered = new HashSet<>();
        handlers.getHandlerMethods().forEach((mapping, handler) -> {
            mapping.getPatternValues().stream().filter(path -> path.startsWith(ROOT)).forEach(path -> {
                assertThat(mapping.getMethodsCondition().getMethods()).isNotEmpty();
                mapping.getMethodsCondition().getMethods().forEach(method ->
                        assertThat(registered.add(method.name().toLowerCase() + " " + path)).isTrue());
            });
        });
        assertThat(registered).containsExactlyInAnyOrderElementsOf(expected.keySet());
        expected.forEach((key, operation) -> {
            JsonNode runtime = actual.get(key);
            assertThat(runtime.path("operationId")).as(key).isEqualTo(operation.path("operationId"));
            assertThat(fields(runtime.path("responses"))).as("Statuses %s", key)
                    .isEqualTo(fields(operation.path("responses")));
            assertSecurityMatches(source.path("security"), runtime.path("security"), key);
            fields(operation.path("responses")).forEach(code -> {
                JsonNode response = resolve(source, operation.path("responses").path(code));
                assertSchema(source, response.path("content").path("application/json").path("schema"),
                        generated, runtime.path("responses").path(code).path("content").path("application/json").path("schema"),
                        key + ":" + code);
            });
            if (operation.has("requestBody")) {
                assertThat(runtime.path("requestBody").path("required")).isEqualTo(operation.path("requestBody").path("required"));
                assertSchema(source, operation.path("requestBody").path("content").path("application/json").path("schema"),
                        generated, runtime.path("requestBody").path("content").path("application/json").path("schema"), key + " request");
            }
        });
    }

    @Test
    void securityComparisonRejectsMissingExtraSchemesAndWrongScopes() throws Exception {
        JsonNode expected = source().path("security");
        assertSecurityMatches(expected, mapper.readTree("[{\"bearerAuth\":[]},{\"cookieAuth\":[]}]"), "reordered OR");
        for (String invalid : List.of(
                "[{\"cookieAuth\":[]}]",
                "[{\"cookieAuth\":[]},{\"bearerAuth\":[]},{\"otherAuth\":[]}]",
                "[{\"cookieAuth\":[]},{\"bearerAuth\":[\"unexpected-scope\"]}]",
                "[{\"cookieAuth\":[],\"bearerAuth\":[]}]")) {
            JsonNode actual = mapper.readTree(invalid);
            assertThatThrownBy(() -> assertSecurityMatches(expected, actual, "invalid security"))
                    .isInstanceOf(AssertionError.class).hasMessageContaining("Cookie/bearer OR");
        }
    }

    @Test
    void integerNullableRecordsAndResponseShapesMatchTheLockedSchemas() throws Exception {
        JsonNode source = source();
        JsonNode generated = generated();
        for (String name : SCHEMAS) {
            assertSchema(source, source.path("components").path("schemas").path(name), generated,
                    generated.path("components").path("schemas").path(name), name);
        }
    }

    @Test
    void completeContractAuditReportsEverySchemaMismatch() throws Exception {
        JsonNode source = source();
        JsonNode generated = generated();
        String output = System.getProperty("cnhsk.contract-audit-output");
        if (output != null) Files.writeString(Path.of(output), generated.toPrettyString());
        List<String> failures = new ArrayList<>();
        for (String name : SCHEMAS) {
            auditSchema(source.path("components").path("schemas").path(name),
                    generated.path("components").path("schemas").path(name), name, failures);
        }
        assertThat(failures).as("All generated schema mismatches").isEmpty();
    }

    @Test
    void paginationBatchPatternAndCsrfParametersMatchTheLockedContract() throws Exception {
        JsonNode source = source();
        JsonNode generated = generated();
        operations(source).forEach((key, operation) -> {
            Map<String, JsonNode> expected = parameters(source, operation);
            Map<String, JsonNode> actual = parameters(generated, operations(generated).get(key));
            assertThat(actual.keySet()).as("Parameters %s", key).isEqualTo(expected.keySet());
            expected.forEach((name, parameter) -> {
                JsonNode runtime = actual.get(name);
                assertThat(runtime.path("required").asBoolean()).as(name).isEqualTo(parameter.path("required").asBoolean());
                assertSchema(source, parameter.path("schema"), generated, runtime.path("schema"), key + " " + name);
            });
        });
    }

    private JsonNode source() throws Exception {
        return new ObjectMapper(new YAMLFactory()).readTree(Files.readString(CONTRACT));
    }

    private JsonNode generated() throws Exception {
        return mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
    }

    private static Map<String, JsonNode> operations(JsonNode document) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        document.path("paths").fields().forEachRemaining(path -> {
            if (path.getKey().startsWith(ROOT)) path.getValue().fields().forEachRemaining(method -> {
                if (Set.of("post", "get", "patch", "put", "delete").contains(method.getKey())) {
                    result.put(method.getKey() + " " + path.getKey(), method.getValue());
                }
            });
        });
        return result;
    }

    private static Map<String, JsonNode> parameters(JsonNode document, JsonNode operation) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        operation.path("parameters").forEach(value -> {
            JsonNode parameter = resolve(document, value);
            result.put(parameter.path("in").asText() + ":" + parameter.path("name").asText(), parameter);
        });
        return result;
    }

    private static void assertSchema(JsonNode expectedDoc, JsonNode expected, JsonNode actualDoc, JsonNode actual, String location) {
        if (expected.has("$ref")) {
            String ref = expected.path("$ref").asText();
            if (!ref.startsWith("#/")) {
                assertThat(actual.path("$ref").asText()).as(location).isEqualTo("#/components/schemas/ErrorResponse");
                return;
            }
            assertThat(actual.path("$ref").asText()).as("Reference %s", location).isEqualTo(ref);
            return;
        }
        assertThat(actual.isMissingNode()).as("Schema exists %s", location).isFalse();
        assertThat(types(actual)).as("Types %s", location).isEqualTo(types(expected));
        for (String constraint : List.of("minimum", "maximum", "minLength", "maxLength", "pattern", "format", "default", "enum")) {
            if (expected.has(constraint)) {
                assertThat(actual.path(constraint).asText()).as("%s %s", location, constraint)
                        .isEqualTo(expected.path(constraint).asText());
                if (expected.path(constraint).isArray()) assertThat(actual.path(constraint)).isEqualTo(expected.path(constraint));
            }
        }
        assertThat(strings(actual.path("required"))).as("Required %s", location).isEqualTo(strings(expected.path("required")));
        assertThat(fields(actual.path("properties"))).as("Properties %s", location).isEqualTo(fields(expected.path("properties")));
        expected.path("properties").fields().forEachRemaining(property -> assertSchema(expectedDoc, property.getValue(),
                actualDoc, actual.path("properties").path(property.getKey()), location + "." + property.getKey()));
        if (expected.has("items")) assertSchema(expectedDoc, expected.path("items"), actualDoc, actual.path("items"), location + "[]");
    }

    private static JsonNode resolve(JsonNode document, JsonNode node) {
        return node.has("$ref") && node.path("$ref").asText().startsWith("#/")
                ? document.at(node.path("$ref").asText().substring(1)) : node;
    }

    private static void auditSchema(JsonNode expected, JsonNode actual, String location, List<String> failures) {
        if (expected.has("$ref")) {
            String ref = expected.path("$ref").asText();
            auditCheck(failures, () -> assertThat(actual.path("$ref").asText()).as("Reference %s", location)
                    .isEqualTo(ref.startsWith("#/") ? ref : "#/components/schemas/ErrorResponse"));
            return;
        }
        auditCheck(failures, () -> assertThat(actual.isMissingNode()).as("Schema exists %s", location).isFalse());
        auditCheck(failures, () -> assertThat(types(actual)).as("Types %s", location).isEqualTo(types(expected)));
        for (String constraint : List.of("minimum", "maximum", "minLength", "maxLength", "pattern", "format", "default", "enum")) {
            if (expected.has(constraint)) {
                auditCheck(failures, () -> assertThat(actual.path(constraint).asText()).as("%s %s", location, constraint)
                        .isEqualTo(expected.path(constraint).asText()));
                if (expected.path(constraint).isArray()) {
                    auditCheck(failures, () -> assertThat(actual.path(constraint)).as("%s %s", location, constraint)
                            .isEqualTo(expected.path(constraint)));
                }
            }
        }
        auditCheck(failures, () -> assertThat(strings(actual.path("required"))).as("Required %s", location)
                .isEqualTo(strings(expected.path("required"))));
        auditCheck(failures, () -> assertThat(fields(actual.path("properties"))).as("Properties %s", location)
                .isEqualTo(fields(expected.path("properties"))));
        expected.path("properties").fields().forEachRemaining(property -> auditSchema(property.getValue(),
                actual.path("properties").path(property.getKey()), location + "." + property.getKey(), failures));
        if (expected.has("items")) auditSchema(expected.path("items"), actual.path("items"), location + "[]", failures);
    }

    private static void auditCheck(List<String> failures, Runnable check) {
        try {
            check.run();
        } catch (AssertionError failure) {
            failures.add(failure.getMessage());
        }
    }

    private static void assertSecurityMatches(JsonNode expected, JsonNode actual, String location) {
        assertThat(securityRequirements(actual)).as("Cookie/bearer OR %s", location)
                .isEqualTo(securityRequirements(expected));
    }

    private static Set<JsonNode> securityRequirements(JsonNode requirements) {
        assertThat(requirements.isArray()).as("Security requirements must be an array").isTrue();
        Set<JsonNode> result = new HashSet<>();
        requirements.forEach(requirement -> {
            assertThat(requirement.isObject()).as("Each security requirement must be an object").isTrue();
            result.add(requirement);
        });
        return result;
    }

    private static Set<String> types(JsonNode schema) {
        return schema.path("type").isArray() ? strings(schema.path("type"))
                : schema.has("type") ? Set.of(schema.path("type").asText()) : Set.of();
    }

    private static Set<String> fields(JsonNode node) {
        Set<String> result = new HashSet<>();
        Iterator<String> names = node.fieldNames();
        names.forEachRemaining(result::add);
        return result;
    }

    private static Set<String> strings(JsonNode node) {
        Set<String> result = new HashSet<>();
        node.forEach(value -> result.add(value.asText()));
        return result;
    }
}
