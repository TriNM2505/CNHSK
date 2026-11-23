package com.cnhsk.learning.trial.web;

import com.cnhsk.learning.trial.TrialService;
import com.cnhsk.learning.trial.web.TrialViews.TrialSet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Open to everyone; nothing is recorded, so a visitor can try learning before creating an account. */
@RestController
public class TrialController {
    private final TrialService service;

    public TrialController(TrialService service) {
        this.service = service;
    }

    @GetMapping("/api/public/learning/trial")
    @Operation(operationId = "getLearningTrial", tags = "trial")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Câu hỏi học thử"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")})
    public TrialSet trial(@RequestParam(defaultValue = "VOCAB") String feature,
            @RequestParam(defaultValue = "1") int hskLevel) {
        return service.questions(feature, hskLevel);
    }
}
