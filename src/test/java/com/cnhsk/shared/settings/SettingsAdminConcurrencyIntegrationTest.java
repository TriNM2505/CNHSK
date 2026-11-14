package com.cnhsk.shared.settings;

import static com.cnhsk.shared.settings.SettingKey.*;
import static com.cnhsk.shared.settings.SettingsAdminModels.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class SettingsAdminConcurrencyIntegrationTest extends AbstractIntegrationTest {
    @Autowired private SettingsAdminService service;
    @Autowired private SettingsService settings;
    @Autowired private JdbcTemplate jdbc;
    private SettingsAdminFixture fixture;

    @BeforeEach void setup() { fixture = new SettingsAdminFixture(jdbc, settings); }
    @AfterEach void cleanup() { fixture.close(); }

    @Test
    void competingMinAndMaxWritesCannotBreakTheEffectiveInvariant() throws Exception {
        assertThat(race(() -> service.change(GRADING_MIN_HANZI.key(), fixture.actor, () -> new SettingWrite(400, "Min")),
                () -> service.change(GRADING_MAX_HANZI.key(), fixture.actor, () -> new SettingWrite(300, "Max"))))
                .containsExactlyInAnyOrder("OK", "SETTING_OUT_OF_RANGE");
        assertThat(settings.getInt(GRADING_MIN_HANZI)).isLessThan(settings.getInt(GRADING_MAX_HANZI));
        assertThat(fixture.auditCount(GRADING_MIN_HANZI) + fixture.auditCount(GRADING_MAX_HANZI)).isEqualTo(1);
    }

    @Test
    void sameKeyWritersKeepBothAuditsAndReadThePreviousCommittedValue() throws Exception {
        assertThat(race(() -> service.change(BILLING_FREE_MONTHLY_LIMIT.key(), fixture.actor, () -> new SettingWrite(20, "Một")),
                () -> service.change(BILLING_FREE_MONTHLY_LIMIT.key(), fixture.actor, () -> new SettingWrite(30, "Hai"))))
                .containsExactly("OK", "OK");
        var logs = jdbc.queryForList("SELECT detail::text FROM shared.audit_logs WHERE actor_id=? AND target_type='SETTING' "
                + "AND target_id=? ORDER BY id", String.class, fixture.actor, BILLING_FREE_MONTHLY_LIMIT.auditId());
        assertThat(logs).hasSize(2);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var first = mapper.readTree(logs.get(0));
        var second = mapper.readTree(logs.get(1));
        assertThat(first.path("oldValue").asInt()).isEqualTo(10);
        assertThat(second.path("oldValue").asInt()).isEqualTo(first.path("newValue").asInt());
        assertThat(settings.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(second.path("newValue").asInt());
    }

    private List<String> race(Callable<?> first, Callable<?> second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var futures = java.util.stream.Stream.of(first, second).map(action -> executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                try { action.call(); return "OK"; }
                catch (ApiException exception) { return exception.getCode().name(); }
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(futures.get(0).get(30, TimeUnit.SECONDS), futures.get(1).get(30, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }
}
