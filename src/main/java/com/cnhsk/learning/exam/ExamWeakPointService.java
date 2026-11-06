package com.cnhsk.learning.exam;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.cnhsk.learning.knowledge.KnowledgeTreeService;
import com.cnhsk.learning.knowledge.KnowledgeTreeService.KnowledgeNode;
import com.cnhsk.learning.knowledge.KnowledgeTreeService.QuestionKids;
import com.cnhsk.learning.mastery.MasteryReader;
import com.cnhsk.learning.practice.PracticeService.ServedSnapshot;
import com.cnhsk.learning.practice.ServedItem;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamWeakPointService {
    private final EntityManager entities;
    private final ObjectMapper mapper;
    private final KnowledgeTreeService knowledge;
    private final MasteryReader mastery;

    public ExamWeakPointService(EntityManager entities, ObjectMapper mapper, KnowledgeTreeService knowledge,
            MasteryReader mastery) {
        this.entities = entities;
        this.mapper = mapper;
        this.knowledge = knowledge;
        this.mastery = mastery;
    }

    @Transactional(readOnly = true)
    public List<WeakPoint> weakPoints(long userId, long sessionId) {
        var attempt = ExamResultService.requireSubmittedAttempt(entities, userId, sessionId);
        var served = mapper.convertValue(attempt.getServedItems(), ServedSnapshot.class);
        var questionIds = served.items().stream().map(ServedItem::questionId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        var kids = knowledge.kidsOfQuestions(questionIds).values().stream().map(QuestionKids::primaryKid)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (kids.isEmpty()) {
            return List.of();
        }

        var values = mastery.valuesOf(userId, kids);
        Map<String, String> names = knowledge.nodes().stream().filter(node -> kids.contains(node.kid()))
                .collect(Collectors.toMap(KnowledgeNode::kid, KnowledgeNode::nameVi));
        return kids.stream().map(kid -> {
            var value = values.get(kid);
            return new WeakPoint(kid, names.getOrDefault(kid, kid), value.value() == null ? 0 : value.value(),
                    value.state());
        }).sorted(Comparator.comparingInt(WeakPoint::mastery).thenComparing(WeakPoint::kid)).limit(5).toList();
    }

    @Schema(requiredProperties = {"kid", "nameVi", "mastery"})
    public record WeakPoint(String kid, String nameVi,
            @Schema(type = "integer", types = {"integer"}, minimum = "0", maximum = "100") int mastery,
            @JsonInclude(JsonInclude.Include.ALWAYS) @Schema(types = {"string", "null"}) String state) {}
}
