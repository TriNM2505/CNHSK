package com.cnhsk.community.post;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.community.common.CommunityLimits;
import com.cnhsk.community.common.CommunityProperties;
import com.cnhsk.community.common.PostCategory;
import com.cnhsk.community.common.PostStatus;
import com.cnhsk.community.post.web.OwnPost;
import com.cnhsk.community.post.web.PostWrite;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PostServiceTest {

    private static final long AUTHOR_ID = 42L;
    private static final Instant NOW = Instant.parse("2026-11-01T05:00:00Z");

    private AuthApi authApi;
    private PostRepository posts;
    private CommunityLimits limits;
    private NamedParameterJdbcTemplate jdbc;
    private PostService service;
    private CommunityLimits.Snapshot snapshot;

    @BeforeEach
    void setUp() {
        authApi = mock(AuthApi.class);
        posts = mock(PostRepository.class);
        limits = mock(CommunityLimits.class);
        jdbc = mock(NamedParameterJdbcTemplate.class);
        service = new PostService(authApi, posts, limits, jdbc);
        snapshot = new CommunityLimits(Clock.fixed(NOW, ZoneOffset.UTC), new CommunityProperties()).snapshot();
        when(authApi.currentUser()).thenReturn(new CurrentUser(AUTHOR_ID, Set.of(RoleCode.LEARNER), true));
        when(limits.snapshot()).thenReturn(snapshot);
        when(posts.save(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            if (post.getId() == null) {
                ReflectionTestUtils.setField(post, "id", 100L);
            }
            return post;
        });
    }

    @Test
    void createMapsDraftFieldsAndUsesSingleQuotaSnapshot() {
        PostWrite request = write(false);
        OwnPost result = service.create(request);

        assertThat(result.title()).isEqualTo("A title");
        assertThat(result.content()).isEqualTo("Some content");
        assertThat(result.category()).isEqualTo(PostCategory.EXPERIENCE);
        assertThat(result.status()).isEqualTo(PostStatus.DRAFT);
        assertThat(result.createdAt()).isEqualTo(NOW);
        assertThat(result.updatedAt()).isEqualTo(NOW);
        verify(authApi).requireVerifiedEmail(AUTHOR_ID);
        verify(posts).countByAuthorIdAndCreatedAtGreaterThanEqual(AUTHOR_ID, snapshot.dayStart());
        verify(limits).assertCanCreatePost(0, snapshot);
        verify(limits).snapshot();
    }

    @Test
    void createWithSubmitTrueUsesPendingStatus() {
        OwnPost result = service.create(write(true));

        assertThat(result.status()).isEqualTo(PostStatus.PENDING);
    }

    @Test
    void createRequiresVerifiedEmailBeforeQuotaOrPersistence() {
        org.mockito.Mockito.doThrow(new ApiException(ErrorCode.ACCOUNT_UNVERIFIED, HttpStatus.FORBIDDEN,
                "Email verification required", java.util.Map.of()))
                .when(authApi).requireVerifiedEmail(AUTHOR_ID);

        assertThatThrownBy(() -> service.create(write(true)))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo(ErrorCode.ACCOUNT_UNVERIFIED));

        verifyNoInteractions(limits, posts);
    }

    @Test
    void createRejectsCallerWithoutLearnerRoleBeforeVerifiedEmailOrDatabaseWork() {
        when(authApi.currentUser()).thenReturn(new CurrentUser(AUTHOR_ID, Set.of(RoleCode.ADMIN), true));

        assertForbidden(() -> service.create(write(true)));

        verify(authApi, never()).requireVerifiedEmail(AUTHOR_ID);
        verifyNoInteractions(limits, posts, jdbc);
    }

    @Test
    void updateLocksOwnedPostThenUpdatesWritableFieldsAndPreservesReviewAndPublicationMetadata() {
        Instant createdAt = NOW.minusSeconds(3600);
        Instant publishedAt = NOW.minusSeconds(1800);
        Post post = new Post(AUTHOR_ID, PostCategory.QUESTION, "Old title", "Old content", createdAt);
        ReflectionTestUtils.setField(post, "id", 7L);
        post.setStatus(PostStatus.REJECTED);
        post.setReviewReason("Please add detail");
        post.setPublishedAt(publishedAt);
        when(posts.findByIdForUpdate(7L)).thenReturn(Optional.of(post));
        when(posts.save(post)).thenReturn(post);

        OwnPost result = service.update(7L, write(true));

        assertThat(post.getTitle()).isEqualTo("A title");
        assertThat(post.getContent()).isEqualTo("Some content");
        assertThat(post.getCategory()).isEqualTo(PostCategory.EXPERIENCE);
        assertThat(post.getStatus()).isEqualTo(PostStatus.PENDING);
        assertThat(post.getAuthorId()).isEqualTo(AUTHOR_ID);
        assertThat(post.getCreatedAt()).isEqualTo(createdAt);
        assertThat(post.getPublishedAt()).isEqualTo(publishedAt);
        assertThat(post.getReviewReason()).isEqualTo("Please add detail");
        assertThat(post.getUpdatedAt()).isEqualTo(NOW);
        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.status()).isEqualTo(PostStatus.PENDING);
        assertThat(result.reviewReason()).isNull();
        assertThat(result.publishedAt()).isEqualTo(publishedAt);
        assertThat(result.createdAt()).isEqualTo(createdAt);
        assertThat(result.updatedAt()).isEqualTo(NOW);
        verify(posts).findByIdForUpdate(7L);
        verify(posts).save(post);
        verify(limits).snapshot();
    }

    @Test
    void updateWithSubmitFalseSetsDraft() {
        Post post = ownedPost(8L, PostStatus.DRAFT);
        when(posts.findByIdForUpdate(8L)).thenReturn(Optional.of(post));
        when(posts.save(post)).thenReturn(post);

        assertThat(service.update(8L, write(false)).status()).isEqualTo(PostStatus.DRAFT);
    }

    @Test
    void updateMissingPostReturnsForbiddenWithoutSnapshotOrSave() {
        when(posts.findByIdForUpdate(9L)).thenReturn(Optional.empty());

        assertForbidden(() -> service.update(9L, write(false)));

        verify(limits, never()).snapshot();
        verify(posts, never()).save(any(Post.class));
    }

    @Test
    void updateNonOwnerReturnsForbiddenWithoutSnapshotOrSave() {
        Post post = new Post(AUTHOR_ID + 1, PostCategory.QUESTION, "Title", "Content", NOW);
        when(posts.findByIdForUpdate(10L)).thenReturn(Optional.of(post));

        assertForbidden(() -> service.update(10L, write(false)));

        verify(limits, never()).snapshot();
        verify(posts, never()).save(any(Post.class));
    }

    @Test
    void updateHiddenPostReturnsConflictWithoutMutationOrSnapshot() {
        Post post = ownedPost(11L, PostStatus.HIDDEN);
        String originalTitle = post.getTitle();
        when(posts.findByIdForUpdate(11L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service.update(11L, write(true)))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(ErrorCode.POST_NOT_EDITABLE);
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });

        assertThat(post.getTitle()).isEqualTo(originalTitle);
        verify(limits, never()).snapshot();
        verify(posts, never()).save(any(Post.class));
    }

    private static PostWrite write(boolean submit) {
        return new PostWrite("A title", "Some content", PostCategory.EXPERIENCE, submit);
    }

    private static Post ownedPost(long id, PostStatus status) {
        Post post = new Post(AUTHOR_ID, PostCategory.QUESTION, "Title", "Content", NOW.minusSeconds(20));
        ReflectionTestUtils.setField(post, "id", id);
        post.setStatus(status);
        return post;
    }

    private static void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(ErrorCode.FORBIDDEN);
            assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        });
    }
}
