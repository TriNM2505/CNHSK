package com.cnhsk.learning.library.web;

import java.util.Map;

import com.cnhsk.learning.library.DictionaryService;
import com.cnhsk.learning.library.NotebookService;
import com.cnhsk.learning.library.NotebookService.Note;
import com.cnhsk.learning.library.NotebookService.NotePage;
import com.cnhsk.shared.exception.GlobalExceptionHandler;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notes")
@ApiResponses({@ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN, CSRF_TOKEN_INVALID hoặc COLLECTION_NOT_OWNED")})
public class NoteController {

    private final NotebookService notes;
    private final GlobalExceptionHandler errors;

    public NoteController(NotebookService notes, GlobalExceptionHandler errors) {
        this.notes = notes;
        this.errors = errors;
    }

    @GetMapping
    @Operation(operationId = "listNotes", tags = "notes")
    @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")
    public NotePage list(@RequestParam(required = false) String q, @RequestParam(required = false) String tag,
            @Parameter(schema = @Schema(type = "integer", minimum = "0", defaultValue = "0")) @RequestParam(required = false) String page,
            @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "50", defaultValue = "20"))
            @RequestParam(required = false) String size) {
        return notes.list(q, tag, integer(page, 0, "page"), integer(size, 20, "size"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createNote", tags = "notes")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Đã tạo"),
            @ApiResponse(responseCode = "404", description = "LEXEME_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "NOTE_DUPLICATE"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED hoặc LIBRARY_LIMIT_REACHED")})
    public Note create(@Valid @RequestBody NoteWrite request) { return notes.create(request); }

    @GetMapping("/{id}")
    @Operation(operationId = "getNote", tags = "notes")
    public Note get(@Parameter(schema = @Schema(type = "integer", format = "int64")) @PathVariable String id) {
        return notes.get(id);
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateNote", tags = "notes")
    @ApiResponses({@ApiResponse(responseCode = "404", description = "LEXEME_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "NOTE_DUPLICATE"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")})
    public Note update(@Parameter(schema = @Schema(type = "integer", format = "int64")) @PathVariable String id,
                       @Valid @RequestBody NoteWrite request) { return notes.update(id, request); }

    @Hidden
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> invalidBody(HttpServletRequest request) {
        return errors.handleApiException(DictionaryService.invalid("request", "Nội dung yêu cầu không hợp lệ."), request);
    }

    private static int integer(String value, int fallback, String field) {
        if (value == null) return fallback;
        try {
            if (!value.matches("-?[0-9]+")) throw new NumberFormatException();
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) { throw DictionaryService.invalid(field, "Giá trị phải là số nguyên hợp lệ."); }
    }
}
