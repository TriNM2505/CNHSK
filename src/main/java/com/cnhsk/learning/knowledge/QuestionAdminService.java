package com.cnhsk.learning.knowledge;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.repository.QuestionAdminQueryRepository;
import com.cnhsk.learning.knowledge.repository.QuestionAdminQueryRepository.Filters;
import com.cnhsk.learning.knowledge.repository.QuestionAdminQueryRepository.LabelRow;
import com.cnhsk.learning.knowledge.repository.QuestionRepository;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionAdminService {
    private final QuestionRepository questions;
    private final QuestionAdminQueryRepository queries;

    public QuestionAdminService(QuestionRepository questions, QuestionAdminQueryRepository queries) {
        this.questions = questions;
        this.queries = queries;
    }

    @Transactional(readOnly = true)
    public QuestionPage list(Filters filters, int page, int size) {
        var rows = queries.page(filters, page, size);
        return new QuestionPage(views(rows.items()), page, size, rows.totalElements());
    }

    @Transactional(readOnly = true)
    public QuestionView detail(long id) {
        Question question = questions.findById(id).orElseThrow(QuestionAdminService::notFound);
        return views(List.of(question)).get(0);
    }

    private List<QuestionView> views(List<Question> items) {
        if (items.isEmpty()) return List.of();
        Set<Long> ids = items.stream().map(Question::getId).collect(Collectors.toSet());
        Map<Long, List<LabelRow>> labels = queries.labels(ids).stream().collect(Collectors.groupingBy(LabelRow::questionId));
        Set<Long> used = questions.usedQuestionIds(ids);
        return items.stream().map(question -> new QuestionView(question,
                labels.getOrDefault(question.getId(), List.of()), used.contains(question.getId()))).toList();
    }

    public static ApiException notFound() {
        return new ApiException(ErrorCode.QUESTION_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Không tìm thấy câu hỏi.", Map.of());
    }

    public record QuestionView(Question question, List<LabelRow> labels, boolean used) {
        public QuestionView { labels = List.copyOf(labels); }
    }
    public record QuestionPage(List<QuestionView> items, int page, int size, long totalElements) { }
}
