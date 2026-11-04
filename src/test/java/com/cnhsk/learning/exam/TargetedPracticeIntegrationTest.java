package com.cnhsk.learning.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TargetedPracticeIntegrationTest extends AbstractIntegrationTest {
    private static final String PATH = "/api/targeted-practice/sessions";
    private static final String KID = "NP1-991";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private EntityManagerFactory entities;
    @Autowired private TargetedPracticeService practice;
    private final List<Long> nodeIds = new ArrayList<>();
    private long learner;

    @BeforeAll
    void user() throws Exception {
        String password = "Aa1" + UUID.randomUUID();
        learner = json(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.test",
                        "password", password, "confirmPassword", password, "displayName", "Targeted practice test",
                        "acceptTerms", true)))).andExpect(status().isCreated()).andReturn()).path("userId").asLong();
    }

    @AfterEach
    void removeNodes() {
        for (long id : nodeIds) {
            jdbc.update("DELETE FROM learning.question_knowledge_points WHERE knowledge_point_id=?", id);
            jdbc.update("DELETE FROM learning.knowledge_points WHERE id=?", id);
        }
        nodeIds.clear();
    }

    @Test
    void FR015_prefersSevenTrueFalseThenFillsAndExcludesNonUsableQuestions() throws Exception {
        long node = node(KID);
        for (int i = 0; i < 7; i++) question(node, "TRUE_FALSE", "APPROVED", "PRIMARY", false);
        for (int i = 0; i < 7; i++) question(node, "MULTIPLE_CHOICE", "APPROVED", "PRIMARY", false);
        long excluded = question(node, "MULTIPLE_CHOICE", "APPROVED", "PRIMARY", false);
        long pending = question(node, "TRUE_FALSE", "PENDING_REVIEW", "PRIMARY", false);
        long secondary = question(node, "TRUE_FALSE", "APPROVED", "SECONDARY", false);
        long withAudio = question(node, "TRUE_FALSE", "APPROVED", "PRIMARY", true);

        JsonNode session = start(Map.of("kid", KID, "qtype", "TRUE_FALSE", "excludeQuestionId", excluded), 201);
        assertThat(session.path("source").asText()).isEqualTo("TARGETED_PRACTICE");
        assertThat(session.path("feedback").asText()).isEqualTo("IMMEDIATE");
        assertThat(session.path("items").size()).isEqualTo(10);
        int trueFalse = 0;
        int number = 0;
        for (JsonNode item : session.path("items")) {
            assertThat(item.path("no").asInt()).isEqualTo(++number);
            if (item.path("options").get(0).path("text").asText().equals("Đúng")) trueFalse++;
            for (String field : List.of("answer", "accept", "explanation", "is_correct", "isCorrect", "correctKey")) {
                assertThat(item.findValue(field)).as(field).isNull();
            }
        }
        assertThat(trueFalse).isEqualTo(7);
        long id = session.path("sessionId").asLong();
        assertThat(servedIds(id)).doesNotContain(excluded, pending, secondary, withAudio);
        assertThat(session.path("meta").path("repeatedNos").isEmpty()).isTrue();
        long next = start(Map.of("kid", KID), 201).path("sessionId").asLong();
        assertThat(next).isNotEqualTo(id);
    }

    @Test
    void FR015_keepsExcludedOnlyWhenItIsTheSoleUsableQuestion() throws Exception {
        long node = node(KID);
        long only = question(node, "TRUE_FALSE", "APPROVED", "PRIMARY", false);
        JsonNode session = start(Map.of("kid", KID, "excludeQuestionId", only), 201);
        assertThat(session.path("meta").path("repeatedNos").toString()).isEqualTo("[1]");
        assertThat(servedIds(session.path("sessionId").asLong())).containsExactly(only);
        long other = question(node, "MULTIPLE_CHOICE", "APPROVED", "PRIMARY", false);
        JsonNode next = start(Map.of("kid", KID, "qtype", "TRUE_FALSE", "excludeQuestionId", only), 201);
        assertThat(next.path("meta").path("repeatedNos").isEmpty()).isTrue();
        assertThat(servedIds(next.path("sessionId").asLong())).containsExactly(other);
    }

    @Test
    void FR015_emptyBankAndValidationUseContractResponses() throws Exception {
        assertThat(start(Map.of("kid", KID), 404).path("error_code").asText()).isEqualTo("KNOWLEDGE_NODE_NOT_FOUND");
        node(KID);
        assertThat(start(Map.of("kid", KID), 200)).isEqualTo(mapper.readTree("""
                {"session":null,"reason":"NO_QUESTIONS"}
                """));
        for (String invalid : List.of("{}", "{", "{\"kid\":\"BAD\"}", "{\"kid\":\"NP1-991\",\"qtype\":\"ESSAY\"}")) {
            mvc.perform(post(PATH).header("Authorization", bearer("LEARNER"))
                    .contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    void FR015_requiresLearnerAndCookieCsrfAndOpensOnlyPost() throws Exception {
        mvc.perform(post(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).header("Authorization", bearer("CONTENT_EDITOR"))).andExpect(status().isForbidden());
        mvc.perform(post(PATH).cookie(new Cookie("access_token", token("LEARNER"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        mvc.perform(get(PATH).header("Authorization", bearer("LEARNER"))).andExpect(status().isForbidden());
    }

    @Test
    void FR015_queryCountDoesNotGrowWithTheBank() {
        long node = node(KID);
        question(node, "TRUE_FALSE", "APPROVED", "PRIMARY", false);
        long small = statementsForStart();
        for (int i = 0; i < 15; i++) question(node, "MULTIPLE_CHOICE", "APPROVED", "PRIMARY", false);
        assertThat(small).isPositive();
        assertThat(statementsForStart()).isEqualTo(small);
    }

    @Test
    void FR016_immediateFeedbackAndFinishUseSessionKid() throws Exception {
        long node = node(KID);
        question(node, "TRUE_FALSE", "APPROVED", "PRIMARY", false);
        long id = start(Map.of("kid", KID), 201).path("sessionId").asLong();
        mvc.perform(post("/api/practice/sessions/{id}/answers", id).header("Authorization", bearer("LEARNER"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"no":1,"answer":"B","durationMs":2000}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.correctAnswer").value("A"))
                .andExpect(jsonPath("$.explanation").value("Giải thích tự soạn"));
        mvc.perform(post("/api/practice/sessions/{id}/finish", id).header("Authorization", bearer("LEARNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.extra.kid").value(KID));
    }

    private long statementsForStart() {
        var stats = entities.unwrap(SessionFactory.class).getStatistics();
        boolean enabled = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        try {
            long before = stats.getPrepareStatementCount();
            assertThat(practice.start(learner, KID, null, null)).isNotNull();
            return stats.getPrepareStatementCount() - before;
        } finally {
            stats.setStatisticsEnabled(enabled);
        }
    }

    private List<Long> servedIds(long session) {
        return jdbc.queryForList("""
                SELECT (item->>'questionId')::bigint FROM learning.attempts a,
                jsonb_array_elements(a.served_items->'items') item WHERE a.id=?
                """, Long.class, session);
    }

    private long node(String kid) {
        long id = jdbc.queryForObject("""
                INSERT INTO learning.knowledge_points(code,title,domain,name_zh,hsk_level)
                VALUES (?, 'Nút tự soạn', 'NP', '样例', 1) RETURNING id
                """, Long.class, kid);
        nodeIds.add(id);
        return id;
    }

    private long question(long node, String type, String state, String role, boolean audio) {
        long id = jdbc.queryForObject("""
                INSERT INTO learning.questions(code,qtype,hsk_level,stem,options,correct_key,explanation,status,audio_url)
                VALUES (?, ?, 1, 'Câu tự soạn',
                    '[{"key":"A","text":"Một","is_correct":true},{"key":"B","text":"Hai","is_correct":false}]',
                    'A', 'Giải thích tự soạn', ?, ?) RETURNING id
                """, Long.class, "TEST-" + UUID.randomUUID(), type, state, audio ? "sample.mp3" : null);
        jdbc.update("INSERT INTO learning.question_knowledge_points(question_id,knowledge_point_id,role) VALUES (?,?,?)",
                id, node, role);
        return id;
    }

    private JsonNode start(Map<String, Object> body, int expected) throws Exception {
        return json(mvc.perform(post(PATH).header("Authorization", bearer("LEARNER"))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().is(expected)).andReturn());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String bearer(String role) {
        return "Bearer " + token(role);
    }

    private String token(String role) {
        return jwt.issueAccessToken(learner, List.of(role), false, UUID.randomUUID());
    }
}
