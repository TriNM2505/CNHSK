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
import com.cnhsk.learning.api.LearningNotificationApi;
import com.cnhsk.learning.api.StaffQueueApi;
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
    private final StaffQueueApi staffQueues;
    private final LearningNotificationApi learning;
    private final AuthApi auth;
    private final Clock clock;

    public NotificationService(CommunityNotificationRepository community, StaffQueueApi staffQueues,
            LearningNotificationApi learning, AuthApi auth, Clock clock) {
        this.community = community;
        this.learning = learning;
        this.staffQueues = staffQueues;
        this.auth = auth;
        this.clock = clock;
    }

    public NotificationSummary summary() {
        CurrentUser user = auth.currentUser();
        Instant since = clock.instant().minus(WINDOW);
        Instant seenAt = auth.notificationsSeenAt(user.userId()).orElse(null);
        long unread = community.countAfter(user.userId(), since, seenAt) + learning.countAfter(user.userId(), since, seenAt);
        return new NotificationSummary(unread, tasks(user));
    }

    public NotificationPage list() {
        CurrentUser user = auth.currentUser();
        Instant since = clock.instant().minus(WINDOW);
        Instant seenAt = auth.notificationsSeenAt(user.userId()).orElse(null);
        var items = community.recent(user.userId(), since, PAGE_SIZE);
        var actors = items.stream().map(CommunityNotificationRepository.Item::actorId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> names = actors.isEmpty() ? Map.of() : auth.displayNames(actors);
        List<Notification> notifications = new ArrayList<>();
        items.forEach(item -> notifications.add(new Notification(item.key(), item.type(), item.at(),
                seenAt == null || item.at().isAfter(seenAt), item.actorId(),
                item.actorId() == null ? null : names.get(item.actorId()), item.total(), item.postId(), item.postTitle(),
                item.commentId(), item.excerpt())));
        // Learning events reuse the generic slots: the amount (score, points, approved count) goes in total, the
        // service request in postId and the second number (rejected count) in commentId.
        learning.recent(user.userId(), since, PAGE_SIZE).forEach(item -> notifications.add(new Notification(
                item.key(), item.type(), item.at(), seenAt == null || item.at().isAfter(seenAt), null, null,
                (int) Math.min(Integer.MAX_VALUE, item.amount()), item.refId(), null, item.second(), item.detail())));
        notifications.sort(java.util.Comparator.comparing(Notification::at).reversed());
        return new NotificationPage(notifications.subList(0, Math.min(PAGE_SIZE, notifications.size())), tasks(user));
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
        if (user.roles().contains(RoleCode.TEACHER)) {
            staffQueues.teacherQueues(user.userId(), clock.instant()).forEach((key, count) -> {
                if (count > 0) tasks.add(new Task(key, count));
            });
        }
        return List.copyOf(tasks);
    }
}
