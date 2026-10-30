package com.cnhsk.learning.practice;

import java.time.Clock;

import jakarta.persistence.EntityManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Expiration only changes session status; recorded answers and drafts remain evidence. */
@Component
public class PracticeMaintenance {

    private final EntityManager entityManager;
    private final Clock clock;

    public PracticeMaintenance(EntityManager entityManager, Clock clock) {
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Scheduled(cron = "${cnhsk.practice.expire-cron:0 5 * * * *}", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void expireSessions() {
        entityManager.flush();
        // PostgreSQL rechecks the predicate after waiting for a concurrent session row lock.
        entityManager.createNativeQuery("update learning.attempts set status = 'EXPIRED' "
                        + "where status = 'IN_PROGRESS' and expires_at < :now")
                .setParameter("now", clock.instant()).executeUpdate();
        entityManager.clear();
    }
}
