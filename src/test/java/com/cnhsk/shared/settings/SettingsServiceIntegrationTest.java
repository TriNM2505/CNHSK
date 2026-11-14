package com.cnhsk.shared.settings;

import static com.cnhsk.shared.settings.SettingKey.BILLING_FREE_MONTHLY_LIMIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class SettingsServiceIntegrationTest extends AbstractIntegrationTest {
    private static final String PASSWORD_HASH = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12)
            .encode(UUID.randomUUID().toString());
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Environment environment;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private SettingsService springService;
    @Autowired private SettingsApi api;
    private final SettingsServiceTest.AdjustableClock clock = new SettingsServiceTest.AdjustableClock();
    private List<Map<String, Object>> saved;
    private long userId;
    private SettingsService service;

    @BeforeEach
    void setUp() {
        saved = jdbc.queryForList("SELECT * FROM shared.app_settings WHERE key = ?", BILLING_FREE_MONTHLY_LIMIT.key());
        jdbc.update("DELETE FROM shared.app_settings WHERE key = ?", BILLING_FREE_MONTHLY_LIMIT.key());
        userId = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) "
                + "VALUES (?,?,'Settings fixture') RETURNING id", Long.class,
                "settings-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH);
        service = new SettingsService(jdbc, environment, clock, transactions);
        springService.invalidate(BILLING_FREE_MONTHLY_LIMIT);
    }

    @AfterEach
    void restoreFixture() {
        jdbc.update("DELETE FROM shared.app_settings WHERE key = ?", BILLING_FREE_MONTHLY_LIMIT.key());
        for (Map<String, Object> row : saved) {
            jdbc.update("INSERT INTO shared.app_settings(key,value,updated_by,updated_at) VALUES (?,?,?,?)",
                    row.get("key"), row.get("value"), row.get("updated_by"), row.get("updated_at"));
        }
        jdbc.update("DELETE FROM auth.users WHERE id = ?", userId);
        springService.invalidate(BILLING_FREE_MONTHLY_LIMIT);
    }

    @Test
    void springExposesTheReadContractWithYamlDefaults() {
        assertThat(api).isSameAs(springService);
        assertThat(api.getInt(BILLING_FREE_MONTHLY_LIMIT))
                .isEqualTo(environment.getProperty(BILLING_FREE_MONTHLY_LIMIT.defaultProperty(), Integer.class));
    }

    @Test
    void committedOverridesExpireAndResetInvalidationRestoresYamlImmediately() {
        assertThat(service.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(10);
        write("41");
        assertThat(service.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(10);
        clock.advance(29_999);
        assertThat(service.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(10);
        clock.advance(1);
        assertThat(service.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(41);
        write("0");
        service.invalidate(BILLING_FREE_MONTHLY_LIMIT);
        assertThat(service.getInt(BILLING_FREE_MONTHLY_LIMIT)).isZero();
        jdbc.update("DELETE FROM shared.app_settings WHERE key = ?", BILLING_FREE_MONTHLY_LIMIT.key());
        service.invalidate(BILLING_FREE_MONTHLY_LIMIT);
        assertThat(service.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(10);
    }

    @Test
    void realSqlFailureOutsideCallerDoesNotAbortItsTransaction() {
        JdbcTemplate failing = spy(jdbc);
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return jdbc.queryForObject("SELECT 1 / 0", String.class);
        }).when(failing).query(anyString(), any(ResultSetExtractor.class), eq(BILLING_FREE_MONTHLY_LIMIT.key()));
        SettingsService fallback = new SettingsService(failing, environment, clock, transactions);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            jdbc.update("UPDATE auth.users SET display_name = 'Caller continues' WHERE id = ?", userId);
            assertThat(fallback.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(10);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(status.isRollbackOnly()).isFalse();
            assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
        });
        assertThat(jdbc.queryForObject("SELECT display_name FROM auth.users WHERE id = ?", String.class, userId))
                .isEqualTo("Caller continues");
    }

    @Test
    void corruptDatabaseValueFallsBackAndRecoversAfterInvalidation() {
        write("not-an-integer");
        assertThat(service.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(10);
        write("38");
        service.invalidate(BILLING_FREE_MONTHLY_LIMIT);
        assertThat(service.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(38);
    }

    private void write(String value) {
        jdbc.update("INSERT INTO shared.app_settings(key,value,updated_by) VALUES (?,?,?) "
                + "ON CONFLICT (key) DO UPDATE SET value=EXCLUDED.value, updated_by=EXCLUDED.updated_by, updated_at=now()",
                BILLING_FREE_MONTHLY_LIMIT.key(), value, userId);
    }
}
