package com.cnhsk.learning.reference;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.learning.reference.ReferenceReportService.Reason;
import com.cnhsk.learning.reference.ReferenceReportService.ReportCommand;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReferenceReportServiceTest {

    private final AuthApi auth = mock(AuthApi.class);
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final RateLimiter counters = mock(RateLimiter.class);
    private final AuditService audit = mock(AuditService.class);
    private final ReferenceReportService service = new ReferenceReportService(auth, jdbc, counters, audit,
            Clock.fixed(Instant.parse("2026-11-14T16:59:59.500Z"), ZoneOffset.UTC));

    @BeforeEach
    void setup() {
        when(auth.currentUser()).thenReturn(new CurrentUser(7, Set.of(RoleCode.LEARNER), false));
        when(jdbc.queryForList(anyString(), anyMap(), eq(Long.class))).thenReturn(List.of(11L));
        when(counters.increment(anyString(), any(Duration.class))).thenReturn(1L);
    }

    @Test
    void FR009_FR010_reservesTheVietnamDayBeforeWritingTheExactUntrimmedDetail() {
        String note = "  Ghi chu 中文 😀\n  ";
        service.report("11", new ReportCommand(Reason.DEAD_LINK, note));
        var order = org.mockito.Mockito.inOrder(counters, audit);
        order.verify(counters).increment("reference:report:7:11:20261114", Duration.ofSeconds(1));
        order.verify(audit).recordDetail(7L, "REFERENCE_REPORTED", "REFERENCE", 11L,
                Map.of("reason", "DEAD_LINK", "note", note));
        verify(auth, never()).requireVerifiedEmail(anyLong());
    }

    @Test
    void FR009_nullNotesArePreservedAnd300CodePointsAreAllowed() {
        service.report("11", new ReportCommand(Reason.OTHER, null));
        var detail = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(audit).recordDetail(eq(7L), eq("REFERENCE_REPORTED"), eq("REFERENCE"), eq(11L), detail.capture());
        assertThat(detail.getValue()).containsEntry("note", null);
        service.report("11", new ReportCommand(Reason.OTHER, "😀".repeat(300)));
    }

    @Test
    void FR009_invalidBodiesDoNotConsumeQuotaOrReadStorage() {
        error(() -> service.report("11", null), ErrorCode.VALIDATION_FAILED);
        error(() -> service.report("11", new ReportCommand(null, null)), ErrorCode.VALIDATION_FAILED);
        error(() -> service.report("11", new ReportCommand(Reason.OTHER, "😀".repeat(301))), ErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(auth, jdbc, counters, audit);
    }

    @Test
    void FR002_nonLearnersDoNotConsumeQuota() {
        when(auth.currentUser()).thenReturn(new CurrentUser(7, Set.of(RoleCode.CONTENT_EDITOR), true));
        error(() -> service.report("11", new ReportCommand(Reason.OTHER, null)), ErrorCode.FORBIDDEN);
        verifyNoInteractions(jdbc, counters, audit);
    }

    @Test
    void FR009_invalidAndUnpublishedIdsDoNotConsumeQuota() {
        for (String id : List.of("0", "-1", "abc", "9223372036854775808")) {
            error(() -> service.report(id, new ReportCommand(Reason.OTHER, null)), ErrorCode.REFERENCE_NOT_FOUND);
        }
        when(jdbc.queryForList(anyString(), anyMap(), eq(Long.class))).thenReturn(List.of());
        error(() -> service.report("11", new ReportCommand(Reason.OTHER, null)), ErrorCode.REFERENCE_NOT_FOUND);
        verifyNoInteractions(counters, audit);
    }

    @Test
    void AT12_duplicateReturnsRetryWithoutAnotherAudit() {
        when(counters.increment(anyString(), any(Duration.class))).thenReturn(2L);
        assertThatThrownBy(() -> service.report("11", new ReportCommand(Reason.OTHER, null)))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(ErrorCode.RATE_LIMITED);
                    assertThat(error.getExtra()).containsEntry("retryAfterSeconds", 1L);
                });
        verifyNoInteractions(audit);
    }

    @Test
    void FR010_redisFailureRejectsAndLogsNoCallerOrInput() {
        when(counters.increment(anyString(), any(Duration.class))).thenThrow(new IllegalStateException("PRIVATE_NOTE"));
        Logger logger = (Logger) LoggerFactory.getLogger(ReferenceReportService.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            error(() -> service.report("11", new ReportCommand(Reason.OTHER, "PRIVATE_NOTE")), ErrorCode.INTERNAL_ERROR);
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getFormattedMessage())
                    .isEqualTo("Reference report rate limit unavailable; rejecting request");
            assertThat(appender.list.get(0).getThrowableProxy()).isNull();
            verifyNoInteractions(audit);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void FR010_auditFailureDoesNotRefundAndDoesNotExposeTheUnderlyingMessage() {
        doThrow(new IllegalStateException("PRIVATE_NOTE")).when(audit)
                .recordDetail(anyLong(), anyString(), anyString(), anyLong(), anyMap());
        assertThatThrownBy(() -> service.report("11", new ReportCommand(Reason.OTHER, "PRIVATE_NOTE")))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
                    assertThat(error.getMessage()).isEqualTo("Đã xảy ra lỗi nội bộ.");
                    assertThat(error.getCause()).isNull();
                });
        verify(counters, never()).clear(anyString());
    }

    private void error(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getCode()).isEqualTo(code));
    }
}
