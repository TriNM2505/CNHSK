package com.cnhsk.shared.settings;

import static com.cnhsk.shared.settings.SettingKey.*;
import static com.cnhsk.shared.settings.SettingsAdminModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class SettingsAdminIntegrationTest extends AbstractIntegrationTest {
    @Autowired private SettingsAdminService service;
    @Autowired private SettingsApi api;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @Autowired private ObjectMapper mapper;
    @Autowired private Environment environment;
    @Autowired private PlatformTransactionManager transactions;
    @MockitoSpyBean private SettingsService settings;
    @MockitoSpyBean private SettingsAdminStore store;
    @MockitoSpyBean private AuditService audit;
    private SettingsAdminFixture fixture;

    @BeforeEach void setup() { fixture = new SettingsAdminFixture(jdbc, settings); clearInvocations(settings, store, audit); }
    @AfterEach void cleanup() { fixture.close(); }

    @Test
    void fourEndpointsReturnCatalogCommitThePrincipalAndResetIdempotentlyWithHistory() throws Exception {
        String bearer = bearer();
        var listed = json(mvc.perform(get("/api/admin/settings").header("Authorization", bearer)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(listed).hasSize(24);
        assertThat(listed.get(0).path("currentValue").asInt()).isEqualTo(10);
        assertThat(listed.get(0).get("updatedBy").isNull()).isTrue();
        assertThat(listed.get(0).get("updatedAt").isNull()).isTrue();
        List<SettingKey> generationKeys = List.of(AI_GENERATE_SYSTEM_USER_ID, AI_GENERATE_WINDOW_DAYS, AI_GENERATE_MIN_WRONG,
                AI_GENERATE_MIN_BANK, AI_GENERATE_BATCH_SIZE, AI_GENERATE_MAX_BATCHES_PER_RUN);
        for (int i = 0; i < generationKeys.size(); i++) {
            SettingKey key = generationKeys.get(i);
            JsonNode item = listed.get(18 + i);
            assertThat(item.path("key").asText()).isEqualTo(key.key());
            assertThat(item.path("group").asText()).isEqualTo("AI sinh câu");
            assertThat(item.path("description").asText()).isEqualTo(key.description());
            assertThat(item.path("type").asText()).isEqualTo("INT");
            assertThat(item.path("min").asInt()).isEqualTo(key.min());
            assertThat(item.path("max").asInt()).isEqualTo(key.max());
            assertThat(item.path("defaultValue").asInt()).isEqualTo(environment.getProperty(key.defaultProperty(), Integer.class));
        }
        long count = fixture.auditCount(BILLING_FREE_MONTHLY_LIMIT);
        assertThat(api.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(10);
        mvc.perform(put(path(BILLING_FREE_MONTHLY_LIMIT)).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":13,\"reason\":\"Điều chỉnh\",\"updatedBy\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentValue").value(13))
                .andExpect(jsonPath("$.updatedBy.id").value(fixture.actor))
                .andExpect(jsonPath("$.updatedBy.displayName").value("Quản trị cài đặt"));
        assertThat(api.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(13);
        assertThat(jdbc.queryForObject("SELECT updated_by FROM shared.app_settings WHERE key=?", Long.class, BILLING_FREE_MONTHLY_LIMIT.key()))
                .isEqualTo(fixture.actor);
        var saved = jdbc.queryForMap("SELECT target_type,target_id,detail::text FROM shared.audit_logs WHERE actor_id=? AND action='SETTING_CHANGE'", fixture.actor);
        assertThat(saved.get("target_type")).isEqualTo("SETTING");
        assertThat(((Number) saved.get("target_id")).longValue()).isEqualTo(BILLING_FREE_MONTHLY_LIMIT.auditId());
        JsonNode detail = mapper.readTree(saved.get("detail").toString());
        assertThat(detail).hasSize(4);
        assertThat(detail.path("oldValue").asInt()).isEqualTo(10);
        assertThat(detail.path("newValue").asInt()).isEqualTo(13);
        assertThat(detail.path("reason").asText()).isEqualTo("Điều chỉnh");
        for (int i = 0; i < 2; i++) {
            mvc.perform(delete(path(BILLING_FREE_MONTHLY_LIMIT)).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"Về mặc định\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.currentValue").value(10))
                    .andExpect(jsonPath("$.overridden").value(false)).andExpect(jsonPath("$.updatedBy").isEmpty());
            assertThat(api.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(10);
        }
        mvc.perform(get(path(BILLING_FREE_MONTHLY_LIMIT) + "/history").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(count + 3));
        var history = service.history(BILLING_FREE_MONTHLY_LIMIT.key(), 0, 50);
        assertThat(history.items().get(0).newValue()).isNull();
        assertThat(history.items().get(1).newValue()).isNull();
        assertThat(history.items().get(2).newValue()).isEqualTo(13);
    }

    @Test
    void cacheIsNotInvalidatedUntilTheOuterTransactionCommits() {
        assertThat(api.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(10);
        clearInvocations(settings);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var result = change(BILLING_FREE_MONTHLY_LIMIT, 21);
            assertThat(result.currentValue()).isEqualTo(21);
            assertThat(api.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(10);
            verify(settings, never()).invalidate(any());
        });
        verify(settings).invalidate(BILLING_FREE_MONTHLY_LIMIT);
        assertThat(api.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(21);
    }

    @Test
    void outerRollbackKeepsCacheAndDatabaseAndAuditUnchanged() {
        long count = fixture.auditCount(BILLING_FREE_MONTHLY_LIMIT);
        assertThat(api.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(10);
        clearInvocations(settings);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            change(BILLING_FREE_MONTHLY_LIMIT, 21);
            assertThat(api.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(10);
            status.setRollbackOnly();
        });
        verify(settings, never()).invalidate(any());
        assertThat(api.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(10);
        assertThat(fixture.auditCount(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shared.app_settings WHERE key=?", Integer.class, BILLING_FREE_MONTHLY_LIMIT.key())).isZero();
    }

    @Test
    void otherInstancesObserveCommittedChangesAtTheThirtySecondBoundary() {
        var clock = new SettingsServiceTest.AdjustableClock();
        var other = new SettingsService(jdbc, environment, clock, transactions);
        assertThat(other.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(10);
        change(BILLING_FREE_MONTHLY_LIMIT, 22);
        assertThat(api.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(22);
        clock.advance(29_999);
        assertThat(other.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(10);
        clock.advance(1);
        assertThat(other.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(22);
    }

    @Test
    void auditFailureRollsBackAnUpsertAndDoesNotInvalidate() {
        assertThat(api.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(10);
        clearInvocations(settings);
        doThrow(new IllegalStateException("audit-fixture")).when(audit)
                .recordDetail(eq(fixture.actor), eq("SETTING_CHANGE"), eq("SETTING"), anyLong(), anyMap());
        assertThatThrownBy(() -> change(BILLING_FREE_MONTHLY_LIMIT, 25)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shared.app_settings WHERE key=?", Integer.class, BILLING_FREE_MONTHLY_LIMIT.key())).isZero();
        verify(settings, never()).invalidate(any());
        assertThat(api.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(10);
    }

    @Test
    void aWriteFailureAfterTheSqlRollsBackAndCannotCreateAnAudit() {
        long count = fixture.auditCount(BILLING_FREE_MONTHLY_LIMIT);
        doAnswer(call -> { call.callRealMethod(); throw new DataAccessResourceFailureException("write-fixture"); })
                .when(store).put(eq(BILLING_FREE_MONTHLY_LIMIT), anyInt(), anyLong());
        assertThatThrownBy(() -> change(BILLING_FREE_MONTHLY_LIMIT, 25)).isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shared.app_settings WHERE key=?", Integer.class, BILLING_FREE_MONTHLY_LIMIT.key())).isZero();
        assertThat(fixture.auditCount(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(count);
        verifyNoInteractions(audit);
        verify(settings, never()).invalidate(any());
    }

    @Test
    void auditFailureDuringResetRestoresTheExistingOverride() {
        change(BILLING_FREE_MONTHLY_LIMIT, 25);
        long count = fixture.auditCount(BILLING_FREE_MONTHLY_LIMIT);
        assertThat(api.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(25);
        clearInvocations(settings);
        doThrow(new IllegalStateException("reset-audit-fixture")).when(audit)
                .recordDetail(eq(fixture.actor), eq("SETTING_RESET"), eq("SETTING"), anyLong(), anyMap());
        assertThatThrownBy(() -> service.reset(BILLING_FREE_MONTHLY_LIMIT.key(), fixture.actor, () -> new SettingReset("Về mặc định")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT value FROM shared.app_settings WHERE key=?", String.class, BILLING_FREE_MONTHLY_LIMIT.key())).isEqualTo("25");
        assertThat(fixture.auditCount(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(count);
        verify(settings, never()).invalidate(any());
        assertThat(api.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(25);
    }

    @Test
    void resettingMaxCannotMakeTheEffectiveRangeInvalid() {
        change(GRADING_MAX_HANZI, 700);
        change(GRADING_MIN_HANZI, 600);
        long count = fixture.auditCount(GRADING_MAX_HANZI);
        clearInvocations(settings);
        assertThatThrownBy(() -> service.reset(GRADING_MAX_HANZI.key(), fixture.actor, () -> new SettingReset("Thử")))
                .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.SETTING_OUT_OF_RANGE));
        assertThat(api.getInt(GRADING_MAX_HANZI)).isEqualTo(700);
        assertThat(fixture.auditCount(GRADING_MAX_HANZI)).isEqualTo(count);
        verify(settings, never()).invalidate(any());
        service.reset(GRADING_MIN_HANZI.key(), fixture.actor, () -> new SettingReset("Khôi phục min"));
        assertThat(api.getInt(GRADING_MIN_HANZI)).isEqualTo(50);
    }

    @Test
    void sensitiveDatabaseRowsNeverAppearOrBecomeWritableAndCorruptKnownValuesFallBack() throws Exception {
        jdbc.update("INSERT INTO shared.app_settings(key,value,updated_by) VALUES ('jwt.secret','private-sentinel',?)", fixture.actor);
        jdbc.update("INSERT INTO shared.app_settings(key,value,updated_by) VALUES (?, 'invalid-sentinel',?)", BILLING_FREE_MONTHLY_LIMIT.key(), fixture.actor);
        var response = mvc.perform(get("/api/admin/settings").header("Authorization", bearer())).andExpect(status().isOk()).andReturn();
        String body = response.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("jwt.secret", "private-sentinel", "invalid-sentinel", "@example.test");
        assertThat(service.list().get(0).currentValue()).isEqualTo(10);
        for (String key : List.of("jwt.secret", "ai.api-key", "billing.card-code-pepper")) {
            mvc.perform(put("/api/admin/settings/" + key).header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON).content("{"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code").value("SETTING_NOT_FOUND"));
            mvc.perform(delete("/api/admin/settings/" + key).header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON).content("{"))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/admin/settings/" + key + "/history").header("Authorization", bearer())).andExpect(status().isNotFound());
        }
        change(BILLING_FREE_MONTHLY_LIMIT, 12);
        assertThat(api.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(12);
    }

    @Test
    void historyUsesStableTargetsActionsPaginationAndCurrentActorNames() {
        change(BILLING_FREE_MONTHLY_LIMIT, 20);
        long other = fixture.user("Quản trị khác");
        service.change(BILLING_FREE_MONTHLY_LIMIT.key(), other, () -> new SettingWrite(30, "Hai"));
        service.reset(BILLING_FREE_MONTHLY_LIMIT.key(), fixture.actor, () -> new SettingReset("Ba"));
        change(ASSISTANT_DAILY_LIMIT, 50);
        audit.recordDetail(fixture.actor, "OTHER_ACTION", "SETTING", BILLING_FREE_MONTHLY_LIMIT.auditId(), Map.of("reason", "Không được hiện"));
        audit.recordDetail(fixture.actor, "SETTING_CHANGE", "OTHER_TYPE", BILLING_FREE_MONTHLY_LIMIT.auditId(), Map.of("reason", "Không được hiện"));
        jdbc.update("UPDATE shared.audit_logs SET created_at='2100-01-01T00:00:00Z' WHERE actor_id IN (?,?)", fixture.actor, other);
        jdbc.update("UPDATE auth.users SET display_name='Tên mới' WHERE id=?", other);
        var first = service.history(BILLING_FREE_MONTHLY_LIMIT.key(), 0, 2);
        assertThat(first.items()).extracting(SettingHistoryEntry::reason).containsExactly("Ba", "Hai");
        assertThat(first.items().get(0).newValue()).isNull();
        assertThat(first.items().get(1).actor()).isEqualTo(new UserRef(other, "Tên mới"));
        var next = service.history(BILLING_FREE_MONTHLY_LIMIT.key(), 1, 2);
        assertThat(next.items().get(0).newValue()).isEqualTo(20);
        assertThat(service.history(ASSISTANT_DAILY_LIMIT.key(), 0, 50).items().get(0).newValue()).isEqualTo(50);
        assertThat(service.history(BILLING_FREE_MONTHLY_LIMIT.key(), Integer.MAX_VALUE, 50).items()).isEmpty();
    }

    private SettingItem change(SettingKey key, int value) { return service.change(key.key(), fixture.actor, () -> new SettingWrite(value, "Lý do kiểm thử")); }
    private String bearer() { return "Bearer " + jwt.issueAccessToken(fixture.actor, List.of("ADMIN"), true, UUID.randomUUID()); }
    private String path(SettingKey key) { return "/api/admin/settings/" + key.key(); }
    private JsonNode json(byte[] value) throws Exception { return mapper.readTree(value); }
}

class SettingsAdminFixture implements AutoCloseable {
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private static final List<String> KEYS = List.of(BILLING_FREE_MONTHLY_LIMIT.key(), ASSISTANT_DAILY_LIMIT.key(),
            GRADING_MIN_HANZI.key(), GRADING_MAX_HANZI.key(), "jwt.secret");
    private final JdbcTemplate jdbc;
    private final SettingsService settings;
    private final List<Map<String, Object>> saved;
    private final List<Long> users = new ArrayList<>();
    final long actor;

    SettingsAdminFixture(JdbcTemplate jdbc, SettingsService settings) {
        this.jdbc = jdbc;
        this.settings = settings;
        var named = new NamedParameterJdbcTemplate(jdbc);
        saved = named.queryForList("SELECT * FROM shared.app_settings WHERE key IN (:keys)", Map.of("keys", KEYS));
        named.update("DELETE FROM shared.app_settings WHERE key IN (:keys)", Map.of("keys", KEYS));
        invalidate();
        actor = user("Quản trị cài đặt");
    }

    long user(String name) {
        long id = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,?) RETURNING id", Long.class,
                "settings-admin-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH, name);
        users.add(id);
        return id;
    }

    long auditCount(SettingKey key) {
        return jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs WHERE target_type='SETTING' AND target_id=? "
                + "AND action IN ('SETTING_CHANGE','SETTING_RESET')", Long.class, key.auditId());
    }

    private void invalidate() {
        for (SettingKey key : List.of(BILLING_FREE_MONTHLY_LIMIT, ASSISTANT_DAILY_LIMIT, GRADING_MIN_HANZI, GRADING_MAX_HANZI)) settings.invalidate(key);
    }

    @Override public void close() {
        var named = new NamedParameterJdbcTemplate(jdbc);
        named.update("DELETE FROM shared.audit_logs WHERE actor_id IN (:ids)", Map.of("ids", users));
        named.update("DELETE FROM shared.app_settings WHERE key IN (:keys)", Map.of("keys", KEYS));
        for (Map<String, Object> row : saved) jdbc.update("INSERT INTO shared.app_settings(key,value,updated_by,updated_at) VALUES (?,?,?,?)",
                row.get("key"), row.get("value"), row.get("updated_by"), row.get("updated_at"));
        named.update("DELETE FROM auth.users WHERE id IN (:ids)", Map.of("ids", users));
        invalidate();
    }
}
