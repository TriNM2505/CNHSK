package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.PlanState;
import com.cnhsk.learning.api.Consumption;
import com.cnhsk.learning.api.Consumption.Source;
import com.cnhsk.learning.api.EntitlementApi;
import com.cnhsk.learning.api.PaidFeature;
import com.cnhsk.learning.credit.LedgerService.AppendCommand;
import com.cnhsk.learning.credit.LedgerService.LedgerEntry;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class EntitlementServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private static final Instant NOW = Instant.parse("2031-11-15T10:00:00Z");
    private static final Instant MONTH = Instant.parse("2031-10-31T17:00:00Z");

    @Autowired private EntitlementApi entitlement;
    @Autowired private AuthApi auth;
    @Autowired private LedgerService ledger;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NamedParameterJdbcTemplate namedJdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper mapper;
    @Autowired private PlatformTransactionManager manager;
    private long userId;
    private long otherUserId;
    private EntitlementService service;
    private final List<String> createdPremiumCodes = new ArrayList<>();
    private List<Map<String, Object>> premiumBefore;

    @BeforeEach
    void setUp() {
        premiumBefore = premiumRows();
        userId = createUser();
        otherUserId = createUser();
        service = serviceAt(NOW);
        usage(Map.of(), MONTH);
    }

    @AfterEach
    void cleanUp() {
        for (long id : new long[] {userId, otherUserId}) {
            var keys = redis.keys("billing:*:" + id + ":*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
            jdbc.update("DELETE FROM learning.credit_transactions WHERE user_id = ?", id);
            jdbc.update("DELETE FROM auth.users WHERE id = ?", id);
        }
        cleanUpOwnedPremium(createdPremiumCodes);
        assertThat(premiumRows()).isEqualTo(premiumBefore);
    }

    @Test
    void ownedPremiumCleanupRestoresThePlanSnapshotAfterUsersAndTransactions() {
        seedPremium();
        assertThat(createdPremiumCodes).hasSize(premiumBefore.isEmpty() ? 1 : 0);
        transaction().executeWithoutResult(status -> {
            auth.lockPlan(userId);
            auth.updatePlan(userId, "PREMIUM", NOW.plusSeconds(86400));
        });
        append(userId, "TOPUP", 1, null, null);

        cleanUp();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.credit_transactions WHERE user_id = ?",
                Integer.class, userId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth.users WHERE id IN (?, ?)",
                Integer.class, userId, otherUserId)).isZero();
        assertThat(createdPremiumCodes).isEmpty();
        assertThat(premiumRows()).isEqualTo(premiumBefore);
    }

    @Test
    void conflictingPremiumSeedPreservesTheRowAndDoesNotClaimAnotherFixturesOwnership() {
        seedPremium();
        List<Map<String, Object>> beforeConflict = premiumRows();
        List<String> ownedBefore = List.copyOf(createdPremiumCodes);

        seedPremium();

        assertThat(premiumRows()).isEqualTo(beforeConflict);
        assertThat(createdPremiumCodes).containsExactlyElementsOf(ownedBefore);
        List<String> anotherFixturesCodes = new ArrayList<>();
        seedPremium(anotherFixturesCodes);
        assertThat(anotherFixturesCodes).isEmpty();

        cleanUpOwnedPremium(anotherFixturesCodes);

        assertThat(premiumRows()).isEqualTo(beforeConflict);
        assertThat(createdPremiumCodes).containsExactlyElementsOf(ownedBefore);
    }

    @Test
    void freeQuotaThenCreditThenInsufficientCredits() {
        append(userId, "TOPUP", 1, null, null);
        for (int use = 1; use <= 10; use++) {
            Consumption receipt = consume("free-" + use);
            assertThat(receipt.source()).isEqualTo(Source.QUOTA);
            assertThat(receipt.transactionId()).isNull();
            assertThat(receipt.periodStart()).isEqualTo(MONTH);
        }
        Consumption paid = consume("paid");
        assertThat(paid.source()).isEqualTo(Source.CREDIT);
        assertThat(jdbc.queryForMap("""
                SELECT tx_type, amount, feature_code, balance_before, balance_after
                FROM learning.credit_transactions WHERE id = ?
                """, paid.transactionId())).containsEntry("tx_type", "DEDUCT").containsEntry("amount", -1)
                .containsEntry("feature_code", "TRANSLATE").containsEntry("balance_before", 1)
                .containsEntry("balance_after", 0);
        assertThatThrownBy(() -> consume("exhausted")).isInstanceOf(ApiException.class).satisfies(error -> {
            ApiException api = (ApiException) error;
            assertThat(api.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_CREDITS);
            assertThat(api.getStatus()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
            assertThat(api.getMessage()).isEqualTo("Đã hết hạn mức và điểm.");
        });
        assertThat(state().freeUsage()).containsExactlyEntriesOf(Map.of("TRANSLATE", 10));
        assertThat(ledger.balance(userId)).isZero();
        assertThat(countTransactions()).isEqualTo(2);
        assertThat(redis.hasKey(idemKey("exhausted"))).isFalse();
    }

    @Test
    void configuredFreeLimitAppliesIndependentlyToEveryFeature() {
        BillingConfig config = new BillingConfig();
        config.setFreeMonthlyLimit(2);
        EntitlementService configured = new EntitlementService(auth, ledger, namedJdbc, redis, mapper,
                config, Clock.fixed(NOW, ZoneOffset.UTC));
        for (PaidFeature feature : PaidFeature.values()) {
            for (int use = 0; use < 2; use++) {
                String key = feature.name() + use;
                assertThat(transaction().execute(status -> configured.consume(userId, feature, key)).source())
                        .isEqualTo(Source.QUOTA);
            }
        }
        assertThat(state().freeUsage()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "AI_GENERATE", 2, "TRANSLATE", 2, "ASSISTANT", 2, "GRADING", 2));
        assertThat(countTransactions()).isZero();
    }

    @Test
    void premiumUsesOnlyGrantedBenefitsIncludingZeroAndMissingFeatures() {
        seedPremium();
        append(userId, "TOPUP", 2, null, null);
        transaction().executeWithoutResult(status -> {
            status.setRollbackOnly();
            jdbc.queryForObject("SELECT code FROM learning.plans WHERE code = 'PREMIUM' FOR UPDATE", String.class);
            jdbc.update("UPDATE learning.plans SET benefits = CAST(? AS jsonb), is_active = false WHERE code = 'PREMIUM'",
                    "{\"TRANSLATE\":1,\"GRADING\":0}");
            auth.lockPlan(userId);
            auth.updatePlan(userId, "PREMIUM", NOW.plusSeconds(86400));
            assertThat(service.consume(userId, PaidFeature.TRANSLATE, "premium-quota").source()).isEqualTo(Source.QUOTA);
            assertThat(service.consume(userId, PaidFeature.TRANSLATE, "premium-credit").source()).isEqualTo(Source.CREDIT);
            assertThat(service.consume(userId, PaidFeature.GRADING, "premium-zero").source()).isEqualTo(Source.CREDIT);
            assertThatThrownBy(() -> service.consume(userId, PaidFeature.ASSISTANT, "premium-missing"))
                    .isInstanceOf(ApiException.class).satisfies(error ->
                            assertThat(((ApiException) error).getCode()).isEqualTo(ErrorCode.INSUFFICIENT_CREDITS));
        });
        assertThat(state().planCode()).isEqualTo("FREE");
        assertThat(ledger.balance(userId)).isEqualTo(2);
    }

    @Test
    void premiumExpiryAtNowFallsBackToFreeQuota() {
        seedPremium();
        transaction().executeWithoutResult(status -> {
            auth.lockPlan(userId);
            auth.updatePlan(userId, "PREMIUM", NOW);
        });
        assertThat(consume("expired").source()).isEqualTo(Source.QUOTA);
        assertThat(state().freeUsage()).containsEntry("TRANSLATE", 1);
        assertThat(countTransactions()).isZero();
    }

    @Test
    void vietnamMonthBoundaryResetsAllCountersOnlyWhenConsumed() {
        usage(Map.of("TRANSLATE", 9, "GRADING", 6), Instant.parse("2031-09-30T17:00:00Z"));
        EntitlementService before = serviceAt(Instant.parse("2031-10-31T16:59:59Z"));
        Consumption october = transaction().execute(status -> before.consume(userId, PaidFeature.TRANSLATE, "october"));
        assertThat(october.periodStart()).isEqualTo(Instant.parse("2031-09-30T17:00:00Z"));
        assertThat(state().freeUsage()).containsExactlyInAnyOrderEntriesOf(Map.of("TRANSLATE", 10, "GRADING", 6));
        Consumption november = transaction().execute(status -> serviceAt(MONTH).consume(userId, PaidFeature.TRANSLATE, "november"));
        assertThat(november.periodStart()).isEqualTo(MONTH);
        assertThat(state().usageResetAt()).isEqualTo(MONTH);
        assertThat(state().freeUsage()).containsExactlyEntriesOf(Map.of("TRANSLATE", 1));
    }

    @Test
    void renewingPlanPreservesUsageWithinTheCurrentMonth() {
        seedPremium();
        usage(Map.of("TRANSLATE", 7, "GRADING", 3), MONTH);
        transaction().executeWithoutResult(status -> {
            status.setRollbackOnly();
            jdbc.queryForObject("SELECT code FROM learning.plans WHERE code = 'PREMIUM' FOR UPDATE", String.class);
            jdbc.update("UPDATE learning.plans SET benefits = CAST(? AS jsonb) WHERE code = 'PREMIUM'",
                    "{\"TRANSLATE\":10}");
            auth.lockPlan(userId);
            auth.updatePlan(userId, "PREMIUM", NOW.plusSeconds(86400));
            auth.updatePlan(userId, "PREMIUM", NOW.plusSeconds(172800));
            assertThat(service.consume(userId, PaidFeature.TRANSLATE, "renewed").source()).isEqualTo(Source.QUOTA);
            assertThat(auth.lockPlan(userId).freeUsage())
                    .containsExactlyInAnyOrderEntriesOf(Map.of("TRANSLATE", 8, "GRADING", 3));
        });
        assertThat(state().freeUsage()).containsExactlyInAnyOrderEntriesOf(Map.of("TRANSLATE", 7, "GRADING", 3));
        assertThat(state().usageResetAt()).isEqualTo(MONTH);
    }

    @Test
    void proxyRejectsMissingAndReadOnlyTransactionsEvenForCachedReceipts() {
        Consumption receipt = consume("cached");
        assertThatThrownBy(() -> entitlement.consume(userId, PaidFeature.TRANSLATE, "cached"))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> entitlement.refund(userId, receipt))
                .isInstanceOf(IllegalTransactionStateException.class);
        TransactionTemplate readOnly = transaction();
        readOnly.setReadOnly(true);
        assertThatThrownBy(() -> readOnly.execute(status -> entitlement.consume(userId, PaidFeature.TRANSLATE, "cached")))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> readOnly.executeWithoutResult(status -> entitlement.refund(userId, receipt)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(state().freeUsage()).containsEntry("TRANSLATE", 1);
    }

    @Test
    void quotaAndCreditConsumptionRollbackRestoresStateWithoutPublishingReceipts() {
        transaction().executeWithoutResult(status -> {
            service.consume(userId, PaidFeature.TRANSLATE, "rollback-quota");
            status.setRollbackOnly();
        });
        assertThat(state().freeUsage()).isEmpty();
        assertThat(redis.hasKey(idemKey("rollback-quota"))).isFalse();
        usage(Map.of("TRANSLATE", 10), MONTH);
        append(userId, "TOPUP", 1, null, null);
        transaction().executeWithoutResult(status -> {
            service.consume(userId, PaidFeature.TRANSLATE, "rollback-credit");
            status.setRollbackOnly();
        });
        assertThat(ledger.balance(userId)).isEqualTo(1);
        assertThat(countTransactions()).isEqualTo(1);
        assertThat(redis.hasKey(idemKey("rollback-credit"))).isFalse();
    }

    @Test
    void creditRefundUsesOriginalAmountAndFeatureAndIsRepeatableWithoutAnotherRow() {
        append(userId, "TOPUP", 5, null, null);
        LedgerEntry debit = append(userId, "DEDUCT", -3, "TRANSLATE", null);
        Consumption receipt = new Consumption(Source.CREDIT, debit.id(), PaidFeature.GRADING, "original-three", MONTH);
        refund(receipt);
        refund(receipt);
        assertThat(ledger.balance(userId)).isEqualTo(5);
        assertThat(countTransactions()).isEqualTo(3);
        assertThat(jdbc.queryForMap("""
                SELECT amount, feature_code, ref_transaction_id FROM learning.credit_transactions
                WHERE user_id = ? AND tx_type = 'REFUND'
                """, userId)).containsEntry("amount", 3).containsEntry("feature_code", "TRANSLATE")
                .containsEntry("ref_transaction_id", debit.id());
    }

    @Test
    void creditRefundRejectsMissingWrongOwnerAndNonDeductReferences() {
        LedgerEntry topup = append(userId, "TOPUP", 5, null, null);
        append(otherUserId, "TOPUP", 5, null, null);
        LedgerEntry othersDebit = append(otherUserId, "DEDUCT", -1, "TRANSLATE", null);
        LedgerEntry positiveDeduct = append(userId, "DEDUCT", 1, "TRANSLATE", null);
        for (long reference : new long[] {Long.MAX_VALUE, othersDebit.id(), topup.id(), positiveDeduct.id()}) {
            Consumption receipt = new Consumption(Source.CREDIT, reference, PaidFeature.TRANSLATE, "invalid-" + reference, MONTH);
            assertThatThrownBy(() -> refund(receipt)).isInstanceOf(ApiException.class).satisfies(error -> {
                assertThat(((ApiException) error).getCode()).isEqualTo(ErrorCode.FORBIDDEN);
                assertThat(((ApiException) error).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(error.getMessage()).isEqualTo("Không có quyền truy cập giao dịch.");
            });
        }
        assertThat(countTransactions()).isEqualTo(2);
        assertThat(ledger.balance(userId)).isEqualTo(6);
        assertThat(ledger.balance(otherUserId)).isEqualTo(4);
    }

    @Test
    void creditRefundRollbackDoesNotPreventRetry() {
        append(userId, "TOPUP", 3, null, null);
        LedgerEntry debit = append(userId, "DEDUCT", -3, "TRANSLATE", null);
        Consumption receipt = new Consumption(Source.CREDIT, debit.id(), PaidFeature.TRANSLATE, "credit-rollback", MONTH);
        transaction().executeWithoutResult(status -> {
            service.refund(userId, receipt);
            status.setRollbackOnly();
        });
        assertThat(ledger.balance(userId)).isZero();
        assertThat(countTransactions()).isEqualTo(2);
        refund(receipt);
        assertThat(ledger.balance(userId)).isEqualTo(3);
        assertThat(countTransactions()).isEqualTo(3);
    }

    @Test
    void quotaRefundDecrementsOnceAndConsumeWithTheSameKeyStillReturnsTheOldReceipt() {
        Consumption receipt = consume("quota-refund");
        refund(receipt);
        refund(receipt);
        assertThat(state().freeUsage().getOrDefault("TRANSLATE", 0)).isZero();
        assertThat(consume("quota-refund")).isEqualTo(receipt);
        assertThat(state().freeUsage().getOrDefault("TRANSLATE", 0)).isZero();
        assertThat(ledger.balance(userId)).isZero();
        assertThat(countTransactions()).isZero();
    }

    @Test
    void quotaRefundRollbackRemovesReservationSoRetryCanDecrement() {
        Consumption receipt = consume("quota-rollback");
        String marker = "billing:refund:" + userId + ":quota-rollback";
        transaction().executeWithoutResult(status -> {
            service.refund(userId, receipt);
            assertThat(redis.hasKey(marker)).isTrue();
            status.setRollbackOnly();
        });
        assertThat(state().freeUsage()).containsEntry("TRANSLATE", 1);
        assertThat(redis.hasKey(marker)).isFalse();
        refund(receipt);
        assertThat(state().freeUsage().getOrDefault("TRANSLATE", 0)).isZero();
        assertThat(redis.hasKey(marker)).isTrue();
    }

    @Test
    void oldPeriodAndZeroUsageQuotaRefundsDoNotDecrementOrAddCredits() {
        usage(Map.of("TRANSLATE", 4), MONTH);
        Consumption old = new Consumption(Source.QUOTA, null, PaidFeature.TRANSLATE, "old-month",
                Instant.parse("2031-09-30T17:00:00Z"));
        refund(old);
        assertThat(state().freeUsage()).containsEntry("TRANSLATE", 4);
        usage(Map.of("TRANSLATE", 0), MONTH);
        refund(new Consumption(Source.QUOTA, null, PaidFeature.TRANSLATE, "zero", MONTH));
        assertThat(state().freeUsage()).containsEntry("TRANSLATE", 0);
        assertThat(countTransactions()).isZero();
        assertThat(redis.hasKey("billing:refund:" + userId + ":old-month")).isFalse();
        assertThat(redis.hasKey("billing:refund:" + userId + ":zero")).isFalse();
    }

    @Test
    void unavailableRedisRefundReservationLeavesPersistedUsageUnchanged() {
        usage(Map.of("TRANSLATE", 4), MONTH);
        var unavailable = org.mockito.Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.ValueOperations<String, String> values =
                org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);
        org.mockito.Mockito.when(unavailable.opsForValue()).thenReturn(values);
        org.mockito.Mockito.when(values.setIfAbsent(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("1"), org.mockito.ArgumentMatchers.any(java.time.Duration.class)))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("Injected failure"));
        EntitlementService selected = new EntitlementService(auth, ledger, namedJdbc, unavailable, mapper,
                new BillingConfig(), Clock.fixed(NOW, ZoneOffset.UTC));
        Consumption receipt = new Consumption(Source.QUOTA, null, PaidFeature.TRANSLATE, "redis-unavailable", MONTH);
        transaction().executeWithoutResult(status -> selected.refund(userId, receipt));
        assertThat(state().freeUsage()).containsEntry("TRANSLATE", 4);
        assertThat(countTransactions()).isZero();
        assertThat(redis.hasKey("billing:refund:" + userId + ":redis-unavailable")).isFalse();
    }

    @Test
    void repeatedCallsWithinOneTransactionConsumeAndRefundOnlyOnce() {
        transaction().executeWithoutResult(status -> {
            Consumption first = service.consume(userId, PaidFeature.TRANSLATE, "same-transaction");
            assertThat(service.consume(userId, PaidFeature.TRANSLATE, "same-transaction")).isEqualTo(first);
            service.refund(userId, first);
            service.refund(userId, first);
            assertThat(service.consume(userId, PaidFeature.TRANSLATE, "same-transaction")).isEqualTo(first);
        });
        assertThat(state().freeUsage().getOrDefault("TRANSLATE", 0)).isZero();
        assertThat(countTransactions()).isZero();
    }

    private long createUser() {
        return jdbc.queryForObject("""
                INSERT INTO auth.users(email, password_hash, display_name)
                VALUES (?, ?, 'Entitlement integration test') RETURNING id
                """, Long.class, "entitlement-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH);
    }

    private void seedPremium() {
        seedPremium(createdPremiumCodes);
    }

    private void seedPremium(List<String> ownedCodes) {
        int inserted = jdbc.update("""
                INSERT INTO learning.plans(code, name, duration_days) VALUES ('PREMIUM', 'Test Premium', 30)
                ON CONFLICT (code) DO NOTHING
                """);
        if (inserted == 1) {
            ownedCodes.add("PREMIUM");
        }
    }

    private void cleanUpOwnedPremium(List<String> ownedCodes) {
        for (String code : ownedCodes) {
            assertThat(jdbc.update("DELETE FROM learning.plans WHERE code = ?", code)).isEqualTo(1);
        }
        ownedCodes.clear();
    }

    private List<Map<String, Object>> premiumRows() {
        return jdbc.queryForList("SELECT * FROM learning.plans WHERE code = 'PREMIUM'");
    }

    private EntitlementService serviceAt(Instant instant) {
        return new EntitlementService(auth, ledger, namedJdbc, redis, mapper, new BillingConfig(),
                Clock.fixed(instant, ZoneOffset.UTC));
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(manager);
    }

    private Consumption consume(String key) {
        return transaction().execute(status -> service.consume(userId, PaidFeature.TRANSLATE, key));
    }

    private void refund(Consumption receipt) {
        transaction().executeWithoutResult(status -> service.refund(userId, receipt));
    }

    private void usage(Map<String, Integer> values, Instant reset) {
        transaction().executeWithoutResult(status -> {
            auth.lockPlan(userId);
            auth.updateUsage(userId, values, reset);
        });
    }

    private PlanState state() {
        return transaction().execute(status -> auth.lockPlan(userId));
    }

    private LedgerEntry append(long owner, String type, int amount, String feature, Long reference) {
        return transaction().execute(status -> ledger.append(new AppendCommand(owner, type, amount, feature,
                null, null, reference, null, null, null)));
    }

    private long countTransactions() {
        return jdbc.queryForObject("SELECT count(*) FROM learning.credit_transactions WHERE user_id = ?", Long.class, userId);
    }

    private String idemKey(String key) {
        return "billing:idem:" + userId + ":" + key;
    }
}
