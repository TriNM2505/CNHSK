package com.cnhsk.community.common;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;

import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Evaluates caller-supplied counts against one consistent set of time boundaries. */
@Component
public class CommunityLimits {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Duration COMMENT_WINDOW = Duration.ofMinutes(60);

    private final Clock clock;
    private final CommunityProperties properties;

    public CommunityLimits(Clock clock, CommunityProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    public Snapshot snapshot() {
        return new Snapshot(clock.instant());
    }

    public void assertCanCreatePost(long count, Snapshot snapshot) {
        validateCount(count);
        Objects.requireNonNull(snapshot, "snapshot");
        if (count >= properties.getPostsPerDay()) {
            throw quotaExceeded("Bạn đã đạt giới hạn tạo bài viết trong ngày.",
                    snapshot.now(), snapshot.nextDayStart());
        }
    }

    public void assertCanReport(long count, Snapshot snapshot) {
        validateCount(count);
        Objects.requireNonNull(snapshot, "snapshot");
        if (count >= properties.getReportsPerDay()) {
            throw quotaExceeded("Bạn đã đạt giới hạn báo cáo trong ngày.",
                    snapshot.now(), snapshot.nextDayStart());
        }
    }

    public void assertCanComment(long count, Instant oldestCreatedAt, Snapshot snapshot) {
        validateCount(count);
        Objects.requireNonNull(snapshot, "snapshot");
        if (count >= properties.getCommentsPerHour()) {
            if (oldestCreatedAt == null || !oldestCreatedAt.isAfter(snapshot.commentCutoff())
                    || oldestCreatedAt.isAfter(snapshot.now())) {
                throw new IllegalArgumentException("oldestCreatedAt must be within the active comment window");
            }
            throw quotaExceeded("Bạn đã đạt giới hạn bình luận trong 60 phút.",
                    snapshot.now(), oldestCreatedAt.plus(COMMENT_WINDOW));
        }
    }

    private static void validateCount(long count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
    }

    private static ApiException quotaExceeded(String message, Instant now, Instant retryAt) {
        Duration remaining = Duration.between(now, retryAt);
        long seconds = remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1);
        return new ApiException(ErrorCode.TOO_MANY_REQUESTS, HttpStatus.TOO_MANY_REQUESTS, message,
                Map.of("retryAfterSeconds", Math.max(1L, seconds)));
    }

    public static final class Snapshot {

        private final Instant now;
        private final Instant dayStart;
        private final Instant nextDayStart;
        private final Instant commentCutoff;

        private Snapshot(Instant now) {
            this.now = Objects.requireNonNull(now, "now");
            LocalDate day = now.atZone(VIETNAM_ZONE).toLocalDate();
            this.dayStart = day.atStartOfDay(VIETNAM_ZONE).toInstant();
            this.nextDayStart = day.plusDays(1).atStartOfDay(VIETNAM_ZONE).toInstant();
            this.commentCutoff = now.minus(COMMENT_WINDOW);
        }

        public Instant now() {
            return now;
        }

        public Instant dayStart() {
            return dayStart;
        }

        public Instant nextDayStart() {
            return nextDayStart;
        }

        public Instant commentCutoff() {
            return commentCutoff;
        }
    }
}
