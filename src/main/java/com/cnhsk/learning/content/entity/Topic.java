package com.cnhsk.learning.content.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "topics", schema = "learning")
public class Topic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "hsk_level", nullable = false)
    private Short hskLevel;

    @Column(name = "order_index", nullable = false)
    private int orderIndex = 0;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "prereq_ids", nullable = false, columnDefinition = "BIGINT[]")
    private Long[] prereqIds = new Long[0];

    @CreationTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant createdAt;

    protected Topic() {
    }

    public Topic(String code, String name, short hskLevel) {
        this.code = code;
        this.name = name;
        this.hskLevel = hskLevel;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Short getHskLevel() {
        return hskLevel;
    }

    public void setHskLevel(Short hskLevel) {
        this.hskLevel = hskLevel;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public Long[] getPrereqIds() {
        return prereqIds;
    }

    public void setPrereqIds(Long[] prereqIds) {
        this.prereqIds = prereqIds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
