package com.cnhsk.learning.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.learning.knowledge.entity.KnowledgeDomain;
import com.cnhsk.learning.knowledge.entity.KnowledgeLabelRole;
import com.cnhsk.learning.knowledge.entity.KnowledgePoint;
import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.entity.QuestionKnowledgePoint;
import com.cnhsk.learning.knowledge.entity.QuestionOption;
import com.cnhsk.learning.knowledge.entity.QuestionStatus;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import com.cnhsk.learning.knowledge.repository.QuestionAdminQueryRepository.Filters;
import com.cnhsk.learning.knowledge.repository.QuestionKnowledgePointRepository;
import com.cnhsk.learning.knowledge.repository.QuestionRepository;
import com.cnhsk.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
@Transactional
@Import(QuestionAdminServiceIntegrationTest.SqlConfiguration.class)
class QuestionAdminServiceIntegrationTest extends AbstractIntegrationTest {
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    @Autowired private QuestionAdminService service;
    @Autowired private QuestionRepository questions;
    @Autowired private KnowledgePointRepository points;
    @Autowired private QuestionKnowledgePointRepository labels;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entities;
    @Autowired private SqlCapture capture;

    @Test
    void FR018_AT18_oneAndTenQuestionPagesBothUseExactlyFourSelects() {
        KnowledgePoint primary = node("NP1-901"); KnowledgePoint secondary = node("TV1-901");
        KnowledgePoint other = node("CH1-901");
        question("UNRELATED_BATCH", primary);
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            Question question = question("T11_BATCH_" + index, primary);
            labels.saveAndFlush(new QuestionKnowledgePoint(question.getId(), secondary.getId(), KnowledgeLabelRole.SECONDARY));
            labels.saveAndFlush(new QuestionKnowledgePoint(question.getId(), other.getId(), KnowledgeLabelRole.SECONDARY));
            ids.add(question.getId());
        }
        use(ids.get(11), "SUBMITTED", true);
        for (int size : List.of(1, 10)) {
            entities.flush(); entities.clear(); capture.start();
            try {
                var page = service.list(new Filters(null, null, null, null, "T11_BATCH_"), 0, size);
                assertThat(page.totalElements()).isEqualTo(12); assertThat(page.items()).hasSize(size);
                assertThat(page.items().get(0).question().getId()).isEqualTo(ids.get(11));
                assertThat(page.items().get(0).used()).isTrue();
                assertThat(page.items()).allSatisfy(view -> assertThat(view.labels()).hasSize(3));
                List<String> sql = capture.statements();
                assertThat(sql).hasSize(4).allSatisfy(statement -> assertThat(statement).startsWith("select"));
                assertThat(sql.stream().filter(statement -> statement.contains("question_knowledge_points"))).hasSize(1);
                assertThat(sql.stream().filter(statement -> statement.contains("attempt_items"))).hasSize(1);
                assertThat(sql).anySatisfy(statement -> assertThat(statement).contains("order by", " desc", "fetch first"));
            } finally { capture.stop(); }
        }
    }

    @Test
    void FR017_FR018_AT17_AT18_emptyAndPastEndPagesOnlyCountWithoutBatchQueries() {
        Question question = question("T11_EMPTY", node("NP1-901"));
        for (Filters filters : List.of(new Filters(null, null, null, "NP1-999", "T11_EMPTY"),
                new Filters(null, null, null, null, "T11_EMPTY"))) {
            entities.flush(); entities.clear(); capture.start();
            try {
                int page = filters.kid() == null ? Integer.MAX_VALUE : 0;
                var result = service.list(filters, page, 100);
                assertThat(result.items()).isEmpty();
                assertThat(result.totalElements()).isEqualTo(filters.kid() == null ? 1 : 0);
                assertThat(capture.statements()).hasSize(1).allSatisfy(sql -> assertThat(sql).startsWith("select").contains("count("));
            } finally { capture.stop(); }
        }
        assertThat(service.detail(question.getId()).labels()).singleElement().satisfies(label -> {
            assertThat(label.kid()).isEqualTo("NP1-901"); assertThat(label.nameVi()).isEqualTo("Nút tự soạn NP1-901");
        });
    }

    @ParameterizedTest
    @CsvSource({"IN_PROGRESS,NULL", "SUBMITTED,true", "ABANDONED,false", "EXPIRED,false"})
    void FR018_FR019_AT18_usedIncludesEveryAttemptStateAndCorrectness(String state, String correctness) {
        Question question = question("T11_USED", node("NP1-901"));
        use(question.getId(), state, correctness.equals("NULL") ? null : Boolean.valueOf(correctness));
        entities.clear();
        assertThat(service.detail(question.getId()).used()).isTrue();
        assertThat(service.list(new Filters(null, null, null, null, "T11_USED"), 0, 10).items()).singleElement()
                .satisfies(view -> assertThat(view.used()).isTrue());
    }

    @Test
    void FR018_AT18_examMembershipWithoutAnAttemptItemIsNotUsed() {
        Question question = question("T11_EXAM_ONLY", node("NP1-901"));
        User user = user();
        long exam = jdbc.queryForObject("INSERT INTO learning.exams(code,title,hsk_level,created_by) VALUES (?,'Đề tự soạn',1,?) RETURNING id",
                Long.class, "T11_EXAM_" + UUID.randomUUID(), user.getId());
        jdbc.update("INSERT INTO learning.exam_questions(exam_id,question_id) VALUES (?,?)", exam, question.getId());
        entities.clear(); assertThat(service.detail(question.getId()).used()).isFalse();
    }

    private KnowledgePoint node(String kid) {
        return points.saveAndFlush(new KnowledgePoint(kid, "Nút tự soạn " + kid, KnowledgeDomain.valueOf(kid.substring(0, 2)), "示例", (short) 1));
    }

    private Question question(String code, KnowledgePoint point) {
        Question question = new Question(code, QuestionType.MULTIPLE_CHOICE, (short) 1, "Nội dung câu tự soạn");
        question.setStatus(QuestionStatus.APPROVED);
        question.setOptions(List.of(new QuestionOption("A", "我", true, null, null), new QuestionOption("B", "你", false, null, null)));
        questions.saveAndFlush(question);
        labels.saveAndFlush(new QuestionKnowledgePoint(question.getId(), point.getId(), KnowledgeLabelRole.PRIMARY));
        return question;
    }

    private User user() {
        return users.saveAndFlush(new User("question-admin-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH, "Người kiểm thử kho câu"));
    }

    private void use(long questionId, String state, Boolean correct) {
        long attempt = jdbc.queryForObject("INSERT INTO learning.attempts(user_id,kind,status,submitted_at) VALUES (?,'PRACTICE',?,now()) RETURNING id",
                Long.class, user().getId(), state);
        jdbc.update("INSERT INTO learning.attempt_items(attempt_id,question_id,item_type,skill,order_index,is_correct) VALUES (?,?,'QUESTION_CHOICE','READING',1,?)",
                attempt, questionId, correct);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlConfiguration {
        @Bean SqlCapture questionAdminSqlCapture() { return new SqlCapture(); }
        @Bean HibernatePropertiesCustomizer questionAdminStatementInspector(SqlCapture capture) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", capture);
        }
    }

    static class SqlCapture implements StatementInspector {
        private final ThreadLocal<List<String>> active = new ThreadLocal<>();
        void start() { active.set(new ArrayList<>()); }
        List<String> statements() { return List.copyOf(active.get()); }
        void stop() { active.remove(); }
        @Override public String inspect(String sql) {
            if (active.get() != null) active.get().add(sql.toLowerCase(Locale.ROOT).strip());
            return sql;
        }
    }
}
