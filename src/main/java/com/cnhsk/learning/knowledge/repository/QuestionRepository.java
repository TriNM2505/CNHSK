package com.cnhsk.learning.knowledge.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.cnhsk.learning.knowledge.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    Optional<Question> findByCode(String code);

    List<Question> findByCodeIn(Collection<String> codes);

    // Explicit FOR UPDATE also conflicts with the FK key-share lock of a newly recorded attempt item.
    @Query(value = "SELECT * FROM learning.questions WHERE code = :code FOR UPDATE", nativeQuery = true)
    Optional<Question> findByCodeForUpdate(@Param("code") String code);

    @Query("select distinct item.questionId from AttemptItem item where item.questionId in :ids")
    Set<Long> usedQuestionIds(@Param("ids") Collection<Long> ids);
}
