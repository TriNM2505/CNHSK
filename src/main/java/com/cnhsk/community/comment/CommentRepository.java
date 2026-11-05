package com.cnhsk.community.comment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import jakarta.persistence.LockModeType;
import com.cnhsk.community.common.PostCount;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query("select c.postId from Comment c where c.id = :id and c.authorId = :authorId")
    Optional<Long> findOwnedPostId(@Param("id") long id, @Param("authorId") long authorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Comment c where c.id = :id and c.authorId = :authorId and c.postId = :postId")
    Optional<Comment> findOwnedForUpdate(@Param("id") long id, @Param("authorId") long authorId,
            @Param("postId") long postId);

    @Query("select c from Comment c where c.postId = :postId order by c.createdAt asc, c.id asc")
    List<Comment> findTreeByPostId(@Param("postId") long postId);

    long countByAuthorIdAndCreatedAtAfter(long authorId, Instant cutoff);

    @Query("select min(c.createdAt) from Comment c where c.authorId = :authorId and c.createdAt > :cutoff")
    Instant findOldestCreatedAtByAuthorIdAndCreatedAtAfter(@Param("authorId") long authorId,
            @Param("cutoff") Instant cutoff);

    @Query("select c from Comment c, Post p where c.id = :parentId and p.id = c.postId "
            + "and p.status = com.cnhsk.community.common.PostStatus.APPROVED")
    Optional<Comment> findReplyParentOnPublishedPost(@Param("parentId") long parentId);

    @Query("select new com.cnhsk.community.common.PostCount(c.postId, count(c)) from Comment c "
            + "where c.postId in :postIds and c.status = com.cnhsk.community.common.CommentStatus.VISIBLE "
            + "group by c.postId")
    List<PostCount> countVisibleByPostIds(@Param("postIds") Set<Long> postIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE community.comments
            SET status = :status, moderated_by = :moderatorId, moderation_reason = :note
            WHERE id = :id
              AND (status = 'VISIBLE' OR (:status = 'DELETED' AND status = 'HIDDEN'))
            """, nativeQuery = true)
    int moderateForReport(@Param("id") long id, @Param("status") String status,
            @Param("moderatorId") long moderatorId, @Param("note") String note);
}
