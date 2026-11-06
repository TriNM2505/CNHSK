package com.cnhsk.learning.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cnhsk.learning.exam.ExamWeakPointService.WeakPoint;
import com.cnhsk.learning.knowledge.KnowledgeTreeService;
import com.cnhsk.learning.knowledge.KnowledgeTreeService.KnowledgeNode;
import com.cnhsk.learning.knowledge.KnowledgeTreeService.QuestionKids;
import com.cnhsk.learning.mastery.MasteryReader;
import com.cnhsk.learning.mastery.MasteryReader.MasteryView;
import com.cnhsk.learning.practice.PracticeService.ServedSnapshot;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.entity.Attempt;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExamWeakPointServiceTest {
    private final EntityManager entities = mock(EntityManager.class);
    private final ObjectMapper mapper = mock(ObjectMapper.class);
    private final KnowledgeTreeService knowledge = mock(KnowledgeTreeService.class);
    private final MasteryReader mastery = mock(MasteryReader.class);
    private final ExamWeakPointService service = new ExamWeakPointService(entities, mapper, knowledge, mastery);

    @BeforeEach
    void submittedAttempt() {
        @SuppressWarnings("unchecked")
        TypedQuery<Attempt> query = mock(TypedQuery.class, RETURNS_SELF);
        Attempt attempt = mock(Attempt.class);
        var json = new ObjectMapper().createObjectNode();
        when(entities.createQuery(anyString(), eq(Attempt.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(attempt));
        when(attempt.getStatus()).thenReturn("SUBMITTED");
        when(attempt.getServedItems()).thenReturn(json);
        List<ServedItem> items = java.util.stream.LongStream.of(1, 2, 3, 4, 5, 6, 7)
                .mapToObj(id -> {
                    ServedItem item = mock(ServedItem.class);
                    when(item.questionId()).thenReturn(id);
                    return item;
                }).toList();
        when(mapper.convertValue(json, ServedSnapshot.class)).thenReturn(new ServedSnapshot(Map.of(), items));
    }

    @Test
    void FR014_AT13_ranksSixPrimaryKidsAndReturnsFiveWithMissingMasteryFirst() {
        Map<Long, QuestionKids> labels = new LinkedHashMap<>();
        Map<String, MasteryView> values = new LinkedHashMap<>();
        Integer[] scores = {70, 20, null, 90, 40, 55};
        for (int i = 0; i < scores.length; i++) {
            String kid = "NP1-10" + (i + 1);
            labels.put((long) i + 1, label(kid));
            values.put(kid, new MasteryView(scores[i], scores[i] == null ? "notyet" : "learning"));
        }
        labels.put(7L, label("NP1-101"));
        stub(labels, values);
        var result = service.weakPoints(12L, 30L);
        assertThat(result).extracting(WeakPoint::mastery).containsExactly(0, 20, 40, 55, 70);
        assertThat(result.get(0)).isEqualTo(new WeakPoint("NP1-103", "Tên NP1-103", 0, "notyet"));
        verify(knowledge).kidsOfQuestions(Set.of(1L, 2L, 3L, 4L, 5L, 6L, 7L));
        verify(mastery).valuesOf(12L, values.keySet());
    }

    @Test
    void FR014_AT13_returnsBothKidsAndBreaksTiesByKid() {
        stub(Map.of(1L, label("NP1-102"), 2L, label("NP1-101")),
                Map.of("NP1-102", new MasteryView(0, null), "NP1-101", new MasteryView(null, "notyet")));
        assertThat(service.weakPoints(12L, 30L)).extracting(WeakPoint::kid).containsExactly("NP1-101", "NP1-102");
    }

    @Test
    void FR014_ignoresSecondaryAndDistractorKidsAndSkipsMasteryForEmptySet() {
        when(knowledge.kidsOfQuestions(anySet())).thenReturn(Map.of(1L, label(null)));
        assertThat(service.weakPoints(12L, 30L)).isEmpty();
        verifyNoInteractions(mastery);
        verify(knowledge, never()).nodes();
    }

    private void stub(Map<Long, QuestionKids> labels, Map<String, MasteryView> values) {
        when(knowledge.kidsOfQuestions(anySet())).thenReturn(labels);
        when(mastery.valuesOf(12L, values.keySet())).thenReturn(values);
        var nodes = values.keySet().stream().map(kid -> {
            KnowledgeNode node = mock(KnowledgeNode.class);
            when(node.kid()).thenReturn(kid);
            when(node.nameVi()).thenReturn("Tên " + kid);
            return node;
        }).toList();
        when(knowledge.nodes()).thenReturn(nodes);
    }

    private static QuestionKids label(String primary) {
        return new QuestionKids(primary, List.of("TV1-099"),
                Map.of("B", new KnowledgeTreeService.DistractorError("CH1-099", "Sai mẫu")));
    }
}
