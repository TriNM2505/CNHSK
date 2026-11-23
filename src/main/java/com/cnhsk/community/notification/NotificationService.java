package com.cnhsk.community.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.community.notification.web.NotificationViews.Notification;
import com.cnhsk.community.notification.web.NotificationViews.NotificationPage;
import com.cnhsk.community.notification.web.NotificationViews.NotificationSummary;
import com.cnhsk.community.notification.web.NotificationViews.Task;
import org.springframework.stereotype.Service;

/**
 * The bell: tasks staff still have to handle, counted live, plus what happened to the user lately, read from the
 * data where it happened. Opening the bell marks everything up to that moment as read.
 */
@Service
public class NotificationService {
    /** Notifications older than this are no longer shown. */
    static final Duration WINDOW = Duration.ofDays(90);
    static final int PAGE_SIZE = 50;

    private final CommunityNotificationRepository community;
    private final AuthApi auth;
    private final Clock clock;

    public NotificationService(CommunityNotificationRepository community, AuthApi auth, Clock clock) {
        this.community = community;
        this.auth = auth;
        this.clock = clock;
    }

    public NotificationSummary summary() {
        CurrentUser user = auth.currentUser();
        Instant since = clock.instant().minus(WINDOW);
        Instant seenAt = auth.notificationsSeenAt(user.userId()).orElse(null);
        return new NotificationSummary(community.countAfter(user.userId(), since, seenAt), tasks(user));
    }

    public NotificationPage list() {
        CurrentUser user = auth.currentUser();
        Instant since = clock.instant().minus(WINDOW);
        Instant seenAt = auth.notificationsSeenAt(user.userId()).orElse(null);
        var items = community.recent(user.userId(), since, PAGE_SIZE);
        var actors = items.stream().map(CommunityNotificationRepository.Item::actorId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> names = actors.isEmpty() ? Map.of() : auth.displayNames(actors);
        var notifications = items.stream().map(item -> new Notification(item.key(), item.type(), item.at(),
                seenAt == null || item.at().isAfter(seenAt), item.actorId(),
                item.actorId() == null ? null : names.get(item.actorId()), item.total(), item.postId(), item.postTitle(),
                item.commentId(), item.excerpt())).toList();
        return new NotificationPage(notifications, tasks(user));
    }

    public void markSeen() {
        auth.markNotificationsSeen(auth.currentUser().userId(), clock.instant());
    }

    private List<Task> tasks(CurrentUser user) {
        List<Task> tasks = new ArrayList<>();
        if (user.roles().contains(RoleCode.MODERATOR)) {
            community.moderatorQueues().forEach((key, count) -> {
                if (count > 0) tasks.add(new Task(key, count));
            });
        }
        tasks.sort(java.util.Comparator.comparing(Task::key));
        return List.copyOf(tasks);
    }
}
