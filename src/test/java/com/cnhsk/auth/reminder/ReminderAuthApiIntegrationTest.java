package com.cnhsk.auth.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.ReminderSendResult;
import com.cnhsk.auth.api.ReminderSettings;
import com.cnhsk.auth.api.ReminderTarget;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.auth.service.AuthApiService;
import com.cnhsk.auth.service.MailService;
import com.cnhsk.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(properties = "cnhsk.reminder.batch-size=2")
class ReminderAuthApiIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-13T13:00:00Z");
    private static final LocalTime HOUR = LocalTime.of(20, 0);
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode("ReminderFixture123");

    @Autowired private AuthApi auth;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private UnsubscribeTokens tokens;
    @MockitoBean private Clock clock;
    @MockitoSpyBean private MailService mail;

    private final List<Long> ownedUsers = new ArrayList<>();

    @BeforeEach
    void fixedClock() {
        when(clock.instant()).thenReturn(NOW);
    }

    @AfterEach
    void removeOwnedFixtures() {
        for (long userId : ownedUsers) {
            jdbc.update("DELETE FROM auth.users WHERE id = ?", userId);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"banned", "unverified", "web", "disabled", "other-hour", "unconfigured",
            "suspended", "EMAIL_ARCHIVE", "WEB,EMAILING"})
    void FR008_AT11_ineligibleUsersAreNotTargets(String condition) {
        User eligible = createUser(HOUR, "WEB,EMAIL", true);
        User excluded = createUser(HOUR, "EMAIL", true);
        switch (condition) {
            case "banned" -> jdbc.update("UPDATE auth.users SET banned_at = ?, ban_reason = ? WHERE id = ?",
                    java.sql.Timestamp.from(NOW), "Test ban", excluded.getId());
            case "unverified" -> jdbc.update("UPDATE auth.users SET email_verified_at = NULL WHERE id = ?",
                    excluded.getId());
            case "web" -> jdbc.update("UPDATE auth.users SET reminder_channels = 'WEB' WHERE id = ?",
                    excluded.getId());
            case "disabled" -> jdbc.update("UPDATE auth.users SET reminder_channels = '' WHERE id = ?",
                    excluded.getId());
            case "other-hour" -> jdbc.update("UPDATE auth.users SET reminder_time = TIME '19:00' WHERE id = ?",
                    excluded.getId());
            case "unconfigured" -> jdbc.update("UPDATE auth.users SET reminder_time = NULL WHERE id = ?",
                    excluded.getId());
            case "suspended" -> jdbc.update("UPDATE auth.users SET suspended_until = ? WHERE id = ?",
                    java.sql.Timestamp.from(NOW.plusSeconds(60)), excluded.getId());
            default -> jdbc.update("UPDATE auth.users SET reminder_channels = ? WHERE id = ?",
                    condition, excluded.getId());
        }

        assertThat(auth.reminderTargets(HOUR)).containsExactly(new ReminderTarget(eligible.getId()));
        verify(mail, never()).sendStudyReminder(anyString(), anyString(), anyInt(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"EMAIL", "WEB,EMAIL"})
    void FR008_exactEmailChannelIsEligible(String channels) {
        User user = createUser(HOUR, channels, true);

        assertThat(auth.reminderTargets(HOUR)).containsExactly(new ReminderTarget(user.getId()));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1})
    void Q_T03_02_suspensionUsesClockAndIncludesItsExpiryBoundary(long secondsFromNow) {
        User user = createUser(HOUR, "EMAIL", true);
        jdbc.update("UPDATE auth.users SET suspended_until = ? WHERE id = ?",
                java.sql.Timestamp.from(NOW.plusSeconds(secondsFromNow)), user.getId());

        assertThat(auth.reminderTargets(HOUR)).isEqualTo(secondsFromNow <= 0
                ? List.of(new ReminderTarget(user.getId())) : List.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"00:00", "20:00", "23:00"})
    void Q_T01_02_matchesRawDatabaseWallClockTimeWithoutTimezoneConversion(String time) {
        LocalTime hour = LocalTime.parse(time);
        User user = createUser(hour, "EMAIL", true);

        assertThat(jdbc.queryForObject("SELECT reminder_time::text FROM auth.users WHERE id = ?",
                String.class, user.getId())).isEqualTo(time + ":00");
        assertThat(auth.reminderTargets(hour)).containsExactly(new ReminderTarget(user.getId()));
    }

    @Test
    void FR008_batchIsOrderedLimitedAndWarnsExactRemainingCountWithoutPersonalData() {
        List<User> candidates = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            candidates.add(createUser(HOUR, "EMAIL", true));
        }
        Logger logger = (Logger) LoggerFactory.getLogger(AuthApiService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThat(auth.reminderTargets(HOUR)).containsExactly(
                    new ReminderTarget(candidates.get(0).getId()), new ReminderTarget(candidates.get(1).getId()));
            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).isEqualTo("Study reminder batch limit reached; remaining=3");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reminderSettingsUsesTheSameDefaultsWithoutWriting(boolean verified) {
        User user = createUser(null, "EMAIL", verified);
        Map<String, Object> before = reminderRow(user);

        assertThat(auth.reminderSettings(user.getId()))
                .isEqualTo(new ReminderSettings(HOUR, true, false, verified, false));
        assertThat(reminderRow(user)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "WEB", "EMAIL", "WEB,EMAIL"})
    void reminderSettingsReturnsConfiguredValues(String channels) {
        User user = createUser(LocalTime.of(7, 0), channels, true);
        Map<String, Object> before = reminderRow(user);

        assertThat(auth.reminderSettings(user.getId())).isEqualTo(new ReminderSettings(LocalTime.of(7, 0),
                channels.contains("WEB"), channels.contains("EMAIL"), true, true));
        assertThat(reminderRow(user)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"banned", "unverified", "web", "suspended", "missing"})
    void AT11_rechecksDatabaseAfterSelectionEvenWithACachedEntity(String condition) {
        User user = createUser(HOUR, "EMAIL", true);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(entityManager.find(User.class, user.getId()).getEmailVerifiedAt()).isNotNull();
            assertThat(auth.reminderTargets(HOUR)).containsExactly(new ReminderTarget(user.getId()));
            switch (condition) {
                case "banned" -> jdbc.update("UPDATE auth.users SET banned_at = ?, ban_reason = ? WHERE id = ?",
                        java.sql.Timestamp.from(NOW), "Test ban", user.getId());
                case "unverified" -> jdbc.update("UPDATE auth.users SET email_verified_at = NULL WHERE id = ?",
                        user.getId());
                case "web" -> jdbc.update("UPDATE auth.users SET reminder_channels = 'WEB' WHERE id = ?",
                        user.getId());
                case "suspended" -> jdbc.update("UPDATE auth.users SET suspended_until = ? WHERE id = ?",
                        java.sql.Timestamp.from(NOW.plusSeconds(60)), user.getId());
                default -> jdbc.update("DELETE FROM auth.users WHERE id = ?", user.getId());
            }

            assertThat(auth.sendStudyReminder(user.getId(), 5)).isEqualTo(ReminderSendResult.SKIPPED);
        });
        verify(mail, never()).sendStudyReminder(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void expiredSuspensionAllowsDeliveryAndIssuesAVerifiableToken() {
        User user = createUser(HOUR, "EMAIL", true);
        jdbc.update("UPDATE auth.users SET suspended_until = ? WHERE id = ?",
                java.sql.Timestamp.from(NOW.minusSeconds(1)), user.getId());
        doNothing().when(mail).sendStudyReminder(anyString(), anyString(), anyInt(), anyString());

        assertThat(auth.sendStudyReminder(user.getId(), 5)).isEqualTo(ReminderSendResult.SENT);

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(mail).sendStudyReminder(org.mockito.ArgumentMatchers.eq(user.getEmail()),
                org.mockito.ArgumentMatchers.eq(user.getDisplayName()), org.mockito.ArgumentMatchers.eq(5),
                token.capture());
        assertThat(tokens.verify(token.getValue())).isEqualTo(user.getId());
    }

    @Test
    void smtpFailureReachesTheAuthApiCallerForJobRetries() {
        User user = createUser(HOUR, "EMAIL", true);
        MailSendException failure = new MailSendException("simulated SMTP failure");
        doThrow(failure).when(mail).sendStudyReminder(anyString(), anyString(), anyInt(), anyString());

        assertThatThrownBy(() -> auth.sendStudyReminder(user.getId(), 5)).isSameAs(failure);
    }

    @Test
    void smtpFailureCanBeRetriedWithoutMarkingTheCallersTransactionRollbackOnly() {
        User user = createUser(HOUR, "EMAIL", true);
        MailSendException failure = new MailSendException("simulated transient SMTP failure");
        doThrow(failure).doNothing().when(mail).sendStudyReminder(anyString(), anyString(), anyInt(), anyString());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThatThrownBy(() -> auth.sendStudyReminder(user.getId(), 5)).isSameAs(failure);
            assertThat(status.isRollbackOnly()).isFalse();
            assertThat(auth.sendStudyReminder(user.getId(), 5)).isEqualTo(ReminderSendResult.SENT);
        });
    }

    private User createUser(LocalTime time, String channels, boolean verified) {
        User user = new User("reminder-target-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH,
                "Reminder Target");
        user.setReminderTime(time);
        user.setReminderChannels(channels);
        user.setEmailVerifiedAt(verified ? NOW.minusSeconds(60) : null);
        user = users.saveAndFlush(user);
        ownedUsers.add(user.getId());
        return user;
    }

    private Map<String, Object> reminderRow(User user) {
        return jdbc.queryForMap("SELECT reminder_time::text, reminder_channels, updated_at FROM auth.users WHERE id = ?",
                user.getId());
    }
}
