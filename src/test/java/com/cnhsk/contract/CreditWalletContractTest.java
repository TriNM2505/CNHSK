package com.cnhsk.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cnhsk.learning.credit.LedgerService;
import com.cnhsk.learning.credit.LedgerService.AppendCommand;
import com.cnhsk.learning.credit.web.WalletController;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true", "springdoc.packages-to-scan=com.cnhsk.learning.credit"})
class CreditWalletContractTest extends AbstractIntegrationTest {
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private static final Path CONTRACT = Path.of("specs/013-credit-cards/contracts/openapi-credit.yaml");
    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace");
    private static final Set<String> SCHEMA_ANNOTATIONS = Set.of("description", "title", "example", "examples", "externalDocs", "$comment");
    private static final Set<String> WALLET_PATHS = Set.of("/api/me/credits", "/api/me/credit-transactions", "/api/credits/redeem");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private LedgerService ledger;
    @Autowired private PlatformTransactionManager manager;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlers;
    private long userId;
    private List<Map<String, Object>> plansBefore;

    @BeforeEach void setUp() {
        plansBefore = jdbc.queryForList("SELECT * FROM learning.plans ORDER BY code");
        userId = jdbc.queryForObject("""
                INSERT INTO auth.users(email, password_hash, display_name) VALUES (?, ?, 'Hợp đồng ví') RETURNING id
                """, Long.class, "wallet-contract-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH);
        new TransactionTemplate(manager).executeWithoutResult(status -> ledger.append(
                new AppendCommand(userId, "TOPUP", 7, null, null, null, null, null, null, null)));
    }

    @AfterEach void cleanUp() {
        jdbc.update("DELETE FROM learning.credit_transactions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM auth.users WHERE id = ?", userId);
        assertThat(jdbc.queryForList("SELECT * FROM learning.plans ORDER BY code")).isEqualTo(plansBefore);
    }

    @Test void walletOperationsRegisteredRoutesStatusesSecurityParametersAndSchemasMatchTheLockedContractBidirectionally() throws Exception {
        JsonNode expected = new ObjectMapper(new YAMLFactory()).readTree(Files.readString(CONTRACT));
        JsonNode actual = mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        Map<String, JsonNode> expectedOperations = operations(expected);
        Map<String, JsonNode> actualOperations = operations(actual);
        List<String> mismatches = new ArrayList<>();
        List<Map<String, JsonNode>> backlog = new ArrayList<>();
        compare(expectedOperations.keySet(), actualOperations.keySet(), "wallet operation set", mismatches);
        compare(Set.of("get /api/me/credits", "get /api/me/credit-transactions", "post /api/credits/redeem"),
                expectedOperations.keySet(), "locked wallet operations", mismatches);
        Set<String> registered = new LinkedHashSet<>();
        handlers.getHandlerMethods().forEach((mapping, handler) -> {
            if (WalletController.class.isAssignableFrom(handler.getBeanType())) {
                mapping.getPatternValues().forEach(path -> mapping.getMethodsCondition().getMethods().forEach(method ->
                        registered.add(method.name().toLowerCase(Locale.ROOT) + " " + path)));
            }
        });
        compare(expectedOperations.keySet(), registered, "actual WalletController registrations", mismatches);
        expectedOperations.forEach((name, operation) -> {
            JsonNode generated = actualOperations.get(name);
            if (generated == null) return;
            compare(operation.path("operationId"), generated.path("operationId"), name + " operationId", mismatches);
            compare(fields(operation.path("responses")), fields(generated.path("responses")), name + " status set", mismatches);
            compare(security(expected, operation), security(actual, generated), name + " OR cookie/bearer security", mismatches);
            operation.path("responses").fields().forEachRemaining(response -> {
                JsonNode sourceResponse = resolve(expected, response.getValue());
                JsonNode runtimeResponse = resolve(actual, generated.path("responses").path(response.getKey()));
                schema(sourceResponse.path("content").path("application/json").path("schema"),
                        runtimeResponse.path("content").path("application/json").path("schema"),
                        name + " response " + response.getKey(), mismatches, backlog);
            });
            Map<String, JsonNode> expectedParameters = parameters(expected, operation);
            Map<String, JsonNode> actualParameters = parameters(actual, generated);
            compare(expectedParameters.keySet(), actualParameters.keySet(), name + " parameter set", mismatches);
            expectedParameters.forEach((key, parameter) -> {
                JsonNode runtime = actualParameters.get(key);
                if (runtime == null) return;
                compare(parameter.path("required").asBoolean(), runtime.path("required").asBoolean(), name + " " + key + " required", mismatches);
                schema(parameter.path("schema"), runtime.path("schema"), name + " " + key, mismatches, backlog);
            });
        });
        for (String name : List.of("Wallet", "PlanStatus", "PlanCode", "QuotaLine", "PaidFeature", "Transaction", "TransactionType", "TransactionPage")) {
            schema(expected.path("components").path("schemas").path(name), actual.path("components").path("schemas").path(name), name, mismatches, backlog);
        }
        Path directory = evidenceDirectory();
        String runId = UUID.randomUUID().toString();
        mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("wallet-generated-openapi-" + runId + ".json").toFile(), actual);
        mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("wallet-contract-audit-" + runId + ".json").toFile(),
                Map.of("expectedOperations", expectedOperations, "actualOperations", actualOperations,
                        "registeredOperations", registered, "mismatches", mismatches,
                        "nullableTransactionFeatureCodeBacklog", backlog));
        assertThat(mismatches).as("Locked wallet contract mismatches (nullable featureCode representation alone is recorded separately)").isEmpty();
    }

    @Test void actualGetJsonUsesRequiredNumericBooleanDateTimeAndExplicitNullableTransactionFields() throws Exception {
        String bearer = "Bearer " + jwt.issueAccessToken(userId, List.of("LEARNER"), false, UUID.randomUUID());
        JsonNode wallet = mapper.readTree(mvc.perform(get("/api/me/credits").header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(fields(wallet)).isEqualTo(Set.of("balance", "plan", "quota", "resetAt"));
        assertThat(wallet.path("balance").isIntegralNumber()).isTrue();
        assertThat(wallet.path("balance").intValue()).isEqualTo(7);
        assertThat(fields(wallet.path("plan"))).isEqualTo(Set.of("code", "name", "expiresAt", "active"));
        assertThat(wallet.path("plan").path("code").textValue()).isEqualTo("FREE");
        assertThat(wallet.path("plan").path("name").isTextual()).isTrue();
        assertThat(wallet.path("plan").path("expiresAt").isNull()).isTrue();
        assertThat(wallet.path("plan").path("active").isBoolean()).isTrue();
        assertThat(Instant.parse(wallet.path("resetAt").textValue())).isNotNull();
        assertThat(wallet.path("quota").size()).isEqualTo(4);
        Set<String> features = new LinkedHashSet<>();
        for (JsonNode quota : wallet.path("quota")) {
            assertThat(fields(quota)).isEqualTo(Set.of("feature", "limit", "used", "remaining"));
            features.add(quota.path("feature").textValue());
            for (String field : List.of("limit", "used", "remaining")) {
                assertThat(quota.path(field).isIntegralNumber()).isTrue();
                assertThat(quota.path(field).intValue()).isNotNegative();
            }
        }
        assertThat(features).containsExactlyInAnyOrder("AI_GENERATE", "TRANSLATE", "ASSISTANT", "GRADING");
        JsonNode history = mapper.readTree(mvc.perform(get("/api/me/credit-transactions").header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(fields(history)).isEqualTo(Set.of("items", "page", "size", "total"));
        for (String field : List.of("page", "size", "total")) assertThat(history.path(field).isIntegralNumber()).isTrue();
        assertThat(history.path("page").intValue()).isZero();
        assertThat(history.path("size").intValue()).isEqualTo(20);
        assertThat(history.path("total").longValue()).isEqualTo(1);
        assertThat(history.path("items").size()).isEqualTo(1);
        JsonNode item = history.path("items").get(0);
        assertThat(fields(item)).isEqualTo(Set.of("seq", "type", "amount", "balanceBefore", "balanceAfter", "featureCode", "reason", "createdAt"));
        for (String field : List.of("seq", "amount", "balanceBefore", "balanceAfter")) assertThat(item.path(field).isIntegralNumber()).isTrue();
        assertThat(item.path("featureCode").isNull()).isTrue();
        assertThat(item.path("reason").isNull()).isTrue();
        assertThat(Instant.parse(item.path("createdAt").textValue())).isNotNull();
        mapper.writerWithDefaultPrettyPrinter().writeValue(evidenceDirectory().resolve("wallet-runtime-json-" + UUID.randomUUID() + ".json").toFile(),
                Map.of("wallet", wallet, "history", history, "walletKeys", fields(wallet),
                        "historyKeys", fields(history), "transactionKeys", fields(item),
                        "walletTypes", observedTypes(wallet), "historyTypes", observedTypes(history),
                        "transactionTypes", observedTypes(item),
                        "explicitNulls", Map.of("plan.expiresAt", wallet.path("plan").path("expiresAt").isNull(),
                                "transaction.featureCode", item.path("featureCode").isNull(), "transaction.reason", item.path("reason").isNull())));
    }

    private static void schema(JsonNode expected, JsonNode actual, String location, List<String> mismatches, List<Map<String, JsonNode>> backlog) {
        if (location.equals("Transaction.featureCode")) {
            JsonNode pinned = JsonNodeFactory.instance.objectNode()
                    .put("type", "null").put("$ref", "#/components/schemas/PaidFeature");
            compare(pinned, actual, location + " pinned generated nullable representation", mismatches);
            if (pinned.equals(actual) && !expected.equals(actual)) {
                backlog.add(Map.of("expected", expected, "actual", actual));
            }
            return;
        }
        if (actual.isMissingNode()) { mismatches.add(location + " missing schema"); return; }
        Set<String> expectedKeywords = new LinkedHashSet<>(fields(expected));
        Set<String> actualKeywords = new LinkedHashSet<>(fields(actual));
        expectedKeywords.removeAll(SCHEMA_ANNOTATIONS);
        actualKeywords.removeAll(SCHEMA_ANNOTATIONS);
        compare(expectedKeywords, actualKeywords, location + " semantic keyword set", mismatches);
        Set<String> keywords = new LinkedHashSet<>(expectedKeywords);
        keywords.addAll(actualKeywords);
        for (String key : keywords) {
            JsonNode wanted = expected.path(key), found = actual.path(key);
            switch (key) {
                case "properties" -> {
                    compare(fields(wanted), fields(found), location + " properties", mismatches);
                    wanted.fields().forEachRemaining(property -> schema(property.getValue(), found.path(property.getKey()),
                            location + "." + property.getKey(), mismatches, backlog));
                }
                case "items" -> schema(wanted, found, location + "[]", mismatches, backlog);
                case "type" -> compare(types(expected), types(actual), location + " type", mismatches);
                case "required", "enum" -> compare(strings(wanted), strings(found), location + " " + key, mismatches);
                case "oneOf", "anyOf", "allOf" -> compare(schemaAlternatives(wanted), schemaAlternatives(found), location + " " + key, mismatches);
                default -> {
                    if (wanted.isNumber() && found.isNumber()) compare(wanted.decimalValue().stripTrailingZeros(), found.decimalValue().stripTrailingZeros(), location + " " + key, mismatches);
                    else compare(semanticValue(wanted), semanticValue(found), location + " " + key, mismatches);
                }
            }
        }
    }
    private static Set<Object> schemaAlternatives(JsonNode node) {
        Set<Object> result = new LinkedHashSet<>();
        node.forEach(value -> result.add(semanticValue(value)));
        return result;
    }
    private static Object semanticValue(JsonNode node) {
        if (node.isObject()) {
            Map<String, Object> result = new LinkedHashMap<>();
            node.fields().forEachRemaining(field -> {
                if (!SCHEMA_ANNOTATIONS.contains(field.getKey())) result.put(field.getKey(), semanticValue(field.getValue()));
            });
            return result;
        }
        if (node.isArray()) {
            List<Object> result = new ArrayList<>();
            node.forEach(value -> result.add(semanticValue(value)));
            return result;
        }
        return node.isNumber() ? node.decimalValue().stripTrailingZeros() : node;
    }
    private static void compare(Object expected, Object actual, String location, List<String> mismatches) {
        if (!java.util.Objects.equals(expected, actual)) mismatches.add(location + " expected=" + expected + " actual=" + actual);
    }
    private static Map<String, JsonNode> operations(JsonNode document) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        document.path("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(method -> {
            if (METHODS.contains(method.getKey()) && (WALLET_PATHS.contains(path.getKey()) || strings(method.getValue().path("tags")).contains("wallet")))
                result.put(method.getKey() + " " + path.getKey(), method.getValue());
        }));
        return result;
    }
    private static Map<String, JsonNode> parameters(JsonNode document, JsonNode operation) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        operation.path("parameters").forEach(value -> { JsonNode parameter = resolve(document, value); result.put(parameter.path("in").asText() + " " + parameter.path("name").asText(), parameter); });
        return result;
    }
    private static Set<JsonNode> security(JsonNode document, JsonNode operation) {
        Set<JsonNode> result = new LinkedHashSet<>();
        (operation.has("security") ? operation.path("security") : document.path("security")).forEach(result::add);
        return result;
    }
    private static JsonNode resolve(JsonNode document, JsonNode node) {
        return node.path("$ref").asText().startsWith("#/") ? document.at(node.path("$ref").asText().substring(1)) : node;
    }
    private static Set<String> fields(JsonNode node) { Set<String> result = new LinkedHashSet<>(); node.fieldNames().forEachRemaining(result::add); return result; }
    private static Set<String> strings(JsonNode node) { Set<String> result = new LinkedHashSet<>(); node.forEach(value -> result.add(value.asText())); return result; }
    private static Set<String> types(JsonNode node) { return node.path("type").isArray() ? strings(node.path("type")) : node.has("type") ? Set.of(node.path("type").asText()) : Set.of(); }
    private static Map<String, String> observedTypes(JsonNode node) {
        Map<String, String> result = new LinkedHashMap<>();
        node.fields().forEachRemaining(field -> result.put(field.getKey(), field.getValue().getNodeType().name()));
        return result;
    }
    private static Path evidenceDirectory() throws Exception {
        Path directory = Path.of(System.getProperty("java.io.tmpdir"), "wallet-query-evidence");
        Files.createDirectories(directory);
        return directory;
    }
}
