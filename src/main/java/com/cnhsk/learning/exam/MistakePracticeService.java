package com.cnhsk.learning.exam;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeService.SessionView;
import com.cnhsk.learning.practice.PracticeService.StartPracticeCommand;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.ServedItem;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Luyện lỗi của tôi": approved questions from the nodes this learner answered wrongly in the last seven
 * Vietnamese days. It is a targeted practice session, so allowance and mastery rules are the same.
 */
@Service
public class MistakePracticeService {
    static final int DAYS = 7;
    static final int MAX_QUESTIONS = 10;
    private static final int MAX_NODES = 20;
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private final EntityManager entities;
    private final PracticeService practice;
    private final Clock clock;
    private final QuestionItemFactory factory = new QuestionItemFactory();

    public MistakePracticeService(EntityManager entities, PracticeService practice, Clock clock) {
        this.entities = entities;
        this.practice = practice;
        this.clock = clock;
    }

    /**
     * Nodes with the learner's wrong answers since the start of the Vietnamese day six days ago, from every
     * graded section: a bank question counts for its primary node, a word, character or grammar item for
     * the node its lexeme belongs to.
     */
    @Transactional(readOnly = true)
    public List<MistakeNode> recentMistakes(long userId) {
        Instant from = LocalDate.ofInstant(clock.instant(), VIETNAM).minusDays(DAYS - 1L).atStartOfDay(VIETNAM).toInstant();
        List<?> rows = entities.createNativeQuery("""
                SELECT kp.code, kp.title, count(*) AS wrong
                FROM learning.attempt_items item
                JOIN learning.attempts a ON a.id = item.attempt_id
                LEFT JOIN learning.question_knowledge_points label
                  ON label.question_id = item.question_id AND label.role = 'PRIMARY'
                LEFT JOIN learning.lexemes lexeme ON item.question_id IS NULL AND lexeme.id = item.lexeme_id
                JOIN learning.knowledge_points kp ON kp.id = coalesce(label.knowledge_point_id, lexeme.knowledge_point_id)
                WHERE a.user_id = :user AND item.is_correct = false AND item.answered_at >= :from
                GROUP BY kp.code, kp.title
                ORDER BY wrong DESC, kp.code
                LIMIT :limit
                """).setParameter("user", userId).setParameter("from", from).setParameter("limit", MAX_NODES)
                .getResultList();
        return rows.stream().map(value -> {
            Object[] row = (Object[]) value;
            return new MistakeNode((String) row[0], (String) row[1], ((Number) row[2]).longValue());
        }).toList();
    }

    @Transactional
    public Result start(long userId) {
        List<MistakeNode> nodes = recentMistakes(userId);
        if (nodes.isEmpty()) return Result.empty("NO_RECENT_MISTAKES");
        List<String> kids = nodes.stream().map(MistakeNode::kid).toList();
        Map<String, Deque<Question>> byKid = candidates(userId, kids);
        List<Question> chosen = new ArrayList<>();
        // Round-robin across nodes, most mistaken node first, so one node cannot fill the whole session.
        while (chosen.size() < MAX_QUESTIONS && byKid.values().stream().anyMatch(queue -> !queue.isEmpty())) {
            for (String kid : kids) {
                Deque<Question> queue = byKid.get(kid);
                if (queue != null && !queue.isEmpty() && chosen.size() < MAX_QUESTIONS) chosen.add(queue.poll());
            }
        }
        if (chosen.isEmpty()) return Result.empty("NO_QUESTIONS");
        List<ServedItem> items = new ArrayList<>();
        for (Question question : chosen) items.add(factory.create(question, items.size() + 1).orElseThrow());
        Map<String, Object> meta = Map.of("mode", "MISTAKES", "kids", kids, "repeatedNos", List.of());
        return new Result(practice.start(new StartPracticeCommand(userId, PracticeSource.TARGETED_PRACTICE,
                null, null, items, List.of(), null, meta)), null);
    }

    /**
     * Servable approved questions per node: questions the learner never answered first, then the most
     * recently reviewed (new AI questions surface early), then by id.
     */
    private Map<String, Deque<Question>> candidates(long userId, Collection<String> kids) {
        // The cap applies per node so a large bank on one node cannot crowd the other nodes out of the pool.
        List<?> rows = entities.createNativeQuery("""
                SELECT id, code FROM (
                    SELECT q.id, kp.code, seen.value AS seen, q.reviewed_at,
                           row_number() OVER (PARTITION BY kp.code ORDER BY seen.value, q.reviewed_at DESC NULLS LAST, q.id) AS rank
                    FROM learning.questions q
                    JOIN learning.question_knowledge_points label ON label.question_id = q.id AND label.role = 'PRIMARY'
                    JOIN learning.knowledge_points kp ON kp.id = label.knowledge_point_id
                    CROSS JOIN LATERAL (SELECT EXISTS (SELECT 1 FROM learning.attempt_items item
                            JOIN learning.attempts a ON a.id = item.attempt_id
                            WHERE a.user_id = :user AND item.question_id = q.id) AS value) seen
                    WHERE kp.code IN (:kids) AND q.status = 'APPROVED' AND q.qtype IN ('MULTIPLE_CHOICE', 'TRUE_FALSE')
                ) ranked
                WHERE rank <= 20
                ORDER BY seen, reviewed_at DESC NULLS LAST, id
                """).setParameter("user", userId).setParameter("kids", kids).getResultList();
        Map<Long, String> kidOf = new LinkedHashMap<>();
        for (Object value : rows) {
            Object[] row = (Object[]) value;
            kidOf.putIfAbsent(((Number) row[0]).longValue(), (String) row[1]);
        }
        Map<String, Deque<Question>> result = new LinkedHashMap<>();
        if (kidOf.isEmpty()) return result;
        Map<Long, Question> loaded = new LinkedHashMap<>();
        entities.createQuery("SELECT q FROM Question q WHERE q.id IN :ids", Question.class)
                .setParameter("ids", kidOf.keySet()).getResultList().forEach(question -> loaded.put(question.getId(), question));
        for (var entry : kidOf.entrySet()) {
            Question question = loaded.get(entry.getKey());
            if (question != null && factory.isUsable(question)) {
                result.computeIfAbsent(entry.getValue(), kid -> new ArrayDeque<>()).add(question);
            }
        }
        return result;
    }

    public record MistakeNode(String kid, String nameVi, long wrongCount) { }

    /** Either a started session or the reason none could start. */
    public record Result(SessionView session, String reason) {
        static Result empty(String reason) { return new Result(null, reason); }
    }
}
