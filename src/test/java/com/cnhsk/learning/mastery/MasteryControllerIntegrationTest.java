package com.cnhsk.learning.mastery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler;
import com.cnhsk.learning.practice.PracticeSourceHandler.Answer;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
class MasteryControllerIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtService jwt;
    @Autowired PracticeService practice;
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;
    @MockitoBean Clock clock;
    @MockitoBean(name = "topicTestHandler") PracticeSourceHandler handler;
    private MasteryListenerIntegrationTest.Fixtures fixtures;
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> nodeIds = new ArrayList<>();
    private final List<Long> questionIds = new ArrayList<>();
    private final List<Long> sessionIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        userIds.clear();
        nodeIds.clear();
        questionIds.clear();
        sessionIds.clear();
        when(clock.instant()).thenReturn(NOW);
        when(handler.source()).thenReturn(PracticeSource.TOPIC_TEST);
        when(handler.onFinish(any())).thenReturn(Map.of());
        fixtures = new MasteryListenerIntegrationTest.Fixtures(jdbc, practice);
    }

    @AfterEach
    void cleanUpFixtures() {
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

    @Test
    void generatedOpenApiHasBothMasteryRoutesAndContractTypes() throws Exception {
        MockMvc openApiMvc = MockMvcBuilders.webAppContextSetup(context).build();
        MvcResult response = openApiMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        JsonNode openApi = mapper.readTree(response.getResponse().getContentAsByteArray());

        assertThat(openApi.path("paths").path("/api/mastery").has("get")).isTrue();
        assertThat(openApi.path("paths").path("/api/mastery/nodes/{kid}").has("get")).isTrue();
        assertThat(openApi.path("paths").path("/api/mastery").path("get").path("responses").has("401")).isTrue();
        assertThat(openApi.path("paths").path("/api/mastery/nodes/{kid}").path("get")
                .path("responses").has("404")).isTrue();
        assertThat(openApi.path("components").path("schemas").path("MasteryNode")
                .path("properties").path("evidenceCount").path("type").asText()).isEqualTo("integer");
        assertThat(openApi.path("components").path("schemas").path("MasteryNode")
                .path("properties").path("confidence").path("type").asText()).isEqualTo("number");
    }

    @Test
    void AT01_onlyLearnersCanReadOwnMasteryAndNodeDetail() throws Exception {
        mvc.perform(get("/api/mastery")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/mastery/nodes/NP3-999")).andExpect(status().isUnauthorized());

        long learner = user();
        long nodeId = node();
        String kid = jdbc.queryForObject("SELECT code FROM learning.knowledge_points WHERE id=?", String.class, nodeId);
        finish(learner, List.of(question(nodeId)));
        long otherLearner = user();

        mvc.perform(get("/api/mastery").header("Authorization", bearer(learner, "CONTENT_EDITOR")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        mvc.perform(get("/api/mastery/nodes/{kid}", kid)
                        .header("Authorization", bearer(learner, "CONTENT_EDITOR")))
                .andExpect(status().isForbidden());

        JsonNode own = response("/api/mastery", learner);
        assertThat(own.path("nodes")).hasSize(1);
        assertThat(own.path("nodes").get(0).path("kid").asText()).isEqualTo(kid);
        assertThat(own.path("nodes").get(0).path("value").asDouble()).isEqualTo(100.0);
        assertThat(own.path("nodes").get(0).path("confidence").asDouble()).isGreaterThan(0);

        JsonNode other = response("/api/mastery", otherLearner);
        assertThat(other.path("nodes").get(0).path("value").isNull()).isTrue();
        assertThat(other.path("nodes").get(0).path("state").asText()).isEqualTo("notyet");

        mvc.perform(get("/api/mastery/nodes/{kid}", "NP3-999")
                        .header("Authorization", bearer(learner, "LEARNER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("KNOWLEDGE_NODE_NOT_FOUND"));
        mvc.perform(get("/api/mastery/nodes/{kid}", kid)
                        .header("Authorization", bearer(learner, "LEARNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kid").value(kid))
                .andExpect(jsonPath("$.evidence[0].source").value("TOPIC_TEST"))
                .andExpect(jsonPath("$.evidence[0].weight").isNumber())
                .andExpect(jsonPath("$.approvedQuestionCount").value(1));
    }

    private JsonNode response(String path, long userId) throws Exception {
        return mapper.readTree(mvc.perform(get(path).header("Authorization", bearer(userId, "LEARNER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }

    private String bearer(long userId, String role) {
        return "Bearer " + jwt.issueAccessToken(userId, List.of(role), true, UUID.randomUUID());
    }

    private void finish(long user, List<Long> questions) {
        long session = fixtures.session(user, questions);
        sessionIds.add(session);
        practice.finish(user, session,
                IntStream.rangeClosed(1, questions.size()).mapToObj(Fixtures::answer).toList());
    }

    private long user() {
        long id = fixtures.user();
        userIds.add(id);
        return id;
    }

    private long node() {
        long id = fixtures.node();
        nodeIds.add(id);
        return id;
    }

    private long question(long nodeId) {
        long id = fixtures.question(nodeId);
        questionIds.add(id);
        return id;
    }

    private static class Fixtures {
        private static final java.util.concurrent.atomic.AtomicInteger CODES = new java.util.concurrent.atomic.AtomicInteger(700);
        private final JdbcTemplate jdbc;
        private final PracticeService practice;
        private Fixtures(JdbcTemplate jdbc, PracticeService practice) {
            this.jdbc = jdbc;
            this.practice = practice;
        }
        private long user() {
            return jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,'Controller fixture') RETURNING id",
                    Long.class, "t09-api-" + UUID.randomUUID() + "@example.test",
                    "$2a$12$fixtureTestHashForDatabaseOnly00000000000000000000000000");
        }
        private long node() {
            return jdbc.queryForObject("INSERT INTO learning.knowledge_points(code,title,domain,name_zh,hsk_level) "
                    + "VALUES (?,'Nút giả','VB','字',3) RETURNING id", Long.class,
                    "VB3-" + String.format("%03d", CODES.incrementAndGet()));
        }
        private long question(long nodeId) {
            long id = jdbc.queryForObject("INSERT INTO learning.questions(qtype,hsk_level,stem,options,correct_key,status,code) "
                    + "VALUES ('MULTIPLE_CHOICE',1,'Câu giả','[{\"key\":\"A\",\"text\":\"Đúng\",\"is_correct\":true},"
                    + "{\"key\":\"B\",\"text\":\"Sai\",\"is_correct\":false}]'::jsonb,'A','APPROVED',?) RETURNING id",
                    Long.class, "T09-API-" + UUID.randomUUID());
            jdbc.update("INSERT INTO learning.question_knowledge_points(question_id,knowledge_point_id) VALUES (?,?)", id, nodeId);
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
            return practice.start(new PracticeService.StartPracticeCommand(userId, PracticeSource.TOPIC_TEST,
                    UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE, null, served, List.of(), null, null))
                    .sessionId();
        }
        private static Answer answer(int no) {
            return new Answer(no, "A", 2000, 0, false, false, null);
        }
    }
}
