package com.cnhsk.community.comment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.community.comment.web.CommentResponse;
import com.cnhsk.community.comment.web.CommentWrite;
import com.cnhsk.community.common.CommentStatus;
import com.cnhsk.community.common.CommunityAuthors;
import com.cnhsk.community.common.CommunityLimits;
import com.cnhsk.community.common.CommunityProperties;
import com.cnhsk.community.common.PostCategory;
import com.cnhsk.community.common.PostStatus;
import com.cnhsk.community.common.UserRef;
import com.cnhsk.community.post.Post;
import com.cnhsk.community.post.PostRepository;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CommentServiceTest {
    private static final long AUTHOR_ID = 42L;
    private static final long POST_ID = 70L;
    private static final Instant NOW = Instant.parse("2026-11-04T05:00:00.123456Z");
    private AuthApi auth;
    private CommentRepository comments;
    private PostRepository posts;
    private CommunityLimits limits;
    private CommunityAuthors authors;
    private NamedParameterJdbcTemplate jdbc;
    private CommentService service;
    private CommunityLimits.Snapshot snapshot;
    private CommunityProperties properties;

    @BeforeEach
    void setUp() {
        auth = mock(AuthApi.class);
        comments = mock(CommentRepository.class);
        posts = mock(PostRepository.class);
        limits = mock(CommunityLimits.class);
        authors = mock(CommunityAuthors.class);
        jdbc = mock(NamedParameterJdbcTemplate.class);
        properties = new CommunityProperties();
        service = new CommentService(auth, comments, posts, limits, authors, jdbc, properties);
        snapshot = new CommunityLimits(Clock.fixed(NOW, ZoneOffset.UTC), new CommunityProperties()).snapshot();
        when(auth.currentUser()).thenReturn(new CurrentUser(AUTHOR_ID, Set.of(RoleCode.LEARNER), true));
        when(limits.snapshot()).thenReturn(snapshot);
        when(posts.findByIdForUpdate(POST_ID)).thenReturn(Optional.of(post(PostStatus.APPROVED)));
        when(authors.resolve(Set.of(AUTHOR_ID))).thenReturn(Map.of(AUTHOR_ID, new UserRef(AUTHOR_ID, "Synthetic User")));
        when(comments.save(any(Comment.class))).thenAnswer(call -> {
            Comment comment = call.getArgument(0);
            ReflectionTestUtils.setField(comment, "id", 100L);
            return comment;
        });
    }

    @Test
    void rootCreationLocksQuotaThenPostAndUsesOneSnapshotForCountingAndPersistence() {
        when(comments.countByAuthorIdAndCreatedAtAfter(AUTHOR_ID, snapshot.commentCutoff())).thenReturn(7L);
        when(comments.findOldestCreatedAtByAuthorIdAndCreatedAtAfter(AUTHOR_ID, snapshot.commentCutoff()))
                .thenReturn(NOW.minusSeconds(30));
        CommentResponse result = service.create(POST_ID, new CommentWrite("  <img src=x onerror=alert(1)> **đậm**  "));

        assertThat(result.id()).isEqualTo(100L);
        assertThat(result.postId()).isEqualTo(POST_ID);
        assertThat(result.parentId()).isNull();
        assertThat(result.depth()).isZero();
        assertThat(result.status()).isEqualTo(CommentStatus.VISIBLE);
        assertThat(result.content()).isEqualTo("<img src=x onerror=alert(1)> **đậm**");
        assertThat(result.author()).isEqualTo(new UserRef(AUTHOR_ID, "Synthetic User"));
        assertThat(result.createdAt()).isEqualTo(NOW);
        assertThat(result.editedAt()).isNull();
        var order = inOrder(auth, jdbc, posts, limits, comments, authors);
        order.verify(auth).currentUser();
        order.verify(auth).requireVerifiedEmail(AUTHOR_ID);
        order.verify(jdbc).query(contains("pg_advisory_xact_lock"),
                eq(Map.of("key", "community:comment-quota:" + AUTHOR_ID)), any(ResultSetExtractor.class));
        order.verify(posts).findByIdForUpdate(POST_ID);
        order.verify(limits).snapshot();
        order.verify(comments).countByAuthorIdAndCreatedAtAfter(AUTHOR_ID, snapshot.commentCutoff());
        order.verify(comments).findOldestCreatedAtByAuthorIdAndCreatedAtAfter(AUTHOR_ID, snapshot.commentCutoff());
        order.verify(limits).assertCanComment(7, NOW.minusSeconds(30), snapshot);
        order.verify(comments).save(any(Comment.class));
        order.verify(authors).resolve(Set.of(AUTHOR_ID));
        verify(limits).snapshot();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void repliesUseParentPostAndCapDepthByCreatingASiblingAtTheFourthLevel(int depth) {
        Comment parent = parent(depth, CommentStatus.VISIBLE);
        when(comments.findReplyParentOnPublishedPost(90L)).thenReturn(Optional.of(parent));

        CommentResponse response = service.reply(90L, new CommentWrite(" Reply "));

        assertThat(response.postId()).isEqualTo(POST_ID);
        assertThat(response.parentId()).isEqualTo(depth == 3 ? 89L : 90L);
        assertThat(response.depth()).isEqualTo((short) Math.min(depth + 1, 3));
        assertThat(response.content()).isEqualTo("Reply");
        assertThat(response.editedAt()).isNull();
        verify(posts).findByIdForUpdate(POST_ID);
        verify(limits).assertCanComment(0, null, snapshot);
    }

    @ParameterizedTest
    @EnumSource(CommentStatus.class)
    void replyAcceptsEveryParentStatusAndPreservesAllParentFields(CommentStatus status) {
        Comment parent = parent(2, status);
        parent.setModeratedBy(99L);
        parent.setModerationReason("Private reason");
        parent.setUpdatedAt(NOW.minusSeconds(5));
        when(comments.findReplyParentOnPublishedPost(90L)).thenReturn(Optional.of(parent));

        CommentResponse result = service.reply(90L, new CommentWrite("Child"));

        assertThat(result.status()).isEqualTo(CommentStatus.VISIBLE);
        assertThat(result.content()).isEqualTo("Child");
        assertThat(parent.getPostId()).isEqualTo(POST_ID);
        assertThat(parent.getParentId()).isEqualTo(89L);
        assertThat(parent.getDepth()).isEqualTo((short) 2);
        assertThat(parent.getStatus()).isEqualTo(status);
        assertThat(parent.getContent()).isEqualTo("Private parent content");
        assertThat(parent.getModeratedBy()).isEqualTo(99L);
        assertThat(parent.getModerationReason()).isEqualTo("Private reason");
        assertThat(parent.getUpdatedAt()).isEqualTo(NOW.minusSeconds(5));
        verify(comments, never()).save(parent);
    }

    @Test
    void nonLearnerIsRejectedBeforeEmailChecksLocksOrTargetReads() {
        when(auth.currentUser()).thenReturn(new CurrentUser(AUTHOR_ID, Set.of(RoleCode.ADMIN), true));
        assertError(() -> service.create(POST_ID, new CommentWrite("Body")), ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN);
        assertError(() -> service.reply(90L, new CommentWrite("Body")), ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN);
        verify(auth, never()).requireVerifiedEmail(AUTHOR_ID);
        verifyNoInteractions(jdbc, posts, comments, limits, authors);
    }

    @Test
    void unverifiedLearnerIsRejectedBeforeAnyTargetLookupOrQuotaWork() {
        doThrow(new ApiException(ErrorCode.ACCOUNT_UNVERIFIED, HttpStatus.FORBIDDEN, "Verify email", Map.of()))
                .when(auth).requireVerifiedEmail(AUTHOR_ID);
        assertError(() -> service.create(POST_ID, new CommentWrite("Body")), ErrorCode.ACCOUNT_UNVERIFIED, HttpStatus.FORBIDDEN);
        assertError(() -> service.reply(90L, new CommentWrite("Body")), ErrorCode.ACCOUNT_UNVERIFIED, HttpStatus.FORBIDDEN);
        verifyNoInteractions(jdbc, posts, comments, limits, authors);
    }

    @ParameterizedTest
    @EnumSource(value = PostStatus.class, names = "APPROVED", mode = EnumSource.Mode.EXCLUDE)
    void rootCreationRejectsEveryNonpublicPostBeforeQuotaChecks(PostStatus status) {
        when(posts.findByIdForUpdate(POST_ID)).thenReturn(Optional.of(post(status)));
        assertError(() -> service.create(POST_ID, new CommentWrite("Body")), ErrorCode.POST_NOT_FOUND, HttpStatus.NOT_FOUND);
        verifyNoInteractions(limits, comments, authors);
    }

    @Test
    void missingPostAndMissingReplyParentAreNotFoundBeforeQuotaChecks() {
        when(posts.findByIdForUpdate(POST_ID)).thenReturn(Optional.empty());
        when(comments.findReplyParentOnPublishedPost(90L)).thenReturn(Optional.empty());
        assertError(() -> service.create(POST_ID, new CommentWrite("Body")), ErrorCode.POST_NOT_FOUND, HttpStatus.NOT_FOUND);
        assertError(() -> service.reply(90L, new CommentWrite("Body")), ErrorCode.COMMENT_NOT_FOUND, HttpStatus.NOT_FOUND);
        verifyNoInteractions(limits, authors);
        verify(comments, never()).countByAuthorIdAndCreatedAtAfter(anyLong(), any(Instant.class));
        verify(comments, never()).save(any(Comment.class));
    }

    @Test
    void replyRechecksLockedPostVisibilityBeforeQuotaOrInsertion() {
        when(comments.findReplyParentOnPublishedPost(90L)).thenReturn(Optional.of(parent(1, CommentStatus.VISIBLE)));
        when(posts.findByIdForUpdate(POST_ID)).thenReturn(Optional.of(post(PostStatus.PENDING)));
        assertError(() -> service.reply(90L, new CommentWrite("Body")), ErrorCode.COMMENT_NOT_FOUND, HttpStatus.NOT_FOUND);
        verifyNoInteractions(limits, authors);
        verify(comments, never()).save(any(Comment.class));
    }

    @Test
    void rootsAndRepliesShareCountOldestAndQuotaHelperWithoutInsertingWhenBlocked() {
        Instant oldest = snapshot.commentCutoff().plusSeconds(120);
        when(comments.countByAuthorIdAndCreatedAtAfter(AUTHOR_ID, snapshot.commentCutoff())).thenReturn(30L);
        when(comments.findOldestCreatedAtByAuthorIdAndCreatedAtAfter(AUTHOR_ID, snapshot.commentCutoff()))
                .thenReturn(oldest);
        doThrow(new ApiException(ErrorCode.TOO_MANY_REQUESTS, HttpStatus.TOO_MANY_REQUESTS,
                "Quota", Map.of("retryAfterSeconds", 120L))).when(limits).assertCanComment(30, oldest, snapshot);
        when(comments.findReplyParentOnPublishedPost(90L)).thenReturn(Optional.of(parent(1, CommentStatus.VISIBLE)));
        assertError(() -> service.create(POST_ID, new CommentWrite("Root")), ErrorCode.TOO_MANY_REQUESTS, HttpStatus.TOO_MANY_REQUESTS);
        assertError(() -> service.reply(90L, new CommentWrite("Reply")), ErrorCode.TOO_MANY_REQUESTS, HttpStatus.TOO_MANY_REQUESTS);
        verify(comments, never()).save(any(Comment.class));
        verifyNoInteractions(authors);
    }

    @Test
    void configuredThresholdIsEnforcedByTheSharedLimitHelper() {
        CommunityProperties properties = new CommunityProperties();
        properties.setCommentsPerHour(2);
        CommunityLimits configured = new CommunityLimits(Clock.fixed(NOW, ZoneOffset.UTC), properties);
        service = new CommentService(auth, comments, posts, configured, authors, jdbc, properties);
        when(comments.countByAuthorIdAndCreatedAtAfter(AUTHOR_ID, snapshot.commentCutoff())).thenReturn(2L);
        when(comments.findOldestCreatedAtByAuthorIdAndCreatedAtAfter(AUTHOR_ID, snapshot.commentCutoff()))
                .thenReturn(NOW.minusSeconds(30));
        assertThatThrownBy(() -> service.create(POST_ID, new CommentWrite("Body")))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
                    assertThat(error.getExtra()).containsEntry("retryAfterSeconds", 3570L);
                });
        verify(comments, never()).save(any(Comment.class));
    }

    @Test
    void missingDisplayNameStillReturnsAuthorIdWithNullDisplayNameUsingOneBatchLookup() {
        service = new CommentService(auth, comments, posts, limits, new CommunityAuthors(auth), jdbc, properties);
        when(auth.displayNames(Set.of(AUTHOR_ID))).thenReturn(Map.of());
        CommentResponse result = service.create(POST_ID, new CommentWrite("Body"));
        assertThat(result.author().id()).isEqualTo(AUTHOR_ID);
        assertThat(result.author().displayName()).isNull();
        verify(auth).displayNames(Set.of(AUTHOR_ID));
    }

    @Test
    void writeTransactionsUseReadCommittedSoWaitingWritersSeeTheCommittedQuota() throws Exception {
        for (String method : new String[]{"create", "reply"}) {
            Transactional transaction = CommentService.class.getMethod(method, long.class, CommentWrite.class)
                    .getAnnotation(Transactional.class);
            assertThat(transaction).isNotNull();
            assertThat(transaction.isolation()).isEqualTo(Isolation.READ_COMMITTED);
        }
    }

    @Test
    void updateAuthenticatesThenLocksThePostBeforeTheOwnedCommentAndTakesOneClockSnapshot() {
        Comment comment = owned(CommentStatus.VISIBLE, NOW.minusSeconds(600));
        allowMutation(comment);

        CommentResponse response = service.update(90L, new CommentWrite("\u3000 <b>literal</b> **text** \u3000"));

        assertThat(response.id()).isEqualTo(90L);
        assertThat(response.postId()).isEqualTo(POST_ID);
        assertThat(response.parentId()).isEqualTo(89L);
        assertThat(response.depth()).isEqualTo((short) 2);
        assertThat(response.status()).isEqualTo(CommentStatus.VISIBLE);
        assertThat(response.content()).isEqualTo("<b>literal</b> **text**");
        assertThat(response.createdAt()).isEqualTo(NOW.minusSeconds(600));
        assertThat(response.editedAt()).isEqualTo(NOW);
        assertThat(response.author()).isEqualTo(new UserRef(AUTHOR_ID, "Synthetic User"));
        assertThat(comment.getModeratedBy()).isEqualTo(99L);
        assertThat(comment.getModerationReason()).isEqualTo("Private moderation reason");
        var order = inOrder(auth, comments, posts, limits, authors);
        order.verify(auth).currentUser();
        order.verify(auth).requireVerifiedEmail(AUTHOR_ID);
        order.verify(comments).findOwnedPostId(90L, AUTHOR_ID);
        order.verify(posts).findByIdForUpdate(POST_ID);
        order.verify(comments).findOwnedForUpdate(90L, AUTHOR_ID, POST_ID);
        order.verify(limits).snapshot();
        order.verify(authors).resolve(Set.of(AUTHOR_ID));
        verify(limits).snapshot();
        verifyNoInteractions(jdbc);
        verify(comments, never()).countByAuthorIdAndCreatedAtAfter(anyLong(), any(Instant.class));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1000, 0})
    void updateAllowsTheInstantBeforeAndExactlyAtTheConfiguredFifteenMinuteDeadline(long offsetNanos) {
        Comment comment = owned(CommentStatus.VISIBLE, NOW.minusSeconds(900).minusNanos(offsetNanos));
        allowMutation(comment);
        assertThat(service.update(90L, new CommentWrite("Updated")).content()).isEqualTo("Updated");
        assertThat(comment.getUpdatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 1000, 60000000000L})
    void updateRejectsAnyInstantAfterTheDeadlineWithoutChangingTheComment(long offsetNanos) {
        Comment comment = owned(CommentStatus.VISIBLE, NOW.minusSeconds(900).minusNanos(offsetNanos));
        allowMutation(comment);
        assertError(() -> service.update(90L, new CommentWrite("Rejected")),
                ErrorCode.COMMENT_EDIT_WINDOW_EXPIRED, HttpStatus.CONFLICT);
        assertThat(comment.getContent()).isEqualTo("Original content");
        assertThat(comment.getUpdatedAt()).isEqualTo(comment.getCreatedAt());
        verifyNoInteractions(authors, jdbc);
    }

    @Test
    void rawClockPrecisionControlsExpiryAndOnlyPersistedUpdatedAtIsTruncatedToMicroseconds() {
        Instant rawNow = NOW.plusNanos(999);
        snapshot = new CommunityLimits(Clock.fixed(rawNow, ZoneOffset.UTC), properties).snapshot();
        when(limits.snapshot()).thenReturn(snapshot);
        Comment comment = owned(CommentStatus.VISIBLE, NOW.minusSeconds(900));
        allowMutation(comment);
        assertError(() -> service.update(90L, new CommentWrite("Expired by 999 nanoseconds")),
                ErrorCode.COMMENT_EDIT_WINDOW_EXPIRED, HttpStatus.CONFLICT);
        ReflectionTestUtils.setField(comment, "createdAt", NOW.minusSeconds(600));
        CommentResponse response = service.update(90L, new CommentWrite("Within window"));
        assertThat(response.editedAt()).isEqualTo(rawNow.truncatedTo(ChronoUnit.MICROS));
        assertThat(comment.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void updateUsesTheConfiguredWindowAndDoesNotManufactureAnEditTimestampAtCreationTime() {
        properties.setCommentEditWindow(Duration.ofMinutes(5));
        Comment comment = owned(CommentStatus.VISIBLE, NOW.minusSeconds(301));
        allowMutation(comment);
        assertError(() -> service.update(90L, new CommentWrite("Expired")),
                ErrorCode.COMMENT_EDIT_WINDOW_EXPIRED, HttpStatus.CONFLICT);
        ReflectionTestUtils.setField(comment, "createdAt", NOW);
        comment.setUpdatedAt(NOW);
        assertThat(service.update(90L, new CommentWrite("Same instant")).editedAt()).isNull();
        assertThat(comment.getUpdatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @EnumSource(value = CommentStatus.class, names = {"HIDDEN", "DELETED"})
    void updateChecksNonvisibleStateBeforeTheExpiredWindowAndDoesNotMutateAnything(CommentStatus status) {
        Comment comment = owned(status, NOW.minusSeconds(3600));
        allowMutation(comment);
        assertError(() -> service.update(90L, new CommentWrite("Rejected")),
                ErrorCode.COMMENT_NOT_EDITABLE, HttpStatus.CONFLICT);
        assertThat(comment.getStatus()).isEqualTo(status);
        assertThat(comment.getContent()).isEqualTo("Original content");
        assertThat(comment.getUpdatedAt()).isEqualTo(comment.getCreatedAt());
        assertThat(comment.getModeratedBy()).isEqualTo(99L);
        assertThat(comment.getModerationReason()).isEqualTo("Private moderation reason");
        verifyNoInteractions(authors, jdbc);
    }

    @Test
    void mutationAuthorizationRejectsGuestsWrongRolesAndUnverifiedLearnersBeforeAnyResourceRead() {
        when(auth.currentUser()).thenThrow(new ApiException(ErrorCode.UNAUTHENTICATED,
                HttpStatus.UNAUTHORIZED, "Authentication required", Map.of()));
        assertError(() -> service.update(90L, new CommentWrite("Body")), ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED);
        assertError(() -> service.delete(90L), ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED);
        doReturn(new CurrentUser(AUTHOR_ID, Set.of(RoleCode.MODERATOR), true)).when(auth).currentUser();
        assertError(service::authorizeMutation, ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN);
        assertError(() -> service.update(90L, new CommentWrite("Body")), ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN);
        assertError(() -> service.delete(90L), ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN);
        when(auth.currentUser()).thenReturn(new CurrentUser(AUTHOR_ID, Set.of(RoleCode.LEARNER), false));
        doThrow(new ApiException(ErrorCode.ACCOUNT_UNVERIFIED, HttpStatus.FORBIDDEN, "Verify email", Map.of()))
                .when(auth).requireVerifiedEmail(AUTHOR_ID);
        assertError(service::authorizeMutation, ErrorCode.ACCOUNT_UNVERIFIED, HttpStatus.FORBIDDEN);
        assertError(() -> service.update(90L, new CommentWrite("Body")), ErrorCode.ACCOUNT_UNVERIFIED, HttpStatus.FORBIDDEN);
        assertError(() -> service.delete(90L), ErrorCode.ACCOUNT_UNVERIFIED, HttpStatus.FORBIDDEN);
        verifyNoInteractions(comments, posts, limits, authors, jdbc);
    }

    @Test
    void missingOrUnownedCommentsReturnGenericForbiddenWithoutLockingAnyTarget() {
        when(comments.findOwnedPostId(90L, AUTHOR_ID)).thenReturn(Optional.empty());
        assertForbidden(() -> service.update(90L, new CommentWrite("Body")));
        assertForbidden(() -> service.delete(90L));
        verify(comments, never()).findById(anyLong());
        verify(comments, never()).findOwnedForUpdate(anyLong(), anyLong(), anyLong());
        verifyNoInteractions(posts, limits, authors, jdbc);
    }

    @Test
    void disappearingOwnedTargetsReturnTheSameGenericForbiddenAfterTheirOwnershipCheck() {
        when(comments.findOwnedPostId(90L, AUTHOR_ID)).thenReturn(Optional.of(POST_ID));
        when(posts.findByIdForUpdate(POST_ID)).thenReturn(Optional.empty());
        assertForbidden(() -> service.update(90L, new CommentWrite("Body")));
        assertForbidden(() -> service.delete(90L));
        when(posts.findByIdForUpdate(POST_ID)).thenReturn(Optional.of(post(PostStatus.APPROVED)));
        when(comments.findOwnedForUpdate(90L, AUTHOR_ID, POST_ID)).thenReturn(Optional.empty());
        assertForbidden(() -> service.update(90L, new CommentWrite("Body")));
        assertForbidden(() -> service.delete(90L));
        verifyNoInteractions(limits, authors, jdbc);
    }

    @ParameterizedTest
    @EnumSource(PostStatus.class)
    void ownedCommentMutationsDoNotRequireAPublicParentPost(PostStatus status) {
        Comment comment = owned(CommentStatus.VISIBLE, NOW.minusSeconds(600));
        allowMutation(comment);
        when(posts.findByIdForUpdate(POST_ID)).thenReturn(Optional.of(post(status)));
        service.update(90L, new CommentWrite("Updated"));
        service.delete(90L);
        assertThat(comment.getStatus()).isEqualTo(CommentStatus.DELETED);
    }

    @Test
    void updateResolvesMissingDisplayNamesThroughOneAuthorBatchWithoutDroppingTheId() {
        service = new CommentService(auth, comments, posts, limits, new CommunityAuthors(auth), jdbc, properties);
        when(auth.displayNames(Set.of(AUTHOR_ID))).thenReturn(Map.of());
        allowMutation(owned(CommentStatus.VISIBLE, NOW.minusSeconds(600)));
        CommentResponse response = service.update(90L, new CommentWrite("Body"));
        assertThat(response.author()).isEqualTo(new UserRef(AUTHOR_ID, null));
        verify(auth).displayNames(Set.of(AUTHOR_ID));
    }

    @Test
    void deleteSoftDeletesOldVisibleCommentsAfterPostAndCommentLocksWithoutCheckingQuotaOrEditWindow() {
        Comment comment = owned(CommentStatus.VISIBLE, NOW.minusSeconds(86400));
        allowMutation(comment);
        Instant rawNow = NOW.plusNanos(999);
        when(limits.snapshot()).thenReturn(new CommunityLimits(Clock.fixed(rawNow, ZoneOffset.UTC), properties).snapshot());

        service.delete(90L);

        assertThat(comment.getStatus()).isEqualTo(CommentStatus.DELETED);
        assertThat(comment.getModeratedBy()).isEqualTo(AUTHOR_ID);
        assertThat(comment.getModerationReason()).isEqualTo("AUTHOR_DELETED");
        assertThat(comment.getUpdatedAt()).isEqualTo(NOW);
        assertThat(comment.getContent()).isEqualTo("Original content");
        assertThat(comment.getCreatedAt()).isEqualTo(NOW.minusSeconds(86400));
        assertThat(comment.getPostId()).isEqualTo(POST_ID);
        assertThat(comment.getParentId()).isEqualTo(89L);
        assertThat(comment.getAuthorId()).isEqualTo(AUTHOR_ID);
        assertThat(comment.getDepth()).isEqualTo((short) 2);
        var order = inOrder(comments, posts, limits);
        order.verify(comments).findOwnedPostId(90L, AUTHOR_ID);
        order.verify(posts).findByIdForUpdate(POST_ID);
        order.verify(comments).findOwnedForUpdate(90L, AUTHOR_ID, POST_ID);
        order.verify(limits).snapshot();
        verify(comments, never()).delete(any(Comment.class));
        verify(comments, never()).deleteById(anyLong());
        verifyNoInteractions(jdbc, authors);
    }

    @ParameterizedTest
    @EnumSource(value = CommentStatus.class, names = {"HIDDEN", "DELETED"})
    void deleteOfNonvisibleCommentsPreservesEveryFieldAndDoesNotResolveAuthors(CommentStatus status) {
        Comment comment = owned(status, NOW.minusSeconds(86400));
        allowMutation(comment);
        service.delete(90L);
        service.delete(90L);
        assertThat(comment.getId()).isEqualTo(90L);
        assertThat(comment.getPostId()).isEqualTo(POST_ID);
        assertThat(comment.getParentId()).isEqualTo(89L);
        assertThat(comment.getAuthorId()).isEqualTo(AUTHOR_ID);
        assertThat(comment.getContent()).isEqualTo("Original content");
        assertThat(comment.getDepth()).isEqualTo((short) 2);
        assertThat(comment.getStatus()).isEqualTo(status);
        assertThat(comment.getCreatedAt()).isEqualTo(NOW.minusSeconds(86400));
        assertThat(comment.getUpdatedAt()).isEqualTo(comment.getCreatedAt());
        assertThat(comment.getModeratedBy()).isEqualTo(99L);
        assertThat(comment.getModerationReason()).isEqualTo("Private moderation reason");
        verify(comments, never()).save(any(Comment.class));
        verify(comments, never()).delete(any(Comment.class));
        verifyNoInteractions(jdbc, authors);
    }

    @Test
    void mutationTransactionsUseReadCommittedForRechecksAfterContendedPostLocks() throws Exception {
        for (var method : new java.lang.reflect.Method[]{
                CommentService.class.getMethod("update", long.class, CommentWrite.class),
                CommentService.class.getMethod("delete", long.class)}) {
            Transactional transaction = method.getAnnotation(Transactional.class);
            assertThat(transaction).isNotNull();
            assertThat(transaction.isolation()).isEqualTo(Isolation.READ_COMMITTED);
        }
    }

    private void allowMutation(Comment comment) {
        when(comments.findOwnedPostId(90L, AUTHOR_ID)).thenReturn(Optional.of(POST_ID));
        when(comments.findOwnedForUpdate(90L, AUTHOR_ID, POST_ID)).thenReturn(Optional.of(comment));
    }

    private static Comment owned(CommentStatus status, Instant createdAt) {
        Comment comment = new Comment(POST_ID, 89L, AUTHOR_ID, "Original content", (short) 2, createdAt);
        ReflectionTestUtils.setField(comment, "id", 90L);
        comment.setStatus(status);
        comment.setModeratedBy(99L);
        comment.setModerationReason("Private moderation reason");
        return comment;
    }

    private static void assertForbidden(Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(ErrorCode.FORBIDDEN);
            assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(error.getMessage()).isEqualTo("Bạn không có quyền thực hiện thao tác này.");
            assertThat(error.getExtra()).isEmpty();
        });
    }

    private static Post post(PostStatus status) {
        Post post = new Post(99L, PostCategory.QUESTION, "Title", "Post body", NOW.minusSeconds(60));
        ReflectionTestUtils.setField(post, "id", POST_ID);
        post.setStatus(status);
        return post;
    }

    private static Comment parent(int depth, CommentStatus status) {
        Comment comment = new Comment(POST_ID, depth == 0 ? null : 89L, 99L, "Private parent content",
                (short) depth, NOW.minusSeconds(30));
        ReflectionTestUtils.setField(comment, "id", 90L);
        comment.setStatus(status);
        return comment;
    }

    private static void assertError(Runnable operation, ErrorCode code, HttpStatus status) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(code);
            assertThat(error.getStatus()).isEqualTo(status);
        });
    }
}
