package com.cnhsk.community.post.web;

import java.time.Clock;
import java.util.List;

import com.cnhsk.community.post.ModeratorPostService;
import com.cnhsk.community.post.PostQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Moderators publish and pin posts; everyone reads what is pinned. */
@RestController
public class ModeratorPostController {
    private final ModeratorPostService moderatorPosts;
    private final PostQueryService queries;
    private final Clock clock;

    public ModeratorPostController(ModeratorPostService moderatorPosts, PostQueryService queries, Clock clock) {
        this.moderatorPosts = moderatorPosts;
        this.queries = queries;
        this.clock = clock;
    }

    @GetMapping(value = "/api/public/community/posts/pinned", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "listPinnedPosts", tags = "public-posts")
    @SecurityRequirements
    @ApiResponses(@ApiResponse(responseCode = "200", description = "OK",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = PinnedPost.class)))))
    public List<PinnedPost> listPinnedPosts() {
        return queries.pinnedList(clock.instant());
    }

    @PostMapping(value = "/api/community/moderation/posts", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createModeratorPost", tags = "moderation")
    @SecurityRequirement(name = "cookieAuth")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đã đăng",
                    content = @Content(schema = @Schema(implementation = OwnPost.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Không có quyền",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "422", description = "Dữ liệu không hợp lệ",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public OwnPost createModeratorPost(@Valid @RequestBody ModeratorPostWrite request) {
        return moderatorPosts.create(request);
    }

    @PutMapping(value = "/api/community/moderation/posts/{id}/pin", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "pinPost", tags = "moderation")
    @SecurityRequirement(name = "cookieAuth")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã ghim hoặc bỏ ghim"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Không có quyền",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "404", description = "POST_NOT_FOUND",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "422", description = "Dữ liệu không hợp lệ",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public void pinPost(@PathVariable long id, @RequestBody PinWrite request) {
        moderatorPosts.pin(id, request.pinnedUntil());
    }
}
