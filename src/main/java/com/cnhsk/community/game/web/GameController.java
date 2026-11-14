package com.cnhsk.community.game.web;

import java.util.List;
import java.util.Map;

import com.cnhsk.community.game.GameCatalogService;
import com.cnhsk.community.game.GameRoundService;
import com.cnhsk.community.game.web.GameViews.GameInfo;
import com.cnhsk.shared.config.RequestIdFilter;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GameController {
    private final GameCatalogService catalog;

    private final GameRoundService rounds;
    private final ObjectMapper mapper;

    public GameController(GameCatalogService catalog, GameRoundService rounds, ObjectMapper mapper) {
        this.catalog = catalog;
        this.rounds = rounds;
        this.mapper = mapper;
    }

    @PostMapping("/api/community/games/{code}/rounds")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "startRound", tags = "games")
    @ApiResponse(responseCode = "201", description = "Ván mới, không có đáp án")
    @ApiResponse(responseCode = "400", description = "Mã game không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không có quyền")
    @ApiResponse(responseCode = "422", description = "Dữ liệu không hợp lệ hoặc game chưa sẵn sàng")
    @ApiResponse(responseCode = "429", description = "Đã đạt giới hạn lượt")
    public GameViews.RoundStart start(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String code, @RequestBody(required = false) String body) {
        var game = GameRoundStartRequest.code(code);
        var request = GameRoundStartRequest.parse(body, mapper);
        return rounds.start(user.userId(), game, request.hskLevel());
    }

    @Hidden
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadableBody() {
        return ResponseEntity.unprocessableEntity().body(Map.of("error_code", ErrorCode.VALIDATION_FAILED.name(),
                "message", "Dữ liệu tạo ván không hợp lệ.", "request_id", RequestIdFilter.currentRequestId()));
    }

    @GetMapping("/api/community/games")
    @Operation(operationId = "listGames", tags = "games")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Danh mục game và thành tích của người gọi nếu đã đăng nhập")
    public List<GameInfo> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return catalog.list(user == null ? null : user.userId());
    }
}
