package com.cnhsk.community.post.web;

import java.util.List;

import com.cnhsk.community.media.web.MediaItem;

import java.time.Instant;

import com.cnhsk.community.common.PostCategory;
import com.cnhsk.community.common.PostStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Owner-only projection returned after creating or updating a post. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record OwnPost(
        long id,
        String title,
        String content,
        PostCategory category,
        PostStatus status,
        String reviewReason,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        List<MediaItem> media) {

    public OwnPost {
        media = media == null ? List.of() : List.copyOf(media);
    }

    public OwnPost(long id, String title, String content, PostCategory category, PostStatus status,
            String reviewReason, Instant publishedAt, Instant createdAt, Instant updatedAt) {
        this(id, title, content, category, status, reviewReason, publishedAt, createdAt, updatedAt, List.of());
    }
}
