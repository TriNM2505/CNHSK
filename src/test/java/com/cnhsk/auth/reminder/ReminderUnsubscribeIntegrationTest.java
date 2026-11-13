package com.cnhsk.auth.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.shared.config.CnhskProperties;
import com.cnhsk.shared.security.RateLimiter;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ReminderUnsubscribeIntegrationTest extends AbstractIntegrationTest {

    private static final String ROUTE = "/api/public/reminder/unsubscribe";
    private static final String ACTION = "REMINDER_EMAIL_UNSUBSCRIBED";
    private static final String MESSAGE = "Liên kết tắt nhắc không hợp lệ hoặc đã hết hạn.";
    private static final Instant NOW = Instant.parse("2031-11-15T13:00:20Z");
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private UnsubscribeTokens tokens;
    @Autowired private CnhskProperties properties;
    @Autowired private StringRedisTemplate redis;
    @MockitoBean private Clock clock;
    @MockitoSpyBean private RateLimiter counters;
    private final List<Long> ownedUsers = new ArrayList<>();
    private final String address = "2001:db8:" + UUID.randomUUID().toString().substring(0, 4)
            + ":" + UUID.randomUUID().toString().substring(0, 4) + "::1";

    @BeforeEach
    void setClock() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @AfterEach
    void cleanOwnedFixtures() {
        for (long userId : ownedUsers) {
            jdbc.update("DELETE FROM shared.audit_logs WHERE actor_id = ?", userId);
            jdbc.update("DELETE FROM auth.users WHERE id = ?", userId);
        }
        redis.delete(List.of(key(address, "203111151300"), key(address, "203111151301"),
                key("192.0.2.254", "203111151300")));
    }

    @ParameterizedTest
    @CsvSource(value = {"WEB,EMAIL|WEB|1", "EMAIL|''|1", "WEB|WEB|0", "''|''|0"},
            delimiter = '|', emptyValue = "")
    void FR017_AT14_AT15_removesOnlyEmailAndRepeatedCallsDoNotWriteOrAudit(
            String before, String after, int expectedAudit) throws Exception {
        User user = user(before);
        String token = tokens.issue(user.getId());
        mvc.perform(request().queryParam("token", token)).andExpect(status().isNoContent());
        Map<String, Object> once = row(user);
        assertThat(once.get("reminder_channels")).isEqualTo(after);
        assertThat(once.get("reminder_time")).isEqualTo("20:00:00");
        assertThat(auditCount(user)).isEqualTo(expectedAudit);
        if (expectedAudit == 1) {
            String payload = jdbc.queryForObject(
                    "SELECT detail::text FROM shared.audit_logs WHERE actor_id = ? AND action = ?",
                    String.class, user.getId(), ACTION);
            assertThat(json.readTree(payload)).isEqualTo(json.valueToTree(Map.of(
                    "before", Map.of("channels", before), "after", Map.of("channels", after))));
            assertThat(payload).doesNotContain(token, user.getEmail(), address);
        }
        mvc.perform(request().queryParam("token", token)).andExpect(status().isNoContent());
        assertThat(row(user)).isEqualTo(once);
        assertThat(auditCount(user)).isEqualTo(expectedAudit);
    }

    @Test
    void FR017_acceptsJsonAndMatchingQueryBodyValues() throws Exception {
        User user = user("WEB,EMAIL");
        String token = tokens.issue(user.getId());
        mvc.perform(request().contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", token))))
                .andExpect(status().isNoContent());
        mvc.perform(request().queryParam("token", token).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", token))))
                .andExpect(status().isNoContent());
        assertThat(auditCount(user)).isEqualTo(1);
    }

    @Test
    void FR017_acceptsRealFormBodiesIncludingRfc8058OneClick() throws Exception {
        User oneClick = user("WEB,EMAIL");
        mvc.perform(request().queryParam("token", tokens.issue(oneClick.getId()))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("List-Unsubscribe=One-Click"))
                .andExpect(status().isNoContent());
        assertThat(row(oneClick).get("reminder_channels")).isEqualTo("WEB");
        assertThat(auditCount(oneClick)).isEqualTo(1);

        User form = user("EMAIL");
        String token = tokens.issue(form.getId());
        mvc.perform(request().contentType(MediaType.APPLICATION_FORM_URLENCODED).content("token=" + token))
                .andExpect(status().isNoContent());
        mvc.perform(request().queryParam("token", token).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("List-Unsubscribe=One-Click&token=" + token))
                .andExpect(status().isNoContent());
        assertThat(row(form).get("reminder_channels")).isEqualTo("");
        assertThat(auditCount(form)).isEqualTo(1);
    }

    @Test
    void FR017_rejectsConflictingJsonFormAndRepeatedQueryTokensWithoutChanges() throws Exception {
        User first = user("WEB,EMAIL");
        User second = user("EMAIL");
        String firstToken = tokens.issue(first.getId());
        String secondToken = tokens.issue(second.getId());
        Map<String, Object> beforeFirst = row(first);
        Map<String, Object> beforeSecond = row(second);
        invalid(request().queryParam("token", firstToken).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("token", secondToken))));
        invalid(request().queryParam("token", firstToken).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("token=" + secondToken));
        invalid(request().queryParam("token", firstToken, secondToken));
        assertThat(row(first)).isEqualTo(beforeFirst);
        assertThat(row(second)).isEqualTo(beforeSecond);
        assertThat(auditCount(first)).isZero();
        assertThat(auditCount(second)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{\"token\":", "{\"token\":42}", "{\"token\":true}",
            "{\"token\":[]}", "{\"token\":{}}", "{\"token\":null}", "{\"token\":\"\"}",
            "{\"token\":\" \"}", "[]", "null", "true", "{} {}", "{\"token\":\"a\",\"token\":\"b\"}"})
    void FR017_badJsonReturnsTheUniform400WithoutErrorLogsOrStackTraces(String body) throws Exception {
        User user = user("WEB,EMAIL");
        Map<String, Object> before = row(user);
        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            invalid(request().queryParam("token", tokens.issue(user.getId()))
                    .contentType(MediaType.APPLICATION_JSON).content(body));
            assertThat(appender.list).allSatisfy(event -> {
                assertThat(event.getLevel()).isNotEqualTo(Level.ERROR);
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
        assertThat(row(user)).isEqualTo(before);
        assertThat(auditCount(user)).isZero();
    }

    @Test
    void FR017_AT16_tamperingPurposeExpiryAndMissingUserHaveTheSameResponse() throws Exception {
        User user = user("WEB,EMAIL");
        Map<String, Object> before = row(user);
        String token = tokens.issue(user.getId());
        int signature = token.indexOf('.') + 1;
        String tampered = token.substring(0, signature) + (token.charAt(signature) == 'A' ? 'B' : 'A')
                + token.substring(signature + 1);
        invalid(request().queryParam("token", tampered));
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String payload = encoder.encodeToString((user.getId() + "."
                + NOW.plus(Duration.ofDays(30)).getEpochSecond() + ".PASSWORD_RESET")
                .getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        invalid(request().queryParam("token", payload + "."
                + encoder.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII)))));
        invalid(request().queryParam("token", tokens.issue(Long.MAX_VALUE)));
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofDays(30)));
        invalid(request().queryParam("token", token));
        assertThat(row(user)).isEqualTo(before);
        assertThat(auditCount(user)).isZero();
    }

    @Test
    void FR017_missingEmptyAndOversizedTokensAre400() throws Exception {
        invalid(request());
        invalid(request().queryParam("token", ""));
        invalid(request().queryParam("token", " "));
        invalid(request().queryParam("token", "a".repeat(513)));
        invalid(request().contentType(MediaType.APPLICATION_JSON).content("{}"));
        invalid(request().contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("token", "a".repeat(513)))));
    }

    @Test
    void FR017_tokenSelectsOnlyItsSignedUserAndNeverAClientProvidedId() throws Exception {
        User first = user("WEB,EMAIL");
        User second = user("EMAIL");
        Map<String, Object> before = row(first);
        mvc.perform(request().queryParam("token", tokens.issue(second.getId()))
                        .queryParam("userId", first.getId().toString()))
                .andExpect(status().isNoContent());
        assertThat(row(first)).isEqualTo(before);
        assertThat(auditCount(first)).isZero();
        assertThat(row(second).get("reminder_channels")).isEqualTo("");
        assertThat(auditCount(second)).isEqualTo(1);
    }

    @Test
    void FR017_AT15_concurrentCallsChangeChannelsAndAuditOnlyOnce() throws Exception {
        User user = user("WEB,EMAIL");
        String token = tokens.issue(user.getId());
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return mvc.perform(request().queryParam("token", token)).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                assertThat(result.get(20, TimeUnit.SECONDS)).isEqualTo(204);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(row(user).get("reminder_channels")).isEqualTo("WEB");
        assertThat(auditCount(user)).isEqualTo(1);
    }

    @Test
    void FR002_realRedisLimitsPerRemoteIpAndCalendarMinuteIgnoringForwardedHeaders() throws Exception {
        User user = user("WEB,EMAIL");
        String token = tokens.issue(user.getId());
        for (int index = 0; index < 20; index++) {
            mvc.perform(request().queryParam("token", token).header("X-Forwarded-For", "192.0.2." + index))
                    .andExpect(status().isNoContent());
        }
        mvc.perform(request().queryParam("token", token).header("X-Forwarded-For", "192.0.2.200"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error_code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(40));
        assertThat(redis.opsForValue().get(key(address, "203111151300"))).isEqualTo("21");
        assertThat(redis.getExpire(key(address, "203111151300"), TimeUnit.SECONDS)).isBetween(30L, 40L);
        mvc.perform(request().with(req -> { req.setRemoteAddr("192.0.2.254"); return req; })
                        .queryParam("token", token)).andExpect(status().isNoContent());
        when(clock.instant()).thenReturn(NOW.plusSeconds(40));
        mvc.perform(request().queryParam("token", token)).andExpect(status().isNoContent());
        assertThat(redis.opsForValue().get(key(address, "203111151301"))).isEqualTo("1");
        assertThat(auditCount(user)).isEqualTo(1);
    }

    @Test
    void FR002_redisFailureStillAllowsAValidUnsubscribe() throws Exception {
        User user = user("WEB,EMAIL");
        doThrow(new IllegalStateException("fixture-redis-connection-secret"))
                .when(counters).increment(anyString(), any());
        mvc.perform(request().queryParam("token", tokens.issue(user.getId())))
                .andExpect(status().isNoContent());
        assertThat(row(user).get("reminder_channels")).isEqualTo("WEB");
        assertThat(auditCount(user)).isEqualTo(1);
    }

    private User user(String channels) {
        User user = new User("unsubscribe-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH, "Unsubscribe Learner");
        user.setReminderTime(LocalTime.of(20, 0));
        user.setReminderChannels(channels);
        user = users.saveAndFlush(user);
        ownedUsers.add(user.getId());
        return user;
    }

    private MockHttpServletRequestBuilder request() {
        return post(ROUTE).with(req -> { req.setRemoteAddr(address); return req; });
    }

    private MvcResult invalid(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_UNSUBSCRIBE_TOKEN"))
                .andExpect(jsonPath("$.message").value(MESSAGE))
                .andExpect(jsonPath("$.request_id").isNotEmpty()).andReturn();
    }

    private Map<String, Object> row(User user) {
        return jdbc.queryForMap("SELECT reminder_channels, reminder_time::text, updated_at "
                + "FROM auth.users WHERE id = ?", user.getId());
    }

    private int auditCount(User user) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM shared.audit_logs WHERE actor_id = ? AND action = ?",
                Integer.class, user.getId(), ACTION);
    }

    private String key(String remoteAddress, String minute) {
        return "reminder:unsubscribe:" + remoteAddress + ":" + minute;
    }
}
