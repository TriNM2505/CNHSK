package com.cnhsk.learning.knowledge.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/** An error a teacher can tag (appendix D). It always points at the one node the error shows is not yet solid. */
@Entity
@Table(name = "error_codes", schema = "learning")
public class ErrorCode {

    @Id
    @Column(length = 12)
    private String code;

    /** Null only for communicative codes (E-NL-*), which land on the NL node of the graded task. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "kid_id")
    private KnowledgePoint kid;

    /** 1 minor (feedback only), 2 moderate, 3 serious; 2 and 3 become zero-score evidence. */
    @Column(nullable = false)
    private short severity;

    @Column(name = "name_vi", nullable = false, length = 200)
    private String nameVi;

    @Column(length = 300)
    private String example;

    @Column(name = "fix_hint", length = 300)
    private String fixHint;

    @Column(columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant createdAt;

    @UpdateTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant updatedAt;

    protected ErrorCode() {
    }

    public ErrorCode(String code, KnowledgePoint kid, short severity, String nameVi) {
        this.code = code;
        this.kid = kid;
        this.severity = severity;
        this.nameVi = nameVi;
    }

    /** Whether a tagged occurrence becomes evidence; minor errors only give feedback. */
    public boolean countsAsEvidence() { return severity >= 2; }

    public String getCode() { return code; }
    public KnowledgePoint getKid() { return kid; }
    public void setKid(KnowledgePoint kid) { this.kid = kid; }
    public short getSeverity() { return severity; }
    public void setSeverity(short severity) { this.severity = severity; }
    public String getExample() { return example; }
    public void setExample(String example) { this.example = example; }
    public String getFixHint() { return fixHint; }
    public void setFixHint(String fixHint) { this.fixHint = fixHint; }
    public String getNameVi() { return nameVi; }
    public void setNameVi(String nameVi) { this.nameVi = nameVi; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
