package com.cnhsk.learning.content.entity;

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
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "lexemes", schema = "learning")
public class Lexeme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private LexemeKind kind;

    @Column(name = "text", nullable = false, length = 200)
    private String text;

    @Column(name = "pinyin", nullable = true, length = 200)
    private String pinyin;

    @Column(name = "pinyin_norm", nullable = true, length = 200)
    private String pinyinNorm;

    @Column(name = "sino_viet", nullable = true, length = 200)
    private String sinoViet;

    @Column(name = "meaning_vi", nullable = false, columnDefinition = "TEXT")
    private String meaningVi;

    @Column(name = "meaning_norm", nullable = true, columnDefinition = "TEXT")
    private String meaningNorm;

    @Column(name = "hsk_level")
    private Short hskLevel;

    @Column(name = "audio_url", nullable = true, columnDefinition = "TEXT")
    private String audioUrl;

    @Column(name = "radical", nullable = true, length = 10)
    private String radical;

    @Column(name = "stroke_count")
    private Short strokeCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra", nullable = false, columnDefinition = "JSONB")
    private LexemeExtra extra = LexemeExtra.empty();

    @Column(nullable = false)
    private int frequency = 0;

    @CreationTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant createdAt;

    protected Lexeme() {
    }

    public Lexeme(LexemeKind kind, String text, String meaningVi) {
        this.kind = kind;
        this.text = text;
        this.meaningVi = meaningVi;
    }

    public Long getId() {
        return id;
    }

    public LexemeKind getKind() {
        return kind;
    }

    public void setKind(LexemeKind kind) {
        this.kind = kind;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getPinyin() {
        return pinyin;
    }

    public void setPinyin(String pinyin) {
        this.pinyin = pinyin;
    }

    public String getPinyinNorm() {
        return pinyinNorm;
    }

    public void setPinyinNorm(String pinyinNorm) {
        this.pinyinNorm = pinyinNorm;
    }

    public String getSinoViet() {
        return sinoViet;
    }

    public void setSinoViet(String sinoViet) {
        this.sinoViet = sinoViet;
    }

    public String getMeaningVi() {
        return meaningVi;
    }

    public void setMeaningVi(String meaningVi) {
        this.meaningVi = meaningVi;
    }

    public String getMeaningNorm() {
        return meaningNorm;
    }

    public void setMeaningNorm(String meaningNorm) {
        this.meaningNorm = meaningNorm;
    }

    public Short getHskLevel() {
        return hskLevel;
    }

    public void setHskLevel(Short hskLevel) {
        this.hskLevel = hskLevel;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public String getRadical() {
        return radical;
    }

    public void setRadical(String radical) {
        this.radical = radical;
    }

    public Short getStrokeCount() {
        return strokeCount;
    }

    public void setStrokeCount(Short strokeCount) {
        this.strokeCount = strokeCount;
    }

    public LexemeExtra getExtra() {
        return extra;
    }

    public void setExtra(LexemeExtra extra) {
        this.extra = extra;
    }

    public int getFrequency() {
        return frequency;
    }

    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
