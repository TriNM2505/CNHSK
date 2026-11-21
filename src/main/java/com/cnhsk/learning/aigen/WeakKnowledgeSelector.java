package com.cnhsk.learning.aigen;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.cnhsk.learning.aigen.WeakKnowledgeQueryRepository.NodeSummary;
import com.cnhsk.learning.aigen.WeakKnowledgeQueryRepository.WrongAnswer;
import com.cnhsk.learning.knowledge.entity.QuestionOption;
import com.cnhsk.learning.mastery.MasteryReader;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Selects candidates only; the generation caller must claim and recheck them. */
@Service
public class WeakKnowledgeSelector {

    private static final Logger log = LoggerFactory.getLogger(WeakKnowledgeSelector.class);
    private static final TypeReference<List<QuestionOption>> OPTIONS = new TypeReference<>() { };
    private static final Pattern KID = Pattern.compile("(NA|CH|TV|NP|NL|VB)[1-7]-[0-9]{3}");
    private static final int MAX_MASTERY_USERS = 100;
    private static final Comparator<Candidate> ORDER = Comparator.comparingLong(Candidate::wrongCount).reversed()
            .thenComparing(Comparator.comparingInt(Candidate::weakUsers).reversed()).thenComparing(Candidate::kid);

    private final WeakKnowledgeQueryRepository repository;
    private final AiGenerateConfig config;
    private final MasteryReader mastery;
    private final Clock clock;
    private final ObjectMapper mapper;
    private final TransactionTemplate cleanup;
    private final TransactionTemplate reads;

    public WeakKnowledgeSelector(WeakKnowledgeQueryRepository repository, AiGenerateConfig config,
            MasteryReader mastery, Clock clock, PlatformTransactionManager transactions, ObjectMapper mapper) {
        this.repository = repository;
        this.config = config;
        this.mastery = mastery;
        this.clock = clock;
        this.mapper = mapper;
        cleanup = new TransactionTemplate(transactions);
        cleanup.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        cleanup.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        reads = new TransactionTemplate(transactions);
        reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        reads.setReadOnly(true);
    }

    public List<Candidate> select() {
        return select(false);
    }

    /** Generation can continue past an unreadable legacy question without changing strict selection callers. */
    public List<Candidate> selectForGeneration() {
        return select(true);
    }

    private List<Candidate> select(boolean skipMalformed) {
        Instant now = clock.instant();
        Settings settings = new Settings(config.windowDays(), config.minWrong(), config.minBank(),
                config.maxBatchesPerRun());
        cleanup.executeWithoutResult(status -> repository.failStale(now));
        if (settings.max() == 0) return List.of();
        return reads.execute(status -> select(now, settings, skipMalformed));
    }

    private List<Candidate> select(Instant now, Settings settings, boolean skipMalformed) {
        List<Contribution> contributions = new ArrayList<>();
        Map<String, Long> wrongCounts = new HashMap<>();
        Map<Long, Map<String, String>> optionsByQuestion = new HashMap<>();
        Set<Long> invalidQuestions = new HashSet<>();
        for (WrongAnswer answer : repository.wrongAnswers(now.minus(settings.windowDays(), ChronoUnit.DAYS), now)) {
            if (invalidQuestions.contains(answer.questionId())) continue;
            Map<String, String> errors;
            try {
                errors = optionsByQuestion.computeIfAbsent(answer.questionId(), ignored -> skipMalformed
                        ? generationErrors(answer.optionsJson()) : errors(answer.optionsJson()));
            } catch (IllegalStateException exception) {
                if (!skipMalformed) throw exception;
                invalidQuestions.add(answer.questionId());
                log.warn("Skipping question with malformed options: {}", answer.questionId());
                continue;
            }
            String errorKid = errors.get(answer.answer());
            contribute(wrongCounts, answer.primaryKid(), answer.count());
            contribute(wrongCounts, errorKid, answer.count());
            contributions.add(new Contribution(answer.userId(), answer.primaryKid(), errorKid, answer.count()));
        }
        if (wrongCounts.isEmpty()) return List.of();
        // Wrong answers flow back along prerequisite links, so a shared prerequisite of several weak
        // nodes can qualify by its root score; without links the score is the raw count.
        Map<String, Long> rootScores = RootCauseRanker.scores(wrongCounts, repository.prerequisiteLinks());
        Set<String> kids = rootScores.keySet().stream()
                .filter(kid -> wrongCounts.getOrDefault(kid, 0L) >= settings.minWrong()
                        || rootScores.get(kid) >= settings.minWrong())
                .collect(Collectors.toSet());
        if (kids.isEmpty()) return List.of();
        List<NodeSummary> nodes = repository.nodes(kids);
        Set<String> running = runningKids(nodes);
        List<Candidate> candidates = new ArrayList<>();
        for (NodeSummary node : nodes) {
            if (node.canDo() == null || node.canDo().isBlank() || !node.hasLexemes()) {
                log.info("Skipping generation candidate without required content: {}", node.kid());
                continue;
            }
            if (node.bankCount() < settings.minBank() && !node.hasPending() && !running.contains(node.kid())) {
                candidates.add(new Candidate(node.knowledgePointId(), node.kid(),
                        wrongCounts.getOrDefault(node.kid(), 0L), node.bankCount(), 0));
            }
        }
        Comparator<Candidate> rank = Comparator.<Candidate>comparingLong(candidate -> rootScores.get(candidate.kid()))
                .reversed().thenComparing(Comparator.comparingLong(Candidate::wrongCount).reversed());
        Comparator<Candidate> order = rank.thenComparing(ORDER);
        candidates.sort(order);
        if (candidates.size() > settings.max()
                && rank.compare(candidates.get(settings.max() - 1), candidates.get(settings.max())) == 0) {
            Candidate boundary = candidates.get(settings.max());
            Set<String> tiedKids = candidates.stream().filter(candidate -> rank.compare(candidate, boundary) == 0)
                    .map(Candidate::kid).collect(Collectors.toCollection(LinkedHashSet::new));
            Map<String, Integer> weakUsers = weakUsers(tiedKids, contributions);
            candidates.replaceAll(candidate -> new Candidate(candidate.knowledgePointId(), candidate.kid(),
                    candidate.wrongCount(), candidate.bankCount(), weakUsers.getOrDefault(candidate.kid(), 0)));
            candidates.sort(order);
        }
        return List.copyOf(candidates.subList(0, Math.min(settings.max(), candidates.size())));
    }

    private Map<String, Integer> weakUsers(Set<String> tiedKids, List<Contribution> contributions) {
        Map<Long, Long> itemCounts = new HashMap<>();
        Map<Long, Set<String>> contributedKids = new HashMap<>();
        for (Contribution contribution : contributions) {
            Set<String> relevant = new HashSet<>();
            if (tiedKids.contains(contribution.primaryKid())) relevant.add(contribution.primaryKid());
            if (tiedKids.contains(contribution.errorKid())) relevant.add(contribution.errorKid());
            if (!relevant.isEmpty()) {
                // A wrong item counts once for user ranking, even when both labels contribute.
                itemCounts.merge(contribution.userId(), contribution.count(), Math::addExact);
                contributedKids.computeIfAbsent(contribution.userId(), ignored -> new HashSet<>()).addAll(relevant);
            }
        }
        List<Long> users = itemCounts.keySet().stream().sorted(Comparator
                .<Long>comparingLong(itemCounts::get).reversed().thenComparingLong(Long::longValue))
                .limit(MAX_MASTERY_USERS).toList();
        Map<String, Integer> result = new HashMap<>();
        Set<String> requested = Set.copyOf(tiedKids);
        for (long user : users) {
            Map<String, MasteryReader.MasteryView> values = mastery.valuesOf(user, requested);
            for (String kid : contributedKids.get(user)) {
                MasteryReader.MasteryView value = values.get(kid);
                if (value != null && "review".equals(value.state())) result.merge(kid, 1, Integer::sum);
            }
        }
        return result;
    }

    private Map<String, String> errors(String json) {
        try {
            List<QuestionOption> options = mapper.readValue(json, OPTIONS);
            if (options == null) throw new IllegalStateException("Invalid generation question options");
            Map<String, String> result = new HashMap<>();
            Set<String> keys = new HashSet<>();
            for (QuestionOption option : options) {
                if (option == null || option.key() == null || option.key().isBlank() || !keys.add(option.key())) {
                    throw new IllegalStateException("Invalid generation question options");
                }
                if (!option.isCorrect() && option.errorKid() != null && !option.errorKid().isBlank()) {
                    result.put(option.key(), option.errorKid());
                }
            }
            return result;
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Cannot read generation question options");
        }
    }

    private Map<String, String> generationErrors(String json) {
        try {
            JsonNode options = mapper.readTree(json);
            if (options == null || !options.isArray()) throw new IllegalStateException("Invalid generation question options");
            Set<String> keys = new HashSet<>();
            Map<String, String> result = new HashMap<>();
            for (JsonNode option : options) {
                if (!option.isObject() || !option.path("key").isTextual() || option.get("key").textValue().isBlank()
                        || !option.path("text").isTextual() || option.get("text").textValue().isBlank()
                        || !option.path("is_correct").isBoolean() || !keys.add(option.get("key").textValue())) {
                    throw new IllegalStateException("Invalid generation question options");
                }
                JsonNode kid = option.get("error_kid");
                if (kid != null && !kid.isNull() && !kid.isTextual()) throw new IllegalStateException("Invalid generation question options");
                if (!option.get("is_correct").booleanValue() && kid != null && kid.isTextual() && !kid.textValue().isBlank()) {
                    result.put(option.get("key").textValue(), kid.textValue());
                }
            }
            return result;
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Cannot read generation question options");
        }
    }

    private Set<String> runningKids(List<NodeSummary> nodes) {
        Map<String, Long> idsByKid = nodes.stream().collect(Collectors.toMap(NodeSummary::kid,
                NodeSummary::knowledgePointId));
        Map<Long, String> kidsById = nodes.stream().collect(Collectors.toMap(NodeSummary::knowledgePointId,
                NodeSummary::kid));
        Set<String> result = new HashSet<>();
        for (WeakKnowledgeQueryRepository.ActiveRun run : repository.activeRuns()) {
            try {
                JsonNode payload = mapper.readTree(run.payloadJson());
                JsonNode kid = payload == null ? null : payload.get("kid");
                JsonNode id = payload == null ? null : payload.get("knowledgePointId");
                if (payload == null || !payload.isObject() || kid == null || !kid.isTextual()
                        || !KID.matcher(kid.textValue()).matches() || id == null || !id.isIntegralNumber()
                        || !id.canConvertToLong() || id.longValue() <= 0) {
                    throw new IllegalStateException("Invalid active generation payload");
                }
                if ((idsByKid.containsKey(kid.textValue()) && idsByKid.get(kid.textValue()) != id.longValue())
                        || (kidsById.containsKey(id.longValue())
                        && !kidsById.get(id.longValue()).equals(kid.textValue()))) {
                    throw new IllegalStateException("Inconsistent active generation identity");
                }
                result.add(kid.textValue());
            } catch (IOException | IllegalArgumentException exception) {
                throw new IllegalStateException("Cannot read active generation payload");
            }
        }
        return result;
    }

    private static void contribute(Map<String, Long> counts, String kid, long count) {
        if (kid != null && !kid.isBlank()) counts.merge(kid, count, Math::addExact);
    }

    private record Settings(int windowDays, int minWrong, int minBank, int max) { }
    private record Contribution(long userId, String primaryKid, String errorKid, long count) { }
    public record Candidate(long knowledgePointId, String kid, long wrongCount, long bankCount, int weakUsers) { }
}
