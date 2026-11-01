package com.cnhsk.community.post;

import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.community.common.CommunityLimits;
import com.cnhsk.community.common.PostStatus;
import com.cnhsk.community.post.web.OwnPost;
import com.cnhsk.community.post.web.PostWrite;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates and updates owner-authored posts. */
@Service
public class PostService {

    private static final String CREATE_QUOTA_LOCK_SQL =
            "SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))";

    private final AuthApi authApi;
    private final PostRepository posts;
    private final CommunityLimits limits;
    private final NamedParameterJdbcTemplate jdbc;

    public PostService(AuthApi authApi, PostRepository posts, CommunityLimits limits,
            NamedParameterJdbcTemplate jdbc) {
        this.authApi = authApi;
        this.posts = posts;
        this.limits = limits;
        this.jdbc = jdbc;
    }

    @Transactional
    public OwnPost create(PostWrite request) {
        CurrentUser user = authApi.currentUser();
        requireLearner(user);
        long authorId = user.userId();
        authApi.requireVerifiedEmail(authorId);

        jdbc.query(CREATE_QUOTA_LOCK_SQL,
                Map.of("key", "community:post-quota:" + authorId), result -> null);

        CommunityLimits.Snapshot snapshot = limits.snapshot();
        long createdToday = posts.countByAuthorIdAndCreatedAtGreaterThanEqual(authorId, snapshot.dayStart());
        limits.assertCanCreatePost(createdToday, snapshot);

        Post post = new Post(authorId, request.category(), request.title(), request.content(), snapshot.now());
        post.setStatus(request.submit() ? PostStatus.PENDING : PostStatus.DRAFT);
        Post saved = posts.save(post);
        return toOwnPost(saved);
    }

    @Transactional
    public OwnPost update(long id, PostWrite request) {
        CurrentUser user = authApi.currentUser();
        requireLearner(user);
        long authorId = user.userId();
        authApi.requireVerifiedEmail(authorId);

        Post post = posts.findByIdForUpdate(id)
                .orElseThrow(() -> forbidden());
        if (post.getAuthorId() != authorId) {
            throw forbidden();
        }
        if (post.getStatus() == PostStatus.HIDDEN) {
            throw new ApiException(ErrorCode.POST_NOT_EDITABLE, HttpStatus.CONFLICT,
                    "Bài viết này không thể chỉnh sửa.", Map.of());
        }

        CommunityLimits.Snapshot snapshot = limits.snapshot();
        post.setTitle(request.title());
        post.setContent(request.content());
        post.setCategory(request.category());
        post.setStatus(request.submit() ? PostStatus.PENDING : PostStatus.DRAFT);
        post.setUpdatedAt(snapshot.now());
        return toOwnPost(posts.save(post));
    }

    private static ApiException forbidden() {
        return new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
                "Bạn không có quyền thực hiện thao tác này.", Map.of());
    }

    private static void requireLearner(CurrentUser user) {
        if (!user.roles().contains(RoleCode.LEARNER)) {
            throw forbidden();
        }
    }

    private static OwnPost toOwnPost(Post post) {
        String reviewReason = post.getStatus() == PostStatus.REJECTED || post.getStatus() == PostStatus.HIDDEN
                ? post.getReviewReason() : null;
        return new OwnPost(post.getId(), post.getTitle(), post.getContent(), post.getCategory(), post.getStatus(),
                reviewReason, post.getPublishedAt(), post.getCreatedAt(), post.getUpdatedAt());
    }
}
