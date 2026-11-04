package com.cnhsk.learning.library.web;

import java.util.List;

import com.cnhsk.learning.library.DictionaryService;
import com.cnhsk.learning.library.DictionaryService.SearchResult;
import com.cnhsk.learning.library.LibraryRateLimiter;
import com.cnhsk.learning.library.LexemeDetailService;
import com.cnhsk.learning.library.LexemeDetailService.LexemeDetail;
import com.cnhsk.learning.library.RadicalService;
import com.cnhsk.learning.library.RadicalService.RadicalCount;
import com.cnhsk.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/dictionary")
public class DictionaryController {

    private final DictionaryService dictionary;
    private final LibraryRateLimiter limiter;
    private final RadicalService radicals;
    private final LexemeDetailService details;

    public DictionaryController(DictionaryService dictionary, LibraryRateLimiter limiter,
                                RadicalService radicals, LexemeDetailService details) {
        this.dictionary = dictionary;
        this.limiter = limiter;
        this.radicals = radicals;
        this.details = details;
    }

    // MVC counts every dictionary request before resolving/validating endpoint parameters.
    @ModelAttribute
    public void countLookup(HttpServletRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        limiter.check(user == null ? null : user.userId(), request.getRemoteAddr());
    }

    @GetMapping("/search")
    @SecurityRequirements
    @Operation(operationId = "searchDictionary", tags = "dictionary", summary = "Tra chữ Hán, pinyin, nghĩa Việt")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Kết quả tra cứu, có thể rỗng"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED"),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED kèm retryAfterSeconds; khách có loginSuggested")})
    public SearchResult search(
            @Parameter(required = true, schema = @Schema(allowableValues = {"HANZI", "PINYIN", "MEANING"}))
            @RequestParam(required = false) String type,
            @Parameter(required = true) @RequestParam(required = false) String q,
            @Parameter(schema = @Schema(type = "integer", defaultValue = "0", minimum = "0")) @RequestParam(required = false) String page,
            @Parameter(schema = @Schema(type = "integer", defaultValue = "20", minimum = "1", maximum = "50"))
            @RequestParam(required = false) String size) {
        return dictionary.search(type, q, integer(page, 0, "page"), integer(size, 20, "size"));
    }

    @GetMapping("/radicals")
    @SecurityRequirements
    @Operation(operationId = "listRadicals", tags = "dictionary", summary = "Bộ thủ có dữ liệu")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Danh sách bộ thủ"),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED")})
    public List<RadicalCount> radicals() {
        return radicals.radicals();
    }

    @GetMapping("/by-radical")
    @SecurityRequirements
    @Operation(operationId = "searchByRadical", tags = "dictionary", summary = "Chữ theo bộ thủ và số nét")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Kết quả, có thể rỗng hoặc gần đúng"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED"),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED")})
    public SearchResult byRadical(
            @Parameter(schema = @Schema(maxLength = 10)) @RequestParam(required = false) String radical,
            @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "64"))
            @RequestParam(required = false) String strokes,
            @Parameter(schema = @Schema(type = "integer", defaultValue = "0", minimum = "0"))
            @RequestParam(required = false) String page,
            @Parameter(schema = @Schema(type = "integer", defaultValue = "20", minimum = "1", maximum = "50"))
            @RequestParam(required = false) String size) {
        return radicals.search(radical, strokes == null ? null : integer(strokes, 0, "strokes"),
                integer(page, 0, "page"), integer(size, 20, "size"));
    }

    @GetMapping("/lexemes/{id}")
    @SecurityRequirements
    @Operation(operationId = "getLexeme", tags = "dictionary", summary = "Chi tiết mục từ")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Chi tiết công khai"),
            @ApiResponse(responseCode = "404", description = "LEXEME_NOT_FOUND"),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED")})
    public LexemeDetail detail(@Parameter(schema = @Schema(type = "integer", format = "int64")) @PathVariable String id) {
        return details.detail(id);
    }

    private static int integer(String value, int fallback, String field) {
        if (value == null) return fallback;
        try {
            if (!value.matches("-?[0-9]+")) throw new NumberFormatException();
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw DictionaryService.invalid(field, "Giá trị phải là số nguyên hợp lệ.");
        }
    }
}
