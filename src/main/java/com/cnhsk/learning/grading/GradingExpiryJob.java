package com.cnhsk.learning.grading;

import java.time.Clock;
import java.util.List;

import com.cnhsk.learning.api.Consumption;
import com.cnhsk.learning.api.EntitlementApi;
import com.cnhsk.learning.api.PaidFeature;
import com.cnhsk.learning.grading.GradingRepository.RequestRow;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Each successful conditional transition and its refund commit together, independently of other rows. */
@Component
public class GradingExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(GradingExpiryJob.class);
    private final GradingRepository repository;
    private final EntitlementApi entitlement;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public GradingExpiryJob(GradingRepository repository, EntitlementApi entitlement, ObjectMapper mapper,
            Clock clock, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.entitlement = entitlement;
        this.mapper = mapper;
        this.clock = clock;
        transactions = new TransactionTemplate(transactionManager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(cron = "${cnhsk.grading.expiry-job-cron}", zone = "Asia/Ho_Chi_Minh")
    public void run() {
        List<RequestRow> candidates;
        try {
            candidates = repository.findOverdue(clock.instant());
        } catch (RuntimeException exception) {
            log.warn("Grading expiry scan failed.");
            summary(0, 0, null);
            return;
        }
        int expired = 0;
        int refundFailed = 0;
        for (RequestRow candidate : candidates) {
            try {
                if (Boolean.TRUE.equals(transactions.execute(status -> expire(candidate)))) expired++;
            } catch (RuntimeException exception) {
                // Never include request data or exception messages: a failure can contain stored essay text.
                refundFailed++;
            }
        }
        Long remainingOverdue = null;
        try {
            remainingOverdue = repository.countOverdue(clock.instant());
        } catch (RuntimeException exception) {
            log.warn("Grading expiry count failed.");
        }
        summary(expired, refundFailed, remainingOverdue);
        if (remainingOverdue != null && remainingOverdue > 0) log.warn("Grading expiry has overdue requests remaining.");
    }

    private boolean expire(RequestRow row) {
        if (!repository.expire(row.id(), row.status(), clock.instant())) return false;
        Consumption receipt;
        try {
            receipt = mapper.treeToValue(row.payload().get("consumption"), Consumption.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("Stored grading receipt is invalid.");
        }
        if (receipt == null || receipt.feature() != PaidFeature.GRADING) {
            throw new IllegalStateException("Stored grading receipt is invalid.");
        }
        // QUOTA refunds intentionally inherit the billing API's best-effort Redis behavior.
        entitlement.refund(row.userId(), receipt);
        return true;
    }

    private void summary(int expired, int refundFailed, Long remainingOverdue) {
        log.info("Grading expiry run expired={}, refundFailed={}, remainingOverdue={}", expired, refundFailed, remainingOverdue);
    }
}
