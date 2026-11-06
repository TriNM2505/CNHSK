package com.cnhsk.learning.library.web;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.library.FlashcardReviewService;
import com.cnhsk.learning.library.FlashcardReviewService.ReviewResult;
import com.cnhsk.learning.library.LibraryRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flashcards")
public class FlashcardReviewController {
    private final FlashcardReviewService reviews;
    private final LibraryRateLimiter limiter;
    private final AuthApi auth;
    private final ObjectMapper mapper;

    public FlashcardReviewController(FlashcardReviewService reviews, LibraryRateLimiter limiter, AuthApi auth, ObjectMapper mapper) {
        this.reviews = reviews;
        this.limiter = limiter;
        this.auth = auth;
        this.mapper = mapper;
    }

    @PostMapping("/{cardId}/review")
    @Operation(operationId = "reviewCard", tags = "flashcards")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(schema = @Schema(implementation = FlashcardReviewWrite.class)))
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Đã cập nhật lịch ôn"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN, CSRF_TOKEN_INVALID hoặc COLLECTION_NOT_OWNED"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED"),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED kèm retryAfterSeconds")})
    public ReviewResult review(@Parameter(schema = @Schema(type = "integer", format = "int64")) @PathVariable String cardId,
                               @RequestBody(required = false) String body) {
        var rating = FlashcardReviewWrite.validate(body, mapper);
        limiter.checkFlashcardReview(auth.currentUser().userId());
        return reviews.review(cardId, rating);
    }
}
