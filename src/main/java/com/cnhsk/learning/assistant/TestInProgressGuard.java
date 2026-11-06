package com.cnhsk.learning.assistant;

import java.time.Instant;

import com.cnhsk.learning.api.ActiveTestApi;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class TestInProgressGuard {
    private final ActiveTestApi activeTests;

    public TestInProgressGuard(ActiveTestApi activeTests) {
        this.activeTests = activeTests;
    }

    public void check(long userId, Instant now) {
        if (activeTests.hasActiveTest(userId, now)) {
            throw new ApiException(ErrorCode.ASSISTANT_BLOCKED_DURING_TEST, HttpStatus.FORBIDDEN,
                    "Trợ lý tắt trong lúc làm bài.", java.util.Map.of());
        }
    }
}
