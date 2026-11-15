package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import com.cnhsk.auth.api.AuthApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

class PlanMaintenanceTest {
    private static final Instant NOW = Instant.parse("2035-11-15T17:10:00.123456Z");

    @ParameterizedTest
    @ValueSource(ints = {0, 7})
    void capturesOneInstantAndDelegatesExactlyOnceRegardlessOfAffectedRows(int affectedRows) {
        AuthApi auth = mock(AuthApi.class);
        Clock clock = spy(Clock.fixed(NOW, ZoneOffset.UTC));
        when(auth.downgradeExpiredPlans(NOW)).thenReturn(affectedRows);

        new PlanMaintenance(auth, clock).downgradeExpiredPlans();

        verify(clock).instant();
        verify(auth).downgradeExpiredPlans(NOW);
        verifyNoMoreInteractions(auth, clock);
    }

    @Test
    void propagatesTheOriginalPersistenceFailureWithoutRetry() {
        AuthApi auth = mock(AuthApi.class);
        Clock clock = spy(Clock.fixed(NOW, ZoneOffset.UTC));
        IllegalStateException failure = new IllegalStateException("Persistence unavailable");
        when(auth.downgradeExpiredPlans(NOW)).thenThrow(failure);

        assertThatThrownBy(() -> new PlanMaintenance(auth, clock).downgradeExpiredPlans())
                .isSameAs(failure);

        verify(clock).instant();
        verify(auth).downgradeExpiredPlans(NOW);
        verifyNoMoreInteractions(auth, clock);
    }

    @Test
    void declaresTheConfigurableDailyVietnamSchedule() throws Exception {
        Scheduled schedule = schedule();
        assertThat(schedule.cron()).isEqualTo("${cnhsk.billing.downgrade-cron:0 10 0 * * *}");
        assertThat(schedule.zone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(new MockEnvironment().resolveRequiredPlaceholders(schedule.cron()))
                .isEqualTo("0 10 0 * * *");
        assertThat(new MockEnvironment().withProperty("cnhsk.billing.downgrade-cron", "-")
                .resolveRequiredPlaceholders(schedule.cron())).isEqualTo(Scheduled.CRON_DISABLED);
    }

    @Test
    void defaultScheduleFiresAtTenPastMidnightInVietnamAcrossUtcDates() throws Exception {
        Scheduled schedule = schedule();
        CronExpression cron = CronExpression.parse(new MockEnvironment().resolveRequiredPlaceholders(schedule.cron()));
        ZoneId vietnam = ZoneId.of(schedule.zone());
        ZonedDateTime before = Instant.parse("2035-11-15T17:09:59Z").atZone(vietnam);

        ZonedDateTime first = cron.next(before);
        assertThat(first).isEqualTo(ZonedDateTime.parse("2035-11-16T00:10:00+07:00[Asia/Ho_Chi_Minh]"));
        assertThat(first.toInstant()).isEqualTo(Instant.parse("2035-11-15T17:10:00Z"));
        assertThat(cron.next(first)).isEqualTo(first.plusDays(1));
    }

    private static Scheduled schedule() throws Exception {
        Scheduled schedule = PlanMaintenance.class.getMethod("downgradeExpiredPlans").getAnnotation(Scheduled.class);
        assertThat(schedule).isNotNull();
        return schedule;
    }
}
