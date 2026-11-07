package com.cnhsk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.PlanState;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.support.AbstractIntegrationTest;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.util.Masking;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

class AuthPlanApiIntegrationTest extends AbstractIntegrationTest {

    private static final Instant EXPIRY = Instant.parse("2030-12-01T00:00:00Z");
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());

    @Autowired private AuthApi auth;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager manager;
    private long userId;
    private final List<Long> createdIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        jdbc.update("""
                INSERT INTO learning.plans(code, name, duration_days) VALUES ('PREMIUM', 'Test Premium', 30)
                ON CONFLICT (code) DO NOTHING
                """);
        userId = users.saveAndFlush(new User("plan-" + UUID.randomUUID() + "@example.test",
                PASSWORD_HASH, "Plan test")).getId();
        createdIds.add(userId);
    }

    @AfterEach
    void cleanUp() {
        users.deleteAllById(createdIds);
    }

    @Test
    void staleUserFlushDoesNotOverwriteJdbcPlanUpdate() {
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            User stale = users.findById(userId).orElseThrow();
            assertThat(stale.getPlanCode()).isEqualTo("FREE");
            auth.lockPlan(userId);
            auth.updatePlan(userId, "PREMIUM", EXPIRY);
            stale.setDisplayName("Updated profile");
            users.saveAndFlush(stale);
        });

        assertThat(jdbc.queryForObject("SELECT plan_code FROM auth.users WHERE id = ?", String.class, userId))
                .isEqualTo("PREMIUM");
        assertThat(jdbc.queryForObject("SELECT plan_expires_at FROM auth.users WHERE id = ?", Timestamp.class, userId)
                .toInstant()).isEqualTo(EXPIRY);
        assertThat(users.findById(userId).orElseThrow().getDisplayName()).isEqualTo("Updated profile");
    }

    @Test
    void lockedSnapshotReflectsJdbcPlanAndUsageUpdates() {
        Instant reset = Instant.parse("2026-11-01T00:00:00Z");
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            PlanState before = auth.lockPlan(userId);
            assertThat(before.planCode()).isEqualTo("FREE");
            assertThat(before.freeUsage()).isEmpty();
            auth.updatePlan(userId, "PREMIUM", EXPIRY);
            auth.updateUsage(userId, Map.of("TRANSLATE", 3), reset);
            PlanState after = auth.lockPlan(userId);
            assertThat(after.planCode()).isEqualTo("PREMIUM");
            assertThat(after.planExpiresAt()).isEqualTo(EXPIRY);
            assertThat(after.usageResetAt()).isEqualTo(reset);
            assertThat(after.freeUsage()).containsExactlyEntriesOf(Map.of("TRANSLATE", 3));
            assertThat(before.freeUsage()).isEmpty();
            assertThatThrownBy(() -> after.freeUsage().put("TRANSLATE", 4))
                    .isInstanceOf(UnsupportedOperationException.class);
            auth.updatePlan(userId, "FREE", null);
            assertThat(auth.lockPlan(userId).planExpiresAt()).isNull();
        });
    }

    @Test
    void planAndUsageChangesRollBackTogether() {
        PlanState before = new TransactionTemplate(manager).execute(status -> auth.lockPlan(userId));
        assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
            auth.lockPlan(userId);
            auth.updatePlan(userId, "PREMIUM", EXPIRY);
            auth.updateUsage(userId, Map.of("TRANSLATE", 7), Instant.EPOCH);
            throw new IllegalStateException("Rollback requested.");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Rollback requested.");
        PlanState after = new TransactionTemplate(manager).execute(status -> auth.lockPlan(userId));
        assertThat(after).isEqualTo(before);
    }

    @Test
    void lockingAndUpdatesRequireAnExistingWritableTransaction() {
        List<Runnable> writes = List.of(() -> auth.lockPlan(userId),
                () -> auth.updatePlan(userId, "PREMIUM", EXPIRY),
                () -> auth.updateUsage(userId, Map.of(), Instant.EPOCH));
        for (Runnable write : writes) {
            assertThatThrownBy(write::run).isInstanceOf(IllegalTransactionStateException.class);
            TransactionTemplate readOnly = new TransactionTemplate(manager);
            readOnly.setReadOnly(true);
            assertThatThrownBy(() -> readOnly.executeWithoutResult(status -> write.run()))
                    .isInstanceOf(IllegalTransactionStateException.class);
        }
    }

    @Test
    void missingUsersHaveTheExistingNotFoundError() {
        long missing = Long.MAX_VALUE;
        for (Runnable call : List.<Runnable>of(() -> auth.lockPlan(missing),
                () -> auth.updatePlan(missing, "PREMIUM", EXPIRY),
                () -> auth.updateUsage(missing, Map.of(), Instant.EPOCH))) {
            assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(status -> call.run()))
                    .isInstanceOf(ApiException.class).satisfies(error -> {
                        ApiException apiError = (ApiException) error;
                        assertThat(apiError.getCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
                        assertThat(apiError.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(apiError.getMessage()).isEqualTo("Không tìm thấy tài khoản.");
                    });
        }
    }

    @Test
    void invalidWriteArgumentsHaveAFixedMessage() {
        for (Runnable call : List.<Runnable>of(() -> auth.lockPlan(0),
                () -> auth.updatePlan(userId, null, EXPIRY),
                () -> auth.updatePlan(userId, "UNKNOWN", EXPIRY),
                () -> auth.updatePlan(userId, "FREE", EXPIRY),
                () -> auth.updatePlan(userId, "PREMIUM", null),
                () -> auth.updateUsage(userId, null, Instant.EPOCH),
                () -> auth.updateUsage(userId, Map.of(), null),
                () -> auth.updateUsage(userId, Map.of("TRANSLATE", -1), Instant.EPOCH),
                () -> auth.downgradeExpiredPlans(null))) {
            assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(status -> call.run()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Tham số không hợp lệ.");
        }
    }

    @Test
    void maskedEmailsUseSharedMaskingAndOmitUnknownUsers() {
        String email = users.findById(userId).orElseThrow().getEmail();
        Map<Long, String> result = auth.maskedEmails(Set.of(userId, Long.MAX_VALUE));
        assertThat(result).containsExactlyEntriesOf(Map.of(userId, Masking.email(email)));
        assertThat(result.get(userId)).isNotEqualTo(email);
        assertThat(auth.maskedEmails(Set.of())).isEmpty();
    }

    @Test
    void activeCountsIncludeExpiredPlansInFreeWithoutRoleOrStatusFilters() {
        Instant now = Instant.parse("2026-11-07T00:00:00Z");
        long premiumBefore = auth.countActivePlan("PREMIUM", now);
        long freeBefore = auth.countActivePlan("FREE", now);
        long active = additionalUser("PREMIUM", now.plusSeconds(1));
        additionalUser("PREMIUM", now.minusSeconds(1));
        additionalUser("PREMIUM", now);
        jdbc.update("""
                INSERT INTO learning.plans(code, name, duration_days) VALUES ('PREMIUM_PLUS', 'Test Plus', 30)
                ON CONFLICT (code) DO NOTHING
                """);
        additionalUser("PREMIUM_PLUS", now.plusSeconds(1));
        additionalUser("PREMIUM_PLUS", now.minusSeconds(1));
        jdbc.update("UPDATE auth.users SET banned_at = ?, ban_reason = 'Test status' WHERE id = ?",
                Timestamp.from(now), active);

        assertThat(auth.countActivePlan("PREMIUM", now)).isEqualTo(premiumBefore + 1);
        assertThat(auth.countActivePlan("FREE", now)).isEqualTo(freeBefore + 3);
        assertThat(auth.countActivePlan("PREMIUM_PLUS", now)).isZero();
        assertThat(auth.countActivePlan("UNKNOWN", now)).isZero();
    }

    @Test
    void downgradeChangesOnlyExpiredPaidPlansIncludingTheBoundary() {
        Instant now = Instant.parse("2026-11-07T00:00:00Z");
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            int existing = jdbc.queryForObject("""
                    SELECT count(*) FROM auth.users WHERE plan_code <> 'FREE' AND plan_expires_at <= ?
                    """, Integer.class, Timestamp.from(now));
            long expired = additionalUser("PREMIUM", now.minusSeconds(1));
            long boundary = additionalUser("PREMIUM", now);
            long active = additionalUser("PREMIUM", now.plusSeconds(1));
            assertThat(auth.downgradeExpiredPlans(now)).isEqualTo(existing + 2);
            assertThat(auth.lockPlan(expired).planCode()).isEqualTo("FREE");
            assertThat(auth.lockPlan(boundary).planExpiresAt()).isNull();
            assertThat(auth.lockPlan(active).planCode()).isEqualTo("PREMIUM");
            assertThat(auth.lockPlan(userId).planCode()).isEqualTo("FREE");
            status.setRollbackOnly();
        });
    }

    @Test
    void corruptStoredUsageIsRejectedWithoutEchoingItsValue() {
        jdbc.update("UPDATE auth.users SET free_usage = '{\"TRANSLATE\":\"invalid-counter\"}'::jsonb WHERE id = ?", userId);
        assertThatThrownBy(() -> new TransactionTemplate(manager).execute(status -> auth.lockPlan(userId)))
                .isInstanceOf(IllegalStateException.class).hasMessage("Dữ liệu hạn mức không hợp lệ.");
    }

    private long additionalUser(String code, Instant expiry) {
        long id = jdbc.queryForObject("""
                INSERT INTO auth.users(email, password_hash, display_name, plan_code, plan_expires_at)
                VALUES (?, ?, 'Count test', ?, ?) RETURNING id
                """, Long.class, "plan-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH,
                code, Timestamp.from(expiry));
        createdIds.add(id);
        return id;
    }
}
