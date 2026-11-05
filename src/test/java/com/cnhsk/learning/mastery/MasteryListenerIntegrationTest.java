package com.cnhsk.learning.mastery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import com.cnhsk.learning.practice.*;
import com.cnhsk.learning.practice.PracticeSourceHandler.Answer;
import com.cnhsk.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class MasteryListenerIntegrationTest extends AbstractIntegrationTest {
    static final Instant NOW = Instant.parse("2026-11-05T08:00:00Z");
    @Autowired PracticeService practice;
    @Autowired EntityManagerFactory factory;
    @MockitoSpyBean JdbcTemplate jdbc;
    @MockitoBean Clock clock;
    @MockitoBean(name = "topicTestHandler") PracticeSourceHandler handler;
    Fixtures fixtures;

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(NOW);
        when(handler.source()).thenReturn(PracticeSource.TOPIC_TEST);
        when(handler.onFinish(any())).thenReturn(Map.of());
        fixtures = new Fixtures(jdbc, practice);
    }

    @Test void AT04_finishCommitsMasteryInsideTheEngineTransaction() {
        long user = fixtures.user();
        long kid = fixtures.node();
        long session = fixtures.session(user, List.of(fixtures.question(kid)));
        practice.finish(user, session, List.of(Fixtures.answer(1)));
        var progress = jdbc.queryForMap("SELECT * FROM learning.user_progress WHERE user_id=? AND target_type='KNOWLEDGE_POINT'", user);
        assertThat(progress.get("mp_state")).isEqualTo("insufficient");
        assertThat((java.math.BigDecimal) progress.get("mastery")).isEqualByComparingTo("1.0");
        assertThat((java.math.BigDecimal) progress.get("confidence")).isEqualByComparingTo("0.4");
        assertThat(progress.get("updated_at").toString()).contains("2026-11-05");
    }

    @Test void E8_failureOnSecondKidRollsBackFirstKidItemsStudyEventAndSubmission() {
        long user = fixtures.user();
        long session = fixtures.session(user, List.of(fixtures.question(fixtures.node()), fixtures.question(fixtures.node())));
        AtomicInteger updates = new AtomicInteger();
        doAnswer(call -> {
            if (updates.incrementAndGet() == 2) throw new IllegalStateException("Injected second KID write failure");
            return call.callRealMethod();
        }).when(jdbc).update(any(PreparedStatementCreator.class));
        assertThatThrownBy(() -> practice.finish(user, session, List.of(Fixtures.answer(1), Fixtures.answer(2))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("second KID");
        assertThat(updates.get()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM learning.attempts WHERE id=?", String.class, session)).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.attempt_items WHERE attempt_id=?", Long.class, session)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.study_events WHERE user_id=?", Long.class, user)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.user_progress WHERE user_id=?", Long.class, user)).isZero();
    }

    @Test void AT04_expiredSessionDoesNotAllocateEvidence() {
        long user = fixtures.user();
        long session = fixtures.session(user, List.of(fixtures.question(fixtures.node())));
        jdbc.update("UPDATE learning.attempts SET expires_at=? WHERE id=?", java.sql.Timestamp.from(NOW.minusSeconds(1)), session);
        assertThatThrownBy(() -> practice.finish(user, session, List.of())).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.user_progress WHERE user_id=?", Long.class, user)).isZero();
    }

    @Test void NFR_P01_tenAndFiftyItemsHaveTheSameReadQueryCount() {
        long ten = measuredFinish(10);
        long fifty = measuredFinish(50);
        assertThat(ten).isPositive();
        assertThat(fifty).isEqualTo(ten);
        System.out.println("T07 NFR-P01 reads: 10 items=" + ten + ", 50 items=" + fifty);
    }

    private long measuredFinish(int count) {
        long user = fixtures.user();
        long kid = fixtures.node();
        long session = fixtures.session(user, IntStream.range(0, count).mapToObj(i -> fixtures.question(kid)).toList());
        var statistics = factory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        clearInvocations(jdbc);
        practice.finish(user, session, IntStream.rangeClosed(1, count).mapToObj(Fixtures::answer).toList());
        long reads = statistics.getQueryExecutionCount() + mockingDetails(jdbc).getInvocations().stream()
                .filter(call -> call.getMethod().getName().startsWith("query")).count();
        statistics.setStatisticsEnabled(false);
        return reads;
    }

    static class Fixtures {
        private static final AtomicInteger CODES = new AtomicInteger(800);
        private final JdbcTemplate jdbc;
        private final PracticeService practice;
        Fixtures(JdbcTemplate jdbc, PracticeService practice) { this.jdbc = jdbc; this.practice = practice; }
        long user() {
            return jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,'Fixture') RETURNING id",
                    Long.class, "t07-" + UUID.randomUUID() + "@example.test", "$2a$12$fixtureTestHashForDatabaseOnly00000000000000000000000000");
        }
        long node() {
            return jdbc.queryForObject("INSERT INTO learning.knowledge_points(code,title,domain,name_zh,hsk_level) "
                    + "VALUES (?,'Nút giả','VB','字',3) RETURNING id", Long.class, "VB3-" + CODES.incrementAndGet());
        }
        long question(long kid) {
            long id = jdbc.queryForObject("INSERT INTO learning.questions(qtype,hsk_level,stem,options,correct_key,status,code) "
                    + "VALUES ('MULTIPLE_CHOICE',1,'Câu giả','[{\"key\":\"A\",\"text\":\"Đúng\",\"is_correct\":true},{\"key\":\"B\",\"text\":\"Sai\",\"is_correct\":false}]'::jsonb,'A','APPROVED',?) RETURNING id",
                    Long.class, "T07-" + UUID.randomUUID());
            jdbc.update("INSERT INTO learning.question_knowledge_points(question_id,knowledge_point_id) VALUES (?,?)", id, kid);
            return id;
        }
        long session(long user, List<Long> questions) {
            List<ServedItem> served = IntStream.range(0, questions.size()).mapToObj(i -> new ServedItem(i + 1,
                    ItemType.QUESTION_CHOICE, Skill.READING, null,
                    new ServedItem.Prompt("Câu giả",null,null,null,null,null,null,null,null),
                    List.of(new ServedItem.Option("A","Đúng"),new ServedItem.Option("B","Sai")),
                    "A",null,null,new ServedItem.Rules(0,0,false),questions.get(i))).toList();
            return practice.start(new PracticeService.StartPracticeCommand(user,PracticeSource.TOPIC_TEST,
                    UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE,null,served,List.of(),null,null)).sessionId();
        }
        static Answer answer(int no) { return new Answer(no,"A",2000,0,false,false,null); }
    }
}
