package com.cnhsk.community.notification.web;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Payloads of the notification bell. */
public final class NotificationViews {
    private NotificationViews() { }

    /** A queue the user still has to work through, such as PENDING_POSTS. */
    @Schema(name = "NotificationTask")
    public record Task(String key, long count) { }

    @Schema(name = "NotificationSummary")
    public record NotificationSummary(long unread, List<Task> tasks) {
        public NotificationSummary { tasks = List.copyOf(tasks); }
    }

    /**
     * One line in the bell. {@code type} is POST_APPROVED, POST_REJECTED, POST_HIDDEN, COMMENT, REPLY, LIKE or FOLLOW;
     * {@code total} is how many people liked the post for LIKE and 1 otherwise.
     */
    @Schema(name = "Notification")
    public record Notification(String key, String type, Instant at, boolean unread,
            @Schema(types = {"integer", "null"}) Long actorId, @Schema(types = {"string", "null"}) String actorName,
            int total, @Schema(types = {"integer", "null"}) Long postId,
            @Schema(types = {"string", "null"}) String postTitle, @Schema(types = {"integer", "null"}) Long commentId,
            @Schema(types = {"string", "null"}) String excerpt) { }

    @Schema(name = "NotificationPage")
    public record NotificationPage(List<Notification> items, List<Task> tasks) {
        public NotificationPage {
            items = List.copyOf(items);
            tasks = List.copyOf(tasks);
        }
    }
}
