package com.cnhsk.community.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.community.comment.Comment;
import com.cnhsk.community.comment.CommentRepository;
import com.cnhsk.community.common.CaseStatus;
import com.cnhsk.community.common.CommentStatus;
import com.cnhsk.community.common.CommunityAuthors;
import com.cnhsk.community.common.PostCategory;
import com.cnhsk.community.common.PostStatus;
import com.cnhsk.community.common.ReportReason;
import com.cnhsk.community.common.TargetType;
import com.cnhsk.community.common.UserRef;
import com.cnhsk.community.moderation.web.ReportDecision;
import com.cnhsk.community.moderation.web.ReportDecision.CommentAction;
import com.cnhsk.community.moderation.web.ReportDecision.Decision;
import com.cnhsk.community.moderation.web.ReportWrite.ReportTargetType;
import com.cnhsk.community.moderation.web.ReportedComment;
import com.cnhsk.community.moderation.web.ReportedPost;
import com.cnhsk.community.post.Post;
import com.cnhsk.community.post.PostRepository;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

class ReportCaseServiceTest {
    private static final long CASE = 17L;
    private static final long TARGET = 81L;
    private static final long PARENT = 91L;
    private static final long MODERATOR = 42L;
    private static final long AUTHOR = 77L;
    private static final Instant ORIGINAL = Instant.parse("2026-10-01T01:02:03Z");
    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00.123456789Z");
    private static final Instant STORED_NOW = NOW.truncatedTo(ChronoUnit.MICROS);
    private ModerationCaseRepository cases;
    private PostRepository posts;
    private CommentRepository comments;
    private AuthApi auth;
    private CommunityAuthors authors;
    private Clock clock;
    private AuditService audit;
    private ReportCaseService service;

    @BeforeEach
    void setUp() {
        cases = mock(ModerationCaseRepository.class);
        posts = mock(PostRepository.class);
        comments = mock(CommentRepository.class);
        auth = mock(AuthApi.class);
        authors = mock(CommunityAuthors.class);
        clock = mock(Clock.class);
        audit = mock(AuditService.class);
        when(auth.currentUser()).thenReturn(new CurrentUser(MODERATOR, Set.of(RoleCode.MODERATOR), false));
        when(clock.instant()).thenReturn(NOW);
        when(authors.resolve(anySet())).thenReturn(Map.of());
        service = new ReportCaseService(cases, posts, comments, auth, authors, clock, audit);
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = "MODERATOR", mode = EnumSource.Mode.EXCLUDE)
    void everyOperationRejectsOtherRolesBeforeAnyDatabaseOrClockWork(RoleCode role) {
        when(auth.currentUser()).thenReturn(new CurrentUser(MODERATOR, Set.of(role), true));
        for (Runnable operation : operations()) assertError(operation, ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN);
        verifyNoInteractions(cases, posts, comments, authors, clock, audit);
        verify(auth, never()).requireVerifiedEmail(anyLong());
    }

    @Test
    void everyOperationPropagatesUnauthenticatedBeforeAnyWork() {
        when(auth.currentUser()).thenThrow(new ApiException(ErrorCode.UNAUTHENTICATED,
                HttpStatus.UNAUTHORIZED, "Synthetic authentication failure", Map.of()));
        for (Runnable operation : operations()) assertError(operation, ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(cases, posts, comments, authors, clock, audit);
    }

    @Test
    void unverifiedModeratorWithAdminRoleNeedsNeitherLearnerNorVerification() {
        when(auth.currentUser()).thenReturn(new CurrentUser(MODERATOR, Set.of(RoleCode.MODERATOR, RoleCode.ADMIN), false));
        assertThat(service.listPendingReportCases(0, 20).items()).isEmpty();
        verify(auth).currentUser();
        verifyNoMoreInteractions(auth);
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,51"})
    void invalidPaginationStopsBeforeCounting(int page, int size) {
        assertError(() -> service.listPendingReportCases(page, size), ErrorCode.VALIDATION_FAILED,
                HttpStatus.UNPROCESSABLE_ENTITY);
        verifyNoInteractions(cases, posts, comments, authors, clock, audit);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 99, Integer.MAX_VALUE})
    void emptyAndOutOfRangeQueueOnlyCountsWithoutOverflowOrTargetReads(int page) {
        when(cases.countPending()).thenReturn(page == 0 ? 0L : 30L);
        var result = service.listPendingReportCases(page, 50);
        assertThat(result.items()).isEmpty();
        assertThat(result.page()).isEqualTo(page);
        assertThat(result.size()).isEqualTo(50);
        assertThat(result.totalElements()).isEqualTo(page == 0 ? 0L : 30L);
        verify(cases).countPending();
        verifyNoMoreInteractions(cases);
        verifyNoInteractions(posts, comments, authors, clock, audit);
    }

    @Test
    void mixedQueueBatchesDistinctTargetsPreservesOrderAndUsesLastArrayReasonAndUnicodePreview() {
        ModerationCase first = reportCase(CASE, TargetType.COMMENT);
        first.setReasons(List.of(new ReportEntry(11L, ReportReason.SPAM, null, NOW),
                new ReportEntry(12L, ReportReason.OTHER, "Original report detail", ORIGINAL)));
        first.setReportCount(2);
        ModerationCase second = reportCase(CASE + 1, TargetType.POST);
        ModerationCase third = reportCase(CASE + 2, TargetType.COMMENT);
        Post post = post(TARGET, AUTHOR);
        post.setTitle("中".repeat(121));
        Comment comment = comment(AUTHOR);
        comment.setContent("😀".repeat(121));
        when(cases.countPending()).thenReturn(3L);
        when(cases.findPending(PageRequest.of(0, 20))).thenReturn(List.of(first, second, third));
        when(posts.findAllById(Set.of(TARGET))).thenReturn(List.of(post));
        when(comments.findAllById(Set.of(TARGET))).thenReturn(List.of(comment));

        var result = service.listPendingReportCases(0, 20);

        assertThat(result.items()).extracting(item -> item.id()).containsExactly(CASE, CASE + 1, CASE + 2);
        assertThat(result.items().get(0).targetType()).isEqualTo(ReportTargetType.COMMENT);
        assertThat(result.items().get(0).reportCount()).isEqualTo(2);
        assertThat(result.items().get(0).latestReason()).isEqualTo(ReportReason.OTHER);
        assertThat(result.items().get(0).targetPreview()).isEqualTo("😀".repeat(120));
        assertThat(result.items().get(1).targetPreview()).isEqualTo("中".repeat(120));
        assertThat(result.items().get(0).createdAt()).isEqualTo(ORIGINAL);
        var order = inOrder(auth, cases, posts, comments);
        order.verify(auth).currentUser();
        order.verify(cases).countPending();
        order.verify(cases).findPending(PageRequest.of(0, 20));
        order.verify(posts).findAllById(Set.of(TARGET));
        order.verify(comments).findAllById(Set.of(TARGET));
        verifyNoMoreInteractions(cases, posts, comments);
        verifyNoInteractions(authors, clock, audit);
    }

    @ParameterizedTest
    @EnumSource(ReportTargetType.class)
    void oneTypeQueueDoesNotQueryTheOtherTargetRepository(ReportTargetType type) {
        when(cases.countPending()).thenReturn(1L);
        when(cases.findPending(PageRequest.of(0, 20))).thenReturn(List.of(reportCase(CASE, TargetType.valueOf(type.name()))));
        if (type == ReportTargetType.POST) when(posts.findAllById(Set.of(TARGET))).thenReturn(List.of(post(TARGET, AUTHOR)));
        else when(comments.findAllById(Set.of(TARGET))).thenReturn(List.of(comment(AUTHOR)));
        assertThat(service.listPendingReportCases(0, 20).items()).hasSize(1);
        if (type == ReportTargetType.POST) verifyNoInteractions(comments); else verifyNoInteractions(posts);
        verifyNoInteractions(authors, clock, audit);
    }

    @ParameterizedTest
    @EnumSource(ReportTargetType.class)
    void anyMissingQueueTargetFailsTheWholePageWithoutInventingAPreview(ReportTargetType type) {
        when(cases.countPending()).thenReturn(1L);
        when(cases.findPending(PageRequest.of(0, 20))).thenReturn(List.of(reportCase(CASE, TargetType.valueOf(type.name()))));
        assertThatThrownBy(() -> service.listPendingReportCases(0, 20))
                .isInstanceOf(IllegalStateException.class).hasMessage("Report case target is unavailable");
        verifyNoInteractions(authors, clock, audit);
    }

    @ParameterizedTest
    @EnumSource(value = TargetType.class, names = {"POST", "COMMENT"}, mode = EnumSource.Mode.EXCLUDE)
    void unsupportedTypesFailClosedInQueueDetailAndDecisionBeforeTargetReads(TargetType type) {
        ModerationCase item = reportCase(CASE, type);
        when(cases.countPending()).thenReturn(1L);
        when(cases.findPending(PageRequest.of(0, 20))).thenReturn(List.of(item));
        when(cases.findById(CASE)).thenReturn(Optional.of(item));
        for (Runnable operation : operations()) {
            assertThatThrownBy(operation::run).isInstanceOf(IllegalStateException.class)
                    .hasMessage("Report case target is unavailable");
        }
        verifyNoInteractions(posts, comments, authors, clock, audit);
    }

    @ParameterizedTest
    @EnumSource(CaseStatus.class)
    void postDetailReadsAllCaseStatesAndBatchesTargetReporterAndHandlerIdsOnce(CaseStatus status) {
        ModerationCase item = reportCase(CASE, TargetType.POST);
        item.setStatus(status);
        item.setHandledBy(MODERATOR);
        item.setHandledAt(ORIGINAL.plusSeconds(2));
        item.setDecisionNote("Stored decision note");
        item.setReasons(List.of(new ReportEntry(11L, ReportReason.SPAM, null, ORIGINAL),
                new ReportEntry(AUTHOR, ReportReason.OTHER, "Original report detail", NOW)));
        item.setReportCount(2);
        when(cases.findById(CASE)).thenReturn(Optional.of(item));
        when(posts.findById(TARGET)).thenReturn(Optional.of(post(TARGET, AUTHOR)));
        when(authors.resolve(Set.of(AUTHOR, MODERATOR, 11L))).thenReturn(Map.of(AUTHOR, new UserRef(AUTHOR, "Author")));

        var result = service.getReportCase(CASE);

        assertThat(result.status()).isEqualTo(status);
        assertThat(result.reportCount()).isEqualTo(2);
        assertThat(result.handledBy()).isEqualTo(new UserRef(MODERATOR, null));
        assertThat(result.handledAt()).isEqualTo(ORIGINAL.plusSeconds(2));
        assertThat(result.decisionNote()).isEqualTo("Stored decision note");
        assertThat(result.target()).isEqualTo(new ReportedPost("POST", TARGET, "Original title", "Original content",
                PostCategory.QUESTION, PostStatus.APPROVED, new UserRef(AUTHOR, "Author")));
        assertThat(result.reports()).hasSize(2);
        assertThat(result.reports().get(0).reporter()).isEqualTo(new UserRef(11L, null));
        assertThat(result.reports().get(0).detail()).isNull();
        assertThat(result.reports().get(1).detail()).isEqualTo("Original report detail");
        assertThat(result.reports().get(1).reportedAt()).isEqualTo(NOW);
        verify(authors).resolve(Set.of(AUTHOR, MODERATOR, 11L));
        verifyNoInteractions(comments, clock, audit);
    }

    @Test
    void deletedCommentDetailRetainsRawContentAndParentTitleAndNullHandlingMetadata() {
        ModerationCase item = reportCase(CASE, TargetType.COMMENT);
        Comment comment = comment(AUTHOR);
        comment.setStatus(CommentStatus.DELETED);
        when(cases.findById(CASE)).thenReturn(Optional.of(item));
        when(comments.findById(TARGET)).thenReturn(Optional.of(comment));
        when(posts.findById(PARENT)).thenReturn(Optional.of(post(PARENT, 88L)));
        var result = service.getReportCase(CASE);
        assertThat(result.target()).isEqualTo(new ReportedComment("COMMENT", TARGET, "Original comment",
                CommentStatus.DELETED, new UserRef(AUTHOR, null), PARENT, "Original title"));
        assertThat(result.handledBy()).isNull();
        assertThat(result.handledAt()).isNull();
        assertThat(result.decisionNote()).isNull();
        verify(authors).resolve(Set.of(AUTHOR, 11L));
        verifyNoInteractions(clock, audit);
    }

    @Test
    void missingCaseReturnsCaseNotFoundBeforeAnyTargetClockOrAuditWork() {
        assertError(() -> service.getReportCase(CASE), ErrorCode.REPORT_CASE_NOT_FOUND, HttpStatus.NOT_FOUND);
        assertError(() -> service.decideReportCase(CASE, reject()), ErrorCode.REPORT_CASE_NOT_FOUND, HttpStatus.NOT_FOUND);
        verifyNoInteractions(posts, comments, authors, clock, audit);
    }

    @ParameterizedTest
    @EnumSource(ReportTargetType.class)
    void missingDetailAndDecisionTargetsFailBeforeSelfValidationOrWrites(ReportTargetType type) {
        when(cases.findById(CASE)).thenReturn(Optional.of(reportCase(CASE, TargetType.valueOf(type.name()))));
        assertThatThrownBy(() -> service.getReportCase(CASE)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.decideReportCase(CASE, reject())).isInstanceOf(IllegalStateException.class);
        verify(cases, never()).closePending(anyLong(), any(), anyLong(), any(), any());
        verifyNoInteractions(authors, clock, audit);
    }

    @Test
    void missingCommentParentIsAlsoAnOrphanWithoutInventedPostTitle() {
        when(cases.findById(CASE)).thenReturn(Optional.of(reportCase(CASE, TargetType.COMMENT)));
        when(comments.findById(TARGET)).thenReturn(Optional.of(comment(MODERATOR)));
        assertThatThrownBy(() -> service.getReportCase(CASE)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.decideReportCase(CASE, new ReportDecision(Decision.ACTION, "Valid note", null)))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(authors, clock, audit);
    }

    @Test
    void independentValidationPrecedesCaseLookupAndDoesNotConsultClock() {
        for (ReportDecision request : List.of(new ReportDecision(null, "Valid note", null),
                new ReportDecision(Decision.REJECT, null, null), new ReportDecision(Decision.REJECT, " \t ", null),
                new ReportDecision(Decision.REJECT, "😀".repeat(1001), null))) {
            assertError(() -> service.decideReportCase(CASE, request), ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        assertError(() -> service.decideReportCase(CASE, null), ErrorCode.VALIDATION_FAILED,
                HttpStatus.UNPROCESSABLE_ENTITY);
        verifyNoInteractions(cases, posts, comments, authors, clock, audit);
    }

    @ParameterizedTest
    @EnumSource(ReportTargetType.class)
    void selfModerationPrecedesContextSpecificCommentActionValidation(ReportTargetType type) {
        initial(type, MODERATOR).setStatus(CaseStatus.REJECTED);
        ReportDecision invalidContext = new ReportDecision(Decision.ACTION, "Valid note",
                type == ReportTargetType.POST ? CommentAction.HIDE : null);
        assertError(() -> service.decideReportCase(CASE, invalidContext), ErrorCode.SELF_MODERATION, HttpStatus.FORBIDDEN);
        verify(cases, never()).closePending(anyLong(), any(), anyLong(), any(), any());
        verifyNoInteractions(authors, clock, audit);
    }

    @Test
    void contextValidationRejectsMissingActionForCommentAndAnyActionForOtherContexts() {
        initial(ReportTargetType.COMMENT, AUTHOR).setStatus(CaseStatus.REJECTED);
        assertError(() -> service.decideReportCase(CASE, new ReportDecision(Decision.ACTION, "Valid note", null)),
                ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY);
        assertError(() -> service.decideReportCase(CASE, new ReportDecision(Decision.REJECT, "Valid note", CommentAction.DELETE)),
                ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY);
        initial(ReportTargetType.POST, AUTHOR).setStatus(CaseStatus.ACTIONED);
        assertError(() -> service.decideReportCase(CASE, new ReportDecision(Decision.ACTION, "Valid note", CommentAction.HIDE)),
                ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY);
        assertError(() -> service.decideReportCase(CASE, new ReportDecision(Decision.REJECT, "Valid note", CommentAction.HIDE)),
                ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY);
        verify(cases, never()).closePending(anyLong(), any(), anyLong(), any(), any());
        verifyNoInteractions(authors, clock, audit);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 1000})
    void noteBoundariesCountUnicodeCodePointsAfterStrippingInsteadOfUtf16Units(int codePoints) {
        initial(ReportTargetType.POST, AUTHOR);
        String note = "😀".repeat(codePoints);
        assertError(() -> service.decideReportCase(CASE,
                new ReportDecision(Decision.REJECT, "\u3000" + note + "\t", null)),
                ErrorCode.REPORT_ALREADY_HANDLED, HttpStatus.CONFLICT);
        verify(cases).closePending(CASE, "REJECTED", MODERATOR, STORED_NOW, note);
        verify(clock).instant();
        verifyNoInteractions(authors, audit);
    }

    @ParameterizedTest
    @EnumSource(ReportTargetType.class)
    void conditionalCaseLoserReturnsConflictWithoutTargetDmlAuditOrFreshRead(ReportTargetType type) {
        initial(type, AUTHOR);
        assertError(() -> service.decideReportCase(CASE, reject()), ErrorCode.REPORT_ALREADY_HANDLED, HttpStatus.CONFLICT);
        verify(cases).findById(CASE);
        verify(cases).closePending(CASE, "REJECTED", MODERATOR, STORED_NOW, "Valid note");
        verify(posts, never()).hideForReport(anyLong(), anyLong(), any(), any());
        verify(comments, never()).moderateForReport(anyLong(), any(), anyLong(), any());
        verifyNoInteractions(authors, audit);
    }

    @ParameterizedTest
    @EnumSource(ReportTargetType.class)
    void rejectionOnlyClosesCaseThenAuditsAndReloadsLatestReportEntries(ReportTargetType type) {
        ModerationCase original = initial(type, AUTHOR);
        ModerationCase fresh = fresh(CaseStatus.REJECTED, type);
        when(cases.findById(CASE)).thenReturn(Optional.of(original), Optional.of(fresh));
        when(cases.closePending(CASE, "REJECTED", MODERATOR, STORED_NOW, "Valid note")).thenReturn(1);

        var result = service.decideReportCase(CASE, new ReportDecision(Decision.REJECT, "\u3000Valid note\t", null));

        assertThat(result.status()).isEqualTo(CaseStatus.REJECTED);
        assertThat(result.reports()).hasSize(2);
        assertThat(result.reportCount()).isEqualTo(2);
        assertThat(result.handledAt()).isEqualTo(STORED_NOW);
        assertThat(original.getStatus()).isEqualTo(CaseStatus.PENDING);
        assertThat(original.getReportCount()).isEqualTo(1);
        verify(posts, never()).hideForReport(anyLong(), anyLong(), any(), any());
        verify(comments, never()).moderateForReport(anyLong(), any(), anyLong(), any());
        verify(cases, never()).save(any());
        var order = inOrder(auth, cases, clock, audit, authors);
        order.verify(auth).currentUser();
        order.verify(cases).findById(CASE);
        order.verify(clock).instant();
        order.verify(cases).closePending(CASE, "REJECTED", MODERATOR, STORED_NOW, "Valid note");
        order.verify(audit).recordDetail(MODERATOR, "COMMUNITY_REPORT_REJECT", "REPORT_CASE", CASE,
                Map.of("targetType", type.name(), "targetId", TARGET));
        order.verify(cases).findById(CASE);
        order.verify(authors).resolve(Set.of(AUTHOR, MODERATOR, 11L, 12L));
        verify(clock).instant();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void postActionClosesBeforeConditionalHideAndSharesOneClockSnapshotWithAuditAndFreshResponse(int affected) {
        ModerationCase original = initial(ReportTargetType.POST, AUTHOR);
        ModerationCase fresh = fresh(CaseStatus.ACTIONED, ReportTargetType.POST);
        Post updated = post(TARGET, AUTHOR);
        updated.setStatus(PostStatus.HIDDEN);
        when(cases.findById(CASE)).thenReturn(Optional.of(original), Optional.of(fresh));
        when(posts.findById(TARGET)).thenReturn(Optional.of(post(TARGET, AUTHOR)), Optional.of(updated));
        when(cases.closePending(CASE, "ACTIONED", MODERATOR, STORED_NOW, "Valid note")).thenReturn(1);
        when(posts.hideForReport(TARGET, MODERATOR, STORED_NOW, "Valid note")).thenReturn(affected);

        var result = service.decideReportCase(CASE, new ReportDecision(Decision.ACTION, "Valid note", null));

        assertThat(((ReportedPost) result.target()).status()).isEqualTo(PostStatus.HIDDEN);
        var order = inOrder(cases, posts, clock, audit);
        order.verify(cases).findById(CASE);
        order.verify(posts).findById(TARGET);
        order.verify(clock).instant();
        order.verify(cases).closePending(CASE, "ACTIONED", MODERATOR, STORED_NOW, "Valid note");
        order.verify(posts).hideForReport(TARGET, MODERATOR, STORED_NOW, "Valid note");
        order.verify(audit).recordDetail(MODERATOR, "COMMUNITY_REPORT_ACTION", "REPORT_CASE", CASE,
                Map.of("targetType", "POST", "targetId", TARGET));
        order.verify(cases).findById(CASE);
        order.verify(posts).findById(TARGET);
        verify(clock).instant();
        verify(cases, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(CommentAction.class)
    void commentActionsSendExactNativeStateAndAuditCommentActionWithoutChangingManagedEntities(CommentAction action) {
        ModerationCase original = initial(ReportTargetType.COMMENT, AUTHOR);
        ModerationCase fresh = fresh(CaseStatus.ACTIONED, ReportTargetType.COMMENT);
        Comment old = comment(AUTHOR);
        Comment updated = comment(AUTHOR);
        updated.setStatus(action == CommentAction.HIDE ? CommentStatus.HIDDEN : CommentStatus.DELETED);
        when(cases.findById(CASE)).thenReturn(Optional.of(original), Optional.of(fresh));
        when(comments.findById(TARGET)).thenReturn(Optional.of(old), Optional.of(updated));
        when(cases.closePending(CASE, "ACTIONED", MODERATOR, STORED_NOW, "Valid note")).thenReturn(1);
        when(comments.moderateForReport(TARGET, updated.getStatus().name(), MODERATOR, "Valid note")).thenReturn(1);

        var result = service.decideReportCase(CASE, new ReportDecision(Decision.ACTION, "Valid note", action));

        assertThat(((ReportedComment) result.target()).status()).isEqualTo(updated.getStatus());
        assertThat(old.getStatus()).isEqualTo(CommentStatus.VISIBLE);
        assertThat(old.getUpdatedAt()).isEqualTo(ORIGINAL);
        assertThat(old.getContent()).isEqualTo("Original comment");
        var order = inOrder(cases, comments, audit);
        order.verify(cases).findById(CASE);
        order.verify(comments).findById(TARGET);
        order.verify(cases).closePending(CASE, "ACTIONED", MODERATOR, STORED_NOW, "Valid note");
        order.verify(comments).moderateForReport(TARGET, updated.getStatus().name(), MODERATOR, "Valid note");
        order.verify(audit).recordDetail(MODERATOR, "COMMUNITY_REPORT_ACTION", "REPORT_CASE", CASE,
                Map.of("targetType", "COMMENT", "targetId", TARGET, "commentAction", action.name()));
        order.verify(cases).findById(CASE);
        order.verify(comments).findById(TARGET);
        verify(clock).instant();
        verify(comments, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({"HIDDEN,HIDE", "DELETED,HIDE", "DELETED,DELETE"})
    void conditionalCommentNoOpStillClosesAndAuditsWithoutOverwritingOldMetadata(CommentStatus state, CommentAction action) {
        ModerationCase original = initial(ReportTargetType.COMMENT, AUTHOR);
        Comment unchanged = comment(AUTHOR);
        unchanged.setStatus(state);
        unchanged.setModeratedBy(88L);
        unchanged.setModerationReason("Original moderation reason");
        when(comments.findById(TARGET)).thenReturn(Optional.of(unchanged));
        when(cases.findById(CASE)).thenReturn(Optional.of(original), Optional.of(fresh(CaseStatus.ACTIONED, ReportTargetType.COMMENT)));
        when(cases.closePending(CASE, "ACTIONED", MODERATOR, STORED_NOW, "Valid note")).thenReturn(1);

        assertThat(((ReportedComment) service.decideReportCase(CASE,
                new ReportDecision(Decision.ACTION, "Valid note", action)).target()).status()).isEqualTo(state);

        assertThat(unchanged.getModeratedBy()).isEqualTo(88L);
        assertThat(unchanged.getModerationReason()).isEqualTo("Original moderation reason");
        assertThat(unchanged.getUpdatedAt()).isEqualTo(ORIGINAL);
        verify(audit).recordDetail(MODERATOR, "COMMUNITY_REPORT_ACTION", "REPORT_CASE", CASE,
                Map.of("targetType", "COMMENT", "targetId", TARGET, "commentAction", action.name()));
    }

    @Test
    void alreadyHiddenPostStillClosesAndAuditsWithoutOverwritingExistingReviewMetadata() {
        ModerationCase original = initial(ReportTargetType.POST, AUTHOR);
        Post unchanged = post(TARGET, AUTHOR);
        unchanged.setStatus(PostStatus.HIDDEN);
        unchanged.setReviewedBy(88L);
        unchanged.setReviewedAt(ORIGINAL);
        unchanged.setReviewReason("Original review reason");
        when(posts.findById(TARGET)).thenReturn(Optional.of(unchanged));
        when(cases.findById(CASE)).thenReturn(Optional.of(original), Optional.of(fresh(CaseStatus.ACTIONED, ReportTargetType.POST)));
        when(cases.closePending(CASE, "ACTIONED", MODERATOR, STORED_NOW, "Valid note")).thenReturn(1);

        assertThat(((ReportedPost) service.decideReportCase(CASE,
                new ReportDecision(Decision.ACTION, "Valid note", null)).target()).status()).isEqualTo(PostStatus.HIDDEN);

        assertThat(unchanged.getReviewedBy()).isEqualTo(88L);
        assertThat(unchanged.getReviewedAt()).isEqualTo(ORIGINAL);
        assertThat(unchanged.getReviewReason()).isEqualTo("Original review reason");
        assertThat(unchanged.getPublishedAt()).isEqualTo(ORIGINAL);
        assertThat(unchanged.getUpdatedAt()).isEqualTo(ORIGINAL);
        verify(posts).hideForReport(TARGET, MODERATOR, STORED_NOW, "Valid note");
        verify(audit).recordDetail(MODERATOR, "COMMUNITY_REPORT_ACTION", "REPORT_CASE", CASE,
                Map.of("targetType", "POST", "targetId", TARGET));
    }

    @Test
    void zeroTargetUpdateFollowedByMissingFreshTargetFailsInsteadOfReturningPartialSuccess() {
        ModerationCase original = initial(ReportTargetType.POST, AUTHOR);
        when(cases.findById(CASE)).thenReturn(Optional.of(original), Optional.of(fresh(CaseStatus.ACTIONED, ReportTargetType.POST)));
        when(cases.closePending(CASE, "ACTIONED", MODERATOR, STORED_NOW, "Valid note")).thenReturn(1);
        when(posts.findById(TARGET)).thenReturn(Optional.of(post(TARGET, AUTHOR)), Optional.empty());
        assertThatThrownBy(() -> service.decideReportCase(CASE, new ReportDecision(Decision.ACTION, "Valid note", null)))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(authors);
    }

    @Test
    void targetDmlFailurePropagatesBeforeAuditAndFreshReload() {
        initial(ReportTargetType.POST, AUTHOR);
        when(cases.closePending(CASE, "ACTIONED", MODERATOR, STORED_NOW, "Valid note")).thenReturn(1);
        RuntimeException failure = new IllegalStateException("Synthetic target DML failure");
        doThrow(failure).when(posts).hideForReport(TARGET, MODERATOR, STORED_NOW, "Valid note");
        assertThatThrownBy(() -> service.decideReportCase(CASE, new ReportDecision(Decision.ACTION, "Valid note", null)))
                .isSameAs(failure);
        verify(cases).findById(CASE);
        verifyNoInteractions(audit, authors);
    }

    @Test
    void auditFailurePropagatesWithoutFreshReadOrSuccessResponse() {
        initial(ReportTargetType.POST, AUTHOR);
        when(cases.closePending(CASE, "REJECTED", MODERATOR, STORED_NOW, "Valid note")).thenReturn(1);
        RuntimeException failure = new IllegalStateException("Synthetic audit failure");
        doThrow(failure).when(audit).recordDetail(MODERATOR, "COMMUNITY_REPORT_REJECT", "REPORT_CASE", CASE,
                Map.of("targetType", "POST", "targetId", TARGET));
        assertThatThrownBy(() -> service.decideReportCase(CASE, reject())).isSameAs(failure);
        verify(cases).findById(CASE);
        verifyNoInteractions(authors);
    }

    @Test
    void readAndWriteTransactionsHaveExplicitRequiredContracts() throws Exception {
        for (String name : List.of("listPendingReportCases", "getReportCase")) {
            var method = name.equals("getReportCase") ? ReportCaseService.class.getMethod(name, long.class)
                    : ReportCaseService.class.getMethod(name, int.class, int.class);
            assertThat(method.getAnnotation(Transactional.class).readOnly()).isTrue();
        }
        Transactional mutation = ReportCaseService.class.getMethod("decideReportCase", long.class, ReportDecision.class)
                .getAnnotation(Transactional.class);
        assertThat(mutation.readOnly()).isFalse();
        assertThat(mutation.isolation()).isEqualTo(Isolation.READ_COMMITTED);
        assertThat(mutation.propagation()).isEqualTo(org.springframework.transaction.annotation.Propagation.REQUIRED);
    }

    private List<Runnable> operations() {
        return List.of(() -> service.listPendingReportCases(0, 20), () -> service.getReportCase(CASE),
                () -> service.decideReportCase(CASE, reject()));
    }

    private ModerationCase initial(ReportTargetType type, long author) {
        ModerationCase item = reportCase(CASE, TargetType.valueOf(type.name()));
        when(cases.findById(CASE)).thenReturn(Optional.of(item));
        if (type == ReportTargetType.POST) when(posts.findById(TARGET)).thenReturn(Optional.of(post(TARGET, author)));
        else {
            when(comments.findById(TARGET)).thenReturn(Optional.of(comment(author)));
            when(posts.findById(PARENT)).thenReturn(Optional.of(post(PARENT, 88L)));
        }
        return item;
    }

    private static ModerationCase fresh(CaseStatus status, ReportTargetType type) {
        ModerationCase item = reportCase(CASE, TargetType.valueOf(type.name()));
        item.setStatus(status);
        item.setHandledBy(MODERATOR);
        item.setHandledAt(STORED_NOW);
        item.setDecisionNote("Valid note");
        item.setReportCount(2);
        item.setReasons(List.of(new ReportEntry(11L, ReportReason.SPAM, null, ORIGINAL),
                new ReportEntry(12L, ReportReason.OTHER, "Appended report", NOW)));
        return item;
    }

    private static ModerationCase reportCase(long id, TargetType type) {
        ModerationCase item = new ModerationCase(type, TARGET, ORIGINAL);
        ReflectionTestUtils.setField(item, "id", id);
        item.setReasons(List.of(new ReportEntry(11L, ReportReason.SPAM, null, ORIGINAL)));
        return item;
    }

    private static Post post(long id, long author) {
        Post post = new Post(author, PostCategory.QUESTION, "Original title", "Original content", ORIGINAL);
        ReflectionTestUtils.setField(post, "id", id);
        post.setStatus(PostStatus.APPROVED);
        post.setPublishedAt(ORIGINAL);
        return post;
    }

    private static Comment comment(long author) {
        Comment comment = new Comment(PARENT, null, author, "Original comment", (short) 0, ORIGINAL);
        ReflectionTestUtils.setField(comment, "id", TARGET);
        return comment;
    }

    private static ReportDecision reject() {
        return new ReportDecision(Decision.REJECT, "Valid note", null);
    }

    private static void assertError(Runnable operation, ErrorCode code, HttpStatus status) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(code);
            assertThat(error.getStatus()).isEqualTo(status);
        });
    }
}
