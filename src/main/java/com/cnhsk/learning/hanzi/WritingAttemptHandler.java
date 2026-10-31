package com.cnhsk.learning.hanzi;

import java.util.Map;

import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler;
import com.cnhsk.learning.practice.ServedItem;
import org.springframework.stereotype.Component;

@Component
public class WritingAttemptHandler implements PracticeSourceHandler {

    @Override
    public PracticeSource source() {
        return PracticeSource.CHAR_WRITING;
    }

    @Override
    public Map<String, Object> enrichMeta(ServedItem item, Answer answer) {
        return answer.clientMeta();
    }

    @Override
    public Map<String, Object> onFinish(FinishContext context) {
        return Map.of();
    }
}
