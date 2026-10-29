package com.cnhsk.learning.content.importer.web;

import java.util.Map;

import com.cnhsk.learning.content.importer.ImportPreview;
import com.cnhsk.learning.content.importer.ImportService;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.exception.GlobalExceptionHandler;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/imports")
public class ImportController {
    private final ImportService service;
    private final GlobalExceptionHandler errors;

    public ImportController(ImportService service, GlobalExceptionHandler errors) {
        this.service = service;
        this.errors = errors;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "uploadImportFile", tags = "imports", summary = "Tải lên và kiểm tra dữ liệu")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Lần nhập PENDING kèm xem trước"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Không có quyền hoặc CSRF không hợp lệ"),
            @ApiResponse(responseCode = "413", description = "FILE_TOO_LARGE kèm runId"),
            @ApiResponse(responseCode = "422", description = "Dữ liệu hoặc tệp không hợp lệ")})
    public ImportPreview upload(@Parameter(required = true, schema = @Schema(implementation = ImportDataType.class))
            @RequestParam(required = false) String dataType,
            @Parameter(required = true) @RequestParam(required = false) MultipartFile file) {
        return service.upload(dataType, file);
    }

    @Hidden
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> tooLarge(MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return errors.handleApiException(service.multipartTooLarge(), request);
    }

    @Hidden
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, Object>> malformed(MultipartException exception, HttpServletRequest request) {
        return errors.handleApiException(new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Yêu cầu tải tệp không hợp lệ.", Map.of()), request);
    }
}
