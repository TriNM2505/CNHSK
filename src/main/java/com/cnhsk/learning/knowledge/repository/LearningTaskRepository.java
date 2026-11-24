package com.cnhsk.learning.knowledge.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.cnhsk.learning.knowledge.entity.LearningTask;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LearningTaskRepository extends JpaRepository<LearningTask, Long> {

    List<LearningTask> findByCodeIn(Collection<String> codes);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM LearningTask t WHERE t.code = :code")
    Optional<LearningTask> findByCodeForUpdate(@Param("code") String code);

    @Query("SELECT t FROM LearningTask t WHERE t.active = true AND t.kind = :kind AND t.hskLevel = :level ORDER BY t.code")
    List<LearningTask> findActive(@Param("kind") LearningTask.Kind kind, @Param("level") short level);
}
