package com.cnhsk.learning.library.web;

import java.util.List;
import java.util.Map;

import com.cnhsk.learning.library.DeckService;
import com.cnhsk.learning.library.DeckService.DeckDetail;
import com.cnhsk.learning.library.DeckService.DeckSummary;
import com.cnhsk.learning.library.DictionaryService;
import com.cnhsk.shared.exception.GlobalExceptionHandler;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flashcard-decks")
@ApiResponses({@ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN, CSRF_TOKEN_INVALID hoặc COLLECTION_NOT_OWNED")})
public class DeckController {
    private final DeckService decks;
    private final GlobalExceptionHandler errors;

    public DeckController(DeckService decks, GlobalExceptionHandler errors) { this.decks = decks; this.errors = errors; }

    @GetMapping
    @Operation(operationId = "listDecks", tags = "flashcards")
    public List<DeckSummary> list() { return decks.list(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createDeck", tags = "flashcards")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Đã tạo"),
            @ApiResponse(responseCode = "404", description = "TOPIC_NOT_FOUND"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED hoặc LIBRARY_LIMIT_REACHED")})
    public DeckDetail create(@Valid @RequestBody DeckWrite request) { return decks.create(request); }

    @GetMapping("/{id}")
    @Operation(operationId = "getDeck", tags = "flashcards")
    public DeckDetail get(@Parameter(schema = @Schema(type = "integer", format = "int64")) @PathVariable String id) { return decks.detail(id); }

    @PatchMapping("/{id}")
    @Operation(operationId = "updateDeck", tags = "flashcards")
    @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")
    public DeckSummary update(@Parameter(schema = @Schema(type = "integer", format = "int64")) @PathVariable String id,
                              @Valid @RequestBody DeckWrite request) { return decks.update(id, request); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteDeck", tags = "flashcards")
    @ApiResponse(responseCode = "204", description = "Đã xoá mềm")
    public void delete(@Parameter(schema = @Schema(type = "integer", format = "int64")) @PathVariable String id) { decks.delete(id); }

    @Hidden
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> invalidBody(HttpServletRequest request) {
        return errors.handleApiException(DictionaryService.invalid("request", "Nội dung yêu cầu không hợp lệ."), request);
    }
}
