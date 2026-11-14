package com.cnhsk.learning.grading;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cnhsk.learning.api.Consumption;
import com.cnhsk.learning.api.EntitlementApi;
import com.cnhsk.learning.api.PaidFeature;
import com.cnhsk.learning.grading.GradingRepository.RequestRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GradingExpiryJobTest {
    private static final Instant NOW = Instant.parse("2035-11-15T01:00:00Z");
    private static final String PRIVATE = "Bài tự soạn riêng tư secret-fixture@example.test";
    private final GradingRepository repository = mock(GradingRepository.class);
    private final EntitlementApi entitlement = mock(EntitlementApi.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final GradingExpiryJob job = new GradingExpiryJob(repository, entitlement, mapper, Clock.fixed(NOW, ZoneOffset.UTC), manager);
    private final Logger logger = (Logger) LoggerFactory.getLogger(GradingExpiryJob.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private Level previous;

    @BeforeEach void setup() {
        previous = logger.getLevel(); logger.setLevel(Level.INFO); logs.start(); logger.addAppender(logs);
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(repository.findOverdue(NOW)).thenReturn(List.of());
    }
    @AfterEach void cleanup() { logger.detachAppender(logs); logs.stop(); logger.setLevel(previous); }

    @Test void AT16_emptyRunLogsZeroCountsWithoutCreatingAnyTransaction() {
        job.run(); summary("expired=0, refundFailed=0, remainingOverdue=0");
        verifyNoInteractions(manager, entitlement);
    }

    @Test void AT13_eachSuccessfulRowCommitsInItsOwnRequiresNewTransaction() {
        var first = row(1, "PENDING"); var second = row(2, "ASSIGNED");
        when(repository.findOverdue(NOW)).thenReturn(List.of(first, second));
        when(repository.expire(anyLong(), anyString(), eq(NOW))).thenReturn(true);
        job.run(); summary("expired=2, refundFailed=0, remainingOverdue=0");
        verify(manager, times(2)).getTransaction(argThat(definition -> definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        verify(manager, times(2)).commit(any()); verify(manager, never()).rollback(any());
        var order = inOrder(repository, entitlement, manager);
        order.verify(repository).expire(1, "PENDING", NOW); order.verify(entitlement).refund(7, receipt(1)); order.verify(manager).commit(any());
        order.verify(repository).expire(2, "ASSIGNED", NOW); order.verify(entitlement).refund(7, receipt(2)); order.verify(manager).commit(any());
    }

    @Test void AT13_lostConditionalUpdateDoesNotRefundOrCountAsExpired() {
        when(repository.findOverdue(NOW)).thenReturn(List.of(row(1, "PENDING")));
        job.run(); summary("expired=0, refundFailed=0, remainingOverdue=0"); verifyNoInteractions(entitlement);
    }

    @Test void AT15_refundExceptionRollsBackOnlyThatRowAndTheNextRowStillCommits() {
        when(repository.findOverdue(NOW)).thenReturn(List.of(row(1, "PENDING"), row(2, "ASSIGNED")));
        when(repository.expire(anyLong(), anyString(), eq(NOW))).thenReturn(true);
        doThrow(new IllegalStateException(PRIVATE)).when(entitlement).refund(7, receipt(1));
        job.run(); summary("expired=1, refundFailed=1, remainingOverdue=0");
        verify(manager).rollback(any()); verify(manager).commit(any()); verify(entitlement).refund(7, receipt(2)); privateDataIsAbsent();
    }

    @Test void Q_T05_04_databaseFailureCountsAsRowFailureWithoutPreventingLaterRows() {
        when(repository.findOverdue(NOW)).thenReturn(List.of(row(1, "PENDING"), row(2, "PENDING")));
        when(repository.expire(1, "PENDING", NOW)).thenThrow(new IllegalStateException(PRIVATE));
        when(repository.expire(2, "PENDING", NOW)).thenReturn(true);
        job.run(); summary("expired=1, refundFailed=1, remainingOverdue=0");
        verify(entitlement, never()).refund(7, receipt(1)); verify(entitlement).refund(7, receipt(2)); privateDataIsAbsent();
    }

    @Test void Q_T05_04_corruptMissingOrWrongFeatureReceiptRollsBackWithoutCallingBilling() {
        for (String invalid : List.of("{\"essay\":\"private\"}", "{\"consumption\":\"private\"}",
                "{\"consumption\":null}", "{\"consumption\":{\"feature\":\"TRANSLATE\"}}")) {
            var row = row(1, "PENDING");
            try { when(repository.findOverdue(NOW)).thenReturn(List.of(new RequestRow(row.id(), row.userId(), row.status(),
                    mapper.readTree(invalid), null, null, null, row.deadline(), null, null, null, row.createdAt(), null, null))); }
            catch (java.io.IOException exception) { throw new IllegalStateException("Fixture invalid"); }
            when(repository.expire(1, "PENDING", NOW)).thenReturn(true);
            job.run();
        }
        verify(manager, times(4)).rollback(any()); verifyNoInteractions(entitlement); privateDataIsAbsent();
    }

    @Test void AT16_remainingRowsProduceAFixedWarningAndTheActualCount() {
        when(repository.countOverdue(NOW)).thenReturn(105L);
        job.run(); summary("expired=0, refundFailed=0, remainingOverdue=105");
        assertThat(logs.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).isEqualTo("Grading expiry has overdue requests remaining.");
        });
    }

    @Test void Q_T05_04_scanFailureLogsUnknownRemainingAndDoesNotStartTransactions() {
        when(repository.findOverdue(NOW)).thenThrow(new IllegalStateException(PRIVATE));
        job.run(); summary("expired=0, refundFailed=0, remainingOverdue=null");
        assertThat(logs.list).anySatisfy(event -> assertThat(event.getFormattedMessage()).isEqualTo("Grading expiry scan failed."));
        verify(repository, never()).countOverdue(any()); verifyNoInteractions(manager, entitlement); privateDataIsAbsent();
    }

    @Test void Q_T05_04_countFailureDoesNotPretendTheBacklogIsEmpty() {
        when(repository.countOverdue(NOW)).thenThrow(new IllegalStateException(PRIVATE));
        job.run(); summary("expired=0, refundFailed=0, remainingOverdue=null");
        assertThat(logs.list).anySatisfy(event -> assertThat(event.getFormattedMessage()).isEqualTo("Grading expiry count failed."));
        privateDataIsAbsent();
    }

    @Test void FR017_cronUsesTheApprovedPropertyAndVietnamZone() throws Exception {
        var scheduled = GradingExpiryJob.class.getMethod("run").getAnnotation(Scheduled.class);
        assertThat(scheduled.cron()).isEqualTo("${cnhsk.grading.expiry-job-cron}");
        assertThat(scheduled.zone()).isEqualTo("Asia/Ho_Chi_Minh");
    }

    private void summary(String expected) {
        assertThat(logs.list.stream().filter(event -> event.getLevel() == Level.INFO).toList()).singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).isEqualTo("Grading expiry run " + expected));
    }
    private void privateDataIsAbsent() {
        assertThat(logs.list).allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).doesNotContain(PRIVATE, "private", "consumption", "essay");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }
    private Consumption receipt(long id) { return new Consumption(Consumption.Source.CREDIT, id, PaidFeature.GRADING, "fixture-" + id, NOW); }
    private RequestRow row(long id, String state) {
        var payload = mapper.createObjectNode().put("essay", PRIVATE); payload.set("consumption", mapper.valueToTree(receipt(id)));
        return new RequestRow(id, 7, state, payload, null, state.equals("ASSIGNED") ? 8L : null, null,
                NOW.minusSeconds(1), null, null, null, NOW.minusSeconds(3600), null, null);
    }
}
