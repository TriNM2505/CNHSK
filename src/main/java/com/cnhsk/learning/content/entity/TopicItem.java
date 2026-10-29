package com.cnhsk.learning.content.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "topic_items", schema = "learning")
public class TopicItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    @Column(name = "lexeme_id")
    private Long lexemeId;

    @Column(name = "knowledge_point_id")
    private Long knowledgePointId;

    @Column(name = "order_index", nullable = false)
    private int orderIndex = 0;

    protected TopicItem() {
    }

    public TopicItem(Long topicId, Long lexemeId) {
        this.topicId = topicId;
        this.lexemeId = lexemeId;
    }

    public Long getId() {
        return id;
    }

    public Long getTopicId() {
        return topicId;
    }

    public void setTopicId(Long topicId) {
        this.topicId = topicId;
    }

    public Long getLexemeId() {
        return lexemeId;
    }

    public void setLexemeId(Long lexemeId) {
        this.lexemeId = lexemeId;
    }

    public Long getKnowledgePointId() {
        return knowledgePointId;
    }

    public void setKnowledgePointId(Long knowledgePointId) {
        this.knowledgePointId = knowledgePointId;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }
}
