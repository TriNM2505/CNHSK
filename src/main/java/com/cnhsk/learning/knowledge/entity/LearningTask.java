package com.cnhsk.learning.knowledge.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * A speaking or writing task (v2.0 §3.7): one communicative node (NL) plus the target nodes that each rubric
 * criterion feeds. C1 and C5 always land on the NL node, so targets only list C2–C4.
 */
@Entity
@Table(name = "learning_tasks", schema = "learning")
public class LearningTask {

    public enum Kind { SPEAKING, WRITING }

    @Embeddable
    public static class Target {
        @ManyToOne(fetch = FetchType.EAGER, optional = false)
        @JoinColumn(name = "kid_id", nullable = false)
        private KnowledgePoint kid;

        @Column(nullable = false, length = 2)
        private String criterion;

        protected Target() {
        }

        public Target(KnowledgePoint kid, String criterion) {
            this.kid = kid;
            this.criterion = criterion;
        }

        public KnowledgePoint getKid() { return kid; }
        public String getCriterion() { return criterion; }

        String cell() { return criterion + ":" + kid.getCode(); }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind;

    @Column(name = "hsk_level", nullable = false)
    private short hskLevel;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "nl_kid_id", nullable = false)
    private KnowledgePoint nlKid;

    @Column(name = "title_vi", nullable = false, length = 200)
    private String titleVi;

    @Column(name = "prompt_vi", nullable = false, columnDefinition = "TEXT")
    private String promptVi;

    @Column(name = "prompt_zh", columnDefinition = "TEXT")
    private String promptZh;

    @Column(name = "min_chars")
    private Short minChars;

    @Column(name = "max_seconds")
    private Short maxSeconds;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "learning_task_targets", schema = "learning", joinColumns = @JoinColumn(name = "task_id"))
    private List<Target> targets = new ArrayList<>();

    @CreationTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant createdAt;

    @UpdateTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant updatedAt;

    protected LearningTask() {
    }

    public LearningTask(String code) {
        this.code = code;
    }

    /** "C2:NA1-006|C3:TV1-001" sorted by criterion then KID, the canonical import cell. */
    public String targetsCell() {
        return targets.stream().sorted(Comparator.comparing(Target::getCriterion)
                        .thenComparing(target -> target.getKid().getCode()))
                .map(Target::cell).collect(Collectors.joining("|"));
    }

    public Map<String, String> toCsv() {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("code", code);
        v.put("kind", kind.name());
        v.put("hsk_level", Short.toString(hskLevel));
        v.put("nl_kid", nlKid.getCode());
        v.put("title_vi", titleVi);
        v.put("prompt_vi", promptVi);
        if (promptZh != null) v.put("prompt_zh", promptZh);
        v.put("targets", targetsCell());
        if (minChars != null) v.put("min_chars", minChars.toString());
        if (maxSeconds != null) v.put("max_seconds", maxSeconds.toString());
        v.put("is_active", active ? "TRUE" : "FALSE");
        return v;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }
    public short getHskLevel() { return hskLevel; }
    public void setHskLevel(short hskLevel) { this.hskLevel = hskLevel; }
    public KnowledgePoint getNlKid() { return nlKid; }
    public void setNlKid(KnowledgePoint nlKid) { this.nlKid = nlKid; }
    public String getTitleVi() { return titleVi; }
    public void setTitleVi(String titleVi) { this.titleVi = titleVi; }
    public String getPromptVi() { return promptVi; }
    public void setPromptVi(String promptVi) { this.promptVi = promptVi; }
    public String getPromptZh() { return promptZh; }
    public void setPromptZh(String promptZh) { this.promptZh = promptZh; }
    public Short getMinChars() { return minChars; }
    public void setMinChars(Short minChars) { this.minChars = minChars; }
    public Short getMaxSeconds() { return maxSeconds; }
    public void setMaxSeconds(Short maxSeconds) { this.maxSeconds = maxSeconds; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public List<Target> getTargets() { return targets; }
    public void setTargets(List<Target> targets) { this.targets = new ArrayList<>(targets); }
}
