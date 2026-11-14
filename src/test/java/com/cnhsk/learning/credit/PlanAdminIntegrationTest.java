package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassDescriptor;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.ClassOrdererContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class PlanAdminIntegrationTest extends AbstractIntegrationTest {
    private static final String BASE = "/api/admin/plans";
    private static final Instant NOW = Instant.parse("2031-11-15T10:00:00Z");
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private static final Set<String> VIEW_KEYS = Set.of("code", "exists", "name", "description", "durationDays",
            "price", "benefits", "isActive", "activeUsers");
    private static final Set<String> SNAPSHOT_KEYS = Set.of("name", "description", "durationDays", "price", "benefits", "isActive");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @MockitoSpyBean private AuditService audit;
    @MockitoSpyBean private NamedParameterJdbcTemplate named;
    @MockitoSpyBean private Clock clock;

    private final List<Long> users = new ArrayList<>();
    private final Set<String> touchedPlans = new LinkedHashSet<>();
    private List<Map<String, Object>> catalogBefore;
    private List<Map<String, Object>> globalAuditBefore;
    private long admin;
    private long baselineFree;
    private long baselinePremium;

    @BeforeEach void fixtures() {
        doReturn(NOW).when(clock).instant();
        catalogBefore = catalog();
        globalAuditBefore = globalAudit();
        baselineFree = jdbc.queryForObject("SELECT count(*) FROM auth.users WHERE plan_code='FREE' OR plan_expires_at<=?",
                Long.class, Timestamp.from(NOW));
        baselinePremium = jdbc.queryForObject("SELECT count(*) FROM auth.users WHERE plan_code='PREMIUM' AND plan_expires_at>?",
                Long.class, Timestamp.from(NOW));
        admin = user("ADMIN");
        touchedPlans.add("PREMIUM");
        jdbc.update("""
                INSERT INTO learning.plans(code,name,description,duration_days,price,benefits,is_active)
                VALUES ('PREMIUM','Synthetic original','Original description',30,12.50,'{"AI_GENERATE":7}',true)
                ON CONFLICT(code) DO UPDATE SET name=EXCLUDED.name,description=EXCLUDED.description,
                    duration_days=EXCLUDED.duration_days,price=EXCLUDED.price,benefits=EXCLUDED.benefits,is_active=true
                """);
    }

    @AfterEach void restoreOnlyOwnedFixtures(TestInfo test) {
        List<Long> ownedIds = List.copyOf(users);
        List<Executable> cleanup = new ArrayList<>();
        cleanup.add(() -> {
            AuditService target = AopTestUtils.getUltimateTargetObject(audit);
            reset(target);
        });
        cleanup.add(() -> {
            NamedParameterJdbcTemplate target = AopTestUtils.getUltimateTargetObject(named);
            reset(target);
        });
        cleanup.add(() -> reset(clock));
        for (long id : ownedIds) {
            cleanup.add(() -> jdbc.update("DELETE FROM shared.audit_logs WHERE actor_id=?", id));
            cleanup.add(() -> jdbc.update("DELETE FROM learning.credit_transactions WHERE user_id=?", id));
            cleanup.add(() -> jdbc.update("DELETE FROM auth.user_roles WHERE user_id=?", id));
            cleanup.add(() -> jdbc.update("DELETE FROM auth.users WHERE id=?", id));
        }
        for (String code : touchedPlans) cleanup.add(() -> restorePlan(code));
        cleanup.add(() -> verifyCleanupAndRecord(test, ownedIds));
        assertAll("Restore all owned plan fixtures and retain every cleanup failure", cleanup.stream());
        users.clear();
        touchedPlans.clear();
    }

    @Test void getReturnsThreeOrderedPlansAndRealEffectiveCountsWithoutAnyWrite() throws Exception {
        user("LEARNER");
        long expired = subscriber(NOW.minusSeconds(1));
        subscriber(NOW);
        subscriber(NOW.plusSeconds(1));
        long banned = subscriber(NOW.plusSeconds(3600));
        long suspended = subscriber(NOW.plusSeconds(3600));
        jdbc.update("UPDATE auth.users SET banned_at=?,ban_reason='Synthetic fixture ban' WHERE id=?", Timestamp.from(NOW.minusSeconds(1)), banned);
        jdbc.update("UPDATE auth.users SET suspended_at=?,suspended_until=? WHERE id=?",
                Timestamp.from(NOW.minusSeconds(1)), Timestamp.from(NOW.plusSeconds(3600)), suspended);
        jdbc.update("UPDATE auth.users SET free_usage=CAST(? AS jsonb) WHERE id=?", "{\"TRANSLATE\":3}", expired);
        List<Map<String, Object>> catalogSnapshot = catalog();
        List<Map<String, Object>> userSnapshot = ownedUserState();
        List<Map<String, Object>> ledgerSnapshot = ownedLedger();
        JsonNode plans = getPlans();
        assertThat(plans).hasSize(3);
        assertThat(plans.get(0).path("code").asText()).isEqualTo("FREE");
        assertThat(plans.get(1).path("code").asText()).isEqualTo("PREMIUM");
        assertThat(plans.get(2).path("code").asText()).isEqualTo("PREMIUM_PLUS");
        for (JsonNode plan : plans) assertView(plan);
        assertThat(plans.get(0).path("activeUsers").longValue()).isEqualTo(baselineFree + 4);
        assertThat(plans.get(1).path("activeUsers").longValue()).isEqualTo(baselinePremium + 3);
        assertThat(plans.get(2).path("activeUsers").longValue()).isZero();
        assertThat(plans.get(1).path("price").textValue()).isEqualTo("12.50");
        assertThat(catalog()).isEqualTo(catalogSnapshot);
        assertThat(ownedUserState()).isEqualTo(userSnapshot);
        assertThat(ownedLedger()).isEqualTo(ledgerSnapshot);
        assertThat(audits()).isEmpty();
    }

    @Test void absentPremiumHasAnExplicitPlaceholderAndCreateReturns200WithAtomicCreatedAudit() throws Exception {
        removeUnreferencedPremium();
        JsonNode missing = getPlans().get(1);
        assertView(missing);
        assertThat(missing.path("exists").booleanValue()).isFalse();
        for (String field : List.of("name", "description", "durationDays", "price", "isActive"))
            assertThat(missing.has(field) && missing.path(field).isNull()).as(field).isTrue();
        assertThat(missing.path("benefits").isObject()).isTrue();
        assertThat(missing.path("benefits")).isEmpty();
        assertThat(missing.path("activeUsers").longValue()).isZero();
        JsonNode created = update("PREMIUM", body(), 200);
        assertView(created);
        assertThat(created.path("exists").booleanValue()).isTrue();
        assertThat(created.path("price").textValue()).isEqualTo("12.50");
        assertAudit(0, "PLAN_CREATED", "PREMIUM", null, snapshot("PREMIUM"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.plans WHERE code='PREMIUM'", Long.class)).isEqualTo(1);
    }

    @Test void updatesAndUnchangedPutsAuditActualBeforeAfterAndPreserveCreationTime() throws Exception {
        Object createdAt = row("PREMIUM").get("created_at");
        JsonNode before = snapshot("PREMIUM");
        update("PREMIUM", body().put("name", "  Updated name  ").put("description", "Updated description")
                .put("durationDays", 365).put("price", "9999999999.99").put("isActive", false), 200);
        JsonNode after = snapshot("PREMIUM");
        assertThat(after.path("name").textValue()).isEqualTo("Updated name");
        assertAudit(0, "PLAN_UPDATED", "PREMIUM", before, after);
        update("PREMIUM", bodyFrom(after), 200);
        assertAudit(1, "PLAN_UPDATED", "PREMIUM", after, after);
        assertThat(audits()).hasSize(2);
        assertThat(row("PREMIUM").get("created_at")).isEqualTo(createdAt);
    }

    @Test void benefitsGuardRejectsRealChangesButAllowsScalarsAndEquivalentSparseZeroValues() throws Exception {
        subscriber(NOW.plusSeconds(3600));
        JsonNode before = snapshot("PREMIUM");
        ObjectNode changed = body();
        changed.set("benefits", mapper.createObjectNode().put("AI_GENERATE", 8));
        assertError(update("PREMIUM", changed, 409), "PLAN_IN_USE");
        assertThat(snapshot("PREMIUM")).isEqualTo(before);
        assertThat(audits()).isEmpty();
        ObjectNode equivalent = body().put("name", "Still in use").put("price", "0").put("durationDays", 1).put("isActive", false);
        equivalent.set("benefits", mapper.createObjectNode().put("AI_GENERATE", 7).put("TRANSLATE", 0)
                .put("ASSISTANT", 0).put("GRADING", 0));
        update("PREMIUM", equivalent, 200);
        assertThat(snapshot("PREMIUM").path("price").textValue()).isEqualTo("0.00");
        assertAudit(0, "PLAN_UPDATED", "PREMIUM", before, snapshot("PREMIUM"));
        ObjectNode sparse = bodyFrom(snapshot("PREMIUM"));
        sparse.set("benefits", mapper.createObjectNode().put("AI_GENERATE", 7));
        update("PREMIUM", sparse, 200);
        assertThat(audits()).hasSize(2);
        assertThat(getPlans().get(1).path("activeUsers").longValue()).isEqualTo(baselinePremium + 1);
    }

    @Test void expiredSubscribersDoNotIncreaseTheGuardCountAndSparseMissingLimitsRemainMissing() throws Exception {
        subscriber(NOW);
        subscriber(NOW.minusSeconds(1));
        assertThat(getPlans().get(1).path("activeUsers").longValue()).isEqualTo(baselinePremium);
        JsonNode before = snapshot("PREMIUM");
        ObjectNode request = body();
        request.set("benefits", mapper.createObjectNode().put("GRADING", 10_000));
        JsonNode result = update("PREMIUM", request, baselinePremium == 0 ? 200 : 409);
        if (baselinePremium > 0) {
            assertError(result, "PLAN_IN_USE");
            assertThat(snapshot("PREMIUM")).isEqualTo(before);
            assertThat(audits()).isEmpty();
            return;
        }
        assertThat(keys(result.path("benefits"))).containsExactly("GRADING");
        assertThat(result.path("benefits").path("GRADING").intValue()).isEqualTo(10_000);
        assertThat(result.path("activeUsers").longValue()).isZero();
    }

    @Test void freeChangesOnlyNameAndDescriptionAndIgnoresPremiumFieldsIncludingNulls() throws Exception {
        Map<String, Object> original = row("FREE");
        JsonNode before = snapshot("FREE");
        ObjectNode request = body().put("name", "  Free renamed  ").put("description", "Free description")
                .put("price", "999.99").put("durationDays", 365).put("isActive", false);
        request.set("benefits", mapper.createObjectNode().put("GRADING", 10_000));
        update("FREE", request, 200);
        Map<String, Object> after = row("FREE");
        for (String field : List.of("duration_days", "price", "benefits", "is_active", "created_at"))
            assertThat(after.get(field)).as(field).isEqualTo(original.get(field));
        assertAudit(0, "PLAN_UPDATED", "FREE", before, snapshot("FREE"));
        update("FREE", mapper.createObjectNode().put("name", "Free null fields").putNull("description")
                .putNull("durationDays").putNull("price").putNull("benefits").putNull("isActive"), 200);
        assertThat(row("FREE")).containsEntry("description", null).containsEntry("duration_days", null);
        assertThat(row("FREE").get("price")).isEqualTo(original.get("price"));
        assertThat(row("FREE").get("benefits")).isEqualTo(original.get("benefits"));
        assertThat(row("FREE").get("is_active")).isEqualTo(original.get("is_active"));
        assertThat(audits()).hasSize(2);
    }

    @Test void freeIgnoresWellTypedPremiumSemanticsAndAuditsOnlyActualEditableChanges() throws Exception {
        Map<String, Object> original = row("FREE");
        JsonNode before = snapshot("FREE");
        List<Map<String, Object>> usersBefore = ownedUserState();
        List<Map<String, Object>> ledgerBefore = ownedLedger();
        ObjectNode arbitraryPrice = mapper.createObjectNode().put("name", "  Free semantic fields ignored  ")
                .put("description", "Only editable fields change").put("durationDays", -1)
                .put("price", "abc").put("isActive", false);
        arbitraryPrice.set("benefits", mapper.createObjectNode().put("UNKNOWN", -1)
                .put("GRADING", 10_001).put("TRANSLATE", Long.MAX_VALUE));
        ObjectNode overflowingValues = mapper.createObjectNode().put("name", "Free overflow fields ignored")
                .putNull("description").put("durationDays", new java.math.BigInteger("1" + "0".repeat(50)))
                .put("price", "12.500").put("isActive", false);
        overflowingValues.set("benefits", mapper.createObjectNode().put("UNSUPPORTED", Long.MAX_VALUE)
                .put("AI_GENERATE", -10_001));
        int auditIndex = 0;
        for (ObjectNode request : List.of(arbitraryPrice, overflowingValues)) {
            ObjectNode expected = ((ObjectNode) before).deepCopy();
            expected.put("name", request.path("name").textValue().trim());
            if (request.path("description").isNull()) expected.putNull("description");
            else expected.put("description", request.path("description").textValue());
            JsonNode result = update("FREE", request, 200);
            assertView(result);
            for (String field : List.of("duration_days", "price", "benefits", "is_active", "created_at"))
                assertThat(row("FREE").get(field)).as(field + " ignores noneditable input").isEqualTo(original.get(field));
            assertThat(snapshot("FREE")).isEqualTo(expected);
            assertAudit(auditIndex++, "PLAN_UPDATED", "FREE", before, expected);
            before = expected;
        }
        assertThat(audits()).hasSize(2);
        assertThat(ownedUserState()).isEqualTo(usersBefore);
        assertThat(ownedLedger()).isEqualTo(ledgerBefore);
    }

    @Test void descriptionOmittedOrNullClearsForBothEditableCodesAndUnknownTopLevelFieldsAreIgnored() throws Exception {
        for (String code : List.of("FREE", "PREMIUM")) {
            touchedPlans.add(code);
            jdbc.update("UPDATE learning.plans SET description='Must clear' WHERE code=?", code);
            ObjectNode request = code.equals("FREE") ? mapper.createObjectNode().put("name", "Free") : body();
            request.remove("description");
            request.put("unknown", "ignored by contract");
            update(code, request, 200);
            assertThat(row(code)).containsEntry("description", null);
            jdbc.update("UPDATE learning.plans SET description='Must clear again' WHERE code=?", code);
            request.putNull("description");
            update(code, request, 200);
            assertThat(row(code)).containsEntry("description", null);
        }
        assertThat(audits()).hasSize(4);
    }

    @Test void unavailableAndInvalidPathCodesNeverMutateCatalogUsersOrAudit() throws Exception {
        List<Map<String, Object>> before = catalog();
        List<Map<String, Object>> userBefore = ownedUserState();
        assertError(update("PREMIUM_PLUS", body(), 422), "PLAN_NOT_AVAILABLE");
        for (String code : List.of("premium", "UNKNOWN")) assertError(update(code, body(), 422), "VALIDATION_FAILED");
        assertError(update("PREMIUM_PLUS", mapper.createObjectNode(), 422), "VALIDATION_FAILED");
        assertThat(catalog()).isEqualTo(before);
        assertThat(ownedUserState()).isEqualTo(userBefore);
        assertThat(audits()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test void simulatedMissingFreeLookupReturns422WithoutCreatingDefaultsOrAudit() throws Exception {
        List<Map<String, Object>> before = catalog();
        // The real FREE row may have unrelated FK dependants; only simulate this missing-row read.
        doReturn(List.of()).when(named).query(eq("SELECT code,name,description,duration_days,price,benefits::text,is_active FROM learning.plans WHERE code=:code FOR NO KEY UPDATE"),
                eq(Map.of("code", "FREE")), any(RowMapper.class));
        assertError(update("FREE", mapper.createObjectNode().put("name", "Unavailable"), 422), "PLAN_NOT_AVAILABLE");
        assertThat(catalog()).isEqualTo(before);
        assertThat(audits()).isEmpty();
    }

    @Test void strictJsonMalformedTypesDuplicateFieldsAndTrailingTokensReturn422BeforeMutation() throws Exception {
        List<Map<String, Object>> before = catalog();
        List<String> invalid = new ArrayList<>(List.of("", "{", "[]", "null", "true", "3", "\"text\"", "{}",
                "{\"name\":\"one\",\"name\":\"two\"}", "{\"name\":\"one\"} {}", "{\"name\":\"one\"} garbage",
                "{\"name\":\"one\",\"benefits\":{\"GRADING\":1,\"GRADING\":2}}"));
        for (String field : List.of("name", "description", "price")) invalid.add("{\"name\":\"x\",\"" + field + "\":1}");
        for (String value : List.of("\"30\"", "30.0", "true", "[]", "{}"))
            invalid.add("{\"name\":\"x\",\"durationDays\":" + value + "}");
        for (String value : List.of("\"false\"", "0", "[]", "{}")) invalid.add("{\"name\":\"x\",\"isActive\":" + value + "}");
        for (String value : List.of("[]", "true", "1", "\"limits\"", "{\"GRADING\":null}", "{\"GRADING\":1.0}",
                "{\"GRADING\":\"1\"}", "{\"UNKNOWN\":\"1\"}", "{\"GRADING\":true}", "{\"GRADING\":[]}", "{\"GRADING\":{}}"))
            invalid.add("{\"name\":\"x\",\"benefits\":" + value + "}");
        for (String body : invalid) assertError(rawUpdate("FREE", body, 422), "VALIDATION_FAILED");
        assertThat(catalog()).isEqualTo(before);
        assertThat(audits()).isEmpty();
    }

    @Test void premiumRequiresEveryNonNullFieldAndRejectsInvalidMoneyWithoutRounding() throws Exception {
        List<Map<String, Object>> before = catalog();
        for (String field : List.of("name", "durationDays", "price", "benefits", "isActive")) {
            ObjectNode missing = body(); missing.remove(field);
            assertError(update("PREMIUM", missing, 422), "VALIDATION_FAILED");
            assertError(update("PREMIUM", body().putNull(field), 422), "VALIDATION_FAILED");
        }
        for (String price : List.of("abc", "1.000", "12.500", "-1", "+1", "1e2", ".50", "1.", "10000000000", " 1", "1,00"))
            assertError(update("PREMIUM", body().put("price", price), 422), "VALIDATION_FAILED");
        for (int days : new int[]{0, -1, 366})
            assertError(update("PREMIUM", body().put("durationDays", days), 422), "VALIDATION_FAILED");
        assertError(update("PREMIUM", body().put("durationDays", Long.MAX_VALUE), 422), "VALIDATION_FAILED");
        for (String benefits : List.of("{\"UNKNOWN\":1}", "{\"GRADING\":-1}", "{\"GRADING\":10001}", "{\"GRADING\":2147483648}")) {
            ObjectNode invalid = body();
            invalid.set("benefits", mapper.readTree(benefits));
            assertError(update("PREMIUM", invalid, 422), "VALIDATION_FAILED");
        }
        assertThat(catalog()).isEqualTo(before);
        assertThat(audits()).isEmpty();
    }

    @Test void unicodeTextLimitsAreCodePointsAndNullCharactersNeverReachPostgres() throws Exception {
        String character = new String(Character.toChars(0x20000));
        update("FREE", mapper.createObjectNode().put("name", character.repeat(100)).put("description", character.repeat(2000)), 200);
        JsonNode valid = snapshot("FREE");
        for (ObjectNode invalid : List.of(mapper.createObjectNode().put("name", character.repeat(101)),
                mapper.createObjectNode().put("name", " "), mapper.createObjectNode().put("name", "bad\0name"),
                mapper.createObjectNode().put("name", "valid").put("description", character.repeat(2001)),
                mapper.createObjectNode().put("name", "valid").put("description", "bad\0description")))
            assertError(update("FREE", invalid, 422), "VALIDATION_FAILED");
        assertThat(snapshot("FREE")).isEqualTo(valid);
        assertThat(audits()).hasSize(1);
    }

    @ParameterizedTest @ValueSource(strings = {"0", "12.5", "9999999999.99"})
    void legalMoneyIsPersistedExactlyAndReturnedAsTwoDecimalString(String price) throws Exception {
        JsonNode result = update("PREMIUM", body().put("price", price), 200);
        assertThat(result.path("price").isTextual()).isTrue();
        assertThat(result.path("price").textValue()).isEqualTo(new java.math.BigDecimal(price).setScale(2).toPlainString());
        assertThat(result.path("price")).isEqualTo(snapshot("PREMIUM").path("price"));
        assertThat(jdbc.queryForObject("SELECT price FROM learning.plans WHERE code='PREMIUM'", java.math.BigDecimal.class))
                .isEqualByComparingTo(price);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void auditFailureAfterInsertRollsBackBothCreateAndUpdateAndProtectsTheErrorEnvelope(boolean create) throws Exception {
        if (create) removeUnreferencedPremium();
        List<Map<String, Object>> before = catalog();
        List<Map<String, Object>> userBefore = ownedUserState();
        AuditService target = AopTestUtils.getUltimateTargetObject(audit);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("synthetic-private-audit-failure");
        }).when(target).recordDetail(eq(admin), eq(create ? "PLAN_CREATED" : "PLAN_UPDATED"), eq("PLAN"), anyMap());
        JsonNode error = update("PREMIUM", body().put("name", "Must roll back"), 500);
        assertError(error, "INTERNAL_ERROR");
        assertThat(error.toString()).doesNotContain("synthetic-private-audit-failure", "IllegalStateException", "stackTrace");
        assertThat(catalog()).isEqualTo(before);
        assertThat(ownedUserState()).isEqualTo(userBefore);
        assertThat(ownedLedger()).isEmpty();
        assertThat(audits()).isEmpty();
    }

    @Test void realAuthenticationRolesAndCookieCsrfProtectCatalogAndAudit() throws Exception {
        List<Map<String, Object>> before = catalog();
        assertError(response(get(BASE), 401), "UNAUTHENTICATED");
        for (String role : List.of("LEARNER", "ACCOUNTANT", "TEACHER", "MODERATOR", "CONTENT_EDITOR")) {
            long id = user(role);
            assertError(response(get(BASE).header("Authorization", bearer(id, role)), 403), "FORBIDDEN");
            assertError(response(put(BASE + "/FREE").header("Authorization", bearer(id, role))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Denied\"}"), 403), "FORBIDDEN");
        }
        Cookie access = new Cookie("access_token", jwt.issueAccessToken(admin, List.of("ADMIN"), false, UUID.randomUUID()));
        assertError(response(put(BASE + "/FREE").cookie(access).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Denied CSRF\"}"), 403), "CSRF_TOKEN_INVALID");
        assertThat(catalog()).isEqualTo(before);
        assertThat(audits()).isEmpty();
        response(get(BASE).cookie(access), 200);
        touchedPlans.add("FREE");
        response(put(BASE + "/FREE").cookie(access, new Cookie("XSRF-TOKEN", "synthetic-plan-csrf"))
                .header("X-XSRF-TOKEN", "synthetic-plan-csrf").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cookie allowed\"}"), 200);
        assertThat(audits()).hasSize(1);
    }

    private long user(String role) {
        long id = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,'Synthetic plan user') RETURNING id",
                Long.class, "plan-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH);
        users.add(id);
        jdbc.update("INSERT INTO auth.user_roles(user_id,role_code) VALUES (?,?)", id, role);
        return id;
    }

    private long subscriber(Instant expires) {
        long id = user("LEARNER");
        jdbc.update("UPDATE auth.users SET plan_code='PREMIUM',plan_expires_at=? WHERE id=?", Timestamp.from(expires), id);
        return id;
    }

    private void removeUnreferencedPremium() {
        int deleted = jdbc.update("DELETE FROM learning.plans p WHERE p.code='PREMIUM' AND NOT EXISTS (SELECT 1 FROM auth.users u WHERE u.plan_code=p.code)");
        assertThat(deleted).as("missing-catalog fixture requires no unrelated PREMIUM FK dependants; never reassign unrelated users").isEqualTo(1);
    }

    private ObjectNode body() {
        ObjectNode body = mapper.createObjectNode().put("name", "Synthetic original").put("description", "Original description")
                .put("durationDays", 30).put("price", "12.5").put("isActive", true);
        body.set("benefits", mapper.createObjectNode().put("AI_GENERATE", 7));
        return body;
    }

    private ObjectNode bodyFrom(JsonNode snapshot) { return ((ObjectNode) snapshot).deepCopy(); }
    private String bearer(long id, String role) { return "Bearer " + jwt.issueAccessToken(id, List.of(role), false, UUID.randomUUID()); }
    private JsonNode getPlans() throws Exception { return response(get(BASE).header("Authorization", bearer(admin, "ADMIN")), 200); }
    private JsonNode update(String code, JsonNode body, int status) throws Exception { return rawUpdate(code, mapper.writeValueAsString(body), status); }
    private JsonNode rawUpdate(String code, String body, int status) throws Exception {
        if (code.equals("FREE") || code.equals("PREMIUM")) touchedPlans.add(code);
        return response(put(BASE + "/" + code).header("Authorization", bearer(admin, "ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body), status);
    }
    private JsonNode response(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        return mapper.readTree(mvc.perform(request).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsByteArray());
    }
    private List<Map<String, Object>> catalog() { return jdbc.queryForList("SELECT * FROM learning.plans ORDER BY code"); }
    private Map<String, Object> row(String code) { return jdbc.queryForMap("SELECT * FROM learning.plans WHERE code=?", code); }
    private List<Map<String, Object>> audits() { return jdbc.queryForList("SELECT action,target_type,target_id,detail::text AS detail FROM shared.audit_logs WHERE actor_id=? ORDER BY id", admin); }
    private List<Map<String, Object>> globalAudit() {
        return jdbc.queryForList("SELECT id,actor_id,action,target_type,target_id,detail::text AS detail,ip_address::text AS ip_address,created_at FROM shared.audit_logs ORDER BY id");
    }
    private void restorePlan(String code) {
        Map<String, Object> original = catalogBefore.stream().filter(row -> code.equals(row.get("code"))).findFirst().orElse(null);
        if (original == null) {
            if (code.equals("PREMIUM")) {
                jdbc.update("DELETE FROM learning.plans p WHERE p.code='PREMIUM' AND NOT EXISTS (SELECT 1 FROM auth.users u WHERE u.plan_code=p.code)");
            }
            return;
        }
        jdbc.update("""
                INSERT INTO learning.plans(code,name,description,duration_days,price,benefits,is_active,created_at)
                VALUES (?,?,?,?,?,CAST(? AS jsonb),?,?)
                ON CONFLICT(code) DO UPDATE SET name=EXCLUDED.name,description=EXCLUDED.description,
                    duration_days=EXCLUDED.duration_days,price=EXCLUDED.price,benefits=EXCLUDED.benefits,
                    is_active=EXCLUDED.is_active,created_at=EXCLUDED.created_at
                """, code, original.get("name"), original.get("description"), original.get("duration_days"), original.get("price"),
                original.get("benefits").toString(), original.get("is_active"), original.get("created_at"));
    }
    private void verifyCleanupAndRecord(TestInfo test, List<Long> ownedIds) throws Exception {
        List<Map<String, Object>> catalogAfter = catalog();
        List<Map<String, Object>> auditAfter = globalAudit();
        Map<String, Long> ownedRows = new LinkedHashMap<>();
        long userCount = 0, roleCount = 0, auditCount = 0, ledgerCount = 0;
        for (long id : ownedIds) {
            userCount += jdbc.queryForObject("SELECT count(*) FROM auth.users WHERE id=?", Long.class, id);
            roleCount += jdbc.queryForObject("SELECT count(*) FROM auth.user_roles WHERE user_id=?", Long.class, id);
            auditCount += jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs WHERE actor_id=?", Long.class, id);
            ledgerCount += jdbc.queryForObject("SELECT count(*) FROM learning.credit_transactions WHERE user_id=?", Long.class, id);
        }
        ownedRows.put("users", userCount); ownedRows.put("roles", roleCount);
        ownedRows.put("audit", auditCount); ownedRows.put("ledger", ledgerCount);
        Map<String, Object> database = jdbc.queryForMap("""
                SELECT current_database() AS database_name,
                    (SELECT oid::bigint FROM pg_database WHERE datname=current_database()) AS database_oid,
                    pg_postmaster_start_time()::text AS postmaster_started_at
                """);
        Map<String, Object> probe = jdbc.queryForMap("SELECT price,benefits,benefits::text AS benefits_text FROM learning.plans WHERE code='FREE'");
        Map<String, String> actualTypes = new LinkedHashMap<>();
        probe.forEach((key, value) -> actualTypes.put(key, value == null ? "null" : value.getClass().getName()));
        Map<String, String> expectedTypes = Map.of("price", BigDecimal.class.getName(), "benefits", "org.postgresql.util.PGobject",
                "benefits_text", String.class.getName());
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("observedUtc", Instant.now().toString());
        evidence.put("suite", getClass().getName());
        evidence.put("test", test.getDisplayName());
        evidence.put("pid", ProcessHandle.current().pid());
        evidence.put("postgresContainerId", POSTGRES.getContainerId());
        evidence.put("database", database);
        evidence.put("ownedIds", ownedIds);
        evidence.put("ownedRowsAfterCleanup", ownedRows);
        evidence.put("restoredPlanCodes", List.copyOf(touchedPlans));
        evidence.put("catalogEqual", catalogBefore != null && catalogBefore.equals(catalogAfter));
        evidence.put("globalAuditEqual", globalAuditBefore != null && globalAuditBefore.equals(auditAfter));
        evidence.put("catalogBeforeSha256", digest(catalogBefore));
        evidence.put("catalogAfterSha256", digest(catalogAfter));
        evidence.put("globalAuditBeforeSha256", digest(globalAuditBefore));
        evidence.put("globalAuditAfterSha256", digest(auditAfter));
        evidence.put("globalAuditBeforeRows", globalAuditBefore == null ? null : globalAuditBefore.size());
        evidence.put("globalAuditAfterRows", auditAfter.size());
        evidence.put("expectedJdbcValueTypes", expectedTypes);
        evidence.put("actualJdbcValueTypes", actualTypes);
        Path directory = Path.of(System.getProperty("java.io.tmpdir"), "plan-fixture-isolation-evidence");
        Files.createDirectories(directory);
        mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("cleanup-" + UUID.randomUUID() + ".json").toFile(), evidence);
        assertAll("Actual post-cleanup state",
                () -> assertThat(catalogBefore).isNotNull(),
                () -> assertThat(globalAuditBefore).isNotNull(),
                () -> assertThat(catalogAfter).isEqualTo(catalogBefore),
                () -> assertThat(auditAfter).isEqualTo(globalAuditBefore),
                () -> assertThat(ownedRows).containsExactlyInAnyOrderEntriesOf(Map.of("users", 0L, "roles", 0L, "audit", 0L, "ledger", 0L)),
                () -> assertThat(actualTypes).containsExactlyInAnyOrderEntriesOf(expectedTypes));
    }
    private String digest(Object snapshot) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(snapshot)));
    }
    private List<Map<String, Object>> ownedUserState() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (long id : users) rows.add(jdbc.queryForMap("SELECT id,plan_code,plan_expires_at,free_usage,usage_reset_at,updated_at FROM auth.users WHERE id=?", id));
        return rows;
    }
    private List<Map<String, Object>> ownedLedger() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (long id : users) rows.addAll(jdbc.queryForList("SELECT * FROM learning.credit_transactions WHERE user_id=? ORDER BY seq", id));
        return rows;
    }
    private JsonNode snapshot(String code) throws Exception {
        Map<String, Object> row = row(code);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", row.get("name")); values.put("description", row.get("description"));
        values.put("durationDays", row.get("duration_days"));
        values.put("price", ((java.math.BigDecimal) row.get("price")).setScale(2).toPlainString());
        values.put("benefits", mapper.readTree(row.get("benefits").toString())); values.put("isActive", row.get("is_active"));
        return mapper.valueToTree(values);
    }
    private void assertAudit(int index, String action, String code, JsonNode before, JsonNode after) throws Exception {
        Map<String, Object> row = audits().get(index);
        assertThat(row).containsEntry("action", action).containsEntry("target_type", "PLAN").containsEntry("target_id", null);
        JsonNode detail = mapper.readTree(row.get("detail").toString());
        assertThat(keys(detail)).isEqualTo(Set.of("code", "before", "after"));
        assertThat(detail.path("code").textValue()).isEqualTo(code);
        if (before == null) assertThat(detail.path("before").isNull()).isTrue();
        else { assertThat(keys(detail.path("before"))).isEqualTo(SNAPSHOT_KEYS); assertThat(detail.path("before")).isEqualTo(before); }
        assertThat(keys(detail.path("after"))).isEqualTo(SNAPSHOT_KEYS);
        assertThat(detail.path("after")).isEqualTo(after);
    }
    private static void assertError(JsonNode error, String code) {
        assertThat(keys(error)).isEqualTo(Set.of("error_code", "message", "request_id"));
        assertThat(error.path("error_code").textValue()).isEqualTo(code);
        assertThat(error.path("message").isTextual()).isTrue();
        assertThat(error.path("request_id").textValue()).isNotBlank();
    }
    private static void assertView(JsonNode view) {
        assertThat(keys(view)).isEqualTo(VIEW_KEYS);
        assertThat(view.path("exists").isBoolean()).isTrue();
        assertThat(view.path("activeUsers").isIntegralNumber()).isTrue();
        assertThat(view.path("activeUsers").longValue()).isNotNegative();
        assertThat(view.path("benefits").isObject()).isTrue();
        if (!view.path("price").isNull()) {
            assertThat(view.path("price").isTextual()).isTrue();
            assertThat(view.path("price").textValue()).matches("[0-9]{1,10}\\.[0-9]{2}");
        }
    }
    private static Set<String> keys(JsonNode node) { Set<String> result = new LinkedHashSet<>(); node.fieldNames().forEachRemaining(result::add); return result; }

    public static final class IsolationOrderer implements ClassOrderer {
        private static final List<String> PRIORITY = List.of(
                "com.cnhsk.auth.AuthPlanApiIntegrationTest",
                "com.cnhsk.contract.CreditPlanContractTest",
                "com.cnhsk.learning.credit.PlanUpdateRequestTest",
                "com.cnhsk.learning.credit.PlanAdminServiceTest",
                "com.cnhsk.shared.exception.GlobalExceptionHandlerTest",
                "com.cnhsk.shared.config.PlanAdminSecurityConfigIntegrationTest",
                "com.cnhsk.learning.credit.PlanAdminConcurrencyIntegrationTest",
                "com.cnhsk.learning.credit.PlanAdminIntegrationTest",
                "com.cnhsk.contract.CreditRedeemContractTest",
                "com.cnhsk.shared.config.CreditRedeemSecurityConfigIntegrationTest",
                "com.cnhsk.learning.credit.RedeemServiceTest",
                "com.cnhsk.learning.credit.RedeemRateLimitServiceTest",
                "com.cnhsk.learning.credit.RedeemRateLimitIntegrationTest",
                "com.cnhsk.learning.credit.RedeemConcurrencyIntegrationTest",
                "com.cnhsk.learning.credit.RedeemIntegrationTest",
                "com.cnhsk.learning.credit.WalletQueryServiceTest",
                "com.cnhsk.learning.credit.CreditTransactionHistoryIntegrationTest",
                "com.cnhsk.shared.config.WalletQuerySecurityConfigIntegrationTest",
                "com.cnhsk.contract.CreditWalletContractTest",
                "com.cnhsk.learning.credit.WalletQueryIntegrationTest",
                "com.cnhsk.learning.api.EntitlementApiCompatibilityTest",
                "com.cnhsk.learning.credit.EntitlementServiceTest",
                "com.cnhsk.learning.credit.EntitlementRedisIntegrationTest",
                "com.cnhsk.learning.credit.EntitlementConcurrencyIntegrationTest",
                "com.cnhsk.learning.credit.EntitlementServiceIntegrationTest");

        @Override public void orderClasses(ClassOrdererContext context) {
            context.getClassDescriptors().sort(Comparator.comparingInt((ClassDescriptor descriptor) -> {
                int priority = PRIORITY.indexOf(descriptor.getTestClass().getName());
                return priority < 0 ? Integer.MAX_VALUE : priority;
            }).thenComparing(descriptor -> descriptor.getTestClass().getName()));
            System.out.println("CNHSK_ISOLATION_PLANNED_ORDER pid=" + ProcessHandle.current().pid() + " plannedClasses="
                    + context.getClassDescriptors().stream().map(descriptor -> descriptor.getTestClass().getName()).toList());
        }
    }
}
