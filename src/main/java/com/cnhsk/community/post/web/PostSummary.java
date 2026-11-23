package com.cnhsk.community.post.web;

import java.util.List;

import com.cnhsk.community.media.web.MediaItem;

import java.time.Instant;
import com.cnhsk.community.common.PostCategory;
import com.cnhsk.community.common.UserRef;
import io.swagger.v3.oas.annotations.media.Schema;

public record PostSummary(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PostCategory category,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 200) String excerpt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UserRef author,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant publishedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long commentCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long likeCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<MediaItem> media) {

    public PostSummary {
        media = media == null ? List.of() : List.copyOf(media);
    }

    public PostSummary(long id, String title, PostCategory category, String excerpt, UserRef author,
            Instant publishedAt, long commentCount, long likeCount) {
        this(id, title, category, excerpt, author, publishedAt, commentCount, likeCount, List.of());
    }
}
