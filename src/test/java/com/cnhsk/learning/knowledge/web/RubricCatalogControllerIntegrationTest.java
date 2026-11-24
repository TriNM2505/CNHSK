package com.cnhsk.learning.knowledge.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class RubricCatalogControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void visitorsSeeOnlyActiveTasksOfTheChosenKindAndLevel() throws Exception {
        long nl = jdbc.queryForObject("INSERT INTO learning.knowledge_points (code, title, domain, name_zh, hsk_level) "
                + "VALUES ('NL2-951', 'Nút giả', 'NL', '说', 2) RETURNING id", Long.class);
        insertTask("NOI_TEST_A", "SPEAKING", 2, nl, true);
        insertTask("NOI_TEST_B", "SPEAKING", 2, nl, false);
        insertTask("VIET_TEST_A", "WRITING", 2, nl, true);

        mvc.perform(get("/api/public/learning/tasks").param("kind", "SPEAKING").param("hskLevel", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'NOI_TEST_A')].nlKid").value("NL2-951"))
                .andExpect(jsonPath("$[?(@.code == 'NOI_TEST_B')]").isEmpty())
                .andExpect(jsonPath("$[?(@.code == 'VIET_TEST_A')]").isEmpty());
        mvc.perform(get("/api/public/learning/tasks").param("kind", "READING").param("hskLevel", "2"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/public/learning/tasks").param("kind", "SPEAKING").param("hskLevel", "0"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void errorCodesNeedATeacher() throws Exception {
        mvc.perform(get("/api/teacher/error-codes")).andExpect(status().isUnauthorized());
    }

    private void insertTask(String code, String kind, int level, long nl, boolean active) {
        jdbc.update("INSERT INTO learning.learning_tasks (code, kind, hsk_level, nl_kid_id, title_vi, prompt_vi, "
                + "max_seconds, min_chars, is_active) VALUES (?, ?, ?, ?, 'Nhiệm vụ', 'Làm bài', ?, ?, ?)",
                code, kind, level, nl, "SPEAKING".equals(kind) ? 60 : null, "WRITING".equals(kind) ? 40 : null, active);
    }
}
