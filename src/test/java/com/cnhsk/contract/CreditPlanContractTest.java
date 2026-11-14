package com.cnhsk.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true", "springdoc.packages-to-scan=com.cnhsk.learning.credit"})
class CreditPlanContractTest extends AbstractIntegrationTest {
    private static final Path CONTRACT = Path.of("specs/013-credit-cards/contracts/openapi-credit.yaml");
    private static final String BASE = "/api/admin/plans";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace");
    private static final Set<String> ANNOTATIONS = Set.of("description", "title", "example", "examples", "externalDocs", "$comment");
    private static final Set<String> OPERATIONS = Set.of("post /api/credits/redeem", "get /api/me/credits",
            "get /api/me/credit-transactions", "post /api/admin/card-batches", "get /api/admin/card-batches",
            "get /api/admin/card-batches/{batchId}", "patch /api/admin/card-batches/{batchId}/void",
            "get /api/admin/plans", "put /api/admin/plans/{code}");
    private static final Set<String> VIEW_KEYS = Set.of("code", "exists", "name", "description", "durationDays", "price",
            "benefits", "isActive", "activeUsers");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlers;
    private long admin;
    private List<Map<String, Object>> catalogBefore;

    @BeforeEach void fixtures() {
        catalogBefore = catalog();
        admin = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,'Synthetic plan contract') RETURNING id",
                Long.class, "plan-contract-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH);
        jdbc.update("INSERT INTO auth.user_roles(user_id,role_code) VALUES (?,'ADMIN')", admin);
        jdbc.update("""
                INSERT INTO learning.plans(code,name,description,duration_days,price,benefits,is_active)
                VALUES ('PREMIUM','Synthetic contract premium',NULL,30,0,'{}',true)
                ON CONFLICT(code) DO UPDATE SET name=EXCLUDED.name,description=NULL,duration_days=30,
                    price=0,benefits='{}',is_active=true
                """);
    }

    @AfterEach void restoreOwnedFixtures() {
        assertAll("Restore owned plan contract fixtures",
                () -> jdbc.update("DELETE FROM shared.audit_logs WHERE actor_id=?", admin),
                () -> jdbc.update("DELETE FROM learning.credit_transactions WHERE user_id=?", admin),
                () -> jdbc.update("DELETE FROM auth.user_roles WHERE user_id=?", admin),
                () -> jdbc.update("DELETE FROM auth.users WHERE id=?", admin),
                this::restorePremiumCatalog,
                () -> assertThat(catalog()).isEqualTo(catalogBefore));
    }

    private void restorePremiumCatalog() {
        Map<String, Object> original = catalogBefore.stream().filter(row -> "PREMIUM".equals(row.get("code"))).findFirst().orElse(null);
        if (original == null) {
            jdbc.update("""
                    DELETE FROM learning.plans p WHERE p.code='PREMIUM'
                    AND NOT EXISTS (SELECT 1 FROM auth.users u WHERE u.plan_code=p.code)
                    """);
        } else {
            jdbc.update("""
                    INSERT INTO learning.plans(code,name,description,duration_days,price,benefits,is_active,created_at)
                    VALUES ('PREMIUM',?,?,?,?,CAST(? AS jsonb),?,?)
                    ON CONFLICT(code) DO UPDATE SET name=EXCLUDED.name,description=EXCLUDED.description,
                        duration_days=EXCLUDED.duration_days,price=EXCLUDED.price,benefits=EXCLUDED.benefits,
                        is_active=EXCLUDED.is_active,created_at=EXCLUDED.created_at
                    """, original.get("name"), original.get("description"), original.get("duration_days"), original.get("price"),
                    original.get("benefits").toString(), original.get("is_active"), original.get("created_at"));
        }
    }

    @Test void exactNineRegisteredCreditOperationsAndStrictPlanSchemasMatchTheLockedContract() throws Exception {
        JsonNode expected = new ObjectMapper(new YAMLFactory()).readTree(Files.readString(CONTRACT));
        JsonNode actual = mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        Map<String, JsonNode> expectedOperations = operations(expected, true);
        Map<String, JsonNode> actualOperations = operations(actual, false);
        Set<String> registered = new LinkedHashSet<>();
        handlers.getHandlerMethods().forEach((mapping, handler) -> {
            if (handler.getBeanType().getPackageName().startsWith("com.cnhsk.learning.credit")) {
                mapping.getPatternValues().forEach(path -> mapping.getMethodsCondition().getMethods().forEach(method ->
                        registered.add(method.name().toLowerCase(Locale.ROOT) + " " + path)));
            }
        });
        List<String> mismatches = new ArrayList<>();
        List<Map<String, Object>> aliases = new ArrayList<>();
        compare(OPERATIONS, expectedOperations.keySet(), "locked implemented credit operation set", mismatches);
        compare(OPERATIONS, actualOperations.keySet(), "generated credit operation set", mismatches);
        compare(OPERATIONS, registered, "registered credit operation set", mismatches);
        expectedOperations.forEach((name, operation) -> {
            JsonNode generated = actualOperations.get(name);
            if (generated == null) return;
            compare(operation.path("operationId"), generated.path("operationId"), name + " operationId", mismatches);
            compare(strings(operation.path("tags")), strings(generated.path("tags")), name + " tags", mismatches);
            compare(security(expected, operation), security(actual, generated), name + " OR cookie/bearer security", mismatches);
            compare(fields(operation.path("responses")), fields(generated.path("responses")), name + " status set", mismatches);
            Map<String, JsonNode> wantedParameters = parameters(expected, operation);
            Map<String, JsonNode> foundParameters = parameters(actual, generated);
            compare(wantedParameters.keySet(), foundParameters.keySet(), name + " parameter set", mismatches);
            wantedParameters.forEach((key, parameter) -> {
                JsonNode found = foundParameters.get(key);
                if (found == null) return;
                compare(parameter.path("required").asBoolean(), found.path("required").asBoolean(), name + " " + key + " required", mismatches);
                schema(parameter.path("schema"), found.path("schema"), actual, name + " " + key, mismatches, aliases);
            });
            compare(operation.has("requestBody"), generated.has("requestBody"), name + " requestBody presence", mismatches);
            if (operation.has("requestBody")) {
                JsonNode wantedBody = resolve(expected, operation.path("requestBody"));
                JsonNode foundBody = resolve(actual, generated.path("requestBody"));
                compare(wantedBody.path("required").asBoolean(), foundBody.path("required").asBoolean(), name + " requestBody required", mismatches);
                content(wantedBody, foundBody, actual, name + " requestBody", mismatches, aliases);
            }
            operation.path("responses").fields().forEachRemaining(response -> content(resolve(expected, response.getValue()),
                    resolve(actual, generated.path("responses").path(response.getKey())), actual,
                    name + " response " + response.getKey(), mismatches, aliases));
        });
        for (String name : List.of("PlanView", "PlanUpdateRequest", "Benefits", "PlanCode"))
            schema(expected.path("components").path("schemas").path(name), actual.path("components").path("schemas").path(name),
                    actual, name, mismatches, aliases);
        for (String name : List.of("cookieAuth", "bearerAuth"))
            compare(expected.path("components").path("securitySchemes").path(name), actual.path("components").path("securitySchemes").path(name),
                    name + " security scheme", mismatches);
        writeEvidence("generated-openapi", actual);
        writeEvidence("contract-audit", Map.of("expectedOperations", expectedOperations.keySet(), "actualOperations", actualOperations.keySet(),
                "registeredOperations", registered, "mismatches", mismatches, "resolvedInlinePrimitiveAliases", aliases,
                "sharedErrorResponsePolicy", "Exact inherited ErrorResponse references; shared external definition remains outside T10 DTO scope"));
        assertThat(mismatches).as("Locked credit operations and strict T10 schema mismatches; no new schema debt allowed").isEmpty();
    }

    @Test void actualGetAndPutJsonHaveExactlyNineKeysCorrectTypesAndTwoDecimalPriceStrings() throws Exception {
        JsonNode plans = getPlans();
        assertThat(plans).hasSize(3);
        for (int index = 0; index < 3; index++) {
            JsonNode plan = plans.get(index);
            assertView(plan);
            assertThat(plan.path("code").textValue()).isEqualTo(List.of("FREE", "PREMIUM", "PREMIUM_PLUS").get(index));
        }
        assertThat(plans.get(0).path("durationDays").isNull()).isTrue();
        assertThat(plans.get(0).path("price").textValue()).isEqualTo("0.00");
        assertThat(plans.get(1).path("description").isNull()).isTrue();
        List<JsonNode> updated = new ArrayList<>();
        for (String price : List.of("0", "12.5", "9999999999.99")) {
            JsonNode plan = update(price);
            assertView(plan);
            assertThat(plan.path("exists").booleanValue()).isTrue();
            assertThat(plan.path("price").textValue()).isEqualTo(new java.math.BigDecimal(price).setScale(2).toPlainString());
            assertThat(plan.path("benefits").isObject()).isTrue();
            assertThat(plan.path("benefits")).isEmpty();
            updated.add(plan);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.credit_transactions WHERE user_id=?", Long.class, admin)).isZero();
        writeEvidence("runtime-json", Map.of("get", plans, "put", updated, "viewKeys", VIEW_KEYS));
    }

    @Test void nonemptyBenefitsMatchLockedIntegerBoundsAcrossPutGetAndPersistence() throws Exception {
        JsonNode locked = new ObjectMapper(new YAMLFactory()).readTree(Files.readString(CONTRACT));
        JsonNode limitSchema = locked.path("components").path("schemas").path("Benefits").path("additionalProperties");
        assertThat(limitSchema.path("type").textValue()).isEqualTo("integer");
        assertThat(limitSchema.path("minimum").isIntegralNumber()).isTrue();
        assertThat(limitSchema.path("maximum").isIntegralNumber()).isTrue();
        BigInteger minimum = limitSchema.path("minimum").bigIntegerValue();
        BigInteger maximum = limitSchema.path("maximum").bigIntegerValue();

        JsonNode expectedBenefits = mapper.createObjectNode().put("AI_GENERATE", 0).put("TRANSLATE", 10_000)
                .put("ASSISTANT", 1).put("GRADING", 9999);
        ObjectNode request = mapper.createObjectNode().put("name", "Synthetic contract premium").put("durationDays", 30)
                .put("price", "12.50").put("isActive", true);
        request.set("benefits", expectedBenefits);
        JsonNode updated = mapper.readTree(mvc.perform(put(BASE + "/PREMIUM").header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(request)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertView(updated);
        JsonNode actualBenefits = updated.path("benefits");
        assertThat(actualBenefits).isEqualTo(expectedBenefits);
        assertThat(actualBenefits.has("empty")).isFalse();
        actualBenefits.fields().forEachRemaining(benefit -> {
            JsonNode limit = benefit.getValue();
            assertThat(limit.isIntegralNumber()).as(benefit.getKey()).isTrue();
            BigInteger value = limit.bigIntegerValue();
            assertThat(value).as(benefit.getKey()).isBetween(minimum, maximum);
        });

        JsonNode reloaded = getPlans().get(1);
        assertView(reloaded);
        assertThat(reloaded.path("code").textValue()).isEqualTo("PREMIUM");
        assertThat(reloaded.path("benefits")).isEqualTo(actualBenefits);
        JsonNode storedBenefits = mapper.readTree(jdbc.queryForObject(
                "SELECT benefits::text FROM learning.plans WHERE code='PREMIUM'", String.class));
        assertThat(storedBenefits).isEqualTo(actualBenefits);
    }

    @Test void absentPremiumRuntimePlaceholderHasExplicitNullScalarsEmptyBenefitsAndNoInventedDefaults() throws Exception {
        int removed = jdbc.update("DELETE FROM learning.plans p WHERE p.code='PREMIUM' AND NOT EXISTS (SELECT 1 FROM auth.users u WHERE u.plan_code=p.code)");
        assertThat(removed).as("owned missing-catalog fixture must never reassign unrelated FK dependants").isEqualTo(1);
        List<Map<String, Object>> before = catalog();
        JsonNode plans = getPlans();
        JsonNode missing = plans.get(1);
        assertView(missing);
        assertThat(missing.path("code").textValue()).isEqualTo("PREMIUM");
        assertThat(missing.path("exists").booleanValue()).isFalse();
        for (String key : List.of("name", "description", "durationDays", "price", "isActive"))
            assertThat(missing.has(key) && missing.path(key).isNull()).as(key + " is explicitly null").isTrue();
        assertThat(missing.path("benefits")).isEmpty();
        assertThat(missing.path("activeUsers").longValue()).isZero();
        assertThat(catalog()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs WHERE actor_id=?", Long.class, admin)).isZero();
        writeEvidence("missing-runtime-json", Map.of("plans", plans, "missingPlanKeys", fields(missing), "missingPlanTypes", observedTypes(missing)));
    }

    private static void content(JsonNode expected, JsonNode actual, JsonNode document, String location,
            List<String> mismatches, List<Map<String, Object>> aliases) {
        compare(fields(expected.path("content")), fields(actual.path("content")), location + " media types", mismatches);
        expected.path("content").fields().forEachRemaining(media -> schema(media.getValue().path("schema"),
                actual.path("content").path(media.getKey()).path("schema"), document, location + " " + media.getKey(), mismatches, aliases));
    }

    private static void schema(JsonNode expected, JsonNode actual, JsonNode document, String location,
            List<String> mismatches, List<Map<String, Object>> aliases) {
        if (actual.isMissingNode()) { mismatches.add(location + " missing schema"); return; }
        if (!expected.has("$ref") && actual.has("$ref") && !types(expected).isEmpty()
                && !types(expected).contains("object") && !types(expected).contains("array")) {
            String reference = actual.path("$ref").asText();
            Set<String> keywords = semanticKeys(actual);
            JsonNode effective = reference.startsWith("#/") ? document.at(reference.substring(1)) : null;
            if (!keywords.equals(Set.of("$ref")) || effective == null || effective.isMissingNode() || effective.has("$ref")) {
                mismatches.add(location + " primitive alias is unresolved, chained, or has semantic siblings: " + actual);
                return;
            }
            aliases.add(Map.of("location", location, "reference", reference, "effectiveSchema", effective));
            schema(expected, effective, document, location + " resolved " + reference, mismatches, aliases);
            return;
        }
        Set<String> wantedKeys = semanticKeys(expected), foundKeys = semanticKeys(actual);
        compare(wantedKeys, foundKeys, location + " semantic keyword set", mismatches);
        Set<String> keywords = new LinkedHashSet<>(wantedKeys); keywords.addAll(foundKeys);
        for (String key : keywords) {
            JsonNode wanted = expected.path(key), found = actual.path(key);
            switch (key) {
                case "properties" -> {
                    compare(fields(wanted), fields(found), location + " properties", mismatches);
                    wanted.fields().forEachRemaining(property -> schema(property.getValue(), found.path(property.getKey()), document,
                            location + "." + property.getKey(), mismatches, aliases));
                }
                case "items" -> schema(wanted, found, document, location + "[]", mismatches, aliases);
                case "additionalProperties" -> {
                    if (wanted.isObject()) schema(wanted, found, document, location + " additionalProperties", mismatches, aliases);
                    else compare(wanted, found, location + " " + key, mismatches);
                }
                case "type" -> compare(types(expected), types(actual), location + " type", mismatches);
                case "required", "enum" -> compare(strings(wanted), strings(found), location + " " + key, mismatches);
                default -> {
                    if (wanted.isNumber() && found.isNumber())
                        compare(wanted.decimalValue().stripTrailingZeros(), found.decimalValue().stripTrailingZeros(), location + " " + key, mismatches);
                    else compare(wanted, found, location + " " + key, mismatches);
                }
            }
        }
    }

    private static Map<String, JsonNode> operations(JsonNode document, boolean locked) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        document.path("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(method -> {
            if (METHODS.contains(method.getKey()) && (!locked || !path.getKey().startsWith("/api/admin/credit-transactions")))
                result.put(method.getKey() + " " + path.getKey(), method.getValue());
        }));
        return result;
    }
    private static Map<String, JsonNode> parameters(JsonNode document, JsonNode operation) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        operation.path("parameters").forEach(value -> {
            JsonNode parameter = resolve(document, value);
            String key = parameter.path("in").asText() + " " + parameter.path("name").asText();
            assertThat(result.put(key, parameter)).as("duplicate parameter " + key).isNull();
        });
        return result;
    }
    private static JsonNode resolve(JsonNode document, JsonNode value) {
        return value.path("$ref").asText().startsWith("#/") ? document.at(value.path("$ref").asText().substring(1)) : value;
    }
    private static Set<JsonNode> security(JsonNode document, JsonNode operation) {
        Set<JsonNode> result = new LinkedHashSet<>();
        (operation.has("security") ? operation.path("security") : document.path("security")).forEach(result::add);
        return result;
    }
    private static Set<String> semanticKeys(JsonNode node) { Set<String> keys = fields(node); keys.removeAll(ANNOTATIONS); return keys; }
    private static Set<String> fields(JsonNode node) { Set<String> result = new LinkedHashSet<>(); node.fieldNames().forEachRemaining(result::add); return result; }
    private static Set<String> strings(JsonNode node) { Set<String> result = new LinkedHashSet<>(); node.forEach(value -> result.add(value.asText())); return result; }
    private static Set<String> types(JsonNode node) { return node.path("type").isArray() ? strings(node.path("type")) : node.has("type") ? Set.of(node.path("type").asText()) : Set.of(); }
    private static void compare(Object expected, Object actual, String location, List<String> mismatches) {
        if (!Objects.equals(expected, actual)) mismatches.add(location + " expected=" + expected + " actual=" + actual);
    }
    private static Map<String, String> observedTypes(JsonNode node) {
        Map<String, String> result = new LinkedHashMap<>();
        node.fields().forEachRemaining(field -> result.put(field.getKey(), field.getValue().getNodeType().name()));
        return result;
    }
    private static void assertView(JsonNode view) {
        assertThat(fields(view)).isEqualTo(VIEW_KEYS);
        assertThat(view.path("code").isTextual()).isTrue();
        assertThat(view.path("exists").isBoolean()).isTrue();
        assertThat(view.path("activeUsers").isIntegralNumber()).isTrue();
        assertThat(view.path("activeUsers").canConvertToLong()).isTrue();
        assertThat(view.path("activeUsers").longValue()).isNotNegative();
        assertThat(view.path("benefits").isObject()).isTrue();
        view.path("benefits").fields().forEachRemaining(benefit -> {
            assertThat(Set.of("AI_GENERATE", "TRANSLATE", "ASSISTANT", "GRADING")).contains(benefit.getKey());
            assertThat(benefit.getValue().isIntegralNumber()).isTrue();
            assertThat(benefit.getValue().canConvertToInt()).isTrue();
            assertThat(benefit.getValue().intValue()).isBetween(0, 10_000);
        });
        for (String field : List.of("name", "description")) assertThat(view.path(field).isNull() || view.path(field).isTextual()).as(field).isTrue();
        assertThat(view.path("durationDays").isNull() || view.path("durationDays").isIntegralNumber()).isTrue();
        assertThat(view.path("isActive").isNull() || view.path("isActive").isBoolean()).isTrue();
        if (!view.path("price").isNull()) {
            assertThat(view.path("price").isTextual()).isTrue();
            assertThat(view.path("price").textValue()).matches("[0-9]{1,10}\\.[0-9]{2}");
        }
    }
    private List<Map<String, Object>> catalog() { return jdbc.queryForList("SELECT * FROM learning.plans ORDER BY code"); }
    private String bearer() { return "Bearer " + jwt.issueAccessToken(admin, List.of("ADMIN"), false, UUID.randomUUID()); }
    private JsonNode getPlans() throws Exception {
        return mapper.readTree(mvc.perform(get(BASE).header("Authorization", bearer())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
    }
    private JsonNode update(String price) throws Exception {
        ObjectNode body = mapper.createObjectNode().put("name", "Synthetic contract premium").put("durationDays", 30)
                .put("price", price).put("isActive", false);
        body.set("benefits", mapper.createObjectNode());
        return mapper.readTree(mvc.perform(put(BASE + "/PREMIUM").header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }
    private void writeEvidence(String name, Object value) throws Exception {
        Path directory = Path.of(System.getProperty("java.io.tmpdir"), "credit-plan-contract-evidence");
        Files.createDirectories(directory);
        mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve(name + "-" + UUID.randomUUID() + ".json").toFile(), value);
    }
}
