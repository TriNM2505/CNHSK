package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.concurrent.atomic.AtomicInteger;
import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.learning.credit.web.Benefits;
import com.cnhsk.learning.credit.web.PlanStatus.PlanCode;
import com.cnhsk.learning.credit.web.PlanUpdateRequest;
import com.cnhsk.learning.credit.web.PlanView;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

class PlanAdminServiceTest {
    private static final Instant NOW = Instant.parse("2031-11-15T10:00:00Z");
    private final AuthApi auth = mock(AuthApi.class);
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final AuditService audit = mock(AuditService.class);
    private final Clock clock = mock(Clock.class);

    @BeforeEach void admin() {
        when(auth.currentUser()).thenReturn(new CurrentUser(42, Set.of(RoleCode.ADMIN), false));
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @ParameterizedTest @ValueSource(strings = {"LEARNER", "TEACHER", "MODERATOR", "CONTENT_EDITOR", "ACCOUNTANT"})
    void directCallsRequireAdminBeforeCatalogCountsAndAudit(String role) {
        when(auth.currentUser()).thenReturn(new CurrentUser(42, Set.of(RoleCode.valueOf(role)), true));
        expect(() -> service().listPlans(), ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN);
        expect(() -> service().updatePlan(PlanCode.PREMIUM, premium(new Benefits())), ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN);
        verifyNoInteractions(jdbc, audit, clock);
        verify(auth, never()).countActivePlan(anyString(), any());
    }

    @Test void absentAndPartialCatalogAlwaysReturnThreeCodesWithRealCountsAtOneInstant() throws Exception {
        stubRead(List.of(row("FREE", "Miễn phí", null, null, "0.00", "{}", true)));
        when(auth.countActivePlan("FREE", NOW)).thenReturn(7L);
        when(auth.countActivePlan("PREMIUM", NOW)).thenReturn(2L);
        when(auth.countActivePlan("PREMIUM_PLUS", NOW)).thenReturn(0L);
        List<PlanView> plans = service().listPlans();
        assertThat(plans).extracting(PlanView::code).containsExactly(PlanCode.FREE, PlanCode.PREMIUM, PlanCode.PREMIUM_PLUS);
        assertThat(plans.get(0)).isEqualTo(new PlanView(PlanCode.FREE, true, "Miễn phí", null, null, "0.00", new Benefits(), true, 7));
        for (int index : List.of(1, 2)) {
            PlanView missing = plans.get(index);
            assertThat(missing.exists()).isFalse();
            assertThat(missing.name()).isNull(); assertThat(missing.description()).isNull();
            assertThat(missing.durationDays()).isNull(); assertThat(missing.price()).isNull();
            assertThat(missing.isActive()).isNull(); assertThat(missing.benefits()).isEmpty();
        }
        assertThat(plans.get(1).activeUsers()).isEqualTo(2);
        verify(jdbc, times(1)).query(anyString(), anyMap(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any());
        verify(auth).countActivePlan("FREE", NOW);
        verify(auth).countActivePlan("PREMIUM", NOW);
        verify(auth).countActivePlan("PREMIUM_PLUS", NOW);
        verify(clock, times(1)).instant();
        verifyNoInteractions(audit);
        verify(auth, never()).lockPlan(anyLong());
        verify(auth, never()).updatePlan(anyLong(), anyString(), any());
        verify(auth, never()).updateUsage(anyLong(), anyMap(), any());
        verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test void emptyCatalogDoesNotInventNamesOrInsertRows() throws Exception {
        stubRead(List.of());
        assertThat(service().listPlans()).allSatisfy(plan -> {
            assertThat(plan.exists()).isFalse(); assertThat(plan.name()).isNull();
            assertThat(plan.price()).isNull(); assertThat(plan.benefits()).isEmpty();
        });
        verifyNoInteractions(audit);
        verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test void nullRequestsAndUnavailableCodeFailBeforeCatalogAndAudit() {
        expect(() -> service().updatePlan(null, premium(new Benefits())), ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY);
        expect(() -> service().updatePlan(PlanCode.PREMIUM, null), ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY);
        expect(() -> service().updatePlan(PlanCode.PREMIUM_PLUS, premium(new Benefits())), ErrorCode.PLAN_NOT_AVAILABLE, HttpStatus.UNPROCESSABLE_ENTITY);
        verifyNoInteractions(jdbc, audit, clock);
    }

    @Test void missingFreeCannotBeCreatedByTheAdminEndpoint() throws Exception {
        stubRead(List.of());
        expect(() -> service().updatePlan(PlanCode.FREE, new PlanUpdateRequest("Free", null, null, null, null, null)),
                ErrorCode.PLAN_NOT_AVAILABLE, HttpStatus.UNPROCESSABLE_ENTITY);
        verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class));
        verifyNoInteractions(audit);
        verify(auth, never()).countActivePlan(anyString(), any());
    }

    @Test void activePremiumRejectsEffectiveBenefitChangesAfterTakingCompatibleCatalogLock() throws Exception {
        stubRead(List.of(row("PREMIUM", "Cũ", null, 30, "1.00", "{\"TRANSLATE\":10}", true)));
        when(auth.countActivePlan("PREMIUM", NOW)).thenReturn(1L);
        expect(() -> service().updatePlan(PlanCode.PREMIUM, premium(new Benefits(Map.of("TRANSLATE", 11)))),
                ErrorCode.PLAN_IN_USE, HttpStatus.CONFLICT);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), anyMap(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any());
        assertThat(sql.getValue()).contains("FOR NO KEY UPDATE").doesNotContain("FOR UPDATE");
        verify(jdbc, never()).query(anyString(), any(MapSqlParameterSource.class), org.mockito.ArgumentMatchers.<RowMapper<Object>>any());
        verifyNoInteractions(audit);
        verify(auth, never()).lockPlan(anyLong());
    }

    @Test void activePremiumAllowsSparseZeroEqualityAndChangesToOtherCatalogFields() throws Exception {
        stubRead(List.of(row("PREMIUM", "Cũ", "Trước", 30, "1.00", "{\"TRANSLATE\":10}", true)));
        ResultSet saved = row("PREMIUM", "Mới", null, 365, "9999999999.99", "{\"AI_GENERATE\":0,\"TRANSLATE\":10}", false);
        stubWrite(saved);
        when(auth.countActivePlan("PREMIUM", NOW)).thenReturn(5L);
        var request = new PlanUpdateRequest("Mới", null, 365, "9999999999.99",
                new Benefits(Map.of("AI_GENERATE", 0, "TRANSLATE", 10)), false);
        PlanView result = service().updatePlan(PlanCode.PREMIUM, request);
        assertThat(result.name()).isEqualTo("Mới"); assertThat(result.durationDays()).isEqualTo(365);
        assertThat(result.price()).isEqualTo("9999999999.99"); assertThat(result.isActive()).isFalse();
        assertThat(result.activeUsers()).isEqualTo(5);
        ArgumentCaptor<Map<String, ?>> details = detailCaptor();
        verify(audit).recordDetail(eq(42L), eq("PLAN_UPDATED"), eq("PLAN"), details.capture());
        Map<String, ?> detail = details.getValue();
        assertThat(detail).containsOnlyKeys("code", "before", "after");
        assertThat(detail.get("code")).isEqualTo("PREMIUM");
        assertSnapshot(detail.get("before")); assertSnapshot(detail.get("after"));
        verify(auth, never()).lockPlan(anyLong());
        verify(auth, never()).updatePlan(anyLong(), anyString(), any());
    }

    @Test void freeUpdateOnlyBindsEditableColumnsEvenWhenPremiumFieldsAreSupplied() throws Exception {
        stubRead(List.of(row("FREE", "Trước", "Cũ", null, "0.00", "{\"TRANSLATE\":10}", true)));
        stubWrite(row("FREE", "Sau", null, null, "0.00", "{\"TRANSLATE\":10}", true));
        service().updatePlan(PlanCode.FREE, new PlanUpdateRequest("Sau", null, 365, "999.99", new Benefits(), false));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parameters.capture(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any());
        assertThat(sql.getValue()).startsWith("UPDATE learning.plans SET name=:name,description=:description WHERE code=:code");
        assertThat(parameters.getValue().getValues()).containsOnlyKeys("code", "name", "description");
        assertThat(parameters.getValue().getValue("description")).isNull();
        verify(audit).recordDetail(eq(42L), eq("PLAN_UPDATED"), eq("PLAN"), anyMap());
    }

    @Test void winningAbsentPremiumInsertProducesCreatedAuditWithNullBefore() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        ResultSet saved = row("PREMIUM", "Gói", null, 30, "1.20", "{}", true);
        when(jdbc.query(anyString(), anyMap(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any()))
                .thenAnswer(invocation -> reads.getAndIncrement() == 0 ? List.of() : List.of(
                        ((RowMapper<?>) invocation.getArgument(2)).mapRow(saved, 0)));
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        PlanView result = service().updatePlan(PlanCode.PREMIUM, premium(new Benefits()));
        assertThat(result.exists()).isTrue(); assertThat(result.price()).isEqualTo("1.20");
        ArgumentCaptor<Map<String, ?>> details = detailCaptor();
        verify(audit).recordDetail(eq(42L), eq("PLAN_CREATED"), eq("PLAN"), details.capture());
        assertThat(details.getValue().get("before")).isNull(); assertSnapshot(details.getValue().get("after"));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), any(MapSqlParameterSource.class));
        assertThat(sql.getValue()).contains("ON CONFLICT (code) DO NOTHING");
        verify(jdbc, never()).query(anyString(), any(MapSqlParameterSource.class), org.mockito.ArgumentMatchers.<RowMapper<Object>>any());
    }

    @Test void transactionContractsProvideRepeatableReadsAndReadCommittedMutation() throws Exception {
        Transactional read = PlanAdminService.class.getMethod("listPlans").getAnnotation(Transactional.class);
        assertThat(read.readOnly()).isTrue(); assertThat(read.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
        Transactional write = PlanAdminService.class.getMethod("updatePlan", PlanCode.class, PlanUpdateRequest.class)
                .getAnnotation(Transactional.class);
        assertThat(write.readOnly()).isFalse(); assertThat(write.isolation()).isEqualTo(Isolation.READ_COMMITTED);
    }

    private PlanAdminService service() { return new PlanAdminService(auth, jdbc, new ObjectMapper(), audit, clock); }
    private static PlanUpdateRequest premium(Benefits benefits) { return new PlanUpdateRequest("Gói", null, 30, "1.2", benefits, true); }
    private void stubRead(List<ResultSet> rows) {
        when(jdbc.query(anyString(), anyMap(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any()))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapping = invocation.getArgument(2);
                    java.util.ArrayList<Object> result = new java.util.ArrayList<>();
                    for (int index = 0; index < rows.size(); index++) result.add(mapping.mapRow(rows.get(index), index));
                    return result;
                });
    }
    private void stubWrite(ResultSet saved) {
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), org.mockito.ArgumentMatchers.<RowMapper<Object>>any()))
                .thenAnswer(invocation -> List.of(((RowMapper<?>) invocation.getArgument(2)).mapRow(saved, 0)));
    }
    private static ResultSet row(String code, String name, String description, Integer days, String price, String benefits, boolean active) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getString("code")).thenReturn(code); when(row.getString("name")).thenReturn(name);
        when(row.getString("description")).thenReturn(description); when(row.getObject("duration_days", Integer.class)).thenReturn(days);
        when(row.getBigDecimal("price")).thenReturn(new BigDecimal(price)); when(row.getString("benefits")).thenReturn(benefits);
        when(row.getBoolean("is_active")).thenReturn(active); return row;
    }
    private static void assertSnapshot(Object value) {
        assertThat(value).isInstanceOfSatisfying(Map.class, snapshot -> assertThat(snapshot)
                .containsOnlyKeys("name", "description", "durationDays", "price", "benefits", "isActive"));
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<Map<String, ?>> detailCaptor() { return ArgumentCaptor.forClass((Class) Map.class); }
    private static void expect(Runnable action, ErrorCode code, HttpStatus status) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(code); assertThat(error.getStatus()).isEqualTo(status);
        });
    }
}
