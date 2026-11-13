package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import com.cnhsk.auth.api.*;
import com.cnhsk.learning.api.PaidFeature;
import com.cnhsk.learning.credit.web.PlanStatus.PlanCode;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

class WalletQueryServiceTest {
    private static final Instant NOW = Instant.parse("2031-11-15T10:00:00Z");
    private static final Instant MONTH = Instant.parse("2031-10-31T17:00:00Z");
    private final AuthApi auth = mock(AuthApi.class);
    private final LedgerService ledger = mock(LedgerService.class);
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final BillingConfig config = new BillingConfig();

    @Test void freeKeepsActualUsedAndReadsBalanceFromLedgerWithoutReset() throws Exception {
        arrange(new PlanState("FREE", null, Map.of("AI_GENERATE", 3, "TRANSLATE", 37), MONTH), "[]");
        var wallet = service(NOW).wallet();
        assertThat(wallet.balance()).isEqualTo(27);
        assertThat(wallet.plan().code()).isEqualTo(PlanCode.FREE);
        assertThat(wallet.plan().expiresAt()).isNull();
        assertThat(wallet.plan().active()).isTrue();
        assertThat(wallet.quota()).hasSize(4).extracting(q -> q.feature()).containsExactly(PaidFeature.values());
        assertThat(wallet.quota().get(0).used()).isEqualTo(3);
        assertThat(wallet.quota().get(0).remaining()).isEqualTo(7);
        assertThat(wallet.quota().get(1).used()).isEqualTo(37);
        assertThat(wallet.quota().get(1).remaining()).isZero();
        assertThatThrownBy(() -> wallet.quota().clear()).isInstanceOf(UnsupportedOperationException.class);
        verify(auth).readPlan(42);
        verify(auth, never()).lockPlan(anyLong());
        verify(auth, never()).updateUsage(anyLong(), anyMap(), any());
        verify(auth, never()).updatePlan(anyLong(), anyString(), any());
        verify(ledger).balance(42);
        verify(ledger, never()).append(any());
    }

    @Test void previousVietnameseMonthIsProjectedAsUnusedWithoutMutation() throws Exception {
        PlanState state = new PlanState("FREE", null, Map.of("AI_GENERATE", 3), MONTH.minusNanos(1));
        arrange(state, "{}");
        assertThat(service(NOW).wallet().quota()).allSatisfy(q -> {
            assertThat(q.used()).isZero(); assertThat(q.remaining()).isEqualTo(10);
        });
        assertThat(state.freeUsage()).containsEntry("AI_GENERATE", 3);
        verify(auth, never()).updateUsage(anyLong(), anyMap(), any());
    }

    @Test void premiumUsesBenefitsWithMissingFeatureZeroAndPreservesGrantWhenCatalogInactive() throws Exception {
        arrange(new PlanState("PREMIUM", NOW.plusSeconds(1), Map.of("TRANSLATE", 3), MONTH), "{\"AI_GENERATE\":25}");
        var wallet = service(NOW).wallet();
        assertThat(wallet.plan().code()).isEqualTo(PlanCode.PREMIUM);
        assertThat(wallet.plan().expiresAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(wallet.quota().get(0).limit()).isEqualTo(25);
        assertThat(wallet.quota().get(1).limit()).isZero();
        assertThat(wallet.quota().get(1).used()).isEqualTo(3);
        assertThat(wallet.quota().get(1).remaining()).isZero();
    }

    @ParameterizedTest @ValueSource(longs = {-1, 0})
    void premiumAtOrBeforeExpiryReadsAsFreeWithoutDowngrade(long delta) throws Exception {
        arrange(new PlanState("PREMIUM", NOW.plusSeconds(delta), Map.of(), MONTH), "{}");
        var wallet = service(NOW).wallet();
        assertThat(wallet.plan().code()).isEqualTo(PlanCode.FREE);
        assertThat(wallet.plan().expiresAt()).isNull();
        verify(auth, never()).updatePlan(anyLong(), anyString(), any());
    }

    @ParameterizedTest @CsvSource({
            "2031-11-30T16:59:59Z,2031-11-30T17:00:00Z",
            "2031-11-30T17:00:00Z,2031-12-31T17:00:00Z",
            "2031-12-31T17:00:00Z,2032-01-31T17:00:00Z",
            "2032-02-28T17:00:00Z,2032-02-29T17:00:00Z",
            "2032-02-29T17:00:00Z,2032-03-31T17:00:00Z"})
    void resetAtUsesNextVietnameseCalendarMonth(String now, String next) throws Exception {
        arrange(new PlanState("FREE", null, Map.of(), Instant.EPOCH), "{}");
        assertThat(service(Instant.parse(now)).wallet().resetAt()).isEqualTo(Instant.parse(next));
    }

    @Test void absentPlanIsUnauthenticatedForBothQueriesAndNeverNotFoundOrEmptyHistory() {
        when(auth.currentUser()).thenReturn(new CurrentUser(42, Set.of(RoleCode.LEARNER), false));
        when(auth.readPlan(42)).thenReturn(Optional.empty());
        for (Runnable query : List.<Runnable>of(() -> service(NOW).wallet(), () -> service(NOW).history(0, 20))) {
            assertThatThrownBy(query::run).isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.getCode()).isEqualTo(ErrorCode.UNAUTHENTICATED);
                assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
            });
        }
        verifyNoInteractions(jdbc, ledger);
    }

    @Test void invalidPaginationFailsWith422BeforeAnyDatabaseAccess() {
        for (int[] parameters : List.of(new int[]{-1,20}, new int[]{0,0}, new int[]{0,101})) {
            assertThatThrownBy(() -> service(NOW).history(parameters[0], parameters[1]))
                    .isInstanceOfSatisfying(ApiException.class, exception -> {
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                        assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    });
        }
        verifyNoInteractions(auth, jdbc, ledger);
    }

    @ParameterizedTest @ValueSource(strings = {"{\"TRANSLATE\":-1}","{\"TRANSLATE\":1.2}","{\"TRANSLATE\":2147483648}","[]","broken"})
    void corruptPremiumBenefitsFailWithoutEchoingStoredData(String benefits) throws Exception {
        arrange(new PlanState("PREMIUM", NOW.plusSeconds(1), Map.of(), MONTH), benefits);
        assertThatThrownBy(() -> service(NOW).wallet()).isInstanceOf(IllegalStateException.class)
                .hasMessage("Dữ liệu hạn mức không hợp lệ.");
    }

    @Test void bothEntryPointsDeclareReadOnlyRepeatableRead() throws Exception {
        for (var method : List.of(WalletQueryService.class.getMethod("wallet"), WalletQueryService.class.getMethod("history", int.class, int.class))) {
            var transaction = method.getAnnotation(Transactional.class);
            assertThat(transaction.readOnly()).isTrue();
            assertThat(transaction.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
        }
    }

    private WalletQueryService service(Instant now) {
        return new WalletQueryService(auth, ledger, jdbc, new ObjectMapper(), config, Clock.fixed(now, ZoneOffset.UTC));
    }

    private void arrange(PlanState state, String benefits) throws Exception {
        when(auth.currentUser()).thenReturn(new CurrentUser(42, Set.of(RoleCode.LEARNER), false));
        when(auth.readPlan(42)).thenReturn(Optional.of(state));
        when(ledger.balance(42)).thenReturn(27);
        ResultSet row = mock(ResultSet.class);
        when(row.getString("name")).thenReturn("Public plan");
        when(row.getString("benefits")).thenReturn(benefits);
        when(jdbc.query(anyString(), anyMap(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any()))
                .thenAnswer(invocation -> {
                    assertThat((String) invocation.getArgument(0)).doesNotContainIgnoringCase("FOR UPDATE");
                    RowMapper<?> mapping = invocation.getArgument(2);
                    return List.of(mapping.mapRow(row, 0));
                });
    }
}
