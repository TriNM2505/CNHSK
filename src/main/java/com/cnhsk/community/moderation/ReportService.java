package com.cnhsk.community.moderation;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.community.comment.Comment;
import com.cnhsk.community.comment.CommentRepository;
import com.cnhsk.community.common.CommentStatus;
import com.cnhsk.community.common.CommunityLimits;
import com.cnhsk.community.common.ReactionKind;
import com.cnhsk.community.common.TargetType;
import com.cnhsk.community.moderation.web.ReportReceived;
import com.cnhsk.community.moderation.web.ReportWrite;
import com.cnhsk.community.post.Post;
import com.cnhsk.community.post.PostRepository;
import com.cnhsk.community.reaction.ReactionId;
import com.cnhsk.community.reaction.ReactionRepository;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    private static final String QUOTA_LOCK_SQL =
            "SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))";

    private final AuthApi auth;
    private final PostRepository posts;
    private final CommentRepository comments;
    private final ReactionRepository reactions;
    private final ModerationCaseRepository cases;
    private final CommunityLimits limits;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ReportService(AuthApi auth, PostRepository posts, CommentRepository comments,
            ReactionRepository reactions, ModerationCaseRepository cases, CommunityLimits limits,
            NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.auth = auth;
        this.posts = posts;
        this.comments = comments;
        this.reactions = reactions;
        this.cases = cases;
        this.limits = limits;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReportReceived report(ReportWrite request) {
        CurrentUser user = auth.currentUser();
        if (!user.roles().contains(RoleCode.LEARNER)) {
            throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "Bạn không có quyền thực hiện thao tác này.", Map.of());
        }
        long userId = user.userId();
        auth.requireVerifiedEmail(userId);
        requireEligibleTarget(request, userId);

        jdbc.query(QUOTA_LOCK_SQL, Map.of("key", "community:report-quota:" + userId), result -> null);
        CommunityLimits.Snapshot snapshot = limits.snapshot();
        TargetType targetType = TargetType.valueOf(request.targetType().name());
        if (reactions.existsById(new ReactionId(userId, targetType, request.targetId(), ReactionKind.REPORT))) {
            throw duplicate();
        }
        long count = reactions.countReportsInDay(userId, snapshot.dayStart(), snapshot.nextDayStart());
        limits.assertCanReport(count, snapshot);

        Instant reportedAt = snapshot.now().truncatedTo(ChronoUnit.MICROS);
        String entry = entryJson(new ReportEntry(userId, request.reason(), request.detail(), reportedAt));
        int inserted = reactions.insertReport(userId, targetType.name(), request.targetId(), reportedAt);
        if (inserted == 0) throw duplicate();
        if (inserted != 1 || cases.upsertPendingReport(targetType.name(), request.targetId(), entry, reportedAt) != 1) {
            throw new IllegalStateException("Report writes must each affect one row");
        }
        return new ReportReceived(true);
    }

    private void requireEligibleTarget(ReportWrite request, long userId) {
        long authorId;
        if (request.targetType() == ReportWrite.ReportTargetType.POST) {
            Post post = posts.findPublished(request.targetId(), null, PageRequest.of(0, 1)).stream()
                    .findFirst().orElseThrow(ReportService::notFound);
            authorId = post.getAuthorId();
        } else {
            Comment comment = comments.findReplyParentOnPublishedPost(request.targetId())
                    .filter(value -> value.getStatus() == CommentStatus.VISIBLE)
                    .orElseThrow(ReportService::notFound);
            authorId = comment.getAuthorId();
        }
        if (authorId == userId) {
            throw new ApiException(ErrorCode.SELF_REPORT, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Bạn không thể báo cáo nội dung của chính mình.", Map.of());
        }
    }

    private String entryJson(ReportEntry entry) {
        try {
            return mapper.writeValueAsString(List.of(entry));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize report entry");
        }
    }

    private static ApiException duplicate() {
        return new ApiException(ErrorCode.DUPLICATE_REPORT, HttpStatus.CONFLICT,
                "Bạn đã báo cáo nội dung này.", Map.of());
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.TARGET_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Không tìm thấy nội dung.", Map.of());
    }
}
