package com.cnhsk.community.moderation;

import java.time.Instant;
import java.util.List;

import com.cnhsk.community.common.CaseStatus;
import com.cnhsk.community.common.TargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "moderation_cases", schema = "community")
public class ModerationCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false, length = 20)
    private TargetType targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private Long targetId;

    @Column(name = "report_count", nullable = false)
    private int reportCount = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reasons", nullable = false, columnDefinition = "JSONB")
    private List<ReportEntry> reasons = List.of();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CaseStatus status = CaseStatus.PENDING;

    @Column(name = "auto_hidden", nullable = false)
    private boolean autoHidden = false;

    @Column(name = "handled_by")
    private Long handledBy;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "handled_at", columnDefinition = "TIMESTAMPTZ")
    private Instant handledAt;

    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant createdAt;

    protected ModerationCase() {
    }

    public ModerationCase(TargetType targetType, Long targetId, Instant createdAt) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public TargetType getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public int getReportCount() {
        return reportCount;
    }

    public void setReportCount(int reportCount) {
        this.reportCount = reportCount;
    }

    public List<ReportEntry> getReasons() {
        return reasons;
    }

    public void setReasons(List<ReportEntry> reasons) {
        this.reasons = List.copyOf(reasons);
    }

    public CaseStatus getStatus() {
        return status;
    }

    public void setStatus(CaseStatus status) {
        this.status = status;
    }

    public boolean isAutoHidden() {
        return autoHidden;
    }

    public void setAutoHidden(boolean autoHidden) {
        this.autoHidden = autoHidden;
    }

    public Long getHandledBy() {
        return handledBy;
    }

    public void setHandledBy(Long handledBy) {
        this.handledBy = handledBy;
    }

    public Instant getHandledAt() {
        return handledAt;
    }

    public void setHandledAt(Instant handledAt) {
        this.handledAt = handledAt;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public void setDecisionNote(String decisionNote) {
        this.decisionNote = decisionNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
