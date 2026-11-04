package com.cnhsk.learning.exam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.exam.ExamQueryService.Exam;
import com.cnhsk.learning.exam.ExamQueryService.ExamQuestion;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeService.SessionView;
import com.cnhsk.learning.practice.PracticeService.StartPracticeCommand;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamAttemptService {
    private final ExamQueryService exams;
    private final PracticeService practice;
    private final NamedParameterJdbcTemplate jdbc;
    private final QuestionItemFactory factory = new QuestionItemFactory();

    public ExamAttemptService(ExamQueryService exams, PracticeService practice, NamedParameterJdbcTemplate jdbc) {
        this.exams = exams;
        this.practice = practice;
        this.jdbc = jdbc;
    }

    @Transactional
    public StartResult start(long userId, long examId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))",
                Map.of("key", "exam:start:" + userId + ":" + examId), result -> null);

        Exam exam = exams.find(examId);
        if (exam == null) {
            throw new ApiException(ErrorCode.EXAM_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy đề thi.", Map.of());
        }
        if (!"PUBLISHED".equals(exam.status())) {
            throw new ApiException(ErrorCode.EXAM_NOT_PUBLISHED, HttpStatus.FORBIDDEN, "Đề thi chưa được mở.", Map.of());
        }

        List<ServedItem> items = new ArrayList<>();
        Map<Integer, Integer> sectionOf = new LinkedHashMap<>();
        Map<Integer, Integer> points = new LinkedHashMap<>();
        for (ExamQuestion question : exams.questions(List.of(examId)).getOrDefault(examId, List.of())) {
            int no = items.size() + 1;
            factory.create(question.question(), no).ifPresent(item -> {
                items.add(item);
                sectionOf.put(no, question.section());
                points.put(no, question.points());
            });
        }
        if (items.isEmpty()) {
            throw new ApiException(ErrorCode.EXAM_HAS_NO_QUESTIONS, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Đề thi chưa có câu hỏi dùng được.", Map.of());
        }

        SessionView active = practice.findActive(userId, PracticeSource.MOCK_EXAM, examId);
        if (active != null) return new StartResult(active, false);

        Map<String, Object> snapshot = Map.of("examId", examId, "title", exam.title(), "sections", exam.sections(),
                "sectionOf", sectionOf, "points", points);
        SessionView session = practice.start(new StartPracticeCommand(userId, PracticeSource.MOCK_EXAM, examId,
                null, items, List.of(), null, Map.of("exam", snapshot)));
        return new StartResult(session, true);
    }

    public record StartResult(SessionView session, boolean created) {}
}
