package com.cnhsk.learning.exam;

import java.time.Clock;
import java.time.Instant;
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
import com.cnhsk.learning.exam.PlacementAlgorithm.Confidence;
import com.cnhsk.learning.exam.PlacementAlgorithm.PlacementLevel;
import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeService.SessionView;
import com.cnhsk.learning.practice.PracticeService.StartPracticeCommand;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.entity.Attempt;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
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
    private final RandomGenerator random;
    private final PlacementAlgorithm algorithm = new PlacementAlgorithm();
    private final QuestionItemFactory factory = new QuestionItemFactory();

    public PlacementService(PlacementConfig config, PracticeService practice, EntityManager entities,
            NamedParameterJdbcTemplate jdbc, Clock clock, @Qualifier("practiceChoiceRandom") RandomGenerator random) {
        this.config = config;
        this.practice = practice;
        this.entities = entities;
        this.jdbc = jdbc;
        this.clock = clock;
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
        var items = new ArrayList<ServedItem>();
        Map<Integer, String> kidOf = new LinkedHashMap<>();
        for (BankQuestion entry : selected) {
            int no = items.size() + 1;
            items.add(factory.create(entry.question(), no).orElseThrow());
            kidOf.put(no, entry.kid());
        }
        SessionView session = practice.start(new StartPracticeCommand(userId, PracticeSource.PLACEMENT,
                null, null, items, List.of(), null,
                Map.of("placement", Map.of("block", 1, "level", level, "kidOf", kidOf))));
        return new StartResult(runId(session.sessionId(), session.refId()), session, true);
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
        List<BlockResult> blocks = attempts.stream()
                .filter(a -> runId(a.getId(), a.getRefId()) == root.getId() && "SUBMITTED".equals(a.getStatus()))
                .sorted(Comparator.comparingInt(a -> placement(a).path("block").asInt()))
                .map(a -> new BlockResult(placement(a).path("level").asInt(), Math.toIntExact(a.getScore()),
                        a.getServedItems().path("items").size()))
                .toList();
        boolean exhausted = placement(root).path("conclusion").path("bankExhausted").asBoolean();
        if (!(algorithm.decide(blocks, config, exhausted) instanceof Conclusion result)) {
            throw new IllegalStateException("Concluded placement run does not have a conclusion");
        }
        return new Completed(root.getId(), result.level(), result.confidence(), completedAt(root));
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
    public record StartResult(long runId, SessionView session, boolean created) {}
}
