package com.cnhsk.learning.aigen;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class AiRunTriggerTest {
    private final AuthApi auth = mock(AuthApi.class);
    private final AiGenerateConfig config = mock(AiGenerateConfig.class);
    private final AiGenerationGuard guard = mock(AiGenerationGuard.class);
    private final AiGenerationGuard.Lease lease = mock(AiGenerationGuard.Lease.class);
    private final AiGenerationService generation = mock(AiGenerationService.class);
    private final List<Runnable> accepted = new ArrayList<>();
    private AiRunTrigger trigger;

    @BeforeEach
    void prepare() {
        when(auth.currentUser()).thenReturn(new CurrentUser(19, Set.of(RoleCode.ADMIN), true));
        when(auth.isActiveAdmin(7)).thenReturn(true);
        when(config.systemUserId()).thenReturn(7);
        when(config.getGenerateModel()).thenReturn("accepted-model");
        when(config.batchSize()).thenReturn(2); when(config.maxBatchesPerRun()).thenReturn(3);
        when(config.minBank()).thenReturn(10); when(config.getTimeout()).thenReturn(Duration.ofSeconds(9));
        when(guard.tryAcquire()).thenReturn(Optional.of(lease));
        trigger = new AiRunTrigger(auth, config, guard, generation, accepted::add);
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = {"TEACHER", "CONTENT_EDITOR", "LEARNER", "MODERATOR", "ACCOUNTANT"})
    void serviceRequiresAnExplicitAdminRoleBeforeConfigGuardOrSubmission(RoleCode role) {
        when(auth.currentUser()).thenReturn(new CurrentUser(19, Set.of(role), true));
        error(trigger::trigger, ErrorCode.FORBIDDEN);
        verifyNoInteractions(config, guard, generation, lease);
        assertThat(accepted).isEmpty();
    }

    @Test
    void zeroSystemAccountRejectsBeforeRoleLookupLeaseOrSubmission() {
        when(config.systemUserId()).thenReturn(0);
        error(trigger::trigger, ErrorCode.AI_GENERATION_DISABLED);
        verify(auth, never()).isActiveAdmin(org.mockito.ArgumentMatchers.anyLong());
        verifyNoInteractions(guard, generation, lease); assertThat(accepted).isEmpty();
    }

    @Test
    void inactiveSystemAccountRejectsBeforeLeaseOrSubmission() {
        when(auth.isActiveAdmin(7)).thenReturn(false);
        error(trigger::trigger, ErrorCode.AI_GENERATION_DISABLED);
        verifyNoInteractions(guard, generation, lease); assertThat(accepted).isEmpty();
    }

    @Test
    void occupiedGuardIs409AndInfrastructureFailureIsSafe500WithoutSubmission() {
        when(guard.tryAcquire()).thenReturn(Optional.empty());
        error(trigger::trigger, ErrorCode.AI_GENERATION_BUSY);
        when(guard.tryAcquire()).thenThrow(new AiGenerationGuard.GuardException());
        error(trigger::trigger, ErrorCode.INTERNAL_ERROR);
        verifyNoInteractions(generation, lease); assertThat(accepted).isEmpty();
    }

    @Test
    void requestAcquiresBeforeAcceptanceAndWorkerUsesExactSnapshotWithoutAnotherAcquire() {
        Executor executor = task -> {
            verify(guard).tryAcquire(); verifyNoInteractions(generation); accepted.add(task);
        };
        trigger = new AiRunTrigger(auth, config, guard, generation, executor);
        trigger.trigger();
        assertThat(accepted).hasSize(1); verify(lease, never()).close();
        when(config.batchSize()).thenReturn(9); when(config.getGenerateModel()).thenReturn("changed-model");
        accepted.get(0).run();
        verify(generation).runReserved(lease, new AiGenerationService.Settings(7, "accepted-model", 2, 3, 10, Duration.ofSeconds(9)));
        verify(guard, times(1)).tryAcquire(); verify(lease, times(1)).close();
    }

    @Test
    void rejectedSubmissionClosesOnlyTheUnacceptedLeaseAndNeverRunsGeneration() {
        trigger = new AiRunTrigger(auth, config, guard, generation,
                task -> { throw new RejectedExecutionException("private rejection detail"); });
        error(trigger::trigger, ErrorCode.INTERNAL_ERROR);
        verify(lease, times(1)).close(); verifyNoInteractions(generation);
    }

    @Test
    void failedReleaseAfterRejectedSubmissionKeepsSafe500AndDoesNotRetryClose(CapturedOutput output) {
        trigger = new AiRunTrigger(auth, config, guard, generation,
                task -> { throw new RejectedExecutionException("private rejection detail"); });
        doThrow(new AiGenerationGuard.GuardException()).when(lease).close();
        error(trigger::trigger, ErrorCode.INTERNAL_ERROR);
        verify(lease, times(1)).close(); verifyNoInteractions(generation);
        assertThat(output.getAll()).doesNotContain("private rejection detail");
    }

    @Test
    void workerFailureAlwaysClosesTheAcceptedLeaseAndDoesNotExposeFailureText(CapturedOutput output) {
        doThrow(new IllegalStateException("private worker canary")).when(generation).runReserved(eq(lease), any());
        trigger.trigger(); accepted.get(0).run();
        verify(lease, times(1)).close(); verify(guard, times(1)).tryAcquire();
        assertThat(output.getAll()).contains("Accepted generation worker failed").doesNotContain("private worker canary");
    }

    @Test
    void managedExecutorNeverRunsProviderOnCallerAndDrainsAcceptedWorkOnShutdown() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), closed = new CountDownLatch(1);
        long caller = Thread.currentThread().getId(); AtomicLong worker = new AtomicLong();
        doAnswer(invocation -> { worker.set(Thread.currentThread().getId()); entered.countDown(); await(release); return null; })
                .when(generation).runReserved(eq(lease), any());
        doAnswer(invocation -> { closed.countDown(); return null; }).when(lease).close();
        trigger = new AiRunTrigger(auth, config, guard, generation);
        var shutdownWorker = java.util.concurrent.Executors.newSingleThreadExecutor();
        CountDownLatch shutdownStarted = new CountDownLatch(1);
        try {
            trigger.trigger(); await(entered);
            assertThat(worker.get()).isNotEqualTo(caller);
            var shutdown = shutdownWorker.submit(() -> { shutdownStarted.countDown(); trigger.shutdown(); });
            await(shutdownStarted);
            assertThat(shutdown.isDone()).isFalse();
            assertThat(closed.getCount()).isEqualTo(1);
            release.countDown(); await(closed);
            shutdown.get(25, TimeUnit.SECONDS);
            AiGenerationGuard.Lease later = mock(AiGenerationGuard.Lease.class);
            when(guard.tryAcquire()).thenReturn(Optional.of(later));
            error(trigger::trigger, ErrorCode.INTERNAL_ERROR);
            verify(later).close();
            verify(generation, times(1)).runReserved(any(), any());
        } finally {
            release.countDown(); trigger.shutdown(); shutdownWorker.shutdown();
            assertThat(shutdownWorker.awaitTermination(25, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void publicAdmissionSuspendsAmbientTransactions() throws Exception {
        Transactional boundary = AiRunTrigger.class.getMethod("trigger").getAnnotation(Transactional.class);
        assertThat(boundary).isNotNull(); assertThat(boundary.propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
    }

    private static void error(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, failure -> {
            assertThat(failure.getCode()).isEqualTo(code);
            assertThat(failure.getMessage()).doesNotContain("private");
            assertThat(failure.getStatus().value()).isEqualTo(code == ErrorCode.FORBIDDEN ? 403 : code == ErrorCode.INTERNAL_ERROR ? 500 : 409);
        });
    }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
    }
}
