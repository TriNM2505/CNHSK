package com.cnhsk.learning.pronunciation;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** Public summary with no practice items or answer keys. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record StageSummary(
        @Schema(requiredMode = REQUIRED) long id,
        @Schema(requiredMode = REQUIRED, minimum = "1", maximum = "8") short stageIndex,
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(types = {"string", "null"}) String description,
        List<String> targetSounds,
        @Schema(requiredMode = REQUIRED) BigDecimal passPercent,
        @Schema(requiredMode = REQUIRED) int unitCount,
        @Schema(requiredMode = REQUIRED) boolean playable,
        @Schema(requiredMode = REQUIRED) State state,
        @Schema(types = {"number", "null"}) BigDecimal bestAccuracy,
        @Schema(types = {"integer", "null"}, format = "int64") Long activeSessionId,
        @Schema(types = {"integer", "null"}) Integer activeAnswered) {

    @Schema(name = "StageState", enumAsRef = true)
    public enum State { LOCKED, AVAILABLE, COMPLETED }
}
