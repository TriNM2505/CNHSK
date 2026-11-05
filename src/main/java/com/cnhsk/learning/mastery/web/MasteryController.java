package com.cnhsk.learning.mastery.web;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.mastery.MasteryQueryService;
import com.cnhsk.learning.mastery.MasteryQueryService.MasteryNodeDetail;
import com.cnhsk.learning.mastery.MasteryQueryService.MasteryTree;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/mastery", produces = MediaType.APPLICATION_JSON_VALUE)
public class MasteryController {

    private final MasteryQueryService mastery;
    private final AuthApi auth;

    public MasteryController(MasteryQueryService mastery, AuthApi auth) {
        this.mastery = mastery;
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
}
