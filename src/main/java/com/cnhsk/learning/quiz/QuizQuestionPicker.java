package com.cnhsk.learning.quiz;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

import com.cnhsk.learning.exam.QuestionItemFactory;
import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import com.cnhsk.learning.practice.ServedItem;
import org.springframework.stereotype.Component;

@Component
public class QuizQuestionPicker {
    private final QuizRepository repository;
    private final QuestionItemFactory factory = new QuestionItemFactory();

    public QuizQuestionPicker(QuizRepository repository) { this.repository = repository; }

    public List<ServedItem> pick(long topicId, int count) {
        return pick(repository.questions(topicId), count, ThreadLocalRandom.current());
    }

    List<ServedItem> pick(List<Question> questions, int count, RandomGenerator random) {
        Map<QuestionType, List<Question>> groups = new LinkedHashMap<>();
        questions.stream().filter(factory::isUsable)
                .forEach(q -> groups.computeIfAbsent(q.getQtype(), ignored -> new ArrayList<>()).add(q));
        if (groups.values().stream().mapToInt(List::size).sum() < count) throw QuizSessionService.unavailable();
        List<List<Question>> buckets = new ArrayList<>(groups.values());
        buckets.forEach(bucket -> shuffle(bucket, random));
        shuffle(buckets, random);
        List<Question> selected = new ArrayList<>();
        while (selected.size() < count) {
            for (List<Question> bucket : buckets) {
                if (!bucket.isEmpty()) selected.add(bucket.remove(bucket.size() - 1));
                if (selected.size() == count) break;
            }
        }
        shuffle(selected, random);
        List<ServedItem> items = new ArrayList<>();
        for (Question question : selected) {
            ServedItem item = factory.create(question, items.size() + 1).orElseThrow();
            // Quiz suspicion uses elapsed server time for the entire session, never client item durations.
            items.add(new ServedItem(item.no(), item.type(), item.skill(), item.lexemeId(), item.prompt(),
                    item.options(), item.answer(), item.accept(), item.explanation(),
                    new ServedItem.Rules(0, 0, false), item.questionId()));
        }
        return List.copyOf(items);
    }

    private static <T> void shuffle(List<T> items, RandomGenerator random) {
        for (int i = items.size() - 1; i > 0; i--) Collections.swap(items, i, random.nextInt(i + 1));
    }
}
