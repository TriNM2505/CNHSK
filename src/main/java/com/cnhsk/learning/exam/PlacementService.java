package com.cnhsk.learning.exam;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.exam.PlacementFormScoring.Conclusion;
import com.cnhsk.learning.exam.PlacementFormScoring.Confidence;
import com.cnhsk.learning.exam.PlacementFormScoring.Level;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import com.cnhsk.learning.knowledge.KnowledgeTreeService;
import com.cnhsk.learning.knowledge.KnowledgeTreeService.KnowledgeNode;
import com.cnhsk.learning.knowledge.entity.KnowledgeDomain;
import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeService.SessionView;
import com.cnhsk.learning.practice.PracticeService.StartPracticeCommand;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.entity.Attempt;
import com.cnhsk.learning.practice.entity.AttemptItem;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Placement test on fixed forms. A form is a published exam whose code starts with the placement prefix and
 * whose questions span HSK 1–3. A run serves one whole form in one session; submitting it concludes the run,
 * scores each level part and records the measured level on the learner's profile. Runs may be retaken any
 * time and draw a different form than the last one whenever another form exists.
 */
@Service
public class PlacementService {
    private static final Logger log = LoggerFactory.getLogger(PlacementService.class);

    private final PlacementConfig config;
    private final PracticeService practice;
    private final EntityManager entities;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final KnowledgeTreeService knowledge;
    private final AuthApi auth;
    private final RandomGenerator random;
    private final QuestionItemFactory factory = new QuestionItemFactory();

    public PlacementService(PlacementConfig config, PracticeService practice, EntityManager entities,
            NamedParameterJdbcTemplate jdbc, Clock clock, KnowledgeTreeService knowledge, AuthApi auth,
            @Qualifier("practiceChoiceRandom") RandomGenerator random) {
        this.config = config;
        this.practice = practice;
        this.entities = entities;
        this.jdbc = jdbc;
        this.clock = clock;
        this.knowledge = knowledge;
        this.auth = auth;
        this.random = random;
    }

    @Transactional(readOnly = true)
    public Summary summary(long userId) {
        List<Attempt> attempts = attempts(userId);
        Attempt active = active(attempts);
        Attempt last = latestCompleted(attempts);
        return new Summary(active == null ? null : new Active(runId(active.getId(), active.getRefId()), active.getId()),
                last == null ? null : completed(last, attempts));
    }

    /** {@code startLevel} is accepted for older clients and ignored: every form covers HSK 1–3. */
    @Transactional
    public StartResult start(long userId, Integer startLevel) {
        lockRun(userId);
        List<Attempt> attempts = attempts(userId);
        Attempt active = active(attempts);
        if (active != null) {
            return new StartResult(runId(active.getId(), active.getRefId()), practice.view(userId, active.getId()), false);
        }
        List<Form> forms = forms();
        if (forms.isEmpty()) {
            throw new ApiException(ErrorCode.PLACEMENT_BANK_INSUFFICIENT, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Bài kiểm tra trình độ đang được chuẩn bị.", Map.of("details", List.of()));
        }
        Attempt last = latestCompleted(attempts);
        String previous = last == null ? null : placement(last).path("form").textValue();
        List<Form> fresh = forms.stream().filter(form -> !form.code().equals(previous)).toList();
        List<Form> pool = fresh.isEmpty() ? forms : fresh;
        Form form = pool.get(random.nextInt(pool.size()));
        SessionView session = startForm(userId, null, form);
        return new StartResult(runId(session.sessionId(), session.refId()), session, true);
    }

    /**
     * Called after the form is submitted: concludes the run. A session that expired before submission is
     * served again from the same form so the learner can finish it.
     */
    @Transactional
    public StartResult next(long userId, long runId) {
        lockRun(userId);
        List<Attempt> sessions = runSessions(userId, runId, true);
        Attempt root = root(sessions, runId);
        if (hasConclusion(root)) return new StartResult(runId, null, false);

        Attempt active = active(sessions);
        if (active != null) return new StartResult(runId, practice.view(userId, active.getId()), false);

        boolean submitted = sessions.stream().anyMatch(a -> "SUBMITTED".equals(a.getStatus()));
        if (submitted) return conclude(userId, runId, sessions, root);

        Attempt latest = sessions.get(0);
        if ("IN_PROGRESS".equals(latest.getStatus())) latest.setStatus("EXPIRED");
        String code = placement(root).path("form").textValue();
        Form form = code == null ? null : forms().stream().filter(f -> f.code().equals(code)).findFirst().orElse(null);
        // The form was withdrawn meanwhile, or the run predates fixed forms: nothing to serve, so conclude it.
        if (form == null) return conclude(userId, runId, sessions, root);
        return new StartResult(runId, startForm(userId, runId, form), true);
    }

    private StartResult conclude(long userId, long runId, List<Attempt> sessions, Attempt root) {
        Evaluation evaluation = evaluate(sessions);
        Conclusion conclusion = PlacementFormScoring.decide(calibrate(evaluation.items(), root.getId()), config);
        int updated = jdbc.update("""
                UPDATE learning.attempts
                SET served_items = jsonb_set(served_items, '{meta,placement,conclusion}',
                    jsonb_build_object('completedAt', CAST(:completedAt AS text), 'level', CAST(:level AS text),
                                       'confidence', CAST(:confidence AS text), 'ability', CAST(:ability AS float8),
                                       'standardError', CAST(:error AS float8)))
                WHERE id = :id AND user_id = :userId AND ref_type = 'PLACEMENT' AND ref_id IS NULL
                """, new MapSqlParameterSource("completedAt", clock.instant().toString())
                .addValue("level", conclusion.level().name()).addValue("confidence", conclusion.confidence().name())
                // No answer at all leaves the ability unknown; it is stored as null so calibration skips the run.
                .addValue("ability", Double.isNaN(conclusion.ability()) ? null : conclusion.ability(), java.sql.Types.DOUBLE)
                .addValue("error", Double.isNaN(conclusion.standardError()) ? null : conclusion.standardError(),
                        java.sql.Types.DOUBLE)
                .addValue("id", root.getId()).addValue("userId", userId));
        if (updated != 1) throw new IllegalStateException("Placement conclusion could not be recorded");
        auth.recordPlacedLevel(userId, PlacementFormScoring.profileLevel(conclusion.level()));
        return new StartResult(runId, null, false);
    }

    private SessionView startForm(long userId, Long refId, Form form) {
        var items = new ArrayList<ServedItem>();
        Map<Integer, String> kidOf = new LinkedHashMap<>();
        Map<Integer, Integer> levelOf = new LinkedHashMap<>();
        Map<Integer, Double> difficultyOf = new LinkedHashMap<>();
        Map<Integer, Double> guessOf = new LinkedHashMap<>();
        for (FormQuestion entry : form.questions()) {
            int no = items.size() + 1;
            Question question = entry.question();
            ServedItem item = factory.create(question, no).orElseThrow();
            items.add(item);
            kidOf.put(no, entry.kid());
            levelOf.put(no, question.getHskLevel().intValue());
            // The starting difficulty and guessing chance travel with the run, so scoring never depends on later edits.
            difficultyOf.put(no, PlacementFormScoring.priorDifficulty(question.getHskLevel().intValue(),
                    question.getDifficulty() == null ? null : question.getDifficulty().intValue()));
            guessOf.put(no, PlacementFormScoring.guessing(question.getQtype() == QuestionType.TRUE_FALSE,
                    item.options() == null ? 0 : item.options().size()));
        }
        return practice.start(new StartPracticeCommand(userId, PracticeSource.PLACEMENT, refId, null, items, List.of(),
                null, Map.of("placement", Map.of("block", 1, "form", form.code(), "title", form.title(),
                        "kidOf", kidOf, "levelOf", levelOf, "difficultyOf", difficultyOf, "guessOf", guessOf))));
    }

    private List<Attempt> runSessions(long userId, long runId, boolean lock) {
        var query = entities.createQuery("""
                SELECT a FROM Attempt a WHERE a.userId = :userId AND a.refType = 'PLACEMENT'
                    AND (a.id = :runId OR a.refId = :runId)
                ORDER BY a.servedAt DESC, a.id DESC
                """, Attempt.class).setParameter("userId", userId).setParameter("runId", runId);
        if (lock) query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        return query.getResultList();
    }

    private static Attempt root(List<Attempt> sessions, long runId) {
        return sessions.stream().filter(a -> a.getId() == runId && a.getRefId() == null).findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.ATTEMPT_NOT_OWNED, HttpStatus.FORBIDDEN,
                        "Bạn không có quyền truy cập lượt luyện này.", Map.of()));
    }

    @Transactional(readOnly = true)
    public Result result(long userId, long runId) {
        List<Attempt> sessions = runSessions(userId, runId, false);
        Attempt root = root(sessions, runId);
        if (!hasConclusion(root)) {
            throw new ApiException(ErrorCode.PLACEMENT_NOT_FINISHED, HttpStatus.CONFLICT,
                    "Lượt xếp trình độ chưa kết luận.", Map.of());
        }
        Evaluation evaluation = evaluate(sessions);
        Conclusion conclusion = conclusion(root, evaluation);
        List<LevelScore> byLevel = evaluation.byLevel().entrySet().stream().map(entry -> {
            Counts count = entry.getValue();
            return new LevelScore(entry.getKey(), count.correct, count.total, percent(count.correct, count.total));
        }).toList();
        List<DomainScore> domainScores = java.util.Arrays.stream(KnowledgeDomain.values()).map(domain -> {
            Counts count = evaluation.domains().getOrDefault(domain, new Counts());
            return new DomainScore(domain, count.correct, count.total, count.total == 0 ? null : count.percent(),
                    count.total > 0, count.total > 0 && count.total < 3);
        }).toList();
        return new Result(runId, completedAt(root), conclusion.level(), conclusion.confidence(),
                evaluation.itemCount(), byLevel, domainScores, wrongKids(evaluation.kids()));
    }

    /** Scores every submitted session of a run by level, knowledge domain and node. */
    private Evaluation evaluate(List<Attempt> sessions) {
        List<Attempt> submitted = sessions.stream().filter(a -> "SUBMITTED".equals(a.getStatus())).toList();
        Map<Long, Map<Integer, AttemptItem>> answers = answers(submitted);
        Map<Integer, Counts> levels = new TreeMap<>();
        Map<KnowledgeDomain, Counts> domains = new EnumMap<>(KnowledgeDomain.class);
        Map<String, Counts> kids = new TreeMap<>();
        int itemCount = 0;
        List<ScoredItem> scored = new ArrayList<>();
        for (Attempt attempt : submitted) {
            JsonNode meta = placement(attempt);
            JsonNode kidOf = meta.path("kidOf");
            JsonNode levelOf = meta.path("levelOf");
            Map<Integer, AttemptItem> given = answers.getOrDefault(attempt.getId(), Map.of());
            for (JsonNode item : attempt.getServedItems().path("items")) {
                int no = item.path("no").intValue();
                String key = Integer.toString(no);
                AttemptItem answer = given.get(no);
                boolean right = answer != null && Boolean.TRUE.equals(answer.getCorrect());
                boolean answered = answer != null && answer.getAnswer() != null;
                // Runs from the earlier adaptive test carry one level per block instead of one per question.
                int level = levelOf.path(key).asInt(meta.path("level").asInt());
                if (level >= 1 && level <= 3) levels.computeIfAbsent(level, ignored -> new Counts()).add(right);
                double difficulty = meta.path("difficultyOf").path(key).isNumber()
                        ? meta.path("difficultyOf").path(key).doubleValue()
                        : PlacementFormScoring.priorDifficulty(Math.max(1, Math.min(3, level)), null);
                double guess = meta.path("guessOf").path(key).isNumber() ? meta.path("guessOf").path(key).doubleValue() : 0.25;
                Long questionId = item.path("questionId").isIntegralNumber() ? item.path("questionId").longValue() : null;
                scored.add(new ScoredItem(questionId, new PlacementFormScoring.Item(difficulty, guess, right, answered)));
                String kid = kidOf.path(Integer.toString(no)).textValue();
                if (kid != null) {
                    domain(kid).ifPresent(domain -> domains.computeIfAbsent(domain, ignored -> new Counts()).add(right));
                    kids.computeIfAbsent(kid, ignored -> new Counts()).add(right);
                }
                itemCount++;
            }
        }
        Map<Integer, Counts> byLevel = new TreeMap<>(levels);
        return new Evaluation(byLevel, domains, kids, itemCount, List.copyOf(scored));
    }

    /**
     * Replaces each item's starting difficulty with the one seen in other learners' answers once it has
     * {@code calibration-min-answers} of them from concluded runs whose ability is known. Calibration is read live
     * from the answers already stored, so it needs no table of its own and sharpens as more learners take the test.
     */
    private List<PlacementFormScoring.Item> calibrate(List<ScoredItem> scored, long runId) {
        Set<Long> ids = scored.stream().map(ScoredItem::questionId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, double[]> seen = new HashMap<>();
        if (!ids.isEmpty()) {
            jdbc.query("""
                    SELECT ai.question_id, count(*) AS answers,
                           avg(CASE WHEN ai.is_correct THEN 1.0 ELSE 0.0 END) AS share,
                           avg(CAST(root.served_items->'meta'->'placement'->'conclusion'->>'ability' AS float8)) AS ability
                    FROM learning.attempt_items ai
                    JOIN learning.attempts a ON a.id = ai.attempt_id AND a.ref_type = 'PLACEMENT'
                    JOIN learning.attempts root ON root.id = coalesce(a.ref_id, a.id)
                    WHERE ai.question_id IN (:ids) AND root.id <> :run
                      AND root.served_items->'meta'->'placement'->'conclusion'->>'ability' IS NOT NULL
                    GROUP BY ai.question_id
                    HAVING count(*) >= :min
                    """, new MapSqlParameterSource("ids", ids).addValue("run", runId)
                    .addValue("min", config.getCalibrationMinAnswers()), rs -> {
                seen.put(rs.getLong("question_id"), new double[] {rs.getDouble("ability"), rs.getDouble("share")});
            });
        }
        return scored.stream().map(entry -> {
            double[] observed = entry.questionId() == null ? null : seen.get(entry.questionId());
            PlacementFormScoring.Item item = entry.item();
            if (observed == null) return item;
            return new PlacementFormScoring.Item(PlacementFormScoring.calibrated(observed[0], observed[1], item.guess()),
                    item.guess(), item.correct(), item.answered());
        }).toList();
    }

    private static java.util.Optional<KnowledgeDomain> domain(String kid) {
        try {
            return java.util.Optional.of(KnowledgeDomain.valueOf(kid.substring(0, 2)));
        } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
            return java.util.Optional.empty();
        }
    }

    private Map<Long, Map<Integer, AttemptItem>> answers(List<Attempt> submitted) {
        if (submitted.isEmpty()) return Map.of();
        return entities.createQuery("SELECT i FROM AttemptItem i WHERE i.attemptId IN :ids", AttemptItem.class)
                .setParameter("ids", submitted.stream().map(Attempt::getId).toList()).getResultList().stream()
                .collect(Collectors.groupingBy(AttemptItem::getAttemptId,
                        Collectors.toMap(AttemptItem::getOrderIndex, item -> item, (first, second) -> second)));
    }

    private List<WrongKid> wrongKids(Map<String, Counts> kids) {
        var wrong = kids.entrySet().stream().filter(entry -> entry.getValue().correct < entry.getValue().total).toList();
        if (wrong.isEmpty()) return List.of();
        Map<String, String> names = knowledge.nodes().stream()
                .collect(Collectors.toMap(KnowledgeNode::kid, KnowledgeNode::nameVi, (first, second) -> first));
        return wrong.stream().map(entry -> new WrongKid(entry.getKey(), names.get(entry.getKey()),
                entry.getValue().total - entry.getValue().correct, entry.getValue().total)).toList();
    }

    private static final class Counts {
        private int correct;
        private int total;

        private void add(boolean right) {
            total++;
            if (right) correct++;
        }

        private int percent() {
            return correct * 100 / total;
        }
    }

    private static int percent(int correct, int total) {
        return total == 0 ? 0 : correct * 100 / total;
    }

    static long runId(long id, Long refId) {
        return refId == null ? id : refId;
    }

    void lockRun(long userId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))",
                Map.of("key", "placement:run:" + userId), result -> null);
    }

    private List<Attempt> attempts(long userId) {
        return entities.createQuery("""
                SELECT a FROM Attempt a WHERE a.userId = :userId AND a.refType = 'PLACEMENT'
                ORDER BY a.servedAt DESC, a.id DESC
                """, Attempt.class).setParameter("userId", userId).getResultList();
    }

    private Attempt active(List<Attempt> attempts) {
        Instant now = clock.instant();
        Set<Long> completed = attempts.stream().filter(PlacementService::hasConclusion)
                .map(Attempt::getId).collect(Collectors.toSet());
        return attempts.stream().filter(a -> "IN_PROGRESS".equals(a.getStatus())
                && !a.getExpiresAt().isBefore(now) && !completed.contains(runId(a.getId(), a.getRefId())))
                .findFirst().orElse(null);
    }

    private static Attempt latestCompleted(List<Attempt> attempts) {
        return attempts.stream().filter(PlacementService::hasConclusion)
                .max(Comparator.comparing(PlacementService::completedAt).thenComparing(Attempt::getId)).orElse(null);
    }

    private static boolean hasConclusion(Attempt attempt) {
        return attempt.getRefId() == null && placement(attempt).path("conclusion").isObject();
    }

    private static Instant completedAt(Attempt attempt) {
        return Instant.parse(placement(attempt).path("conclusion").path("completedAt").asText());
    }

    private Completed completed(Attempt root, List<Attempt> attempts) {
        List<Attempt> sessions = attempts.stream().filter(a -> runId(a.getId(), a.getRefId()) == root.getId()).toList();
        Conclusion result = conclusion(root, evaluate(sessions));
        return new Completed(root.getId(), result.level(), result.confidence(), completedAt(root));
    }

    /** The conclusion stored when the run ended; runs concluded before fixed forms are scored again. */
    private Conclusion conclusion(Attempt root, Evaluation evaluation) {
        JsonNode stored = placement(root).path("conclusion");
        if (stored.path("level").isTextual() && stored.path("confidence").isTextual()) {
            return new Conclusion(Level.valueOf(stored.path("level").textValue()),
                    Confidence.valueOf(stored.path("confidence").textValue()),
                    stored.path("ability").asDouble(), stored.path("standardError").asDouble());
        }
        return PlacementFormScoring.decide(evaluation.items().stream().map(ScoredItem::item).toList(), config);
    }

    private static JsonNode placement(Attempt attempt) {
        return attempt.getServedItems().path("meta").path("placement");
    }

    /** Published placement forms whose questions can all be served, in code order. */
    private List<Form> forms() {
        List<Tuple> rows = entities.createNativeQuery("""
                SELECT e.code AS code, e.title AS title, q.id AS question_id
                FROM learning.exams e
                JOIN learning.exam_questions eq ON eq.exam_id = e.id
                JOIN learning.questions q ON q.id = eq.question_id
                WHERE e.status = 'PUBLISHED' AND e.code LIKE :prefix
                ORDER BY e.code, eq.section_index, eq.order_index, q.id
                """, Tuple.class).setParameter("prefix", config.getFormCodePrefix() + "%").getResultList();
        if (rows.isEmpty()) return List.of();
        Set<Long> ids = rows.stream().map(row -> ((Number) row.get("question_id")).longValue()).collect(Collectors.toSet());
        Map<Long, FormQuestion> usable = new HashMap<>();
        entities.createQuery("""
                SELECT q AS question, kp.code AS kid
                FROM QuestionKnowledgePoint label
                JOIN Question q ON q.id = label.questionId
                JOIN KnowledgePoint kp ON kp.id = label.knowledgePointId
                WHERE label.role = com.cnhsk.learning.knowledge.entity.KnowledgeLabelRole.PRIMARY
                  AND q.status = com.cnhsk.learning.knowledge.entity.QuestionStatus.APPROVED
                  AND q.hskLevel BETWEEN 1 AND 3 AND q.id IN :ids
                """, Tuple.class).setParameter("ids", ids).getResultList().forEach(row -> {
            Question question = row.get("question", Question.class);
            if (factory.isUsable(question)) usable.put(question.getId(), new FormQuestion(question, row.get("kid", String.class)));
        });
        Map<String, List<FormQuestion>> byForm = new LinkedHashMap<>();
        Map<String, String> titles = new HashMap<>();
        for (Tuple row : rows) {
            String code = (String) row.get("code");
            titles.putIfAbsent(code, (String) row.get("title"));
            FormQuestion question = usable.get(((Number) row.get("question_id")).longValue());
            List<FormQuestion> list = byForm.computeIfAbsent(code, ignored -> new ArrayList<>());
            if (question == null) log.warn("Placement form {} has a question that cannot be served", code);
            else list.add(question);
        }
        List<Form> forms = new ArrayList<>();
        byForm.forEach((code, questions) -> {
            Set<Integer> levels = questions.stream().map(q -> q.question().getHskLevel().intValue()).collect(Collectors.toSet());
            if (levels.containsAll(Set.of(1, 2, 3))) forms.add(new Form(code, titles.get(code), List.copyOf(questions)));
            else log.warn("Placement form {} does not cover HSK 1-3 and is skipped", code);
        });
        return forms;
    }

    private record Form(String code, String title, List<FormQuestion> questions) {}
    private record FormQuestion(Question question, String kid) {}
    private record Evaluation(Map<Integer, Counts> byLevel, Map<KnowledgeDomain, Counts> domains,
            Map<String, Counts> kids, int itemCount, List<ScoredItem> items) {}
    private record ScoredItem(Long questionId, PlacementFormScoring.Item item) {}

    public record Active(long runId, long sessionId) {}
    public record Completed(long runId, Level level, Confidence confidence, Instant completedAt) {}
    public record Summary(Active active, Completed last) {}
    public record LevelScore(int level, int correct, int total, int percent) {}
    public record DomainScore(KnowledgeDomain domain, int correct, int total, Integer percent,
            boolean measured, boolean tentative) {}
    public record WrongKid(String kid, String nameVi, int wrong, int total) {}
    public record Result(long runId, Instant completedAt, Level level, Confidence confidence,
            int itemCount, List<LevelScore> byLevel, List<DomainScore> domains, List<WrongKid> wrongKids) {}
    public record StartResult(long runId, SessionView session, boolean created) {}
}
