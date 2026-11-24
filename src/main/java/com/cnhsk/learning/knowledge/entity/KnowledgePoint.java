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

    /** Skills this node is used in (LISTENING, SPEAKING, READING, WRITING); see {@link KnowledgeSkill}. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "skills", nullable = false, columnDefinition = "VARCHAR(10)[]")
    private String[] skills = new String[0];

    @Column(length = 100)
    private String cluster;

    @Column(length = 300)
    private String pattern;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "examples", nullable = false, columnDefinition = "JSONB")
    private KnowledgeExamples examples = KnowledgeExamples.empty();

    @Column(name = "teaching_note", columnDefinition = "TEXT")
    private String teachingNote;

    @Column(length = 300)
    private String source;

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

    public String[] getSkills() { return skills; }
    public void setSkills(String[] skills) { this.skills = skills == null ? new String[0] : skills; }
    public String getCluster() { return cluster; }
    public void setCluster(String cluster) { this.cluster = cluster; }
    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }
    public KnowledgeExamples getExamples() { return examples; }
    public void setExamples(KnowledgeExamples examples) { this.examples = examples == null ? KnowledgeExamples.empty() : examples; }
    public String getTeachingNote() { return teachingNote; }
    public void setTeachingNote(String teachingNote) { this.teachingNote = teachingNote; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
