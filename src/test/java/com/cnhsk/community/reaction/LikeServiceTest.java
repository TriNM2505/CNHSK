package com.cnhsk.community.reaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.cnhsk.community.common.PostCategory;
import com.cnhsk.community.common.PostCount;
import com.cnhsk.community.common.PostStatus;
import com.cnhsk.community.post.Post;
import com.cnhsk.community.post.PostRepository;
import com.cnhsk.community.post.web.LikeState;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

class LikeServiceTest {
    private static final long USER = 42L;
    private static final long POST = 17L;
    private AuthApi auth;
    private PostRepository posts;
    private ReactionRepository reactions;
    private LikeService service;

    @BeforeEach
    void setUp() {
        auth = mock(AuthApi.class);
        posts = mock(PostRepository.class);
        reactions = mock(ReactionRepository.class);
        service = new LikeService(auth, posts, reactions);
        when(auth.currentUser()).thenReturn(new CurrentUser(USER, Set.of(RoleCode.LEARNER), false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void FR002_unverifiedLearnersMayLikeAndUnlikeWithoutVerifiedEmailCheck(boolean like) {
        published();
        when(reactions.countLikesByPostIds(Set.of(POST))).thenReturn(List.of(new PostCount(POST, 9L)));

        assertThat(mutate(like)).isEqualTo(new LikeState(like, 9));
        verify(auth).currentUser();
        verify(auth, never()).requireVerifiedEmail(anyLong());
        verifyNoMoreInteractions(auth);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void FR001_wrongRoleIsRejectedBeforeVisibilityOrMutation(boolean like) {
        when(auth.currentUser()).thenReturn(new CurrentUser(USER, Set.of(RoleCode.ADMIN), true));

        assertThatThrownBy(() -> mutate(like)).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(ErrorCode.FORBIDDEN);
            assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        });
        verifyNoInteractions(posts, reactions);
        verify(auth, never()).requireVerifiedEmail(anyLong());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void FR001_authenticationFailurePreventsDatabaseWork(boolean like) {
        when(auth.currentUser()).thenThrow(new ApiException(ErrorCode.UNAUTHENTICATED,
                HttpStatus.UNAUTHORIZED, "Chưa xác thực.", Map.of()));

        assertThatThrownBy(() -> mutate(like)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        verifyNoInteractions(posts, reactions);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void FR028_visibilityMissShortCircuitsDmlAndCount(boolean like) {
        when(posts.findPublished(POST, null, PageRequest.of(0, 1))).thenReturn(List.of());

        assertThatThrownBy(() -> mutate(like)).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(ErrorCode.POST_NOT_FOUND);
            assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(error.getMessage()).isEqualTo("Không tìm thấy bài viết.");
        });
        verifyNoInteractions(reactions);
        verify(posts).findPublished(POST, null, PageRequest.of(0, 1));
        verifyNoMoreInteractions(posts);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void FR028_likeAlwaysReturnsTrueAndDatabaseCountIndependentOfAffectedRows(int affected) {
        published();
        when(reactions.insertPostLike(USER, POST)).thenReturn(affected);
        when(reactions.countLikesByPostIds(Set.of(POST))).thenReturn(List.of(new PostCount(POST, 23L)));

        assertThat(service.like(POST)).isEqualTo(new LikeState(true, 23));
        var order = inOrder(auth, posts, reactions);
        order.verify(auth).currentUser();
        order.verify(posts).findPublished(POST, null, PageRequest.of(0, 1));
        order.verify(reactions).insertPostLike(USER, POST);
        order.verify(reactions).countLikesByPostIds(Set.of(POST));
        verifyNoMoreInteractions(auth, posts, reactions);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void FR029_unlikeAlwaysReturnsFalseAndDatabaseCountIndependentOfAffectedRows(int affected) {
        published();
        when(reactions.deletePostLike(USER, POST)).thenReturn(affected);
        when(reactions.countLikesByPostIds(Set.of(POST))).thenReturn(List.of(new PostCount(POST, 7L)));

        assertThat(service.unlike(POST)).isEqualTo(new LikeState(false, 7));
        var order = inOrder(auth, posts, reactions);
        order.verify(auth).currentUser();
        order.verify(posts).findPublished(POST, null, PageRequest.of(0, 1));
        order.verify(reactions).deletePostLike(USER, POST);
        order.verify(reactions).countLikesByPostIds(Set.of(POST));
        verifyNoMoreInteractions(auth, posts, reactions);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void FR031_emptyAggregateFallsBackToZero(boolean like) {
        published();
        when(reactions.countLikesByPostIds(Set.of(POST))).thenReturn(List.of());

        assertThat(mutate(like)).isEqualTo(new LikeState(like, 0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void FR031_countSelectsOnlyTheRequestedPostAggregate(boolean like) {
        published();
        when(reactions.countLikesByPostIds(Set.of(POST))).thenReturn(
                List.of(new PostCount(POST + 1, 99L), new PostCount(POST, 4L)));

        assertThat(mutate(like).likeCount()).isEqualTo(4);
    }

    @Test
    void FR030_selfLikeAndRepeatedCallsRequireNeitherOwnershipNorQuotaOrPostLock() {
        published();
        when(reactions.countLikesByPostIds(Set.of(POST))).thenReturn(List.of(new PostCount(POST, 1L)));

        for (int call = 0; call < 35; call++) assertThat(service.like(POST)).isEqualTo(new LikeState(true, 1));
        verify(posts, never()).findByIdForUpdate(anyLong());
        verify(auth, never()).requireVerifiedEmail(anyLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {"like", "unlike"})
    void FR031_mutationsDeclareReadCommittedWriteTransactions(String method) throws Exception {
        Transactional transaction = LikeService.class.getMethod(method, long.class)
                .getAnnotation(Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.isolation()).isEqualTo(Isolation.READ_COMMITTED);
        assertThat(transaction.readOnly()).isFalse();
    }

    private LikeState mutate(boolean like) {
        return like ? service.like(POST) : service.unlike(POST);
    }

    private void published() {
        Post ownPost = new Post(USER, PostCategory.QUESTION, "A synthetic question", "Some content", Instant.EPOCH);
        ownPost.setStatus(PostStatus.APPROVED);
        when(posts.findPublished(POST, null, PageRequest.of(0, 1))).thenReturn(List.of(ownPost));
    }
}
