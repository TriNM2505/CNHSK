package com.cnhsk.learning.mastery;

import com.cnhsk.learning.practice.entity.Attempt;
import com.cnhsk.learning.practice.entity.AttemptItemRepository;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.learning.practice.event.SessionFinishedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Handles a submitted session before its finish transaction commits. */
@Component
public class MasteryListener {

    private final AttemptRepository attempts;
    private final AttemptItemRepository items;
    private final EvidenceAllocator allocator;
    private final MasteryUpdater updater;

    public MasteryListener(AttemptRepository attempts, AttemptItemRepository items,
            EvidenceAllocator allocator, MasteryUpdater updater) {
        this.attempts = attempts;
        this.items = items;
        this.allocator = allocator;
        this.updater = updater;
    }

    @EventListener
    public void onFinished(SessionFinishedEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Mastery listener requires the finish transaction");
        }
        Attempt attempt = attempts.findById(event.sessionId())
                .orElseThrow(() -> new IllegalStateException("Finished attempt does not exist"));
        if (attempt.getUserId() != event.userId() || !attempt.getRefType().equals(event.source().name())) {
            throw new IllegalStateException("Finished attempt does not match the event owner or source");
        }
        if (event.source().name().equals("DUE_REVIEW")
                || EvidenceAllocator.contextOf(event.source().name()) == null) {
            return;
        }
        updater.update(event.userId(), allocator.allocateNew(attempt,
                items.findByAttemptIdOrderByOrderIndexAsc(event.sessionId())));
    }
}
