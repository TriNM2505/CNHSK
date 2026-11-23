package com.cnhsk.community.post.web;

import java.util.List;

import com.cnhsk.community.common.PostCategory;
import com.cnhsk.community.media.web.MediaItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The only client-writable fields for a Community post. */
public record PostWrite(
        @NotBlank @Size(min = 1, max = 200) String title,
        @NotBlank @Size(min = 1, max = 50_000) String content,
        @NotNull PostCategory category,
        @NotNull Boolean submit,
        @Schema(description = "Ảnh/video đã tải lên Cloudinary, tối đa 4; bỏ trống khi sửa thì giữ nguyên")
        List<MediaItem> media) {

    public PostWrite(String title, String content, PostCategory category, Boolean submit) {
        this(title, content, category, submit, null);
    }

    public PostWrite {
        if (title != null) {
            title = title.strip();
        }
        if (content != null) {
            content = content.strip();
        }
    }
}
