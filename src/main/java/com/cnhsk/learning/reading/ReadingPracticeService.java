package com.cnhsk.learning.reading;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

import com.cnhsk.learning.exam.QuestionItemFactory;
import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.entity.QuestionStatus;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeService.SessionView;
import com.cnhsk.learning.practice.PracticeService.StartPracticeCommand;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.ServedItem;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reading practice for one HSK level: approved text-only choice questions (fill the blank, true/false)
 * that measure reading. Questions without a skill label are read on screen, so they count too.
 * Answers become knowledge-tree evidence through the question's node labels, like any practice.
 */
@Service
public class ReadingPracticeService {
    static final int MAX_QUESTIONS = 10;
    private static final int POOL = 200;

    private final EntityManager entities;
    private final PracticeService practice;
    private final RandomGenerator random;
    private final QuestionItemFactory factory = new QuestionItemFactory();

    public ReadingPracticeService(EntityManager entities, PracticeService practice,
            @Qualifier("practiceChoiceRandom") RandomGenerator random) {
        this.entities = entities;
        this.practice = practice;
        this.random = random;
    }

    /** Returns null when the level has no usable reading question yet. */
    @Transactional
    public SessionView start(long userId, int hskLevel, QuestionType type) {
        List<QuestionType> types = type == null
                ? List.of(QuestionType.MULTIPLE_CHOICE, QuestionType.TRUE_FALSE) : List.of(type);
        List<Long> ids = new ArrayList<>(entities.createQuery("SELECT q.id FROM Question q "
                + "WHERE q.status = :approved AND q.hskLevel = :level AND q.qtype IN :types "
                + "AND (q.skill = 'READING' OR q.skill IS NULL) "
                + "AND q.audioUrl IS NULL AND q.imageUrl IS NULL "
                // Mock-paper questions stay in their papers so learners do not meet them before the exam.
                + "AND (q.code IS NULL OR q.code NOT LIKE 'MCK%') ORDER BY q.id", Long.class)
                .setParameter("approved", QuestionStatus.APPROVED).setParameter("level", (short) hskLevel)
                .setParameter("types", types).getResultList());
        if (ids.isEmpty()) return null;
        shuffle(ids);
        List<Long> pool = ids.subList(0, Math.min(POOL, ids.size()));

        List<Question> usable = new ArrayList<>(entities.createQuery(
                "SELECT q FROM Question q WHERE q.id IN :ids", Question.class)
                .setParameter("ids", pool).getResultList().stream().filter(factory::isUsable).toList());
        if (usable.isEmpty()) return null;
        shuffle(usable);

        List<ServedItem> items = new ArrayList<>();
        for (Question question : usable.subList(0, Math.min(MAX_QUESTIONS, usable.size()))) {
            items.add(factory.create(question, items.size() + 1).orElseThrow());
        }
        Map<String, Object> meta = Map.of("skill", "READING", "hskLevel", hskLevel);
        return practice.start(new StartPracticeCommand(userId, PracticeSource.READING_PRACTICE,
                null, null, items, List.of(), null, meta));
    }

    private <T> void shuffle(List<T> values) {
        for (int i = values.size() - 1; i > 0; i--) {
            Collections.swap(values, i, random.nextInt(i + 1));
        }
    }
}
