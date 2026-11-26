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
     * the node its lexeme belongs to, a pronunciation or listening unit for the node the session labelled
     * it with ({@code targetKid}). Every node still to practise is listed, most recent mistake first.
     */
    @Transactional(readOnly = true)
    public List<MistakeNode> recentMistakes(long userId) {
        Instant from = LocalDate.ofInstant(clock.instant(), VIETNAM).minusDays(DAYS - 1L).atStartOfDay(VIETNAM).toInstant();
        // Each correct answer on a node after its first mistake in the window settles one mistake, so the count
        // falls as the learner improves and the node leaves the list once every mistake is settled.
        // A mistake belongs to the menu section where it was made: the source of its session, or for a mistake
        // practice session the section it was started from. Correct answers on the node from anywhere settle it.
        List<?> rows = entities.createNativeQuery("""
                WITH answers AS (
                SELECT kp.code, kp.title, item.is_correct, item.answered_at,
                       CASE a.ref_type
                           WHEN 'PRON_STAGE' THEN 'LISTENING'
                           WHEN 'READING_PRACTICE' THEN 'READING'
                           WHEN 'RUBRIC_TASK' THEN 'WRITING'
                           WHEN 'TOPIC_STUDY' THEN 'TOPIC' WHEN 'TOPIC_PRACTICE' THEN 'TOPIC'
                           WHEN 'TOPIC_TEST' THEN 'TOPIC' WHEN 'QUIZ' THEN 'TOPIC' WHEN 'QUIZ_RETRY' THEN 'TOPIC'
                           WHEN 'GRAMMAR_LESSON' THEN 'GRAMMAR'
                           WHEN 'CHAR_RECOGNITION' THEN 'CHARACTER' WHEN 'CHAR_WRITING' THEN 'CHARACTER'
                           WHEN 'WRITING_CHALLENGE' THEN 'CHARACTER'
                           WHEN 'DUE_REVIEW' THEN 'REVIEW'
                           WHEN 'PLACEMENT' THEN 'PLACEMENT'
                           WHEN 'MOCK_EXAM' THEN 'MOCK_EXAM'
                           WHEN 'GAME_ROUND' THEN 'GAME' WHEN 'CONTEST' THEN 'GAME'
                           ELSE coalesce(a.served_items -> 'meta' ->> 'section', 'TARGETED')
                       END AS section
                FROM learning.attempt_items item
                JOIN learning.attempts a ON a.id = item.attempt_id
                LEFT JOIN learning.question_knowledge_points label
                  ON label.question_id = item.question_id AND label.role = 'PRIMARY'
                LEFT JOIN learning.lexemes lexeme ON item.question_id IS NULL AND lexeme.id = item.lexeme_id
                LEFT JOIN LATERAL (
                    SELECT served ->> 'targetKid' AS kid
                    FROM jsonb_array_elements(CASE WHEN item.question_id IS NULL AND item.lexeme_id IS NULL
                                                   THEN a.served_items -> 'items' ELSE '[]'::jsonb END) served
                    WHERE (served ->> 'no')::int = item.order_index
                    LIMIT 1
                ) labelled ON true
                LEFT JOIN learning.knowledge_points labelled_kp ON labelled_kp.code = labelled.kid
                JOIN learning.knowledge_points kp
                  ON kp.id = coalesce(label.knowledge_point_id, lexeme.knowledge_point_id, labelled_kp.id)
                WHERE a.user_id = :user AND item.is_correct IS NOT NULL AND item.answered_at >= :from
                ), wrongs AS (
                    SELECT section, code, max(title) AS title, count(*) AS wrong,
                           min(answered_at) AS first_wrong, max(answered_at) AS last_wrong
                    FROM answers WHERE NOT is_correct GROUP BY section, code
                ), tally AS (
                    SELECT wrong.section, wrong.code, wrong.title, wrong.wrong, wrong.last_wrong,
                           (SELECT count(*) FROM answers fixed WHERE fixed.code = wrong.code AND fixed.is_correct
                              AND fixed.answered_at > wrong.first_wrong) AS settled
                    FROM wrongs wrong
                )
                SELECT code, title, wrong - settled AS remaining, section
                FROM tally WHERE wrong > settled
                ORDER BY last_wrong DESC, remaining DESC, code, section
                """).setParameter("user", userId).setParameter("from", from).getResultList();
        return rows.stream().map(value -> {
            Object[] row = (Object[]) value;
            return new MistakeNode((String) row[0], (String) row[1], ((Number) row[2]).longValue(), (String) row[3]);
        }).toList();
    }

    /** A session over every section, or over one section when {@code section} is given. */
    @Transactional
    public Result start(long userId, String section) {
        List<MistakeNode> nodes = recentMistakes(userId).stream()
                .filter(node -> section == null || section.equals(node.section())).toList();
        if (nodes.isEmpty()) return Result.empty("NO_RECENT_MISTAKES");
        List<String> kids = nodes.stream().map(MistakeNode::kid).distinct().toList();
        Map<String, Deque<Question>> byKid = candidates(userId, kids);
        List<Question> chosen = new ArrayList<>();
        // Round-robin across nodes, most recent mistake first, so one node cannot fill the whole session.
        while (chosen.size() < MAX_QUESTIONS && byKid.values().stream().anyMatch(queue -> !queue.isEmpty())) {
            for (String kid : kids) {
                Deque<Question> queue = byKid.get(kid);
                if (queue != null && !queue.isEmpty() && chosen.size() < MAX_QUESTIONS) chosen.add(queue.poll());
            }
        }
        if (chosen.isEmpty()) return Result.empty("NO_QUESTIONS");
        List<ServedItem> items = new ArrayList<>();
        for (Question question : chosen) items.add(factory.create(question, items.size() + 1).orElseThrow());
        Map<String, Object> meta = new LinkedHashMap<>(Map.of("mode", "MISTAKES", "kids", kids, "repeatedNos", List.of()));
        if (section != null) meta.put("section", section);
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
                      AND (q.code IS NULL OR q.code NOT LIKE 'MCK%')
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

    /**
     * {@code section} is the menu section the mistakes were made in: LISTENING, READING, WRITING, TOPIC, GRAMMAR,
     * CHARACTER, REVIEW, PLACEMENT, MOCK_EXAM, GAME, or TARGETED for older mistake practice sessions.
     */
    public record MistakeNode(String kid, String nameVi, long wrongCount, String section) { }

    /** Either a started session or the reason none could start. */
    public record Result(SessionView session, String reason) {
        static Result empty(String reason) { return new Result(null, reason); }
    }
}
