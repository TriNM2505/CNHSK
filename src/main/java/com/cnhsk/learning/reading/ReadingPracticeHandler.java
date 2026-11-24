package com.cnhsk.learning.reading;

import java.util.Map;

import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler;
import org.springframework.stereotype.Component;

/** Reading sessions need no extra finish data; scoring and evidence come from the practice engine. */
@Component
public class ReadingPracticeHandler implements PracticeSourceHandler {

    @Override
    public PracticeSource source() {
        return PracticeSource.READING_PRACTICE;
    }

    @Override
    public Map<String, Object> onFinish(FinishContext context) {
        return Map.of();
    }
}
