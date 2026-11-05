package com.cnhsk.community.moderation;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.community.comment.Comment;
import com.cnhsk.community.comment.CommentRepository;
import com.cnhsk.community.common.CommunityAuthors;
import com.cnhsk.community.common.TargetType;
import com.cnhsk.community.common.UserRef;
import com.cnhsk.community.moderation.web.ReportCaseDetail;
import com.cnhsk.community.moderation.web.ReportCasePage;
import com.cnhsk.community.moderation.web.ReportCaseSummary;
import com.cnhsk.community.moderation.web.ReportDecision;
import com.cnhsk.community.moderation.web.ReportEntryResponse;
import com.cnhsk.community.moderation.web.ReportWrite.ReportTargetType;
import com.cnhsk.community.moderation.web.ReportedComment;
import com.cnhsk.community.moderation.web.ReportedPost;
import com.cnhsk.community.post.Post;
import com.cnhsk.community.post.PostRepository;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportCaseService {
    private static final Logger log = LoggerFactory.getLogger(ReportCaseService.class);
    private final ModerationCaseRepository cases;
    private final PostRepository posts;
    private final CommentRepository comments;
    private final AuthApi auth;
    private final CommunityAuthors authors;
    private final Clock clock;
    private final AuditService audit;

    public ReportCaseService(ModerationCaseRepository cases, PostRepository posts, CommentRepository comments,
            AuthApi auth, CommunityAuthors authors, Clock clock, AuditService audit) {
        this.cases = cases;
        this.posts = posts;
        this.comments = comments;
        this.auth = auth;
        this.authors = authors;
        this.clock = clock;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public ReportCasePage listPendingReportCases(int page, int size) {
        moderatorId();
        if (page < 0 || size < 1 || size > 50) throw validation("page/size", "Phân trang không hợp lệ.");
        long total = cases.countPending();
        if ((long) page * size >= total) return new ReportCasePage(List.of(), page, size, total);
        List<ModerationCase> found = cases.findPending(PageRequest.of(page, size));
        Set<Long> postIds = new HashSet<>();
        Set<Long> commentIds = new HashSet<>();
        for (ModerationCase item : found) {
            requireSupportedType(item);
            (item.getTargetType() == TargetType.POST ? postIds : commentIds).add(item.getTargetId());
        }
        Map<Long, Post> postTargets = postIds.isEmpty() ? Map.of() : posts.findAllById(postIds).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
        Map<Long, Comment> commentTargets = commentIds.isEmpty() ? Map.of() : comments.findAllById(commentIds).stream()
                .collect(Collectors.toMap(Comment::getId, Function.identity()));
        List<ReportCaseSummary> items = found.stream().map(item -> {
            String preview;
            if (item.getTargetType() == TargetType.POST) {
                Post post = postTargets.get(item.getTargetId());
                if (post == null) throw unresolved(item);
                preview = post.getTitle();
            } else {
                Comment comment = commentTargets.get(item.getTargetId());
                if (comment == null) throw unresolved(item);
                preview = comment.getContent();
            }
            List<ReportEntry> entries = item.getReasons();
            if (entries.isEmpty()) throw unresolved(item);
            return new ReportCaseSummary(item.getId(), ReportTargetType.valueOf(item.getTargetType().name()),
                    item.getTargetId(), item.getReportCount(), entries.get(entries.size() - 1).reason(),
                    item.getCreatedAt(), preview.substring(0,
                            preview.offsetByCodePoints(0, Math.min(120, preview.codePointCount(0, preview.length())))));
        }).toList();
        return new ReportCasePage(items, page, size, total);
    }

    @Transactional(readOnly = true)
    public ReportCaseDetail getReportCase(long id) {
        moderatorId();
        return detail(findCase(id));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReportCaseDetail decideReportCase(long id, ReportDecision request) {
        long moderator = moderatorId();
        if (request == null) throw validation("request", "Dữ liệu yêu cầu không hợp lệ.");
        request.validateIndependent();
        ModerationCase item = findCase(id);
        Target target = target(item);
        if (target.authorId() == moderator) {
            throw new ApiException(ErrorCode.SELF_MODERATION, HttpStatus.FORBIDDEN,
                    "Bạn không thể kiểm duyệt nội dung của chính mình.", Map.of());
        }
        boolean action = request.decision() == ReportDecision.Decision.ACTION;
        boolean comment = item.getTargetType() == TargetType.COMMENT;
        if ((action && comment) != (request.commentAction() != null)) {
            throw validation("commentAction", "Thao tác bình luận không phù hợp với hồ sơ.");
        }
        Instant handledAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        int closed = cases.closePending(id, action ? "ACTIONED" : "REJECTED", moderator, handledAt, request.note());
        if (closed == 0) {
            throw new ApiException(ErrorCode.REPORT_ALREADY_HANDLED, HttpStatus.CONFLICT,
                    "Hồ sơ báo cáo đã được xử lý.", Map.of());
        }
        if (closed != 1) throw new IllegalStateException("Report decision must affect one case");
        if (action) {
            int affected = comment
                    ? comments.moderateForReport(item.getTargetId(),
                            request.commentAction() == ReportDecision.CommentAction.HIDE ? "HIDDEN" : "DELETED",
                            moderator, request.note())
                    : posts.hideForReport(item.getTargetId(), moderator, handledAt, request.note());
            if (affected < 0 || affected > 1) throw new IllegalStateException("Report decision affected invalid targets");
        }
        Map<String, Object> auditDetail = action && comment
                ? Map.of("targetType", item.getTargetType().name(), "targetId", item.getTargetId(),
                        "commentAction", request.commentAction().name())
                : Map.of("targetType", item.getTargetType().name(), "targetId", item.getTargetId());
        audit.recordDetail(moderator, action ? "COMMUNITY_REPORT_ACTION" : "COMMUNITY_REPORT_REJECT",
                "REPORT_CASE", id, auditDetail);
        return detail(findCase(id));
    }

    private ReportCaseDetail detail(ModerationCase item) {
        Target target = target(item);
        Set<Long> ids = new HashSet<>();
        ids.add(target.authorId());
        for (ReportEntry entry : item.getReasons()) ids.add(entry.userId());
        if (item.getHandledBy() != null) ids.add(item.getHandledBy());
        Map<Long, UserRef> resolved = authors.resolve(ids);
        ReportCaseDetail.Target content = target.post() != null
                ? new ReportedPost("POST", target.post().getId(), target.post().getTitle(), target.post().getContent(),
                        target.post().getCategory(), target.post().getStatus(), userRef(resolved, target.authorId()))
                : new ReportedComment("COMMENT", target.comment().getId(), target.comment().getContent(),
                        target.comment().getStatus(), userRef(resolved, target.authorId()),
                        target.comment().getPostId(), target.parent().getTitle());
        List<ReportEntryResponse> reports = item.getReasons().stream().map(entry -> new ReportEntryResponse(
                userRef(resolved, entry.userId()), entry.reason(), entry.detail(), entry.reportedAt())).toList();
        return new ReportCaseDetail(item.getId(), item.getStatus(), item.getReportCount(), content, reports,
                item.getHandledBy() == null ? null : userRef(resolved, item.getHandledBy()), item.getHandledAt(),
                item.getDecisionNote(), item.getCreatedAt());
    }

    private Target target(ModerationCase item) {
        requireSupportedType(item);
        if (item.getTargetType() == TargetType.POST) {
            return new Target(posts.findById(item.getTargetId()).orElseThrow(() -> unresolved(item)), null, null);
        }
        Comment comment = comments.findById(item.getTargetId()).orElseThrow(() -> unresolved(item));
        Post parent = posts.findById(comment.getPostId()).orElseThrow(() -> unresolved(item));
        return new Target(null, comment, parent);
    }

    private static void requireSupportedType(ModerationCase item) {
        if (item.getTargetType() != TargetType.POST && item.getTargetType() != TargetType.COMMENT) throw unresolved(item);
    }

    private static IllegalStateException unresolved(ModerationCase item) {
        log.error("Report case target unavailable caseId={} targetType={}", item.getId(), item.getTargetType());
        return new IllegalStateException("Report case target is unavailable");
    }

    private ModerationCase findCase(long id) {
        return cases.findById(id).orElseThrow(() -> new ApiException(ErrorCode.REPORT_CASE_NOT_FOUND,
                HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ báo cáo.", Map.of()));
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

    private static ApiException validation(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, message,
                Map.of("details", List.of(Map.of("field", field, "message", message))));
    }

    private record Target(Post post, Comment comment, Post parent) {
        long authorId() { return post == null ? comment.getAuthorId() : post.getAuthorId(); }
    }
}
