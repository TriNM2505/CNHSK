package com.cnhsk.learning.grading;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.cnhsk.learning.api.Consumption;
import com.cnhsk.learning.api.EntitlementApi;
import com.cnhsk.learning.api.PaidFeature;
import com.cnhsk.learning.grading.GradingRepository.RequestRow;
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
    private TeacherGradingService service;

    @BeforeEach void setup() {
        repository = mock(GradingRepository.class); entitlement = mock(EntitlementApi.class);
        var config = new GradingConfig(); config.setMaxActiveClaims(3); config.setGradingHours(24); config.setTeacherPayoutCredits(0);
        service = new TeacherGradingService(repository, config, entitlement, mapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test void AT06_queueMapsOnlyMetadataWithTwoRepositoryCalls() {
        when(repository.countQueue(9, NOW)).thenReturn(1L);
        when(repository.findQueue(9, NOW, 0, 20)).thenReturn(List.of(new GradingRepository.QueueRow(42, 50, null, NOW, NOW.plusSeconds(3600))));
        var queue = service.queue(9, 0, 20);
        assertThat(queue.totalElements()).isEqualTo(1); assertThat(queue.items()).hasSize(1);
        var item = queue.items().get(0);
        assertThat(item.reference()).isEqualTo("GR-000042"); assertThat(item.hanziCount()).isEqualTo(50);
        assertThat(item.hskLevel()).isNull(); assertThat(item.payoutCredits()).isZero();
        assertThatThrownBy(() -> queue.items().clear()).isInstanceOf(UnsupportedOperationException.class);
        verify(repository).countQueue(9, NOW); verify(repository).findQueue(9, NOW, 0, 20);
        verifyNoMoreInteractions(repository); verifyNoInteractions(entitlement);
    }

    @Test void FR010_mineMapsTheAlreadyOrderedAssignedProjection() {
        when(repository.findAssigned(9)).thenReturn(List.of(new GradingRepository.AssignedRow(42, 50, NOW.minusSeconds(10), NOW)));
        var items = service.mine(9);
        assertThat(items).hasSize(1); assertThat(items.get(0).gradingDeadline()).isEqualTo(NOW);
        assertThat(items.get(0).assignedAt()).isEqualTo(NOW.minusSeconds(10));
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
}
