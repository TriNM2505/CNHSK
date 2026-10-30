package com.cnhsk.learning.content.importer;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.learning.common.ServiceRequest;
import com.cnhsk.learning.common.ServiceRequestKind;
import com.cnhsk.learning.common.ServiceRequestRepository;
import com.cnhsk.learning.common.ServiceRequestStatus;
import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.content.importer.web.ImportController.ConfirmRequest;
import com.cnhsk.learning.content.importer.write.RowWriter;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ImportConfirmTest {
    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");
    private AuthApi auth;
    private EntityManager entityManager;
    private AuditService audit;
    private ServiceRequestRepository requests;
    private RowWriter writer;
    private PlatformTransactionManager manager;
    private ImportService service;
    private ServiceRequest run;

    @BeforeEach
    void setup() {
        auth = mock(AuthApi.class);
        entityManager = mock(EntityManager.class);
        audit = mock(AuditService.class);
        requests = mock(ServiceRequestRepository.class);
        writer = mock(RowWriter.class);
        manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(invocation -> {
            TransactionDefinition definition = invocation.getArgument(0);
            assertThat(definition.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            return new SimpleTransactionStatus();
        });
        when(auth.currentUser()).thenReturn(new CurrentUser(9L, Set.of(RoleCode.CONTENT_EDITOR), true));
        when(auth.displayNames(Set.of(1L))).thenReturn(Map.of(1L, "Người tạo lần nhập"));
        when(writer.dataType()).thenReturn(ImportDataType.TOPIC);
        when(writer.write(any())).thenReturn(RowAction.CREATE);
        when(requests.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = service(List.of(writer));
        run = pending(ImportDataType.TOPIC, List.of(storedRow(2)), 1);
        when(entityManager.find(ServiceRequest.class, 42L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(run);
    }

    @Test
    void FR018_FR019_rowsRunInPhysicalOrderAndCountOnlyCommittedActions() {
        run.setPayload(payload(ImportDataType.TOPIC,
                List.of(storedRow(7), storedRow(3), storedRow(2), storedRow(4)), 6, NOW.plusSeconds(1800)));
        run.setErrorRows(List.of(rowError(8, "hsk_level"), rowError(6, "name"), rowError(6, "code")));
        List<Integer> order = new ArrayList<>();
        when(writer.write(any())).thenAnswer(invocation -> {
            ValidatedRow row = invocation.getArgument(0);
            assertThat(run.getStatus()).isEqualTo(ServiceRequestStatus.RUNNING);
            order.add(row.line());
            return switch (row.line()) {
                case 2 -> RowAction.CREATE;
                case 3 -> RowAction.UPDATE;
                case 4 -> RowAction.UNCHANGED;
                default -> throw new DataIntegrityViolationException("raw-cell-marker must never escape");
            };
        });
        ImportService.ImportRun response = service.confirm(42L, false);
        assertThat(order).containsExactly(2, 3, 4, 7);
        assertThat(response.result()).isEqualTo(new ImportService.ImportResult(6, 4, 2, 1, 1, 1, 1));
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.createdBy()).isEqualTo(new ImportService.CreatedBy(1L, "Người tạo lần nhập"));
        assertThat(response.errorCount()).isEqualTo(4);
        assertThat(run.getErrorRows()).extracting(e -> e.get("line")).containsExactly(6, 6, 7, 8);
        assertThat(run.getErrorRows().get(0)).containsEntry("column", "code");
        assertThat(run.getErrorRows().get(2)).containsEntry("column", null).containsEntry("value", null)
                .containsEntry("code", "ROW_WRITE_FAILED");
        assertThat(run.getErrorRows().toString()).doesNotContain("raw-cell-marker");
        assertThat(run.getPayload()).containsOnlyKeys("dataType", "fileName", "totalRows");
        verify(manager, times(5)).commit(any());
        verify(manager).rollback(any());
        verify(audit).record(9L, "IMPORT_CONFIRM", "import:42", null, response.result().asMap());
    }

    @Test
    void FR019_exceptionWhileCommittingARowIsAlsoReportedAsRowWriteFailed() {
        AtomicInteger commits = new AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> {
            if (commits.incrementAndGet() == 2) throw new DataIntegrityViolationException("commit failure");
            return null;
        }).when(manager).commit(any());
        ImportService.ImportRun response = service.confirm(42L, false);
        assertThat(response.result().created()).isZero();
        assertThat(response.result().failed()).isEqualTo(1);
        assertThat(response.status()).isEqualTo("COMPLETED");
    }

    @ParameterizedTest
    @EnumSource(value = ServiceRequestStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    void FR015_nonPendingStatusWinsBeforeExpiryAndEmptyRowChecks(ServiceRequestStatus status) {
        run.setStatus(status);
        run.setPayload(Map.of());
        assertFailure(() -> service.confirm(42L, false), ErrorCode.IMPORT_NOT_PENDING, 409);
        verify(manager).rollback(any());
        verify(writer, never()).write(any());
        verifyNoInteractions(audit);
    }

    @Test
    void FR015_unknownIdAndOtherServiceKindsAreNotImportRuns() {
        assertFailure(() -> service.confirm(43L, false), ErrorCode.IMPORT_RUN_NOT_FOUND, 404);
        run.setKind(ServiceRequestKind.TRANSLATE);
        assertFailure(() -> service.confirm(42L, false), ErrorCode.IMPORT_RUN_NOT_FOUND, 404);
        verify(writer, never()).write(any());
    }

    @Test
    void FR015_AT19_expiredPreviewCommitsExpiredAndRemovesRowsBeforeReturningGone() {
        run.setPayload(payload(ImportDataType.TOPIC, List.of(), 0, NOW.minusNanos(1)));
        assertFailure(() -> service.confirm(42L, false), ErrorCode.IMPORT_PREVIEW_EXPIRED, 410);
        assertThat(run.getStatus()).isEqualTo(ServiceRequestStatus.EXPIRED);
        assertThat(run.getPayload()).doesNotContainKey("validRows");
        verify(manager).commit(any());
        verify(manager, never()).rollback(any());
        verify(writer, never()).write(any());
    }

    @Test
    void FR015_expiryComparisonUsesTheStrictLessThanRule() {
        run.setPayload(payload(ImportDataType.TOPIC, List.of(storedRow(2)), 1, NOW));
        assertThat(service.confirm(42L, false).status()).isEqualTo("COMPLETED");
    }

    @Test
    void FR015_zeroValidRowsLeavePendingAndDoNotClaimTheGlobalWriterSlot() {
        run.setPayload(payload(ImportDataType.TOPIC, List.of(), 2, NOW.plusSeconds(1)));
        assertFailure(() -> service.confirm(42L, false), ErrorCode.NO_VALID_ROWS, 422);
        assertThat(run.getStatus()).isEqualTo(ServiceRequestStatus.PENDING);
        verify(requests, never()).saveAndFlush(any());
        verify(writer, never()).write(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void FR016_AT21_completedHashIsRecheckedAndRequiresExplicitAcknowledgement(boolean acknowledgement) {
        when(requests.existsByKindAndStatusAndFileHash(ServiceRequestKind.IMPORT, ServiceRequestStatus.COMPLETED, "a".repeat(64)))
                .thenReturn(true);
        if (acknowledgement) {
            assertThat(service.confirm(42L, true).status()).isEqualTo("COMPLETED");
        } else {
            assertFailure(() -> service.confirm(42L, false), ErrorCode.RERUN_CONFIRMATION_REQUIRED, 409);
            assertThat(run.getStatus()).isEqualTo(ServiceRequestStatus.PENDING);
            verify(writer, never()).write(any());
        }
    }

    @Test
    void FR017_onlyTheRunningImportUniqueConstraintBecomesConcurrentImport() {
        ConstraintViolationException constraint = new ConstraintViolationException("constraint", new SQLException("failure"), "uq_import_running");
        when(requests.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("db failure", constraint));
        assertFailure(() -> service.confirm(42L, false), ErrorCode.CONCURRENT_IMPORT, 409);
        verify(manager).rollback(any());
        verify(writer, never()).write(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"other_constraint", "no_hibernate_constraint"})
    void FR017_unrelatedStorageErrorsAreNotMislabeledAsConcurrentImports(String name) {
        Throwable cause = name.equals("no_hibernate_constraint") ? new SQLException("storage failure")
                : new ConstraintViolationException("constraint", new SQLException("failure"), name);
        DataIntegrityViolationException failure = new DataIntegrityViolationException("unrelated failure", cause);
        when(requests.saveAndFlush(any())).thenThrow(failure);
        assertThatThrownBy(() -> service.confirm(42L, false)).isSameAs(failure);
        verify(writer, never()).write(any());
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = "CONTENT_EDITOR", mode = EnumSource.Mode.EXCLUDE)
    void FR001_otherRolesCannotConfirmEvenWithAnUnknownRunId(RoleCode role) {
        when(auth.currentUser()).thenReturn(new CurrentUser(9L, Set.of(role), true));
        assertFailure(() -> service.confirm(43L, false), ErrorCode.FORBIDDEN, 403);
        verifyNoInteractions(entityManager);
    }

    @Test
    void FR001_unauthenticatedIdentityFailureStopsBeforeDatabaseAccess() {
        when(auth.currentUser()).thenThrow(new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Đăng nhập", Map.of()));
        assertFailure(() -> service.confirm(42L, false), ErrorCode.UNAUTHENTICATED, 401);
        verifyNoInteractions(entityManager);
    }

    @ParameterizedTest
    @EnumSource(ImportDataType.class)
    void FR018_everyDataTypeDispatchesToItsRegisteredWriter(ImportDataType type) {
        RowWriter typedWriter = mock(RowWriter.class);
        when(typedWriter.dataType()).thenReturn(type);
        when(typedWriter.write(any())).thenReturn(RowAction.CREATE);
        run.setPayload(payload(type, List.of(storedRow(2)), 1, NOW.plusSeconds(1800)));
        ImportService typedService = service(List.of(typedWriter));
        assertThat(typedService.confirm(42L, false).dataType()).isEqualTo(type);
        verify(typedWriter).write(any());
    }

    @Test
    void FR021_finalizationAndAuditUseOneTransactionAndAuditFailurePropagates() {
        doThrow(new IllegalStateException("audit unavailable")).when(audit).record(anyLong(), any(), any(), any(), any());
        assertThatThrownBy(() -> service.confirm(42L, false)).isInstanceOf(IllegalStateException.class).hasMessage("audit unavailable");
        verify(manager, times(2)).commit(any());
        verify(manager).rollback(any());
        verify(auth, never()).displayNames(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "true", "\"text\"", "{\"acknowledgeRerun\":null}", "{\"acknowledgeRerun\":\"true\"}", "{\"acknowledgeRerun\":1}"})
    void FR015_requestAcknowledgementAcceptsOnlyAJsonObjectWithABoolean(String json) throws Exception {
        var body = new ObjectMapper().readTree(json);
        assertFailure(() -> ConfirmRequest.fromJson(body), ErrorCode.VALIDATION_FAILED, 422);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"acknowledgeRerun\":false}", "{\"acknowledgeRerun\":true}"})
    void FR015_requestAcknowledgementDefaultsToFalseWhenOmitted(String json) throws Exception {
        assertThat(ConfirmRequest.fromJson(new ObjectMapper().readTree(json)).acknowledgeRerun()).isEqualTo(json.contains("true"));
    }

    private ImportService service(List<RowWriter> writers) {
        return new ImportService(auth, mock(CsvReader.class), List.of(), mock(ImportChangePlanner.class), requests,
                new ImportProperties(), Clock.fixed(NOW, ZoneOffset.UTC), manager, entityManager, audit, writers);
    }

    private static ServiceRequest pending(ImportDataType type, List<Map<String, Object>> rows, int total) {
        ServiceRequest run = new ServiceRequest(1L, ServiceRequestKind.IMPORT);
        ReflectionTestUtils.setField(run, "id", 42L);
        ReflectionTestUtils.setField(run, "createdAt", NOW.minusSeconds(10));
        run.setPayload(payload(type, rows, total, NOW.plusSeconds(1800)));
        run.setErrorRows(List.of());
        run.setFileHash("a".repeat(64));
        return run;
    }

    private static Map<String, Object> payload(ImportDataType type, List<Map<String, Object>> rows, int total, Instant expiry) {
        return Map.of("dataType", type.name(), "fileName", "synthetic.csv", "totalRows", total,
                "validRows", rows, "expiresAt", expiry.toString());
    }

    private static Map<String, Object> storedRow(int line) {
        return Map.of("lineNo", line, "values", Map.of("code", "ROW_" + line, "name", "Chủ đề", "hsk_level", "1"));
    }

    private static Map<String, Object> rowError(int line, String column) {
        return Map.of("line", line, "column", column, "value", "bad", "code", "INVALID_VALUE", "message", "Giá trị sai");
    }

    private static void assertFailure(Runnable action, ErrorCode code, int status) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, failure -> {
            assertThat(failure.getCode()).isEqualTo(code);
            assertThat(failure.getStatus().value()).isEqualTo(status);
        });
    }
}
