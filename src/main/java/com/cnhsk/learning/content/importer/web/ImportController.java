package com.cnhsk.learning.content.importer.web;

import java.util.List;
import java.util.Map;

import com.cnhsk.learning.content.importer.ImportPreview;
import com.cnhsk.learning.content.importer.ImportService;
import com.cnhsk.learning.content.importer.ImportService.ImportRun;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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

    @PostMapping(value = "/{id}/confirm", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "confirmImportRun", tags = "imports", summary = "Xác nhận ghi các dòng hợp lệ")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "COMPLETED"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Không có quyền hoặc CSRF không hợp lệ"),
            @ApiResponse(responseCode = "404", description = "IMPORT_RUN_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "Trạng thái, nhập lại hoặc lần nhập đồng thời"),
            @ApiResponse(responseCode = "410", description = "IMPORT_PREVIEW_EXPIRED"),
            @ApiResponse(responseCode = "422", description = "NO_VALID_ROWS hoặc VALIDATION_FAILED")})
    public ImportRun confirm(@PathVariable long id, @RequestBody ConfirmRequest request) {
        return service.confirm(id, request.acknowledgeRerun());
    }

    public record ConfirmRequest(@Schema(defaultValue = "false") boolean acknowledgeRerun) {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static ConfirmRequest fromJson(JsonNode body) {
            if (!body.isObject() || (body.has("acknowledgeRerun") && !body.get("acknowledgeRerun").isBoolean())) {
                throw validation("acknowledgeRerun", "Giá trị xác nhận nhập lại phải là boolean.");
            }
            return new ConfirmRequest(body.path("acknowledgeRerun").asBoolean(false));
        }
    }

    @Hidden
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> invalidConfirmRequest(Exception exception, HttpServletRequest request) {
        return errors.handleApiException(validation("request", "Yêu cầu xác nhận nhập dữ liệu không hợp lệ."), request);
    }

    private static ApiException validation(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, message,
                Map.of("details", List.of(Map.of("field", field, "message", message))));
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
