package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.cnhsk.auth.api.*;
import com.cnhsk.learning.credit.web.RedeemRequest;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(OutputCaptureExtension.class)
class RedeemServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-08T00:00:00Z");
    private static final String RAW = "0123456789ABCDEF";
    private final AuthApi auth = mock(AuthApi.class);
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final LedgerService ledger = mock(LedgerService.class);
    private final CardCodes codes = mock(CardCodes.class);
    private final RedeemRateLimitService attempts = mock(RedeemRateLimitService.class);
    private final Clock clock = mock(Clock.class);
    private final RedeemService service = new RedeemService(auth, jdbc, ledger, codes, attempts, clock);

    @BeforeEach void setup() {
        when(auth.currentUser()).thenReturn(new CurrentUser(42, Set.of(RoleCode.LEARNER), false));
        when(auth.lockPlan(42)).thenReturn(new PlanState("FREE", null, Map.of("TRANSLATE", 7), NOW));
        when(clock.instant()).thenReturn(NOW);
        when(codes.normalize(RAW)).thenReturn(java.util.Optional.of(RAW));
        when(codes.hash(RAW)).thenReturn("synthetic-digest");
        when(ledger.append(any())).thenReturn(new LedgerService.LedgerEntry(10, 1, 0, 100));
    }

    @Test void invalidSchemaFailsBeforeAuthRedisOrDatabaseEvenForDirectCalls() {
        for (RedeemRequest body : new RedeemRequest[] {null, new RedeemRequest(null), new RedeemRequest("x".repeat(41))}) {
            expect(() -> service.redeem(body, "192.0.2.42"), ErrorCode.VALIDATION_FAILED);
        }
        verifyNoInteractions(auth, attempts, jdbc, ledger, codes, clock);
    }

    @Test void blockedAttemptDoesNotNormalizeLockClaimOrIncrement() {
        doThrow(new ApiException(ErrorCode.RATE_LIMITED, org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                "blocked", Map.of())).when(attempts).check(42, "192.0.2.42");
        expect(() -> redeem(), ErrorCode.RATE_LIMITED);
        verifyNoInteractions(codes, jdbc, ledger, clock);
        verify(auth, never()).lockPlan(anyLong());
        verify(attempts, never()).recordFailure(anyLong(), anyString());
    }

    @Test void invalidFormatCountsFailureWithoutLockingOrQuerying() {
        when(codes.normalize(RAW)).thenReturn(java.util.Optional.empty());
        expect(() -> redeem(), ErrorCode.INVALID_CARD_CODE);
        verify(attempts).recordFailure(42, "192.0.2.42");
        verify(auth, never()).lockPlan(anyLong());
        verifyNoInteractions(jdbc, ledger);
    }

    @Test void nonLearnerIsRejectedBeforeRateLimiter() {
        when(auth.currentUser()).thenReturn(new CurrentUser(42, Set.of(RoleCode.ADMIN), true));
        expect(() -> redeem(), ErrorCode.FORBIDDEN);
        verifyNoInteractions(attempts, jdbc, ledger, codes);
    }

    @Test void zeroAtomicClaimCountsFailureAndDoesNotAppend() {
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        expect(() -> redeem(), ErrorCode.INVALID_CARD_CODE);
        verify(attempts).recordFailure(42, "192.0.2.42");
        verifyNoInteractions(ledger);
    }

    @Test void locksUserBeforeClockAtomicClaimPlanAndLedger() throws Exception {
        card("CREDIT", 100, null);
        plan();
        var result = redeem();
        var order = inOrder(attempts, codes, auth, clock, jdbc, ledger);
        order.verify(attempts).check(42, "192.0.2.42");
        order.verify(codes).normalize(RAW);
        order.verify(auth).lockPlan(42);
        order.verify(clock).instant();
        order.verify(codes).hash(RAW);
        order.verify(jdbc).query(contains("UPDATE learning.credit_cards"), any(MapSqlParameterSource.class), any(RowMapper.class));
        order.verify(jdbc).query(contains("SELECT name,price"), anyMap(), any(RowMapper.class));
        order.verify(ledger).append(any());
        ArgumentCaptor<LedgerService.AppendCommand> command = ArgumentCaptor.forClass(LedgerService.AppendCommand.class);
        verify(ledger).append(command.capture());
        assertThat(command.getValue().amount()).isEqualTo(100);
        assertThat(command.getValue().txType()).isEqualTo("TOPUP");
        assertThat(command.getValue().cardId()).isEqualTo(7L);
        assertThat(result.amount()).isEqualTo(100);
        assertThat(result.balance()).isEqualTo(100);
        assertThat(result.plan().expiresAt()).isNull();
        assertThat(result.plan().active()).isTrue();
        verify(auth, never()).updatePlan(anyLong(), anyString(), any());
        verify(auth, never()).updateUsage(anyLong(), anyMap(), any());
    }

    @ParameterizedTest @ValueSource(strings = {"FREE", "EXPIRED", "ACTIVE"})
    void subscriptionUsesEffectiveExpiryAndExactDecimalReason(String state) throws Exception {
        Instant old = state.equals("ACTIVE") ? NOW.plusSeconds(864000) : NOW.minusSeconds(1);
        when(auth.lockPlan(42)).thenReturn(new PlanState(state.equals("FREE") ? "FREE" : "PREMIUM",
                state.equals("FREE") ? null : old, Map.of("TRANSLATE", 7), NOW));
        card("SUBSCRIPTION", 30, 30);
        plan();
        Instant expected = (state.equals("ACTIVE") ? old : NOW).plusSeconds(30L * 86400);
        assertThat(redeem().plan().expiresAt()).isEqualTo(expected);
        verify(auth).updatePlan(42, "PREMIUM", expected);
        ArgumentCaptor<LedgerService.AppendCommand> command = ArgumentCaptor.forClass(LedgerService.AppendCommand.class);
        verify(ledger).append(command.capture());
        assertThat(command.getValue().amount()).isZero();
        assertThat(command.getValue().txType()).isEqualTo("SUBSCRIPTION");
        assertThat(command.getValue().reason()).isEqualTo("PREMIUM +30 ngày, giá niêm yết 120000.00");
        verify(auth, never()).updateUsage(anyLong(), anyMap(), any());
    }

    @Test void missingPremiumIsFixedInternalFailureWithoutLedger() throws Exception {
        card("SUBSCRIPTION", 30, 30);
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of());
        expect(() -> redeem(), ErrorCode.INTERNAL_ERROR);
        verifyNoInteractions(ledger);
        verify(auth, never()).updatePlan(anyLong(), anyString(), any());
    }

    @Test void runtimeFailureWarnsClassOnlyAndRemovesCause(CapturedOutput output) throws Exception {
        card("CREDIT", 100, null);
        plan();
        when(ledger.append(any())).thenThrow(new IllegalStateException(RAW + " synthetic-digest synthetic-detail",
                new RuntimeException("synthetic-private-cause")));
        assertThatThrownBy(() -> redeem()).isInstanceOf(ApiException.class)
                .hasMessage("Đã xảy ra lỗi nội bộ.").hasNoCause();
        assertThat(output.getAll()).contains("WARN", "IllegalStateException")
                .doesNotContain(RAW, "synthetic-digest", "synthetic-detail", "synthetic-private-cause");
    }

    @Test void transactionUsesReadCommitted() throws Exception {
        var annotation = RedeemService.class.getMethod("redeem", RedeemRequest.class, String.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.isolation()).isEqualTo(org.springframework.transaction.annotation.Isolation.READ_COMMITTED);
    }

    private com.cnhsk.learning.credit.web.RedeemResult redeem() { return service.redeem(new RedeemRequest(RAW), "192.0.2.42"); }

    private void card(String type, int value, Integer days) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(7L);
        when(rs.getString("card_type")).thenReturn(type);
        when(rs.getInt("value")).thenReturn(value);
        when(rs.getObject("plan_days", Integer.class)).thenReturn(days);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenAnswer(call ->
                List.of(((RowMapper<?>) call.getArgument(2)).mapRow(rs, 0)));
    }

    private void plan() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("name")).thenReturn("Synthetic plan");
        when(rs.getBigDecimal("price")).thenReturn(new BigDecimal("120000.00"));
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenAnswer(call ->
                List.of(((RowMapper<?>) call.getArgument(2)).mapRow(rs, 0)));
    }

    private static void expect(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class).hasNoCause()
                .satisfies(f -> assertThat(((ApiException) f).getCode()).isEqualTo(code));
    }
}
