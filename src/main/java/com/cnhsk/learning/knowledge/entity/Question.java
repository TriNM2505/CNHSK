package com.cnhsk.learning.knowledge.entity;

import java.time.Instant;
import java.util.List;

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
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "questions", schema = "learning")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionType qtype;

    @Column(name = "hsk_level", nullable = false)
    private Short hskLevel;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String stem;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "JSONB")
    private List<QuestionOption> options = List.of();

    @Column(name = "correct_key", length = 50)
    private String correctKey;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "audio_url", columnDefinition = "TEXT")
    private String audioUrl;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private QuestionSource source = QuestionSource.IMPORT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionStatus status = QuestionStatus.PENDING_REVIEW;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "reviewed_at", columnDefinition = "TIMESTAMPTZ")
    private Instant reviewedAt;

    @Column(name = "review_reason", columnDefinition = "TEXT")
    private String reviewReason;

    @Column(nullable = false)
    private boolean reusable = true;

    @Column(name = "created_by_request_id")
    private Long createdByRequestId;

    @CreationTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant createdAt;

    @Column(unique = true, length = 50)
    private String code;

    @Column(nullable = false)
    private Short difficulty = (short) 2;

    protected Question() {
    }

    public Question(String code, QuestionType qtype, short hskLevel, String stem) {
        this.code = code;
        this.qtype = qtype;
        this.hskLevel = hskLevel;
        this.stem = stem;
    }

    public Long getId() {
        return id;
    }

    public QuestionType getQtype() {
        return qtype;
    }

    public void setQtype(QuestionType qtype) {
        this.qtype = qtype;
    }

    public Short getHskLevel() {
        return hskLevel;
    }

    public void setHskLevel(Short hskLevel) {
        this.hskLevel = hskLevel;
    }

    public String getStem() {
        return stem;
    }

    public void setStem(String stem) {
        this.stem = stem;
    }

    public List<QuestionOption> getOptions() {
        return options;
    }

    public void setOptions(List<QuestionOption> options) {
        this.options = List.copyOf(options);
    }

    public String getCorrectKey() {
        return correctKey;
    }

    public void setCorrectKey(String correctKey) {
        this.correctKey = correctKey;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public QuestionSource getSource() {
        return source;
    }

    public void setSource(QuestionSource source) {
        this.source = source;
    }

    public QuestionStatus getStatus() {
        return status;
    }

    public void setStatus(QuestionStatus status) {
        this.status = status;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(Long reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getReviewReason() {
        return reviewReason;
    }

    public void setReviewReason(String reviewReason) {
        this.reviewReason = reviewReason;
    }

    public boolean isReusable() {
        return reusable;
    }

    public void setReusable(boolean reusable) {
        this.reusable = reusable;
    }

    public Long getCreatedByRequestId() {
        return createdByRequestId;
    }

    public void setCreatedByRequestId(Long createdByRequestId) {
        this.createdByRequestId = createdByRequestId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Short getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Short difficulty) {
        this.difficulty = difficulty;
    }
}
