package com.cnhsk.learning.mastery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import com.cnhsk.learning.knowledge.KnowledgeTreeService;
import com.cnhsk.learning.knowledge.KnowledgeTreeService.KnowledgeNode;
import com.cnhsk.learning.mastery.MasteryQueryService.MasteryNode;
import com.cnhsk.learning.mastery.MasteryQueryService.MasteryTree;
import org.junit.jupiter.api.Test;

class RecommendationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");

    @Test
    void AT15_ordersPrerequisiteReviewConsolidateAndNewWithExactReasons() {
        KnowledgeTreeService tree = mock(KnowledgeTreeService.class);
        MasteryQueryService mastery = mock(MasteryQueryService.class);
        MasteryQueryRepository queries = mock(MasteryQueryRepository.class);

        KnowledgeNode prerequisite = node("VB3-001", "Tiên quyết", 3, List.of());
        KnowledgeNode firstReview = node("VB3-002", "Nút cần ôn", 3, List.of(prerequisite.kid()));
        KnowledgeNode secondReview = node("VB3-003", "Nút ôn thứ hai", 3, List.of());
        KnowledgeNode consolidate = node("VB3-004", "Nút củng cố", 2, List.of());
        KnowledgeNode newNode = node("VB1-005", "Nút mới", 1, List.of());
        List<KnowledgeNode> knowledgeNodes = List.of(prerequisite, firstReview, secondReview, consolidate, newNode);
        when(tree.nodes()).thenReturn(knowledgeNodes);
        when(mastery.mastery(42L)).thenReturn(new MasteryTree(List.of(
                mastery(prerequisite, "review", "40.0", false, null),
                mastery(firstReview, "review", "34.1", false, null),
                mastery(secondReview, "review", "50.0", false, null),
                mastery(consolidate, "consolidate", "70.0", false, NOW),
                mastery(newNode, "notyet", null, true, null))));
        when(queries.kidsWithApprovedQuestions(anySet())).thenReturn(knowledgeNodes.stream()
                .map(KnowledgeNode::kid).collect(java.util.stream.Collectors.toSet()));

        RecommendationService service = new RecommendationService(mastery, tree, queries,
                Clock.fixed(NOW, ZoneOffset.UTC));
        List<RecommendationService.Recommendation> result = service.recommendations(42L);

        assertThat(result).extracting(RecommendationService.Recommendation::kind)
                .containsExactly("PREREQ", "REVIEW", "REVIEW", "CONSOLIDATE", "NEW");
        assertThat(result).extracting(RecommendationService.Recommendation::kid)
                .containsExactly("VB3-001", "VB3-002", "VB3-003", "VB3-004", "VB1-005");
        assertThat(result.get(0).reason()).isEqualTo("Ôn Tiên quyết trước khi ôn Nút cần ôn.");
        assertThat(result.get(1).reason()).isEqualTo("Cần ôn: 34,1/100.");
        assertThat(result.get(3).reason()).isEqualTo("Đến hạn củng cố Nút củng cố.");
        assertThat(result.get(4).reason()).isEqualTo("Nút mới đã đủ tiên quyết.");
        assertThat(result.get(0).value()).isEqualByComparingTo("40.0");
        assertThat(result.get(4).value()).isNull();
    }

    @Test
    void firstOccurrenceWinsAndOnlyEligibleCandidatesAreIncluded() {
        KnowledgeTreeService tree = mock(KnowledgeTreeService.class);
        MasteryQueryService mastery = mock(MasteryQueryService.class);
        MasteryQueryRepository queries = mock(MasteryQueryRepository.class);
        KnowledgeNode prerequisite = node("VB3-010", "Tiên quyết", 3, List.of());
        KnowledgeNode target = node("VB3-011", "Nút đích", 3, List.of(prerequisite.kid()));
        KnowledgeNode futureConsolidate = node("VB3-012", "Chưa đến hạn", 3, List.of());
        KnowledgeNode inferred = node("VB3-013", "Suy ra vững", 3, List.of());
        List<KnowledgeNode> nodes = List.of(prerequisite, target, futureConsolidate, inferred);
        when(tree.nodes()).thenReturn(nodes);
        when(mastery.mastery(42L)).thenReturn(new MasteryTree(List.of(
                mastery(prerequisite, "review", "30.0", false, null),
                mastery(target, "review", "35.0", false, null),
                mastery(futureConsolidate, "consolidate", "65.0", false, NOW.plusSeconds(1)),
                mastery(inferred, "inferred", "90.0", false, null))));
        when(queries.kidsWithApprovedQuestions(anySet())).thenReturn(Set.of(
                prerequisite.kid(), target.kid(), futureConsolidate.kid(), inferred.kid()));

        RecommendationService service = new RecommendationService(mastery, tree, queries,
                Clock.fixed(NOW, ZoneOffset.UTC));
        List<RecommendationService.Recommendation> result = service.recommendations(42L);

        assertThat(result).extracting(RecommendationService.Recommendation::kid)
                .containsExactly(prerequisite.kid(), target.kid());
        assertThat(result).extracting(RecommendationService.Recommendation::kind)
                .containsExactly("REVIEW", "REVIEW");
    }

    @Test
    void newLearnerGetsOnlyFiveNewNodesInLevelAndKidOrder() {
        KnowledgeTreeService tree = mock(KnowledgeTreeService.class);
        MasteryQueryService mastery = mock(MasteryQueryService.class);
        MasteryQueryRepository queries = mock(MasteryQueryRepository.class);
        List<KnowledgeNode> nodes = List.of(
                node("VB3-020", "Nút cấp ba", 3, List.of()),
                node("VB1-022", "Nút cấp một B", 1, List.of()),
                node("VB1-021", "Nút cấp một A", 1, List.of()),
                node("VB2-023", "Nút cấp hai", 2, List.of()),
                node("VB3-024", "Nút cấp ba B", 3, List.of()),
                node("VB2-025", "Nút cấp hai B", 2, List.of()));
        when(tree.nodes()).thenReturn(nodes);
        when(mastery.mastery(42L)).thenReturn(new MasteryTree(nodes.stream()
                .map(node -> mastery(node, "notyet", null, true, null)).toList()));
        when(queries.kidsWithApprovedQuestions(anySet())).thenReturn(nodes.stream()
                .map(KnowledgeNode::kid).collect(java.util.stream.Collectors.toSet()));

        RecommendationService service = new RecommendationService(mastery, tree, queries,
                Clock.fixed(NOW, ZoneOffset.UTC));
        List<RecommendationService.Recommendation> result = service.recommendations(42L);

        assertThat(result).hasSize(5);
        assertThat(result).extracting(RecommendationService.Recommendation::kind)
                .containsOnly("NEW");
        assertThat(result).extracting(RecommendationService.Recommendation::kid)
                .containsExactly("VB1-021", "VB1-022", "VB2-023", "VB2-025", "VB3-020");
    }

    @Test
    void nodesWithoutApprovedQuestionsAreOmittedAndNoMatchesReturnsEmptyList() {
        KnowledgeTreeService tree = mock(KnowledgeTreeService.class);
        MasteryQueryService mastery = mock(MasteryQueryService.class);
        MasteryQueryRepository queries = mock(MasteryQueryRepository.class);
        KnowledgeNode candidate = node("VB3-030", "Không có câu", 3, List.of());
        when(tree.nodes()).thenReturn(List.of(candidate));
        when(mastery.mastery(42L)).thenReturn(new MasteryTree(List.of(
                mastery(candidate, "notyet", null, true, null))));
        when(queries.kidsWithApprovedQuestions(anySet())).thenReturn(Set.of());

        RecommendationService service = new RecommendationService(mastery, tree, queries,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.recommendations(42L)).isEmpty();
    }

    private static KnowledgeNode node(String kid, String name, int level, List<String> prereqs) {
        return new KnowledgeNode(1L, kid, null, level, name, null, null, null, prereqs, List.of());
    }

    private static MasteryNode mastery(KnowledgeNode node, String state, String value,
            boolean frontier, Instant dueAt) {
        return new MasteryNode(node.kid(), value == null ? null : new BigDecimal(value),
                BigDecimal.ONE, 1, state, frontier, dueAt);
    }
}
