package com.cnhsk.community.comment.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The only client-writable field for a comment or reply. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CommentWrite(@NotBlank @Size(min = 1, max = 5_000) String content) {

    public CommentWrite {
        if (content != null) {
            content = content.strip();
        }
    }
}
