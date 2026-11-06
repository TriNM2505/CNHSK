package com.cnhsk.learning.api;

import java.time.Instant;

import com.cnhsk.learning.practice.entity.AttemptRepository;
import org.springframework.stereotype.Component;

@Component
public class ActiveTestApi {
    private final AttemptRepository attempts;

    ActiveTestApi(AttemptRepository attempts) {
        this.attempts = attempts;
    }

    public boolean hasActiveTest(long userId, Instant now) {
        return attempts.existsActiveTest(userId, now);
    }
}
