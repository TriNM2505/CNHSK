package com.cnhsk.learning.aigen;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** Relational batches for advisory generation selection. JSON is interpreted by the selector. */
@Repository
public class WeakKnowledgeQueryRepository {

    private final EntityManager entityManager;

    public WeakKnowledgeQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** Called in a short write transaction, before opening the selection snapshot. */
    public int failStale(Instant now) {
        Instant cutoff = now.minusSeconds(600);
        List<?> ids = entityManager.createNativeQuery("""
                SELECT id FROM learning.service_requests
                WHERE kind = 'AI_GENERATE' AND status = 'RUNNING' AND created_at < :cutoff
                ORDER BY id
                FOR UPDATE
                """).setParameter("cutoff", cutoff).getResultList();
        if (ids.isEmpty()) return 0;
        return entityManager.createNativeQuery("""
                UPDATE learning.service_requests
                SET status = 'FAILED', error_message = 'STALE', completed_at = :now
                WHERE id IN (:ids) AND kind = 'AI_GENERATE'
                  AND status = 'RUNNING' AND created_at < :cutoff
                """).setParameter("ids", ids).setParameter("now", now)
                .setParameter("cutoff", cutoff).executeUpdate();
    }

    /**
     * Wrong answers in the window from every graded section. A bank question counts for its primary node;
     * a word, character or grammar item (vocabulary, characters, grammar, review) counts for the node its
     * lexeme belongs to and comes back with question id 0, no answer and no options. A pronunciation or
     * listening unit counts for the node the server labelled it with in the session ({@code targetKid}).
     * Items that reach no node are left out.
     */
    public List<WrongAnswer> wrongAnswers(Instant from, Instant through) {
        List<?> rows = entityManager.createNativeQuery("""
                WITH wrong AS (
                    SELECT a.user_id, ai.question_id,
                           CASE WHEN ai.question_id IS NULL THEN NULL ELSE ai.answer END AS answer,
                           kp.code AS primary_kid, count(*) AS wrong_count
                    FROM learning.attempt_items ai
                    JOIN learning.attempts a ON a.id = ai.attempt_id
                    LEFT JOIN learning.question_knowledge_points label
                      ON label.question_id = ai.question_id AND label.role = 'PRIMARY'
                    LEFT JOIN learning.lexemes lexeme ON ai.question_id IS NULL AND lexeme.id = ai.lexeme_id
                    LEFT JOIN LATERAL (
                        SELECT served ->> 'targetKid' AS kid
                        FROM jsonb_array_elements(CASE WHEN ai.question_id IS NULL AND ai.lexeme_id IS NULL
                                                       THEN a.served_items -> 'items' ELSE '[]'::jsonb END) served
                        WHERE (served ->> 'no')::int = ai.order_index
                        LIMIT 1
                    ) labelled ON true
                    LEFT JOIN learning.knowledge_points labelled_kp ON labelled_kp.code = labelled.kid
                    LEFT JOIN learning.knowledge_points kp
                      ON kp.id = coalesce(label.knowledge_point_id, lexeme.knowledge_point_id, labelled_kp.id)
                    WHERE ai.is_correct = false AND ai.answered_at >= :from AND ai.answered_at <= :through
                      AND (ai.question_id IS NOT NULL OR lexeme.knowledge_point_id IS NOT NULL OR labelled_kp.id IS NOT NULL)
                    GROUP BY 1, 2, 3, 4
                )
                SELECT wrong.user_id, coalesce(wrong.question_id, 0), wrong.answer, wrong.primary_kid,
                       coalesce(q.options::text, '[]'), wrong.wrong_count
                FROM wrong
                LEFT JOIN learning.questions q ON q.id = wrong.question_id
                WHERE wrong.question_id IS NULL OR q.id IS NOT NULL
                ORDER BY wrong.user_id, 2, wrong.primary_kid, wrong.answer NULLS FIRST
                """).setParameter("from", from).setParameter("through", through).getResultList();
        return rows.stream().map(value -> {
            Object[] row = (Object[]) value;
            return new WrongAnswer(number(row[0]), number(row[1]), (String) row[2], (String) row[3],
                    (String) row[4], number(row[5]));
        }).toList();
    }

    public List<NodeSummary> nodes(Set<String> kids) {
        if (kids.isEmpty()) return List.of();
        List<?> rows = entityManager.createNativeQuery("""
                WITH targets AS (
                    SELECT id, code, can_do FROM learning.knowledge_points WHERE code IN (:kids)
                ), bank AS (
                    SELECT label.knowledge_point_id, count(*) AS bank_count
                    FROM learning.question_knowledge_points label
                    JOIN targets target ON target.id = label.knowledge_point_id
                    JOIN learning.questions q ON q.id = label.question_id
                    WHERE label.role = 'PRIMARY' AND q.status = 'APPROVED'
                    GROUP BY label.knowledge_point_id
                ), pending AS (
                    SELECT DISTINCT label.knowledge_point_id
                    FROM learning.question_knowledge_points label
                    JOIN targets target ON target.id = label.knowledge_point_id
                    JOIN learning.questions q ON q.id = label.question_id
                    WHERE q.source = 'AI' AND q.status = 'PENDING_REVIEW'
                )
                SELECT target.id, target.code, target.can_do, coalesce(bank.bank_count, 0),
                       EXISTS (SELECT 1 FROM learning.lexemes l WHERE l.knowledge_point_id = target.id),
                       pending.knowledge_point_id IS NOT NULL
                FROM targets target
                LEFT JOIN bank ON bank.knowledge_point_id = target.id
                LEFT JOIN pending ON pending.knowledge_point_id = target.id
                ORDER BY target.code
                """).setParameter("kids", kids).getResultList();
        return rows.stream().map(value -> {
            Object[] row = (Object[]) value;
            return new NodeSummary(number(row[0]), (String) row[1], (String) row[2], number(row[3]),
                    (Boolean) row[4], (Boolean) row[5]);
        }).toList();
    }

    /** Every prerequisite and support link of the tree, as dependent → required KID, in one read. */
    public List<PrerequisiteLink> prerequisiteLinks() {
        List<?> rows = entityManager.createNativeQuery("""
                SELECT dependent.code, required.code, false
                FROM learning.knowledge_points dependent
                CROSS JOIN LATERAL unnest(dependent.prereq_ids) link(id)
                JOIN learning.knowledge_points required ON required.id = link.id
                UNION ALL
                SELECT dependent.code, required.code, true
                FROM learning.knowledge_points dependent
                CROSS JOIN LATERAL unnest(dependent.support_ids) link(id)
                JOIN learning.knowledge_points required ON required.id = link.id
                ORDER BY 1, 2, 3
                """).getResultList();
        return rows.stream().map(value -> {
            Object[] row = (Object[]) value;
            return new PrerequisiteLink((String) row[0], (String) row[1], (Boolean) row[2]);
        }).toList();
    }

    public List<ActiveRun> activeRuns() {
        List<?> rows = entityManager.createNativeQuery("""
                SELECT id, payload::text FROM learning.service_requests
                WHERE kind = 'AI_GENERATE' AND status = 'RUNNING'
                ORDER BY id
                """).getResultList();
        return rows.stream().map(value -> {
            Object[] row = (Object[]) value;
            return new ActiveRun(number(row[0]), (String) row[1]);
        }).toList();
    }

    public Optional<PromptNode> promptNode(long knowledgePointId) {
        List<?> rows = entityManager.createNativeQuery("""
                SELECT kp.id, kp.code, kp.title, kp.name_zh, kp.can_do, kp.hsk_level, prerequisite.title,
                       array_to_string(kp.skills, ','), kp.pattern, kp.examples::text,
                       (SELECT json_agg(json_build_object('code', code.code, 'name_vi', code.name_vi,
                                                          'fix_hint', code.fix_hint) ORDER BY code.code)::text
                        FROM learning.error_codes code WHERE code.kid_id = kp.id AND code.severity >= 2)
                FROM learning.knowledge_points kp
                LEFT JOIN LATERAL unnest(kp.prereq_ids) WITH ORDINALITY link(id, position) ON true
                LEFT JOIN learning.knowledge_points prerequisite ON prerequisite.id = link.id
                WHERE kp.id = :id
                ORDER BY link.position
                """).setParameter("id", knowledgePointId).getResultList();
        if (rows.isEmpty()) return Optional.empty();
        Object[] node = (Object[]) rows.get(0);
        List<String> titles = new ArrayList<>();
        for (Object value : rows) {
            String title = (String) ((Object[]) value)[6];
            if (title != null) titles.add(title);
        }
        String skills = (String) node[7];
        return Optional.of(new PromptNode(number(node[0]), (String) node[1], (String) node[2],
                (String) node[3], (String) node[4], ((Number) node[5]).intValue(), titles,
                new PromptTreeContext(skills == null || skills.isBlank() ? List.of() : List.of(skills.split(",")),
                        (String) node[8], (String) node[9], (String) node[10])));
    }

    public List<PromptLexeme> promptLexemes(long knowledgePointId) {
        List<?> rows = entityManager.createNativeQuery("""
                SELECT text, pinyin, meaning_vi FROM learning.lexemes
                WHERE knowledge_point_id = :id ORDER BY id LIMIT 80
                """).setParameter("id", knowledgePointId).getResultList();
        return rows.stream().map(value -> {
            Object[] row = (Object[]) value;
            return new PromptLexeme((String) row[0], (String) row[1], (String) row[2]);
        }).toList();
    }

    public List<PromptQuestion> promptQuestions(long knowledgePointId) {
        // All linked options feed error patterns; only approved PRIMARY content can become a sample.
        List<?> rows = entityManager.createNativeQuery("""
                SELECT q.id,
                       CASE WHEN q.status = 'APPROVED' AND label.role = 'PRIMARY' THEN q.stem END,
                       q.options::text,
                       CASE WHEN q.status = 'APPROVED' AND label.role = 'PRIMARY' THEN q.explanation END,
                       q.status = 'APPROVED' AND label.role = 'PRIMARY'
                FROM learning.questions q
                JOIN learning.question_knowledge_points label ON label.question_id = q.id
                WHERE label.knowledge_point_id = :id ORDER BY q.id
                """).setParameter("id", knowledgePointId).getResultList();
        return rows.stream().map(value -> {
            Object[] row = (Object[]) value;
            return new PromptQuestion(number(row[0]), (String) row[1], (String) row[2],
                    (String) row[3], (Boolean) row[4]);
        }).toList();
    }

    /** Every linked stem in any status, with its raw options for the near-duplicate rule. */
    public List<ExistingStem> existingStems(long knowledgePointId) {
        List<?> rows = entityManager.createNativeQuery("""
                SELECT q.stem, q.options::text
                FROM learning.questions q
                JOIN learning.question_knowledge_points label ON label.question_id = q.id
                WHERE label.knowledge_point_id = :id
                """).setParameter("id", knowledgePointId).getResultList();
        return rows.stream().map(value -> {
            Object[] row = (Object[]) value;
            return new ExistingStem((String) row[0], (String) row[1]);
        }).toList();
    }

    public Set<String> existingKidCodes(Set<String> kids) {
        if (kids.isEmpty()) return Set.of();
        List<?> rows = entityManager.createNativeQuery("""
                SELECT code FROM learning.knowledge_points WHERE code IN (:kids)
                """, String.class).setParameter("kids", kids).getResultList();
        return strings(rows);
    }

    /**
     * Characters listed at or below the level, plus the characters of the node's own words: those words are
     * curated for the node's level even when one of their characters is listed higher (音乐 is an HSK 2 word
     * while 音 is an HSK 3 character).
     */
    public Set<String> allowedCharacters(Set<String> characters, int hskLevel, long knowledgePointId) {
        if (characters.isEmpty()) return Set.of();
        List<?> rows = entityManager.createNativeQuery("""
                SELECT text FROM learning.lexemes
                WHERE kind = 'CHAR' AND hsk_level <= :level AND text IN (:characters)
                UNION
                SELECT part FROM learning.lexemes word, regexp_split_to_table(word.text, '') AS part
                WHERE word.knowledge_point_id = :node AND part IN (:characters)
                """, String.class).setParameter("level", hskLevel).setParameter("node", knowledgePointId)
                .setParameter("characters", characters).getResultList();
        return strings(rows);
    }

    private static Set<String> strings(List<?> rows) {
        Set<String> result = new HashSet<>();
        for (Object row : rows) result.add((String) row);
        return Set.copyOf(result);
    }

    public record PromptNode(long knowledgePointId, String kid, String title, String nameZh,
            String canDo, int hskLevel, List<String> prerequisiteTitles, PromptTreeContext tree) {
        public PromptNode(long knowledgePointId, String kid, String title, String nameZh,
                String canDo, int hskLevel, List<String> prerequisiteTitles) {
            this(knowledgePointId, kid, title, nameZh, canDo, hskLevel, prerequisiteTitles, PromptTreeContext.NONE);
        }
        public PromptNode {
            prerequisiteTitles = List.copyOf(prerequisiteTitles);
            tree = tree == null ? PromptTreeContext.NONE : tree;
        }
    }
    public record PromptLexeme(String text, String pinyin, String meaningVi) { }
    /**
     * What the competency tree v2.0 says about a node: its skills, the pattern it teaches, the stored examples
     * (only their reviewed correct half reaches the model) and, as JSON, the counting error codes of the node.
     */
    public record PromptTreeContext(List<String> skills, String pattern, String examplesJson, String errorCodesJson) {
        public static final PromptTreeContext NONE = new PromptTreeContext(List.of(), null, null, null);
        public PromptTreeContext { skills = List.copyOf(skills); }
    }
    public record PromptQuestion(long id, String stem, String optionsJson, String explanation,
            boolean approvedPrimary) { }
    /** {@code optionsJson} is the stored options array as text; it may be empty or legacy-shaped. */
    public record ExistingStem(String stem, String optionsJson) { }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        throw new IllegalStateException("Unexpected numeric value in generation selection");
    }

    public record WrongAnswer(long userId, long questionId, String answer, String primaryKid,
            String optionsJson, long count) { }
    public record NodeSummary(long knowledgePointId, String kid, String canDo, long bankCount,
            boolean hasLexemes, boolean hasPending) { }
    public record ActiveRun(long id, String payloadJson) { }
    /** {@code support} links count less than prerequisites when tracing a weakness back. */
    public record PrerequisiteLink(String dependentKid, String requiredKid, boolean support) { }
}
