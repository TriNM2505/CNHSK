package com.cnhsk.learning.exam;

import java.util.Map;

import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import org.springframework.stereotype.Component;

@Component
public class PlacementHandler implements PracticeSourceHandler {
    private final AttemptRepository attempts;

    public PlacementHandler(AttemptRepository attempts) {
        this.attempts = attempts;
    }

    @Override
    public PracticeSource source() {
        return PracticeSource.PLACEMENT;
    }

    @Override
    public Map<String, Object> onFinish(FinishContext context) {
        var attempt = attempts.findById(context.sessionId()).orElseThrow();
        var placement = attempt.getServedItems().path("meta").path("placement");
        if (placement.isMissingNode()) return Map.of();
        return Map.of("runId", PlacementService.runId(context.sessionId(), context.refId()),
                "block", placement.path("block").intValue());
    }
}
