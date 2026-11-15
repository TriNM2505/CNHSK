package com.cnhsk.learning.vocab;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

public final class TopicPrerequisiteModels {
    private TopicPrerequisiteModels() { }

    @Schema(name = "TopicRef")
    public record TopicRef(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int hskLevel) { }

    @Schema(name = "AdminTopic")
    public record AdminTopic(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int hskLevel,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<TopicRef> prereqs,
            @JsonProperty("isRoot") @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean isRoot,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<String> warnings) { }

    @Schema(name = "AdminTopicPage")
    public record TopicPage(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AdminTopic> items,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements) { }

    @Schema(name = "PrerequisiteWrite")
    public record PrerequisiteWrite(
            @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED), maxItems = 10,
                    uniqueItems = true, schema = @Schema(type = "integer", format = "int64")) List<Long> prereqIds) { }

    @Schema(name = "TopicPrerequisiteCycleDetails")
    public record CycleDetails(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Long> path) { }

    @Schema(name = "TopicPrerequisiteFieldError")
    public record FieldError(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String field,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message) { }

    @Schema(name = "TopicPrerequisiteError")
    public record PrerequisiteError(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String error_code,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String request_id,
            @Schema(oneOf = {CycleDetails.class, FieldError[].class}) Object details) { }
}
