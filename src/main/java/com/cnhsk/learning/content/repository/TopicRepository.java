package com.cnhsk.learning.content.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.cnhsk.learning.content.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    Optional<Topic> findByCode(String code);

    List<Topic> findByCodeIn(Collection<String> codes);
}
