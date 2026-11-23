package com.cnhsk.community.post.web;

import java.time.Instant;
import java.util.List;

import com.cnhsk.community.media.web.MediaItem;

import com.cnhsk.community.common.PostCategory;
import com.cnhsk.community.common.UserRef;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/** A post shown above the feed; a contest guide carries its contest so the card can open it. */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "PinnedPost", requiredProperties = {"id", "title", "category", "excerpt", "author", "publishedAt",
        "commentCount", "likeCount", "contestId", "pinnedUntil"})
public record PinnedPost(long id, String title, PostCategory category, String excerpt, UserRef author,
        Instant publishedAt, long commentCount, long likeCount,
        @Schema(types = {"integer", "null"}, format = "int64") Long contestId, Instant pinnedUntil,
        List<MediaItem> media) {
}
