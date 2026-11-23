package com.cnhsk.community.post.web;

import java.time.Instant;
import java.util.List;

import com.cnhsk.community.media.web.MediaItem;

import com.cnhsk.community.common.PostCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A moderator's post, published at once. {@code contestId} links a contest guide; {@code pinnedUntil} keeps it on top
 * of the feed and defaults to the contest's end when a contest is linked.
 */
@Schema(name = "ModeratorPostWrite")
public record ModeratorPostWrite(
        @NotBlank @Size(min = 1, max = 200) String title,
        @NotBlank @Size(min = 1, max = 50_000) String content,
        @NotNull PostCategory category,
        @Schema(types = {"integer", "null"}, format = "int64") Long contestId,
        @Schema(types = {"string", "null"}, format = "date-time") Instant pinnedUntil,
        List<MediaItem> media,
        @Schema(types = {"string", "null"}, format = "date-time", description = "Cuộc thi bình luận: giờ đóng bình luận")
        Instant commentsCloseAt) {

    public ModeratorPostWrite {
        if (title != null) {
            title = title.strip();
        }
        if (content != null) {
            content = content.strip();
        }
    }
}
