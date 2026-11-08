package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.PlanState;
import com.cnhsk.learning.api.Consumption;
import com.cnhsk.learning.api.Consumption.Source;
import com.cnhsk.learning.api.PaidFeature;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class EntitlementServiceTest {
    private static final long USER = 71L;
    private static final Instant NOW = Instant.parse("2026-11-08T03:00:00Z");
    private static final Instant PERIOD = Instant.parse("2026-10-31T17:00:00Z");
    private static final String KEY = "receipt-unit-marker";
    private static final String IDEM = "billing:idem:" + USER + ":" + KEY;
    private static final String REFUND = "billing:refund:" + USER + ":" + KEY;
    private final AuthApi auth = mock(AuthApi.class);
    private final LedgerService ledger = mock(LedgerService.class);
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final BillingConfig config = new BillingConfig();
    private EntitlementService service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        when(auth.lockPlan(USER)).thenReturn(plan("FREE", null, Map.of()));
        service = service(Clock.fixed(NOW, ZoneOffset.UTC));
        begin();
    }

    @AfterEach
    void cleanUp() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) finish(false);
        TransactionSynchronizationManager.clear();
    }

    @Test
    void rejectsInvalidArgumentsBeforeCacheOrPlanAccess() {
        for (String key : new String[] {null, "", " \t", "a".repeat(129)}) {
            assertInvalid(() -> service.consume(USER, PaidFeature.TRANSLATE, key));
        }
        assertInvalid(() -> service.consume(0, PaidFeature.TRANSLATE, KEY));
        assertInvalid(() -> service.consume(-1, PaidFeature.TRANSLATE, KEY));
        assertInvalid(() -> service.consume(USER, null, KEY));
        assertInvalid(() -> service.refund(USER, null));
        assertInvalid(() -> service.refund(0, receipt(PaidFeature.TRANSLATE)));
        verifyNoInteractions(values, ledger);
        verify(auth, never()).lockPlan(anyLong());
    }

    @Test
    void rejectsMalformedServerReceipts() {
        var feature = PaidFeature.TRANSLATE;
        for (Consumption receipt : List.of(
                new Consumption(null, null, feature, KEY, PERIOD),
                new Consumption(Source.QUOTA, 9L, feature, KEY, PERIOD),
                new Consumption(Source.CREDIT, null, feature, KEY, PERIOD),
                new Consumption(Source.CREDIT, 0L, feature, KEY, PERIOD),
                new Consumption(Source.QUOTA, null, null, KEY, PERIOD),
                new Consumption(Source.QUOTA, null, feature, " ", PERIOD),
                new Consumption(Source.QUOTA, null, feature, KEY, null))) {
            assertInvalid(() -> service.refund(USER, receipt));
        }
        verify(auth, never()).lockPlan(anyLong());
    }

    @Test
    void rejectsUnrepresentablePeriodAndRedactsReceiptKey() {
        Consumption receipt = new Consumption(Source.QUOTA, null, PaidFeature.TRANSLATE, KEY, Instant.MAX);
        assertInvalid(() -> service.refund(USER, receipt));
        assertThat(receipt.toString()).contains("[REDACTED]").doesNotContain(KEY);
    }

    @Test
    void requiresWritableTransactionEvenForCachedReceipt() throws Exception {
        finish(false);
        assertThatThrownBy(() -> service.consume(USER, PaidFeature.TRANSLATE, KEY))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> service.refund(USER, receipt(PaidFeature.TRANSLATE)))
                .isInstanceOf(IllegalTransactionStateException.class);
        begin();
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        assertThatThrownBy(() -> service.consume(USER, PaidFeature.TRANSLATE, KEY))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> service.refund(USER, receipt(PaidFeature.TRANSLATE)))
                .isInstanceOf(IllegalTransactionStateException.class);
        verifyNoInteractions(values);
    }

    @ParameterizedTest
    @EnumSource(PaidFeature.class)
    void eachFeatureUsesConfiguredFreeQuotaAndPreservesOtherCounters(PaidFeature feature) {
        config.setFreeMonthlyLimit(3);
        when(auth.lockPlan(USER)).thenReturn(plan("FREE", null, Map.of(feature.name(), 2, "UNCHANGED", 7)));
        Consumption result = service.consume(USER, feature, KEY);
        assertThat(result).isEqualTo(receipt(feature));
        verify(auth).updateUsage(USER, Map.of(feature.name(), 3, "UNCHANGED", 7), PERIOD);
        verifyNoInteractions(ledger);
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
        finish(true);
        verify(values).set(eq(IDEM), anyString(), eq(Duration.ofHours(24)));
    }

    @Test
    void preservesKeyWhitespaceAndMaximumLength() {
        String key = " " + "x".repeat(126) + " ";
        assertThat(service.consume(USER, PaidFeature.TRANSLATE, key).idempotencyKey()).isEqualTo(key);
        finish(true);
        verify(values).set(eq("billing:idem:" + USER + ":" + key), anyString(), eq(Duration.ofHours(24)));
    }

    @Test
    void exhaustedQuotaDeductsOneCreditWithServerSelectedFeature() {
        when(auth.lockPlan(USER)).thenReturn(plan("FREE", null, Map.of("TRANSLATE", 10)));
        when(ledger.balance(USER)).thenReturn(2);
        when(ledger.append(any())).thenReturn(new LedgerService.LedgerEntry(901L, 2, 2, 1));
        Consumption result = service.consume(USER, PaidFeature.TRANSLATE, KEY);
        assertThat(result).isEqualTo(new Consumption(Source.CREDIT, 901L, PaidFeature.TRANSLATE, KEY, PERIOD));
        verify(ledger).append(argThat(command -> command.userId() == USER && command.amount() == -1
                && command.txType().equals("DEDUCT") && command.featureCode().equals("TRANSLATE")
                && command.refTransactionId() == null));
        verify(auth, never()).updateUsage(anyLong(), anyMap(), any());
    }

    @Test
    void exhaustedQuotaAndZeroCreditsReturnFixed402WithoutMutation() {
        when(auth.lockPlan(USER)).thenReturn(plan("FREE", null, Map.of("TRANSLATE", 10)));
        assertThatThrownBy(() -> service.consume(USER, PaidFeature.TRANSLATE, KEY))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatus().value()).isEqualTo(402);
                    assertThat(error.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_CREDITS);
                    assertThat(error.getMessage()).isEqualTo("Đã hết hạn mức và điểm.");
                });
        verify(ledger, never()).append(any());
        verify(auth, never()).updateUsage(anyLong(), anyMap(), any());
    }

    @Test
    void currentPremiumUsesItsBenefitWithoutAddingFreeQuota() {
        when(auth.lockPlan(USER)).thenReturn(plan("PREMIUM", NOW.plusSeconds(1), Map.of("TRANSLATE", 2)));
        when(jdbc.queryForList(anyString(), anyMap(), eq(String.class))).thenReturn(List.of("{\"TRANSLATE\":3}"));
        service.consume(USER, PaidFeature.TRANSLATE, KEY);
        verify(auth).updateUsage(USER, Map.of("TRANSLATE", 3), PERIOD);
        verifyNoInteractions(ledger);
    }

    @Test
    void premiumMissingBenefitAndMissingPlanDoNotReceiveFreeQuota() {
        when(auth.lockPlan(USER)).thenReturn(plan("PREMIUM", NOW.plusSeconds(1), Map.of()));
        when(jdbc.queryForList(anyString(), anyMap(), eq(String.class))).thenReturn(List.of("{}"));
        assertThatThrownBy(() -> service.consume(USER, PaidFeature.TRANSLATE, KEY)).isInstanceOf(ApiException.class);
        finish(false);
        begin();
        when(jdbc.queryForList(anyString(), anyMap(), eq(String.class))).thenReturn(List.of());
        assertThatThrownBy(() -> service.consume(USER, PaidFeature.TRANSLATE, KEY)).isInstanceOf(ApiException.class);
        verify(auth, never()).updateUsage(anyLong(), anyMap(), any());
    }

    @Test
    void expiryAtNowNullExpiryAndOtherPlanUseFreeLimit() {
        for (PlanState state : List.of(plan("PREMIUM", NOW, Map.of()), plan("PREMIUM", null, Map.of()),
                plan("OTHER", NOW.plusSeconds(30), Map.of()))) {
            when(auth.lockPlan(USER)).thenReturn(state);
            assertThat(service.consume(USER, PaidFeature.TRANSLATE, KEY).source()).isEqualTo(Source.QUOTA);
            finish(false);
            begin();
        }
        verifyNoInteractions(jdbc, ledger);
    }

    @Test
    void clockIsReadAfterPlanLockAtVietnamMonthBoundary() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.parse("2026-11-30T16:59:59Z"));
        when(auth.lockPlan(USER)).thenAnswer(invocation -> {
            when(clock.instant()).thenReturn(Instant.parse("2026-11-30T17:00:00Z"));
            return plan("FREE", null, Map.of("TRANSLATE", 10, "GRADING", 6));
        });
        Consumption result = service(clock).consume(USER, PaidFeature.TRANSLATE, KEY);
        assertThat(result.periodStart()).isEqualTo(Instant.parse("2026-11-30T17:00:00Z"));
        verify(auth).updateUsage(USER, Map.of("TRANSLATE", 1), result.periodStart());
    }

    @Test
    void cachedReceiptReturnsBeforeLockAndRemainsTheSameAfterRefund() throws Exception {
        Consumption receipt = receipt(PaidFeature.TRANSLATE);
        when(values.get(IDEM)).thenReturn(mapper.writeValueAsString(receipt));
        assertThat(service.consume(USER, PaidFeature.TRANSLATE, KEY)).isEqualTo(receipt);
        verify(auth, never()).lockPlan(USER);
        when(auth.lockPlan(USER)).thenReturn(plan("FREE", null, Map.of("TRANSLATE", 1)));
        when(values.setIfAbsent(eq(REFUND), eq("1"), any(Duration.class))).thenReturn(true);
        service.refund(USER, receipt);
        finish(true);
        begin();
        assertThat(service.consume(USER, PaidFeature.TRANSLATE, KEY)).isEqualTo(receipt);
        verify(auth).lockPlan(USER);
        verify(auth).updateUsage(USER, Map.of("TRANSLATE", 0), PERIOD);
        verify(redis, never()).delete(IDEM);
    }

    @Test
    void secondLookupFindsReceiptPublishedWhileWaitingForPlanLock() throws Exception {
        Consumption receipt = receipt(PaidFeature.TRANSLATE);
        when(values.get(IDEM)).thenReturn(null, mapper.writeValueAsString(receipt));
        assertThat(service.consume(USER, PaidFeature.TRANSLATE, KEY)).isEqualTo(receipt);
        var order = inOrder(values, auth);
        order.verify(values).get(IDEM);
        order.verify(auth).lockPlan(USER);
        order.verify(values).get(IDEM);
        verify(auth, never()).updateUsage(anyLong(), anyMap(), any());
        verifyNoInteractions(ledger);
    }

    @Test
    void featureMismatchIsRejectedOnEitherRedisLookup() throws Exception {
        String json = mapper.writeValueAsString(receipt(PaidFeature.GRADING));
        when(values.get(IDEM)).thenReturn(json);
        assertInvalid(() -> service.consume(USER, PaidFeature.TRANSLATE, KEY));
        verify(auth, never()).lockPlan(USER);
        when(values.get(IDEM)).thenReturn(null, json);
        assertInvalid(() -> service.consume(USER, PaidFeature.TRANSLATE, KEY));
        verify(auth).lockPlan(USER);
        verifyNoInteractions(ledger);
        verify(auth, never()).updateUsage(anyLong(), anyMap(), any());
    }

    @Test
    void repeatedConsumeWithinTransactionUsesMemoAndRejectsDifferentFeature() {
        Consumption first = service.consume(USER, PaidFeature.TRANSLATE, KEY);
        assertThat(service.consume(USER, PaidFeature.TRANSLATE, KEY)).isEqualTo(first);
        assertInvalid(() -> service.consume(USER, PaidFeature.GRADING, KEY));
        verify(auth).updateUsage(USER, Map.of("TRANSLATE", 1), PERIOD);
        finish(false);
        begin();
        service.consume(USER, PaidFeature.TRANSLATE, KEY);
        verify(auth, times(2)).updateUsage(USER, Map.of("TRANSLATE", 1), PERIOD);
    }

    @Test
    void rollbackNeverPublishesConsumptionReceipt() {
        service.consume(USER, PaidFeature.TRANSLATE, KEY);
        finish(false);
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void redisReadAndWriteFailuresFailOpenAndWarnWithoutExceptionData() {
        captureWarnings(events -> {
            when(values.get(IDEM)).thenThrow(redisFailure());
            doThrow(redisFailure()).when(values).set(eq(IDEM), anyString(), any(Duration.class));
            assertThat(service.consume(USER, PaidFeature.TRANSLATE, KEY).source()).isEqualTo(Source.QUOTA);
            finish(true);
            assertSafeWarnings(events, 3);
        });
    }

    @Test
    void invalidCachedJsonFailsOpenWithoutLoggingPayload() {
        captureWarnings(events -> {
            when(values.get(IDEM)).thenReturn("{private-json-payload-marker");
            assertThat(service.consume(USER, PaidFeature.TRANSLATE, KEY).source()).isEqualTo(Source.QUOTA);
            assertSafeWarnings(events, 2);
        });
    }

    @Test
    void serializationFailureAfterCommitDoesNotEscapeOrExposePayload() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new com.fasterxml.jackson.core.JsonProcessingException(
                "private-json-payload-marker") {});
        service = new EntitlementService(auth, ledger, jdbc, redis, failingMapper, config, Clock.fixed(NOW, ZoneOffset.UTC));
        captureWarnings(events -> {
            service.consume(USER, PaidFeature.TRANSLATE, KEY);
            finish(true);
            assertSafeWarnings(events, 1);
        });
    }

    @Test
    void quotaRefundClaimsMarkerUntilNextVietnamMonthAndDecrementsOnlyOnce() {
        when(auth.lockPlan(USER)).thenReturn(plan("FREE", null, Map.of("TRANSLATE", 2, "GRADING", 4)));
        when(values.setIfAbsent(eq(REFUND), eq("1"), any(Duration.class))).thenReturn(true, false);
        service.refund(USER, receipt(PaidFeature.TRANSLATE));
        service.refund(USER, receipt(PaidFeature.TRANSLATE));
        verify(values, times(2)).setIfAbsent(REFUND, "1", Duration.between(NOW, Instant.parse("2026-11-30T17:00:00Z")));
        verify(auth).updateUsage(USER, Map.of("TRANSLATE", 1, "GRADING", 4), PERIOD);
        finish(true);
        verify(redis, never()).delete(REFUND);
        verifyNoInteractions(ledger);
    }

    @Test
    void markerAlreadyClaimedOrUnavailableNeverDecrementsQuota() {
        when(auth.lockPlan(USER)).thenReturn(plan("FREE", null, Map.of("TRANSLATE", 2)));
        when(values.setIfAbsent(eq(REFUND), eq("1"), any(Duration.class))).thenReturn(false);
        service.refund(USER, receipt(PaidFeature.TRANSLATE));
        finish(false);
        begin();
        captureWarnings(events -> {
            when(values.setIfAbsent(eq(REFUND), eq("1"), any(Duration.class))).thenThrow(redisFailure());
            service.refund(USER, receipt(PaidFeature.TRANSLATE));
            assertSafeWarnings(events, 1);
        });
        verify(auth, never()).updateUsage(anyLong(), anyMap(), any());
    }

    @Test
    void oldPeriodZeroUsageAndStaleStoredUsageDoNotClaimRefundMarker() {
        Consumption old = new Consumption(Source.QUOTA, null, PaidFeature.TRANSLATE, KEY,
                Instant.parse("2026-09-30T17:00:00Z"));
        service.refund(USER, old);
        service.refund(USER, receipt(PaidFeature.TRANSLATE));
        when(auth.lockPlan(USER)).thenReturn(new PlanState("FREE", null, Map.of("TRANSLATE", 2), Instant.EPOCH));
        service.refund(USER, receipt(PaidFeature.TRANSLATE));
        verify(values, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
        verify(auth, never()).updateUsage(anyLong(), anyMap(), any());
    }

    @Test
    void refundRollbackDeletesMarkerAndDeleteFailureWarnsSafely() {
        when(auth.lockPlan(USER)).thenReturn(plan("FREE", null, Map.of("TRANSLATE", 2)));
        when(values.setIfAbsent(eq(REFUND), eq("1"), any(Duration.class))).thenReturn(true);
        captureWarnings(events -> {
            when(redis.delete(REFUND)).thenThrow(redisFailure());
            service.refund(USER, receipt(PaidFeature.TRANSLATE));
            finish(false);
            verify(redis).delete(REFUND);
            assertSafeWarnings(events, 1);
        });
    }

    @Test
    void failedUsageUpdateStillRegistersRollbackMarkerCleanup() {
        when(auth.lockPlan(USER)).thenReturn(plan("FREE", null, Map.of("TRANSLATE", 2)));
        when(values.setIfAbsent(eq(REFUND), eq("1"), any(Duration.class))).thenReturn(true);
        doThrow(new IllegalStateException("test write failure")).when(auth).updateUsage(anyLong(), anyMap(), any());
        assertThatThrownBy(() -> service.refund(USER, receipt(PaidFeature.TRANSLATE))).isInstanceOf(IllegalStateException.class);
        finish(false);
        verify(redis).delete(REFUND);
    }

    private EntitlementService service(Clock clock) {
        return new EntitlementService(auth, ledger, jdbc, redis, mapper, config, clock);
    }

    private PlanState plan(String code, Instant expiry, Map<String, Integer> usage) {
        return new PlanState(code, expiry, usage, PERIOD);
    }

    private Consumption receipt(PaidFeature feature) {
        return new Consumption(Source.QUOTA, null, feature, KEY, PERIOD);
    }

    private void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(IllegalArgumentException.class).hasMessage("Tham số không hợp lệ.");
    }

    private RedisConnectionFailureException redisFailure() {
        return new RedisConnectionFailureException("redis://private-connection-marker/private-json-payload-marker");
    }

    private void captureWarnings(Consumer<List<ILoggingEvent>> assertion) {
        Logger logger = (Logger) LoggerFactory.getLogger(EntitlementService.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertion.accept(appender.list);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private void assertSafeWarnings(List<ILoggingEvent> events, int count) {
        assertThat(events).hasSize(count).allSatisfy(event -> {
            assertThat(event.getLevel().toString()).isEqualTo("WARN");
            assertThat(event.getFormattedMessage()).doesNotContain("private-connection-marker", "redis://",
                    "private-json-payload-marker", KEY, IDEM, REFUND);
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    private void begin() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        TransactionSynchronizationManager.initSynchronization();
    }

    private void finish(boolean committed) {
        List<TransactionSynchronization> callbacks = TransactionSynchronizationManager.getSynchronizations();
        try {
            if (committed) callbacks.forEach(TransactionSynchronization::afterCommit);
        } finally {
            callbacks.forEach(callback -> callback.afterCompletion(committed
                    ? TransactionSynchronization.STATUS_COMMITTED : TransactionSynchronization.STATUS_ROLLED_BACK));
            TransactionSynchronizationManager.clear();
        }
    }
}
