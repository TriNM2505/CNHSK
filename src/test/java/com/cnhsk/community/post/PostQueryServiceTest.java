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
    private PostQueryService service;

    @BeforeEach
    void setUp() {
        posts = mock(PostRepository.class);
        comments = mock(CommentRepository.class);
        reactions = mock(ReactionRepository.class);
        authors = mock(CommunityAuthors.class);
        service = new PostQueryService(posts, comments, reactions, authors);
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
