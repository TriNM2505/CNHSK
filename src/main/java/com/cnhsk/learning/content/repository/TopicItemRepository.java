package com.cnhsk.learning.content.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.cnhsk.learning.content.entity.TopicItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicItemRepository extends JpaRepository<TopicItem, Long> {

    Optional<TopicItem> findByTopicIdAndLexemeId(Long topicId, Long lexemeId);

    List<TopicItem> findByTopicIdInAndLexemeIdIn(Collection<Long> topicIds, Collection<Long> lexemeIds);
}
