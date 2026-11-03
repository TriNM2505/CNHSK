package com.cnhsk.community.reaction;

import java.util.List;
import java.util.Set;
import com.cnhsk.community.common.PostCount;
import com.cnhsk.community.common.ReactionKind;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReactionRepository extends JpaRepository<Reaction, ReactionId> {
    @Query("select r.id.kind from Reaction r where r.id.userId = :userId and r.id.targetId = :postId "
            + "and r.id.targetType = com.cnhsk.community.common.TargetType.POST "
            + "and r.id.kind in (com.cnhsk.community.common.ReactionKind.LIKE, "
            + "com.cnhsk.community.common.ReactionKind.REPORT)")
    List<ReactionKind> findPostViewerKinds(@Param("userId") long userId, @Param("postId") long postId);
    @Query("select new com.cnhsk.community.common.PostCount(r.id.targetId, count(r)) from Reaction r "
            + "where r.id.targetId in :postIds "
            + "and r.id.targetType = com.cnhsk.community.common.TargetType.POST "
            + "and r.id.kind = com.cnhsk.community.common.ReactionKind.LIKE group by r.id.targetId")
    List<PostCount> countLikesByPostIds(@Param("postIds") Set<Long> postIds);
}
