package com.cnhsk.learning.vocab;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class TopicStudyHandler implements PracticeSourceHandler {

    @Override
    public PracticeSource source() {
        return PracticeSource.TOPIC_STUDY;
    }

    @Override
    public void validateAnswer(ServedItem item, Answer answer) {
        if (!answer.skipped() && !answer.audioFailed()
                && !"LEARNED".equals(answer.answer()) && !"KNOWN".equals(answer.answer())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Dữ liệu không hợp lệ.", Map.of("details", List.of(Map.of("field", "answer",
                            "message", "Chỉ chấp nhận LEARNED hoặc KNOWN."))));
        }
    }

    @Override
    public Map<String, Object> enrichMeta(ServedItem item, Answer answer) {
        var meta = new LinkedHashMap<String, Object>();
        if (!answer.skipped() && !answer.audioFailed()) {
            if ("KNOWN".equals(answer.answer())) meta.put("selfDeclared", true);
            if (answer.durationMs() < 1000) meta.put("suspicious", true);
        }
        return meta;
    }

    @Override
    public Map<String, Object> onFinish(FinishContext context) {
        return Map.of();
    }
}
