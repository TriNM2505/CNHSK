package com.cnhsk.learning.knowledge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.cnhsk.learning.knowledge.entity.KnowledgeDomain;
import com.cnhsk.learning.knowledge.entity.KnowledgeLabelRole;
import com.cnhsk.learning.knowledge.entity.KnowledgePoint;
import com.cnhsk.learning.knowledge.entity.KnowledgeSkillType;
import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import com.cnhsk.learning.knowledge.repository.KnowledgeTreeQueryRepository;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class KnowledgeTreeService {

    // Matches V5 ck_kp_kid; domain and level are encoded in the KID itself.
    private static final Pattern KID = Pattern.compile("^(NA|CH|TV|NP|NL|VB)[1-7]-[0-9]{3}$");
    private static final Comparator<KnowledgeNode> NODE_ORDER = Comparator
            .comparing(KnowledgeNode::domain)
            .thenComparingInt(KnowledgeNode::hskLevel).thenComparing(KnowledgeNode::kid);

    private final KnowledgePointRepository points;
    private final KnowledgeTreeQueryRepository queries;

    public KnowledgeTreeService(KnowledgePointRepository points, KnowledgeTreeQueryRepository queries) {
        this.points = points;
        this.queries = queries;
    }

    /** Returns the complete tree with edges, ordered by domain, level and KID. */
    public List<KnowledgeNode> nodes() {
        List<KnowledgePoint> all = points.findAll();
        Map<Long, String> codes = all.stream()
                .collect(Collectors.toMap(KnowledgePoint::getId, KnowledgePoint::getCode));
        return all.stream().map(point -> new KnowledgeNode(point.getId(), point.getCode(), point.getDomain(),
                point.getHskLevel(), point.getTitle(), point.getNameZh(), point.getCanDo(), point.getSkillType(),
                kids(point.getPrereqIds(), codes), kids(point.getSupportIds(), codes)))
                .sorted(NODE_ORDER).toList();
    }

    /** A malformed or unknown KID has no node. */
    public Optional<KnowledgeNode> node(String kid) {
        if (kid == null || !KID.matcher(kid).matches()) return Optional.empty();
        return nodes().stream().filter(node -> node.kid().equals(kid)).findFirst();
    }

    /**
     * Reads primary/secondary labels and distractor errors in one query for the entire set.
     * An empty set returns an empty map without querying. Unknown question IDs and questions
     * without labels are omitted from the map. A secondary-only question has a null primaryKid.
     */
    public Map<Long, QuestionKids> kidsOfQuestions(Set<Long> questionIds) {
        if (questionIds.isEmpty()) return Map.of();
        Map<Long, QuestionKidsBuilder> grouped = new LinkedHashMap<>();
        for (var row : queries.questionLabels(questionIds)) {
            QuestionKidsBuilder builder = grouped.computeIfAbsent(row.question().getId(),
                    ignored -> new QuestionKidsBuilder(row.question()));
            if (row.role() == KnowledgeLabelRole.PRIMARY) builder.primary = row.kid();
            else builder.secondary.add(row.kid());
        }
        Map<Long, QuestionKids> result = new LinkedHashMap<>();
        grouped.forEach((id, builder) -> result.put(id,
                new QuestionKids(builder.primary, builder.secondary, builder.errors)));
        return Map.copyOf(result);
    }

    public Optional<String> kidOfLexeme(long lexemeId) {
        return queries.kidOfLexeme(lexemeId);
    }

    public Optional<String> kidOfTopic(long topicId) {
        return queries.kidOfTopic(topicId);
    }

    /** Returns approved primary matches in ascending ID order; limit must be 1..200. */
    public List<Long> approvedQuestionIds(Set<String> kids, Integer hskLevel, int limit) {
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
        if (kids.isEmpty()) return List.of();
        return queries.approvedQuestionIds(kids, hskLevel, limit);
    }

    /** Controller detail projection; outside the FR-024/FR-025 signature lock for 011/012. */
    public KnowledgeNodeDetail detail(String kid) {
        if (kid == null || !KID.matcher(kid).matches()) throw notFound();
        List<KnowledgeNode> all = nodes();
        KnowledgeNode current = all.stream().filter(node -> node.kid().equals(kid)).findFirst()
                .orElseThrow(KnowledgeTreeService::notFound);
        List<String> dependents = all.stream().filter(node -> node.prereqKids().contains(kid))
                .map(KnowledgeNode::kid).sorted().toList();
        List<LinkedTopic> topics = queries.topics(current.id()).stream()
                .map(topic -> new LinkedTopic(topic.id(), topic.code(), topic.name())).toList();
        return new KnowledgeNodeDetail(current, dependents, topics,
                queries.lexemeCount(current.id()), queries.approvedQuestionCount(current.id()));
    }

    private static List<String> kids(Long[] ids, Map<Long, String> codes) {
        return Arrays.stream(ids).map(codes::get).sorted().toList();
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Không tìm thấy nút năng lực.", Map.of());
    }

    private static class QuestionKidsBuilder {
        private String primary;
        private final List<String> secondary = new ArrayList<>();
        private final Map<String, DistractorError> errors = new LinkedHashMap<>();

        QuestionKidsBuilder(Question question) {
            question.getOptions().stream().filter(option -> !option.isCorrect() && option.errorKid() != null)
                    .forEach(option -> errors.put(option.key(), new DistractorError(option.errorKid(), option.errorNote())));
        }
    }

    public record KnowledgeNode(long id, String kid, KnowledgeDomain domain, int hskLevel,
                                String nameVi, String nameZh, String canDo, KnowledgeSkillType skillType,
                                List<String> prereqKids, List<String> supportKids) {
        public KnowledgeNode {
            prereqKids = List.copyOf(prereqKids);
            supportKids = List.copyOf(supportKids);
        }
    }

    public record QuestionKids(String primaryKid, List<String> secondaryKids, Map<String, DistractorError> errorsByOption) {
        public QuestionKids {
            secondaryKids = List.copyOf(secondaryKids);
            errorsByOption = Map.copyOf(errorsByOption);
        }
    }

    public record DistractorError(String errorKid, String errorNote) { }

    public record KnowledgeNodeDetail(KnowledgeNode node, List<String> dependentKids, List<LinkedTopic> topics,
                                      long lexemeCount, long approvedQuestionCount) { }

    public record LinkedTopic(long id, String code, String name) { }
}
