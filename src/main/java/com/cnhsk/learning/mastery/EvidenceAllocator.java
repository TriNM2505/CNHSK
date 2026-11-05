package com.cnhsk.learning.mastery;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.cnhsk.learning.knowledge.KnowledgeTreeService;
import com.cnhsk.learning.knowledge.KnowledgeTreeService.QuestionKids;
import com.cnhsk.learning.practice.PracticeService.ServedSnapshot;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.entity.Attempt;
import com.cnhsk.learning.practice.entity.AttemptItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Allocates server-graded attempt items to current knowledge labels. */
@Component
public class EvidenceAllocator {

    private static final Logger log = LoggerFactory.getLogger(EvidenceAllocator.class);

    private final KnowledgeTreeService tree;
    private final EntityManager entityManager;
    private final ObjectMapper mapper;
    private final MasteryConfig config;

    public EvidenceAllocator(KnowledgeTreeService tree, EntityManager entityManager, ObjectMapper mapper,
            MasteryConfig config) {
        this.tree = tree;
        this.entityManager = entityManager;
        this.mapper = mapper;
        this.config = config;
    }

    public List<AllocatedEvidence> allocateNew(Attempt attempt, List<AttemptItem> items) {
        Objects.requireNonNull(attempt, "attempt");
        Objects.requireNonNull(items, "items");
        if (attempt.isSuspicious() || contextOf(attempt.getRefType()) == null) {
            return List.of();
        }
        return allocate(contexts(Map.of(attempt.getId(), attempt), items), true, attempt.getUserId());
    }

    public List<AllocatedEvidence> reallocate(long userId, Set<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return List.of();
        }
        List<AttemptItem> items = entityManager.createQuery(
                "SELECT item FROM AttemptItem item WHERE item.id IN :ids", AttemptItem.class)
                .setParameter("ids", itemIds).getResultList();
        Set<Long> attemptIds = items.stream().map(AttemptItem::getAttemptId).collect(Collectors.toSet());
        if (attemptIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Attempt> attempts = entityManager.createQuery(
                "SELECT attempt FROM Attempt attempt WHERE attempt.id IN :ids AND attempt.userId = :userId",
                Attempt.class).setParameter("ids", attemptIds).setParameter("userId", userId).getResultList()
                .stream().collect(Collectors.toMap(Attempt::getId, attempt -> attempt));
        if (attempts.size() != attemptIds.size()) {
            throw new IllegalStateException("Stored evidence does not belong to the progress owner");
        }
        return allocate(contexts(attempts, items), false, userId);
    }

    private List<ItemContext> contexts(Map<Long, Attempt> attempts, List<AttemptItem> items) {
        Map<Long, Map<Integer, ServedItem>> served = new HashMap<>();
        for (Attempt attempt : attempts.values()) {
            ServedSnapshot snapshot = mapper.convertValue(attempt.getServedItems(), ServedSnapshot.class);
            served.put(attempt.getId(), snapshot.items().stream()
                    .collect(Collectors.toMap(ServedItem::no, item -> item)));
        }
        List<ItemContext> result = new ArrayList<>();
        for (AttemptItem item : items) {
            Attempt attempt = attempts.get(item.getAttemptId());
            if (attempt == null) {
                throw new IllegalStateException("Missing attempt for evidence item");
            }
            ServedItem snapshot = served.get(attempt.getId()).get(item.getOrderIndex());
            if (snapshot == null || !Objects.equals(item.getQuestionId(), snapshot.questionId())
                    || !Objects.equals(item.getLexemeId(), snapshot.lexemeId())) {
                throw new IllegalStateException("Evidence item differs from the server snapshot");
            }
            result.add(new ItemContext(attempt, item, snapshot));
        }
        return result;
    }

    private List<AllocatedEvidence> allocate(List<ItemContext> contexts, boolean current, long userId) {
        if (contexts.isEmpty()) {
            return List.of();
        }
        MasteryConfig.EvidenceSettings settings = config.evidenceSettings();
        Set<Long> questionIds = contexts.stream().map(row -> row.item().getQuestionId())
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, QuestionKids> questionKids = tree.kidsOfQuestions(questionIds);
        Set<Long> approved = current ? approvedQuestions(questionIds) : Set.of();
        Map<Long, String> lexemeKids = lexemeKids(contexts);
        Map<Long, String> topicKids = topicKids(contexts);
        List<AttemptItem> priorCorrect = current ? priorCorrect(userId, contexts, settings.seenItemDays()) : List.of();

        List<AllocatedEvidence> result = new ArrayList<>();
        for (ItemContext row : contexts) {
            AttemptItem item = row.item();
            String context = contextOf(row.attempt().getRefType());
            if (context == null || item.getCorrect() == null) {
                continue;
            }
            if (current && (row.attempt().isSuspicious() || excludedMeta(item.getMeta())
                    || item.getQuestionId() != null && !approved.contains(item.getQuestionId())
                    || answeredCorrectlyBefore(item, priorCorrect, settings.seenItemDays()))) {
                continue;
            }
            String form = formOf(row.served());
            int score = item.getCorrect() ? 100 : 0;
            if (item.getQuestionId() != null) {
                QuestionKids kids = questionKids.get(item.getQuestionId());
                if (kids == null) {
                    log.warn("No knowledge label for question {}", item.getQuestionId());
                    continue;
                }
                if (item.getCorrect()) {
                    for (RoleAssignment target : questionTargets(true, kids, item.getAnswer())) {
                        add(result, target.kid(), item, context, form, target.role(), score, settings);
                    }
                } else {
                    for (RoleAssignment target : questionTargets(false, kids, item.getAnswer())) {
                        add(result, target.kid(), item, context, form, target.role(), score, settings);
                    }
                }
            } else if (item.getLexemeId() != null) {
                String kid = lexemeKids.get(item.getLexemeId());
                if (kid == null && row.attempt().getRefType().startsWith("TOPIC_")
                        && row.attempt().getRefId() != null) {
                    kid = topicKids.get(row.attempt().getRefId());
                }
                add(result, kid, item, context, form, "PRIMARY", score, settings);
            } else {
                log.warn("No knowledge label for attempt item {}", item.getId());
            }
        }
        return List.copyOf(result);
    }

    private Set<Long> approvedQuestions(Set<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(entityManager.createQuery("SELECT question.id FROM Question question "
                + "WHERE question.id IN :ids AND question.status = "
                + "com.cnhsk.learning.knowledge.entity.QuestionStatus.APPROVED", Long.class)
                .setParameter("ids", questionIds).getResultList());
    }

    private Map<Long, String> lexemeKids(List<ItemContext> contexts) {
        Set<Long> ids = contexts.stream().map(row -> row.item().getLexemeId())
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        for (Object row : entityManager.createNativeQuery("SELECT lexeme.id, point.code "
                + "FROM learning.lexemes lexeme JOIN learning.knowledge_points point "
                + "ON point.id = lexeme.knowledge_point_id WHERE lexeme.id IN (:ids)")
                .setParameter("ids", ids).getResultList()) {
            Object[] columns = (Object[]) row;
            result.put(((Number) columns[0]).longValue(), (String) columns[1]);
        }
        return result;
    }

    private Map<Long, String> topicKids(List<ItemContext> contexts) {
        Set<Long> ids = contexts.stream().filter(row -> row.attempt().getRefType().startsWith("TOPIC_"))
                .map(row -> row.attempt().getRefId()).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        for (Object row : entityManager.createNativeQuery("SELECT topic.id, point.code "
                + "FROM learning.topics topic JOIN learning.knowledge_points point "
                + "ON point.id = topic.knowledge_point_id WHERE topic.id IN (:ids)")
                .setParameter("ids", ids).getResultList()) {
            Object[] columns = (Object[]) row;
            result.put(((Number) columns[0]).longValue(), (String) columns[1]);
        }
        return result;
    }

    private List<AttemptItem> priorCorrect(long userId, List<ItemContext> contexts, int days) {
        Instant first = contexts.stream().map(row -> row.item().getAnsweredAt()).min(Instant::compareTo).orElseThrow();
        Instant last = contexts.stream().map(row -> row.item().getAnsweredAt()).max(Instant::compareTo).orElseThrow();
        return entityManager.createQuery("SELECT prior FROM AttemptItem prior, Attempt owner "
                + "WHERE prior.attemptId = owner.id AND owner.userId = :userId AND prior.correct = true "
                + "AND prior.answeredAt > :from AND prior.answeredAt < :to", AttemptItem.class)
                .setParameter("userId", userId).setParameter("from", first.minus(days, ChronoUnit.DAYS))
                .setParameter("to", last).getResultList();
    }

    private boolean answeredCorrectlyBefore(AttemptItem item, List<AttemptItem> prior, int days) {
        Instant lower = item.getAnsweredAt().minus(days, ChronoUnit.DAYS);
        return prior.stream().anyMatch(previous -> !previous.getId().equals(item.getId())
                && previous.getAnsweredAt().isAfter(lower) && previous.getAnsweredAt().isBefore(item.getAnsweredAt())
                && (item.getQuestionId() != null
                        ? item.getQuestionId().equals(previous.getQuestionId())
                        : item.getLexemeId() != null && previous.getQuestionId() == null
                                && item.getLexemeId().equals(previous.getLexemeId())
                                && item.getItemType().equals(previous.getItemType())));
    }

    private static boolean excludedMeta(Map<String, Object> meta) {
        return meta != null && (meta.containsKey("skipped") || meta.containsKey("missing")
                || meta.containsKey("audioFailed") || meta.containsKey("suspicious"));
    }

    private void add(List<AllocatedEvidence> result, String kid, AttemptItem item, String context,
            String form, String role, int score, MasteryConfig.EvidenceSettings settings) {
        if (kid == null) {
            log.warn("No knowledge label for attempt item {}", item.getId());
            return;
        }
        BigDecimal weight = staticWeight(context, form, role, settings);
        result.add(new AllocatedEvidence(kid, item.getId(), item.getAnsweredAt(), score,
                context, form, role, weight));
    }

    /** Pure role allocation shared by finish-time and read-time evidence reconstruction. */
    public static List<RoleAssignment> questionTargets(boolean correct, QuestionKids kids, String answer) {
        Objects.requireNonNull(kids, "kids");
        if (correct) {
            List<RoleAssignment> result = new ArrayList<>();
            result.add(new RoleAssignment(kids.primaryKid(), "PRIMARY"));
            kids.secondaryKids().forEach(kid -> result.add(new RoleAssignment(kid, "SECONDARY")));
            return List.copyOf(result);
        }
        var error = kids.errorsByOption().get(answer);
        return error == null ? List.of(new RoleAssignment(kids.primaryKid(), "PRIMARY"))
                : List.of(new RoleAssignment(error.errorKid(), "ERROR"));
    }

    /** Pure static evidence weight shared by finish-time and read-time evidence reconstruction. */
    public static BigDecimal staticWeight(String context, String form, String role,
            MasteryConfig.EvidenceSettings settings) {
        Objects.requireNonNull(settings, "settings");
        BigDecimal contextWeight = settings.contextWeights().get(context);
        BigDecimal formWeight = settings.formWeights().get(form);
        BigDecimal roleWeight = settings.roleWeights().get(role);
        if (contextWeight == null || formWeight == null || roleWeight == null) {
            throw new IllegalArgumentException("Unknown evidence context, form or role");
        }
        return contextWeight.multiply(formWeight).multiply(roleWeight);
    }

    public static String contextOf(String source) {
        if (source == null) {
            return null;
        }
        return switch (source) {
            case "MOCK_EXAM" -> "EXAM";
            case "PLACEMENT", "TOPIC_TEST" -> "QUIZ";
            case "TOPIC_PRACTICE", "GRAMMAR_LESSON", "CHAR_RECOGNITION", "TARGETED_PRACTICE",
                    "GAME_ROUND", "DUE_REVIEW" -> "PRACTICE";
            case "TOPIC_STUDY", "CHAR_WRITING", "WRITING_CHALLENGE", "PRON_STAGE" -> null;
            default -> {
                log.warn("No evidence context for practice source {}", source);
                yield null;
            }
        };
    }

    public static String formOf(ServedItem item) {
        int count = item.options() == null ? 0 : item.options().size();
        if (count >= 4) return "MCQ4";
        if (count == 3) return "MCQ3";
        if (count == 2) return "BINARY";
        if (count == 0) return "OPEN";
        throw new IllegalStateException("One-option item has no evidence form");
    }

    private record ItemContext(Attempt attempt, AttemptItem item, ServedItem served) { }

    public record AllocatedEvidence(String kid, Long itemId, Instant answeredAt, int score,
            String context, String form, String role, BigDecimal staticWeight) {
        public MasteryCalculator.Evidence asCalculatorEvidence() {
            return new MasteryCalculator.Evidence(itemId, answeredAt, staticWeight, score,
                    "PRACTICE".equals(context));
        }
    }

    public record RoleAssignment(String kid, String role) { }
}
