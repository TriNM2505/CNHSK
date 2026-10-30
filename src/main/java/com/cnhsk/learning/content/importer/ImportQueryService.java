package com.cnhsk.learning.content.importer;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.learning.common.ServiceRequest;
import com.cnhsk.learning.common.ServiceRequestKind;
import com.cnhsk.learning.common.ServiceRequestRepository;
import com.cnhsk.learning.content.importer.CsvReader.RowError;
import com.cnhsk.learning.content.importer.ImportService.CreatedBy;
import com.cnhsk.learning.content.importer.ImportService.ImportResult;
import com.cnhsk.learning.content.importer.ImportService.ImportRun;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.template.ImportTemplate;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ImportQueryService {
    private final AuthApi auth;
    private final ServiceRequestRepository requests;
    private final ImportMaintenance maintenance;
    private final ObjectMapper mapper;

    public ImportQueryService(AuthApi auth, ServiceRequestRepository requests, ImportMaintenance maintenance,
            ObjectMapper mapper) {
        this.auth = auth;
        this.requests = requests;
        this.maintenance = maintenance;
        this.mapper = mapper;
    }

    public ImportRun detail(long id) {
        editor();
        ServiceRequest run = readRun(id);
        String displayName = auth.displayNames(Set.of(run.getUserId())).get(run.getUserId());
        return summarize(run, displayName);
    }

    public ImportRunPage history(int page, int size) {
        editor();
        validatePage(page, size);
        Page<ServiceRequest> selected = requests.findByKind(ServiceRequestKind.IMPORT,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        // Read again after maintenance's independent commit; never return a stale PENDING entity.
        List<ServiceRequest> runs = selected.getContent().stream().map(run -> readRun(run.getId())).toList();
        Set<Long> userIds = runs.stream().map(ServiceRequest::getUserId).collect(Collectors.toSet());
        Map<Long, String> names = userIds.isEmpty() ? Map.of() : auth.displayNames(userIds);
        return new ImportRunPage(runs.stream().map(run -> summarize(run, names.get(run.getUserId()))).toList(),
                page, size, selected.getTotalElements());
    }

    public ImportErrorPage errors(long id, int page, int size) {
        editor();
        validatePage(page, size);
        List<RowError> errors = orderedErrors(readRun(id));
        long offset = (long) page * size;
        List<RowError> items = offset >= errors.size() ? List.of()
                : errors.subList((int) offset, (int) Math.min(offset + size, errors.size()));
        return new ImportErrorPage(items, page, size, errors.size());
    }

    public byte[] errorCsv(long id) {
        editor();
        List<List<?>> rows = new ArrayList<>();
        rows.add(List.of("line", "column", "value", "code", "message"));
        for (RowError error : orderedErrors(readRun(id))) {
            rows.add(java.util.Arrays.asList(error.line(), error.column(), error.value(), error.code(), error.message()));
        }
        return csv(rows);
    }

    public List<ImportTemplate> templates() {
        editor();
        return ImportTemplate.all();
    }

    public byte[] templateCsv(String dataType) {
        editor();
        ImportDataType type;
        try {
            type = ImportDataType.valueOf(dataType == null ? "" : dataType);
        } catch (IllegalArgumentException exception) {
            throw invalid("dataType", "Loại dữ liệu không hợp lệ.");
        }
        ImportTemplate template = ImportTemplate.forType(type);
        return csv(List.of(template.columns().stream().map(column -> column.name()).toList(),
                template.columns().stream().map(column -> column.example()).toList()));
    }

    private ServiceRequest readRun(long id) {
        maintenance.expireOnAccess(id);
        return requests.findByIdAndKind(id, ServiceRequestKind.IMPORT).orElseThrow(() ->
                new ApiException(ErrorCode.IMPORT_RUN_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Không tìm thấy lần nhập dữ liệu.", Map.of()));
    }

    private ImportRun summarize(ServiceRequest run, String name) {
        String dataType = (String) run.getPayload().get("dataType");
        return new ImportRun(run.getId(), dataType == null ? null : ImportDataType.valueOf(dataType),
                (String) run.getPayload().get("fileName"), run.getStatus().name(), run.getCreatedAt(), run.getCompletedAt(),
                new CreatedBy(run.getUserId(), name), ImportService.storedTotals(run),
                mapper.convertValue(run.getResult(), ImportResult.class),
                run.getErrorRows() == null ? 0 : run.getErrorRows().size(), run.getErrorMessage());
    }

    private List<RowError> orderedErrors(ServiceRequest run) {
        if (run.getErrorRows() == null || run.getErrorRows().isEmpty()) return List.of();
        Map<String, Integer> columns = new HashMap<>();
        ImportTemplate template = ImportTemplate.forType(ImportDataType.valueOf((String) run.getPayload().get("dataType")));
        for (int index = 0; index < template.columns().size(); index++) {
            columns.put(template.columns().get(index).name(), index);
        }
        return run.getErrorRows().stream().map(error -> new RowError(((Number) error.get("line")).intValue(),
                        (String) error.get("column"), truncate((String) error.get("value")),
                        (String) error.get("code"), (String) error.get("message")))
                .sorted(Comparator.comparingInt(RowError::line)
                        .thenComparingInt(error -> columns.getOrDefault(error.column(), Integer.MAX_VALUE)))
                .toList();
    }

    private static String truncate(String value) {
        return value == null || value.codePointCount(0, value.length()) <= 100 ? value
                : value.substring(0, value.offsetByCodePoints(0, 100));
    }

    private static byte[] csv(List<? extends List<?>> rows) {
        StringWriter text = new StringWriter();
        text.write('\uFEFF');
        try (CSVPrinter printer = new CSVPrinter(text, CSVFormat.RFC4180)) {
            for (List<?> row : rows) printer.printRecord(row.stream().map(CsvCellSanitizer::sanitize).toList());
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not create import CSV", exception);
        }
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void editor() {
        if (!auth.currentUser().roles().contains(RoleCode.CONTENT_EDITOR)) {
            throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Bạn không có quyền nhập dữ liệu.", Map.of());
        }
    }

    private static void validatePage(int page, int size) {
        if (page < 0) throw invalid("page", "Số trang phải từ 0 trở lên.");
        if (size < 1 || size > 100) throw invalid("size", "Kích thước trang phải từ 1 đến 100.");
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, message,
                Map.of("details", List.of(Map.of("field", field, "message", message))));
    }

    public record ImportRunPage(List<ImportRun> items, int page, int size, long totalElements) { }

    public record ImportErrorPage(List<RowError> items, int page, int size, long totalElements) { }
}
