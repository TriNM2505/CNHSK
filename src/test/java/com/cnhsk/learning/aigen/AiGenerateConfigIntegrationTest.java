package com.cnhsk.learning.aigen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockingDetails;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToIntFunction;

import com.cnhsk.learning.ai.AnthropicClaudeClient;
import com.cnhsk.learning.ai.ClaudeClient;
import com.cnhsk.learning.ai.StubClaudeClient;
import com.cnhsk.shared.settings.SettingKey;
import com.cnhsk.shared.settings.SettingsAdminModels.SettingReset;
import com.cnhsk.shared.settings.SettingsAdminModels.SettingWrite;
import com.cnhsk.shared.settings.SettingsAdminService;
import com.cnhsk.shared.settings.SettingsApi;
import com.cnhsk.shared.settings.SettingsService;
import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class AiGenerateConfigIntegrationTest extends AbstractIntegrationTest {
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private static final List<BusinessValue> BUSINESS_VALUES = List.of(
            new BusinessValue(SettingKey.AI_GENERATE_SYSTEM_USER_ID, 19, 0, 42, AiGenerateConfig::systemUserId),
            new BusinessValue(SettingKey.AI_GENERATE_WINDOW_DAYS, 20, 14, 28, AiGenerateConfig::windowDays),
            new BusinessValue(SettingKey.AI_GENERATE_MIN_WRONG, 21, 20, 37, AiGenerateConfig::minWrong),
            new BusinessValue(SettingKey.AI_GENERATE_MIN_BANK, 22, 10, 18, AiGenerateConfig::minBank),
            new BusinessValue(SettingKey.AI_GENERATE_BATCH_SIZE, 23, 5, 8, AiGenerateConfig::batchSize),
            new BusinessValue(SettingKey.AI_GENERATE_MAX_BATCHES_PER_RUN, 24, 3, 0, AiGenerateConfig::maxBatchesPerRun));
    private static final List<String> KEYS = BUSINESS_VALUES.stream().map(value -> value.key().key()).toList();

    @Autowired private AiGenerateConfig config;
    @Autowired private SettingsApi api;
    @Autowired private SettingsAdminService admin;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ApplicationContext context;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private StubClaudeClient stub;
    @MockitoSpyBean private SettingsService settings;

    private List<Map<String, Object>> saved;
    private long actorId;
    private int callsBefore;

    @BeforeEach
    void prepareOwnedSettings() {
        callsBefore = stub.callCount();
        saved = new NamedParameterJdbcTemplate(jdbc).queryForList(
                "SELECT key,value,updated_by,updated_at FROM shared.app_settings WHERE key IN (:keys)",
                Map.of("keys", KEYS));
        actorId = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,?) RETURNING id",
                Long.class, "generation-config-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH, "Generation configuration fixture");
        jdbc.update("INSERT INTO auth.user_roles(user_id,role_code) VALUES (?,'ADMIN')", actorId);
        BUSINESS_VALUES.forEach(value -> reset(value.key(), "Prepare isolated generation configuration"));
    }

    @AfterEach
    void restoreOnlyOwnedSettingsAndActor() {
        if (actorId == 0) return;
        // Reset commits invalidate the six slots before restoring their original rows without reading the cache again.
        BUSINESS_VALUES.forEach(value -> reset(value.key(), "Restore generation configuration fixture"));
        for (Map<String, Object> row : saved) {
            jdbc.update("INSERT INTO shared.app_settings(key,value,updated_by,updated_at) VALUES (?,?,?,?)",
                    row.get("key"), row.get("value"), row.get("updated_by"), row.get("updated_at"));
        }
        jdbc.update("DELETE FROM shared.audit_logs WHERE actor_id=? AND target_type='SETTING' "
                + "AND target_id BETWEEN 19 AND 24 AND action IN ('SETTING_CHANGE','SETTING_RESET')", actorId);
        jdbc.update("DELETE FROM auth.user_roles WHERE user_id=?", actorId);
        jdbc.update("DELETE FROM auth.users WHERE id=?", actorId);
    }

    @Test
    void realSettingsCommitAndResetAreVisibleImmediatelyWithPermanentAuditTargets() {
        assertThat(api).isSameAs(settings);
        for (BusinessValue value : BUSINESS_VALUES) {
            assertThat(value.read().applyAsInt(config)).isEqualTo(value.defaultValue());
            long invalidations = invalidationCount(value.key());
            var changed = admin.change(value.key().key(), actorId,
                    () -> new SettingWrite(value.overrideValue(), "Change generation configuration"));
            assertThat(changed.currentValue()).isEqualTo(value.overrideValue());
            assertThat(changed.updatedBy().id()).isEqualTo(actorId);
            assertThat(invalidationCount(value.key())).isEqualTo(invalidations + 1);
            assertThat(value.read().applyAsInt(config)).isEqualTo(value.overrideValue());
            assertThat(jdbc.queryForObject("SELECT target_id FROM shared.audit_logs WHERE actor_id=? "
                            + "AND action='SETTING_CHANGE' AND detail->>'key'=?", Long.class, actorId, value.key().key()))
                    .isEqualTo(value.auditId());
            reset(value.key(), "Reset generation configuration");
            assertThat(value.read().applyAsInt(config)).isEqualTo(value.defaultValue());
            assertThat(jdbc.queryForObject("SELECT target_id FROM shared.audit_logs WHERE actor_id=? "
                            + "AND action='SETTING_RESET' AND detail->>'key'=? AND detail->>'reason'=?", Long.class,
                    actorId, value.key().key(), "Reset generation configuration")).isEqualTo(value.auditId());
        }
        assertOfflineConfiguration();
    }

    @Test
    void rolledBackChangesKeepAllSixCachedDefaultsRowsAndAuditUnchanged() {
        for (BusinessValue value : BUSINESS_VALUES) {
            assertThat(value.read().applyAsInt(config)).isEqualTo(value.defaultValue());
            long invalidations = invalidationCount(value.key());
            long audits = ownedAuditCount();
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                admin.change(value.key().key(), actorId,
                        () -> new SettingWrite(value.overrideValue(), "Roll back generation configuration"));
                assertThat(value.read().applyAsInt(config)).isEqualTo(value.defaultValue());
                assertThat(invalidationCount(value.key())).isEqualTo(invalidations);
                status.setRollbackOnly();
            });
            assertThat(invalidationCount(value.key())).isEqualTo(invalidations);
            assertThat(value.read().applyAsInt(config)).isEqualTo(value.defaultValue());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM shared.app_settings WHERE key=?", Long.class,
                    value.key().key())).isZero();
            assertThat(ownedAuditCount()).isEqualTo(audits);
        }
        assertOfflineConfiguration();
    }

    private void assertOfflineConfiguration() {
        assertThat(context.getBeansOfType(ClaudeClient.class)).hasSize(1).containsValue(stub);
        assertThat(context.getBeansOfType(AnthropicClaudeClient.class)).isEmpty();
        assertThat(stub.callCount()).isEqualTo(callsBefore);
        assertThat(config.getCron()).isEqualTo("-");
        assertThat(config.getGenerateModel()).isEqualTo("claude-sonnet-5-5");
        assertThat(config.getTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(Arrays.stream(AiGenerateConfig.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(Scheduled.class))).allMatch(annotation -> annotation == null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.service_requests WHERE user_id=? AND kind='AI_GENERATE'",
                Long.class, actorId)).isZero();
    }

    private long invalidationCount(SettingKey key) {
        return mockingDetails(settings).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("invalidate"))
                .filter(invocation -> Arrays.equals(invocation.getArguments(), new Object[] {key})).count();
    }

    private long ownedAuditCount() {
        return jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs WHERE actor_id=? AND target_type='SETTING' "
                + "AND target_id BETWEEN 19 AND 24", Long.class, actorId);
    }

    private void reset(SettingKey key, String reason) {
        admin.reset(key.key(), actorId, () -> new SettingReset(reason));
    }

    private record BusinessValue(SettingKey key, long auditId, int defaultValue, int overrideValue,
            ToIntFunction<AiGenerateConfig> read) { }
}
