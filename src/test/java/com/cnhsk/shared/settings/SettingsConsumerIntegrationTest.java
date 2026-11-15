package com.cnhsk.shared.settings;

import static com.cnhsk.shared.settings.SettingKey.AI_MONTHLY_TOKEN_BUDGET;
import static com.cnhsk.shared.settings.SettingKey.ASSISTANT_DAILY_LIMIT;
import static com.cnhsk.shared.settings.SettingKey.BILLING_FREE_MONTHLY_LIMIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cnhsk.learning.ai.AiUnavailableException;
import com.cnhsk.learning.ai.ClaudeClient;
import com.cnhsk.learning.ai.StubClaudeClient;
import com.cnhsk.learning.ai.TokenBudget;
import com.cnhsk.learning.api.Consumption;
import com.cnhsk.learning.api.PaidFeature;
import com.cnhsk.learning.assistant.AssistantDailyLimit;
import com.cnhsk.learning.credit.EntitlementService;
import com.cnhsk.learning.credit.LedgerService;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@TestPropertySource(properties = "cnhsk.assistant.help-location=classpath*:assistant-help/valid/*.md")
class SettingsConsumerIntegrationTest extends AbstractIntegrationTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private static final List<SettingKey> KEYS = List.of(BILLING_FREE_MONTHLY_LIMIT, ASSISTANT_DAILY_LIMIT, AI_MONTHLY_TOKEN_BUDGET);
    private static final String KEY_FILTER = "key IN ('billing.free-monthly-limit','assistant.daily-limit','ai.monthly-token-budget')";
    private static final ClaudeClient.Completion COMPLETION = new ClaudeClient.Completion("Nội dung tự soạn", 2, 1);

    @Autowired private SettingsApi api;
    @Autowired private SettingsService settings;
    @Autowired private EntitlementService entitlement;
    @Autowired private AssistantDailyLimit daily;
    @Autowired private TokenBudget budget;
    @Autowired private StubClaudeClient stub;
    @Autowired private LedgerService ledger;
    @Autowired private ApplicationContext context;
    @Autowired private Clock clock;
    @Autowired private Environment environment;
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private PlatformTransactionManager manager;
    private List<Map<String, Object>> previousSettings;
    private RedisSnapshot previousTokens;
    private String tokenKey;
    private String dailyKey;
    private long actor;
    private long learner;

    @BeforeEach
    void setup() {
        previousSettings = jdbc.queryForList("SELECT * FROM shared.app_settings WHERE " + KEY_FILTER);
        jdbc.update("DELETE FROM shared.app_settings WHERE " + KEY_FILTER);
        KEYS.forEach(settings::invalidate);
        actor = user("Quản trị thử nghiệm");
        learner = user("Người học thử nghiệm");
        var now = clock.instant().atZone(ZONE);
        tokenKey = "ai:tokens:" + now.format(DateTimeFormatter.ofPattern("uuuuMM"));
        dailyKey = "assistant:ask:" + learner + ":" + now.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE);
        previousTokens = new RedisSnapshot(redis.opsForValue().get(tokenKey), redis.getExpire(tokenKey, TimeUnit.MILLISECONDS), System.nanoTime());
        redis.delete(tokenKey);
    }

    @AfterEach
    void cleanup() {
        var billingKeys = redis.keys("billing:*:" + learner + ":*");
        if (billingKeys != null && !billingKeys.isEmpty()) redis.delete(billingKeys);
        var dailyKeys = redis.keys("assistant:ask:" + learner + ":*");
        if (dailyKeys != null && !dailyKeys.isEmpty()) redis.delete(dailyKeys);
        previousTokens.restore(redis, tokenKey);
        jdbc.update("DELETE FROM shared.app_settings WHERE " + KEY_FILTER);
        for (var row : previousSettings) {
            jdbc.update("INSERT INTO shared.app_settings(key,value,updated_by,updated_at) VALUES (?,?,?,?)",
                    row.get("key"), row.get("value"), row.get("updated_by"), row.get("updated_at"));
        }
        KEYS.forEach(settings::invalidate);
        jdbc.update("DELETE FROM shared.audit_logs WHERE actor_id IN (?,?)", actor, learner);
        jdbc.update("DELETE FROM learning.credit_transactions WHERE user_id IN (?,?)", actor, learner);
        jdbc.update("DELETE FROM auth.users WHERE id IN (?,?)", actor, learner);
    }

    @Test
    void defaultsComeFromYamlAndAreUsedByTheExistingConsumerBeans() throws Exception {
        for (SettingKey key : KEYS) assertThat(api.getInt(key)).isEqualTo(defaultValue(key));
        JsonNode wallet = wallet();
        assertThat(wallet.path("quota").size()).isEqualTo(PaidFeature.values().length);
        for (JsonNode quota : wallet.path("quota")) {
            assertThat(quota.path("limit").asInt()).isEqualTo(defaultValue(BILLING_FREE_MONTHLY_LIMIT));
            assertThat(quota.path("used").asInt()).isZero();
        }
        try (var reservation = daily.reserve(learner)) { reservation.commit(); }
        assertThat(redis.opsForValue().get(dailyKey)).isEqualTo("1");
        assertThat(budget.execute(() -> COMPLETION)).isSameAs(COMPLETION);
        assertThat(redis.opsForValue().get(tokenKey)).isEqualTo("3");
        assertThat(context.getBean(EntitlementService.class)).isSameAs(entitlement);
        assertThat(context.getBean(AssistantDailyLimit.class)).isSameAs(daily);
        assertThat(context.getBean(TokenBudget.class)).isSameAs(budget);
    }

    @Test
    void AT06_adminChangeToOneBlocksSecondQuestionAndIncreaseAndResetKeepTheCounter() throws Exception {
        int calls = stub.callCount();
        change(ASSISTANT_DAILY_LIMIT, 1);
        ask(200);
        ask(429);
        assertThat(stub.callCount()).isEqualTo(calls + 1);
        assertThat(redis.opsForValue().get(dailyKey)).isEqualTo("1");
        change(ASSISTANT_DAILY_LIMIT, 2);
        ask(200);
        assertThat(redis.opsForValue().get(dailyKey)).isEqualTo("2");
        reset(ASSISTANT_DAILY_LIMIT);
        assertThat(api.getInt(ASSISTANT_DAILY_LIMIT)).isEqualTo(defaultValue(ASSISTANT_DAILY_LIMIT));
        ask(200);
        assertThat(stub.callCount()).isEqualTo(calls + 3);
        assertThat(redis.opsForValue().get(dailyKey)).isEqualTo("3");
        assertThat(redis.getExpire(dailyKey, TimeUnit.SECONDS)).isPositive();
    }

    @Test
    void zeroDailyLimitBlocksTheFirstQuestionAndIncreasingItDoesNotRequireRestart() throws Exception {
        int calls = stub.callCount();
        change(ASSISTANT_DAILY_LIMIT, 0);
        ask(429);
        assertThat(stub.callCount()).isEqualTo(calls);
        assertThat(redis.hasKey(dailyKey)).isFalse();
        change(ASSISTANT_DAILY_LIMIT, 1);
        ask(200);
        assertThat(redis.opsForValue().get(dailyKey)).isEqualTo("1");
    }

    @Test
    void freeQuotaWalletCreditFallbackIdempotencyAndRefundUseTheChangedLimitWithoutResettingUsage() throws Exception {
        change(BILLING_FREE_MONTHLY_LIMIT, 2);
        Consumption first = consume("first");
        assertThat(first.source()).isEqualTo(Consumption.Source.QUOTA);
        assertQuota(2, 1, 1);
        change(BILLING_FREE_MONTHLY_LIMIT, 0);
        assertThat(consume("first")).isEqualTo(first);
        assertQuota(0, 1, 0);
        tx().executeWithoutResult(status -> ledger.append(new LedgerService.AppendCommand(
                learner, "TOPUP", 1, null, null, null, null, null, null, null)));
        assertThat(consume("credit").source()).isEqualTo(Consumption.Source.CREDIT);
        assertThat(ledger.balance(learner)).isZero();
        assertQuota(0, 1, 0);
        change(BILLING_FREE_MONTHLY_LIMIT, 3);
        assertThat(consume("raised").source()).isEqualTo(Consumption.Source.QUOTA);
        assertQuota(3, 2, 1);
        tx().executeWithoutResult(status -> entitlement.refund(learner, first));
        assertQuota(3, 1, 2);
        reset(BILLING_FREE_MONTHLY_LIMIT);
        int restored = defaultValue(BILLING_FREE_MONTHLY_LIMIT);
        assertQuota(restored, 1, restored - 1);
    }

    @Test
    void zeroTokenBudgetWithMissingKeyBlocksBeforeSupplierAndPositiveBudgetAllowsFirstUse() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        change(AI_MONTHLY_TOKEN_BUDGET, 0);
        assertThatThrownBy(() -> budget.execute(() -> { calls.incrementAndGet(); return COMPLETION; }))
                .isInstanceOfSatisfying(AiUnavailableException.class,
                        error -> assertThat(error.getReason()).isEqualTo(AiUnavailableException.Reason.BUDGET_EXCEEDED));
        assertThat(calls).hasValue(0);
        assertThat(redis.hasKey(tokenKey)).isFalse();
        change(AI_MONTHLY_TOKEN_BUDGET, 10);
        budget.execute(() -> { calls.incrementAndGet(); return COMPLETION; });
        assertThat(calls).hasValue(1);
        assertThat(redis.opsForValue().get(tokenKey)).isEqualTo("3");
        assertThat(redis.getExpire(tokenKey, TimeUnit.SECONDS)).isPositive();
    }

    @Test
    void loweringRaisingAndResettingTheMonthlyBudgetNeverClearTheChargedCounter() throws Exception {
        change(AI_MONTHLY_TOKEN_BUDGET, 10);
        budget.execute(() -> COMPLETION);
        assertThat(redis.opsForValue().get(tokenKey)).isEqualTo("3");
        change(AI_MONTHLY_TOKEN_BUDGET, 3);
        AtomicInteger deniedCalls = new AtomicInteger();
        assertThatThrownBy(() -> budget.execute(() -> { deniedCalls.incrementAndGet(); return COMPLETION; }))
                .isInstanceOf(AiUnavailableException.class);
        assertThat(deniedCalls).hasValue(0);
        assertThat(redis.opsForValue().get(tokenKey)).isEqualTo("3");
        change(AI_MONTHLY_TOKEN_BUDGET, 20);
        budget.execute(() -> COMPLETION);
        assertThat(redis.opsForValue().get(tokenKey)).isEqualTo("6");
        reset(AI_MONTHLY_TOKEN_BUDGET);
        assertThat(api.getInt(AI_MONTHLY_TOKEN_BUDGET)).isEqualTo(defaultValue(AI_MONTHLY_TOKEN_BUDGET));
        budget.execute(() -> COMPLETION);
        assertThat(redis.opsForValue().get(tokenKey)).isEqualTo("9");
    }

    private long user(String name) {
        return jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,?) RETURNING id",
                Long.class, "consumer-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH, name);
    }

    private String bearer(long id, String role) {
        return "Bearer " + jwt.issueAccessToken(id, List.of(role), true, UUID.randomUUID());
    }

    private void change(SettingKey key, int value) throws Exception {
        mvc.perform(put("/api/admin/settings/{key}", key.key()).header("Authorization", bearer(actor, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("value", value, "reason", "Kiểm thử consumer"))))
                .andExpect(status().isOk());
    }

    private void reset(SettingKey key) throws Exception {
        mvc.perform(delete("/api/admin/settings/{key}", key.key()).header("Authorization", bearer(actor, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Khôi phục mặc định\"}"))
                .andExpect(status().isOk());
    }

    private void ask(int expected) throws Exception {
        if (expected == 200) stub.enqueue(new ClaudeClient.Completion("Hướng dẫn tự soạn", 1, 1));
        mvc.perform(post("/api/assistant/ask").header("Authorization", bearer(learner, "LEARNER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Cách học từ mới?\"}"))
                .andExpect(status().is(expected));
    }

    private JsonNode wallet() throws Exception {
        return mapper.readTree(mvc.perform(get("/api/me/credits").header("Authorization", bearer(learner, "LEARNER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }

    private void assertQuota(int limit, int used, int remaining) throws Exception {
        JsonNode line = null;
        for (JsonNode quota : wallet().path("quota")) {
            if (quota.path("feature").asText().equals(PaidFeature.TRANSLATE.name())) line = quota;
        }
        assertThat(line).isNotNull();
        assertThat(line.path("limit").asInt()).isEqualTo(limit);
        assertThat(line.path("used").asInt()).isEqualTo(used);
        assertThat(line.path("remaining").asInt()).isEqualTo(remaining);
    }

    private Consumption consume(String suffix) {
        return tx().execute(status -> entitlement.consume(learner, PaidFeature.TRANSLATE, "consumer-" + suffix));
    }

    private TransactionTemplate tx() { return new TransactionTemplate(manager); }
    private int defaultValue(SettingKey key) { return Integer.parseInt(environment.getRequiredProperty(key.defaultProperty())); }

    private record RedisSnapshot(String value, Long ttlMillis, long sampledAt) {
        void restore(StringRedisTemplate redis, String key) {
            if (value == null) redis.delete(key);
            else if (ttlMillis != null && ttlMillis >= 0) {
                long remaining = ttlMillis - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sampledAt);
                if (remaining > 0) redis.opsForValue().set(key, value, Duration.ofMillis(remaining));
                else redis.delete(key);
            } else redis.opsForValue().set(key, value);
        }
    }
}
