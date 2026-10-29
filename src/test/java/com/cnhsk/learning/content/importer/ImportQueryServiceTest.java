package com.cnhsk.learning.content.importer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.learning.common.ServiceRequest;
import com.cnhsk.learning.common.ServiceRequestKind;
import com.cnhsk.learning.common.ServiceRequestRepository;
import com.cnhsk.learning.common.ServiceRequestStatus;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.template.ImportTemplate;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ImportQueryServiceTest {
    private AuthApi auth;
    private ServiceRequestRepository requests;
    private ImportMaintenance maintenance;
    private ImportQueryService query;
    private ServiceRequest run;

    @BeforeEach
    void setup() {
        auth = mock(AuthApi.class);
        requests = mock(ServiceRequestRepository.class);
        maintenance = mock(ImportMaintenance.class);
        query = new ImportQueryService(auth, requests, maintenance, new ObjectMapper());
        when(auth.currentUser()).thenReturn(new CurrentUser(9L, Set.of(RoleCode.CONTENT_EDITOR), true));
        when(auth.displayNames(Set.of(1L))).thenReturn(Map.of(1L, "Người nhập"));
        run = run(42L, 1L);
        when(requests.findByIdAndKind(42L, ServiceRequestKind.IMPORT)).thenReturn(Optional.of(run));
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = "CONTENT_EDITOR", mode = EnumSource.Mode.EXCLUDE)
    void FR001_allQueryMethodsRejectOtherRolesBeforeLookingUpData(RoleCode role) {
        when(auth.currentUser()).thenReturn(new CurrentUser(9L, Set.of(role), true));
        for (Runnable call : List.<Runnable>of(() -> query.detail(42L), () -> query.history(0, 10),
                () -> query.errors(42L, 0, 20), () -> query.errorCsv(42L), query::templates,
                () -> query.templateCsv("LEXEME"))) {
            assertFailure(call, ErrorCode.FORBIDDEN, 403);
        }
        verifyNoInteractions(requests, maintenance);
    }

    @Test
    void FR001_missingAuthenticationStopsBeforeMaintenanceOrQueries() {
        when(auth.currentUser()).thenThrow(new ApiException(ErrorCode.UNAUTHENTICATED,
                org.springframework.http.HttpStatus.UNAUTHORIZED, "Chưa đăng nhập", Map.of()));
        assertFailure(() -> query.detail(42L), ErrorCode.UNAUTHENTICATED, 401);
        verifyNoInteractions(requests, maintenance);
    }

    @ParameterizedTest
    @CsvSource({"-1,10", "0,0", "0,101"})
    void FR027_FR029_invalidPaginationIs422BeforeAnyDatabaseAccess(int page, int size) {
        assertFailure(() -> query.errors(42L, page, size), ErrorCode.VALIDATION_FAILED, 422);
        assertFailure(() -> query.history(page, size), ErrorCode.VALIDATION_FAILED, 422);
        verifyNoInteractions(requests, maintenance);
    }

    @Test
    void FR026_unknownOrNonImportRequestIs404ForDetailErrorsAndExport() {
        when(requests.findByIdAndKind(42L, ServiceRequestKind.IMPORT)).thenReturn(Optional.empty());
        assertFailure(() -> query.detail(42L), ErrorCode.IMPORT_RUN_NOT_FOUND, 404);
        assertFailure(() -> query.errors(42L, 0, 20), ErrorCode.IMPORT_RUN_NOT_FOUND, 404);
        assertFailure(() -> query.errorCsv(42L), ErrorCode.IMPORT_RUN_NOT_FOUND, 404);
    }

    @ParameterizedTest
    @EnumSource(value = ServiceRequestStatus.class, names = "ASSIGNED", mode = EnumSource.Mode.EXCLUDE)
    void FR026_reportReadsTheSavedSnapshotWithoutReclassifyingContent(ServiceRequestStatus status) {
        run.setStatus(status);
        var report = query.detail(42L);
        assertThat(report.runId()).isEqualTo(42L);
        assertThat(report.createdBy()).isEqualTo(new ImportService.CreatedBy(1L, "Người nhập"));
        assertThat(report.totals()).isEqualTo(new ImportPreview.Totals(3, 2, 1, 1, 1, 0));
        assertThat(report.status()).isEqualTo(status.name());
        verify(auth).displayNames(Set.of(1L));
        verify(maintenance).expireOnAccess(42L);
    }

    @Test
    void FR026_legacyRecordsWithoutTotalsAndFileFailuresReturnNullWithoutInventingCounts() {
        run.setPayload(Map.of("dataType", "TOPIC", "fileName", "legacy.csv", "totalRows", 3));
        assertThat(query.detail(42L).totals()).isNull();
        run.setStatus(ServiceRequestStatus.FAILED);
        run.setPayload(new java.util.LinkedHashMap<>());
        run.setErrorMessage("FILE_TOO_LARGE");
        run.setErrorRows(null);
        var failed = query.detail(42L);
        assertThat(failed.totals()).isNull();
        assertThat(failed.result()).isNull();
        assertThat(failed.dataType()).isNull();
        assertThat(failed.fileName()).isNull();
        assertThat(failed.failureReason()).isEqualTo("FILE_TOO_LARGE");
        assertThat(failed.errorCount()).isZero();
        assertThat(query.errors(42L, 0, 20).items()).isEmpty();
    }

    @Test
    void FR027_errorsSortByPhysicalLineAndTemplateColumnAndTruncateByUnicodeCharacters() {
        run.setErrorRows(List.of(error(8, "name", "b"), error(2, "hsk_level", "c"),
                error(2, "code", "😀".repeat(101)), error(2, "name", null), error(2, null, "row")));
        var page = query.errors(42L, 0, 20);
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.items()).extracting(CsvReader.RowError::line).containsExactly(2, 2, 2, 2, 8);
        assertThat(page.items()).extracting(CsvReader.RowError::column).containsExactly("code", "name", "hsk_level", null, "name");
        assertThat(page.items().get(0).value()).isEqualTo("😀".repeat(100));
        assertThat(page.items().get(1).value()).isNull();
        assertThat(run.getErrorRows().get(2).get("value")).isEqualTo("😀".repeat(101));
        assertThat(query.errors(42L, Integer.MAX_VALUE, 100).items()).isEmpty();
    }

    @Test
    void FR027_AT31_pageTwoContainsTheLastFiveOf45Errors() {
        List<Map<String, Object>> errors = new ArrayList<>();
        for (int line = 46; line >= 2; line--) errors.add(error(line, "name", "Dữ liệu"));
        run.setErrorRows(errors);
        var page = query.errors(42L, 2, 20);
        assertThat(page.items()).extracting(CsvReader.RowError::line).containsExactly(42, 43, 44, 45, 46);
        assertThat(page.totalElements()).isEqualTo(45);
    }

    @Test
    void FR028_exportRoundTripsCommasQuotesAndNewlinesAndHandlesAnEmptyErrorList() throws Exception {
        String value = "中文, \"lỗi\"\r\nhàng mới";
        run.setErrorRows(List.of(error(2, null, value)));
        byte[] bytes = query.errorCsv(42L);
        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        try (var parser = CSVFormat.RFC4180.parse(new java.io.StringReader(new String(bytes, StandardCharsets.UTF_8).substring(1)))) {
            var rows = parser.getRecords();
            assertThat(rows).hasSize(2);
            assertThat(rows.get(0).toList()).containsExactly("line", "column", "value", "code", "message");
            assertThat(rows.get(1).get(1)).isEmpty();
            assertThat(rows.get(1).get(2)).isEqualTo(value);
        }
        run.setErrorRows(List.of());
        assertThat(new String(query.errorCsv(42L), StandardCharsets.UTF_8)).isEqualTo("\uFEFFline,column,value,code,message\r\n");
    }

    @Test
    void FR029_historyUsesDatabasePaginationAndBatchesDisplayNamesForTheWholePage() {
        ServiceRequest second = run(43L, 2L);
        when(requests.findByIdAndKind(43L, ServiceRequestKind.IMPORT)).thenReturn(Optional.of(second));
        when(requests.findByKind(eq(ServiceRequestKind.IMPORT), any())).thenReturn(new PageImpl<>(List.of(run, second),
                PageRequest.of(1, 10), 12));
        when(auth.displayNames(Set.of(1L, 2L))).thenReturn(Map.of(1L, "Người nhập", 2L, "Biên tập khác"));
        var page = query.history(1, 10);
        assertThat(page.totalElements()).isEqualTo(12);
        assertThat(page.items()).extracting(item -> item.createdBy().displayName()).containsExactly("Người nhập", "Biên tập khác");
        verify(requests).findByKind(ServiceRequestKind.IMPORT,
                PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        verify(auth).displayNames(Set.of(1L, 2L));
    }

    @Test
    void FR024_FR029_historyReadsAgainAfterLazyExpiryCommits() {
        ServiceRequest expired = run(42L, 1L);
        expired.setStatus(ServiceRequestStatus.EXPIRED);
        when(requests.findByKind(eq(ServiceRequestKind.IMPORT), any())).thenReturn(new PageImpl<>(List.of(run)));
        doAnswer(call -> {
            when(requests.findByIdAndKind(42L, ServiceRequestKind.IMPORT)).thenReturn(Optional.of(expired));
            return null;
        }).when(maintenance).expireOnAccess(42L);
        assertThat(query.history(0, 10).items().get(0).status()).isEqualTo("EXPIRED");
    }

    @Test
    void FR029_emptyHistoryDoesNotPerformAnUnnecessaryIdentityLookup() {
        when(requests.findByKind(eq(ServiceRequestKind.IMPORT), any())).thenReturn(new PageImpl<>(List.of()));
        assertThat(query.history(0, 10).items()).isEmpty();
        verifyNoInteractions(maintenance);
    }

    @ParameterizedTest
    @EnumSource(ImportDataType.class)
    void FR034_FR035_AT41_eachTemplateHasExactlyItsHeaderAndOneExample(ImportDataType type) throws Exception {
        assertThat(query.templates()).isEqualTo(ImportTemplate.all()).hasSize(7);
        String csv = new String(query.templateCsv(type.name()), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF");
        try (var parser = CSVFormat.RFC4180.parse(new java.io.StringReader(csv.substring(1)))) {
            var rows = parser.getRecords();
            ImportTemplate template = ImportTemplate.forType(type);
            assertThat(rows).hasSize(2);
            assertThat(rows.get(0).toList()).isEqualTo(template.columns().stream().map(column -> column.name()).toList());
            assertThat(rows.get(1).toList()).isEqualTo(template.columns().stream().map(column -> column.example()).toList());
        }
        verifyNoInteractions(requests, maintenance);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"EXAM", "lexeme", "LEXEME.csv"})
    void FR035_unknownTemplateIs422(String dataType) {
        assertFailure(() -> query.templateCsv(dataType), ErrorCode.VALIDATION_FAILED, 422);
        verifyNoInteractions(requests, maintenance);
    }

    private static ServiceRequest run(long id, long userId) {
        ServiceRequest run = new ServiceRequest(userId, ServiceRequestKind.IMPORT);
        ReflectionTestUtils.setField(run, "id", id);
        ReflectionTestUtils.setField(run, "createdAt", Instant.parse("2026-10-30T00:00:00Z"));
        run.setPayload(Map.of("dataType", "TOPIC", "fileName", "synthetic.csv", "totalRows", 3,
                "totals", Map.of("total", 3, "valid", 2, "invalid", 1, "create", 1, "update", 1, "unchanged", 0)));
        run.setErrorRows(List.of());
        return run;
    }

    private static Map<String, Object> error(int line, String column, String value) {
        Map<String, Object> error = new java.util.LinkedHashMap<>();
        error.put("line", line); error.put("column", column); error.put("value", value);
        error.put("code", "INVALID_VALUE"); error.put("message", "Giá trị sai");
        return error;
    }

    private static void assertFailure(Runnable call, ErrorCode code, int status) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(code);
            assertThat(error.getStatus().value()).isEqualTo(status);
        });
    }
}
