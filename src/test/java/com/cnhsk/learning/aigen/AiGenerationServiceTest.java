package com.cnhsk.learning.aigen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.ai.ClaudeClient;
import com.cnhsk.learning.ai.StubClaudeClient;
import com.cnhsk.learning.ai.TokenBudget;
import com.cnhsk.learning.aigen.embedding.EmbeddingUnavailableException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(OutputCaptureExtension.class)
class AiGenerationServiceTest {
    private final AiGenerateConfig config = mock(AiGenerateConfig.class);
    private final AuthApi auth = mock(AuthApi.class);
    private final WeakKnowledgeSelector selector = mock(WeakKnowledgeSelector.class);
    private final AiPromptBuilder builder = mock(AiPromptBuilder.class);
    private final TokenBudget budget = mock(TokenBudget.class);
    private final StubClaudeClient provider = new StubClaudeClient();
    private final AiGenerationStore store = mock(AiGenerationStore.class);
    private final AiGenerationGuard guard = mock(AiGenerationGuard.class);
    private final AiGenerationGuard.Lease lease = mock(AiGenerationGuard.Lease.class);
    private final SemanticDeduplicator semantic = mock(SemanticDeduplicator.class);
    private final SemanticIndex index = SemanticIndex.off();
    private AiGenerationService service;

    @BeforeEach
    void prepare() {
        service = new AiGenerationService(config, auth, selector, builder, budget, provider, store, guard, semantic);
        when(config.systemUserId()).thenReturn(7);
        when(config.getGenerateModel()).thenReturn("fixture-model");
        when(config.batchSize()).thenReturn(5);
        when(config.maxBatchesPerRun()).thenReturn(3);
        when(config.minBank()).thenReturn(10);
        when(config.getTimeout()).thenReturn(Duration.ofSeconds(60));
        when(config.dailyCap()).thenReturn(100);
        when(config.semanticThreshold()).thenReturn(900);
        when(semantic.prepare()).thenReturn(true);
        when(semantic.index(anyLong(), any(), anyInt())).thenReturn(index);
        when(auth.isActiveAdmin(7)).thenReturn(true);
        when(guard.tryAcquire()).thenReturn(Optional.of(lease));
        when(selector.selectForGeneration()).thenReturn(List.of());
    }

    @Test
    void zeroSystemUserDisablesWithoutAuthDatabaseSelectionOrProvider(CapturedOutput output) {
        when(config.systemUserId()).thenReturn(0);
        assertThat(service.runOnce().outcome()).isEqualTo(AiGenerationService.Outcome.DISABLED);
        verifyNoInteractions(auth, selector, builder, budget, store, guard);
        assertThat(provider.callCount()).isZero();
        assertThat(output.getAll()).doesNotContain("fixture-model");
    }

    @Test
    void inactiveAdminDisablesBeforeGuardOrSelection() {
        when(auth.isActiveAdmin(7)).thenReturn(false);
        assertThat(service.runOnce().outcome()).isEqualTo(AiGenerationService.Outcome.DISABLED);
        verify(auth).isActiveAdmin(7);
        verifyNoInteractions(selector, builder, budget, store, guard);
        assertThat(provider.callCount()).isZero();
    }

    @Test
    void busyGuardDoesNotSelectClaimOrCallProvider() {
        when(guard.tryAcquire()).thenReturn(Optional.empty());
        assertThat(service.runOnce().outcome()).isEqualTo(AiGenerationService.Outcome.BUSY);
        verifyNoInteractions(selector, builder, budget, store);
        assertThat(provider.callCount()).isZero();
    }

    @Test
    void emptySelectionReleasesLeaseAndReportsZeroCounters(CapturedOutput output) {
        var result = service.runOnce();
        assertThat(result.outcome()).isEqualTo(AiGenerationService.Outcome.EXECUTED);
        assertThat(result.candidates()).isZero();
        assertThat(result.batches()).isZero();
        assertThat(result.completed()).isZero();
        assertThat(result.failed()).isZero();
        assertThat(result.questions()).isZero();
        assertThat(result.inputTokens()).isZero();
        assertThat(result.outputTokens()).isZero();
        verify(selector).selectForGeneration();
        verify(lease).close();
        verifyNoInteractions(store, builder, budget);
        assertThat(provider.callCount()).isZero();
        assertThat(output.getAll()).contains("candidates=0", "batches=0", "questions=0");
    }

    @Test
    void zeroMaximumNeverClaimsEvenWhenSelectorReturnsCandidates() {
        when(config.maxBatchesPerRun()).thenReturn(0);
        when(selector.selectForGeneration()).thenReturn(List.of(candidate(1)));
        service.runOnce();
        verifyNoInteractions(store, builder, budget);
        assertThat(provider.callCount()).isZero();
        verify(lease).close();
    }

    @Test
    void skippedClaimsDoNotCountAsBatches() {
        when(selector.selectForGeneration()).thenReturn(List.of(candidate(1), candidate(2)));
        when(store.claim(any(), any(), any())).thenReturn(Optional.empty());
        var result = service.runOnce();
        assertThat(result.candidates()).isEqualTo(2);
        assertThat(result.batches()).isZero();
        verify(store, times(2)).claim(any(), any(), any());
        assertThat(provider.callCount()).isZero();
        verify(lease).close();
    }

    @Test
    void providerOperationUsesSnapshotAndCountsOnlyCommittedQuestions() {
        var candidate = candidate(1);
        var settings = new AiGenerationService.Settings(7, "fixture-model", 5, 3, 10, Duration.ofSeconds(60), 100, 900);
        var claim = new AiGenerationStore.Claim(11, candidate, settings);
        var prompt = new AiPromptBuilder.Prompt(1, candidate.kid(), 1, List.of(), List.of());
        var completion = new ClaudeClient.Completion("{\"questions\":[]}", 13, 17);
        when(selector.selectForGeneration()).thenReturn(List.of(candidate));
        when(store.claim(any(), any(), any())).thenReturn(Optional.of(claim));
        when(builder.build(1, 5)).thenReturn(prompt);
        when(budget.execute(any())).thenAnswer(invocation -> {
            java.util.function.Supplier<ClaudeClient.Completion> action = invocation.getArgument(0);
            return action.get();
        });
        provider.enqueue(completion);
        when(store.finish(claim, prompt, completion, index, 5, lease)).thenReturn(new AiGenerationStore.Finish(5, true));

        var result = service.runOnce();

        assertThat(result.batches()).isEqualTo(1);
        assertThat(result.completed()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(result.questions()).isEqualTo(5);
        assertThat(result.inputTokens()).isEqualTo(13);
        assertThat(result.outputTokens()).isEqualTo(17);
        assertThat(provider.callCount()).isEqualTo(1);
        verify(lease).close();
    }

    @Test
    void runSuspendsAmbientTransactionsAndJobHasTheRequiredZone() throws Exception {
        Transactional transaction = AiGenerationService.class.getMethod("runOnce").getAnnotation(Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
        var scheduled = java.util.Arrays.stream(AiGenerationJob.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Scheduled.class)).toList();
        assertThat(scheduled).singleElement().satisfies(method -> {
            Scheduled annotation = method.getAnnotation(Scheduled.class);
            assertThat(annotation.zone()).isEqualTo("Asia/Ho_Chi_Minh");
            assertThat(annotation.cron()).contains("cnhsk.ai-generate.cron");
        });
        AiGenerationService delegate = mock(AiGenerationService.class);
        AiGenerationJob job = new AiGenerationJob(delegate);
        scheduled.get(0).setAccessible(true);
        scheduled.get(0).invoke(job);
        verify(delegate).runOnce();
    }

    @Test
    void unavailableMeaningCheckPausesBeforeAnyClaimOrProviderCall(CapturedOutput output) {
        when(selector.selectForGeneration()).thenReturn(List.of(candidate(1)));
        when(semantic.prepare()).thenReturn(false);
        var result = service.runOnce();
        assertThat(result.outcome()).isEqualTo(AiGenerationService.Outcome.PAUSED);
        assertThat(result.batches()).isZero();
        verify(store, never()).claim(any(), any(), any());
        verify(semantic, never()).backfill(any());
        assertThat(provider.callCount()).isZero();
        verify(lease).close();
        assertThat(output.getAll()).contains("Generation paused");
    }

    @Test
    void fullReviewQueueHoldsGenerationWithoutLoadingTheModelOrClaiming(CapturedOutput output) {
        when(selector.selectForGeneration()).thenReturn(List.of(candidate(1)));
        when(config.maxPending()).thenReturn(500);
        when(store.pendingReview()).thenReturn(500);
        var result = service.runOnce();
        assertThat(result.batches()).isZero();
        verify(semantic, never()).prepare();
        verify(store, never()).claim(any(), any(), any());
        assertThat(provider.callCount()).isZero();
        assertThat(output.getAll()).contains("Generation held: 500 AI questions await review");
    }

    @Test
    void reviewQueueRoomLimitsTheBatchLikeTheDailyCap(CapturedOutput output) {
        when(selector.selectForGeneration()).thenReturn(List.of(candidate(1)));
        when(config.maxPending()).thenReturn(500);
        when(store.pendingReview()).thenReturn(498);
        when(semantic.prepare()).thenReturn(true);
        when(store.claim(any(), any(), any())).thenReturn(Optional.empty());
        service.runOnce();
        verify(store).claim(any(), any(), any());
        assertThat(output.getAll()).contains("remaining=2 ");
    }

    @Test
    void reachedDailyCapNeitherLoadsTheModelNorClaims(CapturedOutput output) {
        when(selector.selectForGeneration()).thenReturn(List.of(candidate(1)));
        when(store.savedToday()).thenReturn(100);
        var result = service.runOnce();
        assertThat(result.outcome()).isEqualTo(AiGenerationService.Outcome.EXECUTED);
        assertThat(result.batches()).isZero();
        verify(semantic, never()).prepare();
        verify(store, never()).claim(any(), any(), any());
        assertThat(provider.callCount()).isZero();
        assertThat(output.getAll()).contains("remaining=0");
    }

    @Test
    void remainingQuestionsAreSplitByWrongAnswersAndBackfillCoversTheChosenNodes() {
        var heavy = new WeakKnowledgeSelector.Candidate(1, "NP1-001", 30, 0, 0);
        var light = new WeakKnowledgeSelector.Candidate(2, "NP1-002", 10, 0, 0);
        when(selector.selectForGeneration()).thenReturn(List.of(heavy, light));
        when(store.savedToday()).thenReturn(96);
        when(store.claim(any(), any(), any())).thenReturn(Optional.empty());
        service.runOnce();
        verify(semantic).backfill(List.of(1L, 2L));
        verify(store).claim(eq(heavy), any(), eq(lease));
        verify(store).claim(eq(light), any(), eq(lease));
        verifyNoInteractions(builder);
    }

    @Test
    void promptAsksForTheAllocatedNumberOfQuestions() {
        var heavy = new WeakKnowledgeSelector.Candidate(1, "NP1-001", 30, 0, 0);
        var light = new WeakKnowledgeSelector.Candidate(2, "NP1-002", 10, 0, 0);
        var settings = new AiGenerationService.Settings(7, "fixture-model", 5, 3, 10, Duration.ofSeconds(60), 100, 900);
        when(selector.selectForGeneration()).thenReturn(List.of(heavy, light));
        when(store.savedToday()).thenReturn(96);
        when(store.claim(any(), any(), any())).thenAnswer(call -> Optional.of(
                new AiGenerationStore.Claim(call.<WeakKnowledgeSelector.Candidate>getArgument(0).knowledgePointId(),
                        call.getArgument(0), settings)));
        when(builder.build(anyLong(), anyInt())).thenThrow(new AiPromptBuilder.PromptContextException("NODE_CHANGED"));
        service.runOnce();
        verify(builder).build(1, 3);
        verify(builder).build(2, 1);
    }

    @Test
    void meaningCheckFailureAfterTheProviderCallFailsTheBatchWithoutSavingAndPauses() {
        var candidate = candidate(1);
        var settings = new AiGenerationService.Settings(7, "fixture-model", 5, 3, 10, Duration.ofSeconds(60), 100, 900);
        var claim = new AiGenerationStore.Claim(11, candidate, settings);
        var prompt = new AiPromptBuilder.Prompt(1, candidate.kid(), 1, List.of(), List.of());
        when(selector.selectForGeneration()).thenReturn(List.of(candidate, candidate(2)));
        when(store.claim(any(), any(), any())).thenReturn(Optional.of(claim));
        when(builder.build(anyLong(), anyInt())).thenReturn(prompt);
        when(budget.execute(any())).thenAnswer(invocation -> {
            java.util.function.Supplier<ClaudeClient.Completion> action = invocation.getArgument(0); return action.get();
        });
        provider.enqueue(new ClaudeClient.Completion("{\"questions\":[]}", 3, 4));
        when(semantic.index(anyLong(), any(), anyInt())).thenThrow(new EmbeddingUnavailableException());
        when(store.fail(claim, "EMBEDDING_UNAVAILABLE", false)).thenReturn(true);
        var result = service.runOnce();
        assertThat(result.outcome()).isEqualTo(AiGenerationService.Outcome.PAUSED);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.batches()).isEqualTo(1);
        verify(store, never()).finish(any(), any(), any(), any(), anyInt(), any());
        verify(store).fail(claim, "EMBEDDING_UNAVAILABLE", false);
        assertThat(provider.callCount()).isEqualTo(1);
    }

    @Test
    void backfillFailurePausesTheRun() {
        when(selector.selectForGeneration()).thenReturn(List.of(candidate(1)));
        when(semantic.backfill(any())).thenThrow(new EmbeddingUnavailableException());
        var result = service.runOnce();
        assertThat(result.outcome()).isEqualTo(AiGenerationService.Outcome.PAUSED);
        verify(store, never()).claim(any(), any(), any());
        assertThat(provider.callCount()).isZero();
        verify(lease).close();
    }

    private static WeakKnowledgeSelector.Candidate candidate(long id) {
        return new WeakKnowledgeSelector.Candidate(id, "NP1-00" + id, 20, 0, 0);
    }

    @Test
    void reservedEntryUsesTheAcceptedLeaseAndSnapshotWithoutAcquiringAnother() {
        var settings = new AiGenerationService.Settings(7, "accepted-model", 2, 1, 4, Duration.ofSeconds(9), 100, 900);
        var result = service.runReserved(lease, settings);
        assertThat(result.outcome()).isEqualTo(AiGenerationService.Outcome.EXECUTED);
        assertThat(result.candidates()).isZero();
        verify(auth, atLeastOnce()).isActiveAdmin(7);
        verify(selector).selectForGeneration();
        verify(lease, atLeastOnce()).assertOwned();
        verify(lease, never()).close();
        verifyNoInteractions(config, guard, store, builder, budget);
    }

    @Test
    void reservedEntryRechecksTheSystemAccountBeforeSelectingAndLeavesLeaseOwnershipWithWorker() {
        when(auth.isActiveAdmin(7)).thenReturn(false);
        var settings = new AiGenerationService.Settings(7, "accepted-model", 2, 1, 4, Duration.ofSeconds(9), 100, 900);
        assertThat(service.runReserved(lease, settings).outcome()).isEqualTo(AiGenerationService.Outcome.DISABLED);
        verifyNoInteractions(config, guard, selector, store, builder, budget);
        verify(lease, never()).close();
        assertThat(provider.callCount()).isZero();
    }

    @Test
    void reservedEntryIsASeparatePublicTransactionSuspendingBoundary() throws Exception {
        Transactional transaction = AiGenerationService.class.getMethod("runReserved", AiGenerationGuard.Lease.class,
                AiGenerationService.Settings.class).getAnnotation(Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
    }

    @Test
    void reservedEntryUsesSnapshotValuesForClaimPromptAndProviderRatherThanCurrentConfig() {
        var selected = candidate(1);
        var settings = new AiGenerationService.Settings(7, "accepted-model", 2, 1, 4, Duration.ofSeconds(9), 100, 900);
        var claim = new AiGenerationStore.Claim(21, selected, settings);
        var prompt = new AiPromptBuilder.Prompt(1, selected.kid(), 1, List.of(), List.of());
        var completion = new ClaudeClient.Completion("{\"questions\":[]}", 6, 8);
        when(selector.selectForGeneration()).thenReturn(List.of(selected, candidate(2)));
        when(store.claim(selected, settings, lease)).thenReturn(Optional.of(claim));
        when(builder.build(1, 2)).thenReturn(prompt);
        when(budget.execute(any())).thenAnswer(invocation -> {
            java.util.function.Supplier<ClaudeClient.Completion> action = invocation.getArgument(0); return action.get();
        });
        provider.enqueue(completion);
        when(store.finish(claim, prompt, completion, index, 2, lease)).thenReturn(new AiGenerationStore.Finish(2, true));
        var result = service.runReserved(lease, settings);
        assertThat(result.batches()).isEqualTo(1);
        assertThat(result.questions()).isEqualTo(2);
        assertThat(result.inputTokens()).isEqualTo(6);
        assertThat(result.outputTokens()).isEqualTo(8);
        verify(store, times(1)).claim(any(), eq(settings), eq(lease));
        verify(builder).build(1, 2);
        verifyNoInteractions(config, guard);
        verify(lease, never()).close();
    }
}
