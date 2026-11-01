package com.cnhsk.community.post;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    long countByAuthorIdAndCreatedAtGreaterThanEqual(long authorId, Instant createdAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select post from Post post where post.id = :id")
    Optional<Post> findByIdForUpdate(@Param("id") long id);
}
