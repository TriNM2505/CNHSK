package com.cnhsk.community.reaction;

import java.util.Map;
import java.util.Set;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.community.post.PostRepository;
import com.cnhsk.community.post.web.LikeState;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LikeService {
    private final AuthApi authApi;
    private final PostRepository posts;
    private final ReactionRepository reactions;

    public LikeService(AuthApi authApi, PostRepository posts, ReactionRepository reactions) {
        this.authApi = authApi;
        this.posts = posts;
        this.reactions = reactions;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LikeState like(long postId) {
        long userId = learnerId();
        requirePublished(postId);
        reactions.insertPostLike(userId, postId);
        return new LikeState(true, likeCount(postId));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LikeState unlike(long postId) {
        long userId = learnerId();
        requirePublished(postId);
        reactions.deletePostLike(userId, postId);
        return new LikeState(false, likeCount(postId));
    }

    private long learnerId() {
        CurrentUser user = authApi.currentUser();
        if (!user.roles().contains(RoleCode.LEARNER)) {
            throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "Bạn không có quyền thực hiện thao tác này.", Map.of());
        }
        return user.userId();
    }

    private void requirePublished(long id) {
        if (posts.findPublished(id, null, PageRequest.of(0, 1)).isEmpty()) {
            throw new ApiException(ErrorCode.POST_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Không tìm thấy bài viết.", Map.of());
        }
    }

    private long likeCount(long postId) {
        return reactions.countLikesByPostIds(Set.of(postId)).stream()
                .filter(row -> row.postId() == postId)
                .mapToLong(row -> row.count()).findFirst().orElse(0L);
    }
}
