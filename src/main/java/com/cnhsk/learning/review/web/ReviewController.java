package com.cnhsk.learning.review.web;

import java.time.Instant;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.practice.PracticeService.SessionView;
import com.cnhsk.learning.review.ReviewSessionService;
import com.cnhsk.learning.review.ReviewSessionService.ReviewSummary;
import com.cnhsk.learning.review.ReviewSessionService.StartResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewSessionService reviews;
    private final AuthApi auth;

    public ReviewController(ReviewSessionService reviews, AuthApi auth) {
        this.reviews = reviews;
        this.auth = auth;
    }

    @GetMapping("/summary")
    @Operation(operationId = "getReviewSummary", tags = "review")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tóm tắt lịch ôn"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Chỉ LEARNER được truy cập")
    })
    public ReviewSummary summary() {
        return reviews.summary(auth.currentUser().userId());
    }

    @PostMapping("/sessions")
    @Operation(operationId = "startReviewSession", tags = "review")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lượt đang làm hoặc không có mục đến hạn"),
            @ApiResponse(responseCode = "201", description = "Lượt ôn mới"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Chỉ LEARNER được truy cập hoặc CSRF không hợp lệ"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED"),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED")
    })
    public ResponseEntity<?> start(@Valid @RequestBody(required = false) ReviewStartRequest request) {
        long userId = auth.currentUser().userId();
        StartResult result = reviews.start(userId, request != null && Boolean.TRUE.equals(request.ahead()));
        if (result.session() == null) {
            return ResponseEntity.ok(new NothingDue(null, "NOTHING_DUE"));
        }
        return result.created()
                ? ResponseEntity.status(HttpStatus.CREATED).body(result.session())
                : ResponseEntity.ok(result.session());
    }

    public record ReviewStartRequest(Boolean ahead) { }

    public record NothingDue(SessionView session, String reason) { }
}
