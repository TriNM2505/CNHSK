package com.cnhsk.learning.content.importer;

import java.util.List;
import java.util.Map;

import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** A file-level import failure; messages never include raw CSV content or parser errors. */
public class ImportFileException extends ApiException {

    public ImportFileException(ErrorCode code) {
        this(code, List.of());
    }

    public ImportFileException(ErrorCode code, List<Map<String, String>> details) {
        super(code, status(code), message(code), details.isEmpty() ? Map.of() : Map.of("details", List.copyOf(details)));
    }

    private static HttpStatus status(ErrorCode code) {
        return switch (code) {
            case FILE_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case UNSUPPORTED_FILE_FORMAT, ENCODING_ERROR, TEMPLATE_MISMATCH, EMPTY_FILE, TOO_MANY_ROWS ->
                    HttpStatus.UNPROCESSABLE_ENTITY;
            default -> throw new IllegalArgumentException("Not an import file error code: " + code);
        };
    }

    private static String message(ErrorCode code) {
        return switch (code) {
            case FILE_TOO_LARGE -> "File vượt quá kích thước tối đa 2 MB.";
            case UNSUPPORTED_FILE_FORMAT -> "File phải là CSV hợp lệ theo định dạng RFC 4180.";
            case ENCODING_ERROR -> "File không phải UTF-8. Hãy lưu lại file bằng UTF-8 rồi thử lại.";
            case TEMPLATE_MISMATCH -> "Các cột trong file không khớp mẫu nhập dữ liệu.";
            case EMPTY_FILE -> "File không có dòng dữ liệu.";
            case TOO_MANY_ROWS -> "File vượt quá giới hạn 5.000 dòng dữ liệu.";
            default -> throw new IllegalArgumentException("Not an import file error code: " + code);
        };
    }
}
