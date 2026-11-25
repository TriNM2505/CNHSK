package com.cnhsk.learning.aigen;

import java.time.Duration;
import java.util.List;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.ai.AiUnavailableException;
import com.cnhsk.learning.ai.ClaudeClient;
import com.cnhsk.learning.ai.TokenBudget;
import com.cnhsk.learning.aigen.AiGenerationStore.Claim;
import com.cnhsk.learning.aigen.WeakKnowledgeSelector.Candidate;
import com.cnhsk.learning.aigen.embedding.EmbeddingUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates sequential generation without holding a database transaction during provider calls. */
@Service
public class AiGenerationService {
    private static final Logger log = LoggerFactory.getLogger(AiGenerationService.class);
    private final AiGenerateConfig config;
    private final AuthApi auth;
    private final WeakKnowledgeSelector selector;
    private final AiPromptBuilder prompts;
    private final TokenBudget budget;
    private final ClaudeClient client;
    private final AiGenerationStore store;
    private final AiGenerationGuard guard;
    private final SemanticDeduplicator semantic;

    public AiGenerationService(AiGenerateConfig config, AuthApi auth, WeakKnowledgeSelector selector,
            AiPromptBuilder prompts, TokenBudget budget, ClaudeClient client, AiGenerationStore store, AiGenerationGuard guard,
            SemanticDeduplicator semantic) {
        this.config = config; this.auth = auth; this.selector = selector; this.prompts = prompts;
        this.budget = budget; this.client = client; this.store = store; this.guard = guard; this.semantic = semantic;
    }


    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RunResult runOnce() {
        long userId = config.systemUserId();
        if (userId == 0) return empty(Outcome.DISABLED);
        if (!auth.isActiveAdmin(userId)) {
            log.warn("Generation disabled: invalid system account");
            return empty(Outcome.DISABLED);
        }
        Settings settings = new Settings(userId, config.getGenerateModel(), config.batchSize(),
                config.maxBatchesPerRun(), config.minBank(), config.getTimeout(), config.dailyCap(),
                config.semanticThreshold());
        var acquired = guard.tryAcquire();
        if (acquired.isEmpty()) return empty(Outcome.BUSY);
        RunResult result = empty(Outcome.EXECUTED);
        try (var lease = acquired.get()) {
            result = runCore(lease, settings);
        } catch (RuntimeException exception) {
            log.warn("Generation stopped: database or lock unavailable");
        }
        return result;
    }

    /** Uses an already reserved session; the worker that accepted it retains close ownership. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RunResult runReserved(AiGenerationGuard.Lease lease, Settings settings) {
        if (settings.userId() == 0 || !auth.isActiveAdmin(settings.userId())) return empty(Outcome.DISABLED);
        return runCore(lease, settings);
    }

    private RunResult runCore(AiGenerationGuard.Lease lease, Settings settings) {
        long userId = settings.userId();
        Counters counts = new Counters();
        Outcome outcome = Outcome.EXECUTED;
        try {
            lease.assertOwned();
            List<Candidate> candidates = selector.selectForGeneration();
            counts.candidates = candidates.size();
            List<Candidate> picked = candidates.stream().limit(settings.maxBatches()).toList();
            if (!picked.isEmpty()) counts.remaining = Math.max(0, settings.dailyCap() - store.savedToday());
            if (counts.remaining > 0 && !semantic.prepare()) {
                log.warn("Generation paused: duplicate meaning check unavailable");
                outcome = Outcome.PAUSED;
            } else if (counts.remaining > 0) {
                semantic.backfill(picked.stream().map(Candidate::knowledgePointId).toList());
            }
            List<AiGenerationQuota.Allocation> allocations = outcome == Outcome.PAUSED ? List.of()
                    : AiGenerationQuota.allocate(picked, counts.remaining, settings.batchSize());
            for (AiGenerationQuota.Allocation allocation : allocations) {
                Candidate candidate = allocation.candidate();
                lease.assertOwned();
                if (!auth.isActiveAdmin(userId)) break;
                Claim claim;
                try {
                    var selected = store.claim(candidate, settings, lease);
                    if (selected.isEmpty()) continue;
                    claim = selected.get();
                } catch (AiGenerationStore.BusyException exception) { outcome = Outcome.BUSY; break; }
                counts.batches++;
                try {
                    var prompt = prompts.build(candidate.knowledgePointId(), allocation.questions());
                    lease.assertOwned();
                    var completion = budget.execute(() -> client.complete(settings.model(), prompt.systemBlocks(), prompt.messages(), 4000, settings.timeout()));
                    counts.inputTokens += completion.inputTokens(); counts.outputTokens += completion.outputTokens();
                    var meanings = semantic.index(candidate.knowledgePointId(), completion.text(), settings.semanticThreshold());
                    lease.assertOwned();
                    if (!auth.isActiveAdmin(userId)) {
                        if (store.fail(claim, "CONTEXT_UNAVAILABLE", false)) counts.failed++;
                        break;
                    }
                    var finished = store.finish(claim, prompt, completion, meanings, allocation.questions(), lease);
                    counts.questions += finished.questions();
                    if (finished.completed()) counts.completed++; else counts.failed++;
                } catch (AiUnavailableException exception) {
                    boolean exhausted = exception.getReason() == AiUnavailableException.Reason.BUDGET_EXCEEDED;
                    if (store.fail(claim, exhausted ? "BUDGET_EXCEEDED" : "PROVIDER_ERROR", !exhausted)) counts.failed++;
                    if (exhausted) break;
                } catch (AiQuestionValidator.InvalidOutputException exception) {
                    if (store.fail(claim, "INVALID_OUTPUT", false)) counts.failed++;
                } catch (EmbeddingUnavailableException exception) {
                    if (store.fail(claim, "EMBEDDING_UNAVAILABLE", false)) counts.failed++;
                    log.warn("Generation paused: duplicate meaning check failed during a batch");
                    outcome = Outcome.PAUSED;
                    break;
                } catch (AiPromptBuilder.PromptContextException | AiGenerationStore.ContextException exception) {
                    if (store.fail(claim, "CONTEXT_UNAVAILABLE", false)) counts.failed++;
                } catch (RuntimeException exception) {
                    try { if (store.fail(claim, "DB_ERROR", false)) counts.failed++; }
                    catch (RuntimeException unavailable) { log.warn("Generation failure finalization unavailable"); }
                    log.warn("Generation stopped: persistence or lock unavailable");
                    break;
                }
            }
        } catch (EmbeddingUnavailableException exception) {
            log.warn("Generation paused: duplicate meaning check failed while embedding stored questions");
            outcome = Outcome.PAUSED;
        } catch (RuntimeException exception) { log.warn("Generation stopped: database or lock unavailable"); }
        log.info("Generation run candidates={} remaining={} batches={} completed={} failed={} questions={} inputTokens={} outputTokens={}",
                counts.candidates, counts.remaining, counts.batches, counts.completed, counts.failed, counts.questions,
                counts.inputTokens, counts.outputTokens);
        return new RunResult(outcome, counts.candidates, counts.batches, counts.completed, counts.failed,
                counts.questions, counts.inputTokens, counts.outputTokens);
    }

    private static RunResult empty(Outcome outcome) { return new RunResult(outcome, 0, 0, 0, 0, 0, 0, 0); }
    /** PAUSED: the duplicate meaning check is unavailable, so no question was generated or saved. */
    public enum Outcome { DISABLED, BUSY, EXECUTED, PAUSED }
    public record Settings(long userId, String model, int batchSize, int maxBatches, int minBank, Duration timeout,
            int dailyCap, int semanticThreshold) { }
    public record RunResult(Outcome outcome, int candidates, int batches, int completed, int failed,
            int questions, long inputTokens, long outputTokens) { }
    private static class Counters {
        int candidates, remaining, batches, completed, failed, questions;
        long inputTokens, outputTokens;
    }
}
