package com.cnhsk.learning.exam;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.cnhsk.learning.exam.PlacementAlgorithm.BlockResult;
import com.cnhsk.learning.exam.PlacementAlgorithm.Conclusion;
import com.cnhsk.learning.exam.PlacementAlgorithm.Next;
import com.cnhsk.learning.exam.PlacementAlgorithm.Confidence;
import com.cnhsk.learning.exam.PlacementAlgorithm.PlacementLevel;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlacementService {
    private final PlacementConfig config;
    private final PracticeService practice;
    private final EntityManager entities;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final KnowledgeTreeService knowledge;
    private final RandomGenerator random;
    private final PlacementAlgorithm algorithm = new PlacementAlgorithm();
    private final QuestionItemFactory factory = new QuestionItemFactory();

    public PlacementService(PlacementConfig config, PracticeService practice, EntityManager entities,
            NamedParameterJdbcTemplate jdbc, Clock clock, KnowledgeTreeService knowledge, @Qualifier("practiceChoiceRandom") RandomGenerator random) {
        this.config = config;
        this.practice = practice;
        this.entities = entities;
        this.jdbc = jdbc;
        this.clock = clock;
        this.knowledge = knowledge;
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

    @Transactional
    public StartResult start(long userId, Integer startLevel) {
        lockRun(userId);
        List<Attempt> attempts = attempts(userId);
        Attempt active = active(attempts);
        if (active != null) {
            return new StartResult(runId(active.getId(), active.getRefId()), practice.view(userId, active.getId()), false);
        }

        Map<Integer, List<BankQuestion>> bank = bank();
        List<BankShortage> shortages = IntStream.rangeClosed(1, 3)
                .mapToObj(level -> new BankShortage(level, bank.getOrDefault(level, List.of()).size()))
                .filter(shortage -> shortage.available() < config.getMinBankPerLevel()).toList();
        if (!shortages.isEmpty()) {
            throw new ApiException(ErrorCode.PLACEMENT_BANK_INSUFFICIENT, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Ngân hàng câu hỏi chưa đủ để xếp trình độ.", Map.of("details", shortages));
        }

        int level = startLevel == null ? config.getStartLevel() : startLevel;
        Attempt last = latestCompleted(attempts);
        Set<Long> previous = last == null ? Set.of() : servedQuestionIds(attempts, last.getId());
        List<BankQuestion> selected = selectBlock(bank.get(level), Set.of(), previous);
        SessionView session = startBlock(userId, null, 1, level, selected);
        return new StartResult(runId(session.sessionId(), session.refId()), session, true);
    }

    @Transactional
    public StartResult next(long userId, long runId) {
        lockRun(userId);
        List<Attempt> blocks = runBlocks(userId, runId, true);
        Attempt root = root(blocks, runId);
        if (hasConclusion(root)) return new StartResult(runId, null, false);

        Attempt active = active(blocks);
        if (active != null) {
            return new StartResult(runId, practice.view(userId, active.getId()), false);
        }
        List<BlockResult> submitted = blockResults(blocks);
        var decision = algorithm.decide(submitted, config, false);
        if (decision instanceof Conclusion) {
            return conclude(userId, runId, false);
        }

        Attempt latest = blocks.get(0);
        boolean retry = !"SUBMITTED".equals(latest.getStatus());
        int level = retry ? placement(latest).path("level").intValue()
                : ((Next) decision).level();
        int block = placement(latest).path("block").intValue() + (retry ? 0 : 1);
        if ("IN_PROGRESS".equals(latest.getStatus())) latest.setStatus("EXPIRED");

        List<Attempt> history = attempts(userId);
        Attempt last = latestCompleted(history);
        Set<Long> previous = last == null ? Set.of() : servedQuestionIds(history, last.getId());
        List<BankQuestion> selected = selectBlock(bank().getOrDefault(level, List.of()),
                servedQuestionIds(blocks, runId), previous);
        if (selected.isEmpty()) return conclude(userId, runId, true);
        return new StartResult(runId, startBlock(userId, runId, block, level, selected), true);
    }

    private StartResult conclude(long userId, long runId, boolean exhausted) {
        int updated = jdbc.update("""
                UPDATE learning.attempts
                SET served_items = jsonb_set(served_items, '{meta,placement,conclusion}',
                    jsonb_build_object('completedAt', CAST(:completedAt AS text), 'bankExhausted', :exhausted))
                WHERE id = :id AND user_id = :userId AND ref_type = 'PLACEMENT' AND ref_id IS NULL
                """, Map.of("completedAt", clock.instant().toString(), "exhausted", exhausted,
                "id", runId, "userId", userId));
        if (updated != 1) throw new IllegalStateException("Placement conclusion could not be recorded");
        return new StartResult(runId, null, false);
    }

    private SessionView startBlock(long userId, Long refId, int block, int level, List<BankQuestion> selected) {
        var items = new ArrayList<ServedItem>();
        Map<Integer, String> kidOf = new LinkedHashMap<>();
        for (BankQuestion entry : selected) {
            int no = items.size() + 1;
            items.add(factory.create(entry.question(), no).orElseThrow());
            kidOf.put(no, entry.kid());
        }
        return practice.start(new StartPracticeCommand(userId, PracticeSource.PLACEMENT,
                refId, null, items, List.of(), null,
                Map.of("placement", Map.of("block", block, "level", level, "kidOf", kidOf))));
    }

    private List<Attempt> runBlocks(long userId, long runId, boolean lock) {
        var query = entities.createQuery("""
                SELECT a FROM Attempt a WHERE a.userId = :userId AND a.refType = 'PLACEMENT'
                    AND (a.id = :runId OR a.refId = :runId)
                ORDER BY a.servedAt DESC, a.id DESC
                """, Attempt.class).setParameter("userId", userId).setParameter("runId", runId);
        if (lock) query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        return query.getResultList();
    }

    private static Attempt root(List<Attempt> blocks, long runId) {
        return blocks.stream().filter(a -> a.getId() == runId && a.getRefId() == null).findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.ATTEMPT_NOT_OWNED, HttpStatus.FORBIDDEN,
                        "Bạn không có quyền truy cập lượt luyện này.", Map.of()));
    }

    @Transactional(readOnly = true)
    public Result result(long userId, long runId) {
        List<Attempt> blocks = runBlocks(userId, runId, false);
        Attempt root = root(blocks, runId);
        if (!hasConclusion(root)) {
            throw new ApiException(ErrorCode.PLACEMENT_NOT_FINISHED, HttpStatus.CONFLICT,
                    "Lượt xếp trình độ chưa kết luận.", Map.of());
        }
        List<Attempt> submitted = blocks.stream().filter(a -> "SUBMITTED".equals(a.getStatus()))
                .sorted(Comparator.comparingInt(a -> placement(a).path("block").intValue())).toList();
        Map<Long, Set<Integer>> correctNumbers = correctNumbers(submitted);
        Map<Integer, Counts> levels = new TreeMap<>();
        Map<KnowledgeDomain, Counts> domains = new EnumMap<>(KnowledgeDomain.class);
        Map<String, Counts> kids = new TreeMap<>();
        List<BlockResult> evidence = new ArrayList<>();
        int itemCount = 0;
        for (Attempt attempt : submitted) {
            int level = placement(attempt).path("level").intValue();
            JsonNode kidOf = placement(attempt).path("kidOf");
            Set<Integer> correct = correctNumbers.getOrDefault(attempt.getId(), Set.of());
            JsonNode items = attempt.getServedItems().path("items");
            int blockCorrect = 0;
            for (JsonNode item : items) {
                int no = item.path("no").intValue();
                boolean right = correct.contains(no);
                if (right) blockCorrect++;
                levels.computeIfAbsent(level, ignored -> new Counts()).add(right);
                String kid = kidOf.path(Integer.toString(no)).textValue();
                KnowledgeDomain domain = KnowledgeDomain.valueOf(kid.substring(0, 2));
                domains.computeIfAbsent(domain, ignored -> new Counts()).add(right);
                kids.computeIfAbsent(kid, ignored -> new Counts()).add(right);
            }
            evidence.add(new BlockResult(level, blockCorrect, items.size()));
            itemCount += items.size();
        }

        Conclusion conclusion = conclusion(root, evidence);
        List<LevelScore> byLevel = levels.entrySet().stream().map(entry -> {
            Counts count = entry.getValue();
            return new LevelScore(entry.getKey(), count.correct, count.total, count.percent());
        }).toList();
        List<DomainScore> domainScores = java.util.Arrays.stream(KnowledgeDomain.values()).map(domain -> {
            Counts count = domains.getOrDefault(domain, new Counts());
            return new DomainScore(domain, count.correct, count.total, count.total == 0 ? null : count.percent(),
                    count.total > 0, count.total > 0 && count.total < 3);
        }).toList();
        return new Result(runId, completedAt(root), conclusion.level(), conclusion.confidence(),
                itemCount, byLevel, domainScores, wrongKids(kids));
    }

    private Map<Long, Set<Integer>> correctNumbers(List<Attempt> submitted) {
        if (submitted.isEmpty()) return Map.of();
        return entities.createQuery("""
                SELECT i FROM AttemptItem i WHERE i.attemptId IN :ids AND i.correct = true
                """, AttemptItem.class).setParameter("ids", submitted.stream().map(Attempt::getId).toList())
                .getResultList().stream().collect(Collectors.groupingBy(AttemptItem::getAttemptId,
                        Collectors.mapping(AttemptItem::getOrderIndex, Collectors.toSet())));
    }

    private List<WrongKid> wrongKids(Map<String, Counts> kids) {
        var wrong = kids.entrySet().stream().filter(entry -> entry.getValue().correct < entry.getValue().total).toList();
        if (wrong.isEmpty()) return List.of();
        Map<String, String> names = knowledge.nodes().stream()
                .collect(Collectors.toMap(KnowledgeNode::kid, KnowledgeNode::nameVi));
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
        List<BlockResult> evidence = blockResults(attempts.stream()
                .filter(a -> runId(a.getId(), a.getRefId()) == root.getId()).toList());
        Conclusion result = conclusion(root, evidence);
        return new Completed(root.getId(), result.level(), result.confidence(), completedAt(root));
    }

    private Conclusion conclusion(Attempt root, List<BlockResult> evidence) {
        boolean exhausted = placement(root).path("conclusion").path("bankExhausted").asBoolean();
        if (algorithm.decide(evidence, config, exhausted) instanceof Conclusion result) return result;
        throw new IllegalStateException("Concluded placement run does not have a conclusion");
    }

    private static List<BlockResult> blockResults(List<Attempt> attempts) {
        return attempts.stream().filter(a -> "SUBMITTED".equals(a.getStatus()))
                .sorted(Comparator.comparingInt(a -> placement(a).path("block").intValue()))
                .map(a -> new BlockResult(placement(a).path("level").intValue(), Math.toIntExact(a.getScore()),
                        a.getServedItems().path("items").size()))
                .toList();
    }

    private static JsonNode placement(Attempt attempt) {
        return attempt.getServedItems().path("meta").path("placement");
    }

    static Set<Long> servedQuestionIds(List<Attempt> attempts, long run) {
        return attempts.stream().filter(a -> runId(a.getId(), a.getRefId()) == run)
                .flatMap(a -> a.getServedItems().path("items").valueStream())
                .map(item -> item.path("questionId")).filter(JsonNode::isIntegralNumber)
                .map(JsonNode::longValue).collect(Collectors.toSet());
    }

    private Map<Integer, List<BankQuestion>> bank() {
        return entities.createQuery("""
                SELECT q AS question, kp.code AS kid, kp.domain AS domain
                FROM QuestionKnowledgePoint label
                JOIN Question q ON q.id = label.questionId
                JOIN KnowledgePoint kp ON kp.id = label.knowledgePointId
                WHERE label.role = com.cnhsk.learning.knowledge.entity.KnowledgeLabelRole.PRIMARY
                  AND q.status = com.cnhsk.learning.knowledge.entity.QuestionStatus.APPROVED
                  AND q.hskLevel BETWEEN 1 AND 3
                ORDER BY q.id
                """, Tuple.class).getResultList().stream()
                .map(row -> new BankQuestion(row.get("question", Question.class), row.get("kid", String.class),
                        row.get("domain").toString()))
                .filter(entry -> factory.isUsable(entry.question()))
                .collect(Collectors.groupingBy(entry -> entry.question().getHskLevel().intValue()));
    }

    List<BankQuestion> selectBlock(List<BankQuestion> bank, Set<Long> alreadyServed, Set<Long> previous) {
        List<BankQuestion> available = bank.stream().filter(q -> !alreadyServed.contains(q.question().getId())).toList();
        if (available.size() < config.getBlockSize()) return List.of();
        List<BankQuestion> selected = new ArrayList<>();
        takeByDomain(available.stream().filter(q -> !previous.contains(q.question().getId())).toList(), selected);
        if (selected.size() < config.getBlockSize()) {
            takeByDomain(available.stream().filter(q -> previous.contains(q.question().getId())).toList(), selected);
        }
        return List.copyOf(selected);
    }

    private void takeByDomain(List<BankQuestion> pool, List<BankQuestion> selected) {
        Map<String, List<BankQuestion>> groups = pool.stream().collect(Collectors.groupingBy(
                BankQuestion::domain, TreeMap::new, Collectors.toCollection(ArrayList::new)));
        List<ArrayDeque<BankQuestion>> queues = new ArrayList<>();
        for (List<BankQuestion> group : groups.values()) {
            shuffle(group);
            queues.add(new ArrayDeque<>(group));
        }
        shuffle(queues);
        while (selected.size() < config.getBlockSize() && !queues.isEmpty()) {
            for (var queue : queues) {
                if (!queue.isEmpty()) selected.add(queue.removeFirst());
                if (selected.size() == config.getBlockSize()) return;
            }
            queues.removeIf(ArrayDeque::isEmpty);
        }
    }

    private <T> void shuffle(List<T> items) {
        for (int i = items.size() - 1; i > 0; i--) Collections.swap(items, i, random.nextInt(i + 1));
    }

    record BankQuestion(Question question, String kid, String domain) {}
    private record BankShortage(int level, int available) {}
    public record Active(long runId, long sessionId) {}
    public record Completed(long runId, PlacementLevel level, Confidence confidence, Instant completedAt) {}
    public record Summary(Active active, Completed last) {}
    public record LevelScore(int level, int correct, int total, int percent) {}
    public record DomainScore(KnowledgeDomain domain, int correct, int total, Integer percent,
            boolean measured, boolean tentative) {}
    public record WrongKid(String kid, String nameVi, int wrong, int total) {}
    public record Result(long runId, Instant completedAt, PlacementLevel level, Confidence confidence,
            int itemCount, List<LevelScore> byLevel, List<DomainScore> domains, List<WrongKid> wrongKids) {}
    public record StartResult(long runId, SessionView session, boolean created) {}
}
