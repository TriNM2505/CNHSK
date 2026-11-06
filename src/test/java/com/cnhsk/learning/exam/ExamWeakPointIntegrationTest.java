package com.cnhsk.learning.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.UUID;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class ExamWeakPointIntegrationTest extends AbstractIntegrationTest {
    private static final String PATH = "/api/exam-attempts/{id}/weak-points";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntityManagerFactory entities;
    @Autowired private ExamWeakPointService weakPoints;

    @Test
    void FR014_HTTPChecksRoleOwnershipSourceAndSubmission() throws Exception {
        long owner = user();
        long other = user();
        long active = attempt(owner, "MOCK_EXAM", false, List.of());
        long topic = attempt(owner, "TOPIC_TEST", true, List.of());

        mvc.perform(get(PATH, active)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH, active).header("Authorization", bearer(owner, "TEACHER")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        denied(other, active, 403, "ATTEMPT_NOT_OWNED");
        denied(owner, "invalid", 403, "ATTEMPT_NOT_OWNED");
        denied(owner, Long.MAX_VALUE, 403, "ATTEMPT_NOT_OWNED");
        denied(owner, topic, 403, "ATTEMPT_NOT_OWNED");
        denied(owner, active, 409, "ATTEMPT_NOT_SUBMITTED");
        mvc.perform(get(PATH, attempt(owner, "MOCK_EXAM", true, List.of()))
                        .header("Authorization", bearer(owner, "LEARNER")))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void FR014_AT13_readsPrimaryLabelsOfServedQuestionsInConstantQueries() throws Exception {
        long learner = user();
        long primary = node("NP1-984", "Nút chính");
        long secondary = node("NP1-985", "Nút phụ");
        var questions = new java.util.ArrayList<Long>();
        for (int index = 0; index < 8; index++) {
            long id = question();
            questions.add(id);
            jdbc.update("""
                    INSERT INTO learning.question_knowledge_points(question_id,knowledge_point_id,role)
                    VALUES (?,?,'PRIMARY'), (?,?,'SECONDARY')
                    """, id, primary, id, secondary);
        }
        long small = attempt(learner, "MOCK_EXAM", true, questions.subList(0, 1));
        long large = attempt(learner, "MOCK_EXAM", true, questions);

        var stats = entities.unwrap(SessionFactory.class).getStatistics();
        boolean enabled = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        try {
            long before = stats.getPrepareStatementCount();
            var first = weakPoints.weakPoints(learner, small);
            long smallQueries = stats.getPrepareStatementCount() - before;
            before = stats.getPrepareStatementCount();
            var second = weakPoints.weakPoints(learner, large);
            assertThat(stats.getPrepareStatementCount() - before).isEqualTo(smallQueries);
            assertThat(smallQueries).isBetween(1L, 6L);
            assertThat(second).isEqualTo(first).containsExactly(
                    new ExamWeakPointService.WeakPoint("NP1-984", "Nút chính", 0, "notyet"));
        } finally {
            stats.setStatisticsEnabled(enabled);
        }
        mvc.perform(get(PATH, large).header("Authorization", bearer(learner, "LEARNER")))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"kid":"NP1-984","nameVi":"Nút chính","mastery":0,"state":"notyet"}]
                        """, true));
    }

    private void denied(long user, Object attempt, int statusCode, String code) throws Exception {
        mvc.perform(get(PATH, attempt).header("Authorization", bearer(user, "LEARNER")))
                .andExpect(status().is(statusCode)).andExpect(jsonPath("$.error_code").value(code));
    }

    private String bearer(long user, String role) {
        return "Bearer " + jwt.issueAccessToken(user, List.of(role), true, UUID.randomUUID());
    }

    private long user() {
        return jdbc.queryForObject("""
                INSERT INTO auth.users(email,password_hash,display_name)
                VALUES (?,'fixture-unused','Exam weak-point fixture') RETURNING id
                """, Long.class, UUID.randomUUID() + "@example.test");
    }

    private long attempt(long user, String source, boolean submitted, List<Long> questions) {
        var items = mapper.createArrayNode();
        for (int i = 0; i < questions.size(); i++) {
            var item = items.addObject().put("questionId", questions.get(i)).put("no", i + 1)
                    .put("type", "QUESTION_CHOICE").put("skill", "VOCAB");
            item.putObject("prompt").put("text", "Câu tự soạn");
            item.putObject("rules");
        }
        var snapshot = mapper.createObjectNode();
        snapshot.putObject("meta");
        snapshot.set("items", items);
        return jdbc.queryForObject("""
                INSERT INTO learning.attempts(user_id,kind,ref_type,status,submitted_at,served_items)
                VALUES (?,'EXAM',?,?,CASE WHEN ? THEN now() END,?::jsonb) RETURNING id
                """, Long.class, user, source, submitted ? "SUBMITTED" : "IN_PROGRESS", submitted, snapshot.toString());
    }

    private long node(String kid, String title) {
        return jdbc.queryForObject("""
                INSERT INTO learning.knowledge_points(code,title,domain,name_zh,hsk_level)
                VALUES (?,?,'NP','样例',1) RETURNING id
                """, Long.class, kid, title);
    }

    private long question() {
        return jdbc.queryForObject("""
                INSERT INTO learning.questions(code,qtype,hsk_level,stem,options,correct_key,status)
                VALUES (?,'MULTIPLE_CHOICE',1,'Câu tự soạn',
                    '[{"key":"A","text":"Một","is_correct":true},{"key":"B","text":"Hai","is_correct":false}]'::jsonb,
                    'A','APPROVED') RETURNING id
                """, Long.class, "TEST-" + UUID.randomUUID());
    }
}
