package com.cnhsk.learning.exam;

import java.util.Map;
import java.util.stream.Collectors;

import com.cnhsk.learning.exam.ExamScoring.Snapshot;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class MockExamHandler implements PracticeSourceHandler {
    private final AttemptRepository attempts;
    private final ObjectMapper mapper;

    public MockExamHandler(AttemptRepository attempts, ObjectMapper mapper) {
        this.attempts = attempts;
        this.mapper = mapper;
    }

    @Override
    public PracticeSource source() {
        return PracticeSource.MOCK_EXAM;
    }

    @Override
    public Map<String, Object> onFinish(FinishContext context) {
        // The engine already loaded and locked this attempt in the same transaction.
        var attempt = attempts.findById(context.sessionId()).orElseThrow();
        var exam = attempt.getServedItems().path("meta").path("exam");
        // Engine callers may create MOCK_EXAM sessions without a published exam snapshot.
        if (exam.isMissingNode()) return Map.of();
        var snapshot = mapper.convertValue(exam, Snapshot.class);
        var correct = context.items().stream().filter(item -> Boolean.TRUE.equals(item.correct()))
                .map(item -> item.item().no()).collect(Collectors.toSet());
        var score = ExamScoring.score(snapshot, correct);
        return Map.of("weightedScore", score.score(), "weightedMaxScore", score.maxScore(),
                "weightedPercent", score.percent(), "bySection", score.bySection());
    }
}
