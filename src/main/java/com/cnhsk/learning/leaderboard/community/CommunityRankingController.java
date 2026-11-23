package com.cnhsk.learning.leaderboard.community;

import com.cnhsk.learning.leaderboard.LeaderboardDtos.Leaderboard;
import com.cnhsk.learning.leaderboard.LeaderboardRateLimiter;
import com.cnhsk.learning.leaderboard.LeaderboardServiceImpl;
import com.cnhsk.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Game-point ranking shown beside the community feed; public like the other leaderboards. */
@RestController
@SecurityRequirements
public class CommunityRankingController {
    private final LeaderboardServiceImpl service;
    private final LeaderboardRateLimiter limiter;

    public CommunityRankingController(@Lazy LeaderboardServiceImpl service, LeaderboardRateLimiter limiter) {
        this.service = service;
        this.limiter = limiter;
    }

    @GetMapping("/api/public/community/rankings")
    @Operation(operationId = "getCommunityRanking", tags = "leaderboard")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Bảng xếp hạng điểm game"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED"),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED")})
    public Leaderboard getCommunityRanking(
            @Parameter(schema = @Schema(allowableValues = {"week", "month", "year"}, defaultValue = "week"))
            @RequestParam(required = false) String period,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        limiter.check(user == null ? null : user.userId(), request.getRemoteAddr());
        return service.communityLeaderboard(period, user == null ? null : user.userId());
    }
}
