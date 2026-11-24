package com.cnhsk.learning.grading;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.cnhsk.learning.api.Consumption;
import com.cnhsk.learning.api.EntitlementApi;
import com.cnhsk.learning.api.PaidFeature;
import com.cnhsk.learning.credit.LedgerService;
import com.cnhsk.learning.grading.GradingRepository.RequestRow;
import com.cnhsk.learning.grading.web.GradingRequestViews.Criteria;
import com.cnhsk.learning.grading.web.GradingResultWrite;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TeacherGradingServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private GradingRepository repository;
    private EntitlementApi entitlement;
    private LedgerService ledger;
    private GradingConfig config;
    private TeacherGradingService service;

    @BeforeEach void setup() {
        repository = mock(GradingRepository.class); entitlement = mock(EntitlementApi.class);
        ledger = mock(LedgerService.class);
        config = new GradingConfig(); config.setMaxActiveClaims(3); config.setGradingHours(24); config.setTeacherPayoutCredits(0);
        service = new TeacherGradingService(repository, settings(config), entitlement, ledger, mapper, Clock.fixed(NOW, ZoneOffset.UTC),
                org.mockito.Mockito.mock(RubricGrading.class));
    }

    @Test void AT06_queueMapsOnlyMetadataWithTwoRepositoryCalls() {
        when(repository.countQueue(9, NOW)).thenReturn(1L);
        when(repository.findQueue(9, NOW, 0, 20)).thenReturn(List.of(new GradingRepository.QueueRow(42, 50, null, NOW, NOW.plusSeconds(3600), "ESSAY")));
        var queue = service.queue(9, 0, 20);
        assertThat(queue.totalElements()).isEqualTo(1); assertThat(queue.items()).hasSize(1);
        var item = queue.items().get(0);
        assertThat(item.reference()).isEqualTo("GR-000042"); assertThat(item.hanziCount()).isEqualTo(50);
        assertThat(item.hskLevel()).isNull(); assertThat(item.payoutCredits()).isZero(); assertThat(item.mode()).isEqualTo("ESSAY");
        assertThatThrownBy(() -> queue.items().clear()).isInstanceOf(UnsupportedOperationException.class);
        verify(repository).countQueue(9, NOW); verify(repository).findQueue(9, NOW, 0, 20);
        verifyNoMoreInteractions(repository); verifyNoInteractions(entitlement);
    }

    @Test void FR010_mineMapsTheAlreadyOrderedAssignedProjection() {
        when(repository.findAssigned(9)).thenReturn(List.of(new GradingRepository.AssignedRow(42, 50, NOW.minusSeconds(10), NOW, "SPEAKING")));
        var items = service.mine(9);
        assertThat(items).hasSize(1); assertThat(items.get(0).gradingDeadline()).isEqualTo(NOW);
        assertThat(items.get(0).assignedAt()).isEqualTo(NOW.minusSeconds(10)); assertThat(items.get(0).mode()).isEqualTo("SPEAKING");
        verify(repository).findAssigned(9); verifyNoMoreInteractions(repository); verifyNoInteractions(entitlement);
    }

    @Test void FR009_invalidPaginationDoesNotQuery() {
        failure(() -> service.queue(9, -1, 20), ErrorCode.VALIDATION_FAILED, 422);
        failure(() -> service.queue(9, 0, 0), ErrorCode.VALIDATION_FAILED, 422);
        failure(() -> service.queue(9, 0, 51), ErrorCode.VALIDATION_FAILED, 422);
        verifyNoInteractions(repository, entitlement);
    }

    @Test void AT07_claimLocksBeforeCountingAndUsesServerDeadlines() {
        when(repository.find(42)).thenReturn(Optional.of(row(7, "PENDING", null)), Optional.of(row(7, "ASSIGNED", 9L)));
        when(repository.countActiveClaims(9)).thenReturn(2L);
        when(repository.claim(42, 9, NOW, NOW.plusSeconds(24 * 3600))).thenReturn(true);
        var detail = service.claim(9, 42);
        assertThat(detail.status()).isEqualTo("ASSIGNED"); assertThat(detail.essay()).isEqualTo("字".repeat(50));
        assertThat(detail.result()).isNull(); assertThat(detail.payoutCredits()).isZero();
        var order = inOrder(repository);
        order.verify(repository).lockTeacherClaim(9); order.verify(repository).find(42);
        order.verify(repository).countActiveClaims(9); order.verify(repository).claim(42, 9, NOW, NOW.plusSeconds(24 * 3600));
        verifyNoInteractions(entitlement);
    }

    @Test void AT08_selfGradingHasPriorityOverTheClaimLimit() {
        when(repository.find(42)).thenReturn(Optional.of(row(9, "PENDING", null)));
        failure(() -> service.claim(9, 42), ErrorCode.GRADING_SELF_GRADING, 403);
        verify(repository, never()).countActiveClaims(anyLong());
        verify(repository, never()).claim(anyLong(), anyLong(), any(), any()); verifyNoInteractions(entitlement);
    }

    @Test void AT08_limitIsCheckedBeforeTheConditionalUpdate() {
        when(repository.find(42)).thenReturn(Optional.of(row(7, "PENDING", null)));
        when(repository.countActiveClaims(9)).thenReturn(3L);
        failure(() -> service.claim(9, 42), ErrorCode.GRADING_CLAIM_LIMIT, 422);
        verify(repository, never()).claim(anyLong(), anyLong(), any(), any()); verifyNoInteractions(entitlement);
    }

    @Test void Q_T03_02_missingOrLostClaimsUse409() {
        when(repository.find(42)).thenReturn(Optional.empty());
        failure(() -> service.claim(9, 42), ErrorCode.GRADING_ALREADY_CLAIMED, 409);
        when(repository.find(42)).thenReturn(Optional.of(row(7, "PENDING", null)));
        failure(() -> service.claim(9, 42), ErrorCode.GRADING_ALREADY_CLAIMED, 409);
        verifyNoInteractions(entitlement);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "COMPLETED", "FAILED", "EXPIRED"})
    void AT09_nonAssignedStatesAreForbiddenBeforeReadingTheEssay(String status) {
        var row = row(7, status, 9L); ((com.fasterxml.jackson.databind.node.ObjectNode) row.payload()).remove("essay");
        when(repository.find(42)).thenReturn(Optional.of(row));
        failure(() -> service.get(9, 42), ErrorCode.GRADING_NOT_ASSIGNEE, 403);
        failure(() -> service.release(9, 42), ErrorCode.GRADING_NOT_ASSIGNEE, 403);
        verify(repository, never()).release(anyLong(), anyLong(), any()); verifyNoInteractions(entitlement);
    }

    @Test void AT09_missingAndOtherTeachersAssignmentsAreForbidden() {
        when(repository.find(42)).thenReturn(Optional.empty());
        failure(() -> service.get(9, 42), ErrorCode.GRADING_NOT_ASSIGNEE, 403);
        when(repository.find(42)).thenReturn(Optional.of(row(7, "ASSIGNED", 10L)));
        failure(() -> service.get(9, 42), ErrorCode.GRADING_NOT_ASSIGNEE, 403);
    }

    @Test void AT10_releaseToPendingHasNoClaimLockOrRefund() {
        when(repository.find(42)).thenReturn(Optional.of(row(7, "ASSIGNED", 9L)), Optional.of(row(7, "PENDING", null)));
        when(repository.release(42, 9, NOW)).thenReturn(true);
        service.release(9, 42);
        verify(repository, never()).lockTeacherClaim(anyLong()); verifyNoInteractions(entitlement);
    }

    @Test void AT10_expiredReleaseRefundsTheLearnerUsingTheStoredReceipt() {
        var assigned = row(7, "ASSIGNED", 9L);
        when(repository.find(42)).thenReturn(Optional.of(assigned), Optional.of(row(7, "EXPIRED", 9L)));
        when(repository.release(42, 9, NOW)).thenReturn(true);
        service.release(9, 42);
        verify(entitlement).refund(7, mapper.convertValue(assigned.payload().get("consumption"), Consumption.class));
        verify(entitlement, never()).consume(anyLong(), any(), anyString());
        verify(repository, never()).lockTeacherClaim(anyLong());
    }

    @Test void AT10_lostReleaseDoesNotRefund() {
        when(repository.find(42)).thenReturn(Optional.of(row(7, "ASSIGNED", 9L)));
        failure(() -> service.release(9, 42), ErrorCode.GRADING_NOT_ASSIGNEE, 403);
        verifyNoInteractions(entitlement); verify(repository, never()).lockTeacherClaim(anyLong());
    }

    @Test void AT12_completeUsesConditionalUpdateBeforePayoutAndReturnsTheStoredResult() {
        config.setTeacherPayoutCredits(2);
        var assigned = row(7, "ASSIGNED", 9L);
        var write = new GradingResultWrite(new Criteria(20, 18, 21, 19), "Nhận xét", List.of());
        var result = mapper.createObjectNode(); result.set("criteria", mapper.valueToTree(write.criteria())); result.putArray("corrections");
        var completed = new RequestRow(42, 7, "COMPLETED", assigned.payload(), result, 9L, NOW, assigned.deadline(),
                78, write.feedback(), 2, NOW, NOW, null);
        when(repository.find(42)).thenReturn(Optional.of(assigned), Optional.of(completed));
        when(repository.complete(42, 9, NOW, 78, write.feedback(), result, 2)).thenReturn(true);
        var detail = service.complete(9, 42, write);
        assertThat(detail.status()).isEqualTo("COMPLETED"); assertThat(detail.result().score()).isEqualTo(78);
        assertThat(detail.result().criteria()).isEqualTo(write.criteria()); assertThat(detail.payoutCredits()).isEqualTo(2);
        var order = inOrder(repository, ledger);
        order.verify(repository).find(42); order.verify(repository).complete(42, 9, NOW, 78, write.feedback(), result, 2);
        order.verify(ledger).append(new LedgerService.AppendCommand(9, "TEACHER_PAYOUT", 2, "GRADING", null, 42L, null, null, null, null));
        order.verify(repository).find(42);
        verify(repository, never()).lockTeacherClaim(anyLong()); verifyNoInteractions(entitlement);
    }

    @Test void AT11_completionAtTheDeadlineIsAllowedAndZeroPayoutDoesNotCallLedger() {
        var assigned = row(7, "ASSIGNED", 9L);
        assigned = new RequestRow(42, 7, "ASSIGNED", assigned.payload(), null, 9L, NOW, NOW, null, null, null, NOW, null, null);
        var write = new GradingResultWrite(new Criteria(0, 25, 0, 25), "Nhận xét", List.of());
        var result = mapper.createObjectNode(); result.set("criteria", mapper.valueToTree(write.criteria())); result.putArray("corrections");
        var completed = new RequestRow(42, 7, "COMPLETED", assigned.payload(), result, 9L, NOW, NOW, 50, write.feedback(), 0, NOW, NOW, null);
        when(repository.find(42)).thenReturn(Optional.of(assigned), Optional.of(completed));
        when(repository.complete(42, 9, NOW, 50, write.feedback(), result, 0)).thenReturn(true);
        assertThat(service.complete(9, 42, write).result().score()).isEqualTo(50);
        verifyNoInteractions(ledger, entitlement);
    }

    @Test void AT11_completionAfterDeadlineNeverUpdatesOrPays() {
        var original = row(7, "ASSIGNED", 9L);
        var assigned = new RequestRow(42, 7, "ASSIGNED", original.payload(), null, 9L, NOW.minusSeconds(3600),
                NOW.minusMillis(1), null, null, null, NOW, null, null);
        when(repository.find(42)).thenReturn(Optional.of(assigned));
        failure(() -> service.complete(9, 42, resultWrite()), ErrorCode.GRADING_DEADLINE_MISSED, 422);
        verify(repository, never()).complete(anyLong(), anyLong(), any(), anyInt(), anyString(), any(), anyInt());
        verifyNoInteractions(ledger, entitlement);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "COMPLETED", "FAILED", "EXPIRED"})
    void AT09_completeChecksAssignmentBeforeDeadlineOrContent(String state) {
        var original = row(7, state, 9L);
        when(repository.find(42)).thenReturn(Optional.of(new RequestRow(42, 7, state, original.payload(), null, 9L,
                NOW, null, null, null, null, NOW, null, null)));
        failure(() -> service.complete(9, 42, resultWrite()), ErrorCode.GRADING_NOT_ASSIGNEE, 403);
        verifyNoInteractions(ledger, entitlement);
    }

    @Test void FR015_lostConditionalCompletionDoesNotPay() {
        config.setTeacherPayoutCredits(2);
        when(repository.find(42)).thenReturn(Optional.of(row(7, "ASSIGNED", 9L)));
        failure(() -> service.complete(9, 42, resultWrite()), ErrorCode.GRADING_NOT_ASSIGNEE, 403);
        verify(repository).complete(eq(42L), eq(9L), eq(NOW), eq(100), eq("Nhận xét"), any(), eq(2));
        verifyNoInteractions(ledger, entitlement);
    }

    @Test void FR015_payoutFailureEscapesTheTransactionWithoutReturningASuccess() {
        config.setTeacherPayoutCredits(2);
        when(repository.find(42)).thenReturn(Optional.of(row(7, "ASSIGNED", 9L)));
        when(repository.complete(eq(42L), eq(9L), eq(NOW), anyInt(), anyString(), any(), eq(2))).thenReturn(true);
        doThrow(new IllegalStateException("Fixture payout unavailable")).when(ledger).append(any());
        assertThatThrownBy(() -> service.complete(9, 42, resultWrite())).isInstanceOf(IllegalStateException.class)
                .hasMessage("Fixture payout unavailable");
        verify(repository, times(1)).find(42); verifyNoInteractions(entitlement);
    }

    private GradingResultWrite resultWrite() { return new GradingResultWrite(new Criteria(25, 25, 25, 25), "Nhận xét", List.of()); }

    private RequestRow row(long owner, String status, Long teacher) {
        var payload = mapper.createObjectNode().put("essay", "字".repeat(50)).put("hanziCount", 50).put("hskLevel", 3);
        payload.set("consumption", mapper.valueToTree(new Consumption(Consumption.Source.QUOTA, null, PaidFeature.GRADING, "server-key", NOW)));
        return new RequestRow(42, owner, status, payload, null, teacher, NOW, NOW.plusSeconds(24 * 3600), null, null, null, NOW, null, null);
    }

    private void failure(Runnable action, ErrorCode code, int status) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(code); assertThat(error.getStatus().value()).isEqualTo(status);
        });
    }

    private static com.cnhsk.shared.settings.SettingsService settings(GradingConfig config) {
        var settings = org.mockito.Mockito.mock(com.cnhsk.shared.settings.SettingsService.class);
        org.mockito.Mockito.when(settings.getInt(com.cnhsk.shared.settings.SettingKey.GRADING_MAX_ACTIVE_CLAIMS))
                .thenAnswer(invocation -> config.getMaxActiveClaims());
        org.mockito.Mockito.when(settings.getInt(com.cnhsk.shared.settings.SettingKey.GRADING_GRADING_HOURS))
                .thenAnswer(invocation -> config.getGradingHours());
        org.mockito.Mockito.when(settings.getInt(com.cnhsk.shared.settings.SettingKey.GRADING_TEACHER_PAYOUT_CREDITS))
                .thenAnswer(invocation -> config.getTeacherPayoutCredits());
        return settings;
    }
}
