package com.cnhsk.learning.mastery.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class SkillMasteryControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private ObjectMapper mapper;

    @Test
    void countsEveryNodeOnceForEachOfItsSkillsAndIgnoresNodesWithoutEvidenceInThePercentage() throws Exception {
        long learner = jdbc.queryForObject("INSERT INTO auth.users (email, password_hash, display_name) VALUES (?, ?, 'Kiểm thử') "
                + "RETURNING id", Long.class, "skills-" + UUID.randomUUID() + "@example.test",
                "$2a$12$fixtureTestHashForDatabaseOnly00000000000000000000000000");
        long tone = node("NA1-961", "NA", 1, "{LISTENING,SPEAKING}");
        long greeting = node("NL1-961", "NL", 1, "{SPEAKING}");
        node("CH1-961", "CH", 1, "{READING,WRITING}");
        node("NP3-961", "NP", 3, "{READING}");
        progress(learner, tone, "consolidate", "0.900");
        progress(learner, greeting, "review", "0.500");

        JsonNode body = mapper.readTree(mvc.perform(get("/api/mastery/skills").param("hskLevel", "1")
                        .header("Authorization", bearer(learner, "LEARNER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());

        assertThat(body.path("hskLevel").asInt()).isEqualTo(1);
        JsonNode listening = group(body.path("skills"), "LISTENING");
        JsonNode speaking = group(body.path("skills"), "SPEAKING");
        JsonNode writing = group(body.path("skills"), "WRITING");
        assertThat(body.path("skills")).extracting(node -> node.path("code").asText())
                .containsExactly("LISTENING", "SPEAKING", "READING", "WRITING");
        // Imported data of other tests may add nodes, so only this fixture's contribution is checked as lower bounds.
        assertThat(listening.path("good").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(speaking.path("nodes").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(speaking.path("percent").asInt()).isEqualTo(70);
        assertThat(writing.path("percent").isNull()).isTrue();
        assertThat(writing.path("missing").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(body.path("domains")).extracting(node -> node.path("code").asText())
                .startsWith("NA", "CH", "TV", "NP", "NL");
    }

    @Test
    void needsALearnerAndAValidLevel() throws Exception {
        mvc.perform(get("/api/mastery/skills")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/mastery/skills").header("Authorization", bearer(1, "CONTENT_EDITOR")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/mastery/skills").param("hskLevel", "0").header("Authorization", bearer(1, "LEARNER")))
                .andExpect(status().isUnprocessableEntity());
    }

    private long node(String code, String domain, int level, String skills) {
        return jdbc.queryForObject("INSERT INTO learning.knowledge_points (code, title, domain, name_zh, hsk_level, skills) "
                + "VALUES (?, 'Nút giả', ?, '字', ?, ?::varchar[]) RETURNING id", Long.class, code, domain, level, skills);
    }

    private void progress(long user, long node, String state, String mastery) {
        jdbc.update("INSERT INTO learning.user_progress (user_id, target_type, target_id, mastery, mp_state, confidence, "
                + "updated_at) VALUES (?, 'KNOWLEDGE_POINT', ?, ?::numeric, ?, 1, now())", user, node, mastery, state);
    }

    private static JsonNode group(JsonNode groups, String code) {
        for (JsonNode group : groups) if (group.path("code").asText().equals(code)) return group;
        throw new AssertionError("Missing group " + code);
    }

    private String bearer(long userId, String role) {
        return "Bearer " + jwt.issueAccessToken(userId, List.of(role), true, UUID.randomUUID());
    }
}
