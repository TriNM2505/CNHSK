package com.cnhsk.community.comment;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.community.comment.web.CommentResponse;
import com.cnhsk.community.comment.web.CommentWrite;
import com.cnhsk.community.common.CommunityAuthors;
import com.cnhsk.community.common.CommunityLimits;
import com.cnhsk.community.common.CommunityProperties;
import com.cnhsk.community.common.CommentStatus;
import com.cnhsk.community.common.PostStatus;
import com.cnhsk.community.common.UserRef;
import com.cnhsk.community.post.Post;
import com.cnhsk.community.post.PostRepository;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Creates comments and replies under one author quota and a locked published post. */
@Service
public class CommentService {

    private static final String QUOTA_LOCK_SQL =
            "SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))";

    private final AuthApi authApi;
    private final CommentRepository comments;
    private final PostRepository posts;
    private final CommunityLimits limits;
    private final CommunityAuthors authors;
    private final NamedParameterJdbcTemplate jdbc;
    private final CommunityProperties properties;

    public CommentService(AuthApi authApi, CommentRepository comments, PostRepository posts,
            CommunityLimits limits, CommunityAuthors authors, NamedParameterJdbcTemplate jdbc,
            CommunityProperties properties) {
        this.authApi = authApi;
        this.comments = comments;
        this.posts = posts;
        this.limits = limits;
        this.authors = authors;
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CommentResponse create(long postId, CommentWrite request) {
        long authorId = verifiedLearnerId();
        lockQuota(authorId);
        Post post = lockPublishedPost(postId, ErrorCode.POST_NOT_FOUND);
        return insert(post.getId(), null, (short) 0, authorId, request.content());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CommentResponse reply(long parentId, CommentWrite request) {
        long authorId = verifiedLearnerId();
        lockQuota(authorId);
        Comment parent = comments.findReplyParentOnPublishedPost(parentId)
                .orElseThrow(() -> notFound(ErrorCode.COMMENT_NOT_FOUND));
        Post post = lockPublishedPost(parent.getPostId(), ErrorCode.COMMENT_NOT_FOUND);
        Long attachment = parent.getDepth() < 3 ? parent.getId() : parent.getParentId();
        short depth = (short) Math.min(3, parent.getDepth() + 1);
        return insert(post.getId(), attachment, depth, authorId, request.content());
    }

    /** Checks write prerequisites before MVC binds a mutation's body or identifier. */
    public void authorizeMutation() {
        verifiedLearnerId();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CommentResponse update(long id, CommentWrite request) {
        long authorId = verifiedLearnerId();
        Comment comment = lockOwnedComment(id, authorId);
        if (comment.getStatus() != CommentStatus.VISIBLE) {
            throw new ApiException(ErrorCode.COMMENT_NOT_EDITABLE, HttpStatus.CONFLICT,
                    "Bình luận không thể chỉnh sửa.", Map.of());
        }
        CommunityLimits.Snapshot snapshot = limits.snapshot();
        if (snapshot.now().isAfter(comment.getCreatedAt().plus(properties.getCommentEditWindow()))) {
            throw new ApiException(ErrorCode.COMMENT_EDIT_WINDOW_EXPIRED, HttpStatus.CONFLICT,
                    "Đã hết thời hạn chỉnh sửa bình luận.", Map.of());
        }
        comment.setContent(request.content());
        comment.setUpdatedAt(snapshot.now().truncatedTo(ChronoUnit.MICROS));
        UserRef author = authors.resolve(Set.of(authorId)).get(authorId);
        return new CommentResponse(comment.getId(), comment.getPostId(), comment.getParentId(), comment.getDepth(),
                comment.getStatus(), comment.getContent(), author, comment.getCreatedAt(),
                comment.getUpdatedAt().isAfter(comment.getCreatedAt()) ? comment.getUpdatedAt() : null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(long id) {
        long authorId = verifiedLearnerId();
        Comment comment = lockOwnedComment(id, authorId);
        if (comment.getStatus() != CommentStatus.VISIBLE) {
            return;
        }
        CommunityLimits.Snapshot snapshot = limits.snapshot();
        comment.setStatus(CommentStatus.DELETED);
        comment.setModeratedBy(authorId);
        comment.setModerationReason("AUTHOR_DELETED");
        comment.setUpdatedAt(snapshot.now().truncatedTo(ChronoUnit.MICROS));
    }

    private Comment lockOwnedComment(long id, long authorId) {
        long postId = comments.findOwnedPostId(id, authorId).orElseThrow(CommentService::forbidden);
        posts.findByIdForUpdate(postId).orElseThrow(CommentService::forbidden);
        return comments.findOwnedForUpdate(id, authorId, postId).orElseThrow(CommentService::forbidden);
    }

    private static ApiException forbidden() {
        return new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
                "Bạn không có quyền thực hiện thao tác này.", Map.of());
    }

    private long verifiedLearnerId() {
        CurrentUser user = authApi.currentUser();
        if (!user.roles().contains(RoleCode.LEARNER)) {
            throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "Bạn không có quyền thực hiện thao tác này.", Map.of());
        }
        authApi.requireVerifiedEmail(user.userId());
        return user.userId();
    }

    private void lockQuota(long authorId) {
        jdbc.query(QUOTA_LOCK_SQL, Map.of("key", "community:comment-quota:" + authorId), result -> null);
    }

    private Post lockPublishedPost(long postId, ErrorCode missingCode) {
        Post post = posts.findByIdForUpdate(postId).orElseThrow(() -> notFound(missingCode));
        if (post.getStatus() != PostStatus.APPROVED) {
            throw notFound(missingCode);
        }
        return post;
    }

    private CommentResponse insert(long postId, Long parentId, short depth, long authorId, String content) {
        CommunityLimits.Snapshot snapshot = limits.snapshot();
        long count = comments.countByAuthorIdAndCreatedAtAfter(authorId, snapshot.commentCutoff());
        Instant oldest = count == 0 ? null
                : comments.findOldestCreatedAtByAuthorIdAndCreatedAtAfter(authorId, snapshot.commentCutoff());
        limits.assertCanComment(count, oldest, snapshot);

        Comment saved = comments.save(new Comment(postId, parentId, authorId, content, depth, snapshot.now()));
        UserRef author = authors.resolve(Set.of(authorId)).get(authorId);
        return new CommentResponse(saved.getId(), saved.getPostId(), saved.getParentId(), saved.getDepth(),
                saved.getStatus(), saved.getContent(), author, saved.getCreatedAt(), null);
    }

    private static ApiException notFound(ErrorCode code) {
        String message = code == ErrorCode.POST_NOT_FOUND ? "Không tìm thấy bài viết." : "Không tìm thấy bình luận.";
        return new ApiException(code, HttpStatus.NOT_FOUND, message, Map.of());
    }
}
