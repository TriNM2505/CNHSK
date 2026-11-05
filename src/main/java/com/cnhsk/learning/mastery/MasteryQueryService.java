package com.cnhsk.learning.mastery;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.cnhsk.learning.knowledge.KnowledgeTreeService;
import com.cnhsk.learning.knowledge.KnowledgeTreeService.KnowledgeNode;
import com.cnhsk.learning.knowledge.KnowledgeTreeService.QuestionKids;
import com.cnhsk.learning.knowledge.entity.QuestionOption;
import com.cnhsk.learning.mastery.FrontierService.Decoration;
import com.cnhsk.learning.mastery.FrontierService.MasterySnapshot;
import com.cnhsk.learning.mastery.MasteryConfig.EvidenceSettings;
import com.cnhsk.learning.mastery.MasteryConfig.Settings;
import com.cnhsk.learning.mastery.MasteryQueryRepository.EvidenceRow;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads and recalculates the caller's mastery without writing progress rows. */
@Service
public class MasteryQueryService {

    private static final Logger log = LoggerFactory.getLogger(MasteryQueryService.class);
    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private static final BigDecimal HALF = new BigDecimal("0.5");

    private final KnowledgeProgressRepository progress;
    private final MasteryQueryRepository queries;
    private final KnowledgeTreeService tree;
    private final MasteryConfig config;
    private final Clock clock;
    private final MasteryCalculator calculator = new MasteryCalculator();
    private final MasteryStateMachine stateMachine = new MasteryStateMachine();
    private final FrontierService frontier;

    public MasteryQueryService(KnowledgeProgressRepository progress, MasteryQueryRepository queries,
            KnowledgeTreeService tree, MasteryConfig config, Clock clock, FrontierService frontier) {
        this.progress = progress;
        this.queries = queries;
        this.tree = tree;
        this.config = config;
        this.clock = clock;
        this.frontier = frontier;
    }

    @Transactional(readOnly = true)
    public MasteryTree mastery(long userId) {
        ReadSnapshot snapshot = read(userId);
        Map<String, MasteryNode> nodes = masteryNodes(snapshot);
        return new MasteryTree(snapshot.nodes().stream().map(node -> nodes.get(node.kid())).toList());
    }

    @Transactional(readOnly = true)
    public MasteryNodeDetail node(long userId, String kid) {
        ReadSnapshot snapshot = read(userId);
        KnowledgeNode node = snapshot.nodes().stream().filter(candidate -> candidate.kid().equals(kid))
                .findFirst().orElseThrow(MasteryQueryService::notFound);
        Map<String, MasteryNode> all = masteryNodes(snapshot);
        MasteryNode mastery = all.get(kid);
        List<PrereqView> prereqs = node.prereqKids().stream().map(prerequisite ->
                new PrereqView(prerequisite, nameOf(snapshot.nodes(), prerequisite),
                        all.get(prerequisite).state())).toList();
        ProgressSnapshot saved = snapshot.progressById().get(node.id());
        Set<Long> savedIds = saved == null ? Set.of() : saved.itemIds();
        List<AllocatedEvidence> currentEvidence = snapshot.byKid().getOrDefault(kid, List.of()).stream()
                .filter(item -> savedIds.contains(item.itemId())).toList();
        List<Long> windowIds = calculator.calculate(currentEvidence.stream()
                        .map(AllocatedEvidence::calculatorEvidence).toList(), snapshot.today(), config.settings())
                .evidence().stream().map(MasteryCalculator.Evidence::itemId).toList();
        Map<Long, AllocatedEvidence> byItemId = currentEvidence.stream().collect(Collectors.toMap(
                AllocatedEvidence::itemId, item -> item, (first, ignored) -> first));
        List<EvidenceView> evidence = windowIds.stream().map(byItemId::get).filter(Objects::nonNull)
                .map(item -> new EvidenceView(item.answeredAt(), item.source(), item.context(), item.form(),
                        item.role(), item.score(), decayedWeight(item, snapshot.today())))
                .toList();
        long approvedQuestionCount = tree.detail(kid).approvedQuestionCount();
        return new MasteryNodeDetail(mastery.kid(), mastery.value(), mastery.confidence(), mastery.evidenceCount(),
                mastery.state(), mastery.frontier(), mastery.dueAt(), prereqs, evidence, approvedQuestionCount);
    }

    @Transactional(readOnly = true)
    public Map<String, ReaderValue> valuesOf(long userId, Set<String> kids) {
        if (kids.isEmpty()) {
            return Map.of();
        }
        ReadSnapshot snapshot = read(userId);
        Map<String, MasteryNode> nodes = masteryNodes(snapshot);
        Map<String, ReaderValue> result = new LinkedHashMap<>();
        for (String kid : kids) {
            MasteryNode node = nodes.get(kid);
            if (node == null || node.value() == null) {
                result.put(kid, new ReaderValue(null, node == null ? MasteryState.NOT_YET.code() : node.state()));
            } else {
                result.put(kid, new ReaderValue(node.value().setScale(0, RoundingMode.HALF_UP).intValueExact(),
                        node.state()));
            }
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private ReadSnapshot read(long userId) {
        List<KnowledgeNode> nodes = tree.nodes();
        Map<Long, ProgressSnapshot> progressById = new HashMap<>();
        Set<Long> itemIds = new HashSet<>();
        for (KnowledgeProgress row : progress.findByUserId(userId)) {
            Set<Long> ids = new HashSet<>(Arrays.asList(row.getEvidenceItemIds()));
            progressById.put(row.getTargetId(), new ProgressSnapshot(
                    row.getMpState() == null ? MasteryState.NOT_YET : MasteryState.fromCode(row.getMpState()),
                    row.getNextReviewAt(), ids));
            itemIds.addAll(ids);
        }
        Map<String, List<AllocatedEvidence>> byKid = reallocate(userId, queries.evidence(itemIds));
        return new ReadSnapshot(nodes, progressById, byKid, clock.instant());
    }

    private Map<String, MasteryNode> masteryNodes(ReadSnapshot snapshot) {
        Settings settings = config.settings();
        Map<String, MasteryNode> calculated = new LinkedHashMap<>();
        Map<String, MasterySnapshot> current = new LinkedHashMap<>();
        for (KnowledgeNode node : snapshot.nodes()) {
            ProgressSnapshot saved = snapshot.progressById().get(node.id());
            Set<Long> itemIds = saved == null ? Set.of() : saved.itemIds();
            List<AllocatedEvidence> evidence = snapshot.byKid().getOrDefault(node.kid(), List.of()).stream()
                    .filter(item -> itemIds.contains(item.itemId())).toList();
            List<MasteryCalculator.Evidence> candidates = evidence.stream()
                    .map(AllocatedEvidence::calculatorEvidence).toList();
            MasteryCalculator.Result result = calculator.calculate(candidates, snapshot.today(), settings);
            MasteryState previous = saved == null ? MasteryState.NOT_YET : saved.state();
            MasteryStateMachine.Decision decision = stateMachine.transition(previous, result, snapshot.today(), settings);
            current.put(node.kid(), new MasterySnapshot(result.value(), result.confidence(), decision.state()));
            Instant dueAt = isDueState(decision.state()) && saved != null ? saved.nextReviewAt() : null;
            calculated.put(node.kid(), new MasteryNode(node.kid(), result.value(), result.confidence(),
                    result.evidenceCount(), decision.state().code(), false, dueAt));
        }
        Map<String, Decoration> decorations = frontier.decorate(snapshot.nodes(), current, config.inferenceSettings());
        Map<String, MasteryNode> result = new LinkedHashMap<>();
        for (KnowledgeNode node : snapshot.nodes()) {
            MasteryNode value = calculated.get(node.kid());
            Decoration decoration = decorations.get(node.kid());
            Instant dueAt = isDueState(decoration.state()) ? value.dueAt() : null;
            result.put(node.kid(), new MasteryNode(value.kid(), value.value(), value.confidence(),
                    value.evidenceCount(), decoration.state().code(), decoration.frontier(), dueAt));
        }
        return result;
    }

    private Map<String, List<AllocatedEvidence>> reallocate(long userId, List<EvidenceRow> rows) {
        Map<Long, List<EvidenceRow>> byItem = rows.stream().collect(Collectors.groupingBy(
                EvidenceRow::itemId, LinkedHashMap::new, Collectors.toList()));
        EvidenceSettings settings = config.evidenceSettings();
        Map<String, List<AllocatedEvidence>> result = new HashMap<>();
        for (List<EvidenceRow> itemRows : byItem.values()) {
            EvidenceRow row = itemRows.get(0);
            if (row.userId() != userId) {
                throw new IllegalStateException("Stored evidence does not belong to the progress owner");
            }
            String context = EvidenceAllocator.contextOf(row.source());
            if (context == null || row.correct() == null) {
                continue;
            }
            String form = EvidenceAllocator.formOf(row.served());
            int score = Boolean.TRUE.equals(row.correct()) ? 100 : 0;
            List<EvidenceAllocator.RoleAssignment> targets;
            if (row.questionId() != null) {
                QuestionKids questionKids = questionKids(itemRows);
                targets = EvidenceAllocator.questionTargets(Boolean.TRUE.equals(row.correct()), questionKids,
                        row.answer());
            } else if (row.lexemeId() != null) {
                String kid = row.lexemeKid();
                if (kid == null && row.source().startsWith("TOPIC_")) {
                    kid = row.topicKid();
                }
                targets = List.of(new EvidenceAllocator.RoleAssignment(kid, "PRIMARY"));
            } else {
                log.warn("No knowledge label for attempt item {}", row.itemId());
                continue;
            }
            for (EvidenceAllocator.RoleAssignment target : targets) {
                if (target.kid() == null) {
                    log.warn("No knowledge label for attempt item {}", row.itemId());
                    continue;
                }
                BigDecimal weight = EvidenceAllocator.staticWeight(context, form, target.role(), settings);
                result.computeIfAbsent(target.kid(), ignored -> new ArrayList<>()).add(new AllocatedEvidence(
                        target.kid(), row.itemId(), row.answeredAt(), score, row.source(), context, form,
                        target.role(), weight));
            }
        }
        return result.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                entry -> List.copyOf(entry.getValue())));
    }

    private static QuestionKids questionKids(List<EvidenceRow> rows) {
        String primary = null;
        List<String> secondary = new ArrayList<>();
        Map<String, KnowledgeTreeService.DistractorError> errors = new HashMap<>();
        EvidenceRow first = rows.get(0);
        for (EvidenceRow row : rows) {
            if ("PRIMARY".equals(row.labelRole())) primary = row.labelKid();
            if ("SECONDARY".equals(row.labelRole()) && row.labelKid() != null) secondary.add(row.labelKid());
        }
        for (QuestionOption option : first.options()) {
            if (!option.isCorrect() && option.errorKid() != null) {
                errors.put(option.key(), new KnowledgeTreeService.DistractorError(option.errorKid(), option.errorNote()));
            }
        }
        return new QuestionKids(primary, secondary, errors);
    }

    private BigDecimal decayedWeight(AllocatedEvidence evidence, Instant today) {
        Duration age = Duration.between(evidence.answeredAt(), today);
        double ageDays = age.isNegative() ? 0.0
                : age.getSeconds() / 86_400.0 + age.getNano() / 86_400_000_000_000.0;
        BigDecimal decay = BigDecimal.valueOf(Math.pow(HALF.doubleValue(), ageDays / config.settings().halfLifeDays()));
        return evidence.staticWeight().multiply(decay, PRECISION);
    }

    private static String nameOf(List<KnowledgeNode> nodes, String kid) {
        return nodes.stream().filter(node -> node.kid().equals(kid)).map(KnowledgeNode::nameVi)
                .findFirst().orElse(kid);
    }

    private static boolean isDueState(MasteryState state) {
        return state == MasteryState.REVIEW || state == MasteryState.CONSOLIDATE || state == MasteryState.EXTEND;
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Không tìm thấy nút năng lực.", Map.of());
    }

    private record ProgressSnapshot(MasteryState state, Instant nextReviewAt, Set<Long> itemIds) { }
    private record ReadSnapshot(List<KnowledgeNode> nodes, Map<Long, ProgressSnapshot> progressById,
            Map<String, List<AllocatedEvidence>> byKid, Instant today) { }
    private record AllocatedEvidence(String kid, long itemId, Instant answeredAt, int score, String source,
            String context, String form, String role, BigDecimal staticWeight) {
        private MasteryCalculator.Evidence calculatorEvidence() {
            return new MasteryCalculator.Evidence(itemId, answeredAt, staticWeight, score,
                    "PRACTICE".equals(context));
        }
    }

    public record MasteryTree(List<MasteryNode> nodes) { }
    public record MasteryNode(String kid, BigDecimal value, BigDecimal confidence, int evidenceCount,
            String state, boolean frontier, Instant dueAt) { }
    public record PrereqView(String kid, String nameVi, String state) { }
    public record EvidenceView(Instant answeredAt, String source, String context, String form,
            String role, int score, BigDecimal weight) { }
    public record MasteryNodeDetail(String kid, BigDecimal value, BigDecimal confidence, int evidenceCount,
            String state, boolean frontier, Instant dueAt, List<PrereqView> prereqs,
            List<EvidenceView> evidence, long approvedQuestionCount) { }
    public record ReaderValue(Integer value, String state) { }
}
