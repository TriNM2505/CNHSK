package com.cnhsk.learning.hanzi.web;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.hanzi.WritingChallengeService;
import com.cnhsk.learning.practice.PracticeService.SessionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/writing/challenges")
public class WritingChallengeController {
    private final WritingChallengeService challenges;
    private final AuthApi auth;

    public WritingChallengeController(WritingChallengeService challenges, AuthApi auth) {
        this.challenges = challenges;
        this.auth = auth;
    }

    @PostMapping
    @Operation(operationId = "startWritingChallenge", tags = "writing")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lượt thử thách đang làm"),
            @ApiResponse(responseCode = "201", description = "Lượt thử thách mới"),
            @ApiResponse(responseCode = "403", description = "Chỉ LEARNER được truy cập"),
            @ApiResponse(responseCode = "422", description = "NOT_ENOUGH_CHARACTERS hoặc VALIDATION_FAILED")
    })
    public ResponseEntity<SessionView> start() {
        var result = challenges.start(auth.currentUser().userId());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.session());
    }
}
