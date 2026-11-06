package com.cnhsk.learning.exam;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cnhsk.learning.exam.ExamScoring.Score;
import com.cnhsk.learning.exam.ExamScoring.SectionScore;
import com.cnhsk.learning.exam.ExamScoring.Snapshot;
import com.cnhsk.learning.knowledge.KnowledgeTreeService;
import com.cnhsk.learning.knowledge.KnowledgeTreeService.KnowledgeNode;
import com.cnhsk.learning.knowledge.KnowledgeTreeService.QuestionKids;
import com.cnhsk.learning.practice.PracticeService.ServedSnapshot;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.entity.Attempt;
import com.cnhsk.learning.practice.entity.AttemptItem;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamResultService {
    private final EntityManager entities;
    private final ObjectMapper mapper;
    private final KnowledgeTreeService knowledge;

    public ExamResultService(EntityManager entities, ObjectMapper mapper, KnowledgeTreeService knowledge) {
        this.entities = entities;
        this.mapper = mapper;
        this.knowledge = knowledge;
    }

    @Transactional(readOnly = true)
    public ExamResult result(long userId, long sessionId) {
        Attempt current = requireSubmittedAttempt(entities, userId, sessionId);

        Attempt prior = entities.createQuery("""
                SELECT a FROM Attempt a
                WHERE a.userId = :userId AND a.refType = 'MOCK_EXAM' AND a.refId = :examId
                  AND a.status = 'SUBMITTED' AND (a.submittedAt < :submittedAt
                    OR (a.submittedAt = :submittedAt AND a.id < :id))
                ORDER BY a.submittedAt DESC, a.id DESC
                """, Attempt.class).setParameter("userId", userId).setParameter("examId", current.getRefId())
                .setParameter("submittedAt", current.getSubmittedAt()).setParameter("id", sessionId)
                .setMaxResults(1).getResultList().stream().findFirst().orElse(null);
        List<Long> ids = prior == null ? List.of(sessionId) : List.of(sessionId, prior.getId());
        Map<Long, List<AttemptItem>> evidence = entities.createQuery("""
                SELECT i FROM AttemptItem i WHERE i.attemptId IN :ids
                """, AttemptItem.class).setParameter("ids", ids).getResultList().stream()
                .collect(Collectors.groupingBy(AttemptItem::getAttemptId));
        List<AttemptItem> answers = evidence.getOrDefault(sessionId, List.of());
        Map<Integer, AttemptItem> byNo = answers.stream()
                .collect(Collectors.toMap(AttemptItem::getOrderIndex, Function.identity()));
        Snapshot exam = exam(current);
        Score score = score(exam, answers);
        PreviousAttempt previous = null;
        if (prior != null) {
            Score previousScore = score(exam(prior), evidence.getOrDefault(prior.getId(), List.of()));
            previous = new PreviousAttempt(previousScore.score(), previousScore.maxScore(),
                    previousScore.percent(), prior.getSubmittedAt());
        }

        List<ServedItem> served = mapper.convertValue(current.getServedItems(), ServedSnapshot.class).items();
        Map<Long, QuestionKids> labels = knowledge.kidsOfQuestions(served.stream().map(ServedItem::questionId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<String, String> names = labels.values().stream().anyMatch(label -> label.primaryKid() != null)
                ? knowledge.nodes().stream().collect(Collectors.toMap(KnowledgeNode::kid, KnowledgeNode::nameVi))
                : Map.of();
        List<ResultItem> items = new ArrayList<>();
        Map<String, int[]> counts = new TreeMap<>();
        for (ServedItem item : served) {
            AttemptItem answer = byNo.get(item.no());
            String choice = answer == null || Boolean.TRUE.equals(answer.getMeta().get("skipped"))
                    ? null : nonBlank(answer.getAnswer());
            ItemResult result = choice == null ? ItemResult.BLANK
                    : Boolean.TRUE.equals(answer.getCorrect()) ? ItemResult.CORRECT : ItemResult.WRONG;
            QuestionKids label = item.questionId() == null ? null : labels.get(item.questionId());
            String kid = label == null ? null : label.primaryKid();
            String name = kid == null ? null : names.get(kid);
            var error = label == null || choice == null ? null : label.errorsByOption().get(choice);
            if (kid != null) {
                int[] count = counts.computeIfAbsent(kid, ignored -> new int[2]);
                count[0]++;
                if (result != ItemResult.CORRECT) count[1]++;
            }
            items.add(new ResultItem(item.no(), exam.sectionOf().get(item.no()), item.prompt().text(),
                    item.options(), choice, item.answer(), result, nonBlank(item.explanation()), kid, name,
                    error == null ? null : error.errorKid(), error == null ? null : error.errorNote()));
        }
        List<KidCount> wrongByKid = counts.entrySet().stream().filter(entry -> entry.getValue()[1] > 0)
                .map(entry -> new KidCount(entry.getKey(), names.get(entry.getKey()),
                        entry.getValue()[1], entry.getValue()[0])).toList();
        int level = exam.hskLevel() != null ? exam.hskLevel() : legacyLevel(exam.examId());
        long correct = items.stream().filter(item -> item.result() == ItemResult.CORRECT).count();
        return new ExamResult(sessionId, new ExamIdentity(exam.examId(), exam.title(), level),
                current.getSubmittedAt(), score.score(), score.maxScore(), score.percent(),
                correct, items.size(), score.bySection(), previous, List.copyOf(items), wrongByKid);
    }

    static Attempt requireSubmittedAttempt(EntityManager entities, long userId, long sessionId) {
        Attempt attempt = entities.createQuery("""
                SELECT a FROM Attempt a WHERE a.id = :id AND a.userId = :userId AND a.refType = 'MOCK_EXAM'
                """, Attempt.class).setParameter("id", sessionId).setParameter("userId", userId)
                .getResultList().stream().findFirst().orElseThrow(() -> new ApiException(
                        ErrorCode.ATTEMPT_NOT_OWNED, HttpStatus.FORBIDDEN, "Không có quyền truy cập lượt làm bài này.", Map.of()));
        if (!"SUBMITTED".equals(attempt.getStatus())) {
            throw new ApiException(ErrorCode.ATTEMPT_NOT_SUBMITTED, HttpStatus.CONFLICT,
                    "Lượt làm bài chưa được nộp.", Map.of());
        }
        return attempt;
    }

    private Snapshot exam(Attempt attempt) {
        return mapper.convertValue(attempt.getServedItems().path("meta").path("exam"), Snapshot.class);
    }

    private int legacyLevel(long examId) {
        List<?> levels = entities.createNativeQuery("SELECT hsk_level FROM learning.exams WHERE id = :id")
                .setParameter("id", examId).getResultList();
        if (levels.isEmpty()) throw new IllegalStateException("Missing exam for legacy attempt");
        return ((Number) levels.get(0)).intValue();
    }

    private static Score score(Snapshot snapshot, List<AttemptItem> answers) {
        return ExamScoring.score(snapshot, answers.stream().filter(answer -> Boolean.TRUE.equals(answer.getCorrect()))
                .map(AttemptItem::getOrderIndex).collect(Collectors.toSet()));
    }

    private static String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public enum ItemResult { CORRECT, WRONG, BLANK }

    public record ExamIdentity(long id, String title, int hskLevel) {}
    public record PreviousAttempt(long score, long maxScore, int percent, Instant submittedAt) {}
    public record KidCount(String kid, String nameVi, int wrong, int total) {}
    public record ResultItem(int no, int sectionIndex, String stem, List<ServedItem.Option> options, String yourAnswer,
            String correctKey, ItemResult result, String explanation, String primaryKid, String primaryKidName,
            String errorKid, String errorNote) {}
    public record ExamResult(long sessionId, ExamIdentity exam, Instant submittedAt, long score, long maxScore,
            int percent, long correctCount, int totalCount, List<SectionScore> bySection, PreviousAttempt previous,
            List<ResultItem> items, List<KidCount> wrongByKid) {}
}
