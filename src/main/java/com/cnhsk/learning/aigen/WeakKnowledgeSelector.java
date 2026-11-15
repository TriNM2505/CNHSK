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
        Instant now = clock.instant();
        Settings settings = new Settings(config.windowDays(), config.minWrong(), config.minBank(),
                config.maxBatchesPerRun());
        cleanup.executeWithoutResult(status -> repository.failStale(now));
        if (settings.max() == 0) return List.of();
        return reads.execute(status -> select(now, settings));
    }

    private List<Candidate> select(Instant now, Settings settings) {
        List<Contribution> contributions = new ArrayList<>();
        Map<String, Long> wrongCounts = new HashMap<>();
        Map<Long, Map<String, String>> optionsByQuestion = new HashMap<>();
        for (WrongAnswer answer : repository.wrongAnswers(now.minus(settings.windowDays(), ChronoUnit.DAYS), now)) {
            Map<String, String> errors = optionsByQuestion.computeIfAbsent(answer.questionId(),
                    ignored -> errors(answer.optionsJson()));
            String errorKid = errors.get(answer.answer());
            contribute(wrongCounts, answer.primaryKid(), answer.count());
            contribute(wrongCounts, errorKid, answer.count());
            contributions.add(new Contribution(answer.userId(), answer.primaryKid(), errorKid, answer.count()));
        }
        Set<String> kids = wrongCounts.entrySet().stream().filter(entry -> entry.getValue() >= settings.minWrong())
                .map(Map.Entry::getKey).collect(Collectors.toSet());
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
                candidates.add(new Candidate(node.knowledgePointId(), node.kid(), wrongCounts.get(node.kid()),
                        node.bankCount(), 0));
            }
        }
        candidates.sort(ORDER);
        if (candidates.size() > settings.max()
                && candidates.get(settings.max() - 1).wrongCount() == candidates.get(settings.max()).wrongCount()) {
            long boundary = candidates.get(settings.max()).wrongCount();
            Set<String> tiedKids = candidates.stream().filter(candidate -> candidate.wrongCount() == boundary)
                    .map(Candidate::kid).collect(Collectors.toCollection(LinkedHashSet::new));
            Map<String, Integer> weakUsers = weakUsers(tiedKids, contributions);
            candidates.replaceAll(candidate -> new Candidate(candidate.knowledgePointId(), candidate.kid(),
                    candidate.wrongCount(), candidate.bankCount(), weakUsers.getOrDefault(candidate.kid(), 0)));
            candidates.sort(ORDER);
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
