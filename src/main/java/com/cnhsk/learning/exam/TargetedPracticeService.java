package com.cnhsk.learning.exam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

import com.cnhsk.learning.knowledge.KnowledgeTreeService;
import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeService.SessionView;
import com.cnhsk.learning.practice.PracticeService.StartPracticeCommand;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TargetedPracticeService {
    private static final int MAX_QUESTIONS = 10;

    private final KnowledgeTreeService knowledge;
    private final EntityManager entities;
    private final PracticeService practice;
    private final RandomGenerator random;
    private final QuestionItemFactory factory = new QuestionItemFactory();

    public TargetedPracticeService(KnowledgeTreeService knowledge, EntityManager entities, PracticeService practice,
            @Qualifier("practiceChoiceRandom") RandomGenerator random) {
        this.knowledge = knowledge;
        this.entities = entities;
        this.practice = practice;
        this.random = random;
    }

    @Transactional
    public SessionView start(long userId, String kid, QuestionType preferred, Long excludeQuestionId) {
        if (knowledge.node(kid).isEmpty()) {
            throw new ApiException(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Không tìm thấy nút năng lực.", Map.of());
        }
        List<Long> ids = knowledge.approvedQuestionIds(Set.of(kid), null, 200);
        if (ids.isEmpty()) return null;

        List<Question> usable = new ArrayList<>(entities.createQuery(
                "SELECT q FROM Question q WHERE q.id IN :ids ORDER BY q.id", Question.class)
                .setParameter("ids", ids).getResultList().stream().filter(factory::isUsable).toList());
        if (usable.isEmpty()) return null;

        boolean repeated = usable.size() == 1 && usable.get(0).getId().equals(excludeQuestionId);
        if (!repeated) usable.removeIf(question -> question.getId().equals(excludeQuestionId));
        shuffle(usable);
        if (preferred != null) usable.sort(Comparator.comparing(question -> question.getQtype() != preferred));
        List<Question> selected = usable.subList(0, Math.min(MAX_QUESTIONS, usable.size()));
        shuffle(selected);

        List<ServedItem> items = new ArrayList<>();
        for (Question question : selected) {
            items.add(factory.create(question, items.size() + 1).orElseThrow());
        }
        Map<String, Object> meta = Map.of("kid", kid, "repeatedNos", repeated ? List.of(1) : List.of());
        return practice.start(new StartPracticeCommand(userId, PracticeSource.TARGETED_PRACTICE,
                null, null, items, List.of(), null, meta));
    }

    private void shuffle(List<Question> questions) {
        for (int i = questions.size() - 1; i > 0; i--) {
            Collections.swap(questions, i, random.nextInt(i + 1));
        }
    }
}
