package com.cnhsk.learning.speaking.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

public final class SpeakingViews {
    private SpeakingViews() { }

    /** One word or sentence to read aloud; {@code audioUrl} is null when the browser voice reads the model. */
    @Schema(name = "SpeakingItem")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SpeakingItem(String kind, String text, String pinyin, String meaning, String audioUrl, int hskLevel) { }

    @Schema(name = "SpeakingItems")
    public record SpeakingItems(List<SpeakingItem> items) {
        public SpeakingItems { items = List.copyOf(items); }
    }

    /** Everything a browser needs for one signed recording upload; valid for about an hour. */
    @Schema(name = "SpeakingUploadSignature")
    public record SpeakingUploadSignature(String cloudName, String apiKey, String folder, long timestamp,
            String signature, String uploadUrl) { }
}
