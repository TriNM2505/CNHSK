package com.cnhsk.community.post.web;

import com.cnhsk.community.common.PostCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The only client-writable fields for a Community post. */
public record PostWrite(
        @NotBlank @Size(min = 1, max = 200) String title,
        @NotBlank @Size(min = 1, max = 50_000) String content,
        @NotNull PostCategory category,
        @NotNull Boolean submit) {

    public PostWrite {
        if (title != null) {
            title = title.strip();
        }
        if (content != null) {
            content = content.strip();
        }
    }
}
