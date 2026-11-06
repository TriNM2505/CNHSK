package com.cnhsk.learning.mastery.web;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.mastery.MasteryQueryService;
import com.cnhsk.learning.mastery.MasteryQueryService.MasteryNodeDetail;
import com.cnhsk.learning.mastery.MasteryQueryService.MasteryTree;
import com.cnhsk.learning.mastery.RecommendationService;
import com.cnhsk.learning.mastery.RecommendationService.Recommendation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.MediaType;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/mastery", produces = MediaType.APPLICATION_JSON_VALUE)
public class MasteryController {

    private final MasteryQueryService mastery;
    private final RecommendationService recommendations;
    private final AuthApi auth;

    public MasteryController(MasteryQueryService mastery, RecommendationService recommendations, AuthApi auth) {
        this.mastery = mastery;
        this.recommendations = recommendations;
        this.auth = auth;
    }

    @GetMapping
    @Operation(operationId = "getMyMastery", tags = "mastery")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cây năng lực của người gọi"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN")
    })
    public MasteryTree mastery() {
        return mastery.mastery(auth.currentUser().userId());
    }

    @GetMapping("/nodes/{kid}")
    @Operation(operationId = "getMyMasteryNode", tags = "mastery")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chi tiết mastery của nút"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
            @ApiResponse(responseCode = "404", description = "KNOWLEDGE_NODE_NOT_FOUND")
    })
    public MasteryNodeDetail node(@PathVariable String kid) {
        return mastery.node(auth.currentUser().userId(), kid);
    }

    @GetMapping("/recommendations")
    @Operation(operationId = "getMyRecommendations", tags = "mastery")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tối đa 5 nút nên luyện hôm nay",
                    content = @Content(array = @ArraySchema(maxItems = 5,
                            schema = @Schema(implementation = Recommendation.class)))),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN")
    })
    public List<Recommendation> recommendations() {
        return recommendations.recommendations(auth.currentUser().userId());
    }
}
