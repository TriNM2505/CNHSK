package com.cnhsk.learning.aigen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.ai.ClaudeClient;
import com.cnhsk.learning.ai.StubClaudeClient;
import com.cnhsk.learning.ai.TokenBudget;
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
    private AiGenerationService service;

    @BeforeEach
    void prepare() {
        service = new AiGenerationService(config, auth, selector, builder, budget, provider, store, guard);
        when(config.systemUserId()).thenReturn(7);
        when(config.getGenerateModel()).thenReturn("fixture-model");
        when(config.batchSize()).thenReturn(5);
        when(config.maxBatchesPerRun()).thenReturn(3);
        when(config.minBank()).thenReturn(10);
        when(config.getTimeout()).thenReturn(Duration.ofSeconds(60));
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
        var settings = new AiGenerationService.Settings(7, "fixture-model", 5, 3, 10, Duration.ofSeconds(60));
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
        when(store.finish(claim, prompt, completion, lease)).thenReturn(new AiGenerationStore.Finish(5, true));

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

    private static WeakKnowledgeSelector.Candidate candidate(long id) {
        return new WeakKnowledgeSelector.Candidate(id, "NP1-00" + id, 20, 0, 0);
    }
}
