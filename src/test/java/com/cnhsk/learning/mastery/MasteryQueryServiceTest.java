package com.cnhsk.learning.mastery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.cnhsk.learning.knowledge.KnowledgeTreeService;
import com.cnhsk.learning.knowledge.KnowledgeTreeService.KnowledgeNode;
import org.junit.jupiter.api.Test;

class MasteryQueryServiceTest {

    @Test
    void AT16_emptyProgressReturnsEveryTreeNodeAsNotYetWithoutWriting() {
        KnowledgeProgressRepository progress = mock(KnowledgeProgressRepository.class);
        MasteryQueryRepository repository = mock(MasteryQueryRepository.class);
        KnowledgeTreeService tree = mock(KnowledgeTreeService.class);
        MasteryConfig config = mock(MasteryConfig.class);
        Instant now = Instant.parse("2026-11-06T00:00:00Z");
        KnowledgeNode node = new KnowledgeNode(10, "NP3-010", null, 3, "Nút giả", null, null,
                null, List.of(), List.of());
        when(tree.nodes()).thenReturn(List.of(node));
        when(progress.findByUserId(42L)).thenReturn(List.of());
        when(repository.evidence(java.util.Set.of())).thenReturn(List.of());
        when(config.settings()).thenReturn(settings());
        when(config.inferenceSettings()).thenReturn(new MasteryConfig.InferenceSettings(
                new BigDecimal("2.0"), new BigDecimal("80")));

        MasteryQueryService service = new MasteryQueryService(progress, repository, tree, config,
                Clock.fixed(now, java.time.ZoneOffset.UTC), new FrontierService());
        MasteryQueryService.MasteryTree result = service.mastery(42L);

        assertThat(result.nodes()).containsExactly(new MasteryQueryService.MasteryNode(
                "NP3-010", null, BigDecimal.ZERO, 0, "notyet", true, null));
        verify(progress).findByUserId(42L);
        verify(repository).evidence(java.util.Set.of());
    }

    private static MasteryConfig.Settings settings() {
        return new MasteryConfig.Settings(8, 3, 3, 2, new BigDecimal("1.0"), 30,
                new BigDecimal("60"), new BigDecimal("80"), new BigDecimal("3"), 3, 7, 14);
    }
}
