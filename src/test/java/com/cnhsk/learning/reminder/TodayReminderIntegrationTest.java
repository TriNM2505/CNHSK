package com.cnhsk.learning.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

import com.cnhsk.support.AbstractIntegrationTest;
import com.cnhsk.shared.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class TodayReminderIntegrationTest extends AbstractIntegrationTest {

    private static final String ROUTE = "/api/reminders/today";
    private static final Instant NOW = Instant.parse("2026-11-14T13:30:00Z");
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode("TodayReminderFixture123");

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private ObjectMapper mapper;
    @Autowired private SqlCapture sqlCapture;
    @MockitoBean private Clock clock;

    private final List<Long> ownedUsers = new ArrayList<>();
    private final List<Long> ownedLexemes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
    }

    @AfterEach
    void removeOwnedFixtures() {
        sqlCapture.stop();
        for (long userId : ownedUsers) {
            jdbc.update("DELETE FROM learning.study_events WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM learning.user_progress WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM auth.users WHERE id = ?", userId);
        }
        for (long lexemeId : ownedLexemes) {
            jdbc.update("DELETE FROM learning.lexemes WHERE id = ?", lexemeId);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "2026-11-14T12:59:59Z, 20:00, WEB, 2, false",
            "2026-11-14T13:00:00Z, 20:00, WEB, 2, true",
            "2026-11-14T13:30:00Z, 20:00, 'WEB,EMAIL', 2, true",
            "2026-11-14T13:30:00Z, 20:00, WEB, 0, false",
            "2026-11-14T13:30:00Z, 20:00, EMAIL, 2, false",
            "2026-11-14T13:30:00Z, 20:00, '', 2, false",
            "2026-11-14T16:59:00Z, 23:00, WEB, 2, true",
            "2026-11-14T17:30:00Z, 20:00, WEB, 2, false",
            "2026-11-14T17:00:00Z, 00:00, WEB, 2, true"
    })
    void AT18_realSettingsAndSummaryApplyVietnamTimeAndAlwaysReturnDueCount(
            String instant, String reminderTime, String channels, int dueCount, boolean show) throws Exception {
        when(clock.instant()).thenReturn(Instant.parse(instant));
        long userId = user(reminderTime, channels);
        seedCards(userId, dueCount, Instant.parse(instant).minusSeconds(1));
        seedCards(userId, 1, Instant.parse(instant).plusSeconds(3600));

        assertThat(today(userId, userId)).isEqualTo(mapper.valueToTree(
                Map.of("show", show, "dueCount", dueCount, "reminderTime", reminderTime)));
    }

    @Test
    void FR019_queryUserIdCannotOverrideAuthenticatedCallerOrReadOtherSchedules() throws Exception {
        long caller = user("20:00", "WEB");
        long other = user("23:00", "EMAIL");
        seedCards(caller, 2, NOW.minusSeconds(1));
        seedCards(other, 5, NOW.minusSeconds(1));

        assertThat(today(caller, other)).isEqualTo(mapper.valueToTree(
                Map.of("show", true, "dueCount", 2, "reminderTime", "20:00")));
        assertThat(today(other, caller)).isEqualTo(mapper.valueToTree(
                Map.of("show", false, "dueCount", 5, "reminderTime", "23:00")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"WEB", "EMAIL", ""})
    void FR003_unconfiguredUsesDefaultsWithoutPersistingSettingsOrAudit(String storedChannels) throws Exception {
        long userId = user(null, storedChannels);
        seedCards(userId, 1, NOW.minusSeconds(1));
        Map<String, Object> before = settingsRow(userId);
        long auditsBefore = auditCount(userId);

        assertThat(today(userId, Long.MAX_VALUE)).isEqualTo(mapper.valueToTree(
                Map.of("show", true, "dueCount", 1, "reminderTime", "20:00")));

        assertThat(settingsRow(userId)).isEqualTo(before);
        assertThat(auditCount(userId)).isEqualTo(auditsBefore);
    }

    @Test
    void Q1_studyAndReminderEventsTodayDoNotChangeBannerOrCreateAnotherEvent() throws Exception {
        long userId = user("20:00", "WEB");
        seedCards(userId, 1, NOW.minusSeconds(1));
        JsonNode before = today(userId, userId);
        LocalDate today = NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
        jdbc.update("INSERT INTO learning.study_events(user_id,event_type,local_date) VALUES (?,'STUDY',?)",
                userId, today);
        jdbc.update("INSERT INTO learning.study_events(user_id,event_type,channel,local_date) "
                + "VALUES (?,'REMINDER_SENT','EMAIL',?), (?,'REMINDER_SENT','WEB',?)", userId, today, userId, today);

        assertThat(today(userId, userId)).isEqualTo(before);
        assertThat(before.path("show").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.study_events WHERE user_id = ?",
                Long.class, userId)).isEqualTo(3);
    }

    private JsonNode today(long userId, long queryUserId) throws Exception {
        String token = jwt.issueAccessToken(userId, List.of("LEARNER"), false, UUID.randomUUID());
        byte[] body;
        List<CapturedSql> statements;
        sqlCapture.start();
        try {
            body = mvc.perform(get(ROUTE).header("Authorization", "Bearer " + token)
                            .param("userId", Long.toString(queryUserId)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        } finally {
            statements = sqlCapture.stop();
        }
        assertThat(statements).isNotEmpty().allSatisfy(statement -> {
            assertThat(statement.sql()).startsWith("select ").doesNotContain("study_events");
            assertThat(statement.readOnly()).as("GET queries run in a read-only transaction").isTrue();
        });
        assertThat(statements).anyMatch(statement -> statement.sql().contains("auth.users"));
        assertThat(statements).anyMatch(statement -> statement.sql().contains("learning.user_progress"));
        JsonNode result = mapper.readTree(body);
        assertThat(result).hasSize(3);
        assertThat(result.has("show") && result.has("dueCount") && result.has("reminderTime")).isTrue();
        return result;
    }

    private long user(String reminderTime, String channels) {
        long userId = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name,"
                        + "reminder_time,reminder_channels) VALUES (?,?,'Người thử nhắc học',?::time,?) RETURNING id",
                Long.class, "today-reminder-" + UUID.randomUUID() + "@example.test",
                PASSWORD_HASH, reminderTime, channels);
        ownedUsers.add(userId);
        return userId;
    }

    private void seedCards(long userId, int count, Instant dueAt) {
        for (int index = 0; index < count; index++) {
            long lexemeId = jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,meaning_vi,hsk_level) "
                            + "VALUES ('WORD',?,'Nghĩa kiểm thử tự soạn',1) RETURNING id",
                    Long.class, "today-reminder-" + UUID.randomUUID());
            ownedLexemes.add(lexemeId);
            jdbc.update("INSERT INTO learning.user_progress(user_id,target_type,target_id,next_review_at) "
                    + "VALUES (?,'LEXEME',?,?)", userId, lexemeId, Timestamp.from(dueAt));
        }
    }

    private Map<String, Object> settingsRow(long userId) {
        return jdbc.queryForMap("SELECT reminder_time::text,reminder_channels FROM auth.users WHERE id = ?", userId);
    }

    private long auditCount(long userId) {
        return jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs WHERE actor_id = ?", Long.class, userId);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class QueryCaptureConfiguration {
        @Bean static SqlCapture todayReminderSqlCapture() { return new SqlCapture(); }

        @Bean static BeanPostProcessor todayReminderDataSourceCapture(SqlCapture capture) {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!(bean instanceof DataSource dataSource)) {
                        return bean;
                    }
                    return new DelegatingDataSource(dataSource) {
                        @Override public Connection getConnection() throws SQLException {
                            return capture.wrap(super.getConnection());
                        }
                        @Override public Connection getConnection(String username, String password) throws SQLException {
                            return capture.wrap(super.getConnection(username, password));
                        }
                    };
                }
            };
        }
    }

    private record CapturedSql(String sql, boolean readOnly) { }

    static final class SqlCapture {
        private final ThreadLocal<List<CapturedSql>> statements = new ThreadLocal<>();

        void start() { statements.set(new ArrayList<>()); }

        List<CapturedSql> stop() {
            List<CapturedSql> result = statements.get();
            statements.remove();
            return result == null ? List.of() : List.copyOf(result);
        }

        Connection wrap(Connection connection) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class}, (proxy, method, arguments) -> {
                        if (method.getName().startsWith("prepare")) {
                            capture(connection, arguments);
                        }
                        try {
                            Object result = method.invoke(connection, arguments);
                            if (method.getName().equals("createStatement") && result instanceof Statement statement) {
                                return wrap(statement, connection);
                            }
                            return result;
                        } catch (InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                    });
        }

        private Statement wrap(Statement statement, Connection connection) {
            return (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(),
                    new Class<?>[] {Statement.class}, (proxy, method, arguments) -> {
                        if (method.getName().startsWith("execute") || method.getName().equals("addBatch")) {
                            capture(connection, arguments);
                        }
                        try {
                            return method.invoke(statement, arguments);
                        } catch (InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                    });
        }

        private void capture(Connection connection, Object[] arguments) throws SQLException {
            List<CapturedSql> active = statements.get();
            if (active != null && arguments != null && arguments.length > 0 && arguments[0] instanceof String sql) {
                active.add(new CapturedSql(sql.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "),
                        connection.isReadOnly()));
            }
        }
    }
}
