package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.PlanState;
import com.cnhsk.learning.api.Consumption;
import com.cnhsk.learning.api.Consumption.Source;
import com.cnhsk.learning.api.PaidFeature;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class EntitlementRedisIntegrationTest extends AbstractIntegrationTest {
    private static final long USER = 8_013_012L;
    private static final Instant NOW = Instant.parse("2026-11-08T03:00:00Z");
    private static final Instant PERIOD = Instant.parse("2026-10-31T17:00:00Z");
    private final AuthApi auth = mock(AuthApi.class);
    private final LedgerService ledger = mock(LedgerService.class);
    private final List<String> keys = new ArrayList<>();
    @Autowired private StringRedisTemplate redis;
    @Autowired private NamedParameterJdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private BillingConfig config;
    @Autowired private PlatformTransactionManager manager;
    private EntitlementService service;
    private String key;
    private String idem;
    private String refund;

    @BeforeEach
    void setUp() {
        key = "entitlement-redis-" + UUID.randomUUID();
        idem = "billing:idem:" + USER + ":" + key;
        refund = "billing:refund:" + USER + ":" + key;
        keys.addAll(List.of(idem, refund));
        when(auth.lockPlan(USER)).thenReturn(new PlanState("FREE", null, Map.of("TRANSLATE", 2), PERIOD));
        service = service(redis);
    }

    @AfterEach
    void cleanUp() {
        redis.delete(keys);
    }

    @Test
    void committedReceiptRoundTripsAllFieldsWithTwentyFourHourTtl() throws Exception {
        Consumption receipt = transaction().execute(status -> {
            Consumption result = service.consume(USER, PaidFeature.TRANSLATE, key);
            assertThat(redis.hasKey(idem)).isFalse();
            return result;
        });
        assertThat(receipt).isEqualTo(new Consumption(Source.QUOTA, null, PaidFeature.TRANSLATE, key, PERIOD));
        String payload = redis.opsForValue().get(idem);
        assertThat(payload).isNotBlank();
        assertThat(mapper.readValue(payload, Consumption.class)).isEqualTo(receipt);
        assertThat(redis.getExpire(idem, TimeUnit.SECONDS)).isBetween(86_390L, 86_400L);
        Consumption repeated = transaction().execute(status -> service.consume(USER, PaidFeature.TRANSLATE, key));
        assertThat(repeated).isEqualTo(receipt);
        verify(auth).lockPlan(USER);
        verify(auth).updateUsage(USER, Map.of("TRANSLATE", 3), PERIOD);
    }

    @Test
    void creditReceiptRoundTripsTransactionIdentifierAndFeature() throws Exception {
        when(auth.lockPlan(USER)).thenReturn(new PlanState("FREE", null, Map.of("GRADING", 10), PERIOD));
        when(ledger.balance(USER)).thenReturn(5);
        when(ledger.append(any())).thenReturn(new LedgerService.LedgerEntry(812L, 2, 5, 4));
        Consumption receipt = transaction().execute(status -> service.consume(USER, PaidFeature.GRADING, key));
        assertThat(receipt).isEqualTo(new Consumption(Source.CREDIT, 812L, PaidFeature.GRADING, key, PERIOD));
        assertThat(mapper.readValue(redis.opsForValue().get(idem), Consumption.class)).isEqualTo(receipt);
        Consumption repeated = transaction().execute(status -> service.consume(USER, PaidFeature.GRADING, key));
        assertThat(repeated).isEqualTo(receipt);
        verify(ledger).append(any());
    }

    @Test
    void callerRollbackPublishesNoReceiptAndDoesNotRetainTransactionMemo() {
        Consumption aborted = transaction().execute(status -> {
            Consumption result = service.consume(USER, PaidFeature.TRANSLATE, key);
            status.setRollbackOnly();
            return result;
        });
        assertThat(redis.hasKey(idem)).isFalse();
        Consumption retried = transaction().execute(status -> service.consume(USER, PaidFeature.TRANSLATE, key));
        assertThat(retried).isEqualTo(aborted);
        verify(auth, times(2)).updateUsage(USER, Map.of("TRANSLATE", 3), PERIOD);
        assertThat(redis.hasKey(idem)).isTrue();
    }

    @Test
    void secondLookupSeesRealRedisReceiptPublishedDuringPlanLock() throws Exception {
        Consumption published = new Consumption(Source.QUOTA, null, PaidFeature.TRANSLATE, key, PERIOD);
        String json = mapper.writeValueAsString(published);
        when(auth.lockPlan(USER)).thenAnswer(invocation -> {
            redis.opsForValue().set(idem, json, Duration.ofHours(24));
            return new PlanState("FREE", null, Map.of("TRANSLATE", 2), PERIOD);
        });
        Consumption result = transaction().execute(status -> service.consume(USER, PaidFeature.TRANSLATE, key));
        assertThat(result).isEqualTo(published);
        verify(auth, never()).updateUsage(anyLong(), anyMap(), any());
        verifyNoInteractions(ledger);
    }

    @Test
    void quotaRefundClaimsRealMarkerBeforeCommitAndKeepsItUntilNextVietnamMonth() {
        Consumption receipt = new Consumption(Source.QUOTA, null, PaidFeature.TRANSLATE, key, PERIOD);
        transaction().executeWithoutResult(status -> {
            service.refund(USER, receipt);
            assertThat(redis.opsForValue().get(refund)).isEqualTo("1");
        });
        long expected = Duration.between(NOW, Instant.parse("2026-11-30T17:00:00Z")).getSeconds();
        assertThat(redis.getExpire(refund, TimeUnit.SECONDS)).isBetween(expected - 10, expected);
        transaction().executeWithoutResult(status -> service.refund(USER, receipt));
        verify(auth).updateUsage(USER, Map.of("TRANSLATE", 1), PERIOD);
        verifyNoInteractions(ledger);
    }

    @Test
    void rolledBackRefundReleasesMarkerSoAnotherTransactionCanRefund() {
        Consumption receipt = new Consumption(Source.QUOTA, null, PaidFeature.TRANSLATE, key, PERIOD);
        transaction().executeWithoutResult(status -> {
            service.refund(USER, receipt);
            assertThat(redis.hasKey(refund)).isTrue();
            status.setRollbackOnly();
        });
        assertThat(redis.hasKey(refund)).isFalse();
        transaction().executeWithoutResult(status -> service.refund(USER, receipt));
        assertThat(redis.hasKey(refund)).isTrue();
        verify(auth, times(2)).updateUsage(USER, Map.of("TRANSLATE", 1), PERIOD);
    }

    @Test
    void refundDoesNotReplaceOrDeleteOriginalIdempotencyReceipt() {
        Consumption receipt = transaction().execute(status -> service.consume(USER, PaidFeature.TRANSLATE, key));
        String originalJson = redis.opsForValue().get(idem);
        transaction().executeWithoutResult(status -> service.refund(USER, receipt));
        assertThat(redis.opsForValue().get(idem)).isEqualTo(originalJson);
        Consumption repeated = transaction().execute(status -> service.consume(USER, PaidFeature.TRANSLATE, key));
        assertThat(repeated).isEqualTo(receipt);
        verify(auth, times(2)).lockPlan(USER);
        verify(auth, times(2)).updateUsage(eq(USER), anyMap(), eq(PERIOD));
    }

    @Test
    void injectedReadFailureUsesQuotaAndStillPublishesThroughHealthyRedis() {
        StringRedisTemplate injected = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(injected.opsForValue()).thenReturn(values);
        when(values.get(idem)).thenThrow(new RedisConnectionFailureException("injected read failure"));
        doAnswer(invocation -> {
            redis.opsForValue().set(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2, Duration.class));
            return null;
        }).when(values).set(eq(idem), anyString(), any(Duration.class));
        EntitlementService failingRead = service(injected);
        Consumption receipt = transaction().execute(status -> failingRead.consume(USER, PaidFeature.TRANSLATE, key));
        assertThat(receipt.source()).isEqualTo(Source.QUOTA);
        assertThat(redis.hasKey(idem)).isTrue();
        verify(auth).updateUsage(USER, Map.of("TRANSLATE", 3), PERIOD);
    }

    @Test
    void injectedPublicationFailureDoesNotTurnSuccessfulCommitIntoFailure() {
        StringRedisTemplate injected = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(injected.opsForValue()).thenReturn(values);
        when(values.get(idem)).thenAnswer(invocation -> redis.opsForValue().get(idem));
        doThrow(new RedisConnectionFailureException("injected write failure"))
                .when(values).set(eq(idem), anyString(), any(Duration.class));
        EntitlementService failingWrite = service(injected);
        Consumption receipt = transaction().execute(status -> failingWrite.consume(USER, PaidFeature.TRANSLATE, key));
        assertThat(receipt.source()).isEqualTo(Source.QUOTA);
        assertThat(redis.hasKey(idem)).isFalse();
        verify(auth).updateUsage(USER, Map.of("TRANSLATE", 3), PERIOD);
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(manager);
    }

    private EntitlementService service(StringRedisTemplate selectedRedis) {
        return new EntitlementService(auth, ledger, jdbc, selectedRedis, mapper, config, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
