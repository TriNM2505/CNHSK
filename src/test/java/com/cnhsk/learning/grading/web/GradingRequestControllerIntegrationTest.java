package com.cnhsk.learning.grading.web;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.cnhsk.learning.api.EntitlementApi;
import com.cnhsk.learning.grading.GradingRepository;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Import(GradingRequestControllerIntegrationTest.TimeConfiguration.class)
class GradingRequestControllerIntegrationTest extends AbstractIntegrationTest {
    private static final Instant NOW = Instant.parse("2035-11-14T10:00:00Z");
    private static final String HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private StringRedisTemplate redis;
    @Autowired private PlatformTransactionManager transactions;
    @MockitoSpyBean private GradingRepository repository;
    @MockitoSpyBean private EntitlementApi entitlement;
    private GradingRepository repositoryTarget;
    private EntitlementApi entitlementTarget;
    private final List<Long> users = new ArrayList<>();
    private long user;

    @BeforeEach void setup() {
        repositoryTarget = AopTestUtils.getUltimateTargetObject(repository);
        entitlementTarget = AopTestUtils.getUltimateTargetObject(entitlement);
        user = user();
    }

    @AfterEach void cleanup() {
        reset(repositoryTarget, entitlementTarget);
        for (long id : users) {
            var keys = redis.keys("billing:*:" + id + ":*");
            if (keys != null && !keys.isEmpty()) redis.delete(keys);
            jdbc.update("DELETE FROM learning.service_requests WHERE user_id=?", id);
            jdbc.update("DELETE FROM learning.credit_transactions WHERE user_id=?", id);
            jdbc.update("DELETE FROM auth.users WHERE id=?", id);
        }
    }

    @Test void FR001_policyAndInitialEmptyListMatchTheContract() throws Exception {
        mvc.perform(get("/api/grading/policy").header("Authorization", bearer(user, "LEARNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.claimHours").value(48))
                .andExpect(jsonPath("$.gradingHours").value(24)).andExpect(jsonPath("$.minHanzi").value(50))
                .andExpect(jsonPath("$.maxHanzi").value(500)).andExpect(jsonPath("$.maxChars").value(3000))
                .andExpect(jsonPath("$.cost").value(1)).andExpect(jsonPath("$.refundTerms").isNotEmpty());
        mvc.perform(get("/api/grading-requests").header("Authorization", bearer(user, "LEARNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"49", "501", "3001"})
    void AT01_invalidEssayDoesNotCreateOrCharge(String length) throws Exception {
        String essay = length.equals("3001") ? "字".repeat(50) + "😀".repeat(2951) : "字".repeat(Integer.parseInt(length));
        mvc.perform(post("/api/grading-requests").header("Authorization", bearer(user, "LEARNER"))
                .contentType(MediaType.APPLICATION_JSON).content(body(essay)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("GRADING_INVALID_ESSAY"))
                .andExpect(jsonPath("$.details.hanziCount").value(length.equals("3001") ? 50 : Integer.parseInt(length)));
        assertThat(requests()).isZero(); assertThat(used()).isZero(); assertThat(debits()).isZero();
        verify(entitlementTarget, never()).consume(anyLong(), any(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{}", "{\"essay\":4}", "{\"essay\":\"字\",\"hskLevel\":10}", "{\"essay\":\"字\",\"prompt\":false}"})
    void Q_T02_02_badBodyUsesValidationFailedBeforeBilling(String body) throws Exception {
        mvc.perform(post("/api/grading-requests").header("Authorization", bearer(user, "LEARNER"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        assertThat(requests()).isZero(); assertThat(used()).isZero();
    }

    @Test void FR002_exactCodePointBoundaryAndNewlinesArePreserved() throws Exception {
        String essay = "字".repeat(50) + "\n" + "😀".repeat(2949);
        JsonNode result = submit("\u3000" + essay + "\u00a0", 201);
        assertThat(result.path("essay").textValue()).isEqualTo(essay);
        assertThat(result.path("essay").textValue().codePointCount(0, essay.length())).isEqualTo(3000);
        assertThat(result.path("reference").textValue()).isEqualTo(String.format("GR-%06d", result.path("id").longValue()));
        assertThat(result.path("createdAt").textValue()).isEqualTo(NOW.toString());
        assertThat(result.path("deadline").textValue()).isEqualTo(NOW.plusSeconds(48 * 3600).toString());
        assertThat(result.has("prompt")).isTrue(); assertThat(result.get("prompt").isNull()).isTrue();
        assertThat(result.has("result")).isTrue(); assertThat(result.get("result").isNull()).isTrue();
        assertThat(result.path("refunded").booleanValue()).isFalse(); noPrivateFields(result);
    }

    @Test void AT02_normalizedDuplicateSubmissionsRaceAndOnlyOneQuotaIsConsumed() throws Exception {
        String first = body("字".repeat(50) + "\n A");
        String second = body("\u3000" + "字".repeat(50) + " \tA \u00a0");
        List<Integer> statuses = race(List.of(() -> postStatus(first), () -> postStatus(second)));
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(requests()).isEqualTo(1); assertThat(used()).isEqualTo(1); assertThat(debits()).isZero();
        assertThat(redis.keys("billing:idem:" + user + ":*")).hasSize(1);
        submit("字".repeat(50) + " A", 409);
        assertThat(used()).isEqualTo(1);
    }

    @Test void AT03_exhaustedQuotaAndZeroCreditsReturn402WithoutARequest() throws Exception {
        usage(user, 10);
        JsonNode error = submit("字".repeat(50), 402);
        assertThat(error.path("error_code").textValue()).isEqualTo("INSUFFICIENT_CREDITS");
        assertThat(requests()).isZero(); assertThat(used()).isEqualTo(10); assertThat(debits()).isZero();
        assertThat(redis.keys("billing:idem:" + user + ":*")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"QUOTA", "CREDIT"})
    void AT03_insertFailureRollsBackBillingAndPublishesNoRedisReceipt(String source) throws Exception {
        boolean credit = source.equals("CREDIT");
        if (credit) { usage(user, 10); topup(); }
        doThrow(new DataIntegrityViolationException("fixture insert failure")).when(repositoryTarget)
                .createPending(eq(user), any(), any(), any());
        submit("字".repeat(50), 500);
        assertThat(requests()).isZero(); assertThat(used()).isEqualTo(credit ? 10 : 0);
        assertThat(debits()).isZero(); assertThat(balance()).isEqualTo(credit ? 1 : 0);
        assertThat(redis.keys("billing:idem:" + user + ":*")).isEmpty();
        verify(entitlementTarget).consume(eq(user), eq(com.cnhsk.learning.api.PaidFeature.GRADING), startsWith("grading:"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"QUOTA", "CREDIT"})
    void AT03_AT05_realConsumptionAndCancellationRefundExactlyOnce(String source) throws Exception {
        boolean credit = source.equals("CREDIT");
        if (credit) { usage(user, 10); topup(); }
        JsonNode created = submit("字".repeat(50), 201);
        long id = created.path("id").longValue();
        assertThat(jdbc.queryForObject("SELECT payload->'consumption'->>'source' FROM learning.service_requests WHERE id=?", String.class, id)).isEqualTo(source);
        assertThat(used()).isEqualTo(credit ? 10 : 1); assertThat(debits()).isEqualTo(credit ? 1 : 0);
        assertThat(balance()).isZero();
        JsonNode cancelled = json(mvc.perform(post("/api/grading-requests/{id}/cancel", id)
                .header("Authorization", bearer(user, "LEARNER"))).andExpect(status().isOk()).andReturn());
        assertThat(cancelled.path("status").textValue()).isEqualTo("FAILED");
        assertThat(cancelled.path("refunded").booleanValue()).isTrue(); noPrivateFields(cancelled);
        mvc.perform(post("/api/grading-requests/{id}/cancel", id).header("Authorization", bearer(user, "LEARNER")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error_code").value("GRADING_NOT_CANCELLABLE"));
        assertThat(used()).isEqualTo(credit ? 10 : 0); assertThat(balance()).isEqualTo(credit ? 1 : 0);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.credit_transactions WHERE user_id=? AND tx_type='REFUND'", Long.class, user)).isEqualTo(credit ? 1L : 0L);
        verify(entitlementTarget, times(1)).refund(eq(user), any());
    }

    @Test void AT05_refundExceptionRollsBackCancellationAndAllowsRetry() throws Exception {
        long id = submit("字".repeat(50), 201).path("id").longValue();
        doThrow(new IllegalStateException("fixture refund failure")).when(entitlementTarget).refund(eq(user), any());
        mvc.perform(post("/api/grading-requests/{id}/cancel", id).header("Authorization", bearer(user, "LEARNER")))
                .andExpect(status().isInternalServerError());
        var row = repository.find(id).orElseThrow();
        assertThat(row.status()).isEqualTo("PENDING"); assertThat(row.completedAt()).isNull(); assertThat(row.errorMessage()).isNull();
        assertThat(used()).isEqualTo(1);
        doCallRealMethod().when(entitlementTarget).refund(eq(user), any());
        mvc.perform(post("/api/grading-requests/{id}/cancel", id).header("Authorization", bearer(user, "LEARNER")))
                .andExpect(status().isOk());
        assertThat(used()).isZero();
    }

    @Test void AT05_twoCreditCancellationsProduceOnlyOneRefund() throws Exception {
        usage(user, 10); topup();
        long id = submit("字".repeat(50), 201).path("id").longValue();
        Callable<Integer> cancel = () -> mvc.perform(post("/api/grading-requests/{id}/cancel", id)
                .header("Authorization", bearer(user, "LEARNER"))).andReturn().getResponse().getStatus();
        assertThat(race(List.of(cancel, cancel))).containsExactlyInAnyOrder(200, 409);
        assertThat(balance()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.credit_transactions WHERE user_id=? AND tx_type='REFUND'", Long.class, user)).isEqualTo(1);
        verify(entitlementTarget, times(1)).refund(eq(user), any());
    }

    @Test void AT04_wrongRequestKindIsForbiddenEvenForItsOwner() throws Exception {
        long id = jdbc.queryForObject("INSERT INTO learning.service_requests(user_id,kind,status,payload) VALUES (?,'IMPORT','PENDING','{}') RETURNING id", Long.class, user);
        mvc.perform(get("/api/grading-requests/{id}", id).header("Authorization", bearer(user, "LEARNER")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("GRADING_REQUEST_NOT_OWNED"));
        mvc.perform(post("/api/grading-requests/{id}/cancel", id).header("Authorization", bearer(user, "LEARNER")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("GRADING_REQUEST_NOT_OWNED"));
        mvc.perform(get("/api/grading-requests").header("Authorization", bearer(user, "LEARNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        verifyNoInteractions(entitlementTarget);
    }

    @Test void AT05_cancelAndTeacherClaimRaceWithOnlyOneWinner() throws Exception {
        long teacher = user();
        long id = submit("字".repeat(50), 201).path("id").longValue();
        List<Integer> results = race(List.of(
                () -> mvc.perform(post("/api/grading-requests/{id}/cancel", id).header("Authorization", bearer(user, "LEARNER"))).andReturn().getResponse().getStatus(),
                () -> new TransactionTemplate(transactions).execute(status -> repository.claim(id, teacher, NOW, NOW.plusSeconds(24 * 3600))) ? 1 : 0));
        if (results.get(0) == 200) {
            assertThat(results.get(1)).isZero(); assertThat(repository.find(id).orElseThrow().status()).isEqualTo("FAILED"); assertThat(used()).isZero();
        } else {
            assertThat(results).containsExactly(409, 1); assertThat(repository.find(id).orElseThrow().status()).isEqualTo("ASSIGNED"); assertThat(used()).isEqualTo(1);
        }
    }

    @Test void AT04_ownershipStatusResultAndPagingDoNotExposePrivateFields() throws Exception {
        long first = submit("字".repeat(50), 201).path("id").longValue();
        long second = submit("文".repeat(50), 201).path("id").longValue();
        long outsider = user();
        mvc.perform(get("/api/grading-requests/{id}", first).header("Authorization", bearer(outsider, "LEARNER")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("GRADING_REQUEST_NOT_OWNED"));
        mvc.perform(post("/api/grading-requests/{id}/cancel", first).header("Authorization", bearer(outsider, "LEARNER")))
                .andExpect(status().isForbidden());
        var result = mapper.createObjectNode();
        result.putObject("criteria").put("grammar", 20).put("vocabulary", 20).put("coherence", 20).put("writing", 20);
        result.putArray("corrections").addObject().put("original", "昨天我去书店了。").put("corrected", "昨天我去了书店。").putNull("note");
        result.put("payout", 999);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThat(repository.claim(first, outsider, NOW, NOW.plusSeconds(3600))).isTrue();
            assertThat(repository.complete(first, outsider, NOW, 80, "Nhận xét tự soạn", result, 0)).isTrue();
        });
        JsonNode detail = json(mvc.perform(get("/api/grading-requests/{id}", first).header("Authorization", bearer(user, "LEARNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.score").value(80))
                .andExpect(jsonPath("$.result.criteria.grammar").value(20)).andExpect(jsonPath("$.result.feedback").value("Nhận xét tự soạn"))
                .andExpect(jsonPath("$.result.corrections[0].note").isEmpty()).andReturn());
        noPrivateFields(detail); assertThat(detail.path("refunded").booleanValue()).isFalse();
        JsonNode page = json(mvc.perform(get("/api/grading-requests?size=1&page=0").header("Authorization", bearer(user, "LEARNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].id").value(second)).andReturn());
        assertThat(page.at("/items/0/score").isNull()).isTrue(); noPrivateFields(page);
        mvc.perform(get("/api/grading-requests?status=COMPLETED&size=1").header("Authorization", bearer(user, "LEARNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].score").value(80));
        mvc.perform(get("/api/grading-requests").header("Authorization", bearer(outsider, "LEARNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "9223372036854775808", "9223372036854775807"})
    void Q_T02_01_badAndMissingIdsAre403ForBothEndpoints(String id) throws Exception {
        for (var request : List.of(get("/api/grading-requests/" + id), post("/api/grading-requests/" + id + "/cancel"))) {
            mvc.perform(request.header("Authorization", bearer(user, "LEARNER"))).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("GRADING_REQUEST_NOT_OWNED"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "page=abc", "page=", "size=0", "size=51", "size=abc", "status=UNKNOWN", "status="})
    void FR004_invalidFiltersReturn422(String query) throws Exception {
        mvc.perform(get("/api/grading-requests?" + query).header("Authorization", bearer(user, "LEARNER")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
    }

    @Test void AT17_anonymousAndCookieCsrfFollowExistingSecurityPolicy() throws Exception {
        mvc.perform(get("/api/grading/policy")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/grading-requests").contentType(MediaType.APPLICATION_JSON).content(body("字".repeat(50))))
                .andExpect(status().isUnauthorized());
        Cookie access = new Cookie("access_token", token(user, "LEARNER"));
        mvc.perform(post("/api/grading-requests").cookie(access).contentType(MediaType.APPLICATION_JSON).content(body("字".repeat(50))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        mvc.perform(post("/api/grading-requests").cookie(access, new Cookie("XSRF-TOKEN", "grading-csrf"))
                .header("X-XSRF-TOKEN", "wrong").contentType(MediaType.APPLICATION_JSON).content(body("字".repeat(50))))
                .andExpect(status().isForbidden());
        assertThat(requests()).isZero(); assertThat(used()).isZero();
        mvc.perform(post("/api/grading-requests").cookie(access, new Cookie("XSRF-TOKEN", "grading-csrf"))
                .header("X-XSRF-TOKEN", "grading-csrf").contentType(MediaType.APPLICATION_JSON).content(body("字".repeat(50))))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/teacher/grading-queue").header("Authorization", bearer(user, "LEARNER"))).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TEACHER", "ADMIN", "CONTENT_EDITOR", "MODERATOR", "ACCOUNTANT"})
    void AT17_allFiveEndpointsRequireLearner(String role) throws Exception {
        for (var request : List.of(get("/api/grading/policy"), get("/api/grading-requests"), get("/api/grading-requests/1"),
                post("/api/grading-requests").contentType(MediaType.APPLICATION_JSON).content(body("字".repeat(50))),
                post("/api/grading-requests/1/cancel"))) {
            mvc.perform(request.header("Authorization", bearer(user, role))).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        }
        assertThat(requests()).isZero(); assertThat(used()).isZero();
    }

    private long user() {
        long id = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,'Grading learner fixture') RETURNING id",
                Long.class, UUID.randomUUID() + "@example.test", HASH);
        users.add(id); usage(id, 0); return id;
    }
    private void usage(long id, int used) {
        Instant month = NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        jdbc.update("UPDATE auth.users SET free_usage=CAST(? AS jsonb),usage_reset_at=? WHERE id=?", "{\"GRADING\":" + used + "}", Timestamp.from(month), id);
    }
    private int used() { return jdbc.queryForObject("SELECT COALESCE((free_usage->>'GRADING')::int,0) FROM auth.users WHERE id=?", Integer.class, user); }
    private long requests() { return jdbc.queryForObject("SELECT count(*) FROM learning.service_requests WHERE user_id=? AND kind='GRADING'", Long.class, user); }
    private long debits() { return jdbc.queryForObject("SELECT count(*) FROM learning.credit_transactions WHERE user_id=? AND tx_type='DEDUCT'", Long.class, user); }
    private int balance() { return jdbc.queryForList("SELECT balance_after FROM learning.credit_transactions WHERE user_id=? ORDER BY seq DESC LIMIT 1", Integer.class, user).stream().findFirst().orElse(0); }
    private void topup() { jdbc.update("INSERT INTO learning.credit_transactions(user_id,seq,tx_type,amount,balance_before,balance_after) VALUES (?,1,'TOPUP',1,0,1)", user); }
    private String token(long id, String role) { return jwt.issueAccessToken(id, List.of(role), false, UUID.randomUUID()); }
    private String bearer(long id, String role) { return "Bearer " + token(id, role); }
    private String body(String essay) throws Exception { return mapper.writeValueAsString(Map.of("essay", essay)); }
    private int postStatus(String body) throws Exception { return mvc.perform(post("/api/grading-requests").header("Authorization", bearer(user, "LEARNER")).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus(); }
    private JsonNode submit(String essay, int expected) throws Exception {
        return json(mvc.perform(post("/api/grading-requests").header("Authorization", bearer(user, "LEARNER"))
                .contentType(MediaType.APPLICATION_JSON).content(body(essay))).andExpect(status().is(expected)).andReturn());
    }
    private JsonNode json(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)); }
    private void noPrivateFields(JsonNode json) {
        for (String field : List.of("payout", "consumption", "assigneeId", "assignee_id", "assignedAt", "userId", "essayHash", "payload")) {
            assertThat(json.findValues(field)).as(field).isEmpty();
        }
    }
    private List<Integer> race(List<Callable<Integer>> calls) throws Exception {
        var executor = Executors.newFixedThreadPool(calls.size());
        var ready = new CountDownLatch(calls.size()); var go = new CountDownLatch(1);
        try {
            var futures = calls.stream().map(call -> executor.submit(() -> { ready.countDown(); if (!go.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Fixture timeout"); return call.call(); })).toList();
            assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue(); go.countDown();
            var results = new ArrayList<Integer>(); for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS)); return results;
        } finally { go.countDown(); executor.shutdownNow(); executor.awaitTermination(10, TimeUnit.SECONDS); }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration { @Bean @Primary Clock gradingClock() { return Clock.fixed(NOW, ZoneOffset.UTC); } }
}
