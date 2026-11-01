package com.cnhsk.community.post.web;

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
        Instant updatedAt) {
}
