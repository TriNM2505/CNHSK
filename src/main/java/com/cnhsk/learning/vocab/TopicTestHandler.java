package com.cnhsk.learning.vocab;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler;
import org.springframework.stereotype.Component;

@Component("topicTestHandler")
public class TopicTestHandler implements PracticeSourceHandler {

    private final TopicTestRepository repository;

    public TopicTestHandler(TopicTestRepository repository) {
        this.repository = repository;
    }

    @Override
    public PracticeSource source() {
        return PracticeSource.TOPIC_TEST;
    }

    @Override
    public boolean isSuspicious(FinishContext context) {
        return Duration.between(context.servedAt(), context.submittedAt()).toMillis() < 2000L * context.items().size();
    }

    @Override
    public Map<String, Object> onFinish(FinishContext context) {
        // The engine owns the finish row lock and transaction. Never acquire a start advisory lock here.
        var past = repository.past(context.userId(), context.refId(), context.sessionId());
        boolean scored = context.score() != null && context.maxScore() != null && context.maxScore() > 0;
        boolean passed = scored && context.score() * 100 >= 90 * context.maxScore();
        int percent = scored ? Math.toIntExact(context.score() * 100 / context.maxScore()) : 0;
        int best = Math.max(percent, past.bestPercent() == null ? 0 : past.bestPercent());
        var wrongIds = new LinkedHashSet<Long>();
        for (GradedItem graded : context.items()) {
            if (Boolean.FALSE.equals(graded.correct()) && !graded.answer().audioFailed() && graded.item().lexemeId() != null) {
                wrongIds.add(graded.item().lexemeId());
            }
        }
        Map<Long, Lexeme> words = repository.wordsByIds(wrongIds).stream()
                .collect(Collectors.toMap(Lexeme::getId, Function.identity()));
        List<Map<String, Object>> wrong = new ArrayList<>();
        for (long id : wrongIds) {
            Lexeme word = words.get(id);
            if (word == null) continue;
            var view = new LinkedHashMap<String, Object>();
            view.put("lexemeId", id);
            view.put("text", word.getText());
            view.put("pinyin", word.getPinyin());
            view.put("meaning", word.getMeaningVi());
            wrong.add(view);
        }
        return Map.of("passed", passed, "bestPercent", best, "topicCompleted", past.completed() || passed,
                "firstCompletion", passed && !past.completed(), "wrongWords", List.copyOf(wrong));
    }
}
