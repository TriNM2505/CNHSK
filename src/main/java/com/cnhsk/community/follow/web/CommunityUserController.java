package com.cnhsk.community.follow.web;

import com.cnhsk.community.follow.FollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/community/users", produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = "cookieAuth")
@SecurityRequirement(name = "bearerAuth")
public class CommunityUserController {
    private final FollowService follows;

    public CommunityUserController(FollowService follows) {
        this.follows = follows;
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getCommunityProfile", tags = "community-users")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = CommunityProfile.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Không có quyền",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public CommunityProfile getCommunityProfile(@PathVariable long id) {
        return follows.profile(id);
    }

    @PostMapping("/{id}/follow")
    @Operation(operationId = "followUser", tags = "community-users")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = FollowState.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Không có quyền",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "422", description = "SELF_FOLLOW · FOLLOW_LIMIT_REACHED",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public FollowState followUser(@PathVariable long id) {
        return follows.follow(id);
    }

    @DeleteMapping("/{id}/follow")
    @Operation(operationId = "unfollowUser", tags = "community-users")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = FollowState.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Không có quyền",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public FollowState unfollowUser(@PathVariable long id) {
        return follows.unfollow(id);
    }
}
