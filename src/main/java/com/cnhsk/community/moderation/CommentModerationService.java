package com.cnhsk.community.moderation;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.community.comment.Comment;
import com.cnhsk.community.comment.CommentRepository;
import com.cnhsk.community.common.CommentStatus;
import com.cnhsk.community.common.CommunityAuthors;
import com.cnhsk.community.common.UserRef;
import com.cnhsk.community.moderation.web.CommentModeration;
import com.cnhsk.community.moderation.web.ModerationComment;
import com.cnhsk.community.post.PostRepository;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentModerationService {
    private final CommentRepository comments;
    private final PostRepository posts;
    private final AuthApi auth;
    private final CommunityAuthors authors;
    private final AuditService audit;

    public CommentModerationService(CommentRepository comments, PostRepository posts, AuthApi auth,
            CommunityAuthors authors, AuditService audit) {
        this.comments = comments;
        this.posts = posts;
        this.auth = auth;
        this.authors = authors;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ModerationComment> listPostCommentsForModeration(long postId) {
        moderatorId();
        posts.findById(postId).orElseThrow(() -> notFound(ErrorCode.POST_NOT_FOUND));
        List<Comment> found = comments.findTreeByPostId(postId);
        if (found.isEmpty()) return List.of();
        Set<Long> ids = new HashSet<>();
        for (Comment comment : found) collectIdentities(comment, ids);
        Map<Long, UserRef> resolved = authors.resolve(ids);
        return found.stream().map(comment -> response(comment, resolved)).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ModerationComment moderateComment(long id, CommentModeration request) {
        long moderator = moderatorId();
        if (request == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Dữ liệu yêu cầu không hợp lệ.", Map.of("details", List.of(
                            Map.of("field", "request", "message", "Thao tác và lý do là bắt buộc."))));
        }
        request.validateIndependent();
        Comment comment = comments.findByIdForUpdate(id).orElseThrow(() -> notFound(ErrorCode.COMMENT_NOT_FOUND));
        if (comment.getAuthorId() == moderator) {
            throw new ApiException(ErrorCode.SELF_MODERATION, HttpStatus.FORBIDDEN,
                    "Bạn không thể kiểm duyệt bình luận của chính mình.", Map.of());
        }
        CommentStatus before = comment.getStatus();
        CommentStatus after = switch (request.action()) {
            case HIDE -> before == CommentStatus.VISIBLE ? CommentStatus.HIDDEN : before;
            case DELETE -> CommentStatus.DELETED;
            case RESTORE -> {
                if (before == CommentStatus.DELETED && "AUTHOR_DELETED".equals(comment.getModerationReason())
                        && Objects.equals(comment.getModeratedBy(), comment.getAuthorId())) {
                    throw new ApiException(ErrorCode.COMMENT_NOT_RESTORABLE, HttpStatus.CONFLICT,
                            "Không thể khôi phục bình luận do tác giả đã xoá.", Map.of());
                }
                yield CommentStatus.VISIBLE;
            }
        };
        if (after == before) return detail(comment);
        int affected = comments.moderateDirect(id, after.name(), moderator, request.reason());
        if (affected != 1) throw new IllegalStateException("Comment moderation must affect one locked row");
        audit.recordDetail(moderator, "COMMUNITY_COMMENT_" + request.action().name(), "COMMENT", id,
                Map.of("before", before.name(), "after", after.name(), "reason", request.reason()));
        return detail(comments.findById(id).orElseThrow(() -> notFound(ErrorCode.COMMENT_NOT_FOUND)));
    }

    private ModerationComment detail(Comment comment) {
        Set<Long> ids = new HashSet<>();
        collectIdentities(comment, ids);
        return response(comment, authors.resolve(ids));
    }

    private static void collectIdentities(Comment comment, Set<Long> ids) {
        ids.add(comment.getAuthorId());
        if (comment.getModeratedBy() != null) ids.add(comment.getModeratedBy());
    }

    private static ModerationComment response(Comment comment, Map<Long, UserRef> resolved) {
        return new ModerationComment(comment.getId(), comment.getPostId(), comment.getParentId(), comment.getDepth(),
                comment.getStatus(), comment.getContent(), userRef(resolved, comment.getAuthorId()),
                comment.getCreatedAt(), comment.getModeratedBy() == null ? null
                        : userRef(resolved, comment.getModeratedBy()), comment.getModerationReason());
    }

    private static UserRef userRef(Map<Long, UserRef> resolved, long id) {
        return resolved.getOrDefault(id, new UserRef(id, null));
    }

    private long moderatorId() {
        CurrentUser user = auth.currentUser();
        if (!user.roles().contains(RoleCode.MODERATOR)) {
            throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "Bạn không có quyền thực hiện thao tác này.", Map.of());
        }
        return user.userId();
    }

    private static ApiException notFound(ErrorCode code) {
        return new ApiException(code, HttpStatus.NOT_FOUND,
                code == ErrorCode.POST_NOT_FOUND ? "Không tìm thấy bài viết." : "Không tìm thấy bình luận.", Map.of());
    }
}
