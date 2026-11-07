package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import javax.sql.DataSource;

import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(CardBatchIntegrationTest.QueryCaptureConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class CardBatchIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/admin/card-batches";
    private static final String MISSING = "B20000101-000000";
    private static final Logger LOG = LoggerFactory.getLogger(CardBatchIntegrationTest.class);
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private CardCodes codes;
    @Autowired private SqlCapture capture;
    @MockitoSpyBean private AuditService audit;

    private long admin;
    private long learner;
    private long accountant;
    private Map<String, Object> premiumBefore;

    @BeforeEach
    void fixtures() {
        admin = user("ADMIN");
        learner = user("LEARNER");
        accountant = user("ACCOUNTANT");
        List<Map<String, Object>> premium = jdbc.queryForList("SELECT * FROM learning.plans WHERE code = 'PREMIUM'");
        premiumBefore = premium.isEmpty() ? null : premium.get(0);
        jdbc.update("""
                INSERT INTO learning.plans(code, name, duration_days, is_active)
                VALUES ('PREMIUM', 'Synthetic card batch fixture', 30, true)
                ON CONFLICT(code) DO UPDATE SET is_active = true
                """);
    }

    @AfterEach
    void cleanOnlyOwnedFixtures() {
        capture.stop();
        reset(audit);
        jdbc.update("DELETE FROM shared.audit_logs WHERE actor_id IN (?, ?, ?)", admin, learner, accountant);
        jdbc.update("DELETE FROM learning.credit_cards WHERE created_by = ?", admin);
        jdbc.update("DELETE FROM auth.user_roles WHERE user_id IN (?, ?, ?)", admin, learner, accountant);
        jdbc.update("DELETE FROM auth.users WHERE id IN (?, ?, ?)", admin, learner, accountant);
        if (premiumBefore == null) {
            jdbc.update("DELETE FROM learning.plans WHERE code = 'PREMIUM'");
        } else {
            jdbc.update("UPDATE learning.plans SET is_active = ? WHERE code = 'PREMIUM'", premiumBefore.get("is_active"));
        }
    }

    @Test
    void everyEndpointRequiresAuthentication() throws Exception {
        for (MockHttpServletRequestBuilder request : endpoints()) {
            expectError(request, 401, "UNAUTHENTICATED");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEARNER", "ACCOUNTANT"})
    void everyEndpointRejectsRolesWithoutAdmin(String role) throws Exception {
        long id = role.equals("LEARNER") ? learner : accountant;
        for (MockHttpServletRequestBuilder request : endpoints()) {
            expectError(request.header("Authorization", bearer(id, role)), 403, "FORBIDDEN");
        }
    }

    @Test
    void cookieWritesRequireCsrf() throws Exception {
        Cookie access = new Cookie("access_token", jwt.issueAccessToken(admin, List.of("ADMIN"), true, UUID.randomUUID()));
        expectError(post(BASE).cookie(access).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(body())), 403, "CSRF_TOKEN_INVALID");
        expectError(patch(BASE + "/" + MISSING + "/void").cookie(access), 403, "CSRF_TOKEN_INVALID");
        JsonNode created = read(mvc.perform(post(BASE).cookie(access, new Cookie("XSRF-TOKEN", "batch-test-csrf"))
                        .header("X-XSRF-TOKEN", "batch-test-csrf").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(body())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mvc.perform(patch(BASE + "/" + created.path("batchId").asText() + "/void")
                        .cookie(access, new Cookie("XSRF-TOKEN", "batch-test-csrf"))
                        .header("X-XSRF-TOKEN", "batch-test-csrf"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.voided").value(1));
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("invalidRequests")
    void invalidInputsAreRejectedWithoutCardsOrAudit(String description, Map<String, Object> request) throws Exception {
        expectError(admin(post(BASE)).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(request)), 422, "VALIDATION_FAILED");
        assertThat(ownedCardCount()).isZero();
        assertThat(ownedAuditCount()).isZero();
    }

    static Stream<Arguments> invalidRequests() {
        List<Arguments> cases = new ArrayList<>();
        for (String required : List.of("cardType", "quantity", "channel")) {
            cases.add(Arguments.of(required + " missing", changed(required, null)));
        }
        for (int value : new int[] {0, -1, 101}) {
            cases.add(Arguments.of("quantity " + value, changed("quantity", value)));
        }
        for (Integer value : Arrays.asList(null, 0, -1, 10001)) {
            cases.add(Arguments.of("credit value " + value, changed("value", value)));
        }
        cases.add(Arguments.of("credit with planDays", changed("planDays", 30)));
        for (Integer days : Arrays.asList(null, 0, -1, 366)) {
            Map<String, Object> request = subscription();
            request.put("planDays", days);
            cases.add(Arguments.of("subscription days " + days, request));
        }
        Map<String, Object> subscriptionWithValue = subscription();
        subscriptionWithValue.put("value", 10);
        cases.add(Arguments.of("subscription with value", subscriptionWithValue));
        for (String partner : Arrays.asList(null, "", "   ")) {
            Map<String, Object> request = changed("channel", "PARTNER_BATCH");
            request.put("partnerCode", partner);
            cases.add(Arguments.of("partner without nonblank code " + partner, request));
        }
        for (String recipient : Arrays.asList(null, "", "   ")) {
            Map<String, Object> request = changed("channel", "ECOSYSTEM_GIFT");
            request.put("issuedTo", recipient);
            cases.add(Arguments.of("gift without nonblank recipient " + recipient, request));
        }
        cases.add(Arguments.of("past expiry", changed("expiresAt", "2000-01-01T00:00:00Z")));
        cases.add(Arguments.of("invalid card type", changed("cardType", "OTHER")));
        cases.add(Arguments.of("invalid channel", changed("channel", "OTHER")));
        cases.add(Arguments.of("long partner code", changed("partnerCode", "P".repeat(51))));
        cases.add(Arguments.of("long campaign", changed("campaign", "C".repeat(51))));
        cases.add(Arguments.of("long recipient", changed("issuedTo", "R".repeat(256))));
        cases.add(Arguments.of("fractional quantity", changed("quantity", 1.5)));
        cases.add(Arguments.of("text quantity", changed("quantity", "1")));
        cases.add(Arguments.of("overflow quantity", changed("quantity", Long.MAX_VALUE)));
        cases.add(Arguments.of("fractional credit value", changed("value", 1.5)));
        cases.add(Arguments.of("nontext campaign", changed("campaign", 123)));
        cases.add(Arguments.of("malformed expiry", changed("expiresAt", "not-an-instant")));
        return cases.stream();
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "null", "{}", "{", ""})
    void malformedOrNonObjectBodiesAreRejected(String request) throws Exception {
        expectError(admin(post(BASE)).contentType(MediaType.APPLICATION_JSON).content(request),
                422, "VALIDATION_FAILED");
        assertThat(ownedCardCount()).isZero();
        assertThat(ownedAuditCount()).isZero();
    }

    @Test
    void subscriptionRequiresActivePremium() throws Exception {
        jdbc.update("UPDATE learning.plans SET is_active = false WHERE code = 'PREMIUM'");
        expectError(admin(post(BASE)).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(subscription())), 422, "PLAN_NOT_AVAILABLE");
        assertThat(ownedCardCount()).isZero();
        assertThat(ownedAuditCount()).isZero();
    }

    @Test
    void createsHundredUniqueCodesAndNeverDisclosesCodesOrHashesAfterCreation(CapturedOutput output) throws Exception {
        JsonNode created = create(changed("quantity", 100));
        String batch = created.path("batchId").asText();
        assertThat(batch).matches("^B[0-9]{8}-[0-9A-Z]{6}$");
        List<String> rawCodes = new ArrayList<>();
        created.path("codes").forEach(value -> rawCodes.add(value.asText()));
        assertThat(rawCodes.size()).isEqualTo(100);
        assertThat(new HashSet<>(rawCodes).size()).isEqualTo(100);
        assertThat(rawCodes.stream().allMatch(value -> value.matches("[0-9A-Z]{4}(-[0-9A-Z]{4}){3}"))).isTrue();
        List<Map<String, Object>> stored = jdbc.queryForList("""
                SELECT code_hash, status, created_by, value, plan_days FROM learning.credit_cards WHERE batch_id = ?
                """, batch);
        assertThat(stored.size()).isEqualTo(100);
        stored.forEach(row -> {
            assertThat(row.get("status")).isEqualTo("UNUSED");
            assertThat(row.get("created_by")).isEqualTo(admin);
            assertThat(row.get("value")).isEqualTo(20);
            assertThat(row.get("plan_days")).isNull();
        });
        List<String> hashes = stored.stream().map(row -> row.get("code_hash").toString()).toList();
        assertThat(new HashSet<>(hashes).size()).isEqualTo(100);
        for (String code : rawCodes) {
            assertThat(hashes.contains(codes.hash(codes.normalize(code).orElseThrow()))).isTrue();
        }
        assertThat(ownedAuditCount()).isEqualTo(1);
        Map<String, Object> auditRow = jdbc.queryForMap("""
                SELECT action, target_type, target_id, detail::text AS detail FROM shared.audit_logs WHERE actor_id = ?
                """, admin);
        assertThat(auditRow).containsEntry("action", "CARD_BATCH_CREATE").containsEntry("target_id", null);
        JsonNode detail = read(auditRow.get("detail").toString());
        assertThat(detail.path("batchId").asText()).isEqualTo(batch);
        assertThat(detail.path("cardType").asText()).isEqualTo("CREDIT");
        assertThat(detail.path("quantity").asInt()).isEqualTo(100);
        assertThat(detail.path("value").asInt()).isEqualTo(20);
        assertThat(detail.path("planDays").isNull()).isTrue();
        assertThat(detail.path("totalValue").asLong()).isEqualTo(2000);
        assertThat(detail.path("channel").asText()).isEqualTo("DIRECT");
        String afterCreate = getText(BASE + "/" + batch) + getText(BASE);
        assertThat(afterCreate.contains("code_hash") || afterCreate.contains("codeHash") || afterCreate.contains("\"codes\"")
                || afterCreate.contains("usedBy") || afterCreate.contains("used_by"))
                .isFalse();
        String auditText = jdbc.queryForObject("SELECT string_agg(detail::text, ' ') FROM shared.audit_logs", String.class);
        for (String code : rawCodes) {
            assertThat(output.getAll().contains(code)).as("formatted code absent from logs").isFalse();
            assertThat(output.getAll().contains(code.replace("-", ""))).as("canonical code absent from logs").isFalse();
            assertThat(auditText.contains(code) || auditText.contains(code.replace("-", "")))
                    .as("codes absent from all persisted audit details").isFalse();
            assertThat(afterCreate.contains(code) || afterCreate.contains(code.replace("-", ""))).isFalse();
        }
        for (String hash : hashes) {
            assertThat(output.getAll().contains(hash)).as("hash absent from logs").isFalse();
            assertThat(auditText.contains(hash)).as("hash absent from audit").isFalse();
            assertThat(afterCreate.contains(hash)).as("hash absent from GET responses").isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"DIRECT", "PARTNER_BATCH", "ECOSYSTEM_GIFT"})
    void bothCardTypesPersistMetadataAndExposeTheirOwnValueFields(String channel) throws Exception {
        for (Map<String, Object> request : List.of(body(), subscription())) {
            request.put("channel", channel);
            request.put("partnerCode", "synthetic-partner");
            request.put("campaign", "synthetic-campaign");
            request.put("issuedTo", "Synthetic recipient");
            request.put("expiresAt", "2099-01-01T00:00:00Z");
            String batch = create(request).path("batchId").asText();
            JsonNode detail = read(getText(BASE + "/" + batch));
            assertNumericSummary(detail);
            assertNumericPage(read(getText(BASE)));
            assertThat(detail.path("cardType").asText()).isEqualTo(request.get("cardType"));
            assertThat(detail.path("channel").asText()).isEqualTo(channel);
            assertThat(detail.path("partnerCode").asText()).isEqualTo("synthetic-partner");
            assertThat(detail.path("campaign").asText()).isEqualTo("synthetic-campaign");
            assertThat(detail.path("issuedTo").asText()).isEqualTo("Synthetic recipient");
            assertThat(Instant.parse(detail.path("expiresAt").asText())).isEqualTo(Instant.parse("2099-01-01T00:00:00Z"));
            assertThat(detail.path("createdByName").asText()).isEqualTo("Synthetic batch ADMIN");
            JsonNode auditDetail = read(jdbc.queryForObject("""
                    SELECT detail::text FROM shared.audit_logs WHERE actor_id = ? AND detail->>'batchId' = ?
                    """, String.class, admin, batch));
            boolean subscription = request.get("cardType").equals("SUBSCRIPTION");
            assertThat(detail.path(subscription ? "value" : "planDays").isNull()).isTrue();
            assertThat(detail.path(subscription ? "planDays" : "value").asInt()).isEqualTo(subscription ? 30 : 20);
            assertThat(auditDetail.path("value").isNull()).isEqualTo(subscription);
            assertThat(auditDetail.path("totalValue").isNull()).isEqualTo(subscription);
            assertThat(jdbc.queryForObject("SELECT value FROM learning.credit_cards WHERE batch_id = ?", Integer.class, batch))
                    .isEqualTo(subscription ? 30 : 20);
        }
    }

    @Test
    void groupedPaginationHasStableQueryCountAndCorrectStatusCounts() throws Exception {
        String oldest = create(changed("quantity", 4)).path("batchId").asText();
        markUsed(oldest, 2);
        String middle = create(body()).path("batchId").asText();
        String newest = create(body()).path("batchId").asText();
        jdbc.update("UPDATE learning.credit_cards SET created_at = ? WHERE batch_id = ?", java.sql.Timestamp.from(Instant.parse("2090-01-01T00:00:00Z")), oldest);
        jdbc.update("UPDATE learning.credit_cards SET created_at = ? WHERE batch_id = ?", java.sql.Timestamp.from(Instant.parse("2090-01-02T00:00:00Z")), middle);
        jdbc.update("UPDATE learning.credit_cards SET created_at = ? WHERE batch_id = ?", java.sql.Timestamp.from(Instant.parse("2090-01-03T00:00:00Z")), newest);
        capture.start();
        JsonNode first = read(getText(BASE + "?page=0&size=1"));
        List<String> one = capture.stop();
        capture.start();
        JsonNode several = read(getText(BASE + "?page=0&size=3"));
        List<String> many = capture.stop();
        assertThat(first.path("items")).hasSize(1);
        assertThat(first.path("items").get(0).path("batchId").asText()).isEqualTo(newest);
        assertThat(several.path("items")).hasSize(3);
        assertThat(several.path("items").get(1).path("batchId").asText()).isEqualTo(middle);
        JsonNode summary = several.path("items").get(2);
        assertThat(summary.path("batchId").asText()).isEqualTo(oldest);
        assertThat(summary.path("total").asInt()).isEqualTo(4);
        assertThat(summary.path("unused").asInt()).isEqualTo(2);
        assertThat(summary.path("used").asInt()).isEqualTo(2);
        assertThat(summary.path("voided").asInt()).isZero();
        assertThat(first.path("total").asLong()).isEqualTo(several.path("total").asLong()).isGreaterThanOrEqualTo(3);
        assertThat(first.path("page").asInt()).isZero();
        assertThat(first.path("size").asInt()).isEqualTo(1);
        assertThat(selects(many)).hasSize(selects(one).size());
        assertThat(many.stream().anyMatch(sql -> sql.contains("credit_cards") && sql.contains("group by"))).isTrue();
        JsonNode second = read(getText(BASE + "?page=1&size=1"));
        assertThat(second.path("items").get(0).path("batchId").asText()).isEqualTo(middle);
        assertNumericPage(first);
        assertNumericPage(several);
        assertNumericPage(second);
        expectError(admin(get(BASE).param("page", "-1")), 422, "VALIDATION_FAILED");
        expectError(admin(get(BASE).param("size", "0")), 422, "VALIDATION_FAILED");
        expectError(admin(get(BASE).param("size", "101")), 422, "VALIDATION_FAILED");
        expectError(admin(get(BASE + "/" + MISSING)), 404, "CARD_BATCH_NOT_FOUND");
        expectError(admin(patch(BASE + "/" + MISSING + "/void")), 404, "CARD_BATCH_NOT_FOUND");
    }

    @Test
    void voidChangesOnlyUnusedCardsAndIsIdempotent() throws Exception {
        String batch = create(changed("quantity", 10)).path("batchId").asText();
        markUsed(batch, 3);
        List<Map<String, Object>> used = jdbc.queryForList("SELECT id, used_by, used_at FROM learning.credit_cards WHERE batch_id = ? AND status = 'USED' ORDER BY id", batch);
        mvc.perform(admin(patch(BASE + "/" + batch + "/void"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(batch)).andExpect(jsonPath("$.voided").value(7));
        mvc.perform(admin(patch(BASE + "/" + batch + "/void"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.voided").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.credit_cards WHERE batch_id = ? AND status = 'VOIDED'", Integer.class, batch)).isEqualTo(7);
        assertThat(jdbc.queryForList("SELECT id, used_by, used_at FROM learning.credit_cards WHERE batch_id = ? AND status = 'USED' ORDER BY id", batch)).isEqualTo(used);
        assertThat(jdbc.queryForList("SELECT (detail->>'voided')::int FROM shared.audit_logs WHERE actor_id = ? AND action = 'CARD_BATCH_VOID' ORDER BY id", Integer.class, admin)).containsExactly(7, 0);
    }

    @Test
    void auditFailureRollsBackCreateAndVoid() throws Exception {
        doThrow(new IllegalStateException("Synthetic audit failure")).when(audit)
                .recordDetail(eq(admin), eq("CARD_BATCH_CREATE"), eq("CARD_BATCH"), any(Map.class));
        mvc.perform(admin(post(BASE)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body())))
                .andExpect(status().isInternalServerError());
        assertThat(ownedCardCount()).isZero();
        assertThat(ownedAuditCount()).isZero();
        reset(audit);
        String batch = create(changed("quantity", 3)).path("batchId").asText();
        doThrow(new IllegalStateException("Synthetic audit failure")).when(audit)
                .recordDetail(eq(admin), eq("CARD_BATCH_VOID"), eq("CARD_BATCH"), any(Map.class));
        mvc.perform(admin(patch(BASE + "/" + batch + "/void"))).andExpect(status().isInternalServerError());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.credit_cards WHERE batch_id = ? AND status = 'UNUSED'", Integer.class, batch)).isEqualTo(3);
        assertThat(ownedAuditCount()).isEqualTo(1);
    }

    @Test
    void hundredCodeCreationHttpP95IsBelowTwoSeconds() throws Exception {
        create(changed("quantity", 100));
        long[] durations = new long[5];
        for (int iteration = 0; iteration < durations.length; iteration++) {
            long start = System.nanoTime();
            JsonNode result = create(changed("quantity", 100));
            durations[iteration] = System.nanoTime() - start;
            assertThat(result.path("codes").size()).isEqualTo(100);
        }
        Arrays.sort(durations);
        long p95 = durations[(int) Math.ceil(0.95 * durations.length) - 1];
        LOG.info("100-card HTTP creation durations (ms): {}; nearest-rank p95: {} ms",
                Arrays.stream(durations).map(value -> value / 1_000_000).toArray(), p95 / 1_000_000);
        assertThat(p95).isLessThan(2_000_000_000L);
    }

    private long user(String role) {
        long id = jdbc.queryForObject("INSERT INTO auth.users(email, password_hash, display_name, email_verified_at) VALUES (?, ?, ?, now()) RETURNING id", Long.class,
                "batch-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH, "Synthetic batch " + role);
        jdbc.update("INSERT INTO auth.user_roles(user_id, role_code) VALUES (?, ?)", id, role);
        return id;
    }

    private List<MockHttpServletRequestBuilder> endpoints() throws Exception {
        return List.of(post(BASE).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body())),
                get(BASE), get(BASE + "/" + MISSING), patch(BASE + "/" + MISSING + "/void"));
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", bearer(admin, "ADMIN"));
    }

    private String bearer(long id, String role) {
        return "Bearer " + jwt.issueAccessToken(id, List.of(role), true, UUID.randomUUID());
    }

    private void expectError(MockHttpServletRequestBuilder request, int code, String error) throws Exception {
        mvc.perform(request.header("X-Request-Id", "card-batch-test")).andExpect(status().is(code))
                .andExpect(jsonPath("$.error_code").value(error)).andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.request_id").value("card-batch-test"));
    }

    private JsonNode create(Map<String, Object> request) throws Exception {
        return read(mvc.perform(admin(post(BASE)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String getText(String url) throws Exception {
        return mvc.perform(admin(get(url))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private JsonNode read(String json) throws Exception { return mapper.readTree(json); }

    private static void assertNumericPage(JsonNode page) {
        for (String field : List.of("total", "page", "size")) {
            assertThat(page.path(field).isIntegralNumber()).as("Page %s is a JSON integer", field).isTrue();
        }
        page.path("items").forEach(CardBatchIntegrationTest::assertNumericSummary);
    }

    private static void assertNumericSummary(JsonNode summary) {
        for (String field : List.of("total", "unused", "used", "voided", "value", "planDays")) {
            JsonNode value = summary.path(field);
            if (value.isNull()) {
                assertThat(field).as("Only value/planDays may be null").isIn("value", "planDays");
            } else {
                assertThat(value.isIntegralNumber()).as("Summary %s is a JSON integer", field).isTrue();
            }
        }
        if ("CREDIT".equals(summary.path("cardType").asText())) {
            assertThat(summary.path("value").isIntegralNumber()).isTrue();
            assertThat(summary.path("planDays").isNull()).isTrue();
        } else {
            assertThat(summary.path("value").isNull()).isTrue();
            assertThat(summary.path("planDays").isIntegralNumber()).isTrue();
        }
    }

    private long ownedCardCount() {
        return jdbc.queryForObject("SELECT count(*) FROM learning.credit_cards WHERE created_by = ?", Long.class, admin);
    }

    private long ownedAuditCount() {
        return jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs WHERE actor_id = ?", Long.class, admin);
    }

    private void markUsed(String batch, int count) {
        jdbc.update("""
                UPDATE learning.credit_cards SET status = 'USED', used_by = ?, used_at = now()
                WHERE id IN (SELECT id FROM learning.credit_cards WHERE batch_id = ? ORDER BY id LIMIT ?)
                """, learner, batch, count);
    }

    private static Map<String, Object> body() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cardType", "CREDIT");
        result.put("value", 20);
        result.put("quantity", 1);
        result.put("channel", "DIRECT");
        return result;
    }

    private static Map<String, Object> changed(String key, Object value) {
        Map<String, Object> result = body();
        result.put(key, value);
        return result;
    }

    private static Map<String, Object> subscription() {
        Map<String, Object> result = changed("cardType", "SUBSCRIPTION");
        result.remove("value");
        result.put("planDays", 30);
        return result;
    }

    private static List<String> selects(List<String> statements) {
        return statements.stream().filter(sql -> sql.startsWith("select") || sql.startsWith("with")).toList();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class QueryCaptureConfiguration {
        @Bean static SqlCapture cardBatchSqlCapture() { return new SqlCapture(); }

        @Bean static BeanPostProcessor cardBatchDataSourceCapture(SqlCapture capture) {
            return new BeanPostProcessor() {
                @Override public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!(bean instanceof DataSource dataSource)) { return bean; }
                    return new DelegatingDataSource(dataSource) {
                        @Override public Connection getConnection() throws SQLException {
                            return capture.wrap(super.getConnection());
                        }
                        @Override public Connection getConnection(String username, String password) throws SQLException {
                            return capture.wrap(super.getConnection(username, password));
                        }
                    };
                }
            };
        }
    }

    /** Captures SQL structure only, never parameter values or returned codes. */
    static final class SqlCapture {
        private final ThreadLocal<List<String>> statements = new ThreadLocal<>();
        void start() { statements.set(new ArrayList<>()); }
        List<String> stop() {
            List<String> result = statements.get();
            statements.remove();
            return result == null ? List.of() : List.copyOf(result);
        }
        Connection wrap(Connection connection) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                    (proxy, method, arguments) -> {
                        if (statements.get() != null && method.getName().startsWith("prepare")
                                && arguments != null && arguments.length > 0 && arguments[0] instanceof String sql) {
                            statements.get().add(sql.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "));
                        }
                        try { return method.invoke(connection, arguments); }
                        catch (InvocationTargetException failure) { throw failure.getCause(); }
                    });
        }
    }
}
