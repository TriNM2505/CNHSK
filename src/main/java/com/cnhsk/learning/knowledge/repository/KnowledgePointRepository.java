package com.cnhsk.learning.knowledge.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.cnhsk.learning.knowledge.entity.KnowledgePoint;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgePointRepository extends JpaRepository<KnowledgePoint, Long> {

    Optional<KnowledgePoint> findByCode(String code);

    List<KnowledgePoint> findByCodeIn(Collection<String> codes);
}
