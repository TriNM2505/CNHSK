package com.cnhsk.community.contest;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "contests", schema = "community")
public class Contest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "exam_id")
    private Long examId;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "starts_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant startsAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "ends_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant endsAt;

    @Column(name = "max_participants")
    private Integer maxParticipants;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "JSONB")
    private List<Prize> prizes = List.of();

    @Column(nullable = false, length = 20)
    private String status;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "ranking_published_at", columnDefinition = "TIMESTAMPTZ")
    private Instant rankingPublishedAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant createdAt;

    protected Contest() { }

    Contest(String name, String description, Long examId, Instant startsAt, Instant endsAt,
            Integer maxParticipants, List<Prize> prizes, long createdBy, Instant createdAt) {
        update(name, description, examId, startsAt, endsAt, maxParticipants, prizes);
        this.status = "DRAFT";
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    void update(String name, String description, Long examId, Instant startsAt, Instant endsAt,
            Integer maxParticipants, List<Prize> prizes) {
        this.name = name;
        this.description = description;
        this.examId = examId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.maxParticipants = maxParticipants;
        this.prizes = List.copyOf(prizes);
    }

    void publish() { status = "PUBLISHED"; }
    void cancel() { status = "CANCELLED"; }
    void publishRanking(Instant now) { rankingPublishedAt = now; }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Long getExamId() { return examId; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public Integer getMaxParticipants() { return maxParticipants; }
    public List<Prize> getPrizes() { return List.copyOf(prizes); }
    public String getStatus() { return status; }
    public Instant getRankingPublishedAt() { return rankingPublishedAt; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }

    public ScheduleState stateAt(Instant now) {
        if (now.isBefore(startsAt)) return ScheduleState.UPCOMING;
        return now.isBefore(endsAt) ? ScheduleState.ONGOING : ScheduleState.ENDED;
    }

    public enum ScheduleState { UPCOMING, ONGOING, ENDED }

    @Schema(requiredProperties = {"fromRank", "toRank", "description"})
    public record Prize(@Min(1) @Schema(minimum = "1") int fromRank, @Min(1) @Schema(minimum = "1") int toRank,
            @NotNull @Size(max = 200) @Schema(maxLength = 200) String description) { }
}
