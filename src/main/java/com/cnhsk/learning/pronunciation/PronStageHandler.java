package com.cnhsk.learning.pronunciation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.content.entity.PronStage;
import com.cnhsk.learning.content.repository.PronStageRepository;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PronStageHandler implements PracticeSourceHandler {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final String UPSERT_PROGRESS = """
            INSERT INTO learning.user_progress
                (user_id, target_type, target_id, percent, state, unlocked_at, last_seen_at, updated_at)
            VALUES (:userId, 'PRON_STAGE', :stageId, :accuracy,
                CASE WHEN :accuracy >= :passPercent THEN 'COMPLETED' ELSE 'AVAILABLE' END, :now, :now, :now)
            ON CONFLICT (user_id, target_type, target_id) DO UPDATE
            SET percent = GREATEST(COALESCE(learning.user_progress.percent, 0), EXCLUDED.percent),
                state = CASE WHEN GREATEST(COALESCE(learning.user_progress.percent, 0), EXCLUDED.percent) >= :passPercent
                             THEN 'COMPLETED' ELSE 'AVAILABLE' END,
                last_seen_at = :now, updated_at = :now
            """;
    private static final String OPEN_NEXT = """
            INSERT INTO learning.user_progress
                (user_id, target_type, target_id, percent, state, unlocked_at, last_seen_at, updated_at)
            VALUES (:userId, 'PRON_STAGE', :stageId, NULL, 'AVAILABLE', :now, :now, :now)
            ON CONFLICT (user_id, target_type, target_id) DO NOTHING
            """;

    private final AttemptRepository attempts;
    private final PronStageRepository stages;
    private final EntityManager entityManager;

    public PronStageHandler(AttemptRepository attempts, PronStageRepository stages, EntityManager entityManager) {
        this.attempts = attempts;
        this.stages = stages;
        this.entityManager = entityManager;
    }

    @Override
    public PracticeSource source() {
        return PracticeSource.PRON_STAGE;
    }

    @Override
    public boolean isSuspicious(FinishContext context) {
        long failedAudio = context.items().stream().filter(item -> item.answer().audioFailed()).count();
        return failedAudio * 100 > totalServed(context) * 30L;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<String, Object> onFinish(FinishContext context) {
        int remaining = totalServed(context) - context.items().size();
        if (remaining > 0) {
            throw new ApiException(ErrorCode.STAGE_INCOMPLETE, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Vui lòng hoàn thành tất cả câu trong tầng.", Map.of("remaining", remaining));
        }
        PronStage stage = stages.findById(context.refId()).orElseThrow();
        var next = stages.findByStageIndex((short) (stage.getStageIndex() + 1));
        BigDecimal passPercent = stage.getPassPercent().setScale(2, RoundingMode.HALF_UP);
        long counted = context.items().stream().filter(item -> item.correct() != null).count();
        long correct = context.items().stream().filter(item -> Boolean.TRUE.equals(item.correct())).count();
        BigDecimal accuracy = counted == 0 ? null : BigDecimal.valueOf(correct).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(counted), 2, RoundingMode.HALF_UP);
        boolean passed = accuracy != null && accuracy.compareTo(passPercent) >= 0;
        boolean unlockedNext = false;
        if (accuracy != null) {
            entityManager.createNativeQuery(UPSERT_PROGRESS)
                    .setParameter("userId", context.userId()).setParameter("stageId", stage.getId())
                    .setParameter("accuracy", accuracy).setParameter("passPercent", passPercent)
                    .setParameter("now", context.submittedAt()).executeUpdate();
            if (passed && next.isPresent()) {
                unlockedNext = entityManager.createNativeQuery(OPEN_NEXT)
                        .setParameter("userId", context.userId()).setParameter("stageId", next.get().getId())
                        .setParameter("now", context.submittedAt()).executeUpdate() == 1;
            }
        }
        // Read after the atomic upsert while its row lock is still held by the finish transaction.
        List<?> stored = entityManager.createNativeQuery("SELECT percent FROM learning.user_progress "
                        + "WHERE user_id = :userId AND target_type = 'PRON_STAGE' AND target_id = :stageId")
                .setParameter("userId", context.userId()).setParameter("stageId", stage.getId()).getResultList();
        BigDecimal best = stored.isEmpty() || stored.get(0) == null ? null
                : ((BigDecimal) stored.get(0)).setScale(2, RoundingMode.HALF_UP);
        boolean completedAll = passed && stage.getStageIndex().equals(entityManager
                .createQuery("SELECT MAX(s.stageIndex) FROM PronStage s", Short.class).getSingleResult());
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("stageIndex", stage.getStageIndex());
        extra.put("accuracy", accuracy);
        extra.put("bestAccuracy", best);
        extra.put("passPercent", passPercent);
        extra.put("passed", passed);
        extra.put("unlockedNextStage", unlockedNext);
        extra.put("nextStageIndex", next.map(PronStage::getStageIndex).orElse(null));
        extra.put("needed", accuracy == null ? null : passPercent.subtract(accuracy).max(ZERO)
                .setScale(2, RoundingMode.HALF_UP));
        extra.put("completedAllStages", completedAll);
        extra.put("noScore", accuracy == null);
        return extra;
    }

    private int totalServed(FinishContext context) {
        return attempts.findById(context.sessionId()).orElseThrow().getServedItems().path("items").size();
    }
}
