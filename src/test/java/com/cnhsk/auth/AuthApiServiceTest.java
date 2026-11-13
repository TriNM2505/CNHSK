package com.cnhsk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import java.time.Clock;
import java.time.Instant;
import java.sql.ResultSet;
import java.time.LocalTime;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.PlanState;
import com.cnhsk.auth.api.PublicProfile;
import com.cnhsk.auth.api.ReminderSendResult;
import com.cnhsk.auth.api.ReminderSettings;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.auth.repository.UserRoleRepository;
import com.cnhsk.auth.reminder.ReminderSettingsService;
import com.cnhsk.auth.reminder.UnsubscribeTokens;
import com.cnhsk.auth.service.AuthApiService;
import com.cnhsk.auth.service.MailService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AuthApiServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private NamedParameterJdbcTemplate jdbc;
    @Mock private ReminderSettingsService reminderSettings;
    @Mock private UnsubscribeTokens unsubscribeTokens;
    @Mock private MailService mailService;

    private AuthApi authApi;

    @BeforeEach
    void setUp() {
        authApi = new AuthApiService(userRepository, userRoleRepository, jdbc, new ObjectMapper(), Clock.systemUTC(),
                reminderSettings, unsubscribeTokens, mailService, 500);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentUserMapsVerifiedJwtPrincipalWithoutDatabaseQueries() {
        AuthenticatedUser principal = new AuthenticatedUser(17L,
                Set.of("LEARNER", "TEACHER"), true, "jwt-id", null, Instant.parse("2026-10-21T00:00:00Z"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        CurrentUser currentUser = authApi.currentUser();

        assertThat(currentUser.userId()).isEqualTo(17L);
        assertThat(currentUser.roles()).containsExactlyInAnyOrder(RoleCode.LEARNER, RoleCode.TEACHER);
        assertThat(currentUser.emailVerified()).isTrue();
        verifyNoInteractions(userRepository, userRoleRepository);
    }

    @Test
    void currentUserWithoutPrincipalThrowsUnauthenticated() {
        assertApiFailure(() -> authApi.currentUser(), ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void requireVerifiedEmailAllowsVerifiedUser() {
        User user = new User("verified@example.test", "hash", "Verified");
        user.setEmailVerifiedAt(Instant.parse("2026-10-21T00:00:00Z"));
        when(userRepository.findById(17L)).thenReturn(Optional.of(user));

        authApi.requireVerifiedEmail(17L);

        verify(userRepository).findById(17L);
    }

    @Test
    void requireVerifiedEmailRejectsUnverifiedUserWithAccountUnverified() {
        when(userRepository.findById(17L))
                .thenReturn(Optional.of(new User("unverified@example.test", "hash", "Unverified")));

        assertApiFailure(() -> authApi.requireVerifiedEmail(17L),
                ErrorCode.ACCOUNT_UNVERIFIED, HttpStatus.FORBIDDEN);
    }

    @Test
    void requireVerifiedEmailTreatsMissingUserAsUnauthenticated() {
        when(userRepository.findById(17L)).thenReturn(Optional.empty());

        assertApiFailure(() -> authApi.requireVerifiedEmail(17L),
                ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void hasRoleChecksAnyUserThroughRoleRepository() {
        when(userRoleRepository.existsByUserIdAndRoleCode(99L,
                com.cnhsk.auth.entity.RoleCode.TEACHER)).thenReturn(true);

        assertThat(authApi.hasRole(99L, RoleCode.TEACHER)).isTrue();
        verify(userRoleRepository).existsByUserIdAndRoleCode(99L,
                com.cnhsk.auth.entity.RoleCode.TEACHER);
    }

    @Test
    void hasRoleReturnsFalseWhenRoleIsAbsent() {
        when(userRoleRepository.existsByUserIdAndRoleCode(99L,
                com.cnhsk.auth.entity.RoleCode.ADMIN)).thenReturn(false);

        assertThat(authApi.hasRole(99L, RoleCode.ADMIN)).isFalse();
    }

    @Test
    void hasRoleRejectsNullRole() {
        assertThatThrownBy(() -> authApi.hasRole(99L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("role must not be null.");
    }

    @Test
    void displayNamesUsesOneLookupAndOmitsUnknownIds() {
        Set<Long> ids = Set.of(1L, 2L, 3L);
        User first = mock(User.class);
        when(first.getId()).thenReturn(1L);
        when(first.getDisplayName()).thenReturn("First");
        User second = mock(User.class);
        when(second.getId()).thenReturn(2L);
        when(second.getDisplayName()).thenReturn("Second");
        when(userRepository.findAllById(ids)).thenReturn(List.of(first, second));

        assertThat(authApi.displayNames(ids)).containsExactlyInAnyOrderEntriesOf(
                Map.of(1L, "First", 2L, "Second"));
        verify(userRepository).findAllById(ids);
    }

    @Test
    void displayNamesForEmptySetReturnsEmptyMapWithoutQuery() {
        assertThat(authApi.displayNames(Set.of())).isEmpty();

        verifyNoInteractions(userRepository);
    }

    @Test
    void bannedUserIdsReturnsOnlyIdsFromTheBanQuery() throws Exception {
        ResultSet rows = mock(ResultSet.class);
        when(rows.next()).thenReturn(true, true, false);
        when(rows.getLong("id")).thenReturn(11L, 12L);
        when(jdbc.query(anyString(), anyMap(), org.mockito.ArgumentMatchers.<ResultSetExtractor<Set<Long>>>any()))
                .thenAnswer(invocation -> {
                    ResultSetExtractor<Set<Long>> extractor = invocation.getArgument(2);
                    return extractor.extractData(rows);
                });

        assertThat(authApi.bannedUserIds()).containsExactlyInAnyOrder(11L, 12L);

        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), eq(Map.of()),
                org.mockito.ArgumentMatchers.<ResultSetExtractor<Set<Long>>>any());
        assertThat(sql.getValue()).contains("auth.users", "banned_at IS NOT NULL");
        verifyNoInteractions(userRepository, userRoleRepository);
    }

    @Test
    void bannedUserIdsReturnsEmptySetWhenNoAccountsAreBanned() throws Exception {
        ResultSet rows = mock(ResultSet.class);
        when(rows.next()).thenReturn(false);
        when(jdbc.query(anyString(), anyMap(), org.mockito.ArgumentMatchers.<ResultSetExtractor<Set<Long>>>any()))
                .thenAnswer(invocation -> {
                    ResultSetExtractor<Set<Long>> extractor = invocation.getArgument(2);
                    return extractor.extractData(rows);
                });

        assertThat(authApi.bannedUserIds()).isEmpty();
    }

    @Test
    void bannedUserIdsPropagatesDatabaseFailures() {
        when(jdbc.query(anyString(), anyMap(), org.mockito.ArgumentMatchers.<ResultSetExtractor<Set<Long>>>any()))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThatThrownBy(authApi::bannedUserIds)
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void publicProfilesUsesOnePublicColumnBatchAndOmitsUnknownIds() throws Exception {
        Set<Long> ids = Set.of(1L, 2L, 3L);
        ResultSet rows = mock(ResultSet.class);
        when(rows.next()).thenReturn(true, true, false);
        when(rows.getLong("id")).thenReturn(1L, 2L);
        when(rows.getString("display_name")).thenReturn("First", "Second");
        when(rows.getString("avatar_url")).thenReturn("https://example.test/first.png", (String) null);
        when(jdbc.query(anyString(), anyMap(),
                org.mockito.ArgumentMatchers.<ResultSetExtractor<Map<Long, PublicProfile>>>any()))
                .thenAnswer(invocation -> {
                    ResultSetExtractor<Map<Long, PublicProfile>> extractor = invocation.getArgument(2);
                    return extractor.extractData(rows);
                });

        assertThat(authApi.publicProfiles(ids)).containsExactlyInAnyOrderEntriesOf(Map.of(
                1L, new PublicProfile("First", "https://example.test/first.png"),
                2L, new PublicProfile("Second", null)));

        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<Map<String, ?>> parameters = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(jdbc).query(sql.capture(), parameters.capture(),
                org.mockito.ArgumentMatchers.<ResultSetExtractor<Map<Long, PublicProfile>>>any());
        assertThat(sql.getValue()).contains("SELECT id, display_name, avatar_url", "WHERE id IN (:ids)")
                .doesNotContain("email", "password_hash", "role");
        assertThat(parameters.getValue().get("ids")).isEqualTo(ids);
        verifyNoInteractions(userRepository, userRoleRepository);
    }

    @Test
    void publicProfilesReturnsEmptyForEmptySetWithoutQuery() {
        assertThat(authApi.publicProfiles(Set.of())).isEmpty();

        verifyNoInteractions(jdbc);
    }

    @Test
    void publicProfilesRejectsNullOrInvalidIds() {
        Set<Long> idsWithNull = new HashSet<>(Arrays.asList(1L, null));

        assertThatThrownBy(() -> authApi.publicProfiles(null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Tham số không hợp lệ.");
        assertThatThrownBy(() -> authApi.publicProfiles(idsWithNull))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Tham số không hợp lệ.");
        assertThatThrownBy(() -> authApi.publicProfiles(Set.of(-1L)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Tham số không hợp lệ.");
        verifyNoInteractions(jdbc);
    }

    @Test
    void planStateCopiesUsageIntoAnImmutableSnapshot() {
        Map<String, Integer> usage = new HashMap<>(Map.of("TRANSLATE", 2));
        Instant reset = Instant.parse("2026-11-01T00:00:00Z");
        PlanState snapshot = new PlanState("FREE", null, usage, reset);
        usage.put("TRANSLATE", 9);

        assertThat(snapshot.planCode()).isEqualTo("FREE");
        assertThat(snapshot.planExpiresAt()).isNull();
        assertThat(snapshot.usageResetAt()).isEqualTo(reset);
        assertThat(snapshot.freeUsage()).containsExactlyEntriesOf(Map.of("TRANSLATE", 2));
        assertThatThrownBy(() -> snapshot.freeUsage().put("TRANSLATE", 3))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new PlanState(null, null, Map.of(), reset))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Tham số không hợp lệ.");
    }

    @Test
    void readPlanMapsAnImmutableSnapshotWithoutLockOrWrite() throws Exception {
        java.sql.ResultSet row = mock(java.sql.ResultSet.class);
        Instant expiresAt = Instant.parse("2031-12-01T00:00:00.123456Z");
        when(row.getString("plan_code")).thenReturn("FREE", "PREMIUM");
        when(row.getString("free_usage")).thenReturn("{\"TRANSLATE\":3}");
        when(row.getTimestamp("plan_expires_at")).thenReturn((java.sql.Timestamp) null, java.sql.Timestamp.from(expiresAt));
        when(row.getTimestamp("usage_reset_at")).thenReturn(java.sql.Timestamp.from(Instant.EPOCH));
        when(jdbc.query(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(Map.of("id", 17L)),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<PlanState>>any()))
                .thenAnswer(invocation -> {
                    assertThat((String) invocation.getArgument(0)).doesNotContainIgnoringCase("FOR UPDATE");
                    org.springframework.jdbc.core.RowMapper<PlanState> mapping = invocation.getArgument(2);
                    return List.of(mapping.mapRow(row, 0));
                });
        PlanState state = authApi.readPlan(17L).orElseThrow();
        assertThat(state.planCode()).isEqualTo("FREE");
        assertThat(state.planExpiresAt()).isNull();
        assertThat(state.usageResetAt()).isEqualTo(Instant.EPOCH);
        assertThat(state.freeUsage()).containsExactlyEntriesOf(Map.of("TRANSLATE", 3));
        assertThatThrownBy(() -> state.freeUsage().put("TRANSLATE", 4)).isInstanceOf(UnsupportedOperationException.class);
        PlanState premium = authApi.readPlan(17L).orElseThrow();
        assertThat(premium.planCode()).isEqualTo("PREMIUM");
        assertThat(premium.planExpiresAt()).isEqualTo(expiresAt);
        assertThat(premium.usageResetAt()).isEqualTo(Instant.EPOCH);
        assertThat(premium.freeUsage()).containsExactlyEntriesOf(Map.of("TRANSLATE", 3));
        verifyNoInteractions(userRepository, userRoleRepository);
    }

    @Test
    void readPlanReturnsEmptyForMissingUserWithoutNotFoundException() {
        when(jdbc.query(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(Map.of("id", 17L)),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<PlanState>>any())).thenReturn(List.of());
        assertThat(authApi.readPlan(17L)).isEmpty();
    }

    @Test
    void maskedEmailsForEmptySetReturnsEmptyMapWithoutQuery() {
        assertThat(authApi.maskedEmails(Set.of())).isEmpty();
        verifyNoInteractions(jdbc, userRepository, userRoleRepository);
    }

    @Test
    void unknownPlanCodeReturnsZeroWithoutQuery() {
        assertThat(authApi.countActivePlan("PREMIUM_PLUS", Instant.EPOCH)).isZero();
        assertThat(authApi.countActivePlan("UNSUPPORTED", Instant.EPOCH)).isZero();
        verifyNoInteractions(jdbc);
    }

    @Test
    void newReadMethodsRejectInvalidArgumentsWithFixedMessage() {
        for (Runnable invalid : List.<Runnable>of(() -> authApi.maskedEmails(null),
                () -> authApi.readPlan(0), () -> authApi.readPlan(-1),
                () -> authApi.maskedEmails(Set.of(-1L)),
                () -> authApi.countActivePlan(null, Instant.EPOCH),
                () -> authApi.countActivePlan(" ", Instant.EPOCH),
                () -> authApi.countActivePlan("PREMIUM", null))) {
            assertThatThrownBy(invalid::run).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Tham số không hợp lệ.");
        }
        verifyNoInteractions(jdbc);
    }

    @Test
    void reminderTargetsRejectsNullOrNonWholeHoursBeforeQuerying() {
        for (LocalTime invalid : new LocalTime[] {null, LocalTime.of(20, 30), LocalTime.of(20, 0, 1),
                LocalTime.of(20, 0, 0, 1)}) {
            assertThatThrownBy(() -> authApi.reminderTargets(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(jdbc);
    }

    @Test
    void reminderSettingsDelegatesToTheSharedTypedSnapshot() {
        ReminderSettings snapshot = new ReminderSettings(LocalTime.of(20, 0), true, false, false, false);
        when(reminderSettings.readSettings(17L)).thenReturn(snapshot);

        assertThat(authApi.reminderSettings(17L)).isSameAs(snapshot);
        verify(reminderSettings).readSettings(17L);
        verifyNoInteractions(jdbc, userRepository, userRoleRepository);
    }

    @Test
    void reminderDeliveryRejectsInvalidUserOrNegativeCount() {
        for (Runnable invalid : List.<Runnable>of(() -> authApi.sendStudyReminder(0, 5),
                () -> authApi.sendStudyReminder(-1, 0), () -> authApi.sendStudyReminder(1, -1),
                () -> authApi.reminderSettings(0), () -> authApi.reminderSettings(-1))) {
            assertThatThrownBy(invalid::run).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(jdbc, mailService, unsubscribeTokens);
    }

    @Test
    void reminderDeliveryWithoutDueCardsSkipsWithoutDatabaseOrMail() {
        assertThat(authApi.sendStudyReminder(17, 0)).isEqualTo(ReminderSendResult.SKIPPED);
        verifyNoInteractions(jdbc, mailService, unsubscribeTokens);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void nonPositiveReminderBatchSizePreventsContextStartup(int batchSize) {
        new ApplicationContextRunner().withUserConfiguration(AuthApiService.class)
                .withBean(UserRepository.class, () -> userRepository)
                .withBean(UserRoleRepository.class, () -> userRoleRepository)
                .withBean(NamedParameterJdbcTemplate.class, () -> jdbc)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(Clock.class, Clock::systemUTC)
                .withBean(ReminderSettingsService.class, () -> reminderSettings)
                .withBean(UnsubscribeTokens.class, () -> unsubscribeTokens)
                .withBean(MailService.class, () -> mailService)
                .withPropertyValues("cnhsk.reminder.batch-size=" + batchSize)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .hasStackTraceContaining("cnhsk.reminder.batch-size must be positive.");
                });
    }

    private static void assertApiFailure(Runnable invocation, ErrorCode code, HttpStatus status) {
        assertThatThrownBy(invocation::run)
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.getCode()).isEqualTo(code);
                    assertThat(apiException.getStatus()).isEqualTo(status);
                });
    }
}
