package com.cnhsk.learning.review;

import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.mastery.EvidenceAllocator;
import com.cnhsk.learning.mastery.MasteryUpdater;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler;
import com.cnhsk.learning.practice.PracticeSourceHandler.Answer;
import com.cnhsk.learning.practice.entity.Attempt;
import com.cnhsk.learning.practice.entity.AttemptItem;
import com.cnhsk.learning.practice.entity.AttemptItemRepository;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.learning.practice.event.ItemGradedEvent;
import com.cnhsk.learning.review.FsrsScheduler.Memory;
import com.cnhsk.learning.review.FsrsScheduler.Rating;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Applies each due-review answer to FSRS and, for choice questions, mastery evidence. */
@Component
public class ReviewHandler implements PracticeSourceHandler {

    private static final Logger log = LoggerFactory.getLogger(ReviewHandler.class);

    private final AttemptRepository attempts;
    private final AttemptItemRepository items;
    private final LexemeScheduleRepository schedules;
    private final FsrsConfig config;
    private final ObjectProvider<FsrsScheduler> schedulers;
    private final Clock clock;
    private final EvidenceAllocator allocator;
    private final MasteryUpdater mastery;

    public ReviewHandler(AttemptRepository attempts, AttemptItemRepository items,
            LexemeScheduleRepository schedules, FsrsConfig config, ObjectProvider<FsrsScheduler> schedulers, Clock clock,
            EvidenceAllocator allocator, MasteryUpdater mastery) {
        this.attempts = attempts;
        this.items = items;
        this.schedules = schedules;
        this.config = config;
        this.schedulers = schedulers;
        this.clock = clock;
        this.allocator = allocator;
        this.mastery = mastery;
    }

    @Override
    public PracticeSource source() {
        return PracticeSource.DUE_REVIEW;
    }

    @Override
    public void validateAnswer(com.cnhsk.learning.practice.ServedItem item, Answer answer) {
        if (item.type() != ItemType.FLASHCARD_RATING || answer.skipped()) {
            return;
        }
        try {
            Rating.valueOf(answer.answer());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Mức tự đánh giá không hợp lệ.", Map.of("field", "answer"));
        }
    }

    @Override
    public Map<String, Object> onFinish(FinishContext context) {
        return Map.of();
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onItemGraded(ItemGradedEvent event) {
        if (event.source() != PracticeSource.DUE_REVIEW) {
            return;
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Review handler requires the answer transaction");
        }
        Attempt attempt = attempts.findById(event.sessionId())
                .orElseThrow(() -> new IllegalStateException("Review attempt does not exist"));
        if (attempt.getUserId() != event.userId()
                || !PracticeSource.DUE_REVIEW.name().equals(attempt.getRefType())) {
            throw new IllegalStateException("Review attempt does not match the event owner or source");
        }
        AttemptItem item = items.findByAttemptIdOrderByOrderIndexAsc(event.sessionId()).stream()
                .filter(candidate -> candidate.getOrderIndex() == event.no())
                .findFirst().orElseThrow(() -> new IllegalStateException("Graded review item does not exist"));
        if (Boolean.TRUE.equals(item.getMeta().get("skipped"))) {
            return;
        }

        Rating rating = rating(event, item);
        if (event.lexemeId() == null) {
            throw new IllegalStateException("Review item has no lexeme id");
        }
        var schedule = schedules.lockByUserIdAndTargetId(event.userId(), event.lexemeId()).orElse(null);
        if (schedule == null) {
            log.warn("Skipping orphaned review item userId={} targetId={}", event.userId(), event.lexemeId());
            return;
        }

        var now = clock.instant();
        FsrsScheduler scheduler = schedulers.getIfAvailable(() -> new FsrsScheduler(config.settings()));
        var result = scheduler.review(new Memory(schedule.getStability(), schedule.getDifficulty(),
                schedule.getLastSeenAt(), schedule.getLapseCount()), rating, now);
        schedule.setStability(result.stability().setScale(3, RoundingMode.HALF_UP));
        schedule.setDifficulty(result.difficulty().setScale(2, RoundingMode.HALF_UP));
        schedule.setNextReviewAt(result.nextReviewAt());
        schedule.setLapseCount(result.lapseCount());
        schedule.setReviewCount(Math.incrementExact(schedule.getReviewCount()));
        schedule.setLastSeenAt(now);

        if (isChoice(event.itemType())) {
            mastery.update(event.userId(), allocator.allocateNew(attempt, List.of(item)));
        }
    }

    private static Rating rating(ItemGradedEvent event, AttemptItem item) {
        if (event.itemType() == ItemType.FLASHCARD_RATING) {
            return Rating.valueOf(item.getAnswer());
        }
        if (isChoice(event.itemType()) && event.correct() != null) {
            return event.correct() ? Rating.GOOD : Rating.AGAIN;
        }
        throw new IllegalStateException("Unsupported or ungraded due-review item");
    }

    private static boolean isChoice(ItemType type) {
        return type == ItemType.MEANING_CHOICE || type == ItemType.CHAR_MEANING_CHOICE;
    }
}
