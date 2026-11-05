package com.cnhsk.community.post;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import com.cnhsk.community.common.PostCategory;
import com.cnhsk.community.common.PostStatus;
import org.springframework.data.domain.Pageable;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("select p from Post p where p.authorId = :authorId "
            + "and (:id is null or p.id = :id) and (:status is null or p.status = :status) "
            + "order by p.updatedAt desc, p.id desc")
    List<Post> findOwned(@Param("authorId") long authorId, @Param("id") Long id,
            @Param("status") PostStatus status, Pageable pageable);

    @Query("select count(p) from Post p where p.authorId = :authorId "
            + "and (:status is null or p.status = :status)")
    long countOwned(@Param("authorId") long authorId, @Param("status") PostStatus status);

    @Query("select p from Post p where p.status = com.cnhsk.community.common.PostStatus.APPROVED "
            + "and (:id is null or p.id = :id) and (:category is null or p.category = :category) "
            + "order by p.publishedAt desc, p.id desc")
    List<Post> findPublished(@Param("id") Long id, @Param("category") PostCategory category, Pageable pageable);

    @Query("select count(p) from Post p where p.status = com.cnhsk.community.common.PostStatus.APPROVED "
            + "and (:category is null or p.category = :category)")
    long countPublished(@Param("category") PostCategory category);

    long countByAuthorIdAndCreatedAtGreaterThanEqual(long authorId, Instant createdAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select post from Post post where post.id = :id")
    Optional<Post> findByIdForUpdate(@Param("id") long id);

    @Query("select count(p) from Post p where p.status = com.cnhsk.community.common.PostStatus.PENDING")
    long countPending();

    @Query("select p from Post p where p.status = com.cnhsk.community.common.PostStatus.PENDING "
            + "order by p.createdAt asc, p.id asc")
    List<Post> findPending(Pageable pageable);

    @Query("select p.authorId from Post p where p.id = :id")
    Optional<Long> findModerationAuthorId(@Param("id") long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE community.posts
            SET status = 'APPROVED', reviewed_by = :moderatorId, reviewed_at = :reviewedAt,
                published_at = COALESCE(published_at, :reviewedAt)
            WHERE id = :id AND status = 'PENDING'
            """, nativeQuery = true)
    int approvePending(@Param("id") long id, @Param("moderatorId") long moderatorId,
            @Param("reviewedAt") Instant reviewedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE community.posts
            SET status = 'REJECTED', review_reason = :reason,
                reviewed_by = :moderatorId, reviewed_at = :reviewedAt
            WHERE id = :id AND status = 'PENDING'
            """, nativeQuery = true)
    int rejectPending(@Param("id") long id, @Param("moderatorId") long moderatorId,
            @Param("reason") String reason, @Param("reviewedAt") Instant reviewedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE community.posts
            SET status = 'HIDDEN', reviewed_by = :moderatorId, reviewed_at = :reviewedAt, review_reason = :note
            WHERE id = :id AND status <> 'HIDDEN'
            """, nativeQuery = true)
    int hideForReport(@Param("id") long id, @Param("moderatorId") long moderatorId,
            @Param("reviewedAt") Instant reviewedAt, @Param("note") String note);
}
