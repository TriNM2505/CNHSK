package com.cnhsk.community.comment;

import java.util.List;
import java.util.Set;
import com.cnhsk.community.common.PostCount;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    @Query("select new com.cnhsk.community.common.PostCount(c.postId, count(c)) from Comment c "
            + "where c.postId in :postIds and c.status = com.cnhsk.community.common.CommentStatus.VISIBLE "
            + "group by c.postId")
    List<PostCount> countVisibleByPostIds(@Param("postIds") Set<Long> postIds);
}
