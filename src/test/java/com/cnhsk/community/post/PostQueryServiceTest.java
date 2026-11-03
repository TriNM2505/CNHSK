package com.cnhsk.community.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.community.common.ReactionKind;
import com.cnhsk.community.comment.CommentRepository;
import com.cnhsk.community.common.CommunityAuthors;
import com.cnhsk.community.common.PostCategory;
import com.cnhsk.community.common.PostCount;
import com.cnhsk.community.common.PostStatus;
import com.cnhsk.community.common.UserRef;
import com.cnhsk.community.reaction.ReactionRepository;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class PostQueryServiceTest {
    private static final Instant PUBLISHED = Instant.parse("2026-10-30T12:00:00Z");
    private PostRepository posts;
    private CommentRepository comments;
    private ReactionRepository reactions;
    private CommunityAuthors authors;
    private AuthApi auth;
    private PostQueryService service;

    @BeforeEach
    void setUp() {
        posts = mock(PostRepository.class);
        comments = mock(CommentRepository.class);
        reactions = mock(ReactionRepository.class);
        authors = mock(CommunityAuthors.class);
        auth = mock(AuthApi.class);
        service = new PostQueryService(posts, comments, reactions, authors, auth);
    }

    @Test
    void FR014_excerptsPreserveCodePointsCombiningMarksAndLiteralWhitespace() {
        List<String> contents = List.of("", "a".repeat(199), "a".repeat(200), "a".repeat(201),
                "😀".repeat(200), "a".repeat(199) + "😀suffix", "汉".repeat(201),
                "a".repeat(198) + "e\u0301suffix", "  literal <b>**text**</b>  ");
        when(authors.resolve(Set.of(42L))).thenReturn(Map.of(42L, new UserRef(42L, null)));
        when(comments.countVisibleByPostIds(Set.of(1L))).thenReturn(List.of());
        when(reactions.countLikesByPostIds(Set.of(1L))).thenReturn(List.of());
        for (String content : contents) {
            when(posts.findPublished(eq(1L), isNull(), any(Pageable.class)))
                    .thenReturn(List.of(post(1, 42, content)));
            var detail = service.publishedDetail(1L);
            int end = content.offsetByCodePoints(0, Math.min(200, content.codePointCount(0, content.length())));
            assertThat(detail.excerpt()).isEqualTo(content.substring(0, end));
            assertThat(detail.excerpt().codePointCount(0, detail.excerpt().length())).isLessThanOrEqualTo(200);
            assertThat(detail.content()).isEqualTo(content);
            assertThat(detail.author()).isEqualTo(new UserRef(42L, null));
            assertThat(detail.commentCount()).isZero();
            assertThat(detail.likeCount()).isZero();
        }
        verifyNoInteractions(auth);
    }

    @Test
    void FR014_listBatchesUniqueAuthorsAndGroupsCountsOnceForThePage() {
        when(posts.countPublished(PostCategory.QUESTION)).thenReturn(12L);
        when(posts.findPublished(isNull(), eq(PostCategory.QUESTION), any(Pageable.class)))
                .thenReturn(List.of(post(8, 42, "First"), post(7, 43, "Second"), post(6, 42, "Third")));
        Set<Long> ids = Set.of(8L, 7L, 6L);
        when(comments.countVisibleByPostIds(ids)).thenReturn(List.of(new PostCount(8L, 3L)));
        when(reactions.countLikesByPostIds(ids)).thenReturn(List.of(new PostCount(7L, 2L)));
        when(authors.resolve(Set.of(42L, 43L))).thenReturn(Map.of(42L, new UserRef(42L, "One"),
                43L, new UserRef(43L, null)));

        var page = service.publishedList(2, 3, PostCategory.QUESTION);

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(3);
        assertThat(page.totalElements()).isEqualTo(12);
        assertThat(page.items()).extracting(item -> item.id()).containsExactly(8L, 7L, 6L);
        assertThat(page.items()).extracting(item -> item.commentCount()).containsExactly(3L, 0L, 0L);
        assertThat(page.items()).extracting(item -> item.likeCount()).containsExactly(0L, 2L, 0L);
        assertThat(page.items().get(1).author().displayName()).isNull();
        var pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(posts).findPublished(isNull(), eq(PostCategory.QUESTION), pageable.capture());
        assertThat(pageable.getValue().getOffset()).isEqualTo(6);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(3);
        verify(comments).countVisibleByPostIds(ids);
        verify(reactions).countLikesByPostIds(ids);
        verify(authors).resolve(Set.of(42L, 43L));
        verifyNoMoreInteractions(authors, comments, reactions);
        var order = inOrder(posts);
        order.verify(posts).countPublished(PostCategory.QUESTION);
        order.verify(posts).findPublished(isNull(), eq(PostCategory.QUESTION), any(Pageable.class));
        verifyNoInteractions(auth);
    }

    @Test
    void FR012_emptyDatasetAndHugePageReturnExactTotalsWithoutFetchingOrEmptyInQueries() {
        when(posts.countPublished(null)).thenReturn(0L, 25L, 25L);
        for (int pageNumber : new int[] {0, 99, Integer.MAX_VALUE}) {
            var page = service.publishedList(pageNumber, 50, null);
            assertThat(page.items()).isEmpty();
            assertThat(page.page()).isEqualTo(pageNumber);
            assertThat(page.size()).isEqualTo(50);
            assertThat(page.totalElements()).isEqualTo(pageNumber == 0 ? 0 : 25);
        }
        verify(posts, never()).findPublished(any(), any(), any());
        verifyNoInteractions(authors, comments, reactions);
    }

    @Test
    void FR014_concurrentlyEmptiedPageDoesNotResolveAuthorsOrIssueEmptyInQueries() {
        when(posts.countPublished(null)).thenReturn(1L);
        when(posts.findPublished(isNull(), isNull(), any(Pageable.class))).thenReturn(List.of());
        assertThat(service.publishedList(0, 20, null).items()).isEmpty();
        verifyNoInteractions(authors, comments, reactions);
    }

    @Test
    void FR015_missingDetailUsesPublishedQueryAndReturnsSafeNotFoundBeforeOtherReads() {
        when(posts.findPublished(eq(9L), isNull(), any(Pageable.class))).thenReturn(List.of());
        assertThatThrownBy(() -> service.publishedDetail(9L)).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(ErrorCode.POST_NOT_FOUND);
            assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        });
        verifyNoInteractions(authors, comments, reactions);
        verify(posts, never()).findById(any());
        verify(posts, never()).findByIdForUpdate(anyLong());
        verify(posts, never()).countPublished(any());
    }

    @Test
    void FR006_missingAuthorNameSerializesExplicitNullWithoutPrivateFieldsForBothAdapterResults() {
        when(posts.findPublished(eq(1L), isNull(), any(Pageable.class))).thenReturn(List.of(post(1, 42, "Body")));
        when(comments.countVisibleByPostIds(Set.of(1L))).thenReturn(List.of());
        when(reactions.countLikesByPostIds(Set.of(1L))).thenReturn(List.of());
        when(authors.resolve(Set.of(42L))).thenReturn(Map.of(42L, new UserRef(42L, null)), Map.of());
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        for (int attempt = 0; attempt < 2; attempt++) {
            JsonNode rendered = mapper.valueToTree(service.publishedDetail(1));
            JsonNode author = rendered.path("author");
            assertThat(author.path("id").asLong()).isEqualTo(42);
            assertThat(author.has("displayName")).isTrue();
            assertThat(author.get("displayName").isNull()).isTrue();
            assertNoPrivateFields(rendered);
        }
        verifyNoInteractions(auth);
    }

    @Test
    void FR001_privateReadsRequireActualLearnerBeforeAnyRepositoryCall() {
        ApiException unauthenticated = new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED,
                "Authentication required", Map.of());
        when(auth.currentUser()).thenThrow(unauthenticated);
        assertThatThrownBy(() -> service.ownList(0, 20, null)).isSameAs(unauthenticated);
        assertThatThrownBy(() -> service.ownDetail(1)).isSameAs(unauthenticated);
        assertThatThrownBy(() -> service.viewerState(1)).isSameAs(unauthenticated);
        org.mockito.Mockito.reset(auth);
        for (RoleCode role : RoleCode.values()) {
            if (role == RoleCode.LEARNER) continue;
            when(auth.currentUser()).thenReturn(new CurrentUser(42, Set.of(role), true));
            assertForbidden(() -> service.ownList(0, 20, null));
            assertForbidden(() -> service.ownDetail(1));
            assertForbidden(() -> service.viewerState(1));
        }
        verifyNoInteractions(posts, comments, reactions, authors);
        verify(auth, never()).requireVerifiedEmail(anyLong());
        verify(auth, never()).hasRole(anyLong(), any());
    }

    @Test
    void FR016_ownerListUsesSessionIdentityCountsBeforeFetchingAndReturnsExactProjection() {
        learner();
        Post owned = post(8, 42, "Body");
        owned.setStatus(PostStatus.REJECTED);
        owned.setReviewReason("Revise synthetic source");
        when(posts.countOwned(42, PostStatus.REJECTED)).thenReturn(9L);
        when(posts.findOwned(eq(42L), isNull(), eq(PostStatus.REJECTED), any(Pageable.class)))
                .thenReturn(List.of(owned));
        var page = service.ownList(1, 3, PostStatus.REJECTED);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(3);
        assertThat(page.totalElements()).isEqualTo(9);
        assertThat(page.items()).containsExactly(PostService.toOwnPost(owned));
        var pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        var order = inOrder(posts);
        order.verify(posts).countOwned(42, PostStatus.REJECTED);
        order.verify(posts).findOwned(eq(42L), isNull(), eq(PostStatus.REJECTED), pageable.capture());
        assertThat(pageable.getValue().getOffset()).isEqualTo(3);
        verifyNoMoreInteractions(posts);
        verifyNoInteractions(authors, comments, reactions);
        verify(auth, never()).requireVerifiedEmail(anyLong());
    }

    @Test
    void FR016_emptyAndOutOfRangeOwnerPagesDoNotOverflowOrFetchRows() {
        learner();
        when(posts.countOwned(42, null)).thenReturn(0L, 3L, 3L);
        for (int number : new int[] {0, 99, Integer.MAX_VALUE}) {
            var page = service.ownList(number, 50, null);
            assertThat(page.items()).isEmpty();
            assertThat(page.page()).isEqualTo(number);
            assertThat(page.totalElements()).isEqualTo(number == 0 ? 0 : 3);
        }
        verify(posts, never()).findOwned(anyLong(), any(), any(), any());
        verifyNoInteractions(authors, comments, reactions);
    }

    @Test
    void FR016_ownerDetailsMapEveryStatusWithoutMutatingStoredReviewMetadata() {
        learner();
        for (PostStatus status : PostStatus.values()) {
            Post post = post(7, 42, "Literal <b>body</b>");
            post.setStatus(status);
            post.setReviewReason("Stored reason");
            when(posts.findOwned(eq(42L), eq(7L), isNull(), any(Pageable.class))).thenReturn(List.of(post));
            var detail = service.ownDetail(7);
            assertThat(detail).isEqualTo(PostService.toOwnPost(post));
            assertThat(detail.status()).isEqualTo(status);
            assertThat(detail.reviewReason()).isEqualTo(status == PostStatus.REJECTED || status == PostStatus.HIDDEN
                    ? "Stored reason" : null);
            assertThat(post.getReviewReason()).isEqualTo("Stored reason");
            assertThat(post.getPublishedAt()).isEqualTo(PUBLISHED);
        }
        Post hidden = post(7, 42, "Body");
        hidden.setStatus(PostStatus.HIDDEN);
        when(posts.findOwned(eq(42L), eq(7L), isNull(), any(Pageable.class))).thenReturn(List.of(hidden));
        assertThat(service.ownDetail(7).reviewReason()).isNull();
        verifyNoInteractions(authors, comments, reactions);
        verify(posts, never()).findById(any());
        verify(posts, never()).findByIdForUpdate(anyLong());
        verify(posts, never()).countOwned(anyLong(), any());
    }

    @Test
    void FR016_ownerMissUsesOnlyOwnershipScopedLookupAndSafeForbidden() {
        learner();
        when(posts.findOwned(eq(42L), eq(99L), isNull(), any(Pageable.class))).thenReturn(List.of());
        assertForbidden(() -> service.ownDetail(99));
        verify(posts).findOwned(eq(42L), eq(99L), isNull(), any(Pageable.class));
        verifyNoMoreInteractions(posts);
        verifyNoInteractions(authors, comments, reactions);
    }

    @Test
    void FR056_viewerVisibilityFailureStopsBeforeReactionLookup() {
        learner();
        when(posts.findPublished(eq(9L), isNull(), any(Pageable.class))).thenReturn(List.of());
        assertThatThrownBy(() -> service.viewerState(9)).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(ErrorCode.POST_NOT_FOUND);
            assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        });
        verify(posts).findPublished(eq(9L), isNull(), any(Pageable.class));
        verifyNoMoreInteractions(posts);
        verifyNoInteractions(reactions, comments, authors);
    }

    @Test
    void FR056_viewerFlagsCoverAllCombinationsForOnlyTheAuthenticatedUser() {
        learner();
        when(posts.findPublished(eq(8L), isNull(), any(Pageable.class))).thenReturn(List.of(post(8, 43, "Body")));
        List<List<ReactionKind>> combinations = List.of(List.of(), List.of(ReactionKind.LIKE),
                List.of(ReactionKind.REPORT), List.of(ReactionKind.LIKE, ReactionKind.REPORT));
        for (List<ReactionKind> kinds : combinations) {
            when(reactions.findPostViewerKinds(42, 8)).thenReturn(kinds);
            var state = service.viewerState(8);
            assertThat(state.liked()).isEqualTo(kinds.contains(ReactionKind.LIKE));
            assertThat(state.reported()).isEqualTo(kinds.contains(ReactionKind.REPORT));
        }
        verifyNoInteractions(authors, comments);
        verify(auth, never()).requireVerifiedEmail(anyLong());
    }

    @Test
    void FR006_publicReadsNeverResolveAuthenticatedIdentity() {
        when(posts.countPublished(null)).thenReturn(0L);
        service.publishedList(0, 20, null);
        when(posts.findPublished(eq(9L), isNull(), any(Pageable.class))).thenReturn(List.of());
        assertThatThrownBy(() -> service.publishedDetail(9)).isInstanceOf(ApiException.class);
        verifyNoInteractions(auth);
    }

    private void learner() {
        when(auth.currentUser()).thenReturn(new CurrentUser(42, Set.of(RoleCode.LEARNER), false));
    }

    private void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(ErrorCode.FORBIDDEN);
            assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        });
    }

    private void assertNoPrivateFields(JsonNode node) {
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                assertThat(Set.of("email", "passwordHash", "password", "roles", "status", "reviewReason",
                        "reviewedBy", "reviewedAt", "createdAt", "updatedAt", "authorId"))
                        .doesNotContain(field.getKey());
                assertNoPrivateFields(field.getValue());
            });
        } else if (node.isArray()) node.forEach(this::assertNoPrivateFields);
    }

    private Post post(long id, long author, String content) {
        Post post = new Post(author, PostCategory.QUESTION, "Title " + id, content, PUBLISHED.minusSeconds(10));
        ReflectionTestUtils.setField(post, "id", id);
        post.setStatus(PostStatus.APPROVED);
        post.setPublishedAt(PUBLISHED);
        return post;
    }
}
