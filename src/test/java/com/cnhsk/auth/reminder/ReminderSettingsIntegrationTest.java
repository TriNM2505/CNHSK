package com.cnhsk.auth.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cnhsk.auth.entity.RoleCode;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.entity.UserRole;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.auth.repository.UserRoleRepository;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ReminderSettingsIntegrationTest extends AbstractIntegrationTest {

    private static final String ROUTE = "/api/me/reminder";
    private static final String ACTION = "REMINDER_SETTINGS_UPDATED";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode("ReminderFixture123");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoSpyBean private AuditService auditService;

    private final List<Long> ownedUsers = new ArrayList<>();

    @AfterEach
    void removeOwnedFixtures() {
        for (long userId : ownedUsers) {
            jdbc.update("DELETE FROM shared.audit_logs WHERE actor_id = ?", userId);
            jdbc.update("DELETE FROM auth.user_roles WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM auth.users WHERE id = ?", userId);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void FR003_AT01_unconfiguredSettingsReturnExactDefaultsWithoutWriting(boolean verified) throws Exception {
        User user = createUser(verified, null, verified ? "EMAIL" : "WEB");
        Map<String, Object> before = settingsRow(user);

        MvcResult result = mockMvc.perform(get(ROUTE).header("Authorization", bearer(user, verified)))
                .andExpect(status().isOk()).andReturn();

        assertSettings(result, "20:00", true, false, verified, false);
        assertThat(settingsRow(user)).isEqualTo(before);
        assertThat(auditCount(user)).isZero();
    }

    @Test
    void FR006_AT02_verifiedLearnerUpdatesSettingsAndAuditsOnlyTimeAndChannels() throws Exception {
        User user = createUser(true, LocalTime.of(7, 0), "WEB");

        MvcResult result = update(user, "20:00", true, true);

        assertSettings(result, "20:00", true, true, true, true);
        assertThat(settingsRow(user).get("reminder_time")).isEqualTo("20:00:00");
        assertThat(settingsRow(user).get("reminder_channels")).isEqualTo("WEB,EMAIL");
        assertThat(auditCount(user)).isEqualTo(1);
        String payload = jdbc.queryForObject(
                "SELECT detail::text FROM shared.audit_logs WHERE actor_id = ? AND action = ? "
                        + "AND target_type = 'user' AND target_id = ?",
                String.class, user.getId(), ACTION, user.getId());
        JsonNode audit = objectMapper.readTree(payload);
        assertThat(audit).isEqualTo(objectMapper.valueToTree(Map.of(
                "before", Map.of("reminderTime", "07:00", "channels", Map.of("web", true, "email", false)),
                "after", Map.of("reminderTime", "20:00", "channels", Map.of("web", true, "email", true)))));
        assertThat(payload).doesNotContain(user.getEmail(), user.getDisplayName(), "@example.test",
                "emailVerified", "configured", "timezone", "password");

        MvcResult readBack = mockMvc.perform(get(ROUTE).header("Authorization", bearer(user, true)))
                .andExpect(status().isOk()).andReturn();
        assertSettings(readBack, "20:00", true, true, true, true);
    }

    @Test
    void FR005_AT03_unverifiedEmailRejectsTheWholeUpdateEvenWhenJwtSaysVerified() throws Exception {
        User user = createUser(false, LocalTime.of(9, 0), "WEB");
        Map<String, Object> before = settingsRow(user);

        mockMvc.perform(put(ROUTE).header("Authorization", bearer(user, true))
                        .contentType(MediaType.APPLICATION_JSON).content(body("22:00", false, true)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("EMAIL_NOT_VERIFIED"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.request_id").isNotEmpty());

        assertThat(settingsRow(user)).isEqualTo(before);
        assertThat(auditCount(user)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"20:30", "25:00", "24:00", "7:00", "20:00:00", " 20:00", "20:00 ", "20:0", "2000", ""})
    void FR004_AT04_invalidHoursReturn422WithoutWriting(String time) throws Exception {
        User user = createUser(true, LocalTime.of(9, 0), "WEB");
        Map<String, Object> before = settingsRow(user);

        mockMvc.perform(put(ROUTE).header("Authorization", bearer(user, true))
                        .contentType(MediaType.APPLICATION_JSON).content(body(time, true, false)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("reminderTime"))
                .andExpect(jsonPath("$.request_id").isNotEmpty());

        assertThat(settingsRow(user)).isEqualTo(before);
        assertThat(auditCount(user)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"web\":true,\"email\":false}",
            "{\"reminderTime\":\"20:00\",\"email\":false}",
            "{\"reminderTime\":\"20:00\",\"web\":true}",
            "{\"reminderTime\":null,\"web\":true,\"email\":false}",
            "{\"reminderTime\":\"20:00\",\"web\":null,\"email\":false}",
            "{\"reminderTime\":\"20:00\",\"web\":true,\"email\":null}"
    })
    void FR004_AT04_missingOrNullFieldsReturn422WithoutWriting(String request) throws Exception {
        User user = createUser(true, LocalTime.of(9, 0), "EMAIL");
        Map<String, Object> before = settingsRow(user);

        mockMvc.perform(put(ROUTE).header("Authorization", bearer(user, true))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isArray());

        assertThat(settingsRow(user)).isEqualTo(before);
        assertThat(auditCount(user)).isZero();
    }

    @Test
    void FR006_AT05_turningOffBothChannelsRemainsConfiguredOnSubsequentGet() throws Exception {
        User user = createUser(false, null, "WEB");

        MvcResult result = update(user, "12:00", false, false);

        assertSettings(result, "12:00", false, false, false, true);
        assertThat(settingsRow(user).get("reminder_time")).isEqualTo("12:00:00");
        assertThat(settingsRow(user).get("reminder_channels")).isEqualTo("");
        MvcResult readBack = mockMvc.perform(get(ROUTE).header("Authorization", bearer(user, false)))
                .andExpect(status().isOk()).andReturn();
        assertSettings(readBack, "12:00", false, false, false, true);
        assertThat(auditCount(user)).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"true, false, WEB", "false, true, EMAIL", "true, true, 'WEB,EMAIL'"})
    void FR006_channelsAreStoredInCanonicalOrder(boolean web, boolean email, String stored) throws Exception {
        User user = createUser(true, null, "WEB");

        assertSettings(update(user, "20:00", web, email), "20:00", web, email, true, true);

        assertThat(settingsRow(user).get("reminder_channels")).isEqualTo(stored);
    }

    @ParameterizedTest
    @ValueSource(strings = {"00:00", "09:00", "19:00", "23:00"})
    void FR004_validHourBoundariesPreserveTheExactResponseFormat(String time) throws Exception {
        User user = createUser(false, null, "WEB");

        assertSettings(update(user, time, true, false), time, true, false, false, true);
        assertThat(settingsRow(user).get("reminder_time")).isEqualTo(time + ":00");
        MvcResult readBack = mockMvc.perform(get(ROUTE).header("Authorization", bearer(user, false)))
                .andExpect(status().isOk()).andReturn();
        assertSettings(readBack, time, true, false, false, true);
    }

    @Test
    void FR001_queryCannotSelectAnotherLearnersSettings() throws Exception {
        User caller = createUser(true, LocalTime.of(8, 0), "WEB");
        User other = createUser(true, LocalTime.of(6, 0), "EMAIL");
        Map<String, Object> otherBefore = settingsRow(other);

        MvcResult read = mockMvc.perform(get(ROUTE).header("Authorization", bearer(caller, true))
                        .param("userId", other.getId().toString()))
                .andExpect(status().isOk()).andReturn();
        assertSettings(read, "08:00", true, false, true, true);
        MvcResult write = mockMvc.perform(put(ROUTE).header("Authorization", bearer(caller, true))
                        .param("userId", other.getId().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("21:00", true, false)))
                .andExpect(status().isOk()).andReturn();
        assertSettings(write, "21:00", true, false, true, true);
        assertThat(settingsRow(other)).isEqualTo(otherBefore);
        assertThat(auditCount(other)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"00:00", "12:00", "20:00", "23:00"})
    void FR003_databaseWallClockTimeIsReturnedWithoutTimezoneConversion(String time) throws Exception {
        User user = createUser(true, null, "WEB");
        jdbc.update("UPDATE auth.users SET reminder_time = CAST(? AS time) WHERE id = ?", time, user.getId());
        Map<String, Object> before = settingsRow(user);

        MvcResult result = mockMvc.perform(get(ROUTE).header("Authorization", bearer(user, true)))
                .andExpect(status().isOk()).andReturn();

        assertSettings(result, time, true, false, true, true);
        assertThat(settingsRow(user)).isEqualTo(before);
        assertThat(auditCount(user)).isZero();
    }

    @Test
    void FR006_auditFailureRollsBackBothSettingsFields() throws Exception {
        User user = createUser(true, LocalTime.of(7, 0), "WEB");
        Map<String, Object> before = settingsRow(user);
        doThrow(new IllegalStateException("simulated reminder audit failure"))
                .when(auditService).record(eq(user.getId()), eq(ACTION), anyString(), any(), any());

        mockMvc.perform(put(ROUTE).header("Authorization", bearer(user, true))
                        .contentType(MediaType.APPLICATION_JSON).content(body("22:00", false, true)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error_code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.request_id").isNotEmpty());

        assertThat(settingsRow(user)).isEqualTo(before);
        assertThat(auditCount(user)).isZero();
    }

    @Test
    void FR001_deletedCurrentAccountCannotReadOrUpdateSettings() throws Exception {
        String bearer = "Bearer " + jwtService.issueAccessToken(Long.MAX_VALUE, List.of("LEARNER"), false,
                UUID.randomUUID());

        mockMvc.perform(get(ROUTE).header("Authorization", bearer))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
        mockMvc.perform(put(ROUTE).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(body("20:00", true, false)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
    }

    private User createUser(boolean verified, LocalTime reminderTime, String channels) {
        User user = new User("reminder-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH, "Reminder Learner");
        user.setEmailVerifiedAt(verified ? Instant.parse("2026-01-01T00:00:00Z") : null);
        user.setReminderTime(reminderTime);
        user.setReminderChannels(channels);
        user = userRepository.saveAndFlush(user);
        ownedUsers.add(user.getId());
        userRoleRepository.saveAndFlush(new UserRole(user.getId(), RoleCode.LEARNER, null));
        return user;
    }

    private MvcResult update(User user, String time, boolean web, boolean email) throws Exception {
        // The database verification state is authoritative even with a stale unverified JWT.
        return mockMvc.perform(put(ROUTE).header("Authorization", bearer(user, false))
                        .contentType(MediaType.APPLICATION_JSON).content(body(time, web, email)))
                .andExpect(status().isOk()).andReturn();
    }

    private String body(String time, boolean web, boolean email) throws Exception {
        return objectMapper.writeValueAsString(Map.of("reminderTime", time, "web", web, "email", email));
    }

    private String bearer(User user, boolean verifiedClaim) {
        return "Bearer " + jwtService.issueAccessToken(user.getId(), List.of("LEARNER"), verifiedClaim,
                UUID.randomUUID());
    }

    private Map<String, Object> settingsRow(User user) {
        return jdbc.queryForMap("SELECT reminder_time::text AS reminder_time, "
                        + "reminder_channels, updated_at FROM auth.users WHERE id = ?",
                user.getId());
    }

    private int auditCount(User user) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM shared.audit_logs WHERE actor_id = ? AND action = ?",
                Integer.class, user.getId(), ACTION);
    }

    private void assertSettings(MvcResult result, String time, boolean web, boolean email, boolean verified,
            boolean configured) throws Exception {
        JsonNode actual = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(actual).isEqualTo(objectMapper.valueToTree(Map.of(
                "reminderTime", time,
                "channels", Map.of("web", web, "email", email),
                "emailVerified", verified,
                "configured", configured,
                "timezone", "Asia/Ho_Chi_Minh")));
    }
}
