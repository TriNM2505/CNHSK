package com.cnhsk.community.game.web;

import java.util.List;

import com.cnhsk.community.game.GameCatalogService;
import com.cnhsk.community.game.web.GameViews.GameInfo;
import com.cnhsk.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GameController {
    private final GameCatalogService catalog;

    public GameController(GameCatalogService catalog) { this.catalog = catalog; }

    @GetMapping("/api/community/games")
    @Operation(operationId = "listGames", tags = "games")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Danh mục game và thành tích của người gọi nếu đã đăng nhập")
    public List<GameInfo> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return catalog.list(user == null ? null : user.userId());
    }
}
