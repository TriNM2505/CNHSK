package com.cnhsk.learning.ai.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.settings.SettingKey;
import com.cnhsk.shared.settings.SettingsApi;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class AiUsageControllerIntegrationTest extends AbstractIntegrationTest {
    private static final String ENDPOINT = "/api/admin/ai-usage";
    private static final String SETTING = "ai.monthly-token-budget";
    private static final String PASSWORD = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private Clock clock;
    @Autowired private SettingsApi settings;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private StringRedisTemplate redis;
    private String month;
    private String key;
    private long actor;
    private String previousCounter;
    private Long previousTtl;
    private long sampledAt;
    private JsonNode previousSetting;
    private List<Map<String, Object>> previousRows;
    private boolean budgetChanged;

    @BeforeEach
    void setup() throws Exception {
        actor = jdbc.queryForObject("""
                INSERT INTO auth.users(email,password_hash,display_name,email_verified_at)
                VALUES (?,?,'Quản trị tự soạn',now()) RETURNING id
                """, Long.class, "usage-" + UUID.randomUUID() + "@example.invalid", PASSWORD);
        YearMonth current = YearMonth.from(clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")));
        month = current.toString();
        key = "ai:tokens:" + current.format(DateTimeFormatter.ofPattern("uuuuMM"));
        previousCounter = redis.opsForValue().get(key);
        previousTtl = redis.getExpire(key, TimeUnit.MILLISECONDS);
        sampledAt = System.nanoTime();
        redis.delete(key);
        previousRows = jdbc.queryForList("SELECT * FROM shared.app_settings WHERE key=?", SETTING);
        JsonNode catalog = mapper.readTree(mvc.perform(get("/api/admin/settings")
                .header("Authorization", bearer("ADMIN"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        for (JsonNode item : catalog) if (item.path("key").asText().equals(SETTING)) previousSetting = item;
        assertThat(previousSetting).isNotNull();
        budgetChanged = false;
    }

    @AfterEach
    void cleanup() throws Exception {
        reset(redis);
        if (budgetChanged) {
            if (previousSetting.path("overridden").asBoolean()) changeBudget(previousSetting.path("currentValue").asInt());
            else mvc.perform(delete("/api/admin/settings/{key}", SETTING)
                    .header("Authorization", bearer("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"Khôi phục fixture tự soạn\"}"))
                    .andExpect(status().isOk());
            if (!previousRows.isEmpty()) {
                var original = previousRows.get(0);
                jdbc.update("UPDATE shared.app_settings SET updated_by=?,updated_at=? WHERE key=?",
                        original.get("updated_by"), original.get("updated_at"), SETTING);
            }
        }
        if (previousCounter == null) redis.delete(key);
        else if (previousTtl != null && previousTtl >= 0) {
            long remaining = previousTtl - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sampledAt);
            if (remaining > 0) redis.opsForValue().set(key, previousCounter, Duration.ofMillis(remaining));
            else redis.delete(key);
        } else redis.opsForValue().set(key, previousCounter);
        jdbc.update("DELETE FROM shared.audit_logs WHERE actor_id=?", actor);
        jdbc.update("DELETE FROM auth.users WHERE id=?", actor);
    }

    @Test
    void adminReadsAnAvailableZeroWithoutCreatingACounter() throws Exception {
        JsonNode body = read("ADMIN");
        assertThat(body.size()).isEqualTo(4);
        assertThat(body.path("month").asText()).isEqualTo(month);
        assertThat(body.path("used").isIntegralNumber()).isTrue();
        assertThat(body.path("used").asLong()).isZero();
        assertThat(body.path("available").asBoolean()).isTrue();
        assertThat(body.path("budget").asInt()).isEqualTo(settings.getInt(SettingKey.AI_MONTHLY_TOKEN_BUDGET));
        assertThat(redis.hasKey(key)).isFalse();
    }

    @Test
    void cookieAuthenticatedAdminCanReadWithoutACsrfToken() throws Exception {
        String token = jwt.issueAccessToken(actor, List.of("ADMIN"), true, UUID.randomUUID());
        mvc.perform(get(ENDPOINT).cookie(new Cookie("access_token", token))).andExpect(status().isOk());
        assertThat(redis.hasKey(key)).isFalse();
    }

    @Test
    void realSettingsWritesAreReflectedImmediatelyWithoutChangingUsage() throws Exception {
        redis.opsForValue().set(key, "1234", Duration.ofMinutes(10));
        for (int value : List.of(2000, 0, 500)) {
            budgetChanged = true;
            changeBudget(value);
            JsonNode body = read("ADMIN");
            assertThat(body.path("budget").asInt()).isEqualTo(value);
            assertThat(body.path("used").asLong()).isEqualTo(1234L);
            assertThat(body.path("available").asBoolean()).isTrue();
        }
        assertThat(redis.opsForValue().get(key)).isEqualTo("1234");
        assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isBetween(1L, 600L);
    }

    @Test
    void adminReceivesTheFullLongCounterWithoutAnArtificialCap() throws Exception {
        redis.opsForValue().set(key, Long.toString(Long.MAX_VALUE));
        JsonNode body = read("ADMIN");
        assertThat(body.path("used").isIntegralNumber()).isTrue();
        assertThat(body.path("used").asLong()).isEqualTo(Long.MAX_VALUE);
        assertThat(body.path("available").asBoolean()).isTrue();
        assertThat(redis.getExpire(key)).isEqualTo(-1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONTENT_EDITOR", "LEARNER", "TEACHER", "MODERATOR", "ACCOUNTANT"})
    void otherRolesReceiveTheCommonForbiddenResponseBeforeReadingUsage(String role) throws Exception {
        var values = spy(redis.opsForValue());
        doReturn(values).when(redis).opsForValue();
        var response = mvc.perform(get(ENDPOINT).header("Authorization", bearer(role)))
                .andExpect(status().isForbidden()).andReturn().getResponse();
        assertError(mapper.readTree(response.getContentAsByteArray()), "FORBIDDEN");
        verify(values, never()).get(key);
    }

    @Test
    void guestReceivesTheCommonUnauthenticatedResponse() throws Exception {
        var values = spy(redis.opsForValue());
        doReturn(values).when(redis).opsForValue();
        var response = mvc.perform(get(ENDPOINT)).andExpect(status().isUnauthorized()).andReturn().getResponse();
        assertError(mapper.readTree(response.getContentAsByteArray()), "UNAUTHENTICATED");
        verify(values, never()).get(key);
    }

    @Test
    void mixedRolesIncludingAdminCanRead() throws Exception {
        assertThat(read("CONTENT_EDITOR", "LEARNER", "ADMIN").path("available").asBoolean()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"counter-fixture-not-a-number", "-1", "9223372036854775808"})
    void malformedCountersReturn200WithAnExplicitNullAndRemainUnchanged(String raw) throws Exception {
        redis.opsForValue().set(key, raw);
        assertUnavailable(read("ADMIN"));
        assertThat(redis.opsForValue().get(key)).isEqualTo(raw);
        assertThat(redis.getExpire(key)).isEqualTo(-1L);
    }

    @Test
    void redisFailureReturns200AndDoesNotExposeInternalErrorDetails() throws Exception {
        var values = spy(redis.opsForValue());
        doThrow(new RedisConnectionFailureException("private-fixture-details")).when(values).get(key);
        doReturn(values).when(redis).opsForValue();
        JsonNode body = read("ADMIN");
        assertUnavailable(body);
        assertThat(body.toString()).doesNotContain("private-fixture-details", "exception", "stackTrace");
    }

    private void assertUnavailable(JsonNode body) {
        assertThat(body.size()).isEqualTo(4);
        assertThat(body.path("month").asText()).isEqualTo(month);
        assertThat(body.has("used")).isTrue();
        assertThat(body.path("used").isNull()).isTrue();
        assertThat(body.path("available").asBoolean()).isFalse();
        assertThat(body.path("budget").asInt()).isEqualTo(settings.getInt(SettingKey.AI_MONTHLY_TOKEN_BUDGET));
    }

    private void assertError(JsonNode body, String code) {
        assertThat(body.path("error_code").asText()).isEqualTo(code);
        assertThat(body.path("message").isTextual()).isTrue();
        assertThat(body.path("request_id").asText()).isNotBlank();
        assertThat(body.toString()).doesNotContain("stackTrace");
    }

    private JsonNode read(String... roles) throws Exception {
        return mapper.readTree(mvc.perform(get(ENDPOINT).header("Authorization", bearer(roles)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }

    private String bearer(String... roles) {
        return "Bearer " + jwt.issueAccessToken(actor, List.of(roles), true, UUID.randomUUID());
    }

    private void changeBudget(int value) throws Exception {
        mvc.perform(put("/api/admin/settings/{key}", SETTING).header("Authorization", bearer("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("value", value, "reason", "Fixture token usage tự soạn"))))
                .andExpect(status().isOk());
    }
}
