package com.cnhsk.learning.mastery;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cnhsk.learning.knowledge.KnowledgeTreeService;
import com.cnhsk.learning.knowledge.KnowledgeTreeService.KnowledgeNode;
import com.cnhsk.learning.knowledge.PersonalizedPageRank;
import com.cnhsk.learning.mastery.MasteryQueryService.MasteryNode;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds today's recommendations from the mastery read model and current knowledge tree. */
@Service
public class RecommendationService {

    private static final int MAX_RECOMMENDATIONS = 5;
    private static final Comparator<MasteryNode> REVIEW_ORDER = Comparator
            .comparing(MasteryNode::value, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(MasteryNode::kid);

    private final MasteryQueryService mastery;
    private final KnowledgeTreeService tree;
    private final MasteryQueryRepository queries;
    private final Clock clock;

    public RecommendationService(MasteryQueryService mastery, KnowledgeTreeService tree,
            MasteryQueryRepository queries, Clock clock) {
        this.mastery = mastery;
        this.tree = tree;
        this.queries = queries;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Recommendation> recommendations(long userId) {
        List<MasteryNode> masteryNodes = mastery.mastery(userId).nodes();
        Instant now = clock.instant();
        List<KnowledgeNode> knowledgeNodes = tree.nodes();
        Map<String, KnowledgeNode> knowledgeByKid = knowledgeNodes.stream()
                .collect(Collectors.toMap(KnowledgeNode::kid, node -> node));
        Map<String, MasteryNode> masteryByKid = masteryNodes.stream()
                .collect(Collectors.toMap(MasteryNode::kid, node -> node));
        Set<String> kidsWithQuestions = queries.kidsWithApprovedQuestions(knowledgeByKid.keySet());

        List<Recommendation> result = new ArrayList<>(MAX_RECOMMENDATIONS);
        Set<String> includedKids = new HashSet<>();
        Map<String, Double> rootScores = rootScores(masteryNodes, knowledgeNodes);
        List<MasteryNode> reviewNodes = masteryNodes.stream()
                .filter(node -> "review".equals(node.state()))
                .filter(node -> knowledgeByKid.containsKey(node.kid()) && kidsWithQuestions.contains(node.kid()))
                .sorted(REVIEW_ORDER)
                .toList();
        // For each weak node that other weak nodes depend on, the weakest of those dependents.
        Map<String, KnowledgeNode> heldBy = new HashMap<>();
        for (MasteryNode node : reviewNodes) {
            KnowledgeNode knowledgeNode = knowledgeByKid.get(node.kid());
            for (String ancestor : ancestors(knowledgeNode, knowledgeByKid)) {
                heldBy.putIfAbsent(ancestor, knowledgeNode);
            }
        }

        reviewNodes.forEach(node -> {
                    if (result.size() >= MAX_RECOMMENDATIONS) {
                        return;
                    }
                    KnowledgeNode knowledgeNode = knowledgeByKid.get(node.kid());
                    // Every weak prerequisite behind this node, at any depth, strongest root first.
                    ancestors(knowledgeNode, knowledgeByKid).stream()
                            .map(masteryByKid::get)
                            .filter(prerequisite -> prerequisite != null && "review".equals(prerequisite.state()))
                            .filter(prerequisite -> kidsWithQuestions.contains(prerequisite.kid()))
                            .sorted(Comparator.comparingDouble(
                                    (MasteryNode prerequisite) -> rootScores.getOrDefault(prerequisite.kid(), 0.0))
                                    .reversed().thenComparing(MasteryNode::kid))
                            .forEach(prerequisite -> addPrerequisite(result, includedKids, prerequisite,
                                    knowledgeByKid, knowledgeNode));
                    KnowledgeNode held = heldBy.get(node.kid());
                    if (held != null) {
                        addPrerequisite(result, includedKids, node, knowledgeByKid, held);
                    } else {
                        add(result, includedKids, node, knowledgeNode, "REVIEW",
                                "Cần ôn: " + formatValue(node.value()) + "/100.");
                    }
                });

        masteryNodes.stream()
                .filter(node -> "consolidate".equals(node.state()))
                .filter(node -> node.dueAt() != null && !node.dueAt().isAfter(now))
                .filter(node -> kidsWithQuestions.contains(node.kid()))
                .sorted(Comparator.comparing(MasteryNode::kid))
                .forEach(node -> {
                    if (result.size() < MAX_RECOMMENDATIONS) {
                        KnowledgeNode knowledgeNode = knowledgeByKid.get(node.kid());
                        if (knowledgeNode != null) {
                            add(result, includedKids, node, knowledgeNode, "CONSOLIDATE",
                                    "Đến hạn củng cố " + knowledgeNode.nameVi() + ".");
                        }
                    }
                });

        masteryNodes.stream()
                .filter(node -> ("notyet".equals(node.state()) || "insufficient".equals(node.state()))
                        && node.frontier())
                .filter(node -> kidsWithQuestions.contains(node.kid()))
                .sorted(Comparator.comparingInt((MasteryNode node) -> knowledgeByKid.get(node.kid()).hskLevel())
                        .thenComparing(MasteryNode::kid))
                .forEach(node -> {
                    if (result.size() < MAX_RECOMMENDATIONS) {
                        KnowledgeNode knowledgeNode = knowledgeByKid.get(node.kid());
                        add(result, includedKids, node, knowledgeNode, "NEW",
                                "Nút mới đã đủ tiên quyết.");
                    }
                });

        return List.copyOf(result);
    }

    /**
     * This learner's weak nodes, weighted by how far they are below full mastery, traced back along
     * prerequisite links: a prerequisite holding up several weak nodes scores highest.
     */
    private static Map<String, Double> rootScores(List<MasteryNode> masteryNodes, List<KnowledgeNode> knowledgeNodes) {
        Map<String, Double> seeds = new HashMap<>();
        for (MasteryNode node : masteryNodes) {
            if ("review".equals(node.state()) && node.value() != null) {
                seeds.put(node.kid(), Math.max(1.0, 100.0 - node.value().doubleValue()));
            }
        }
        Map<String, Map<String, Double>> links = new HashMap<>();
        for (KnowledgeNode node : knowledgeNodes) {
            for (String prerequisite : node.prereqKids()) {
                links.computeIfAbsent(node.kid(), ignored -> new HashMap<>()).put(prerequisite, 1.0);
            }
        }
        return PersonalizedPageRank.rank(seeds, links);
    }

    /** Every prerequisite reachable from the node, nearest first; the tree has no cycles but is guarded anyway. */
    private static List<String> ancestors(KnowledgeNode node, Map<String, KnowledgeNode> knowledgeByKid) {
        Set<String> seen = new LinkedHashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>(node.prereqKids());
        while (!pending.isEmpty()) {
            String kid = pending.removeFirst();
            if (kid.equals(node.kid()) || !seen.add(kid)) continue;
            KnowledgeNode prerequisite = knowledgeByKid.get(kid);
            if (prerequisite != null) pending.addAll(prerequisite.prereqKids());
        }
        return List.copyOf(seen);
    }

    private static void addPrerequisite(List<Recommendation> result, Set<String> includedKids,
            MasteryNode prerequisite, Map<String, KnowledgeNode> knowledgeByKid, KnowledgeNode target) {
        KnowledgeNode prerequisiteNode = knowledgeByKid.get(prerequisite.kid());
        if (prerequisiteNode != null) {
            String reason = target.prereqKids().contains(prerequisite.kid())
                    ? "Ôn " + prerequisiteNode.nameVi() + " trước khi ôn " + target.nameVi() + "."
                    : "Ôn " + prerequisiteNode.nameVi() + " trước: đây là phần gốc của " + target.nameVi() + ".";
            add(result, includedKids, prerequisite, prerequisiteNode, "PREREQ", reason);
        }
    }

    private static void add(List<Recommendation> result, Set<String> includedKids, MasteryNode node,
            KnowledgeNode knowledgeNode, String kind, String reason) {
        if (result.size() < MAX_RECOMMENDATIONS && includedKids.add(node.kid())) {
            result.add(new Recommendation(node.kid(), knowledgeNode.nameVi(), kind, reason, node.value()));
        }
    }

    private static String formatValue(BigDecimal value) {
        if (value == null) {
            throw new IllegalStateException("Review recommendation must have a mastery value");
        }
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
        format.setMinimumFractionDigits(1);
        format.setMaximumFractionDigits(1);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value.setScale(1, RoundingMode.HALF_UP));
    }

    public record Recommendation(String kid, String nameVi, String kind, String reason,
            @JsonInclude(JsonInclude.Include.ALWAYS) @Schema(types = {"number", "null"}) BigDecimal value) { }
}
