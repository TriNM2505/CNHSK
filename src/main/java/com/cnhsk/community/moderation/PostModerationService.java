package com.cnhsk.community.moderation;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.community.common.CommunityAuthors;
import com.cnhsk.community.common.PostExcerpt;
import com.cnhsk.community.common.UserRef;
import com.cnhsk.community.moderation.web.ModerationPost;
import com.cnhsk.community.moderation.web.ModerationPostPage;
import com.cnhsk.community.moderation.web.ModerationPostSummary;
import com.cnhsk.community.moderation.web.RejectWrite;
import com.cnhsk.community.post.Post;
import com.cnhsk.community.post.PostRepository;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostModerationService {
    private final PostRepository posts;
    private final AuthApi auth;
    private final CommunityAuthors authors;
    private final Clock clock;
    private final AuditService audit;

    public PostModerationService(PostRepository posts, AuthApi auth, CommunityAuthors authors,
            Clock clock, AuditService audit) {
        this.posts = posts;
        this.auth = auth;
        this.authors = authors;
        this.clock = clock;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public ModerationPostPage listPendingPosts(int page, int size) {
        moderatorId();
        if (page < 0 || size < 1 || size > 50) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Phân trang không hợp lệ.", Map.of("details", List.of(
                            Map.of("field", "page/size", "message", "Trang từ 0, kích thước từ 1 đến 50."))));
        }
        long total = posts.countPending();
        if ((long) page * size >= total) return new ModerationPostPage(List.of(), page, size, total);
        List<Post> found = posts.findPending(PageRequest.of(page, size));
        Map<Long, UserRef> resolved = authors.resolve(found.stream().map(Post::getAuthorId).collect(Collectors.toSet()));
        List<ModerationPostSummary> items = found.stream().map(post -> new ModerationPostSummary(
                post.getId(), post.getTitle(), post.getCategory(), PostExcerpt.fromContent(post.getContent()),
                userRef(resolved, post.getAuthorId()), post.getCreatedAt(), post.getUpdatedAt())).toList();
        return new ModerationPostPage(items, page, size, total);
    }

    @Transactional(readOnly = true)
    public ModerationPost getModerationPost(long id) {
        moderatorId();
        return detail(posts.findById(id).orElseThrow(PostModerationService::notFound));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ModerationPost approvePost(long id) {
        return decide(id, true, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ModerationPost rejectPost(long id, RejectWrite request) {
        return decide(id, false, request.reason());
    }

    private ModerationPost decide(long id, boolean approve, String reason) {
        long moderator = moderatorId();
        long author = posts.findModerationAuthorId(id).orElseThrow(PostModerationService::notFound);
        if (author == moderator) throw selfModeration();
        Instant reviewedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        int affected;
        try {
            affected = approve ? posts.approvePending(id, moderator, reviewedAt)
                    : posts.rejectPending(id, moderator, reason, reviewedAt);
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException constraint
                        && "ck_post_self".equals(constraint.getConstraintName())) throw selfModeration();
            }
            throw exception;
        }
        if (affected == 0) {
            throw new ApiException(ErrorCode.POST_NOT_PENDING, HttpStatus.CONFLICT,
                    "Bài viết không còn chờ duyệt.", Map.of());
        }
        if (affected != 1) throw new IllegalStateException("Post decision must affect one row");
        audit.recordDetail(moderator, approve ? "COMMUNITY_POST_APPROVE" : "COMMUNITY_POST_REJECT",
                "POST", id, approve ? Map.of() : Map.of("reason", reason));
        return detail(posts.findById(id).orElseThrow(PostModerationService::notFound));
    }

    private ModerationPost detail(Post post) {
        Set<Long> ids = new HashSet<>();
        ids.add(post.getAuthorId());
        if (post.getReviewedBy() != null) ids.add(post.getReviewedBy());
        Map<Long, UserRef> resolved = authors.resolve(ids);
        return new ModerationPost(post.getId(), post.getTitle(), post.getContent(), post.getCategory(),
                post.getStatus(), userRef(resolved, post.getAuthorId()), post.getReviewReason(),
                post.getReviewedBy() == null ? null : userRef(resolved, post.getReviewedBy()),
                post.getReviewedAt(), post.getPublishedAt(), post.getCreatedAt(), post.getUpdatedAt());
    }

    private long moderatorId() {
        CurrentUser user = auth.currentUser();
        if (!user.roles().contains(RoleCode.MODERATOR)) {
            throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "Bạn không có quyền thực hiện thao tác này.", Map.of());
        }
        return user.userId();
    }

    private static UserRef userRef(Map<Long, UserRef> resolved, long id) {
        return resolved.getOrDefault(id, new UserRef(id, null));
    }

    private static ApiException selfModeration() {
        return new ApiException(ErrorCode.SELF_MODERATION, HttpStatus.FORBIDDEN,
                "Bạn không thể kiểm duyệt bài viết của chính mình.", Map.of());
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.POST_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Không tìm thấy bài viết.", Map.of());
    }
}
