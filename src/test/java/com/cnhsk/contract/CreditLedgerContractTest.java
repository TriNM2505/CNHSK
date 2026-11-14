package com.cnhsk.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.cnhsk.learning.credit.LedgerAdminIntegrationSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true", "springdoc.packages-to-scan=com.cnhsk.learning.credit"})
class CreditLedgerContractTest extends LedgerAdminIntegrationSupport {
    private static final Path CONTRACT = Path.of("specs/013-credit-cards/contracts/openapi-credit.yaml");
    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace");
    private static final Set<String> ANNOTATIONS = Set.of("description", "title", "example", "examples", "externalDocs", "$comment");
    private static final Set<String> LEDGER_REFERENCES = Set.of("#/components/schemas/LedgerRow", "#/components/schemas/LedgerPage",
            "#/components/schemas/LedgerTotals", "#/components/schemas/Reconciliation", "#/components/schemas/ReconciliationIssue",
            "#/components/schemas/TransactionType");
    private static final Set<String> OPERATIONS = Set.of("post /api/credits/redeem", "get /api/me/credits",
            "get /api/me/credit-transactions", "post /api/admin/card-batches", "get /api/admin/card-batches",
            "get /api/admin/card-batches/{batchId}", "patch /api/admin/card-batches/{batchId}/void",
            "get /api/admin/plans", "put /api/admin/plans/{code}", "get /api/admin/credit-transactions",
            "get /api/admin/credit-transactions/export", "get /api/admin/credit-transactions/reconciliation");
    private static final List<String> CSV_HEADER = List.of("id", "userId", "maskedEmail", "seq", "type", "amount",
            "balanceBefore", "balanceAfter", "featureCode", "reason", "createdAt");
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlers;

    @Test void allTwelveRegisteredCreditOperationsAndStrictLedgerSchemasMatchTheLockedContract() throws Exception {
        JsonNode expected = locked();
        JsonNode actual = mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        Map<String, JsonNode> wantedOperations = operations(expected), foundOperations = operations(actual);
        Set<String> registered = new LinkedHashSet<>();
        handlers.getHandlerMethods().forEach((mapping, handler) -> {
            if (handler.getBeanType().getPackageName().startsWith("com.cnhsk.learning.credit"))
                mapping.getPatternValues().forEach(path -> mapping.getMethodsCondition().getMethods().forEach(method ->
                        registered.add(method.name().toLowerCase(Locale.ROOT) + " " + path)));
        });
        List<String> mismatches = new ArrayList<>();
        compare(OPERATIONS, wantedOperations.keySet(), "locked operation inventory", mismatches);
        compare(OPERATIONS, foundOperations.keySet(), "generated operation inventory", mismatches);
        compare(OPERATIONS, registered, "registered operation inventory", mismatches);
        wantedOperations.forEach((name, operation) -> {
            JsonNode generated = foundOperations.get(name);
            if (generated == null) return;
            compare(operation.path("operationId"), generated.path("operationId"), name + " operationId", mismatches);
            compare(strings(operation.path("tags")), strings(generated.path("tags")), name + " tags", mismatches);
            compare(security(expected, operation), security(actual, generated), name + " OR cookie/bearer security", mismatches);
            compare(fields(operation.path("responses")), fields(generated.path("responses")), name + " status set", mismatches);
            Map<String, JsonNode> wantedParameters = parameters(expected, operation), foundParameters = parameters(actual, generated);
            compare(wantedParameters.keySet(), foundParameters.keySet(), name + " parameters", mismatches);
            wantedParameters.forEach((key, parameter) -> {
                JsonNode found = foundParameters.get(key);
                if (found == null) return;
                compare(parameter.path("required").asBoolean(), found.path("required").asBoolean(), name + " " + key + " required", mismatches);
                schema(parameter.path("schema"), found.path("schema"), actual, name + " " + key, mismatches);
            });
            compare(operation.has("requestBody"), generated.has("requestBody"), name + " request body presence", mismatches);
            if (operation.has("requestBody")) {
                JsonNode wantedBody = resolve(expected, operation.path("requestBody"));
                JsonNode foundBody = resolve(actual, generated.path("requestBody"));
                compare(wantedBody.path("required").asBoolean(), foundBody.path("required").asBoolean(), name + " request body required", mismatches);
                content(wantedBody, foundBody, actual, name + " request body", mismatches);
            }
            operation.path("responses").fields().forEachRemaining(response -> content(resolve(expected, response.getValue()),
                    resolve(actual, generated.path("responses").path(response.getKey())), actual,
                    name + " response " + response.getKey(), mismatches));
        });
        for (String name : List.of("LedgerRow", "LedgerPage", "ReconciliationIssue", "Reconciliation", "TransactionType"))
            schema(expected.path("components").path("schemas").path(name), actual.path("components").path("schemas").path(name),
                    actual, name, mismatches);
        for (String name : List.of("cookieAuth", "bearerAuth"))
            compare(expected.path("components").path("securitySchemes").path(name), actual.path("components").path("securitySchemes").path(name),
                    name + " security scheme", mismatches);
        assertThat(mismatches).as("Locked credit operation and ledger schema mismatches; no unreviewed schema debt").isEmpty();
    }

    @Test void realLedgerJsonAndCsvHaveTheLockedFieldsTypesNullabilityAndSignedAmounts() throws Exception {
        long first = newUser(0), second = newUser(0);
        insertLedger(first, 1, "TOPUP", Integer.MAX_VALUE, 0, Integer.MAX_VALUE, null, null, AT);
        insertLedger(first, 2, "DEDUCT", -1, Integer.MAX_VALUE, Integer.MAX_VALUE - 1,
                "CUSTOM_FEATURE", "Điều chỉnh, \"中文\"\r\nDòng tiếp", AT.plusSeconds(1));
        insertLedger(second, 1, "TOPUP", Integer.MAX_VALUE, 0, Integer.MAX_VALUE, null, null, AT.plusSeconds(2));
        JsonNode document = locked();
        JsonNode page = json(filtered(""), 200);
        runtime(document, document.path("components").path("schemas").path("LedgerPage"), page, "LedgerPage");
        assertThat(page.path("items")).hasSize(3);
        assertThat(page.path("page").intValue()).isZero();
        assertThat(page.path("size").intValue()).isEqualTo(20);
        assertThat(page.path("total").longValue()).isEqualTo(3);
        assertThat(page.path("totals").path("count").longValue()).isEqualTo(3);
        assertThat(page.path("totals").path("credited").longValue()).isEqualTo(4_294_967_294L);
        assertThat(page.path("totals").path("debited").longValue()).isEqualTo(-1);
        JsonNode debit = page.path("items").get(1);
        assertThat(debit.path("amount").intValue()).isEqualTo(-1);
        assertThat(debit.path("featureCode").textValue()).isEqualTo("CUSTOM_FEATURE");
        assertThat(debit.path("reason").textValue()).isEqualTo("Điều chỉnh, \"中文\"\r\nDòng tiếp");
        assertThat(page.path("items").get(0).path("featureCode").isNull()).isTrue();
        assertThat(page.path("items").get(0).path("reason").isNull()).isTrue();
        for (JsonNode item : page.path("items")) {
            String rawEmail = jdbc.queryForObject("SELECT email FROM auth.users WHERE id=?", String.class, item.path("userId").longValue());
            assertThat(item.path("maskedEmail").textValue()).isNotBlank().isNotEqualTo(rawEmail);
            assertThat(page.toString()).doesNotContain(rawEmail);
        }
        var response = response(filtered("/export").accept("text/csv"), 200).getResponse();
        assertThat(MediaType.parseMediaType(response.getContentType()).isCompatibleWith(MediaType.parseMediaType("text/csv"))).isTrue();
        assertThat(MediaType.parseMediaType(response.getContentType()).getCharset()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"credit-ledger.csv\"");
        byte[] bytes = response.getContentAsByteArray();
        String text = new String(bytes, StandardCharsets.UTF_8);
        assertThat(bytes).isEqualTo(text.getBytes(StandardCharsets.UTF_8));
        assertThat(text).doesNotStartWith("\ufeff").endsWith("\r\n");
        try (CSVParser csv = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).build().parse(new StringReader(text))) {
            assertThat(csv.getHeaderNames()).isEqualTo(CSV_HEADER);
            List<CSVRecord> rows = csv.getRecords();
            assertThat(rows).hasSize(page.path("items").size());
            for (int i = 0; i < rows.size(); i++) {
                assertThat(rows.get(i).size()).isEqualTo(CSV_HEADER.size());
                for (String field : CSV_HEADER) {
                    JsonNode value = page.path("items").get(i).path(field);
                    String rendered = value.isNull() ? "" : value.asText();
                    if (!rendered.isEmpty() && "=+-@".indexOf(rendered.charAt(0)) >= 0) rendered = "'" + rendered;
                    assertThat(rows.get(i).get(field)).as("CSV row %s field %s agrees with JSON", i, field).isEqualTo(rendered);
                }
            }
        }
    }

    @Test void realGlobalReconciliationEmitsOnlyTheThreeLockedIssueKindsWithInt64Totals() throws Exception {
        newUser(Integer.MAX_VALUE);
        newUser(Integer.MAX_VALUE);
        long broken = newUser(0);
        insertLedger(broken, 2, "ADJUSTMENT", 1, 1, 2, null, "First row", AT.plusSeconds(1));
        insertLedger(broken, 4, "ADJUSTMENT", 0, 5, 5, null, "Broken chain", AT.plusSeconds(2));
        JsonNode document = locked();
        JsonNode result = json(get(BASE + "/reconciliation").header("Authorization", bearer()), 200);
        runtime(document, document.path("components").path("schemas").path("Reconciliation"), result, "Reconciliation");
        assertThat(result.path("users").longValue()).isEqualTo(3);
        assertThat(result.path("rows").longValue()).isEqualTo(4);
        assertThat(result.path("totalAmount").longValue()).isEqualTo(4_294_967_295L);
        assertThat(result.path("totalBalance").longValue()).isEqualTo(4_294_967_299L);
        assertThat(result.path("issues")).hasSize(4);
        Set<String> issues = new LinkedHashSet<>();
        for (JsonNode issue : result.path("issues")) {
            assertThat(issue.path("userId").longValue()).isEqualTo(broken);
            issues.add(issue.path("seq").intValue() + ":" + issue.path("kind").textValue());
        }
        assertThat(issues).isEqualTo(Set.of("2:SEQ_GAP", "2:FIRST_BALANCE_NOT_ZERO", "4:SEQ_GAP", "4:BALANCE_CHAIN_BROKEN"));
    }

    @Test void invalidExportWithCsvAcceptReturnsTheLockedJson422Envelope() throws Exception {
        for (var request : List.of(get(BASE + "/export"), get(BASE + "/export").param("from", "2031-11-15"),
                filtered("/export").param("type", "UNKNOWN"),
                get(BASE + "/export").param("from", "2031-02-29").param("to", "2031-03-01"))) {
            request.header("Authorization", bearer()).accept("text/csv");
            var response = response(request, 422).getResponse();
            assertThat(MediaType.parseMediaType(response.getContentType()).isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
            assertThat(response.getHeader("Content-Disposition")).isNull();
            JsonNode error = mapper.readTree(response.getContentAsByteArray());
            JsonNode creditContract = locked();
            String externalReference = creditContract.path("components").path("schemas").path("ErrorResponse").path("$ref").textValue();
            String[] reference = externalReference.split("#", 2);
            JsonNode externalDocument = new ObjectMapper(new YAMLFactory()).readTree(
                    Files.readString(CONTRACT.getParent().resolve(reference[0]).normalize()));
            JsonNode errorSchema = externalDocument.at(reference[1]);
            assertThat(fields(error)).isEqualTo(strings(errorSchema.path("required")))
                    .isEqualTo(Set.of("error_code", "message", "request_id"));
            error.fields().forEachRemaining(field -> runtime(externalDocument, errorSchema.path("properties").path(field.getKey()),
                    field.getValue(), "ErrorResponse." + field.getKey()));
            assertThat(error.path("error_code").textValue()).isEqualTo("VALIDATION_FAILED");
            assertThat(error.path("message").textValue()).isNotBlank();
            assertThat(error.path("request_id").textValue()).isNotBlank();
            assertThat(error.toString()).doesNotContain("stackTrace", "SQLException", "LedgerFilter", "UNKNOWN");
        }
        assertThat(auditCount()).isZero();
    }

    private static JsonNode locked() throws Exception { return new ObjectMapper(new YAMLFactory()).readTree(Files.readString(CONTRACT)); }
    private static void content(JsonNode expected, JsonNode actual, JsonNode document, String location, List<String> mismatches) {
        compare(fields(expected.path("content")), fields(actual.path("content")), location + " media types", mismatches);
        expected.path("content").fields().forEachRemaining(media -> schema(media.getValue().path("schema"),
                actual.path("content").path(media.getKey()).path("schema"), document, location + " " + media.getKey(), mismatches));
    }
    private static void schema(JsonNode expected, JsonNode actual, JsonNode document, String location, List<String> mismatches) {
        if (actual.isMissingNode()) { mismatches.add(location + " missing schema"); return; }
        // Springdoc may name an inline object or primitive; expand only a pure, resolvable local alias.
        if (!expected.has("$ref") && actual.has("$ref")) {
            String ref = actual.path("$ref").asText();
            JsonNode effective = ref.startsWith("#/") ? document.at(ref.substring(1)) : null;
            if (!semanticKeys(actual).equals(Set.of("$ref")) || effective == null || effective.isMissingNode() || effective.has("$ref")) {
                mismatches.add(location + " unresolved/chained alias or semantic siblings: " + actual); return;
            }
            schema(expected, effective, document, location + " resolved " + ref, mismatches); return;
        }
        Set<String> wantedKeys = semanticKeys(expected), foundKeys = semanticKeys(actual);
        compare(wantedKeys, foundKeys, location + " semantic keywords", mismatches);
        Set<String> keys = new LinkedHashSet<>(wantedKeys); keys.addAll(foundKeys);
        for (String key : keys) {
            JsonNode wanted = expected.path(key), found = actual.path(key);
            switch (key) {
                case "properties" -> {
                    compare(fields(wanted), fields(found), location + " properties", mismatches);
                    wanted.fields().forEachRemaining(property -> schema(property.getValue(), found.path(property.getKey()), document,
                            location + "." + property.getKey(), mismatches));
                }
                case "items" -> schema(wanted, found, document, location + "[]", mismatches);
                case "additionalProperties" -> {
                    if (wanted.isObject()) schema(wanted, found, document, location + " additionalProperties", mismatches);
                    else compare(wanted, found, location + " " + key, mismatches);
                }
                case "type" -> compare(types(expected), types(actual), location + " type", mismatches);
                case "required", "enum" -> compare(strings(wanted), strings(found), location + " " + key, mismatches);
                case "$ref" -> {
                    compare(wanted, found, location + " reference", mismatches);
                    // Shared ErrorResponse retains the inherited external-schema policy; its runtime envelope is checked above.
                    if (LEDGER_REFERENCES.contains(found.asText()) && document.at(found.asText().substring(1)).isMissingNode())
                        mismatches.add(location + " dangling reference " + found.asText());
                }
                default -> {
                    if (wanted.isNumber() && found.isNumber()) compare(wanted.decimalValue().stripTrailingZeros(),
                            found.decimalValue().stripTrailingZeros(), location + " " + key, mismatches);
                    else compare(wanted, found, location + " " + key, mismatches);
                }
            }
        }
    }
    private static void runtime(JsonNode document, JsonNode original, JsonNode value, String location) {
        JsonNode schema = resolve(document, original);
        assertThat(schema.isMissingNode()).as(location + " resolved schema").isFalse();
        Set<String> types = types(schema);
        if (value.isNull()) { assertThat(types).as(location + " nullable").contains("null"); return; }
        if (types.contains("object")) {
            assertThat(value.isObject()).as(location).isTrue();
            assertThat(fields(value)).as(location + " exact public fields").isEqualTo(fields(schema.path("properties")));
            schema.path("required").forEach(key -> assertThat(value.has(key.asText())).as(location + " required " + key).isTrue());
            schema.path("properties").fields().forEachRemaining(field -> runtime(document, field.getValue(), value.path(field.getKey()), location + "." + field.getKey()));
        } else if (types.contains("array")) {
            assertThat(value.isArray()).as(location).isTrue();
            value.forEach(item -> runtime(document, schema.path("items"), item, location + "[]"));
        } else if (types.contains("integer")) {
            assertThat(value.isIntegralNumber()).as(location).isTrue();
            if ("int64".equals(schema.path("format").asText())) assertThat(value.canConvertToLong()).as(location).isTrue();
            else assertThat(value.canConvertToInt()).as(location).isTrue();
            if (schema.has("minimum")) assertThat(value.decimalValue()).as(location).isGreaterThanOrEqualTo(schema.path("minimum").decimalValue());
            if (schema.has("maximum")) assertThat(value.decimalValue()).as(location).isLessThanOrEqualTo(schema.path("maximum").decimalValue());
        } else if (types.contains("string")) {
            assertThat(value.isTextual()).as(location).isTrue();
            if (schema.has("enum")) assertThat(strings(schema.path("enum"))).as(location).contains(value.textValue());
            if ("date-time".equals(schema.path("format").asText())) assertThat(Instant.parse(value.textValue()).toString()).isEqualTo(value.textValue());
        } else throw new AssertionError(location + " unsupported locked schema " + schema);
    }
    private static Map<String, JsonNode> operations(JsonNode document) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        document.path("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(method -> {
            if (METHODS.contains(method.getKey())) result.put(method.getKey() + " " + path.getKey(), method.getValue());
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
        (operation.has("security") ? operation.path("security") : document.path("security")).forEach(result::add); return result;
    }
    private static Set<String> semanticKeys(JsonNode node) { Set<String> keys = fields(node); keys.removeAll(ANNOTATIONS); return keys; }
    private static Set<String> fields(JsonNode node) { Set<String> keys = new LinkedHashSet<>(); node.fieldNames().forEachRemaining(keys::add); return keys; }
    private static Set<String> strings(JsonNode node) { Set<String> values = new LinkedHashSet<>(); node.forEach(value -> values.add(value.asText())); return values; }
    private static Set<String> types(JsonNode node) { return node.path("type").isArray() ? strings(node.path("type")) : node.has("type") ? Set.of(node.path("type").asText()) : Set.of(); }
    private static void compare(Object expected, Object actual, String location, List<String> mismatches) {
        if (!Objects.equals(expected, actual)) mismatches.add(location + " expected=" + expected + " actual=" + actual);
    }
}
