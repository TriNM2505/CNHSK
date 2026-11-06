package com.cnhsk.learning.mastery;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.cnhsk.learning.practice.entity.Attempt;
import com.cnhsk.learning.practice.entity.AttemptItem;
import com.cnhsk.learning.mastery.EvidenceAllocator.AllocatedEvidence;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Replays one learner's submitted practice history into KNOWLEDGE_POINT progress. */
@Component
public class MasteryRebuilder {

    private final EntityManager entityManager;
    private final JdbcTemplate jdbc;
    private final EvidenceAllocator allocator;
    private final MasteryUpdater updater;

    public MasteryRebuilder(EntityManager entityManager, JdbcTemplate jdbc,
            EvidenceAllocator allocator, MasteryUpdater updater) {
        this.entityManager = entityManager;
        this.jdbc = jdbc;
        this.allocator = allocator;
        this.updater = updater;
    }

    /** Rebuilds all mastery progress for a user atomically; intended for tests and maintenance only. */
    @Transactional
    public void rebuild(long userId) {
        updater.lockUser(userId);

        List<Attempt> attempts = entityManager.createQuery("SELECT attempt FROM Attempt attempt "
                + "WHERE attempt.userId = :userId AND attempt.status = 'SUBMITTED' "
                + "ORDER BY attempt.submittedAt ASC, attempt.id ASC", Attempt.class)
                .setParameter("userId", userId).getResultList();
        Map<Long, List<AttemptItem>> itemsByAttempt = loadItems(attempts);

        jdbc.update("DELETE FROM learning.user_progress "
                + "WHERE user_id = ? AND target_type = 'KNOWLEDGE_POINT'", userId);

        for (Attempt attempt : attempts) {
            if (attempt.getSubmittedAt() == null) {
                throw new IllegalStateException("Submitted attempt has no submitted_at timestamp");
            }
            List<AllocatedEvidence> evidence = allocator.allocateNew(attempt,
                    itemsByAttempt.getOrDefault(attempt.getId(), List.of()));
            updater.update(userId, evidence, attempt.getSubmittedAt());
        }
    }

    private Map<Long, List<AttemptItem>> loadItems(List<Attempt> attempts) {
        if (attempts.isEmpty()) {
            return Map.of();
        }
        List<Long> attemptIds = attempts.stream().map(Attempt::getId).toList();
        return entityManager.createQuery("SELECT item FROM AttemptItem item WHERE item.attemptId IN :ids "
                + "ORDER BY item.attemptId ASC, item.orderIndex ASC", AttemptItem.class)
                .setParameter("ids", attemptIds).getResultList().stream()
                .collect(Collectors.groupingBy(AttemptItem::getAttemptId));
    }
}
