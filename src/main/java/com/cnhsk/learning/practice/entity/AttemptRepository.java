package com.cnhsk.learning.practice.entity;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Attempt a where a.id = :id")
    Optional<Attempt> findByIdForUpdate(@Param("id") Long id);

    @Query("select (count(a) > 0) from Attempt a where a.userId = :userId "
            + "and a.status = 'IN_PROGRESS' and a.refType in ('MOCK_EXAM', 'PLACEMENT', 'TOPIC_TEST', 'QUIZ') "
            + "and a.expiresAt > :now")
    boolean existsActiveTest(@Param("userId") long userId, @Param("now") Instant now);
}
