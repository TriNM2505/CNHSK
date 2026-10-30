package com.cnhsk.learning.content.importer;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.learning.common.ServiceRequest;
import com.cnhsk.learning.common.ServiceRequestKind;
import com.cnhsk.learning.common.ServiceRequestRepository;
import com.cnhsk.learning.common.ServiceRequestStatus;
import com.cnhsk.learning.content.importer.ImportPreview.PreviewRow;
import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.template.ImportTemplate;
import com.cnhsk.learning.content.importer.validate.RowValidationResult;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.content.importer.validate.RowValidator;
import com.cnhsk.learning.content.importer.write.InvalidAnswerException;
import com.cnhsk.learning.content.importer.write.RowWriter;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImportService {
    private final AuthApi auth;
    private final CsvReader reader;
    private final Map<ImportDataType, RowValidator> validators;
    private final ImportChangePlanner planner;
    private final ServiceRequestRepository requests;
    private final ImportProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final EntityManager entityManager;
    private final AuditService audit;
    private final Map<ImportDataType, RowWriter> writers;

    public ImportService(AuthApi auth, CsvReader reader, List<RowValidator> validators, ImportChangePlanner planner,
            ServiceRequestRepository requests, ImportProperties properties, Clock clock,
            PlatformTransactionManager transactionManager, EntityManager entityManager,
            AuditService audit, List<RowWriter> writers) {
        this.auth = auth;
        this.reader = reader;
        this.validators = validators.stream().collect(Collectors.toUnmodifiableMap(RowValidator::dataType, Function.identity()));
        this.planner = planner;
        this.requests = requests;
        this.properties = properties;
        this.clock = clock;
        this.entityManager = entityManager;
        this.audit = audit;
        this.writers = writers.stream().collect(Collectors.toUnmodifiableMap(RowWriter::dataType, Function.identity()));
        transactions = new TransactionTemplate(transactionManager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public ImportPreview upload(String dataType, MultipartFile file) {
        CurrentUser user = editor();
        ImportDataType type;
        try {
            type = ImportDataType.valueOf(dataType == null ? "" : dataType);
        } catch (IllegalArgumentException exception) {
            throw invalid("dataType", "Loại dữ liệu không hợp lệ.");
        }
        if (file == null || file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw invalid("file", "Cần chọn một tệp có tên.");
        }
        String fileName = file.getOriginalFilename();
        String hash = null;
        try {
            // Keep the declared extension gate before the size gate for parsed requests (FR-002).
            if (!CsvReader.hasCsvExtension(fileName)) throw new ImportFileException(ErrorCode.UNSUPPORTED_FILE_FORMAT);
            if (file.getSize() > properties.getMaxFileSize().toBytes()) throw new ImportFileException(ErrorCode.FILE_TOO_LARGE);
            byte[] bytes;
            try {
                bytes = file.getBytes();
            } catch (IOException exception) {
                throw new ImportFileException(ErrorCode.UNSUPPORTED_FILE_FORMAT);
            }
            hash = sha256(bytes);
            RowValidationResult validated = validators.get(type).validate(reader.read(type, fileName, bytes));
            List<PreviewRow> rows = planner.plan(type, validated.validRows());
            int valid = rows.size();
            ImportPreview.Totals totals = new ImportPreview.Totals(validated.totalRows(), valid,
                    validated.totalRows() - valid, count(rows, RowAction.CREATE), count(rows, RowAction.UPDATE),
                    count(rows, RowAction.UNCHANGED));
            Instant expiresAt = clock.instant().plus(properties.getPreviewTtl());
            ServiceRequest run = new ServiceRequest(user.userId(), ServiceRequestKind.IMPORT);
            Map<String, Object> payload = metadata(type, fileName, validated.totalRows());
            payload.put("expiresAt", expiresAt.toString());
            payload.put("totals", Map.of("total", totals.total(), "valid", totals.valid(), "invalid", totals.invalid(),
                    "create", totals.create(), "update", totals.update(), "unchanged", totals.unchanged()));
            payload.put("validRows", validated.validRows().stream().map(row -> {
                Map<String, Object> stored = new LinkedHashMap<>();
                stored.put("lineNo", row.line());
                stored.put("values", row.values());
                if (row.pinyinNorm() != null) stored.put("pinyinNorm", row.pinyinNorm());
                if (row.meaningNorm() != null) stored.put("meaningNorm", row.meaningNorm());
                return stored;
            }).toList());
            run.setPayload(payload);
            run.setFileHash(hash);
            run.setErrorRows(validated.errors().stream().map(ImportService::errorMap).toList());
            save(run);
            boolean alreadyImported = requests.existsByKindAndStatusAndFileHash(
                    ServiceRequestKind.IMPORT, ServiceRequestStatus.COMPLETED, hash);
            return new ImportPreview(run.getId(), type, fileName, "PENDING", totals, alreadyImported, expiresAt,
                    rows.stream().limit(20).toList(), validated.errors().stream().limit(50).toList(), validated.errors().size());
        } catch (ImportFileException exception) {
            throw failed(user.userId(), type, fileName, hash, exception);
        }
    }

    public ApiException multipartTooLarge() {
        return failed(editor().userId(), null, null, null, new ImportFileException(ErrorCode.FILE_TOO_LARGE));
    }

    /** Deliberately has no outer transaction: each callback must commit or roll back independently. */
    public ImportRun confirm(long id, boolean acknowledgeRerun) {
        CurrentUser actor = editor();
        ConfirmClaim claim;
        try {
            claim = transactions.execute(status -> claim(id, acknowledgeRerun));
        } catch (DataIntegrityViolationException exception) {
            if (runningConflict(exception)) {
                throw error(ErrorCode.CONCURRENT_IMPORT, HttpStatus.CONFLICT,
                        "Đang có lần nhập khác ghi dữ liệu. Hãy thử lại sau.");
            }
            throw exception;
        }
        // Expiration must commit before the HTTP error is raised.
        if (claim.failure() != null) {
            throw claim.failure();
        }
        List<Map<String, Object>> errors = new ArrayList<>(claim.run().getErrorRows() == null
                ? List.of() : claim.run().getErrorRows());
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int failed = 0;
        for (ValidatedRow row : claim.rows()) {
            try {
                RowAction action = transactions.execute(status -> writers.get(claim.dataType()).write(row));
                switch (action) {
                    case CREATE -> created++;
                    case UPDATE -> updated++;
                    case UNCHANGED -> unchanged++;
                }
            } catch (InvalidAnswerException exception) {
                failed++;
                errors.add(errorMap(new CsvReader.RowError(row.line(), "answer", row.values().get("answer"),
                        "INVALID_ANSWER", exception.getMessage())));
            } catch (RuntimeException exception) {
                // Never copy exception messages, SQL or cell values to the error report.
                failed++;
                errors.add(errorMap(new CsvReader.RowError(row.line(), null, null, "ROW_WRITE_FAILED",
                        "Không ghi được dòng này do lỗi hệ thống — thử nhập lại")));
            }
        }
        int total = ((Number) claim.run().getPayload().get("totalRows")).intValue();
        ImportResult result = new ImportResult(total, claim.rows().size(), total - claim.rows().size(),
                created, updated, unchanged, failed);
        sortErrors(errors, claim.dataType());
        ServiceRequest completed = transactions.execute(status -> {
            ServiceRequest run = lockedImport(id);
            if (run.getStatus() != ServiceRequestStatus.RUNNING) {
                throw new IllegalStateException("Import is no longer running");
            }
            run.setStatus(ServiceRequestStatus.COMPLETED);
            run.setCompletedAt(clock.instant());
            run.setResult(result.asMap());
            run.setErrorRows(errors);
            Map<String, Object> payload = metadata(claim.dataType(), (String) run.getPayload().get("fileName"), total);
            if (run.getPayload().containsKey("totals")) payload.put("totals", run.getPayload().get("totals"));
            run.setPayload(payload);
            audit.record(actor.userId(), "IMPORT_CONFIRM", "import:" + id, null, result.asMap());
            return requests.saveAndFlush(run);
        });
        String displayName = auth.displayNames(Set.of(completed.getUserId())).get(completed.getUserId());
        return new ImportRun(completed.getId(), claim.dataType(), (String) completed.getPayload().get("fileName"),
                completed.getStatus().name(), completed.getCreatedAt(), completed.getCompletedAt(),
                new CreatedBy(completed.getUserId(), displayName), storedTotals(completed), result, errors.size(), null);
    }

    private ConfirmClaim claim(long id, boolean acknowledgeRerun) {
        ServiceRequest run = lockedImport(id);
        if (run.getStatus() != ServiceRequestStatus.PENDING) {
            throw error(ErrorCode.IMPORT_NOT_PENDING, HttpStatus.CONFLICT, "Lần nhập không còn ở trạng thái chờ xác nhận.");
        }
        if (Instant.parse((String) run.getPayload().get("expiresAt")).isBefore(clock.instant())) {
            Map<String, Object> payload = new LinkedHashMap<>(run.getPayload());
            payload.remove("validRows");
            run.setPayload(payload);
            run.setStatus(ServiceRequestStatus.EXPIRED);
            requests.saveAndFlush(run);
            return new ConfirmClaim(run, null, List.of(), error(ErrorCode.IMPORT_PREVIEW_EXPIRED, HttpStatus.GONE,
                    "Bản xem trước đã hết hạn. Hãy tải lại tệp."));
        }
        List<ValidatedRow> rows = storedRows(run);
        if (rows.isEmpty()) {
            throw error(ErrorCode.NO_VALID_ROWS, HttpStatus.UNPROCESSABLE_ENTITY, "Không có dòng hợp lệ để nhập.");
        }
        if (!acknowledgeRerun && requests.existsByKindAndStatusAndFileHash(
                ServiceRequestKind.IMPORT, ServiceRequestStatus.COMPLETED, run.getFileHash())) {
            throw error(ErrorCode.RERUN_CONFIRMATION_REQUIRED, HttpStatus.CONFLICT,
                    "Tệp này đã được nhập. Hãy xác nhận nhập lại.");
        }
        ImportDataType type = ImportDataType.valueOf((String) run.getPayload().get("dataType"));
        Map<String, Object> payload = new LinkedHashMap<>(run.getPayload());
        payload.put("runningSince", clock.instant().toString());
        run.setPayload(payload);
        run.setStatus(ServiceRequestStatus.RUNNING);
        requests.saveAndFlush(run);
        return new ConfirmClaim(run, type, rows, null);
    }

    private ServiceRequest lockedImport(long id) {
        ServiceRequest run = entityManager.find(ServiceRequest.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (run == null || run.getKind() != ServiceRequestKind.IMPORT) {
            throw error(ErrorCode.IMPORT_RUN_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy lần nhập dữ liệu.");
        }
        return run;
    }

    private static List<ValidatedRow> storedRows(ServiceRequest run) {
        List<?> stored = (List<?>) run.getPayload().getOrDefault("validRows", List.of());
        return stored.stream().map(item -> {
            Map<?, ?> row = (Map<?, ?>) item;
            Map<String, String> values = new LinkedHashMap<>();
            ((Map<?, ?>) row.get("values")).forEach((column, value) -> values.put((String) column, (String) value));
            return new ValidatedRow(((Number) row.get("lineNo")).intValue(), values,
                    (String) row.get("pinyinNorm"), (String) row.get("meaningNorm"));
        }).sorted(Comparator.comparingInt(ValidatedRow::line)).toList();
    }

    private static boolean runningConflict(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraint
                    && "uq_import_running".equals(constraint.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    private static void sortErrors(List<Map<String, Object>> errors, ImportDataType type) {
        Map<String, Integer> columns = new HashMap<>();
        ImportTemplate template = ImportTemplate.forType(type);
        for (int index = 0; index < template.columns().size(); index++) {
            columns.put(template.columns().get(index).name(), index);
        }
        errors.sort(Comparator.<Map<String, Object>>comparingInt(e -> ((Number) e.get("line")).intValue())
                .thenComparingInt(e -> columns.getOrDefault(e.get("column"), Integer.MAX_VALUE)));
    }

    private static ApiException error(ErrorCode code, HttpStatus status, String message) {
        return new ApiException(code, status, message, Map.of());
    }

    private record ConfirmClaim(ServiceRequest run, ImportDataType dataType, List<ValidatedRow> rows, ApiException failure) { }

    public record CreatedBy(long id, String displayName) { }

    public record ImportResult(int total, int valid, int invalid, int created, int updated, int unchanged, int failed) {
        Map<String, Object> asMap() {
            return Map.of("total", total, "valid", valid, "invalid", invalid, "created", created,
                    "updated", updated, "unchanged", unchanged, "failed", failed);
        }
    }

    public record ImportRun(long runId, ImportDataType dataType, String fileName, String status,
            Instant createdAt, Instant completedAt, CreatedBy createdBy, ImportPreview.Totals totals,
            ImportResult result, int errorCount, String failureReason) { }

    static ImportPreview.Totals storedTotals(ServiceRequest run) {
        Map<?, ?> values = (Map<?, ?>) run.getPayload().get("totals");
        if (values == null) return null;
        return new ImportPreview.Totals(((Number) values.get("total")).intValue(), ((Number) values.get("valid")).intValue(),
                ((Number) values.get("invalid")).intValue(), ((Number) values.get("create")).intValue(),
                ((Number) values.get("update")).intValue(), ((Number) values.get("unchanged")).intValue());
    }

    private ApiException failed(long userId, ImportDataType type, String fileName, String hash, ImportFileException failure) {
        ServiceRequest run = new ServiceRequest(userId, ServiceRequestKind.IMPORT);
        run.setStatus(ServiceRequestStatus.FAILED);
        run.setPayload(metadata(type, fileName, 0));
        run.setFileHash(hash);
        run.setErrorMessage(failure.getCode().name());
        run.setErrorRows(List.of());
        run.setCompletedAt(clock.instant());
        save(run);
        Map<String, Object> extra = new LinkedHashMap<>(failure.getExtra());
        extra.put("runId", run.getId());
        return new ApiException(failure.getCode(), failure.getStatus(), failure.getMessage(), extra);
    }

    private void save(ServiceRequest run) {
        transactions.executeWithoutResult(status -> requests.saveAndFlush(run));
    }

    private CurrentUser editor() {
        CurrentUser current = auth.currentUser();
        if (!current.roles().contains(RoleCode.CONTENT_EDITOR)) {
            throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Bạn không có quyền nhập dữ liệu.", Map.of());
        }
        return current;
    }

    private static Map<String, Object> metadata(ImportDataType type, String name, int total) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dataType", type == null ? null : type.name());
        payload.put("fileName", name);
        payload.put("totalRows", total);
        return payload;
    }

    private static Map<String, Object> errorMap(CsvReader.RowError error) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("line", error.line()); map.put("column", error.column()); map.put("value", error.value());
        map.put("code", error.code()); map.put("message", error.message());
        return map;
    }

    private static int count(List<PreviewRow> rows, RowAction action) {
        return (int) rows.stream().filter(row -> row.action() == action).count();
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, message,
                Map.of("details", List.of(Map.of("field", field, "message", message))));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }
}
