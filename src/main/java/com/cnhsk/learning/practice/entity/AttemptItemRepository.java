package com.cnhsk.learning.practice.entity;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptItemRepository extends JpaRepository<AttemptItem, Long> {

    List<AttemptItem> findByAttemptIdOrderByOrderIndexAsc(Long attemptId);

    boolean existsByAttemptIdAndOrderIndex(Long attemptId, int orderIndex);
}
