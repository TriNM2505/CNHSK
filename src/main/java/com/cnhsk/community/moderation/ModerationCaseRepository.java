package com.cnhsk.community.moderation;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface ModerationCaseRepository extends JpaRepository<ModerationCase, Long> {
    @Modifying
    @Query(value = """
            INSERT INTO community.moderation_cases AS m
                (target_type, target_id, report_count, reasons, status, auto_hidden, created_at)
            VALUES (:targetType, :targetId, 1, CAST(:entry AS jsonb), 'PENDING', false, :reportedAt)
            ON CONFLICT (target_type, target_id) WHERE status = 'PENDING'
            DO UPDATE SET report_count = m.report_count + 1,
                          reasons = m.reasons || EXCLUDED.reasons
            """, nativeQuery = true)
    int upsertPendingReport(@Param("targetType") String targetType, @Param("targetId") long targetId,
            @Param("entry") String entry, @Param("reportedAt") Instant reportedAt);

    @Query("select count(c) from ModerationCase c where c.status = com.cnhsk.community.common.CaseStatus.PENDING")
    long countPending();

    @Query("select c from ModerationCase c where c.status = com.cnhsk.community.common.CaseStatus.PENDING "
            + "order by c.reportCount desc, c.createdAt asc, c.id asc")
    List<ModerationCase> findPending(Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE community.moderation_cases
            SET status = :status, handled_by = :moderatorId, handled_at = :handledAt, decision_note = :note
            WHERE id = :id AND status = 'PENDING'
            """, nativeQuery = true)
    int closePending(@Param("id") long id, @Param("status") String status,
            @Param("moderatorId") long moderatorId, @Param("handledAt") Instant handledAt,
            @Param("note") String note);
}
