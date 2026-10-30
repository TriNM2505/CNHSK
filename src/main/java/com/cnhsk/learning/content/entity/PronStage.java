package com.cnhsk.learning.content.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "pron_stages", schema = "learning")
public class PronStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stage_index", nullable = false, unique = true)
    private Short stageIndex;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "description", nullable = true, columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_sounds", nullable = false, columnDefinition = "JSONB")
    private List<String> targetSounds = List.of();

    @Column(name = "pass_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal passPercent = new BigDecimal("80.00");

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "units", nullable = false, columnDefinition = "JSONB")
    private List<PronUnit> units = List.of();

    @CreationTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant createdAt;

    @UpdateTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant updatedAt;

    protected PronStage() {
    }

    public PronStage(short stageIndex, String name) {
        this.stageIndex = stageIndex;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public Short getStageIndex() {
        return stageIndex;
    }

    public void setStageIndex(Short stageIndex) {
        this.stageIndex = stageIndex;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getTargetSounds() {
        return targetSounds;
    }

    public void setTargetSounds(List<String> targetSounds) {
        this.targetSounds = targetSounds;
    }

    public BigDecimal getPassPercent() {
        return passPercent;
    }

    public void setPassPercent(BigDecimal passPercent) {
        this.passPercent = passPercent;
    }

    public List<PronUnit> getUnits() {
        return units;
    }

    public void setUnits(List<PronUnit> units) {
        this.units = units;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
