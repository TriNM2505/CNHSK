package com.cnhsk.learning.contest;

import java.util.Map;

import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler;
import org.springframework.stereotype.Component;

@Component
public class ContestHandler implements PracticeSourceHandler {
    @Override
    public PracticeSource source() {
        return PracticeSource.CONTEST;
    }

    @Override
    public Map<String, Object> onFinish(FinishContext context) {
        return Map.of();
    }
}
