package com.cnhsk.learning.knowledge.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.cnhsk.learning.knowledge.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    Optional<Question> findByCode(String code);

    List<Question> findByCodeIn(Collection<String> codes);
}
