package com.cnhsk.community.contest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.api.ContestAttemptApi;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Self-authored, committed fixtures so HTTP transactions and concurrent requests see the same data. */
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "dev"})
abstract class ContestFixtures extends AbstractIntegrationTest {
    static final String BASE = "/api/community/contests";
    static final Instant NOW = Instant.parse("2035-11-14T04:00:00Z");
    private static final String HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtService jwt;
    @Autowired StringRedisTemplate redis;
    @MockitoBean Clock clock;
    @MockitoSpyBean AuthApi auth;
    @MockitoSpyBean ContestAttemptApi attempts;
    volatile Instant now;
    long creator;
    long learner;
    long exam;
    long contest;
    final List<Long> users = new ArrayList<>();
    final List<Long> exams = new ArrayList<>();
    final List<Long> questions = new ArrayList<>();
    final List<Long> contests = new ArrayList<>();

    @BeforeEach
    void prepareContestFixtures() {
        now = NOW;
        when(clock.instant()).thenAnswer(ignored -> now);
        when(clock.millis()).thenAnswer(ignored -> now.toEpochMilli());
        when(clock.withZone(any(ZoneId.class))).thenAnswer(call -> Clock.fixed(now, call.getArgument(0)));
        creator = user(true);
        learner = user(true);
        exam = exam(15);
        question(exam);
        contest = contest(-60, 3600, null);
        clearInvocations(auth, attempts);
    }

    @AfterEach
    void cleanContestFixtures() {
        for (long id : contests) jdbc.update("DELETE FROM community.contests WHERE id=?", id);
        for (long id : users) {
            redis.delete("contest:tab-hidden:" + id);
            jdbc.update("DELETE FROM shared.audit_logs WHERE actor_id=?", id);
            jdbc.update("DELETE FROM learning.user_progress WHERE user_id=?", id);
            jdbc.update("DELETE FROM learning.study_events WHERE user_id=?", id);
            jdbc.update("DELETE FROM learning.attempt_items WHERE attempt_id IN (SELECT id FROM learning.attempts WHERE user_id=?)", id);
            jdbc.update("DELETE FROM learning.attempts WHERE user_id=?", id);
            jdbc.update("DELETE FROM auth.user_roles WHERE user_id=?", id);
            jdbc.update("DELETE FROM auth.users WHERE id=?", id);
        }
        for (long id : exams) {
            jdbc.update("DELETE FROM learning.exam_questions WHERE exam_id=?", id);
            jdbc.update("DELETE FROM learning.exams WHERE id=?", id);
        }
        for (long id : questions) jdbc.update("DELETE FROM learning.questions WHERE id=?", id);
        users.clear(); exams.clear(); questions.clear(); contests.clear();
    }

    long user(boolean verified) {
        long id = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name,avatar_url,email_verified_at) "
                        + "VALUES (?,?,'Thí sinh tự soạn','https://example.test/avatar.png',?) RETURNING id", Long.class,
                UUID.randomUUID() + "@example.test", HASH, verified ? Timestamp.from(NOW.minusSeconds(60)) : null);
        users.add(id);
        return id;
    }

    long exam(int duration) {
        long id = jdbc.queryForObject("INSERT INTO learning.exams(code,title,hsk_level,status,sections) "
                        + "VALUES (?,'Đề tự soạn T-05',1,'DRAFT',?::jsonb) RETURNING id", Long.class,
                "T05-" + UUID.randomUUID(), "[{\"index\":0,\"name\":\"Đọc\",\"duration_min\":" + duration + "}]");
        exams.add(id);
        return id;
    }

    void question(long examId) {
        long id = jdbc.queryForObject("INSERT INTO learning.questions(code,qtype,hsk_level,stem,options,correct_key,explanation,status) "
                        + "VALUES (?,'MULTIPLE_CHOICE',1,'水 có nghĩa gì?',?::jsonb,'A','Giải thích tự soạn T-05','APPROVED') RETURNING id", Long.class,
                "T05-" + UUID.randomUUID(), "[{\"key\":\"A\",\"text\":\"nước\",\"is_correct\":true},"
                        + "{\"key\":\"B\",\"text\":\"lửa\",\"is_correct\":false}]");
        questions.add(id);
        jdbc.update("INSERT INTO learning.exam_questions(exam_id,question_id) VALUES (?,?)", examId, id);
    }

    long contest(long startSeconds, long endSeconds, Integer capacity) {
        long id = jdbc.queryForObject("INSERT INTO community.contests(name,exam_id,starts_at,ends_at,max_participants,status,created_by) "
                        + "VALUES ('Cuộc thi tự soạn T-05',?,?,?,?,'PUBLISHED',?) RETURNING id", Long.class, exam,
                Timestamp.from(NOW.plusSeconds(startSeconds)), Timestamp.from(NOW.plusSeconds(endSeconds)), capacity, creator);
        contests.add(id);
        return id;
    }

    long attempt(long userId, long contestId, String state, Long score, Long max, Integer duration, boolean suspicious) {
        return jdbc.queryForObject("INSERT INTO learning.attempts(user_id,kind,ref_type,ref_id,status,served_at,submitted_at,expires_at,score,max_score,duration_ms,suspicious) "
                        + "VALUES (?,'CONTEST','CONTEST',?,?,?,?,?,?,?,?,?) RETURNING id", Long.class, userId, contestId, state,
                Timestamp.from(NOW.minusSeconds(60)), state.equals("SUBMITTED") ? Timestamp.from(NOW.minusSeconds(5)) : null,
                Timestamp.from(NOW.plusSeconds(60)), score, max, duration, suspicious);
    }

    void publishResults() {
        jdbc.update("UPDATE community.contests SET ends_at=?,ranking_published_at=? WHERE id=?",
                Timestamp.from(NOW.minusSeconds(1)), Timestamp.from(NOW), contest);
    }

    String bearer(long userId, String... roles) {
        List<String> granted = roles.length == 0 ? List.of("LEARNER") : List.of(roles);
        return "Bearer " + jwt.issueAccessToken(userId, granted, true, UUID.randomUUID());
    }

    Cookie cookie(long userId) {
        return new Cookie("access_token", bearer(userId).substring(7));
    }

    MockHttpServletRequestBuilder enter(long userId) {
        return post(BASE + "/{id}/attempts", contest).header("Authorization", bearer(userId));
    }

    JsonNode response(MockHttpServletRequestBuilder request, int expected) throws Exception {
        String body = mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return body.isEmpty() ? mapper.nullNode() : mapper.readTree(body);
    }

    JsonNode error(MockHttpServletRequestBuilder request, int expected, String code) throws Exception {
        JsonNode body = response(request, expected);
        assertThat(body.path("error_code").asText()).isEqualTo(code);
        assertThat(body.path("message").asText()).isNotBlank();
        assertThat(body.path("request_id").asText()).isNotBlank();
        assertThat(fields(body)).doesNotContain("trace", "exception", "stackTrace");
        return body;
    }

    static Set<String> fields(JsonNode node) {
        Set<String> result = new HashSet<>(); node.fieldNames().forEachRemaining(result::add); return result;
    }

    static Instant time(JsonNode node, String field) {
        String text = node.path(field).asText();
        OffsetDateTime parsed = OffsetDateTime.parse(text);
        assertThat(text).endsWith("+07:00");
        assertThat(parsed.getOffset()).isEqualTo(ZoneOffset.ofHours(7));
        return parsed.toInstant();
    }

    static void noPrivateFields(JsonNode node) {
        for (String key : List.of("email", "avatarUrl", "answer", "accept", "correct", "correctAnswer", "is_correct", "explanation")) {
            assertThat(node.findValues(key)).as(key).isEmpty();
        }
    }
}
