package com.cnhsk.community.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
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
import com.cnhsk.community.common.CommunityAuthors;
import com.cnhsk.community.common.PostCategory;
import com.cnhsk.community.common.PostStatus;
import com.cnhsk.community.common.UserRef;
import com.cnhsk.community.moderation.web.RejectWrite;
import com.cnhsk.community.post.Post;
import com.cnhsk.community.post.PostRepository;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

class PostModerationServiceTest {
    private static final long MODERATOR = 42L;
    private static final long AUTHOR = 99L;
    private static final long TARGET = 17L;
    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00.123456789Z");
    private static final Instant STORED_NOW = NOW.truncatedTo(ChronoUnit.MICROS);
    private static final Instant ORIGINAL = Instant.parse("2026-10-01T01:02:03Z");
    private AuthApi auth;
    private PostRepository posts;
    private CommunityAuthors authors;
    private Clock clock;
    private AuditService audit;
    private PostModerationService service;

    @BeforeEach
    void setUp() {
        auth = mock(AuthApi.class);
        posts = mock(PostRepository.class);
        authors = mock(CommunityAuthors.class);
        clock = mock(Clock.class);
        audit = mock(AuditService.class);
        when(auth.currentUser()).thenReturn(new CurrentUser(MODERATOR, Set.of(RoleCode.MODERATOR), false));
        when(clock.instant()).thenReturn(NOW);
        service = new PostModerationService(posts, auth, authors, clock, audit);
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = "MODERATOR", mode = EnumSource.Mode.EXCLUDE)
    void everyEntryPointRejectsOtherRolesBeforeDatabaseClockAndAuditWork(RoleCode role) {
        when(auth.currentUser()).thenReturn(new CurrentUser(MODERATOR, Set.of(role), true));
        for (Runnable action : operations()) assertError(action, ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN);
        verifyNoInteractions(posts, authors, clock, audit);
        verify(auth, never()).requireVerifiedEmail(anyLong());
    }

    @Test
    void everyEntryPointPropagatesAuthenticationFailureBeforeAnyWork() {
        when(auth.currentUser()).thenThrow(api(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED));
        for (Runnable action : operations()) assertError(action, ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(posts, authors, clock, audit);
    }

    @Test
    void unverifiedModeratorWithAdditionalRolesCanReadWithoutALearnerOrVerifiedEmailGate() {
        when(auth.currentUser()).thenReturn(new CurrentUser(MODERATOR, Set.of(RoleCode.MODERATOR, RoleCode.ADMIN), false));
        assertThat(service.listPendingPosts(0, 20).items()).isEmpty();
        verify(auth).currentUser();
        verifyNoMoreInteractions(auth);
    }

    @Test
    void pendingQueueCountsBeforeFetchingAndResolvesDistinctAuthorsOnceWithUnicodeExcerpts() {
        Post first = post(TARGET, AUTHOR, PostStatus.PENDING);
        first.setContent("😀".repeat(201));
        Post second = post(TARGET + 1, AUTHOR, PostStatus.PENDING);
        when(posts.countPending()).thenReturn(2L);
        when(posts.findPending(PageRequest.of(0, 20))).thenReturn(List.of(first, second));
        when(authors.resolve(Set.of(AUTHOR))).thenReturn(Map.of(AUTHOR, new UserRef(AUTHOR, "Author")));

        var result = service.listPendingPosts(0, 20);

        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).id()).isEqualTo(TARGET);
        assertThat(result.items().get(0).excerpt()).isEqualTo("😀".repeat(200));
        assertThat(result.items().get(0).author()).isEqualTo(new UserRef(AUTHOR, "Author"));
        assertThat(result.items().get(0).createdAt()).isEqualTo(ORIGINAL);
        var order = inOrder(auth, posts, authors);
        order.verify(auth).currentUser();
        order.verify(posts).countPending();
        order.verify(posts).findPending(PageRequest.of(0, 20));
        order.verify(authors).resolve(Set.of(AUTHOR));
        verifyNoMoreInteractions(auth, posts, authors);
        verifyNoInteractions(clock, audit);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 99, Integer.MAX_VALUE})
    void emptyAndOutOfRangePagesOnlyCountWithoutOverflowOrAuthorLookup(int page) {
        when(posts.countPending()).thenReturn(page == 0 ? 0L : 25L);
        var result = service.listPendingPosts(page, 50);
        assertThat(result.items()).isEmpty();
        assertThat(result.page()).isEqualTo(page);
        assertThat(result.totalElements()).isEqualTo(page == 0 ? 0L : 25L);
        verify(posts).countPending();
        verifyNoMoreInteractions(posts);
        verifyNoInteractions(authors, clock, audit);
    }

    @Test
    void invalidPaginationIsRejectedBeforeCounting() {
        for (int[] input : List.of(new int[] {-1, 20}, new int[] {0, 0}, new int[] {0, 51})) {
            assertError(() -> service.listPendingPosts(input[0], input[1]), ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        verifyNoInteractions(posts, authors, clock, audit);
    }

    @ParameterizedTest
    @EnumSource(PostStatus.class)
    void detailReadsEveryStateAndBatchesAuthorAndReviewerWithoutMaskingReviewMetadata(PostStatus status) {
        Post stored = post(TARGET, AUTHOR, status);
        stored.setReviewedBy(MODERATOR);
        stored.setReviewedAt(ORIGINAL.plusSeconds(2));
        stored.setPublishedAt(ORIGINAL.plusSeconds(1));
        stored.setReviewReason("Stored previous reason");
        when(posts.findById(TARGET)).thenReturn(Optional.of(stored));
        when(authors.resolve(Set.of(AUTHOR, MODERATOR))).thenReturn(Map.of(AUTHOR, new UserRef(AUTHOR, "Author")));

        var result = service.getModerationPost(TARGET);

        assertThat(result.status()).isEqualTo(status);
        assertThat(result.content()).isEqualTo("Literal <b>body</b> **text**");
        assertThat(result.reviewReason()).isEqualTo("Stored previous reason");
        assertThat(result.reviewedBy()).isEqualTo(new UserRef(MODERATOR, null));
        assertThat(result.reviewedAt()).isEqualTo(ORIGINAL.plusSeconds(2));
        assertThat(result.publishedAt()).isEqualTo(ORIGINAL.plusSeconds(1));
        assertThat(result.createdAt()).isEqualTo(ORIGINAL);
        assertThat(result.updatedAt()).isEqualTo(ORIGINAL);
        verify(authors).resolve(Set.of(AUTHOR, MODERATOR));
        verifyNoInteractions(clock, audit);
    }

    @Test
    void detailWithNoReviewerKeepsNullReferenceAndMissingAuthorNameKeepsIdentity() {
        when(posts.findById(TARGET)).thenReturn(Optional.of(post(TARGET, AUTHOR, PostStatus.PENDING)));
        when(authors.resolve(Set.of(AUTHOR))).thenReturn(Map.of());
        var result = service.getModerationPost(TARGET);
        assertThat(result.author()).isEqualTo(new UserRef(AUTHOR, null));
        assertThat(result.reviewedBy()).isNull();
        assertThat(result.reviewedAt()).isNull();
        verify(authors).resolve(Set.of(AUTHOR));
    }

    @Test
    void missingDetailDoesNotResolveNames() {
        assertError(() -> service.getModerationPost(TARGET), ErrorCode.POST_NOT_FOUND, HttpStatus.NOT_FOUND);
        verifyNoInteractions(authors, clock, audit);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void missingAndSelfAuthorChecksPrecedeClockConditionalUpdateAndAudit(boolean approve) {
        when(posts.findModerationAuthorId(TARGET)).thenReturn(Optional.empty());
        assertError(() -> decide(approve), ErrorCode.POST_NOT_FOUND, HttpStatus.NOT_FOUND);
        when(posts.findModerationAuthorId(TARGET)).thenReturn(Optional.of(MODERATOR));
        assertError(() -> decide(approve), ErrorCode.SELF_MODERATION, HttpStatus.FORBIDDEN);
        verify(posts, never()).approvePending(anyLong(), anyLong(), any());
        verify(posts, never()).rejectPending(anyLong(), anyLong(), any(), any());
        verify(posts, never()).findById(anyLong());
        verifyNoInteractions(authors, clock, audit);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void conditionalUpdateLoserReturnsConflictAndNeverAuditsOrReloads(boolean approve) {
        when(posts.findModerationAuthorId(TARGET)).thenReturn(Optional.of(AUTHOR));
        assertError(() -> decide(approve), ErrorCode.POST_NOT_PENDING, HttpStatus.CONFLICT);
        verify(posts, never()).findById(anyLong());
        verify(posts, never()).existsById(anyLong());
        verifyNoInteractions(authors, audit);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void successfulDecisionUsesOneMicrosecondSnapshotAuditsExactDetailThenReloadsFreshState(boolean approve) {
        Post fresh = successful(approve);
        fresh.setPublishedAt(ORIGINAL.minusSeconds(1));
        fresh.setReviewReason(approve ? "Preserved previous reason" : "Reason");

        var result = approve ? service.approvePost(TARGET) : service.rejectPost(TARGET, new RejectWrite("\u3000 Reason \t"));

        assertThat(result.status()).isEqualTo(approve ? PostStatus.APPROVED : PostStatus.REJECTED);
        assertThat(result.reviewedBy()).isEqualTo(new UserRef(MODERATOR, "Moderator"));
        assertThat(result.reviewedAt()).isEqualTo(STORED_NOW);
        assertThat(result.reviewReason()).isEqualTo(fresh.getReviewReason());
        assertThat(result.publishedAt()).isEqualTo(ORIGINAL.minusSeconds(1));
        assertThat(result.createdAt()).isEqualTo(ORIGINAL);
        assertThat(result.updatedAt()).isEqualTo(ORIGINAL);
        var order = inOrder(auth, posts, clock, audit, authors);
        order.verify(auth).currentUser();
        order.verify(posts).findModerationAuthorId(TARGET);
        order.verify(clock).instant();
        if (approve) order.verify(posts).approvePending(TARGET, MODERATOR, STORED_NOW);
        else order.verify(posts).rejectPending(TARGET, MODERATOR, "Reason", STORED_NOW);
        order.verify(audit).recordDetail(MODERATOR, approve ? "COMMUNITY_POST_APPROVE" : "COMMUNITY_POST_REJECT",
                "POST", TARGET, approve ? Map.of() : Map.of("reason", "Reason"));
        order.verify(posts).findById(TARGET);
        order.verify(authors).resolve(Set.of(AUTHOR, MODERATOR));
        verifyNoMoreInteractions(auth, posts, clock, audit, authors);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void auditFailurePropagatesWithoutAFreshReadOrSuccessResponse(boolean approve) {
        successful(approve);
        RuntimeException failure = new IllegalStateException("Synthetic audit failure");
        doThrow(failure).when(audit).recordDetail(MODERATOR,
                approve ? "COMMUNITY_POST_APPROVE" : "COMMUNITY_POST_REJECT", "POST", TARGET,
                approve ? Map.of() : Map.of("reason", "Reason"));
        assertThatThrownBy(() -> decide(approve)).isSameAs(failure);
        verify(posts, never()).findById(anyLong());
        verifyNoInteractions(authors);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void exactStructuredSelfCheckConstraintBecomes403WithoutAudit(boolean approve) {
        when(posts.findModerationAuthorId(TARGET)).thenReturn(Optional.of(AUTHOR));
        DataIntegrityViolationException failure = integrity("ck_post_self", "23514");
        updateFails(approve, failure);
        assertError(() -> decide(approve), ErrorCode.SELF_MODERATION, HttpStatus.FORBIDDEN);
        verifyNoInteractions(audit, authors);
        verify(posts, never()).existsById(anyLong());
    }

    @Test
    void unrelatedConstraintsAndFreeTextMentionsAreNeverMappedToSelfModeration() {
        when(posts.findModerationAuthorId(TARGET)).thenReturn(Optional.of(AUTHOR));
        for (DataIntegrityViolationException failure : List.of(integrity("ck_post_status", "23514"),
                integrity("ck_post_self_suffix", "23514"), integrity("fk_posts_author", "23503"),
                integrity("uq_posts_title", "23505"),
                new DataIntegrityViolationException("ck_post_self SQLState 23514"))) {
            updateFails(true, failure);
            assertThatThrownBy(() -> service.approvePost(TARGET)).isSameAs(failure);
        }
        verifyNoInteractions(audit, authors);
    }

    @Test
    void readAndMutationTransactionContractsAreExplicit() throws Exception {
        Transactional type = PostModerationService.class.getAnnotation(Transactional.class);
        for (String method : List.of("listPendingPosts", "getModerationPost")) {
            Transactional annotation = (method.equals("listPendingPosts")
                    ? PostModerationService.class.getMethod(method, int.class, int.class)
                    : PostModerationService.class.getMethod(method, long.class)).getAnnotation(Transactional.class);
            assertThat(annotation == null ? type.readOnly() : annotation.readOnly()).isTrue();
        }
        for (String method : List.of("approvePost", "rejectPost")) {
            Transactional annotation = (method.equals("approvePost")
                    ? PostModerationService.class.getMethod(method, long.class)
                    : PostModerationService.class.getMethod(method, long.class, RejectWrite.class)).getAnnotation(Transactional.class);
            assertThat(annotation).isNotNull();
            assertThat(annotation.readOnly()).isFalse();
            assertThat(annotation.isolation()).isEqualTo(Isolation.READ_COMMITTED);
        }
    }

    private Post successful(boolean approve) {
        when(posts.findModerationAuthorId(TARGET)).thenReturn(Optional.of(AUTHOR));
        if (approve) when(posts.approvePending(TARGET, MODERATOR, STORED_NOW)).thenReturn(1);
        else when(posts.rejectPending(TARGET, MODERATOR, "Reason", STORED_NOW)).thenReturn(1);
        Post fresh = post(TARGET, AUTHOR, approve ? PostStatus.APPROVED : PostStatus.REJECTED);
        fresh.setReviewedBy(MODERATOR);
        fresh.setReviewedAt(STORED_NOW);
        when(posts.findById(TARGET)).thenReturn(Optional.of(fresh));
        when(authors.resolve(Set.of(AUTHOR, MODERATOR))).thenReturn(Map.of(
                AUTHOR, new UserRef(AUTHOR, "Author"), MODERATOR, new UserRef(MODERATOR, "Moderator")));
        return fresh;
    }

    private List<Runnable> operations() {
        return List.of(() -> service.listPendingPosts(0, 20), () -> service.getModerationPost(TARGET),
                () -> service.approvePost(TARGET), () -> service.rejectPost(TARGET, new RejectWrite("Reason")));
    }

    private void decide(boolean approve) {
        if (approve) service.approvePost(TARGET); else service.rejectPost(TARGET, new RejectWrite("Reason"));
    }

    private void updateFails(boolean approve, RuntimeException failure) {
        if (approve) doThrow(failure).when(posts).approvePending(TARGET, MODERATOR, STORED_NOW);
        else doThrow(failure).when(posts).rejectPending(TARGET, MODERATOR, "Reason", STORED_NOW);
    }

    private static Post post(long id, long author, PostStatus status) {
        Post post = new Post(author, PostCategory.QUESTION, "Synthetic title", "Literal <b>body</b> **text**", ORIGINAL);
        ReflectionTestUtils.setField(post, "id", id);
        post.setStatus(status);
        return post;
    }

    private static DataIntegrityViolationException integrity(String constraint, String state) {
        return new DataIntegrityViolationException("Synthetic integrity error", new IllegalStateException(
                new ConstraintViolationException("Synthetic constraint violation", new SQLException("Synthetic", state), constraint)));
    }

    private static ApiException api(ErrorCode code, HttpStatus status) {
        return new ApiException(code, status, "Synthetic error", Map.of());
    }

    private static void assertError(Runnable action, ErrorCode code, HttpStatus status) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(code);
            assertThat(error.getStatus()).isEqualTo(status);
        });
    }
}
