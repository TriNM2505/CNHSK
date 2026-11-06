package com.cnhsk.learning.mastery;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cnhsk.learning.knowledge.KnowledgeTreeService;
import com.cnhsk.learning.knowledge.KnowledgeTreeService.KnowledgeNode;
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

        masteryNodes.stream()
                .filter(node -> "review".equals(node.state()))
                .sorted(REVIEW_ORDER)
                .forEach(node -> {
                    if (result.size() >= MAX_RECOMMENDATIONS) {
                        return;
                    }
                    KnowledgeNode knowledgeNode = knowledgeByKid.get(node.kid());
                    if (knowledgeNode == null || !kidsWithQuestions.contains(node.kid())) {
                        return;
                    }
                    knowledgeNode.prereqKids().stream()
                            .map(masteryByKid::get)
                            .filter(prerequisite -> prerequisite != null && "review".equals(prerequisite.state()))
                            .filter(prerequisite -> kidsWithQuestions.contains(prerequisite.kid()))
                            .sorted(Comparator.comparing(MasteryNode::kid))
                            .forEach(prerequisite -> addPrerequisite(result, includedKids, prerequisite,
                                    knowledgeByKid, knowledgeNode));
                    add(result, includedKids, node, knowledgeNode, "REVIEW",
                            "Cần ôn: " + formatValue(node.value()) + "/100.");
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

    private static void addPrerequisite(List<Recommendation> result, Set<String> includedKids,
            MasteryNode prerequisite, Map<String, KnowledgeNode> knowledgeByKid, KnowledgeNode target) {
        KnowledgeNode prerequisiteNode = knowledgeByKid.get(prerequisite.kid());
        if (prerequisiteNode != null) {
            add(result, includedKids, prerequisite, prerequisiteNode, "PREREQ",
                    "Ôn " + prerequisiteNode.nameVi() + " trước khi ôn " + target.nameVi() + ".");
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
