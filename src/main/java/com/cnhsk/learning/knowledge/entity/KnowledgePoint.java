package com.cnhsk.learning.knowledge.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "knowledge_points", schema = "learning")
public class KnowledgePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "skill_type", length = 20)
    private KnowledgeSkillType skillType;

    @Column(name = "hsk_level", nullable = false)
    private Short hskLevel;

    @Column(name = "lexeme_id")
    private Long lexemeId;

    @CreationTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private KnowledgeDomain domain;

    @Column(name = "name_zh", nullable = false, length = 200)
    private String nameZh;

    @Column(name = "can_do", columnDefinition = "TEXT")
    private String canDo;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "prereq_ids", nullable = false, columnDefinition = "BIGINT[]")
    private Long[] prereqIds = new Long[0];

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "support_ids", nullable = false, columnDefinition = "BIGINT[]")
    private Long[] supportIds = new Long[0];

    @UpdateTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant updatedAt;

    protected KnowledgePoint() {
    }

    public KnowledgePoint(String code, String title, KnowledgeDomain domain, String nameZh, short hskLevel) {
        this.code = code;
        this.title = title;
        this.domain = domain;
        this.nameZh = nameZh;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public KnowledgeSkillType getSkillType() {
        return skillType;
    }

    public void setSkillType(KnowledgeSkillType skillType) {
        this.skillType = skillType;
    }

    public Short getHskLevel() {
        return hskLevel;
    }

    public void setHskLevel(Short hskLevel) {
        this.hskLevel = hskLevel;
    }

    public Long getLexemeId() {
        return lexemeId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public KnowledgeDomain getDomain() {
        return domain;
    }

    public void setDomain(KnowledgeDomain domain) {
        this.domain = domain;
    }

    public String getNameZh() {
        return nameZh;
    }

    public void setNameZh(String nameZh) {
        this.nameZh = nameZh;
    }

    public String getCanDo() {
        return canDo;
    }

    public void setCanDo(String canDo) {
        this.canDo = canDo;
    }

    public Long[] getPrereqIds() {
        return prereqIds;
    }

    public void setPrereqIds(Long[] prereqIds) {
        this.prereqIds = prereqIds;
    }

    public Long[] getSupportIds() {
        return supportIds;
    }

    public void setSupportIds(Long[] supportIds) {
        this.supportIds = supportIds;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
