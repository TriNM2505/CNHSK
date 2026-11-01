package com.cnhsk.community.post;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import com.cnhsk.community.common.PostCategory;
import org.springframework.data.domain.Pageable;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

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
}
