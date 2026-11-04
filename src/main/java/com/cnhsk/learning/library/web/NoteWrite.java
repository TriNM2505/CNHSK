package com.cnhsk.learning.library.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "NoteRequest")
public record NoteWrite(@Size(max = 200) @Pattern(regexp = "[^\\x00]*") String title,
                        @NotBlank @Size(min = 1, max = 10_000) @Pattern(regexp = "[^\\x00]*") String content,
                        @Size(max = 10) List<@jakarta.validation.constraints.NotNull @Size(max = 50) @Pattern(regexp = "[^\\x00]*") String> tags,
                        Long lexemeId,
                        @Pattern(regexp = "DICTIONARY|TRANSLATION|MANUAL") String sourceType) {
    public NoteWrite {
        tags = tags == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(tags));
    }
}
