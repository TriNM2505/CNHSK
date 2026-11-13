package com.cnhsk.learning.reference.web;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.learning.reference.ReferenceReportService;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.GlobalExceptionHandler;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.security.RateLimiter;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class ReferenceReportControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NamedParameterJdbcTemplate named;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private AuditService audit;
    private long user;
    private long item;

    @BeforeEach
    void setup() {
        user = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,?) RETURNING id",
                Long.class, UUID.randomUUID() + "@example.test", PASSWORD_HASH, "Tu soan");
        item = seed("PUBLISHED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEAD_LINK", "WRONG_CONTENT", "OTHER"})
    void FR009_AT12_reportReturns204WithExactAuditAndDuplicateReturns429(String reason) throws Exception {
        String note = "  Ghi chu 中文 😀\n  ";
        String body = mapper.writeValueAsString(Map.of("reason", reason, "note", note, "userId", Long.MAX_VALUE));
        mvc.perform(learner(Long.toString(item), body)).andExpect(status().isNoContent()).andExpect(content().string(""));
        var row = jdbc.queryForMap("SELECT target_type,target_id,detail::text AS detail FROM shared.audit_logs "
                + "WHERE actor_id=? AND action='REFERENCE_REPORTED'", user);
        assertThat(row).containsEntry("target_type", "REFERENCE").containsEntry("target_id", item);
        JsonNode detail = mapper.readTree(row.get("detail").toString());
        assertThat(detail.path("reason").asText()).isEqualTo(reason);
        assertThat(detail.path("note").asText()).isEqualTo(note);
        assertThat(detail.size()).isEqualTo(2);
        mvc.perform(learner(Long.toString(item), body)).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error_code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
        assertThat(auditCount()).isEqualTo(1);
    }

    @Test
    void AT13_FR002_onlyLearnerCanReportAndCookieRequestsRequireCsrf() throws Exception {
        String body = "{\"reason\":\"OTHER\"}";
        mvc.perform(post(path(Long.toString(item))).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
        for (String role : List.of("CONTENT_EDITOR", "TEACHER", "MODERATOR", "ADMIN", "ACCOUNTANT")) {
            mvc.perform(post(path(Long.toString(item))).header("Authorization", "Bearer " + token(role))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        }
        String access = token("LEARNER");
        mvc.perform(post(path(Long.toString(item))).cookie(new Cookie("access_token", access))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        assertThat(auditCount()).isZero();
        mvc.perform(post(path(Long.toString(item))).cookie(new Cookie("access_token", access),
                        new Cookie("XSRF-TOKEN", "reference-csrf-test"))
                        .header("X-XSRF-TOKEN", "reference-csrf-test")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
        assertThat(auditCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "1", "{", "{}", "{\"reason\":null}", "{\"reason\":1}",
            "{\"reason\":\"other\"}", "{\"reason\":\"OTHER\",\"note\":1}",
            "{\"reason\":\"OTHER\",\"note\":[]}", "{\"reason\":\"OTHER\"} {}"})
    void FR009_badBodyReturns422WithoutConsumingTheDailySlot(String body) throws Exception {
        mvc.perform(learner(Long.toString(item), body)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.request_id").isNotEmpty());
        mvc.perform(learner(Long.toString(item), "{\"reason\":\"OTHER\"}")).andExpect(status().isNoContent());
        assertThat(auditCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "  中文 😀  "})
    void FR009_optionalNoteIsNotTrimmedAndMissingNoteIsStoredAsNull(String note) throws Exception {
        var body = mapper.createObjectNode().put("reason", "OTHER");
        if (note != null) body.put("note", note);
        mvc.perform(learner(Long.toString(item), body.toString())).andExpect(status().isNoContent());
        JsonNode detail = mapper.readTree(jdbc.queryForObject("SELECT detail::text FROM shared.audit_logs "
                + "WHERE actor_id=? AND action='REFERENCE_REPORTED'", String.class, user));
        assertThat(detail.has("note")).isTrue();
        if (note == null) assertThat(detail.path("note").isNull()).isTrue();
        else assertThat(detail.path("note").asText()).isEqualTo(note);
    }

    @Test
    void FR009_noteLimitUsesCodePointsAndExplicitNullIsAllowed() throws Exception {
        String longBody = mapper.writeValueAsString(Map.of("reason", "OTHER", "note", "😀".repeat(301)));
        mvc.perform(learner(Long.toString(item), longBody)).andExpect(status().isUnprocessableEntity());
        String boundary = mapper.writeValueAsString(Map.of("reason", "OTHER", "note", "😀".repeat(300)));
        mvc.perform(learner(Long.toString(item), boundary)).andExpect(status().isNoContent());
        long another = seed("PUBLISHED");
        mvc.perform(learner(Long.toString(another), "{\"reason\":\"OTHER\",\"note\":null}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void FR009_invalidMissingDraftAndHiddenIdsAre404() throws Exception {
        var ids = new java.util.ArrayList<>(List.of("0", "-1", "abc", "9223372036854775808", Long.toString(Long.MAX_VALUE)));
        ids.add(Long.toString(seed("DRAFT")));
        ids.add(Long.toString(seed("HIDDEN")));
        for (String id : ids) mvc.perform(learner(id, "{\"reason\":\"OTHER\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code").value("REFERENCE_NOT_FOUND"));
        assertThat(auditCount()).isZero();
        mvc.perform(learner(Long.toString(item), "{\"reason\":\"OTHER\"}")).andExpect(status().isNoContent());
    }

    @Test
    void FR009_noteNeverAppearsInLogs() throws Exception {
        String note = "NOTE_PRIVATE_" + UUID.randomUUID();
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        root.addAppender(appender);
        try {
            mvc.perform(learner(Long.toString(item), mapper.writeValueAsString(Map.of("reason", "OTHER", "note", note))))
                    .andExpect(status().isNoContent());
            assertThat(appender.list).allSatisfy(event -> assertThat(event.getFormattedMessage()).doesNotContain(note));
        } finally {
            root.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void FR010_redisFailureReturnsFixed500AndWritesNoAudit() throws Exception {
        AuthApi identity = mock(AuthApi.class);
        when(identity.currentUser()).thenReturn(new CurrentUser(user, Set.of(RoleCode.LEARNER), false));
        RateLimiter broken = mock(RateLimiter.class);
        when(broken.increment(anyString(), any(Duration.class))).thenThrow(new IllegalStateException("PRIVATE_NOTE"));
        var service = new ReferenceReportService(identity, named, broken, audit,
                Clock.fixed(Instant.parse("2026-11-14T00:00:00Z"), ZoneOffset.UTC));
        var standalone = MockMvcBuilders.standaloneSetup(new ReferenceReportController(service, mapper,
                new GlobalExceptionHandler())).setControllerAdvice(new GlobalExceptionHandler()).build();
        String response = standalone.perform(post(path(Long.toString(item))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OTHER\",\"note\":\"PRIVATE_NOTE\"}"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.error_code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Đã xảy ra lỗi nội bộ."))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("PRIVATE_NOTE");
        assertThat(auditCount()).isZero();
    }

    @Test
    void FR002_noExtraMethodOrChildRouteIsOpened() throws Exception {
        mvc.perform(get(path(Long.toString(item)))).andExpect(status().isUnauthorized());
        mvc.perform(post(path(Long.toString(item)) + "/extra")).andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder learner(String id, String body) {
        return post(path(id)).header("Authorization", "Bearer " + token("LEARNER"))
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String token(String role) {
        return jwt.issueAccessToken(user, Set.of(role), false, UUID.randomUUID());
    }

    private String path(String id) {
        return "/api/references/" + id + "/report";
    }

    private long auditCount() {
        return jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs WHERE actor_id=? AND action='REFERENCE_REPORTED'",
                Long.class, user);
    }

    private long seed(String state) {
        return jdbc.queryForObject("INSERT INTO learning.reference_items(kind,title,url,status,created_by,published_at) "
                        + "VALUES ('BOOK','Demo','https://fahasa.com/demo',?,?,?) RETURNING id", Long.class,
                state, user, state.equals("PUBLISHED") ? Timestamp.from(Instant.parse("2026-11-13T00:00:00Z")) : null);
    }
}
