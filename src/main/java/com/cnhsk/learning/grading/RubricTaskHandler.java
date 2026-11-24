package com.cnhsk.learning.grading;

import java.util.LinkedHashMap;
import java.util.Map;

import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler;
import com.cnhsk.learning.practice.ServedItem;
import org.springframework.stereotype.Component;

/**
 * Keeps the per-criterion score and evidence role that {@link RubricEvidenceRecorder} attaches to each recorded
 * item. The attempt is created only by the server after a teacher grades, so this meta is not client input.
 */
@Component
public class RubricTaskHandler implements PracticeSourceHandler {

    @Override
    public PracticeSource source() {
        return PracticeSource.RUBRIC_TASK;
    }

    @Override
    public Map<String, Object> enrichMeta(ServedItem item, Answer answer) {
        Map<String, Object> meta = new LinkedHashMap<>();
        for (String key : new String[] {"score", "role", "criterion", "requestId"}) {
            Object value = answer.clientMeta().get(key);
            if (value != null) meta.put(key, value);
        }
        return meta;
    }

    @Override
    public Map<String, Object> onFinish(FinishContext context) {
        return Map.of();
    }
}
