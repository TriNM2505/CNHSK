package com.cnhsk.learning.content.importer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.cnhsk.learning.common.ServiceRequest;
import com.cnhsk.learning.common.ServiceRequestKind;
import com.cnhsk.learning.common.ServiceRequestRepository;
import com.cnhsk.learning.common.ServiceRequestStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@EnableScheduling
public class ImportMaintenance {
    private static final Logger log = LoggerFactory.getLogger(ImportMaintenance.class);
    private static final Duration RECOVERY_AGE = Duration.ofMinutes(10);

    private final ServiceRequestRepository requests;
    private final EntityManager entityManager;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public ImportMaintenance(ServiceRequestRepository requests, EntityManager entityManager, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.requests = requests;
        this.entityManager = entityManager;
        this.clock = clock;
        transactions = new TransactionTemplate(transactionManager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void recoverInterrupted() {
        Instant cutoff = clock.instant().minus(RECOVERY_AGE);
        // Select on real columns only; inspect JSONB in Java (AC-09).
        for (ServiceRequest candidate : requests.findByKindAndStatus(
                ServiceRequestKind.IMPORT, ServiceRequestStatus.RUNNING)) {
            transactions.executeWithoutResult(status -> {
                ServiceRequest run = lockedImport(candidate.getId(), ServiceRequestStatus.RUNNING);
                if (run == null) return;
                String runningSince = (String) run.getPayload().get("runningSince");
                if (runningSince == null || Instant.parse(runningSince).isBefore(cutoff)) {
                    if (runningSince == null) {
                        log.warn("Import missing runningSince; recovering interrupted runId={}", run.getId());
                    }
                    run.setStatus(ServiceRequestStatus.FAILED);
                    run.setErrorMessage("INTERRUPTED");
                    requests.saveAndFlush(run);
                }
            });
        }
    }

    @Scheduled(cron = "0 */15 * * * *", zone = "Asia/Ho_Chi_Minh")
    public void expirePreviews() {
        Instant now = clock.instant();
        for (ServiceRequest candidate : requests.findByKindAndStatus(
                ServiceRequestKind.IMPORT, ServiceRequestStatus.PENDING)) {
            expireOnAccess(candidate.getId(), now);
        }
        recoverInterrupted();
    }

    /** Commits lazy expiry before the caller builds a response or raises an HTTP error. */
    public void expireOnAccess(long id) {
        expireOnAccess(id, clock.instant());
    }

    private void expireOnAccess(long id, Instant now) {
        transactions.executeWithoutResult(status -> {
            ServiceRequest run = lockedImport(id, ServiceRequestStatus.PENDING);
            if (run == null) return;
            if (Instant.parse((String) run.getPayload().get("expiresAt")).isBefore(now)) {
                Map<String, Object> payload = new LinkedHashMap<>(run.getPayload());
                payload.remove("validRows");
                run.setPayload(payload);
                run.setStatus(ServiceRequestStatus.EXPIRED);
                requests.saveAndFlush(run);
            }
        });
    }

    private ServiceRequest lockedImport(long id, ServiceRequestStatus expected) {
        ServiceRequest run = entityManager.find(ServiceRequest.class, id, LockModeType.PESSIMISTIC_WRITE);
        return run != null && run.getKind() == ServiceRequestKind.IMPORT && run.getStatus() == expected ? run : null;
    }

    @Component
    static class ImportRecoveryRunner implements ApplicationRunner {
        private final ImportMaintenance maintenance;

        ImportRecoveryRunner(ImportMaintenance maintenance) {
            this.maintenance = maintenance;
        }

        @Override
        public void run(ApplicationArguments args) {
            maintenance.recoverInterrupted();
        }
    }
}
