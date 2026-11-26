package com.cnhsk.learning.aigen;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Reserves the shared session before accepting asynchronous work. */
@Component
public class AiRunTrigger {
    private static final Logger LOG = LoggerFactory.getLogger(AiRunTrigger.class);
    private final AuthApi auth;
    private final AiGenerateConfig config;
    private final AiGenerationGuard guard;
    private final AiGenerationService generation;
    private final Executor executor;
    private final ThreadPoolExecutor managed;
    private final PlacementDraftPlanner planner;

    @Autowired
    public AiRunTrigger(AuthApi auth, AiGenerateConfig config, AiGenerationGuard guard, AiGenerationService generation,
            PlacementDraftPlanner planner) {
        this(auth, config, guard, generation, newExecutor(), planner);
    }

    /** Without a planner placement drafts are unavailable; tests of the scheduled run use this. */
    public AiRunTrigger(AuthApi auth, AiGenerateConfig config, AiGenerationGuard guard, AiGenerationService generation) {
        this(auth, config, guard, generation, newExecutor(), null);
    }

    /** Allows deterministic admission/rejection testing without another lock or scheduler. */
    AiRunTrigger(AuthApi auth, AiGenerateConfig config, AiGenerationGuard guard, AiGenerationService generation,
            Executor executor) {
        this(auth, config, guard, generation, executor, null);
    }

    AiRunTrigger(AuthApi auth, AiGenerateConfig config, AiGenerationGuard guard, AiGenerationService generation,
            Executor executor, PlacementDraftPlanner planner) {
        this.auth = auth;
        this.config = config;
        this.guard = guard;
        this.generation = generation;
        this.executor = executor;
        this.planner = planner;
        this.managed = executor instanceof ThreadPoolExecutor pool ? pool : null;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void trigger() {
        if (!auth.currentUser().roles().contains(RoleCode.ADMIN)) {
            throw failure(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN);
        }
        var settings = new AiGenerationService.Settings(systemUser(), config.getGenerateModel(), config.batchSize(),
                config.maxBatchesPerRun(), config.minBank(), config.getTimeout(), config.dailyCap(),
                config.semanticThreshold(), config.maxPending());
        accept(lease -> generation.runReserved(lease, settings));
    }

    /**
     * Asks the AI for {@code count} placement-test questions of one HSK level, spread over the level's nodes.
     * Content editors start it; the drafts go to the usual two-step review with code prefix {@code PLC{level}-}.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void triggerPlacement(int level, int count) {
        if (!auth.currentUser().roles().contains(RoleCode.CONTENT_EDITOR)) {
            throw failure(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN);
        }
        var settings = new AiGenerationService.Settings(systemUser(), config.getGenerateModel(), config.batchSize(),
                config.maxBatchesPerRun(), config.minBank(), config.getTimeout(), config.dailyCap(),
                config.semanticThreshold(), config.maxPending(), "PLC" + level + "-");
        var plan = planner.plan(level, count, config.batchSize());
        if (plan.isEmpty()) throw failure(ErrorCode.PLACEMENT_BANK_INSUFFICIENT, HttpStatus.UNPROCESSABLE_ENTITY);
        accept(lease -> generation.runPlanned(lease, settings, plan));
    }

    private long systemUser() {
        long systemUser = config.systemUserId();
        if (systemUser == 0 || !auth.isActiveAdmin(systemUser)) {
            throw failure(ErrorCode.AI_GENERATION_DISABLED, HttpStatus.CONFLICT);
        }
        return systemUser;
    }

    private void accept(java.util.function.Consumer<AiGenerationGuard.Lease> work) {
        AiGenerationGuard.Lease lease;
        try {
            lease = guard.tryAcquire().orElseThrow(() -> failure(ErrorCode.AI_GENERATION_BUSY, HttpStatus.CONFLICT));
        } catch (AiGenerationGuard.GuardException exception) {
            throw failure(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        boolean accepted = false;
        try {
            executor.execute(() -> {
                try (lease) {
                    work.accept(lease);
                } catch (RuntimeException exception) {
                    LOG.warn("Accepted generation worker failed");
                }
            });
            accepted = true;
        } catch (RuntimeException exception) {
            throw failure(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if (!accepted) {
                try { lease.close(); }
                catch (RuntimeException exception) { LOG.warn("Unaccepted generation lease release failed"); }
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        if (managed == null) return;
        managed.shutdown();
        boolean interrupted = false;
        try {
            while (!managed.isTerminated()) {
                try { managed.awaitTermination(1, TimeUnit.SECONDS); }
                catch (InterruptedException exception) { interrupted = true; }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static ThreadPoolExecutor newExecutor() {
        return new ThreadPoolExecutor(0, 2, 30, TimeUnit.SECONDS, new SynchronousQueue<>(), task -> {
            Thread thread = new Thread(task, "generation-worker");
            thread.setDaemon(false);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    private static ApiException failure(ErrorCode code, HttpStatus status) {
        return new ApiException(code, status, code.name(), Map.of());
    }
}
