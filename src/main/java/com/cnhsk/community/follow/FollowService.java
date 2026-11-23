package com.cnhsk.community.follow;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.PublicProfile;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.community.common.PostStatus;
import com.cnhsk.community.follow.web.CommunityProfile;
import com.cnhsk.community.follow.web.FollowState;
import com.cnhsk.community.post.PostRepository;
import com.cnhsk.community.reaction.ReactionRepository;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Following between learners, stored as {@code reactions(USER, FOLLOW)}. Banned accounts look the same as missing ones
 * and never count towards follower numbers.
 */
@Service
public class FollowService {
    static final int FOLLOW_LIMIT = 1_000;

    private final AuthApi authApi;
    private final ReactionRepository reactions;
    private final PostRepository posts;

    public FollowService(AuthApi authApi, ReactionRepository reactions, PostRepository posts) {
        this.authApi = authApi;
        this.reactions = reactions;
        this.posts = posts;
    }

    @Transactional(readOnly = true)
    public CommunityProfile profile(long id) {
        long viewerId = learnerId();
        Set<Long> banned = authApi.bannedUserIds();
        PublicProfile profile = visibleProfile(id, banned);
        Set<Long> excluded = excluded(banned);
        return new CommunityProfile(id, profile.displayName(), profile.avatarUrl(),
                reactions.countFollowers(id, excluded), reactions.countFollowing(id, excluded),
                posts.countByAuthorIdAndStatus(id, PostStatus.APPROVED),
                viewerId != id && reactions.isFollowing(viewerId, id), viewerId == id);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FollowState follow(long id) {
        long viewerId = learnerId();
        if (viewerId == id) {
            throw new ApiException(ErrorCode.SELF_FOLLOW, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Bạn không thể tự theo dõi chính mình.", Map.of());
        }
        Set<Long> banned = authApi.bannedUserIds();
        visibleProfile(id, banned);
        if (!reactions.isFollowing(viewerId, id) && reactions.countFollowing(viewerId, Set.of(0L)) >= FOLLOW_LIMIT) {
            throw new ApiException(ErrorCode.FOLLOW_LIMIT_REACHED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Bạn đã theo dõi đủ " + FOLLOW_LIMIT + " người.", Map.of());
        }
        reactions.insertUserFollow(viewerId, id);
        return new FollowState(true, reactions.countFollowers(id, excluded(banned)));
    }

    /** Always succeeds, so a learner can stop following someone who was banned meanwhile. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FollowState unfollow(long id) {
        long viewerId = learnerId();
        reactions.deleteUserFollow(viewerId, id);
        return new FollowState(false, reactions.countFollowers(id, excluded(authApi.bannedUserIds())));
    }

    private PublicProfile visibleProfile(long id, Set<Long> banned) {
        PublicProfile profile = id > 0 && !banned.contains(id) ? authApi.publicProfiles(Set.of(id)).get(id) : null;
        if (profile == null) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Không tìm thấy người dùng.", Map.of());
        }
        return profile;
    }

    /** JPQL {@code not in} needs a non-empty list; 0 is never a user id. */
    private static Set<Long> excluded(Set<Long> banned) {
        Set<Long> result = new HashSet<>(banned);
        result.add(0L);
        return result;
    }

    private long learnerId() {
        CurrentUser user = authApi.currentUser();
        if (!user.roles().contains(RoleCode.LEARNER)) {
            throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "Bạn không có quyền thực hiện thao tác này.", Map.of());
        }
        return user.userId();
    }
}
