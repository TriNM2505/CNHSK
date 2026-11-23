package com.cnhsk.community.post;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.community.common.PostStatus;
import com.cnhsk.community.contest.Contest;
import com.cnhsk.community.contest.ContestRepository;
import com.cnhsk.community.media.MediaService;
import com.cnhsk.community.media.PostMediaRepository;
import com.cnhsk.community.media.web.MediaItem;
import com.cnhsk.community.post.web.ModeratorPostWrite;
import com.cnhsk.community.post.web.OwnPost;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Posts written by moderators: published straight away (nobody reviews them), optionally linked to a published contest
 * and pinned above the feed.
 */
@Service
public class ModeratorPostService {
    private final AuthApi authApi;
    private final PostRepository posts;
    private final ContestRepository contests;
    private final Clock clock;
    private final MediaService media;
    private final PostMediaRepository mediaRows;

    public ModeratorPostService(AuthApi authApi, PostRepository posts, ContestRepository contests, Clock clock,
            MediaService media, PostMediaRepository mediaRows) {
        this.authApi = authApi;
        this.posts = posts;
        this.contests = contests;
        this.clock = clock;
        this.media = media;
        this.mediaRows = mediaRows;
    }

    @Transactional
    public OwnPost create(ModeratorPostWrite request) {
        long moderatorId = moderatorId();
        Instant now = clock.instant();
        Instant pinnedUntil = request.pinnedUntil();
        if (request.contestId() != null) {
            Contest contest = contests.findPublished(request.contestId())
                    .orElseThrow(() -> invalid("contestId", "Chỉ gắn được cuộc thi đã công bố."));
            if (pinnedUntil == null) pinnedUntil = contest.getEndsAt();
        }
        if (request.commentsCloseAt() != null) {
            requireFuture(request.commentsCloseAt(), now);
            // A comment contest stays on top until it closes unless another moment was chosen.
            if (pinnedUntil == null) pinnedUntil = request.commentsCloseAt();
        }
        requireFuture(pinnedUntil, now);
        List<MediaItem> attached = media.validate(request.media());

        Post post = new Post(moderatorId, request.category(), request.title(), request.content(), now);
        post.setStatus(PostStatus.APPROVED);
        post.setPublishedAt(now);
        post.setContestId(request.contestId());
        post.setPinnedUntil(pinnedUntil);
        post.setCommentsCloseAt(request.commentsCloseAt());
        Post saved = posts.save(post);
        mediaRows.replace(saved.getId(), attached);
        return PostService.toOwnPost(saved, attached);
    }

    /** Pins any approved post until a future moment, or unpins it with {@code null}. */
    @Transactional
    public void pin(long id, Instant pinnedUntil) {
        moderatorId();
        requireFuture(pinnedUntil, clock.instant());
        Post post = posts.findByIdForUpdate(id)
                .filter(found -> found.getStatus() == PostStatus.APPROVED)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Không tìm thấy bài viết.", Map.of()));
        post.setPinnedUntil(pinnedUntil);
        post.setUpdatedAt(clock.instant());
        posts.save(post);
    }

    private static void requireFuture(Instant pinnedUntil, Instant now) {
        if (pinnedUntil != null && !pinnedUntil.isAfter(now)) {
            throw invalid("pinnedUntil", "Thời điểm bỏ ghim phải ở tương lai.");
        }
    }

    private long moderatorId() {
        CurrentUser user = authApi.currentUser();
        if (!user.roles().contains(RoleCode.MODERATOR)) {
            throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "Bạn không có quyền thực hiện thao tác này.", Map.of());
        }
        return user.userId();
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Dữ liệu không hợp lệ.",
                Map.of("details", List.of(Map.of("field", field, "message", message))));
    }
}
