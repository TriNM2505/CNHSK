package com.cnhsk.learning.library.web;

import java.util.List;

import com.cnhsk.learning.api.Consumption.Source;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "TranslateResult")
public record TranslateResult(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String translated,
        @Schema(types = {"string", "null"}) String pinyin,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Segment> segments,
        @Schema(type = "boolean", types = {"boolean"}, requiredMode = Schema.RequiredMode.REQUIRED) boolean cached,
        @Schema(types = {"string", "null"}, nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) Source consumption) {
    public TranslateResult { segments = List.copyOf(segments); }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(name = "Segment")
    public record Segment(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String text,
            @Schema(types = {"string", "null"}) String pinyin,
            @Schema(type = "integer", types = {"integer", "null"}, format = "int64", nullable = true) Long lexemeId) {
        @Override public String toString() { return "Segment[text=[REDACTED], pinyin=[REDACTED], lexemeId=" + lexemeId + "]"; }
    }

    @Override public String toString() { return "TranslateResult[content=[REDACTED], cached=" + cached + ", consumption=" + consumption + "]"; }
}
