package com.cnhsk.learning.aigen;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.cnhsk.learning.ai.ClaudeClient.Completion;
import com.cnhsk.learning.aigen.AiGenerationGuard.Lease;
import com.cnhsk.learning.aigen.AiGenerationService.Settings;
import com.cnhsk.learning.aigen.WeakKnowledgeSelector.Candidate;
import com.cnhsk.learning.common.ServiceRequest;
import com.cnhsk.learning.common.ServiceRequestKind;
import com.cnhsk.learning.common.ServiceRequestStatus;
import com.cnhsk.learning.knowledge.entity.KnowledgeLabelRole;
import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.entity.QuestionKnowledgePoint;
import com.cnhsk.learning.knowledge.entity.QuestionSource;
import com.cnhsk.learning.knowledge.entity.QuestionStatus;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Persists each claim and final outcome atomically, never spanning the provider call. */
@Repository
public class AiGenerationStore {
    private final EntityManager entities;
    private final TransactionTemplate writes;
    private final WeakKnowledgeQueryRepository queries;
    private final AiQuestionValidator validator;
    private final Clock clock;
    private final ObjectMapper mapper;

    public AiGenerationStore(EntityManager entities, PlatformTransactionManager transactions,
            WeakKnowledgeQueryRepository queries, AiQuestionValidator validator, Clock clock, ObjectMapper mapper) {
        this.entities = entities; this.queries = queries; this.validator = validator; this.clock = clock; this.mapper = mapper;
        writes = new TransactionTemplate(transactions);
        writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        writes.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public Optional<Claim> claim(Candidate candidate, Settings settings, Lease lease) {
        try {
            return writes.execute(status -> {
                lease.assertOwned();
                Optional<Node> node = lockNode(candidate);
                if (node.isEmpty() || !eligible(candidate, settings, -1)) return Optional.empty();
                Map<String, Object> payload = Map.of("kid", candidate.kid(), "knowledgePointId", candidate.knowledgePointId(),
                        "model", settings.model(), "wrongCount", candidate.wrongCount(), "bankCount", candidate.bankCount());
                long id = ((Number) entities.createNativeQuery("""
                        INSERT INTO learning.service_requests(user_id,kind,status,payload,created_at)
                        VALUES (:userId,'AI_GENERATE','RUNNING',CAST(:payload AS jsonb),:now) RETURNING id
                        """).setParameter("userId", settings.userId()).setParameter("payload", json(payload))
                        .setParameter("now", clock.instant()).getSingleResult()).longValue();
                lease.assertOwned();
                return Optional.of(new Claim(id, candidate, settings));
            });
        } catch (RuntimeException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause.getMessage() != null && cause.getMessage().contains("uq_ai_running")) throw new BusyException();
            }
            throw exception;
        }
    }

    public Finish finish(Claim claim, AiPromptBuilder.Prompt prompt, Completion completion, Lease lease) {
        return writes.execute(status -> {
            lease.assertOwned();
            Node node = lockNode(claim.candidate()).orElseThrow(ContextException::new);
            ServiceRequest request = entities.find(ServiceRequest.class, claim.id(), LockModeType.PESSIMISTIC_WRITE);
            if (request == null || request.getKind() != ServiceRequestKind.AI_GENERATE
                    || request.getStatus() != ServiceRequestStatus.RUNNING
                    || request.getUserId() != claim.settings().userId()
                    || !claim.candidate().kid().equals(request.getPayload().get("kid"))
                    || !(request.getPayload().get("knowledgePointId") instanceof Number pointId)
                    || pointId.longValue() != claim.candidate().knowledgePointId()) throw new ContextException();
            if (node.hskLevel() != prompt.hskLevel() || !node.kid().equals(prompt.kid())
                    || !eligible(claim.candidate(), claim.settings(), claim.id())) throw new ContextException();
            var checked = validator.validate(completion.text(), prompt);
            List<Long> questionIds = new ArrayList<>();
            for (var draft : checked.accepted()) {
                Question question = new Question(null, QuestionType.MULTIPLE_CHOICE, (short) node.hskLevel(), draft.stem());
                question.setSource(QuestionSource.AI);
                question.setStatus(QuestionStatus.PENDING_REVIEW);
                // Drafts are written four-option questions read on screen, so they train reading.
                question.setSkill("READING");
                question.setOptions(draft.options()); question.setCorrectKey(draft.correctKey());
                question.setExplanation(draft.explanation()); question.setDifficulty((short) draft.difficulty());
                question.setReusable(true); question.setCreatedByRequestId(claim.id());
                entities.persist(question);
                entities.persist(new QuestionKnowledgePoint(question.getId(), claim.candidate().knowledgePointId(), KnowledgeLabelRole.PRIMARY));
                questionIds.add(question.getId());
            }
            request.setResult(Map.of("questionIds", questionIds,
                    "dropped", checked.dropped().stream().map(drop -> Map.of("index", drop.index(), "reason", drop.reason())).toList(),
                    "inputTokens", completion.inputTokens(), "outputTokens", completion.outputTokens()));
            request.setStatus(questionIds.isEmpty() ? ServiceRequestStatus.FAILED : ServiceRequestStatus.COMPLETED);
            request.setErrorMessage(questionIds.isEmpty() ? "NO_VALID_QUESTION" : null);
            request.setCompletedAt(clock.instant());
            entities.flush();
            lease.assertOwned();
            return new Finish(questionIds.size(), !questionIds.isEmpty());
        });
    }

    public boolean fail(Claim claim, String reason, boolean retry) {
        if (!Set.of("INVALID_OUTPUT", "PROVIDER_ERROR", "BUDGET_EXCEEDED", "DB_ERROR", "CONTEXT_UNAVAILABLE").contains(reason)) {
            throw new IllegalArgumentException("Invalid generation failure reason");
        }
        return Boolean.TRUE.equals(writes.execute(status -> entities.createNativeQuery("""
                UPDATE learning.service_requests SET status='FAILED', error_message=:reason, completed_at=:now,
                    retry_count=retry_count+:increment
                WHERE id=:id AND user_id=:userId AND kind='AI_GENERATE' AND status='RUNNING'
                """).setParameter("reason", reason).setParameter("now", clock.instant()).setParameter("increment", retry ? 1 : 0)
                .setParameter("id", claim.id()).setParameter("userId", claim.settings().userId()).executeUpdate() == 1));
    }

    private Optional<Node> lockNode(Candidate candidate) {
        List<?> rows = entities.createNativeQuery("""
                SELECT code,hsk_level FROM learning.knowledge_points WHERE id=:id FOR UPDATE
                """).setParameter("id", candidate.knowledgePointId()).getResultList();
        if (rows.isEmpty()) return Optional.empty();
        Object[] row = (Object[]) rows.get(0);
        return candidate.kid().equals(row[0]) ? Optional.of(new Node((String) row[0], ((Number) row[1]).intValue())) : Optional.empty();
    }

    private boolean eligible(Candidate candidate, Settings settings, long ownRequest) {
        var nodes = queries.nodes(Set.of(candidate.kid()));
        if (nodes.size() != 1) return false;
        var node = nodes.get(0);
        if (node.knowledgePointId() != candidate.knowledgePointId() || node.canDo() == null || node.canDo().isBlank()
                || !node.hasLexemes() || node.hasPending() || node.bankCount() >= settings.minBank()) return false;
        for (var run : queries.activeRuns()) {
            if (run.id() == ownRequest) continue;
            try {
                JsonNode payload = mapper.readTree(run.payloadJson());
                if (payload == null || !payload.isObject() || !payload.path("kid").isTextual()
                        || !payload.path("knowledgePointId").isIntegralNumber()) throw new ContextException();
                if (candidate.kid().equals(payload.get("kid").textValue())
                        || candidate.knowledgePointId() == payload.get("knowledgePointId").longValue()) return false;
            } catch (JsonProcessingException exception) { throw new ContextException(); }
        }
        return true;
    }

    private String json(Map<String, Object> value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new ContextException(); }
    }
    private record Node(String kid, int hskLevel) { }
    public record Claim(long id, Candidate candidate, Settings settings) { }
    public record Finish(int questions, boolean completed) { }
    public static class BusyException extends RuntimeException {
        public BusyException() { super("GENERATION_BUSY"); }
    }
    public static class ContextException extends RuntimeException {
        public ContextException() { super("CONTEXT_UNAVAILABLE"); }
    }
}
