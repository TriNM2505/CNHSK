package com.cnhsk.learning.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.credit.BillingConfig;
import com.cnhsk.learning.credit.EntitlementService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.support.AbstractIntegrationTest;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class EntitlementApiCompatibilityTest extends AbstractIntegrationTest {
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    @Autowired private EntitlementApi billing;
    @Autowired private AuthApi auth;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private StringRedisTemplate redis;
    @Autowired private BillingConfig config;
    @Autowired private Clock clock;
    private final List<String> keys = new ArrayList<>();
    private long userId;
    private String key;

    @BeforeEach
    void setUp() {
        userId = jdbc.queryForObject("""
                INSERT INTO auth.users(email, password_hash, display_name)
                VALUES (?, ?, 'Billing caller test') RETURNING id
                """, Long.class, "billing-caller-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH);
        key = "caller-" + UUID.randomUUID();
        keys.add("billing:idem:" + userId + ":" + key);
        keys.add("billing:refund:" + userId + ":" + key);
    }

    @AfterEach
    void cleanUp() {
        redis.delete(keys);
        jdbc.update("DELETE FROM learning.credit_transactions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM auth.users WHERE id = ?", userId);
    }

    @Test
    void exactPublicTypesSupportCompileTimeCallerUse() throws Exception {
        assertThat(PaidFeature.values()).containsExactly(PaidFeature.AI_GENERATE, PaidFeature.TRANSLATE,
                PaidFeature.ASSISTANT, PaidFeature.GRADING);
        assertThat(Consumption.Source.values()).containsExactly(Consumption.Source.QUOTA, Consumption.Source.CREDIT);
        assertThat(Consumption.class.isRecord()).isTrue();
        assertThat(Consumption.class.getRecordComponents()).extracting(component -> component.getName())
                .containsExactly("source", "transactionId", "feature", "idempotencyKey", "periodStart");
        Consumption quota = new Consumption(Consumption.Source.QUOTA, null, PaidFeature.TRANSLATE, key, period());
        Consumption credit = new Consumption(Consumption.Source.CREDIT, 5L, PaidFeature.GRADING, key, period());
        Long originalId = credit.transactionId();
        Instant periodStart = quota.periodStart();
        assertThat(originalId).isEqualTo(5L);
        assertThat(periodStart).isEqualTo(period());
        assertThat(EntitlementApi.class.getMethod("consume", long.class, PaidFeature.class, String.class).getReturnType())
                .isEqualTo(Consumption.class);
        assertThat(EntitlementApi.class.getMethod("refund", long.class, Consumption.class).getReturnType())
                .isEqualTo(void.class);
        assertThat(EntitlementService.class).isAssignableTo(EntitlementApi.class);
    }

    @Test
    void implementationMethodsDeclareMandatoryCallerTransactions() throws Exception {
        for (var method : List.of(
                EntitlementService.class.getMethod("consume", long.class, PaidFeature.class, String.class),
                EntitlementService.class.getMethod("refund", long.class, Consumption.class))) {
            Transactional transaction = method.getAnnotation(Transactional.class);
            assertThat(transaction).isNotNull();
            assertThat(transaction.propagation()).isEqualTo(Propagation.MANDATORY);
            assertThat(transaction.readOnly()).isFalse();
        }
    }

    @Test
    void externalProviderRunsOnlyAfterCommitAndIndependentUserLockAcquisition() {
        AtomicInteger calls = new AtomicInteger();
        String result = call(billing, false, () -> {
            calls.incrementAndGet();
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(usage()).containsEntry("TRANSLATE", 1);
            probeReleasedUserLock();
            return "Bản dịch tự soạn";
        });
        assertThat(result).isEqualTo("Bản dịch tự soạn");
        assertThat(calls).hasValue(1);
        assertThat(usage()).containsEntry("TRANSLATE", 1);
        assertThat(redis.hasKey("billing:idem:" + userId + ":" + key)).isTrue();
    }

    @Test
    void externalFailureCompensatesInSeparateTransactionAfterCommit() {
        AtomicInteger refunds = new AtomicInteger();
        EntitlementApi observed = new EntitlementApi() {
            @Override
            public Consumption consume(long user, PaidFeature feature, String requestKey) {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                return billing.consume(user, feature, requestKey);
            }

            @Override
            public void refund(long user, Consumption receipt) {
                refunds.incrementAndGet();
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                assertThat(redis.hasKey("billing:idem:" + user + ":" + requestKey(receipt))).isTrue();
                billing.refund(user, receipt);
            }
        };
        assertThatThrownBy(() -> call(observed, false, () -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(usage()).containsEntry("TRANSLATE", 1);
            probeReleasedUserLock();
            throw new IllegalStateException("Provider failed");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Provider failed");
        assertThat(refunds).hasValue(1);
        assertThat(usage()).containsEntry("TRANSLATE", 0);
        assertThat(redis.hasKey("billing:refund:" + userId + ":" + key)).isTrue();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void insufficientCreditsPreventsProviderCallAndCompensation() {
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            auth.lockPlan(userId);
            auth.updateUsage(userId, Map.of("TRANSLATE", config.getFreeMonthlyLimit()), period());
        });
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> call(billing, false, () -> {
            calls.incrementAndGet();
            return "Unexpected provider call";
        })).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_CREDITS);
            assertThat(error.getStatus().value()).isEqualTo(402);
        });
        assertThat(calls).hasValue(0);
        assertThat(redis.hasKey("billing:refund:" + userId + ":" + key)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.credit_transactions WHERE user_id = ?",
                Integer.class, userId)).isZero();
    }

    @Test
    void translationCacheHitDoesNotConsumeRefundOrInvokeProvider() {
        EntitlementApi observed = mock(EntitlementApi.class);
        @SuppressWarnings("unchecked")
        Supplier<String> provider = mock(Supplier.class);
        assertThat(call(observed, true, provider)).isEqualTo("Bản dịch đã lưu");
        verifyNoInteractions(observed, provider);
        assertThat(usage()).isEmpty();
    }

    @Test
    void systemAssistantKeepsItsApprovedNonBillingBoundary() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.cnhsk.learning.assistant");
        assertThat(classes).isNotEmpty();
        noClasses().should().dependOnClassesThat().resideInAnyPackage("com.cnhsk.learning.credit..")
                .check(classes);
        noClasses().should().dependOnClassesThat().haveFullyQualifiedName(EntitlementApi.class.getName())
                .check(classes);
    }

    private String call(EntitlementApi api, boolean cached, Supplier<String> provider) {
        if (cached) return "Bản dịch đã lưu";
        Consumption receipt = new TransactionTemplate(manager).execute(status -> api.consume(userId, PaidFeature.TRANSLATE, key));
        try {
            return provider.get();
        } catch (RuntimeException exception) {
            new TransactionTemplate(manager).executeWithoutResult(status -> api.refund(userId, receipt));
            throw exception;
        }
    }

    private String requestKey(Consumption receipt) {
        return receipt.idempotencyKey();
    }

    private Map<String, Integer> usage() {
        return new TransactionTemplate(manager).execute(status -> auth.lockPlan(userId).freeUsage());
    }

    private Instant period() {
        return clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate().withDayOfMonth(1)
                .atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
    }

    private void probeReleasedUserLock() {
        var executor = Executors.newSingleThreadExecutor();
        try {
            assertThat(executor.submit(() -> new TransactionTemplate(manager).execute(status -> {
                jdbc.execute("SET LOCAL lock_timeout = '2s'");
                return auth.lockPlan(userId).planCode();
            })).get(5, TimeUnit.SECONDS)).isEqualTo("FREE");
        } catch (Exception exception) {
            throw new AssertionError("Provider phase must allow an independent transaction to lock the user", exception);
        } finally {
            executor.shutdownNow();
        }
    }
}
