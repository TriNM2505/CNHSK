package com.cnhsk.learning.mastery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler;
import com.cnhsk.learning.practice.PracticeSourceHandler.Answer;
import com.cnhsk.support.AbstractIntegrationTest;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(MasteryQueryServiceIntegrationTest.SqlConfiguration.class)
class MasteryQueryServiceIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");

    @Autowired MasteryQueryService service;
    @Autowired MasteryReader reader;
    @Autowired PracticeService practice;
    @Autowired JdbcTemplate jdbc;
    @Autowired SqlCapture sql;
    @MockitoBean Clock clock;
    @MockitoBean(name = "topicTestHandler") PracticeSourceHandler handler;
    private Fixtures fixtures;

    @BeforeEach
    void setUp() {
        cleanUpCompletedT07Fixtures();
        when(clock.instant()).thenReturn(NOW);
        when(handler.source()).thenReturn(PracticeSource.TOPIC_TEST);
        when(handler.onFinish(any())).thenReturn(Map.of());
        fixtures = new Fixtures(jdbc, practice);
    }

    @AfterEach
    void cleanUpFixtures() {
        fixtures.cleanUp();
    }

    private void cleanUpCompletedT07Fixtures() {
        List<Long> userIds = jdbc.queryForList("SELECT id FROM auth.users WHERE email LIKE 't07-%'", Long.class);
        List<Long> questionIds = jdbc.queryForList(
                "SELECT id FROM learning.questions WHERE code LIKE 'T07-%'", Long.class);
        List<Long> nodeIds = jdbc.queryForList("SELECT DISTINCT qkp.knowledge_point_id "
                + "FROM learning.question_knowledge_points qkp "
                + "JOIN learning.questions q ON q.id=qkp.question_id WHERE q.code LIKE 'T07-%'", Long.class);
        for (long userId : userIds) {
            jdbc.update("DELETE FROM learning.attempt_items WHERE attempt_id IN "
                    + "(SELECT id FROM learning.attempts WHERE user_id=?)", userId);
            jdbc.update("DELETE FROM learning.attempts WHERE user_id=?", userId);
            jdbc.update("DELETE FROM learning.study_events WHERE user_id=?", userId);
            jdbc.update("DELETE FROM learning.user_progress WHERE user_id=?", userId);
            jdbc.update("DELETE FROM auth.user_roles WHERE user_id=?", userId);
        }
        for (long questionId : questionIds) {
            jdbc.update("DELETE FROM learning.question_knowledge_points WHERE question_id=?", questionId);
            jdbc.update("DELETE FROM learning.questions WHERE id=?", questionId);
        }
        for (long nodeId : nodeIds) {
            jdbc.update("DELETE FROM learning.knowledge_points WHERE id=?", nodeId);
        }
        for (long userId : userIds) {
            jdbc.update("DELETE FROM auth.users WHERE id=?", userId);
        }
    }

    @Test
    void AT16_returnsTheCompleteTreeAndUsesAtMostThreeSelects() {
        long user = fixtures.user();
        List<Long> nodeIds = IntStream.range(0, 60).mapToObj(ignored -> fixtures.node()).toList();
        List<Long> questions = nodeIds.subList(0, 5).stream().map(fixtures::question).toList();
        finish(user, questions);

        sql.clear();
        var result = service.mastery(user);

        assertThat(result.nodes()).hasSize(60);
        assertThat(result.nodes().stream().filter(node -> "notyet".equals(node.state())))
                .hasSize(55).allSatisfy(node -> assertThat(node.value()).isNull());
        assertThat(sql.selectCount()).isLessThanOrEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.user_progress WHERE user_id=?", Long.class, user))
                .isEqualTo(5L);
    }

    @Test
    void AT17_detailReturnsCurrentWindowNewestFirstWithDecayedWeights() {
        long user = fixtures.user();
        long nodeId = fixtures.node();
        String kid = jdbc.queryForObject("SELECT code FROM learning.knowledge_points WHERE id=?", String.class, nodeId);
        finish(user, IntStream.range(0, 5).mapToObj(ignored -> fixtures.question(nodeId)).toList());
        List<Long> expectedIds = jdbc.queryForList("SELECT id FROM learning.attempt_items WHERE attempt_id=("
                + "SELECT max(id) FROM learning.attempts WHERE user_id=?) ORDER BY answered_at DESC,id DESC",
                Long.class, user);

        var detail = service.node(user, kid);

        assertThat(detail.evidence()).hasSize(5);
        assertThat(detail.evidence()).extracting(MasteryQueryService.EvidenceView::source)
                .containsOnly("TOPIC_TEST");
        assertThat(detail.evidence()).extracting(MasteryQueryService.EvidenceView::context)
                .containsOnly("QUIZ");
        assertThat(detail.evidence()).extracting(MasteryQueryService.EvidenceView::form)
                .containsOnly("BINARY");
        assertThat(detail.evidence()).extracting(MasteryQueryService.EvidenceView::role)
                .containsOnly("PRIMARY");
        assertThat(detail.evidence()).extracting(MasteryQueryService.EvidenceView::score)
                .containsOnly(100);
        assertThat(detail.approvedQuestionCount()).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.attempt_items WHERE attempt_id=("
                + "SELECT max(id) FROM learning.attempts WHERE user_id=?)", Long.class, user)).isEqualTo(5L);
        assertThat(expectedIds).hasSize(5);
        assertThat(detail.evidence()).isSortedAccordingTo(java.util.Comparator
                .comparing(MasteryQueryService.EvidenceView::answeredAt).reversed());
        assertThat(detail.evidence().get(0).weight()).isBetween(
                new java.math.BigDecimal("0.39"), new java.math.BigDecimal("0.4"));
    }

    @Test
    void AT18_readerReturnsEveryRequestedKidAndQueryCountDoesNotGrowWithKidCount() {
        long user = fixtures.user();
        long nodeId = fixtures.node();
        String existing = jdbc.queryForObject("SELECT code FROM learning.knowledge_points WHERE id=?", String.class, nodeId);
        finish(user, List.of(fixtures.question(nodeId)));
        Set<String> requested = Set.of(existing, "NP3-999", "VB3-999");

        sql.clear();
        Map<String, MasteryReader.MasteryView> values = reader.valuesOf(user, requested);
        int threeKidQueries = sql.selectCount();
        sql.clear();
        reader.valuesOf(user, Set.of(existing));
        int oneKidQueries = sql.selectCount();

        assertThat(values).containsOnlyKeys(existing, "NP3-999", "VB3-999");
        assertThat(values.get(existing).value()).isEqualTo(100);
        assertThat(values.get("NP3-999")).isEqualTo(new MasteryReader.MasteryView(null, "notyet"));
        assertThat(values.get("VB3-999")).isEqualTo(new MasteryReader.MasteryView(null, "notyet"));
        assertThat(threeKidQueries).isLessThanOrEqualTo(3).isEqualTo(oneKidQueries);
        sql.clear();
        assertThat(reader.valuesOf(user, Set.of())).isEmpty();
        assertThat(sql.selectCount()).isZero();
    }

    private void finish(long userId, List<Long> questionIds) {
        long session = fixtures.session(userId, questionIds);
        practice.finish(userId, session,
                IntStream.rangeClosed(1, questionIds.size()).mapToObj(Fixtures::answer).toList());
    }

    private static class Fixtures {
        private static final java.util.concurrent.atomic.AtomicInteger CODES = new java.util.concurrent.atomic.AtomicInteger(500);
        private final JdbcTemplate jdbc;
        private final PracticeService practice;
        private final List<Long> userIds = new ArrayList<>();
        private final List<Long> nodeIds = new ArrayList<>();
        private final List<Long> questionIds = new ArrayList<>();
        private final List<Long> sessionIds = new ArrayList<>();
        private Fixtures(JdbcTemplate jdbc, PracticeService practice) {
            this.jdbc = jdbc;
            this.practice = practice;
        }
        private long user() {
            long id = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,'Query fixture') RETURNING id",
                    Long.class, "t09-" + UUID.randomUUID() + "@example.test",
                    "$2a$12$fixtureTestHashForDatabaseOnly00000000000000000000000000");
            userIds.add(id);
            return id;
        }
        private long node() {
            long id = jdbc.queryForObject("INSERT INTO learning.knowledge_points(code,title,domain,name_zh,hsk_level) "
                    + "VALUES (?,'Nút giả','VB','字',(3)) RETURNING id", Long.class,
                    "VB3-" + String.format("%03d", CODES.incrementAndGet()));
            nodeIds.add(id);
            return id;
        }
        private long question(long nodeId) {
            long id = jdbc.queryForObject("INSERT INTO learning.questions(qtype,hsk_level,stem,options,correct_key,status,code) "
                    + "VALUES ('MULTIPLE_CHOICE',1,'Câu giả','[{\"key\":\"A\",\"text\":\"Đúng\",\"is_correct\":true},"
                    + "{\"key\":\"B\",\"text\":\"Sai\",\"is_correct\":false}]'::jsonb,'A','APPROVED',?) RETURNING id",
                    Long.class, "T09-" + UUID.randomUUID());
            jdbc.update("INSERT INTO learning.question_knowledge_points(question_id,knowledge_point_id) VALUES (?,?)",
                    id, nodeId);
            questionIds.add(id);
            return id;
        }
        private long session(long userId, List<Long> questions) {
            List<com.cnhsk.learning.practice.ServedItem> served = IntStream.range(0, questions.size()).mapToObj(i ->
                    new com.cnhsk.learning.practice.ServedItem(i + 1,
                            com.cnhsk.learning.practice.ItemType.QUESTION_CHOICE,
                            com.cnhsk.learning.practice.Skill.READING, null,
                            new com.cnhsk.learning.practice.ServedItem.Prompt("Câu giả", null, null, null,
                                    null, null, null, null, null),
                            List.of(new com.cnhsk.learning.practice.ServedItem.Option("A", "Đúng"),
                                    new com.cnhsk.learning.practice.ServedItem.Option("B", "Sai")),
                            "A", null, null, new com.cnhsk.learning.practice.ServedItem.Rules(0, 0, false),
                            questions.get(i))).toList();
            long sessionId = practice.start(new PracticeService.StartPracticeCommand(userId, PracticeSource.TOPIC_TEST,
                    UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE, null, served, List.of(), null, null))
                    .sessionId();
            sessionIds.add(sessionId);
            return sessionId;
        }
        private static Answer answer(int no) {
            return new Answer(no, "A", 2000, 0, false, false, null);
        }

        private void cleanUp() {
            for (long sessionId : sessionIds) {
                jdbc.update("DELETE FROM learning.attempt_items WHERE attempt_id=?", sessionId);
                jdbc.update("DELETE FROM learning.attempts WHERE id=?", sessionId);
            }
            for (long userId : userIds) {
                jdbc.update("DELETE FROM learning.study_events WHERE user_id=?", userId);
                jdbc.update("DELETE FROM learning.user_progress WHERE user_id=?", userId);
                jdbc.update("DELETE FROM auth.user_roles WHERE user_id=?", userId);
            }
            for (long questionId : questionIds) {
                jdbc.update("DELETE FROM learning.question_knowledge_points WHERE question_id=?", questionId);
                jdbc.update("DELETE FROM learning.questions WHERE id=?", questionId);
            }
            for (long nodeId : nodeIds) {
                jdbc.update("DELETE FROM learning.knowledge_points WHERE id=?", nodeId);
            }
            for (long userId : userIds) {
                jdbc.update("DELETE FROM auth.users WHERE id=?", userId);
            }
        }
    }

    @TestConfiguration
    static class SqlConfiguration {
        @Bean SqlCapture masteryQuerySqlCapture() { return new SqlCapture(); }
        @Bean HibernatePropertiesCustomizer masteryQueryInspector(SqlCapture capture) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", capture);
        }
    }

    static class SqlCapture implements StatementInspector {
        private final List<String> statements = new CopyOnWriteArrayList<>();
        @Override public String inspect(String sql) {
            statements.add(sql);
            return sql;
        }
        void clear() { statements.clear(); }
        int selectCount() {
            return (int) statements.stream().filter(statement -> statement.stripLeading().toLowerCase().startsWith("select")).count();
        }
    }
}
