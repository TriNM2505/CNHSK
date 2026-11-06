package com.cnhsk.learning.mastery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.cnhsk.learning.knowledge.KnowledgeTreeService;
import com.cnhsk.support.AbstractIntegrationTest;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(RecommendationServiceIntegrationTest.SqlConfiguration.class)
class RecommendationServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired RecommendationService recommendations;
    @Autowired KnowledgeTreeService tree;
    @Autowired MasteryQueryRepository queries;
    @Autowired JdbcTemplate jdbc;
    @Autowired SqlCapture sql;

    private Fixtures fixtures = new Fixtures();

    @AfterEach
    void cleanUp() {
        fixtures.cleanUp();
    }

    @Test
    void batchAvailabilityMatchesApprovedQuestionIdsAndSelectCountIsConstantForTenAndFiftyNodes() {
        long user = fixtures.user();
        List<String> tenKids = fixtures.nodesWithQuestions(10, 8);
        assertBatchMatchesKnowledgeTreeApi(tenKids);
        sql.clear();
        recommendations.recommendations(user);
        int tenNodeSelects = sql.selectCount();

        fixtures.cleanUp();
        fixtures = new Fixtures();
        user = fixtures.user();
        List<String> fiftyKids = fixtures.nodesWithQuestions(50, 40);
        assertBatchMatchesKnowledgeTreeApi(fiftyKids);
        sql.clear();
        recommendations.recommendations(user);
        int fiftyNodeSelects = sql.selectCount();

        assertThat(tenNodeSelects).isEqualTo(4);
        assertThat(fiftyNodeSelects).isEqualTo(tenNodeSelects);
    }

    private void assertBatchMatchesKnowledgeTreeApi(List<String> kids) {
        Set<String> batch = queries.kidsWithApprovedQuestions(Set.copyOf(kids));
        for (String kid : kids) {
            boolean viaBatch = batch.contains(kid);
            boolean viaApprovedQuestionIds = !tree.approvedQuestionIds(Set.of(kid), null, 200).isEmpty();
            assertThat(viaBatch).as("Approved question availability for %s", kid)
                    .isEqualTo(viaApprovedQuestionIds);
        }
    }

    private final class Fixtures {
        private final List<Long> userIds = new ArrayList<>();
        private final List<Long> nodeIds = new ArrayList<>();
        private final List<Long> questionIds = new ArrayList<>();

        private long user() {
            long id = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) "
                            + "VALUES (?,?,'Recommendation fixture') RETURNING id", Long.class,
                    "t10-rec-" + UUID.randomUUID() + "@example.test",
                    "$2a$12$fixtureTestHashForDatabaseOnly00000000000000000000000000");
            userIds.add(id);
            return id;
        }

        private List<String> nodesWithQuestions(int nodeCount, int approvedQuestionCount) {
            List<String> kids = new ArrayList<>();
            for (int i = 0; i < nodeCount; i++) {
                int level = i % 3 + 1;
                int sequence = 900 + i;
                String kid = "VB" + level + "-" + sequence;
                long nodeId = jdbc.queryForObject("INSERT INTO learning.knowledge_points "
                                + "(code,title,domain,name_zh,hsk_level) VALUES (?,'Nút thử','VB','字',?) RETURNING id",
                        Long.class, kid, level);
                nodeIds.add(nodeId);
                kids.add(kid);
                if (i < approvedQuestionCount) {
                    long questionId = jdbc.queryForObject("INSERT INTO learning.questions "
                                    + "(qtype,hsk_level,stem,options,correct_key,status,code) "
                                    + "VALUES ('MULTIPLE_CHOICE',1,'Câu tự soạn',"
                                    + "'[{\"key\":\"A\",\"text\":\"Đúng\",\"is_correct\":true},"
                                    + "{\"key\":\"B\",\"text\":\"Sai\",\"is_correct\":false}]'::jsonb,"
                                    + "'A','APPROVED',?) RETURNING id", Long.class, "T10-" + UUID.randomUUID());
                    questionIds.add(questionId);
                    jdbc.update("INSERT INTO learning.question_knowledge_points(question_id,knowledge_point_id) "
                            + "VALUES (?,?)", questionId, nodeId);
                }
            }
            return List.copyOf(kids);
        }

        private void cleanUp() {
            for (long questionId : questionIds) {
                jdbc.update("DELETE FROM learning.question_knowledge_points WHERE question_id=?", questionId);
                jdbc.update("DELETE FROM learning.questions WHERE id=?", questionId);
            }
            for (long nodeId : nodeIds) {
                jdbc.update("DELETE FROM learning.knowledge_points WHERE id=?", nodeId);
            }
            for (long userId : userIds) {
                jdbc.update("DELETE FROM auth.user_roles WHERE user_id=?", userId);
                jdbc.update("DELETE FROM auth.users WHERE id=?", userId);
            }
        }
    }

    @TestConfiguration
    static class SqlConfiguration {
        @Bean SqlCapture recommendationSqlCapture() { return new SqlCapture(); }
        @Bean HibernatePropertiesCustomizer recommendationStatementInspector(SqlCapture capture) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", capture);
        }
    }

    static class SqlCapture implements StatementInspector {
        private final List<String> statements = new java.util.concurrent.CopyOnWriteArrayList<>();
        @Override public String inspect(String statement) {
            statements.add(statement);
            return statement;
        }
        void clear() { statements.clear(); }
        int selectCount() {
            return (int) statements.stream()
                    .filter(statement -> statement.stripLeading().toLowerCase().startsWith("select"))
                    .count();
        }
    }
}
