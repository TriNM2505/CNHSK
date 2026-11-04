package com.cnhsk.learning.library.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "DeckRequest")
public record DeckWrite(
        @NotBlank @Pattern(regexp = "[^\\x00]*")
        @Schema(minLength = 1, maxLength = 100) String name,
        @Pattern(regexp = "[^\\x00]*") @Schema(maxLength = 500) String description,
        Long topicId) { }
