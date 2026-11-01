package com.cnhsk.learning.hanzi;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class WritingChallengeHandler implements PracticeSourceHandler {

    private static final Set<String> ANSWERS = Set.of("COMPLETED", "FAILED", "INCOMPLETE");
    private static final BigDecimal COMPLETED_COEFFICIENT = new BigDecimal("1.2");
    private static final BigDecimal ZERO_COEFFICIENT = BigDecimal.ZERO;

    @Override
    public PracticeSource source() {
        return PracticeSource.WRITING_CHALLENGE;
    }

    @Override
    public void validateFinish(List<Answer> answers) {
        Set<Integer> submittedNos = new HashSet<>();
        for (Answer answer : answers) {
            if (answer == null) {
                continue;
            }
            if (answer.skipped() || answer.audioFailed()) {
                throw validationFailed();
            }
            if (answer.replayCount() != 0) {
                throw validationFailed();
            }
            if (!submittedNos.add(answer.no())) {
                throw resultCountMismatch();
            }
        }
    }

    @Override
    public void validateAnswer(ServedItem item, Answer answer) {
        if (answer.skipped() || answer.audioFailed()) {
            throw validationFailed();
        }
        if (answer.replayCount() != 0) {
            throw validationFailed();
        }
        if (answer.answer() != null && !ANSWERS.contains(answer.answer())) {
            throw validationFailed();
        }

        int strokeCount = strokeCount(item);
        Map<String, Object> clientMeta = answer.clientMeta();
        if (clientMeta.containsKey("accuracy")) {
            BigDecimal accuracy = decimal(clientMeta.get("accuracy"));
            if (accuracy == null || accuracy.compareTo(BigDecimal.ZERO) < 0
                    || accuracy.compareTo(BigDecimal.ONE) > 0) {
                throw validationFailed();
            }
        }
        if (clientMeta.containsKey("strokeMisses")) {
            Integer strokeMisses = integer(clientMeta.get("strokeMisses"));
            if (strokeMisses == null || strokeMisses < 0 || strokeMisses > strokeCount * 10) {
                throw validationFailed();
            }
        }
    }

    @Override
    public Map<String, Object> enrichMeta(ServedItem item, Answer answer) {
        boolean completed = "COMPLETED".equals(answer.answer());
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("mode", "CHALLENGE");
        meta.put("completed", completed);
        meta.put("strokeCount", strokeCount(item));
        meta.put("coefficientProposal", (completed ? COMPLETED_COEFFICIENT : ZERO_COEFFICIENT)
                .setScale(2, RoundingMode.HALF_UP));
        meta.put("usedAnimation", false);
        meta.put("previewCount", 0);
        meta.put("previewMs", 0);
        meta.put("replayCount", 0);
        meta.put("repeatWithin10Min", false);
        meta.put("accuracy", answer.clientMeta().containsKey("accuracy")
                ? decimal(answer.clientMeta().get("accuracy")) : null);
        meta.put("strokeMisses", answer.clientMeta().containsKey("strokeMisses")
                ? integer(answer.clientMeta().get("strokeMisses")) : null);
        return meta;
    }

    @Override
    public Map<String, Object> onFinish(FinishContext context) {
        long failedChars = context.items().stream()
                .filter(item -> "FAILED".equals(item.answer().answer())).count();
        if (failedChars > 3) {
            throw resultCountMismatch();
        }

        for (GradedItem graded : context.items()) {
            if (!"COMPLETED".equals(graded.answer().answer())) {
                continue;
            }
            int strokeCount = strokeCount(graded.item());
            if (graded.answer().durationMs() < strokeCount * 200) {
                throw new ApiException(ErrorCode.IMPOSSIBLE_DURATION, HttpStatus.BAD_REQUEST,
                        "Thời gian hoàn thành chữ không hợp lệ.", Map.of());
            }
        }

        long completedChars = context.items().stream()
                .filter(item -> "COMPLETED".equals(item.answer().answer())).count();
        long incompleteChars = context.items().size() - completedChars - failedChars;
        String finishedReason = failedChars == 3 ? "MAX_MISTAKES"
                : completedChars + failedChars == 10 ? "ALL_DONE" : "TIME_UP";

        return Map.of("completedChars", completedChars, "failedChars", failedChars,
                "incompleteChars", incompleteChars, "total", 10, "finishedReason", finishedReason);
    }

    private static int strokeCount(ServedItem item) {
        Integer strokeCount = item.prompt() == null ? null : item.prompt().strokeCount();
        if (strokeCount == null || strokeCount <= 0) {
            throw new IllegalStateException("Writing challenge snapshot must contain a positive strokeCount.");
        }
        return strokeCount;
    }

    private static BigDecimal decimal(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        try {
            return new BigDecimal(number.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Integer integer(Object value) {
        BigDecimal number = decimal(value);
        if (number == null) {
            return null;
        }
        try {
            return number.intValueExact();
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    private static ApiException validationFailed() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Dữ liệu không hợp lệ.", Map.of());
    }

    private static ApiException resultCountMismatch() {
        return new ApiException(ErrorCode.RESULT_COUNT_MISMATCH, HttpStatus.BAD_REQUEST,
                "Kết quả thử thách không hợp lệ.", Map.of());
    }
}
