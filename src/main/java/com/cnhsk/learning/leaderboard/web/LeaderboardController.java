package com.cnhsk.learning.leaderboard.web;

import java.util.List;

import com.cnhsk.learning.leaderboard.LeaderboardDtos.GameOption;
import com.cnhsk.learning.leaderboard.LeaderboardDtos.Leaderboard;
import com.cnhsk.learning.leaderboard.LeaderboardDtos.TopicOption;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/leaderboards")
@SecurityRequirements
public class LeaderboardController {
    private final LeaderboardServiceImpl service;
    private final LeaderboardRateLimiter limiter;

    // Resolve only for leaderboard requests; 019 can substitute its rankOf-only interface independently.
    public LeaderboardController(@Lazy LeaderboardServiceImpl service, LeaderboardRateLimiter limiter) {
        this.service = service;
        this.limiter = limiter;
    }

    @ModelAttribute
    public void countGuestRequest(HttpServletRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        limiter.check(user == null ? null : user.userId(), request.getRemoteAddr());
    }

    @GetMapping("/topics")
    @Operation(operationId = "listLeaderboardTopics", tags = "leaderboard")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Chủ đề"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED"),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED")})
    public List<TopicOption> listLeaderboardTopics(
            @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "9"))
            @RequestParam(required = false) String hsk) {
        Long level = hsk == null ? null : integer(hsk, "hsk");
        if (level != null && (level < 1 || level > 9)) throw LeaderboardServiceImpl.invalid("hsk", "Cấp HSK phải từ 1 đến 9.");
        return service.listTopics(level == null ? null : level.intValue());
    }

    @GetMapping("/topics/{topicId}")
    @Operation(operationId = "getTopicLeaderboard", tags = "leaderboard")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Bảng xếp hạng"),
            @ApiResponse(responseCode = "404", description = "TOPIC_NOT_FOUND"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED"),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED")})
    public Leaderboard getTopicLeaderboard(
            @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) @PathVariable String topicId,
            @Parameter(schema = @Schema(allowableValues = {"week", "month", "all"}, defaultValue = "week"))
            @RequestParam(required = false) String period, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.topicLeaderboard(integer(topicId, "topicId"), period, user == null ? null : user.userId());
    }

    @GetMapping("/games")
    @Operation(operationId = "listLeaderboardGames", tags = "leaderboard")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Game"),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED")})
    public List<GameOption> listLeaderboardGames() { return service.listGames(); }

    @GetMapping("/games/{gameCode}")
    @Operation(operationId = "getGameLeaderboard", tags = "leaderboard")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Bảng xếp hạng"),
            @ApiResponse(responseCode = "422", description = "INVALID_GAME_CODE · VALIDATION_FAILED"),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED")})
    public Leaderboard getGameLeaderboard(@PathVariable String gameCode,
            @Parameter(schema = @Schema(allowableValues = {"week", "month", "all"}, defaultValue = "week"))
            @RequestParam(required = false) String period, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.gameLeaderboard(gameCode, period, user == null ? null : user.userId());
    }

    private static long integer(String value, String field) {
        try {
            if (!value.matches("-?[0-9]+")) throw new NumberFormatException();
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw LeaderboardServiceImpl.invalid(field, "Giá trị phải là số nguyên hợp lệ.");
        }
    }
}
