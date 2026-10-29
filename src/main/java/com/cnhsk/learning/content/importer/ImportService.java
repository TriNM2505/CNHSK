package com.cnhsk.learning.content.importer;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import com.cnhsk.learning.content.importer.validate.RowValidationResult;
import com.cnhsk.learning.content.importer.validate.RowValidator;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
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

    public ImportService(AuthApi auth, CsvReader reader, List<RowValidator> validators, ImportChangePlanner planner,
            ServiceRequestRepository requests, ImportProperties properties, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.auth = auth;
        this.reader = reader;
        this.validators = validators.stream().collect(Collectors.toUnmodifiableMap(RowValidator::dataType, Function.identity()));
        this.planner = planner;
        this.requests = requests;
        this.properties = properties;
        this.clock = clock;
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
            if (!fileName.endsWith(".csv")) throw new ImportFileException(ErrorCode.UNSUPPORTED_FILE_FORMAT);
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
            Instant expiresAt = clock.instant().plus(properties.getPreviewTtl());
            ServiceRequest run = new ServiceRequest(user.userId(), ServiceRequestKind.IMPORT);
            Map<String, Object> payload = metadata(type, fileName, validated.totalRows());
            payload.put("expiresAt", expiresAt.toString());
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
            int valid = rows.size();
            ImportPreview.Totals totals = new ImportPreview.Totals(validated.totalRows(), valid,
                    validated.totalRows() - valid, count(rows, RowAction.CREATE), count(rows, RowAction.UPDATE),
                    count(rows, RowAction.UNCHANGED));
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
