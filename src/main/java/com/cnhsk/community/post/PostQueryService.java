package com.cnhsk.community.post;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cnhsk.community.comment.CommentRepository;
import com.cnhsk.community.common.CommunityAuthors;
import com.cnhsk.community.common.PostCategory;
import com.cnhsk.community.common.PostCount;
import com.cnhsk.community.common.UserRef;
import com.cnhsk.community.post.web.PostDetail;
import com.cnhsk.community.post.web.PostSummary;
import com.cnhsk.community.post.web.PostSummaryPage;
import com.cnhsk.community.reaction.ReactionRepository;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PostQueryService {
    private final PostRepository posts;
    private final CommentRepository comments;
    private final ReactionRepository reactions;
    private final CommunityAuthors authors;

    public PostQueryService(PostRepository posts, CommentRepository comments,
            ReactionRepository reactions, CommunityAuthors authors) {
        this.posts = posts;
        this.comments = comments;
        this.reactions = reactions;
        this.authors = authors;
    }

    public PostSummaryPage publishedList(int page, int size, PostCategory category) {
        if (page < 0 || size < 1 || size > 50) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Phân trang không hợp lệ.", Map.of("details", List.of(
                            Map.of("field", "page/size", "message", "Trang phải từ 0, kích thước từ 1 đến 50."))));
        }
        long total = posts.countPublished(category);
        long offset = (long) page * size;
        if (offset >= total) {
            return new PostSummaryPage(List.of(), page, size, total);
        }
        List<Post> found = posts.findPublished(null, category, PageRequest.of(page, size));
        return new PostSummaryPage(summaries(found), page, size, total);
    }

    public PostDetail publishedDetail(long id) {
        Post post = posts.findPublished(id, null, PageRequest.of(0, 1)).stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Không tìm thấy bài viết.", Map.of()));
        PostSummary summary = summaries(List.of(post)).get(0);
        return new PostDetail(summary.id(), summary.title(), summary.category(), summary.excerpt(),
                summary.author(), summary.publishedAt(), summary.commentCount(), summary.likeCount(), post.getContent());
    }

    private List<PostSummary> summaries(List<Post> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = found.stream().map(Post::getId).collect(Collectors.toSet());
        Map<Long, Long> commentCounts = counts(comments.countVisibleByPostIds(ids));
        Map<Long, Long> likeCounts = counts(reactions.countLikesByPostIds(ids));
        Set<Long> authorIds = found.stream().map(Post::getAuthorId).collect(Collectors.toSet());
        Map<Long, UserRef> resolved = authors.resolve(authorIds);
        return found.stream().map(post -> new PostSummary(post.getId(), post.getTitle(), post.getCategory(),
                excerpt(post.getContent()), resolved.getOrDefault(post.getAuthorId(), new UserRef(post.getAuthorId(), null)),
                post.getPublishedAt(), commentCounts.getOrDefault(post.getId(), 0L),
                likeCounts.getOrDefault(post.getId(), 0L))).toList();
    }

    private static Map<Long, Long> counts(List<PostCount> rows) {
        return rows.stream().collect(Collectors.toMap(PostCount::postId, PostCount::count));
    }

    private static String excerpt(String content) {
        int length = Math.min(200, content.codePointCount(0, content.length()));
        return content.substring(0, content.offsetByCodePoints(0, length));
    }
}
