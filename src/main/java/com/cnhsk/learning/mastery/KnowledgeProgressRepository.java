package com.cnhsk.learning.mastery;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface KnowledgeProgressRepository extends Repository<KnowledgeProgress, KnowledgeProgress.Key> {

    @Query("SELECT progress FROM KnowledgeProgress progress WHERE progress.userId = :userId "
            + "AND progress.targetType = 'KNOWLEDGE_POINT'")
    List<KnowledgeProgress> findByUserId(@Param("userId") long userId);

    @Query("SELECT progress FROM KnowledgeProgress progress WHERE progress.userId = :userId "
            + "AND progress.targetId = :targetId AND progress.targetType = 'KNOWLEDGE_POINT'")
    Optional<KnowledgeProgress> findByUserIdAndTargetId(@Param("userId") long userId, @Param("targetId") long targetId);
}
