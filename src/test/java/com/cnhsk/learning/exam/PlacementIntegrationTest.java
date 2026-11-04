package com.cnhsk.learning.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlacementIntegrationTest extends AbstractIntegrationTest {
    private static final String RUNS = "/api/placement/runs";
    private static final String SUMMARY = "/api/placement/summary";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private PlacementService placement;
    @Autowired private EntityManager entities;
    @Autowired private EntityManagerFactory entityFactory;
    private long learner;
    private long other;

    @BeforeAll
    void users() throws Exception {
        learner = user();
        other = user();
    }

    @Test
    @Transactional
    void FR020_AT19_checksAllLevelsAndRejectsInvalidStartLevel() throws Exception {
        var bank = new Bank();
        bank.addLevel(1, 9);
        bank.addLevel(2, 10);
        bank.addLevel(3, 8);
        start(learner, "{}", 422)
                .andExpect(jsonPath("$.error_code").value("PLACEMENT_BANK_INSUFFICIENT"))
                .andExpect(jsonPath("$.details.length()").value(2))
                .andExpect(jsonPath("$.details[0].level").value(1))
                .andExpect(jsonPath("$.details[0].available").value(9))
                .andExpect(jsonPath("$.details[1].level").value(3))
                .andExpect(jsonPath("$.details[1].available").value(8));
        start(learner, "{\"startLevel\":4}", 422)
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
    }

    @Test
    void FR023_AT22_servesWithoutAnswersAndFinishesWithRunAndBlock() throws Exception {
        var bank = new Bank();
        try {
            bank.complete();
            JsonNode created = json(start(learner, "{}", 201).andReturn());
            long run = created.path("runId").asLong();
            JsonNode session = created.path("session");
            assertThat(run).isEqualTo(session.path("sessionId").asLong());
            assertThat(session.path("refId").isNull()).isTrue();
            JsonNode metadata = session.path("meta").path("placement");
            assertThat(metadata.has("runId")).isFalse();
            assertThat(metadata.path("block").asInt()).isEqualTo(1);
            assertThat(metadata.path("level").asInt()).isEqualTo(2);
            assertThat(metadata.path("kidOf").size()).isEqualTo(5);
            assertThat(session.path("items").size()).isEqualTo(5);
            for (JsonNode item : session.path("items")) {
                for (String field : List.of("answer", "accept", "explanation", "is_correct", "isCorrect", "correctKey")) {
                    assertThat(item.findValue(field)).as(field).isNull();
                }
            }
            start(learner, "{\"startLevel\":3}", 200)
                    .andExpect(jsonPath("$.runId").value(run))
                    .andExpect(jsonPath("$.session.sessionId").value(run));
            mvc.perform(post("/api/practice/sessions/{id}/finish", run).header("Authorization", bearer(learner, "LEARNER")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.extra.runId").value(run))
                    .andExpect(jsonPath("$.extra.block").value(1));
        } finally {
            cleanupPlacement();
            bank.cleanup();
        }
    }

    @Test
    @Transactional
    void FR018_AT17_prefersUnseenQuestionsAndRotatesAcrossDomains() throws Exception {
        var bank = new Bank();
        bank.complete();
        long first = json(start(learner, "{}", 201).andReturn()).path("runId").asLong();
        Set<Long> seen = new HashSet<>(servedIds(first));
        JsonNode firstSummary = json(mvc.perform(get("/api/practice/sessions/{id}", first)
                        .header("Authorization", bearer(learner, "LEARNER"))).andExpect(status().isOk()).andReturn());
        var domains = new HashSet<String>();
        firstSummary.path("meta").path("placement").path("kidOf").forEach(kid -> domains.add(kid.asText().substring(0, 2)));
        assertThat(domains).hasSize(3);
        conclude(first);
        long next = json(start(learner, "{}", 201).andReturn()).path("runId").asLong();
        assertThat(servedIds(next)).doesNotContainAnyElementsOf(seen);

        List<PlacementService.BankQuestion> entries = bank.questions.stream()
                .map(id -> new PlacementService.BankQuestion(entities.find(Question.class, id), "NP2-973", "NP")).toList();
        assertThat(placement.selectBlock(entries.subList(0, 6), Set.of(entries.get(0).question().getId()), Set.of()))
                .hasSize(5).allMatch(entry -> !entry.question().getId().equals(entries.get(0).question().getId()));
        assertThat(placement.selectBlock(entries.subList(0, 5), Set.of(entries.get(0).question().getId()), Set.of()))
                .isEmpty();
    }

    @Test
    @Transactional
    void FR019_AT18_summarizesOnlyOwnedRunsWithOneQuery() throws Exception {
        assertThat(placement.summary(learner).active()).isNull();
        assertThat(placement.summary(learner).last()).isNull();
        new Bank().complete();
        long run = json(start(learner, "{}", 201).andReturn()).path("runId").asLong();
        conclude(run);
        long second = json(start(learner, "{}", 201).andReturn()).path("runId").asLong();
        jdbc.update("UPDATE learning.attempts SET status='SUBMITTED', submitted_at=now(), score=4, max_score=5 WHERE id=?", second);
        long child = jdbc.queryForObject("""
                INSERT INTO learning.attempts(user_id,kind,ref_type,ref_id,expires_at,served_items)
                SELECT user_id,kind,ref_type,id,now()+interval '1 hour',
                    jsonb_set(served_items,'{meta,placement,block}','2')
                FROM learning.attempts WHERE id=? RETURNING id
                """, Long.class, second);
        entities.clear();
        var stats = entityFactory.unwrap(SessionFactory.class).getStatistics();
        boolean enabled = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        try {
            long before = stats.getPrepareStatementCount();
            var result = placement.summary(learner);
            assertThat(stats.getPrepareStatementCount() - before).isEqualTo(1);
            assertThat(result.active()).isEqualTo(new PlacementService.Active(second, child));
            assertThat(result.last().runId()).isEqualTo(run);
            assertThat(result.last().level()).isEqualTo(PlacementAlgorithm.PlacementLevel.HSK2);
            assertThat(result.last().confidence()).isEqualTo(PlacementAlgorithm.Confidence.TENTATIVE);
        } finally {
            stats.setStatisticsEnabled(enabled);
        }
        mvc.perform(get(SUMMARY).header("Authorization", bearer(other, "LEARNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.last").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void FR020_AT19_parallelStartsCreateOneBlock() throws Exception {
        var bank = new Bank();
        var pool = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        try {
            bank.complete();
            Callable<MvcResult> call = () -> {
                assertThat(gate.await(10, TimeUnit.SECONDS)).isTrue();
                return request(learner, "{}");
            };
            var first = pool.submit(call);
            var second = pool.submit(call);
            gate.countDown();
            MvcResult a = first.get(20, TimeUnit.SECONDS);
            MvcResult b = second.get(20, TimeUnit.SECONDS);
            assertThat(List.of(a.getResponse().getStatus(), b.getResponse().getStatus())).containsExactlyInAnyOrder(200, 201);
            assertThat(json(a).path("runId").asLong()).isEqualTo(json(b).path("runId").asLong());
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM learning.attempts WHERE user_id=? AND ref_type='PLACEMENT'
                    """, Long.class, learner)).isEqualTo(1L);
        } finally {
            pool.shutdownNow();
            cleanupPlacement();
            bank.cleanup();
        }
    }

    @Test
    void FR020_onlyLearnerAndRequiresCsrfForCookiePost() throws Exception {
        mvc.perform(get(SUMMARY)).andExpect(status().isUnauthorized());
        mvc.perform(get(SUMMARY).header("Authorization", bearer(learner, "CONTENT_EDITOR"))).andExpect(status().isForbidden());
        mvc.perform(post(RUNS).cookie(new Cookie("access_token", token(learner, "LEARNER"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
    }

    private void cleanupPlacement() {
        jdbc.update("""
                DELETE FROM learning.attempt_items WHERE attempt_id IN (
                    SELECT id FROM learning.attempts WHERE user_id=? AND ref_type='PLACEMENT')
                """, learner);
        jdbc.update("DELETE FROM learning.attempts WHERE user_id=? AND ref_type='PLACEMENT'", learner);
        jdbc.update("DELETE FROM learning.study_events WHERE user_id=? AND ref_type='PLACEMENT'", learner);
    }

    private void conclude(long run) {
        jdbc.update("""
                UPDATE learning.attempts SET status='SUBMITTED',submitted_at=now(),score=4,max_score=5,
                    served_items=jsonb_set(served_items,'{meta,placement,conclusion}',
                        '{"completedAt":"2026-11-04T03:00:00Z","bankExhausted":true}')
                WHERE id=?
                """, run);
        entities.clear();
    }

    private List<Long> servedIds(long run) {
        return jdbc.queryForList("""
                SELECT (item->>'questionId')::bigint FROM learning.attempts a,
                    jsonb_array_elements(a.served_items->'items') item WHERE a.id=?
                """, Long.class, run);
    }

    private final class Bank {
        private final List<Long> questions = new ArrayList<>();
        private final List<Long> nodes = new ArrayList<>();

        private void complete() {
            for (int level = 1; level <= 3; level++) addLevel(level, 10);
        }

        private void addLevel(int level, int count) {
            var domainNodes = new ArrayList<Long>();
            for (String domain : List.of("NP", "TV", "CH")) {
                long node = jdbc.queryForObject("""
                        INSERT INTO learning.knowledge_points(code,title,domain,name_zh,hsk_level)
                        VALUES (?, 'Nút tự soạn', ?, '样例', ?) RETURNING id
                        """, Long.class, domain + level + "-973", domain, level);
                nodes.add(node);
                domainNodes.add(node);
            }
            for (int i = 0; i < count; i++) {
                long question = jdbc.queryForObject("""
                        INSERT INTO learning.questions(code,qtype,hsk_level,stem,options,correct_key,explanation,status)
                        VALUES (?, 'MULTIPLE_CHOICE', ?, 'Câu tự soạn',
                            '[{"key":"A","text":"Một","is_correct":true},{"key":"B","text":"Hai","is_correct":false}]',
                            'A', 'Giải thích tự soạn', 'APPROVED') RETURNING id
                        """, Long.class, "TEST-" + UUID.randomUUID(), level);
                questions.add(question);
                jdbc.update("INSERT INTO learning.question_knowledge_points(question_id,knowledge_point_id) VALUES (?,?)",
                        question, domainNodes.get(i % domainNodes.size()));
            }
        }

        private void cleanup() {
            for (long node : nodes) {
                jdbc.update("DELETE FROM learning.question_knowledge_points WHERE knowledge_point_id=?", node);
                jdbc.update("DELETE FROM learning.knowledge_points WHERE id=?", node);
            }
            for (long question : questions) jdbc.update("DELETE FROM learning.questions WHERE id=?", question);
        }
    }

    private ResultActions start(long user, String body, int expected) throws Exception {
        return mvc.perform(post(RUNS).header("Authorization", bearer(user, "LEARNER"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(expected));
    }

    private MvcResult request(long user, String body) throws Exception {
        return mvc.perform(post(RUNS).header("Authorization", bearer(user, "LEARNER"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private long user() throws Exception {
        String password = "Aa1" + UUID.randomUUID();
        return json(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.test",
                        "password", password, "confirmPassword", password, "displayName", "Placement test user",
                        "acceptTerms", true)))).andExpect(status().isCreated()).andReturn()).path("userId").asLong();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String bearer(long user, String role) {
        return "Bearer " + token(user, role);
    }

    private String token(long user, String role) {
        return jwt.issueAccessToken(user, List.of(role), false, UUID.randomUUID());
    }
}
