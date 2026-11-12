package com.cnhsk.learning.review;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface LexemeScheduleRepository extends Repository<LexemeSchedule, LexemeSchedule.Key> {

    @Query("SELECT schedule FROM LexemeSchedule schedule WHERE schedule.userId = :userId "
            + "AND schedule.targetType = 'LEXEME'")
    List<LexemeSchedule> findByUserId(@Param("userId") long userId);

    @Query("SELECT schedule FROM LexemeSchedule schedule WHERE schedule.userId = :userId "
            + "AND schedule.targetId = :targetId AND schedule.targetType = 'LEXEME'")
    Optional<LexemeSchedule> findByUserIdAndTargetId(@Param("userId") long userId, @Param("targetId") long targetId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT schedule FROM LexemeSchedule schedule WHERE schedule.userId = :userId "
            + "AND schedule.targetId = :targetId AND schedule.targetType = 'LEXEME'")
    Optional<LexemeSchedule> lockByUserIdAndTargetId(@Param("userId") long userId,
            @Param("targetId") long targetId);
}
