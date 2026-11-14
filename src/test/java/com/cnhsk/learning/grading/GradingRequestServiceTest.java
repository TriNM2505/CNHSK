package com.cnhsk.learning.grading;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.cnhsk.learning.api.Consumption;
import com.cnhsk.learning.api.EntitlementApi;
import com.cnhsk.learning.api.PaidFeature;
import com.cnhsk.learning.grading.GradingRepository.RequestRow;
import com.cnhsk.learning.grading.web.GradingRequestWrite;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class GradingRequestServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private GradingRepository repository;
    private EntitlementApi entitlement;
    private GradingRequestService service;

    @BeforeEach void setup() {
        repository = mock(GradingRepository.class); entitlement = mock(EntitlementApi.class);
        GradingConfig config = new GradingConfig(); config.setClaimHours(48); config.setGradingHours(24);
        config.setMinHanzi(50); config.setMaxHanzi(500); config.setMaxChars(3000);
        service = new GradingRequestService(repository, entitlement, config, mapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test void FR001_policyUsesTheConfigurationAndExactRefundTerms() {
        var policy = service.policy();
        assertThat(policy.claimHours()).isEqualTo(48); assertThat(policy.gradingHours()).isEqualTo(24);
        assertThat(policy.minHanzi()).isEqualTo(50); assertThat(policy.maxHanzi()).isEqualTo(500);
        assertThat(policy.maxChars()).isEqualTo(3000); assertThat(policy.cost()).isEqualTo(1);
        assertThat(policy.refundTerms()).isEqualTo("Được hoàn lượt nếu bạn huỷ trước khi có giáo viên nhận, hoặc nếu bài không được nhận/chấm đúng hạn");
    }

    @Test void AT01_invalidEssayDoesNotLockOrConsume() {
        failure(() -> service.submit(7, new GradingRequestWrite("字".repeat(49), null, null)), ErrorCode.GRADING_INVALID_ESSAY);
        verifyNoInteractions(repository, entitlement);
    }

    @Test void AT02_duplicateIsCheckedUnderTheSubmitLockBeforeBilling() {
        when(repository.hasActiveEssay(eq(7L), anyString())).thenReturn(true);
        failure(() -> service.submit(7, new GradingRequestWrite("字".repeat(50), null, null)), ErrorCode.GRADING_DUPLICATE_ACTIVE);
        var order = inOrder(repository);
        order.verify(repository).lockSubmit(7); order.verify(repository).hasActiveEssay(eq(7L), anyString());
        verifyNoInteractions(entitlement); verify(repository, never()).createPending(anyLong(), any(), any(), any());
    }

    @Test void FR003_submitPersistsOnlyTheServerReceiptAndClockDeadlines() {
        Consumption receipt = new Consumption(Consumption.Source.QUOTA, null, PaidFeature.GRADING, "server-key", NOW);
        when(entitlement.consume(eq(7L), eq(PaidFeature.GRADING), startsWith("grading:"))).thenReturn(receipt);
        when(repository.createPending(eq(7L), any(), eq(NOW), eq(NOW.plusSeconds(48 * 3600)))).thenReturn(42L);
        when(repository.find(42)).thenReturn(Optional.of(row(7, "PENDING", null)));
        var response = service.submit(7, new GradingRequestWrite(" \n" + "字".repeat(50) + "\n字字 \t", "Đề tự soạn", 3));
        var payload = ArgumentCaptor.forClass(com.fasterxml.jackson.databind.JsonNode.class);
        verify(repository).createPending(eq(7L), payload.capture(), eq(NOW), eq(NOW.plusSeconds(48 * 3600)));
        assertThat(payload.getValue().path("hanziCount").intValue()).isEqualTo(52);
        assertThat(payload.getValue().path("essay").textValue()).endsWith("\n字字");
        assertThat(payload.getValue().path("essayHash").textValue()).matches("[0-9a-f]{64}");
        assertThat(payload.getValue().get("consumption")).isEqualTo(mapper.valueToTree(receipt));
        assertThat(response.reference()).isEqualTo("GR-000042");
        var order = inOrder(repository, entitlement);
        order.verify(repository).lockSubmit(7); order.verify(repository).hasActiveEssay(eq(7L), anyString());
        order.verify(entitlement).consume(eq(7L), eq(PaidFeature.GRADING), startsWith("grading:"));
        order.verify(repository).createPending(eq(7L), any(), any(), any());
    }

    @Test void AT04_ownershipIsCheckedBeforeResultOrReceiptCanBeUsed() {
        when(repository.find(42)).thenReturn(Optional.empty());
        failure(() -> service.get(7, 42), ErrorCode.GRADING_REQUEST_NOT_OWNED);
        when(repository.find(42)).thenReturn(Optional.of(row(8, "PENDING", null)));
        failure(() -> service.cancel(7, 42), ErrorCode.GRADING_REQUEST_NOT_OWNED);
        verify(repository, never()).cancel(anyLong(), anyLong(), any()); verifyNoInteractions(entitlement);
    }

    @Test void AT05_lostCancellationNeverRefunds() {
        when(repository.find(42)).thenReturn(Optional.of(row(7, "ASSIGNED", null)));
        failure(() -> service.cancel(7, 42), ErrorCode.GRADING_NOT_CANCELLABLE);
        verifyNoInteractions(entitlement);
    }

    @Test void AT05_cancelUsesTheStoredServerReceipt() {
        var before = row(7, "PENDING", null);
        when(repository.find(42)).thenReturn(Optional.of(before), Optional.of(row(7, "FAILED", "CANCELLED_BY_LEARNER")));
        when(repository.cancel(42, 7, NOW)).thenReturn(true);
        assertThat(service.cancel(7, 42).refunded()).isTrue();
        verify(entitlement).refund(7, mapper.convertValue(before.payload().get("consumption"), Consumption.class));
    }

    @ParameterizedTest
    @CsvSource({"PENDING,,false", "ASSIGNED,,false", "EXPIRED,,true", "FAILED,CANCELLED_BY_LEARNER,true", "FAILED,OTHER,false"})
    void Q_T02_03_refundedIsDerivedOnlyFromTheApprovedStates(String status, String error, boolean refunded) {
        when(repository.find(42)).thenReturn(Optional.of(row(7, status, error)));
        var result = service.get(7, 42);
        assertThat(result.refunded()).isEqualTo(refunded); assertThat(result.result()).isNull();
    }

    @Test void FR004_summaryUsesCodePointsAndHidesScoreUntilCompleted() {
        RequestRow row = row(7, "ASSIGNED", null);
        ((ObjectNode) row.payload()).put("essay", "😀".repeat(101));
        when(repository.countMine(7, null)).thenReturn(1L);
        when(repository.findMine(7, null, 0, 20)).thenReturn(List.of(row));
        var page = service.list(7, null, 0, 20);
        assertThat(page.items().get(0).essayPreview()).isEqualTo("😀".repeat(100));
        assertThat(page.items().get(0).score()).isNull();
        failure(() -> service.list(7, "UNKNOWN", 0, 20), ErrorCode.VALIDATION_FAILED);
        failure(() -> service.list(7, null, -1, 20), ErrorCode.VALIDATION_FAILED);
        failure(() -> service.list(7, null, 0, 51), ErrorCode.VALIDATION_FAILED);
    }

    private RequestRow row(long user, String status, String error) {
        var payload = mapper.createObjectNode().put("essay", "字".repeat(50));
        payload.set("consumption", mapper.valueToTree(new Consumption(Consumption.Source.QUOTA, null, PaidFeature.GRADING, "server-key", NOW)));
        return new RequestRow(42, user, status, payload, null, 9L, NOW, NOW.plusSeconds(3600), 80, null, 2, NOW, null, error);
    }

    private void failure(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode()).isEqualTo(code));
    }
}
