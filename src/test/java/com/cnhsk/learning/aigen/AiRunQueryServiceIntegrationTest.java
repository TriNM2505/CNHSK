package com.cnhsk.learning.aigen;

import com.cnhsk.learning.ai.StubClaudeClient;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.AuthenticatedUser;
import com.cnhsk.shared.settings.SettingKey;
import com.cnhsk.shared.settings.SettingsService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@Transactional
@ExtendWith(OutputCaptureExtension.class)
class AiRunQueryServiceIntegrationTest extends AbstractIntegrationTest {
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    static final Instant NOW = Instant.parse("2026-10-31T17:15:00Z");
    static final Instant MONTH_START = Instant.parse("2026-10-31T17:00:00Z");
    static final Instant NEXT_MONTH = Instant.parse("2026-11-30T17:00:00Z");
    @Autowired AiRunQueryService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired StubClaudeClient provider;
    @Autowired StringRedisTemplate redis;
    @MockitoBean SettingsService settings;
    @MockitoBean Clock clock;
    long admin;
    int calls;

    @BeforeEach
    void setup() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.millis()).thenReturn(NOW.toEpochMilli());
        admin = user();
        jdbc.update("INSERT INTO auth.user_roles(user_id,role_code) VALUES (?,'ADMIN')", admin);
        when(settings.getInt(SettingKey.AI_GENERATE_SYSTEM_USER_ID)).thenReturn(Math.toIntExact(admin));
        authenticate("ADMIN");
        calls = provider.callCount();
    }

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
        assertThat(provider.callCount()).isEqualTo(calls);
    }

    @Test
    void listsOnlyOriginalRunLifecyclesWithStableNewestFirstPagingAndExactFilteredTotals() {
        long older = request(admin, "AI_GENERATE", "FAILED", NOW.minusSeconds(1), null, "STALE");
        long first = request(admin, "AI_GENERATE", "COMPLETED", NOW, null, null);
        long second = request(admin, "AI_GENERATE", "COMPLETED", NOW, null, null);
        long running = request(admin, "AI_GENERATE", "RUNNING", NOW.plusSeconds(1), null, null);
        request(user(), "AI_GENERATE", "PENDING", NOW.plusSeconds(2), null, null);
        request(admin, "AI_GENERATE", "ASSIGNED", NOW.plusSeconds(2), null, null);
        request(admin, "AI_GENERATE", "EXPIRED", NOW.plusSeconds(2), null, null);
        for (String kind : Set.of("IMPORT", "GRADING", "TRANSLATE")) {
            request(admin, kind, "COMPLETED", NOW.plusSeconds(3), "{\"inputTokens\":900}", null);
        }
        var page = service.list(null, 0, 2);
        assertThat(page.enabled()).isTrue();
        assertThat(page.items()).extracting(AiRunQueryService.Run::id).containsExactly(running, second);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(4);
        assertThat(service.list(null, 1, 2).items()).extracting(AiRunQueryService.Run::id).containsExactly(first, older);
        assertThat(service.list(null, Integer.MAX_VALUE, 50).items()).isEmpty();
        assertThat(service.list(null, Integer.MAX_VALUE, 50).totalElements()).isEqualTo(4);
        var filtered = service.list("COMPLETED", 1, 1);
        assertThat(filtered.items()).extracting(AiRunQueryService.Run::id).containsExactly(first);
        assertThat(filtered.totalElements()).isEqualTo(2);
        assertThat(service.list("RUNNING", 0, 20).items()).singleElement().satisfies(run -> {
            assertThat(run.id()).isEqualTo(running);
            assertThat(run.completedAt()).isNull();
            assertThat(run.questionCount()).isZero();
            assertThat(run.droppedCount()).isZero();
            assertThat(run.inputTokens()).isZero();
            assertThat(run.outputTokens()).isZero();
            assertThat(run.error()).isNull();
        });
    }

    @Test
    void sumsRecordedTokensAcrossHistoricalUsersByVietnamCreationMonthIndependentlyOfFilterAndPage() {
        long historical = user();
        request(historical, "AI_GENERATE", "COMPLETED", MONTH_START.minusMillis(1), "{\"inputTokens\":1000}", null);
        request(historical, "AI_GENERATE", "COMPLETED", MONTH_START, "{\"inputTokens\":2147483648,\"outputTokens\":7}", null);
        request(admin, "AI_GENERATE", "FAILED", NEXT_MONTH.minusMillis(1), "{\"inputTokens\":11,\"outputTokens\":13}", "NO_VALID_QUESTION");
        request(admin, "AI_GENERATE", "COMPLETED", NEXT_MONTH, "{\"inputTokens\":2000}", null);
        request(historical, "AI_GENERATE", "EXPIRED", NOW, "{\"inputTokens\":17}", null);
        request(admin, "TRANSLATE", "COMPLETED", NOW, "{\"inputTokens\":3000}", null);
        // A charged completion whose finalization failed has no recorded result and adds zero here.
        request(admin, "AI_GENERATE", "FAILED", NOW, null, "DB_ERROR");
        long expected = 2147483648L + 7 + 11 + 13 + 17;
        assertThat(service.list(null, 0, 20).monthTokens()).isEqualTo(expected);
        assertThat(service.list("RUNNING", 9, 1).monthTokens()).isEqualTo(expected);
        assertThat(service.list("FAILED", 0, 1).monthTokens()).isEqualTo(expected);
    }

    @Test
    void projectsOnlySafeFieldsAndCountsPersistedArraysWithoutExposingPrivateMetadata(CapturedOutput output) throws Exception {
        String privateValue = "private-history-" + UUID.randomUUID();
        long id = request(admin, "AI_GENERATE", "FAILED", NOW,
                mapper.writeValueAsString(Map.of("questionIds", new long[]{11, 22}, "dropped", new Object[]{
                        Map.of("reason", privateValue), Map.of("reason", privateValue), Map.of("reason", privateValue)},
                        "inputTokens", 41, "outputTokens", 43, "response", privateValue)), privateValue);
        jdbc.update("UPDATE learning.service_requests SET payload=CAST(? AS jsonb),completed_at=? WHERE id=?",
                mapper.writeValueAsString(Map.of("kid", "NP1-001", "prompt", privateValue, "user", privateValue)),
                Timestamp.from(NOW.plusSeconds(5)), id);
        var run = service.list(null, 0, 20).items().get(0);
        assertThat(run.id()).isEqualTo(id);
        assertThat(run.kid()).isEqualTo("NP1-001");
        assertThat(run.createdAt()).isEqualTo(NOW);
        assertThat(run.completedAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(run.questionCount()).isEqualTo(2);
        assertThat(run.droppedCount()).isEqualTo(3);
        assertThat(run.inputTokens()).isEqualTo(41);
        assertThat(run.outputTokens()).isEqualTo(43);
        assertThat(run.error()).isNull();
        var json = mapper.valueToTree(run);
        assertThat(json.properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder("id", "kid", "status",
                "createdAt", "completedAt", "questionCount", "droppedCount", "inputTokens", "outputTokens", "error");
        assertThat(json.toString()).doesNotContain(privateValue, "payload", "response", "userId", "prompt", "questionIds");
        assertThat(output.getAll()).contains("unsupported error code").doesNotContain(privateValue);
        assertThat(jdbc.queryForObject("SELECT error_message FROM learning.service_requests WHERE id=?", String.class, id))
                .isEqualTo(privateValue);
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVALID_OUTPUT", "BUDGET_EXCEEDED", "PROVIDER_ERROR", "NO_VALID_QUESTION", "STALE"})
    void preservesEveryOriginalPublicErrorCode(String error) {
        request(admin, "AI_GENERATE", "FAILED", NOW, null, error);
        assertThat(service.list(null, 0, 20).items()).singleElement().extracting(AiRunQueryService.Run::error).isEqualTo(error);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DB_ERROR", "CONTEXT_UNAVAILABLE", "", "provider response contains private text"})
    void redactsEveryUnsupportedStoredErrorWithOnlySafeWarnings(String error, CapturedOutput output) {
        request(admin, "AI_GENERATE", "FAILED", NOW, null, error);
        assertThat(service.list(null, 0, 20).items().get(0).error()).isNull();
        assertThat(output.getAll()).contains("unsupported error code");
        if (!error.isEmpty()) assertThat(output.getAll()).doesNotContain(error);
    }

    @Test
    void nullAndUnexpectedMetadataShapesProduceZeroCountsAndTokens() {
        request(admin, "AI_GENERATE", "COMPLETED", NOW,
                "{\"questionIds\":{},\"dropped\":null,\"inputTokens\":-1,\"outputTokens\":\"99\"}", null);
        request(admin, "AI_GENERATE", "COMPLETED", NOW, "null", null);
        request(admin, "AI_GENERATE", "COMPLETED", NOW, "{\"inputTokens\":1.5,\"outputTokens\":9223372036854775808}", null);
        var page = service.list(null, 0, 20);
        assertThat(page.monthTokens()).isZero();
        assertThat(page.items()).allSatisfy(run -> {
            assertThat(run.questionCount()).isZero();
            assertThat(run.droppedCount()).isZero();
            assertThat(run.inputTokens()).isZero();
            assertThat(run.outputTokens()).isZero();
        });
    }

    @Test
    void enabledReflectsCurrentConfiguredAccountRoleAndRestrictionsWithoutHidingHistory() {
        request(admin, "AI_GENERATE", "FAILED", NOW, null, "STALE");
        for (int configured : new int[]{0, Integer.MAX_VALUE, Math.toIntExact(user())}) {
            when(settings.getInt(SettingKey.AI_GENERATE_SYSTEM_USER_ID)).thenReturn(configured);
            assertThat(service.list(null, 0, 20).enabled()).isFalse();
            assertThat(service.list(null, 0, 20).items()).hasSize(1);
        }
        when(settings.getInt(SettingKey.AI_GENERATE_SYSTEM_USER_ID)).thenReturn(Math.toIntExact(admin));
        jdbc.update("UPDATE auth.users SET banned_at=?,ban_reason='Lý do kiểm thử tự soạn' WHERE id=?", Timestamp.from(NOW), admin);
        assertThat(service.list(null, 0, 20).enabled()).isFalse();
        jdbc.update("UPDATE auth.users SET banned_at=NULL,suspended_at=?,suspended_until=? WHERE id=?",
                Timestamp.from(NOW), Timestamp.from(NOW.plusSeconds(60)), admin);
        assertThat(service.list(null, 0, 20).enabled()).isFalse();
        jdbc.update("UPDATE auth.users SET suspended_until=? WHERE id=?", Timestamp.from(NOW), admin);
        assertThat(service.list(null, 0, 20).enabled()).isTrue();
        jdbc.update("DELETE FROM auth.user_roles WHERE user_id=? AND role_code='ADMIN'", admin);
        assertThat(service.list(null, 0, 20).enabled()).isFalse();
    }

    @Test
    void readsDoNotChangeRunningRowsOrGlobalTokenBudgetValueAndExpiry() {
        request(admin, "AI_GENERATE", "RUNNING", NOW.minusSeconds(86400), null, null);
        String key = "ai:tokens:202611";
        String previous = redis.opsForValue().get(key);
        // This test does not initialize or alter the global counter, even in setup.
        Long expiry = redis.getExpire(key, java.util.concurrent.TimeUnit.MILLISECONDS);
        for (int i = 0; i < 3; i++) assertThat(service.list(null, 0, 20).items().get(0).status()).isEqualTo("RUNNING");
        assertThat(redis.opsForValue().get(key)).isEqualTo(previous);
        Long after = redis.getExpire(key, java.util.concurrent.TimeUnit.MILLISECONDS);
        if (expiry != null && expiry < 0) assertThat(after).isEqualTo(expiry);
        else if (expiry != null) assertThat(after).isLessThanOrEqualTo(expiry);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.service_requests WHERE user_id=?", Long.class, admin)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TEACHER", "CONTENT_EDITOR", "LEARNER", "MODERATOR", "ACCOUNTANT"})
    void serviceEnforcesAdminBeforeValidatingParameters(String role) {
        authenticate(role);
        assertThatThrownBy(() -> service.list("INVALID", -1, 0)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void invalidServiceFiltersAre422AndMixedAdminRolesAreAllowed() {
        for (String status : new String[]{"PENDING", "ASSIGNED", "EXPIRED", "completed", "", " FAILED"}) {
            assertThatThrownBy(() -> service.list(status, 0, 20)).isInstanceOfSatisfying(ApiException.class,
                    error -> assertThat(error.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        }
        assertThatThrownBy(() -> service.list(null, -1, 20)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.list(null, 0, 0)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.list(null, 0, 51)).isInstanceOf(ApiException.class);
        authenticate("ADMIN", "TEACHER");
        assertThat(service.list(null, 0, 50).items()).isEmpty();
    }

    private void authenticate(String... roles) {
        var principal = new AuthenticatedUser(admin, Set.of(roles), true, UUID.randomUUID().toString(), UUID.randomUUID(), NOW);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of()));
    }

    private long user() {
        return jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name,email_verified_at) VALUES (?,?,'Run history fixture',?) RETURNING id",
                Long.class, "run-history-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH, Timestamp.from(NOW));
    }

    private long request(long user, String kind, String status, Instant created, String result, String error) {
        return jdbc.queryForObject("INSERT INTO learning.service_requests(user_id,kind,status,payload,result,error_message,created_at,deadline_at)"
                        + " VALUES (?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?,?,?) RETURNING id", Long.class,
                user, kind, status, "{\"kid\":\"NP1-001\"}", result, error, Timestamp.from(created), Timestamp.from(created.plusSeconds(600)));
    }
}
