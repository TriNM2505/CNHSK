package com.cnhsk.learning.content.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.cnhsk.learning.content.entity.PronStage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PronStageRepository extends JpaRepository<PronStage, Long> {

    Optional<PronStage> findByStageIndex(Short stageIndex);

    List<PronStage> findByStageIndexIn(Collection<Short> stageIndexes);
}
