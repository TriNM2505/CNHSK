package com.cnhsk.learning.exam;

import java.util.Map;

import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import org.springframework.stereotype.Component;

@Component
public class TargetedPracticeHandler implements PracticeSourceHandler {
    private final AttemptRepository attempts;

    public TargetedPracticeHandler(AttemptRepository attempts) {
        this.attempts = attempts;
    }

    @Override
    public PracticeSource source() {
        return PracticeSource.TARGETED_PRACTICE;
    }

    @Override
    public Map<String, Object> onFinish(FinishContext context) {
        var attempt = attempts.findById(context.sessionId()).orElseThrow();
        var kid = attempt.getServedItems().path("meta").path("kid");
        return kid.isTextual() ? Map.of("kid", kid.textValue()) : Map.of();
    }
}
