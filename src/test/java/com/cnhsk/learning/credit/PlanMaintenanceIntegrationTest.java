package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cnhsk.learning.credit.LedgerService.AppendCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class PlanMaintenanceIntegrationTest extends PlanMaintenanceIntegrationSupport {
    @Autowired private PlanMaintenance scheduledMaintenance;
    @Autowired private ScheduledAnnotationBeanPostProcessor scheduling;
    @Autowired private Environment environment;
    @Autowired private ApplicationContext context;
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @Test
    void downgradesTwoExpiredSubscriptionsAndLeavesTheActiveUserEntirelyUnchanged() {
        long earlier = user("PREMIUM", NOW.minusSeconds(86400));
        long equal = user("PREMIUM", NOW);
        long active = user("PREMIUM", NOW.plusSeconds(1));
        Map<String, Object> earlierBefore = userRow(earlier);
        Map<String, Object> equalBefore = userRow(equal);
        Map<String, Object> activeBefore = userRow(active);

        maintenance().downgradeExpiredPlans();

        assertDowngraded(earlier, earlierBefore, NOW);
        assertDowngraded(equal, equalBefore, NOW);
        assertThat(userRow(active)).isEqualTo(activeBefore);
        assertThat(state(earlier).planCode()).isEqualTo("FREE");
        assertThat(state(equal).planExpiresAt()).isNull();
        assertThat(state(active).planExpiresAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2035-11-15T17:00:00Z", "2035-11-15T17:10:00.123456Z"})
    void usesAnInclusiveMicrosecondCutoffAcrossVietnamMidnight(String instant) {
        Instant cutoff = Instant.parse(instant);
        long before = user("PREMIUM", cutoff.minusNanos(1000));
        long equal = user("PREMIUM", cutoff);
        long after = user("PREMIUM", cutoff.plusNanos(1000));
        Map<String, Object> beforeRow = userRow(before);
        Map<String, Object> equalRow = userRow(equal);
        Map<String, Object> futureRow = userRow(after);

        new PlanMaintenance(auth, Clock.fixed(cutoff, ZoneOffset.UTC)).downgradeExpiredPlans();

        assertDowngraded(before, beforeRow, cutoff);
        assertDowngraded(equal, equalRow, cutoff);
        assertThat(userRow(after)).isEqualTo(futureRow);
    }

    @Test
    void eligibilityIgnoresRolesAccountStatusVerificationAndCatalogAvailability() {
        long free = user("FREE", null);
        long active = user("PREMIUM_PLUS", NOW.plusSeconds(1));
        Map<String, Object> freeBefore = userRow(free);
        Map<String, Object> activeBefore = userRow(active);
        List<Long> expired = new ArrayList<>();
        for (String role : List.of("LEARNER", "TEACHER", "MODERATOR", "CONTENT_EDITOR", "ACCOUNTANT", "ADMIN")) {
            long id = createUser(role);
            plan(id, "PREMIUM", NOW.minusSeconds(1));
            expired.add(id);
        }
        long noRoles = createUser();
        plan(noRoles, "PREMIUM_PLUS", NOW);
        expired.add(noRoles);
        jdbc.update("UPDATE auth.users SET email_verified_at=NULL WHERE id=?", expired.get(0));
        jdbc.update("UPDATE auth.users SET suspended_at=?,suspended_until=? WHERE id=?",
                Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(NOW.plusSeconds(86400)), expired.get(1));
        jdbc.update("UPDATE auth.users SET banned_at=?,ban_reason='Fixture restriction' WHERE id=?",
                Timestamp.from(NOW.minusSeconds(60)), expired.get(2));
        jdbc.update("UPDATE learning.plans SET is_active=false WHERE code IN ('PREMIUM','PREMIUM_PLUS')");
        List<Map<String, Object>> catalogBeforeJob = catalogRows();
        Map<Long, Map<String, Object>> before = new LinkedHashMap<>();
        expired.forEach(id -> before.put(id, userRow(id)));

        maintenance().downgradeExpiredPlans();

        expired.forEach(id -> assertDowngraded(id, before.get(id), NOW));
        assertThat(userRow(free)).isEqualTo(freeBefore);
        assertThat(userRow(active)).isEqualTo(activeBefore);
        assertThat(catalogRows()).isEqualTo(catalogBeforeJob);
    }

    @Test
    void preservesUsageWalletHistoryCardsRolesCatalogAuditAndEffectiveEntitlement() {
        long learner = user("PREMIUM", NOW);
        long administrator = createUser("ADMIN");
        usage(learner, Map.of("TRANSLATE", 8, "GRADING", 4), Instant.parse("2035-10-31T17:00:00Z"));
        IssuedCard used = card("CREDIT", 25, administrator);
        card("SUBSCRIPTION", 30, administrator);
        tx(() -> {
            jdbc.update("UPDATE learning.credit_cards SET status='USED',used_by=?,used_at=? WHERE id=?",
                    learner, Timestamp.from(NOW.minusSeconds(3600)), used.id());
            ledger.append(new AppendCommand(learner, "TOPUP", 25, null, used.id(), null, null, null, null, null));
        });
        audit.recordDetail(administrator, "PLAN_UPDATED", "PLAN", Map.of("code", "PREMIUM"));
        WalletQueryService wallet = new WalletQueryService(auth, ledger, named, mapper, config, FIXED_CLOCK);
        var walletBefore = asLearner(learner, wallet::wallet);
        assertThat(walletBefore.plan().code().name()).isEqualTo("FREE");
        Map<String, Object> beforeUser = userRow(learner);
        var before = preservedState();
        String receiptKey = "billing:idem:" + learner + ":preserved";
        redis.opsForValue().set(receiptKey, "fixture-receipt");

        maintenance().downgradeExpiredPlans();

        assertDowngraded(learner, beforeUser, NOW);
        assertThat(preservedState()).isEqualTo(before);
        assertThat(ledger.balance(learner)).isEqualTo(25);
        assertThat(asLearner(learner, wallet::wallet)).isEqualTo(walletBefore);
        assertThat(redis.opsForValue().get(receiptKey)).isEqualTo("fixture-receipt");
    }

    @Test
    void repeatedRunsDoNotRewriteFreeUsersAndAnAdvancingCutoffOnlyChangesNewExpiries() {
        long first = user("PREMIUM", NOW.minusSeconds(1));
        long later = user("PREMIUM", NOW.plusSeconds(60));
        long future = user("PREMIUM_PLUS", NOW.plusSeconds(120));
        Map<String, Object> laterBefore = userRow(later);
        Map<String, Object> futureBefore = userRow(future);

        maintenance().downgradeExpiredPlans();
        Map<String, Object> firstCommitted = userRow(first);
        List<Map<String, Object>> firstRun = userRows();
        var preserved = preservedState();
        maintenance().downgradeExpiredPlans();
        assertThat(userRows()).isEqualTo(firstRun);
        Instant nextCutoff = NOW.plusSeconds(60);
        new PlanMaintenance(auth, Clock.fixed(nextCutoff, ZoneOffset.UTC)).downgradeExpiredPlans();

        assertThat(userRow(first)).isEqualTo(firstCommitted);
        assertDowngraded(later, laterBefore, nextCutoff);
        assertThat(userRow(future)).isEqualTo(futureBefore);
        assertThat(preservedState()).isEqualTo(preserved);
    }

    @Test
    void participatesInAnOuterTransactionAndRollsBackEveryUserOnFailure() {
        user("PREMIUM", NOW.minusSeconds(1));
        user("PREMIUM_PLUS", NOW);
        var before = userRows();
        IllegalStateException failure = new IllegalStateException("Abort caller transaction");

        assertThatThrownBy(() -> tx((Runnable) () -> {
            maintenance().downgradeExpiredPlans();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM auth.users WHERE plan_code='FREE'", Integer.class))
                    .isEqualTo(2);
            throw failure;
        })).isSameAs(failure);

        assertThat(userRows()).isEqualTo(before);
    }

    @Test
    void realForeignKeyFailureRollsBackTheWholeBatchAndTheCallersCatalogChange() {
        user("PREMIUM", NOW.minusSeconds(1));
        user("PREMIUM_PLUS", NOW);
        var usersBefore = userRows();
        var catalogBeforeJob = catalogRows();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth.users WHERE plan_code='FREE'", Integer.class)).isZero();

        assertThatThrownBy(() -> tx((Runnable) () -> {
            assertThat(jdbc.update("DELETE FROM learning.plans WHERE code='FREE'")).isEqualTo(1);
            maintenance().downgradeExpiredPlans();
        })).isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().isInstanceOf(java.sql.SQLException.class)
                .satisfies(error -> assertThat(((java.sql.SQLException) error).getSQLState()).isEqualTo("23503"));

        assertThat(userRows()).isEqualTo(usersBefore);
        assertThat(catalogRows()).isEqualTo(catalogBeforeJob);
    }

    @Test
    void rejectsAReadOnlyCallerWithoutChangingAnyEligibleUser() {
        user("PREMIUM", NOW.minusSeconds(1));
        user("PREMIUM_PLUS", NOW);
        var before = userRows();
        TransactionTemplate readOnly = new TransactionTemplate(manager);
        readOnly.setReadOnly(true);

        assertThatThrownBy(() -> readOnly.executeWithoutResult(status -> maintenance().downgradeExpiredPlans()))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(userRows()).isEqualTo(before);
    }

    @Test
    void testProfileDisablesSchedulingWhileTheInternalBeanRemainsCallableWithoutAnActor() throws Exception {
        long expired = user("PREMIUM", Instant.EPOCH);
        List<Long> usersBefore = jdbc.queryForList("SELECT id FROM auth.users ORDER BY id", Long.class);
        assertThat(environment.getActiveProfiles()).contains("test");
        Scheduled annotation = PlanMaintenance.class.getMethod("downgradeExpiredPlans").getAnnotation(Scheduled.class);
        assertThat(environment.getProperty("cnhsk.billing.downgrade-cron")).isEqualTo(Scheduled.CRON_DISABLED);
        assertThat(environment.resolveRequiredPlaceholders(annotation.cron())).isEqualTo(Scheduled.CRON_DISABLED);
        assertThat(context.getBeansOfType(PlanMaintenance.class)).hasSize(1);
        assertThat(scheduling.getScheduledTasks()).noneMatch(task ->
                task.getTask().getRunnable() instanceof ScheduledMethodRunnable runnable
                        && runnable.getTarget() instanceof PlanMaintenance);
        assertThat(mappings.getHandlerMethods().values()).noneMatch(handler ->
                PlanMaintenance.class.isAssignableFrom(handler.getBeanType()));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        Logger logger = (Logger) LoggerFactory.getLogger(PlanMaintenance.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            scheduledMaintenance.downgradeExpiredPlans();
            assertThat(logs.list).isEmpty();
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }

        assertThat(state(expired).planCode()).isEqualTo("FREE");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(jdbc.queryForList("SELECT id FROM auth.users ORDER BY id", Long.class)).isEqualTo(usersBefore);
        assertThat(jdbc.queryForList("SELECT DISTINCT role_code FROM auth.user_roles", String.class)).containsExactly("LEARNER");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs", Integer.class)).isZero();
    }

    @Test
    void bulkRunDowngradesOneThousandExpiredUsersAndPreservesControls() {
        long free = user("FREE", null);
        long active = user("PREMIUM", NOW.plusSeconds(86400));
        Map<String, Object> freeBefore = userRow(free);
        Map<String, Object> activeBefore = userRow(active);
        List<Long> expired = jdbc.queryForList("""
                INSERT INTO auth.users(email,password_hash,display_name,plan_code,plan_expires_at,usage_reset_at,updated_at)
                SELECT ? || n || '@example.test',?,'Bulk maintenance fixture','PREMIUM',?,?,?
                FROM generate_series(1,1000) AS n RETURNING id
                """, Long.class, "maintenance-bulk-" + UUID.randomUUID() + "-", PASSWORD_HASH,
                Timestamp.from(NOW.minusSeconds(1)), Timestamp.from(NOW.minusSeconds(86400)),
                Timestamp.from(NOW.minusSeconds(3600)));
        userIds.addAll(expired);
        named.update("INSERT INTO auth.user_roles(user_id,role_code) SELECT id,'LEARNER' FROM auth.users WHERE id IN (:ids)",
                Map.of("ids", expired));
        var preserved = preservedState();

        long started = System.nanoTime();
        maintenance().downgradeExpiredPlans();
        long elapsedNanos = System.nanoTime() - started;

        assertThat(expired).hasSize(1000);
        assertThat(named.queryForObject("""
                SELECT count(*) FROM auth.users WHERE id IN (:ids)
                AND plan_code='FREE' AND plan_expires_at IS NULL AND updated_at=:cutoff
                """, Map.of("ids", expired, "cutoff", Timestamp.from(NOW)), Integer.class)).isEqualTo(1000);
        assertThat(userRow(free)).isEqualTo(freeBefore);
        assertThat(userRow(active)).isEqualTo(activeBefore);
        assertThat(preservedState()).isEqualTo(preserved);
        System.out.println("PLAN_MAINTENANCE_BULK expiredRows=1000 controlRows=2 elapsedNanos=" + elapsedNanos);
    }

    private Map<String, Object> userRow(long id) {
        return jdbc.queryForMap("SELECT * FROM auth.users WHERE id=?", id);
    }

    private List<Map<String, Object>> userRows() {
        return jdbc.queryForList("SELECT * FROM auth.users ORDER BY id");
    }

    private void assertDowngraded(long id, Map<String, Object> before, Instant cutoff) {
        Map<String, Object> expected = new LinkedHashMap<>(before);
        expected.put("plan_code", "FREE");
        expected.put("plan_expires_at", null);
        expected.put("updated_at", Timestamp.from(cutoff));
        assertThat(userRow(id)).isEqualTo(expected);
    }

    private PreservedState preservedState() {
        return new PreservedState(jdbc.queryForList("""
                SELECT (to_jsonb(u)-'plan_code'-'plan_expires_at'-'updated_at')::text AS preserved
                FROM auth.users u ORDER BY id
                """), jdbc.queryForList("SELECT * FROM auth.user_roles ORDER BY user_id,role_code"),
                catalogRows(), jdbc.queryForList("SELECT * FROM learning.credit_transactions ORDER BY id"),
                jdbc.queryForList("SELECT * FROM learning.credit_cards ORDER BY id"),
                jdbc.queryForList("SELECT * FROM shared.audit_logs ORDER BY id"));
    }

    private record PreservedState(List<Map<String, Object>> users, List<Map<String, Object>> roles,
            List<Map<String, Object>> catalog, List<Map<String, Object>> ledger,
            List<Map<String, Object>> cards, List<Map<String, Object>> audit) {}
}
