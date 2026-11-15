package com.cnhsk.learning.contest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.cnhsk.learning.api.ContestAttemptApi.ContestSession;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.settings.SettingKey;
import com.cnhsk.shared.settings.SettingsService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
public class ContestAttemptIntegrationTest extends AbstractIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-14T04:00:00Z");
    @Autowired private ContestAttemptService contests;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @MockitoBean private Clock clock;
    @MockitoBean private SettingsService settings;
    private Fixture fixture;
    private Instant now;
    private long learner;
    private long exam;

    @BeforeEach
    void setup() {
        now = NOW;
        when(clock.instant()).thenAnswer(ignored -> now);
        when(clock.millis()).thenAnswer(ignored -> now.toEpochMilli());
        when(clock.withZone(any(ZoneId.class))).thenAnswer(invocation -> Clock.fixed(now, invocation.getArgument(0)));
        when(settings.getInt(SettingKey.CONTEST_SUBMIT_GRACE_SECONDS)).thenReturn(5);
        fixture = new Fixture(jdbc);
        learner = fixture.user();
        exam = fixture.exam("DRAFT");
    }

    @AfterEach
    void cleanup() {
        fixture.close();
    }

    @Test
    void AT04_draftExamUsesOnlyUsableQuestionsInExamOrderAndRemainsPrivate() throws Exception {
        fixture.question(exam, "Câu sau", 1, 1, "APPROVED", "MULTIPLE_CHOICE", 99);
        fixture.question(exam, "Chờ duyệt", 0, 1, "PENDING_REVIEW", "MULTIPLE_CHOICE", 1);
        fixture.question(exam, "Đúng sai", 0, 3, "APPROVED", "TRUE_FALSE", 3);
        fixture.question(exam, "Câu đầu", 0, 2, "APPROVED", "MULTIPLE_CHOICE", 7);
        long audio = fixture.question(exam, "Có âm thanh", 0, 4, "APPROVED", "MULTIPLE_CHOICE", 1);
        jdbc.update("UPDATE learning.questions SET audio_url='https://example.test/fixture.mp3' WHERE id=?", audio);
        long malformed = fixture.question(exam, "Sai mã lựa chọn đúng sai", 0, 5, "APPROVED", "TRUE_FALSE", 1);
        jdbc.update("UPDATE learning.questions SET options=replace(options::text,'\"B\"','\"C\"')::jsonb WHERE id=?", malformed);
        fixture.question(exam, "Tự luận", 0, 6, "APPROVED", "ESSAY", 1);

        var session = contests.start(learner, exam, exam, NOW.plusSeconds(60));
        assertThat(session.resumed()).isFalse();
        assertThat(session.items()).extracting(item -> item.prompt().text())
                .containsExactly("Câu đầu", "Đúng sai", "Câu sau");
        assertThat(session.items()).extracting(item -> item.no()).containsExactly(1, 2, 3);
        assertThat(mapper.writeValueAsString(session)).doesNotContain("correct", "answer", "accept", "explanation");
        mvc.perform(get("/api/exams").header("Authorization", bearer(learner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == " + exam + ")]").isEmpty());
        var summary = contests.exam(exam).orElseThrow();
        assertThat(summary.status()).isEqualTo("DRAFT");
        assertThat(summary.durationMinutes()).isEqualTo(15);
        assertThat(summary.questionCount()).isEqualTo(3);
        assertThat(contests.exam(Long.MAX_VALUE)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ARCHIVED", "MISSING", "EMPTY", "UNUSABLE"})
    void AT04_invalidExamsDoNotCreateAnAttempt(String state) {
        if (state.equals("ARCHIVED")) jdbc.update("UPDATE learning.exams SET status='ARCHIVED' WHERE id=?", exam);
        if (state.equals("UNUSABLE")) fixture.question(exam, "Nháp", 0, 1, "PENDING_REVIEW", "MULTIPLE_CHOICE", 1);
        long examId = state.equals("MISSING") ? Long.MAX_VALUE : exam;
        assertThatThrownBy(() -> contests.start(learner, exam, examId, NOW.plusSeconds(60)))
                .satisfies(error(ErrorCode.CONTEST_EXAM_INVALID, HttpStatus.UNPROCESSABLE_ENTITY));
        assertThat(contests.countAttempts(exam)).isZero();
    }

    @Test
    void AT02_resumeKeepsSnapshotDeadlineAndGraceEvenAfterExamAndSettingsChange() throws Exception {
        long question = fixture.question(exam, "Nội dung ban đầu", 0, 1, "APPROVED", "MULTIPLE_CHOICE", 1);
        Instant deadline = NOW.plusSeconds(60);
        ContestSession first = contests.start(learner, exam, exam, deadline);
        String snapshot = jdbc.queryForObject("SELECT served_items::text FROM learning.attempts WHERE id=?", String.class, first.sessionId());
        assertThat(expiry(first.sessionId())).isEqualTo(deadline.plusSeconds(5));
        JsonNode stored = mapper.readTree(snapshot);
        assertThat(stored.at("/meta/contest/submitGraceSeconds").asInt()).isEqualTo(5);
        assertThat(stored.at("/meta/contest/deadline").asText()).isEqualTo(deadline.toString());
        jdbc.update("UPDATE learning.exams SET status='ARCHIVED' WHERE id=?", exam);
        jdbc.update("UPDATE learning.questions SET stem='Nội dung mới',status='ARCHIVED' WHERE id=?", question);
        when(settings.getInt(SettingKey.CONTEST_SUBMIT_GRACE_SECONDS)).thenReturn(60);
        now = NOW.plusSeconds(10);
        ContestSession resumed = contests.start(learner, exam, Long.MAX_VALUE, deadline.plusSeconds(600));
        assertThat(resumed.sessionId()).isEqualTo(first.sessionId());
        assertThat(resumed.resumed()).isTrue();
        assertThat(resumed.deadline()).isEqualTo(deadline);
        assertThat(resumed.items()).isEqualTo(first.items());
        assertThat(expiry(first.sessionId())).isEqualTo(deadline.plusSeconds(5));
        assertThat(jdbc.queryForObject("SELECT served_items::text FROM learning.attempts WHERE id=?", String.class, first.sessionId()))
                .isEqualTo(snapshot);
        assertThat(contests.countAttempts(exam)).isEqualTo(1);

        long nextExam = fixture.exam("PUBLISHED");
        fixture.question(nextExam, "Lượt mới", 0, 1, "APPROVED", "MULTIPLE_CHOICE", 1);
        var next = contests.start(learner, nextExam, nextExam, deadline);
        assertThat(expiry(next.sessionId())).isEqualTo(deadline.plusSeconds(60));
    }

    @Test
    void FR002_absoluteExpiryDoesNotDriftWhenTheEngineReadsTheClockLater() {
        fixture.question(exam, "Câu thử", 0, 1, "APPROVED", "MULTIPLE_CHOICE", 1);
        when(clock.instant()).thenReturn(NOW, NOW.plusMillis(100));
        var session = contests.start(learner, exam, exam, NOW.plusSeconds(60));
        assertThat(expiry(session.sessionId())).isEqualTo(NOW.plusSeconds(65));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBMITTED", "EXPIRED", "ABANDONED", "TIMED_OUT"})
    void AT03_terminalOrTimedOutAttemptsCannotRestart(String state) {
        fixture.question(exam, "Câu thử", 0, 1, "APPROVED", "MULTIPLE_CHOICE", 1);
        var session = contests.start(learner, exam, exam, NOW.plusSeconds(60));
        if (state.equals("TIMED_OUT")) now = NOW.plusSeconds(66);
        else jdbc.update("UPDATE learning.attempts SET status=?,submitted_at=? WHERE id=?", state,
                state.equals("SUBMITTED") ? Timestamp.from(NOW) : null, session.sessionId());
        assertThatThrownBy(() -> contests.start(learner, exam, exam, NOW.plusSeconds(600)))
                .satisfies(error(ErrorCode.CONTEST_ALREADY_ATTEMPTED, HttpStatus.CONFLICT));
        assertThat(contests.countAttempts(exam)).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"0,0,true", "0,1,false", "5,4000,true", "5,5000,true", "5,5001,false", "5,6000,false", "60,60000,true"})
    void AT06_submitUsesSnapshottedGraceAndCommitsExpiration(int grace, int lateMillis, boolean accepted) {
        fixture.question(exam, "Câu thử", 0, 1, "APPROVED", "MULTIPLE_CHOICE", 25);
        when(settings.getInt(SettingKey.CONTEST_SUBMIT_GRACE_SECONDS)).thenReturn(grace);
        Instant deadline = NOW.plusSeconds(60);
        var session = contests.start(learner, exam, exam, deadline);
        now = deadline.plusMillis(lateMillis);
        if (accepted) {
            assertThat(contests.submit(learner, exam)).isEqualTo(now);
            assertThatThrownBy(() -> contests.submit(learner, exam))
                    .satisfies(error(ErrorCode.CONTEST_ALREADY_ATTEMPTED, HttpStatus.CONFLICT));
            assertThat(statusOf(session.sessionId())).isEqualTo("SUBMITTED");
        } else {
            assertThatThrownBy(() -> contests.submit(learner, exam))
                    .satisfies(error(ErrorCode.CONTEST_NOT_OPEN, HttpStatus.UNPROCESSABLE_ENTITY));
            assertThat(statusOf(session.sessionId())).isEqualTo("EXPIRED");
            assertThatThrownBy(() -> contests.submit(learner, exam))
                    .satisfies(error(ErrorCode.CONTEST_NOT_OPEN, HttpStatus.UNPROCESSABLE_ENTITY));
        }
    }

    @Test
    void FR004_submitReturnsOnlyTimestampAndScoresCorrectCountFromOriginalDraftAnswers() throws Exception {
        fixture.question(exam, "Câu một", 0, 1, "APPROVED", "MULTIPLE_CHOICE", 99);
        fixture.question(exam, "Câu hai", 0, 2, "APPROVED", "MULTIPLE_CHOICE", 7);
        var session = contests.start(learner, exam, exam, NOW.plusSeconds(60));
        mvc.perform(post("/api/practice/sessions/{id}/answers", session.sessionId())
                        .header("Authorization", bearer(learner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"no\":1,\"answer\":\"A\",\"durationMs\":3000}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.correct").doesNotExist()).andExpect(jsonPath("$.correctAnswer").doesNotExist());
        jdbc.update("UPDATE learning.questions SET correct_key='B',options=replace(options::text,'true','false')::jsonb WHERE id IN "
                + "(SELECT question_id FROM learning.exam_questions WHERE exam_id=?)", exam);
        now = NOW.plusSeconds(30);
        Instant submitted = contests.submit(learner, exam);
        assertThat(submitted).isEqualTo(now);
        assertThat(mapper.writeValueAsString(submitted)).isEqualTo("\"" + now + "\"");
        assertThat(jdbc.queryForMap("SELECT score,max_score,duration_ms FROM learning.attempts WHERE id=?", session.sessionId()))
                .containsEntry("score", 1L).containsEntry("max_score", 2L).containsEntry("duration_ms", 30_000);
        mvc.perform(get("/api/practice/sessions/{id}", session.sessionId()).header("Authorization", bearer(learner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.answered[0].correct").doesNotExist())
                .andExpect(jsonPath("$.answered[0].correctAnswer").doesNotExist())
                .andExpect(jsonPath("$.items[0].answer").doesNotExist())
                .andExpect(jsonPath("$.items[0].explanation").doesNotExist());
    }

    @Test
    void FR003_countsAllContestStatusesButNoOtherContestOrPracticeKind() {
        fixture.question(exam, "Câu thử", 0, 1, "APPROVED", "MULTIPLE_CHOICE", 1);
        for (String state : List.of("IN_PROGRESS", "SUBMITTED", "ABANDONED", "EXPIRED")) {
            long user = fixture.user();
            var session = contests.start(user, exam, exam, NOW.plusSeconds(60));
            jdbc.update("UPDATE learning.attempts SET status=?,submitted_at=? WHERE id=?", state,
                    state.equals("SUBMITTED") ? Timestamp.from(NOW) : null, session.sessionId());
        }
        contests.start(learner, exam + 1, exam, NOW.plusSeconds(60));
        jdbc.update("INSERT INTO learning.attempts(user_id,kind,ref_type,ref_id,status) VALUES (?,'EXAM','MOCK_EXAM',?,'ABANDONED')", learner, exam);
        assertThat(contests.countAttempts(exam)).isEqualTo(4);
        assertThat(contests.countAttempts(Long.MAX_VALUE)).isZero();
    }

    @Test
    void FR004_submitIsScopedToTheCallerAndMapsMissingAndAbandonedAttempts() {
        assertThatThrownBy(() -> contests.submit(learner, exam)).satisfies(error(ErrorCode.CONTEST_NOT_FOUND, HttpStatus.NOT_FOUND));
        fixture.question(exam, "Câu thử", 0, 1, "APPROVED", "MULTIPLE_CHOICE", 1);
        var session = contests.start(learner, exam, exam, NOW.plusSeconds(60));
        assertThatThrownBy(() -> contests.submit(fixture.user(), exam)).satisfies(error(ErrorCode.CONTEST_NOT_FOUND, HttpStatus.NOT_FOUND));
        jdbc.update("UPDATE learning.attempts SET status='ABANDONED' WHERE id=?", session.sessionId());
        assertThatThrownBy(() -> contests.submit(learner, exam)).satisfies(error(ErrorCode.CONTEST_ALREADY_ATTEMPTED, HttpStatus.CONFLICT));
    }

    @Test
    void FR002_expiredDeadlineDoesNotCreateALateAttempt() {
        fixture.question(exam, "Câu thử", 0, 1, "APPROVED", "MULTIPLE_CHOICE", 1);
        assertThatThrownBy(() -> contests.start(learner, exam, exam, NOW.minusSeconds(5)))
                .satisfies(error(ErrorCode.CONTEST_NOT_OPEN, HttpStatus.UNPROCESSABLE_ENTITY));
        assertThat(contests.countAttempts(exam)).isZero();
    }

    private Instant expiry(long id) {
        return jdbc.queryForObject("SELECT expires_at FROM learning.attempts WHERE id=?", (row, index) -> row.getTimestamp(1).toInstant(), id);
    }

    private String statusOf(long id) {
        return jdbc.queryForObject("SELECT status FROM learning.attempts WHERE id=?", String.class, id);
    }

    private String bearer(long userId) {
        return "Bearer " + jwt.issueAccessToken(userId, List.of("LEARNER"), true, UUID.randomUUID());
    }

    private static Consumer<Throwable> error(ErrorCode code, HttpStatus status) {
        return failure -> {
            assertThat(failure).isInstanceOf(ApiException.class);
            assertThat(((ApiException) failure).getCode()).isEqualTo(code);
            assertThat(((ApiException) failure).getStatus()).isEqualTo(status);
        };
    }

    /** Shared self-authored fixtures for the engine, concurrency and mastery integration tests. */
    public static final class Fixture implements AutoCloseable {
        private static final String HASH = new BCryptPasswordEncoder(12).encode("ContestFixture2026!");
        private static final String OPTIONS = "[{\"key\":\"A\",\"text\":\"nước\",\"is_correct\":true},"
                + "{\"key\":\"B\",\"text\":\"lửa\",\"is_correct\":false}]";
        private final JdbcTemplate jdbc;
        private final List<Long> users = new ArrayList<>();
        private final List<Long> exams = new ArrayList<>();
        private final List<Long> questions = new ArrayList<>();

        public Fixture(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        public long user() {
            long id = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name,email_verified_at) "
                    + "VALUES (?,?,'Thí sinh thử',now()) RETURNING id", Long.class, UUID.randomUUID() + "@example.test", HASH);
            users.add(id);
            return id;
        }

        public long exam(String status) {
            long id = jdbc.queryForObject("INSERT INTO learning.exams(code,title,hsk_level,status,sections) "
                    + "VALUES (?,'Đề cuộc thi tự soạn',1,?,?::jsonb) RETURNING id", Long.class,
                    "CONTEST-" + UUID.randomUUID(), status,
                    "[{\"index\":0,\"name\":\"Đọc\",\"duration_min\":10},{\"index\":1,\"name\":\"Đọc thêm\",\"duration_min\":5}]");
            exams.add(id);
            return id;
        }

        public long question(long exam, String stem, int section, int order, String status, String type, int points) {
            long id = jdbc.queryForObject("INSERT INTO learning.questions(code,qtype,hsk_level,stem,options,correct_key,explanation,status) "
                    + "VALUES (?,?,1,?,?::jsonb,'A','Giải thích tự soạn',?) RETURNING id", Long.class,
                    "CONTEST-" + UUID.randomUUID(), type, stem, OPTIONS, status);
            questions.add(id);
            jdbc.update("INSERT INTO learning.exam_questions(exam_id,question_id,section_index,order_index,points) VALUES (?,?,?,?,?)",
                    exam, id, section, order, points);
            return id;
        }

        @Override
        public void close() {
            for (long id : users) {
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
            for (long id : questions) {
                jdbc.update("DELETE FROM learning.question_knowledge_points WHERE question_id=?", id);
                jdbc.update("DELETE FROM learning.questions WHERE id=?", id);
            }
        }
    }
}
