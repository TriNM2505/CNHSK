package com.cnhsk.community.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.community.notification.web.NotificationViews.Notification;
import com.cnhsk.community.notification.web.NotificationViews.Task;
import com.cnhsk.learning.api.LearningNotificationApi;
import com.cnhsk.learning.api.StaffQueueApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-24T05:00:00Z");
    private static final Instant SINCE = NOW.minus(Duration.ofDays(90));
    private static final long ME = 7;

    private final CommunityNotificationRepository community = mock(CommunityNotificationRepository.class);
    private final StaffQueueApi staff = mock(StaffQueueApi.class);
    private final LearningNotificationApi learning = mock(LearningNotificationApi.class);
    private final AuthApi auth = mock(AuthApi.class);
    private NotificationService service;

    @BeforeEach
    void setUp() {
        when(auth.currentUser()).thenReturn(new CurrentUser(ME, Set.of(RoleCode.LEARNER), true));
        when(auth.notificationsSeenAt(ME)).thenReturn(Optional.empty());
        service = new NotificationService(community, staff, learning, auth, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void summaryCountsBothSourcesAfterTheSeenMarkerWithinNinetyDays() {
        Instant seen = NOW.minusSeconds(3600);
        when(auth.notificationsSeenAt(ME)).thenReturn(Optional.of(seen));
        when(community.countAfter(ME, SINCE, seen)).thenReturn(3L);
        when(learning.countAfter(ME, SINCE, seen)).thenReturn(2L);

        var summary = service.summary();

        assertThat(summary.unread()).isEqualTo(5);
        assertThat(summary.tasks()).isEmpty();
        verify(community, never()).moderatorQueues();
        verify(staff, never()).teacherQueues(anyLong(), any());
    }

    @Test
    void neverOpenedBellCountsEverythingInTheWindow() {
        when(community.countAfter(ME, SINCE, null)).thenReturn(4L);
        when(learning.countAfter(ME, SINCE, null)).thenReturn(0L);
        assertThat(service.summary().unread()).isEqualTo(4);
    }

    @Test
    void staffSeeOnlyTheirOwnNonEmptyQueuesInAStableOrder() {
        when(auth.currentUser()).thenReturn(new CurrentUser(ME, Set.of(RoleCode.MODERATOR, RoleCode.TEACHER), true));
        Map<String, Long> moderator = new LinkedHashMap<>();
        moderator.put("PENDING_POSTS", 2L);
        moderator.put("PENDING_REPORTS", 0L);
        when(community.moderatorQueues()).thenReturn(moderator);
        Map<String, Long> teacher = new LinkedHashMap<>();
        teacher.put("GRADING_DUE_SOON", 1L);
        teacher.put("GRADING_OPEN", 0L);
        when(staff.teacherQueues(ME, NOW)).thenReturn(teacher);

        assertThat(service.summary().tasks()).containsExactly(new Task("PENDING_POSTS", 2), new Task("GRADING_DUE_SOON", 1));
    }

    @Test
    void listMergesBothSourcesNewestFirstMarksUnreadAfterTheSeenMarkerAndKeepsFifty() {
        Instant seen = NOW.minusSeconds(100);
        when(auth.notificationsSeenAt(ME)).thenReturn(Optional.of(seen));
        List<CommunityNotificationRepository.Item> communityItems = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            // Even items are newer than the seen marker, odd items older.
            communityItems.add(new CommunityNotificationRepository.Item("c" + i, "COMMENT", NOW.minusSeconds(i * 10L),
                    i % 2 == 0 ? 8L : null, 1, 42L, "Bài", 5L, "Hay"));
        }
        when(community.recent(ME, SINCE, NotificationService.PAGE_SIZE)).thenReturn(communityItems);
        List<LearningNotificationApi.Item> learningItems = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            learningItems.add(new LearningNotificationApi.Item("l" + i, "GRADED", NOW.minusSeconds(i * 10L + 5), 87, 300L, null, null));
        }
        when(learning.recent(ME, SINCE, NotificationService.PAGE_SIZE)).thenReturn(learningItems);
        when(auth.displayNames(Set.of(8L))).thenReturn(Map.of(8L, "An"));

        var page = service.list();

        assertThat(page.items()).hasSize(NotificationService.PAGE_SIZE);
        assertThat(page.items()).extracting(Notification::at).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(page.items()).allSatisfy(item -> assertThat(item.unread()).isEqualTo(item.at().isAfter(seen)));
        Notification first = page.items().get(0);
        assertThat(first.key()).isEqualTo("c0");
        assertThat(first.actorName()).isEqualTo("An");
        Notification graded = page.items().get(1);
        assertThat(graded.key()).isEqualTo("l0");
        assertThat(graded.total()).isEqualTo(87);
        assertThat(graded.postId()).isEqualTo(300L);
        assertThat(graded.actorId()).isNull();
        verify(auth).displayNames(Set.of(8L));
    }

    @Test
    void listWithoutActorsDoesNotLookUpNamesAndEverythingIsUnreadBeforeTheFirstOpen() {
        when(community.recent(ME, SINCE, NotificationService.PAGE_SIZE)).thenReturn(List.of(
                new CommunityNotificationRepository.Item("post:1:APPROVED", "POST_APPROVED", NOW.minusSeconds(5), null, 1, 1L,
                        "Bài", null, null)));
        when(learning.recent(ME, SINCE, NotificationService.PAGE_SIZE)).thenReturn(List.of());

        var page = service.list();

        assertThat(page.items()).singleElement().satisfies(item -> assertThat(item.unread()).isTrue());
        verify(auth, never()).displayNames(any());
    }

    @Test
    void markSeenUsesTheServerClock() {
        service.markSeen();
        verify(auth).markNotificationsSeen(ME, NOW);
    }
}
