package com.cnhsk.learning.knowledge.repository;

import java.util.Collection;
import java.util.List;

import com.cnhsk.learning.knowledge.entity.QuestionKnowledgePoint;
import com.cnhsk.learning.knowledge.entity.QuestionKnowledgePointId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionKnowledgePointRepository extends JpaRepository<QuestionKnowledgePoint, QuestionKnowledgePointId> {

    List<QuestionKnowledgePoint> findByQuestionIdIn(Collection<Long> questionIds);
}
