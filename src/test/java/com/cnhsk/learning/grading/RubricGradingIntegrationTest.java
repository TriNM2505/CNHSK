package com.cnhsk.learning.grading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.cnhsk.learning.grading.web.GradingResultWrite;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** A teacher's rubric grade becomes knowledge evidence of the learner (competency tree v2.0, appendix E.3). */
@Transactional
class RubricGradingIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(900);

    @Autowired private RubricGrading rubric;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    @Test
    void speakingTaskGradeFeedsTheTaskNodesAndTheConfirmedErrorNode() {
        long learner = user();
        String nl = node("NL", 3);
        String na = node("NA", 1);
        String tv = node("TV", 2);
        String np = node("NP", 2);
        long task = task("SPEAKING", nl, Map.of("C2", na, "C3", tv, "C4", np));
        errorCode("E-NP-97", np, 2);
        errorCode("E-NP-98", np, 1);

        ObjectNode payload = mapper.createObjectNode().put("mode", "SPEAKING").put("essay", "我去了商店。")
                .put("audioUrl", "https://media.example.test/a.webm").put("taskId", task);
        var graded = rubric.grade(payload, new GradingResultWrite(null, "Cần chú ý 了", List.of(), Rubric.R_NOI,
                Map.of("C1", 2, "C2", 2, "C3", 2, "C4", 2, "C5", 2), List.of("E-NP-97", "E-NP-98")));
        rubric.recordEvidence(learner, 41L, graded);

        assertThat(graded.score100()).isEqualTo(67);
        assertThat(graded.stored().path("rubric").asText()).isEqualTo("R_NOI");
        assertThat(graded.stored().path("errors")).hasSize(2);
        List<Map<String, Object>> items = jdbc.queryForList("SELECT item.skill, item.meta->>'role' AS role, "
                + "(item.meta->>'score')::int AS score FROM learning.attempt_items item "
                + "JOIN learning.attempts attempt ON attempt.id = item.attempt_id "
                + "WHERE attempt.user_id = ? AND attempt.ref_type = 'RUBRIC_TASK' ORDER BY item.order_index", learner);
        assertThat(items).extracting(row -> row.get("role"))
                .containsExactly("PRIMARY", "SECONDARY", "SECONDARY", "SECONDARY", "ERROR");
        assertThat(items).extracting(row -> row.get("skill")).containsOnly("SPEAKING");
        assertThat(items.get(4).get("score")).isEqualTo(0);
        List<String> evidenced = jdbc.queryForList("SELECT point.code FROM learning.user_progress progress "
                + "JOIN learning.knowledge_points point ON point.id = progress.target_id "
                + "WHERE progress.user_id = ? AND progress.target_type = 'KNOWLEDGE_POINT'", String.class, learner);
        assertThat(evidenced).containsExactlyInAnyOrder(nl, na, tv, np);
    }

    @Test
    void rubricMustMatchTheKindOfWorkAndErrorCodesMustExist() {
        ObjectNode essay = mapper.createObjectNode().put("essay", "我喜欢学习。");
        var speakingRubric = new GradingResultWrite(null, "x", List.of(), Rubric.R_NOI,
                Map.of("C1", 2, "C2", 2, "C3", 2, "C4", 2, "C5", 2), List.of());
        assertThatThrownBy(() -> rubric.grade(essay, speakingRubric)).isInstanceOf(ApiException.class);
        var unknownCode = new GradingResultWrite(null, "x", List.of(), Rubric.R_VIET,
                Map.of("C1", 4, "C2", 4, "C3", 4, "C4", 4), List.of("E-NP-99"));
        assertThatThrownBy(() -> rubric.grade(essay, unknownCode)).isInstanceOf(ApiException.class);
    }

    @Test
    void freeWritingWithoutATaskRecordsNoEvidenceButKeepsTheGrade() {
        long learner = user();
        var graded = rubric.grade(mapper.createObjectNode().put("essay", "我喜欢学习。"),
                new GradingResultWrite(null, "Tốt", List.of(), Rubric.R_VIET,
                        Map.of("C1", 5, "C2", 4, "C3", 4, "C4", 3), List.of()));
        rubric.recordEvidence(learner, 42L, graded);
        assertThat(graded.score100()).isEqualTo(80);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.attempts WHERE user_id = ?",
                Long.class, learner)).isZero();
    }

    private long user() {
        return jdbc.queryForObject("INSERT INTO auth.users (email, password_hash, display_name) VALUES (?, ?, 'Kiểm thử') "
                + "RETURNING id", Long.class, "rubric-" + UUID.randomUUID() + "@example.test",
                "$2a$12$fixtureTestHashForDatabaseOnly00000000000000000000000000");
    }

    private String node(String domain, int level) {
        String code = domain + level + "-" + SEQUENCE.incrementAndGet();
        jdbc.update("INSERT INTO learning.knowledge_points (code, title, domain, name_zh, hsk_level) "
                + "VALUES (?, 'Nút giả', ?, '字', ?)", code, domain, level);
        return code;
    }

    private long id(String kid) {
        return jdbc.queryForObject("SELECT id FROM learning.knowledge_points WHERE code = ?", Long.class, kid);
    }

    private long task(String kind, String nl, Map<String, String> targets) {
        long id = jdbc.queryForObject("INSERT INTO learning.learning_tasks (code, kind, hsk_level, nl_kid_id, title_vi, "
                + "prompt_vi, max_seconds, is_active) VALUES (?, ?, 3, ?, 'Nhiệm vụ giả', 'Kể lại', 90, true) RETURNING id",
                Long.class, "T-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT), kind, id(nl));
        targets.forEach((criterion, kid) -> jdbc.update("INSERT INTO learning.learning_task_targets "
                + "(task_id, kid_id, criterion) VALUES (?, ?, ?)", id, id(kid), criterion));
        return id;
    }

    private void errorCode(String code, String kid, int severity) {
        jdbc.update("INSERT INTO learning.error_codes (code, kid_id, severity, name_vi) VALUES (?, ?, ?, 'Lỗi giả')",
                code, id(kid), severity);
    }
}
