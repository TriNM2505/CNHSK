package com.cnhsk.learning.library.web;

import com.cnhsk.learning.library.DictionaryService;
import com.cnhsk.learning.library.DictionaryService.SearchResult;
import com.cnhsk.learning.library.LibraryRateLimiter;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/dictionary")
public class DictionaryController {

    private final DictionaryService dictionary;
    private final LibraryRateLimiter limiter;

    public DictionaryController(DictionaryService dictionary, LibraryRateLimiter limiter) {
        this.dictionary = dictionary;
        this.limiter = limiter;
    }

    // MVC initializes the model before resolving/validating the search method's query parameters.
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
