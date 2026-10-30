package com.cnhsk.learning.content.importer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cnhsk.learning.common.ServiceRequest;
import com.cnhsk.learning.common.ServiceRequestKind;
import com.cnhsk.learning.common.ServiceRequestRepository;
import com.cnhsk.learning.common.ServiceRequestStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportMaintenanceTest {
    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");
    private ServiceRequestRepository requests;
    private EntityManager entityManager;
    private PlatformTransactionManager manager;
    private ImportMaintenance maintenance;
    private ServiceRequest run;
    private Logger logger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setup() {
        requests = mock(ServiceRequestRepository.class);
        entityManager = mock(EntityManager.class);
        manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(invocation -> {
            TransactionDefinition definition = invocation.getArgument(0);
            assertThat(definition.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            return new SimpleTransactionStatus();
        });
        maintenance = new ImportMaintenance(requests, entityManager, Clock.fixed(NOW, ZoneOffset.UTC), manager);
        run = new ServiceRequest(1L, ServiceRequestKind.IMPORT);
        ReflectionTestUtils.setField(run, "id", 42L);
        ReflectionTestUtils.setField(run, "createdAt", NOW.minusSeconds(1500));
        run.setPayload(new LinkedHashMap<>(Map.of("dataType", "TOPIC", "fileName", "private-file.csv",
                "totalRows", 1, "validRows", List.of(Map.of("lineNo", 2, "values", Map.of("name", "private-cell"))),
                "expiresAt", NOW.minusNanos(1).toString())));
        when(entityManager.find(ServiceRequest.class, 42L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(run);
        logger = (Logger) LoggerFactory.getLogger(ImportMaintenance.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void cleanup() {
        logger.detachAppender(logs);
        logs.stop();
    }

    @ParameterizedTest
    @ValueSource(longs = {601, 660})
    void FR022_AT27_oldRunningSinceBecomesInterruptedWithoutChangingCommittedData(long ageSeconds) {
        running(NOW.minusSeconds(ageSeconds));
        Map<String, Object> payload = Map.copyOf(run.getPayload());
        run.setErrorRows(List.of(Map.of("line", 3, "code", "INVALID_VALUE")));
        maintenance.recoverInterrupted();
        assertThat(run.getStatus()).isEqualTo(ServiceRequestStatus.FAILED);
        assertThat(run.getErrorMessage()).isEqualTo("INTERRUPTED");
        assertThat(run.getPayload()).isEqualTo(payload);
        assertThat(run.getErrorRows()).containsExactly(Map.of("line", 3, "code", "INVALID_VALUE"));
        assertThat(run.getCompletedAt()).isNull();
        verify(requests).saveAndFlush(run);
        verify(manager).commit(any());
        assertThat(logs.list).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(longs = {-60, 0, 300, 600})
    void FR022_recentRunningSinceIsKeptEvenWhenPreviewWasCreated25MinutesAgo(long ageSeconds) {
        running(NOW.minusSeconds(ageSeconds));
        maintenance.recoverInterrupted();
        assertThat(run.getStatus()).isEqualTo(ServiceRequestStatus.RUNNING);
        assertThat(run.getErrorMessage()).isNull();
        verify(requests, never()).saveAndFlush(any());
        assertThat(logs.list).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void FR022_missingRunningSinceIsInterruptedAndWarnsWithOnlyRunId(boolean explicitNull) {
        running(null);
        if (explicitNull) run.getPayload().put("runningSince", null);
        maintenance.recoverInterrupted();
        assertThat(run.getStatus()).isEqualTo(ServiceRequestStatus.FAILED);
        assertThat(run.getErrorMessage()).isEqualTo("INTERRUPTED");
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("runId=42").doesNotContain("private-cell", "private-file");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @ParameterizedTest
    @EnumSource(value = ServiceRequestStatus.class, names = "RUNNING", mode = EnumSource.Mode.EXCLUDE)
    void FR022_rechecksStatusUnderLockSoCompletedOrChangedRunsAreNotOverwritten(ServiceRequestStatus changed) {
        running(NOW.minusSeconds(660));
        run.setStatus(changed);
        maintenance.recoverInterrupted();
        assertThat(run.getStatus()).isEqualTo(changed);
        assertThat(run.getErrorMessage()).isNull();
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void FR022_deletedCandidateDoesNotPreventStartupRecovery() {
        running(NOW.minusSeconds(660));
        when(entityManager.find(ServiceRequest.class, 42L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(null);
        maintenance.recoverInterrupted();
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void FR022_applicationRunnerInvokesRecoveryAtStartup() {
        ImportMaintenance target = mock(ImportMaintenance.class);
        new ImportMaintenance.ImportRecoveryRunner(target).run(new DefaultApplicationArguments());
        verify(target).recoverInterrupted();
    }

    @Test
    void FR024_AT28_expiryDropsOnlyValidRowsAndCommitsTheChange() {
        when(requests.findByKindAndStatus(ServiceRequestKind.IMPORT, ServiceRequestStatus.PENDING)).thenReturn(List.of(run));
        run.setErrorRows(List.of(Map.of("line", 3, "code", "INVALID_VALUE")));
        maintenance.expirePreviews();
        assertThat(run.getStatus()).isEqualTo(ServiceRequestStatus.EXPIRED);
        assertThat(run.getPayload()).doesNotContainKey("validRows")
                .containsEntry("fileName", "private-file.csv").containsEntry("dataType", "TOPIC")
                .containsEntry("totalRows", 1).containsEntry("expiresAt", NOW.minusNanos(1).toString());
        assertThat(run.getErrorRows()).hasSize(1);
        assertThat(run.getCompletedAt()).isNull();
        verify(requests).saveAndFlush(run);
        verify(manager).commit(any());
    }

    @Test
    void FR022_scheduledJobRecoversRunThatWasRecentAtStartup() {
        Clock advancingClock = mock(Clock.class);
        when(advancingClock.instant()).thenReturn(NOW);
        maintenance = new ImportMaintenance(requests, entityManager, advancingClock, manager);
        running(NOW.minusSeconds(180));
        new ImportMaintenance.ImportRecoveryRunner(maintenance).run(new DefaultApplicationArguments());
        assertThat(run.getStatus()).isEqualTo(ServiceRequestStatus.RUNNING);

        when(advancingClock.instant()).thenReturn(NOW.plusSeconds(660));
        maintenance.expirePreviews();
        assertThat(run.getStatus()).isEqualTo(ServiceRequestStatus.FAILED);
        assertThat(run.getErrorMessage()).isEqualTo("INTERRUPTED");
    }

    @Test
    void FR022_scheduledJobRecoversMissingTimestampAndWarnsWithoutPrivateData() {
        running(null);
        maintenance.expirePreviews();
        assertThat(run.getStatus()).isEqualTo(ServiceRequestStatus.FAILED);
        assertThat(run.getErrorMessage()).isEqualTo("INTERRUPTED");
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("runId=42").doesNotContain("private-cell", "private-file");
        });
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 1800})
    void FR024_previewAtOrBeforeItsDeadlineRemainsPending(long remainingSeconds) {
        run.getPayload().put("expiresAt", NOW.plusSeconds(remainingSeconds).toString());
        maintenance.expireOnAccess(42L);
        assertThat(run.getStatus()).isEqualTo(ServiceRequestStatus.PENDING);
        assertThat(run.getPayload()).containsKey("validRows");
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void FR024_lazyExpiryCommitsBeforeTheCallerContinuesAndIsIdempotent() {
        maintenance.expireOnAccess(42L);
        verify(manager).commit(any());
        assertThat(run.getStatus()).isEqualTo(ServiceRequestStatus.EXPIRED);
        maintenance.expireOnAccess(42L);
        verify(requests).saveAndFlush(run);
    }

    @ParameterizedTest
    @EnumSource(value = ServiceRequestStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    void FR024_doesNotExpireRunsThatChangedStatusAfterTheInitialQuery(ServiceRequestStatus changed) {
        when(requests.findByKindAndStatus(ServiceRequestKind.IMPORT, ServiceRequestStatus.PENDING)).thenReturn(List.of(run));
        run.setStatus(changed);
        maintenance.expirePreviews();
        assertThat(run.getStatus()).isEqualTo(changed);
        verify(requests, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = ServiceRequestKind.class, names = "IMPORT", mode = EnumSource.Mode.EXCLUDE)
    void FR024_lazyExpiryDoesNotModifyRequestsFromOtherWorkflows(ServiceRequestKind kind) {
        ServiceRequest other = new ServiceRequest(1L, kind);
        when(entityManager.find(ServiceRequest.class, 42L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(other);
        maintenance.expireOnAccess(42L);
        assertThat(other.getStatus()).isEqualTo(ServiceRequestStatus.PENDING);
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void FR024_unknownIdIsLeftForTheQueryServiceToReportAsNotFound() {
        when(entityManager.find(ServiceRequest.class, 42L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(null);
        maintenance.expireOnAccess(42L);
        verify(requests, never()).saveAndFlush(any());
    }

    private void running(Instant since) {
        run.setStatus(ServiceRequestStatus.RUNNING);
        if (since != null) run.getPayload().put("runningSince", since.toString());
        when(requests.findByKindAndStatus(ServiceRequestKind.IMPORT, ServiceRequestStatus.RUNNING)).thenReturn(List.of(run));
    }
}
