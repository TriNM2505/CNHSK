package com.cnhsk.community.contest;

import java.time.Clock;

import com.cnhsk.learning.api.ExamUsageGuard;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ContestExamUsageGuard implements ExamUsageGuard {
    private final ContestRepository contests;
    private final Clock clock;

    public ContestExamUsageGuard(ContestRepository contests, Clock clock) {
        this.contests = contests;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isInUse(long examId) {
        return contests.examInUse(examId, clock.instant());
    }
}
