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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
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
class PlacementRunIntegrationTest extends AbstractIntegrationTest {
    private static final String RUNS = "/api/placement/runs";

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
    void FR021_AT20_advancesOnceReplacesExpiredBlockAndKeepsConclusionStable() throws Exception {
        var bank = new Bank();
        var pool = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        try {
            bank.complete();
            long run = json(start(learner, "{}", 201).andReturn()).path("runId").asLong();
            assertThat(next(run, 200).path("session").path("sessionId").asLong()).isEqualTo(run);
            mvc.perform(getResult(other, run)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("ATTEMPT_NOT_OWNED"));
            mvc.perform(getResult(learner, run)).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("PLACEMENT_NOT_FINISHED"));
            var answers = mapper.createArrayNode();
            for (int i = 1; i <= 5; i++) answers.addObject().put("no", i).put("answer", "A").put("durationMs", 2000);
            mvc.perform(post("/api/practice/sessions/{id}/finish", run).header("Authorization", bearer(learner, "LEARNER"))
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.createObjectNode().set("answers", answers).toString()))
                    .andExpect(status().isOk());

            Callable<MvcResult> call = () -> {
                assertThat(gate.await(10, TimeUnit.SECONDS)).isTrue();
                return mvc.perform(nextRequest(learner, run)).andReturn();
            };
            var one = pool.submit(call);
            var two = pool.submit(call);
            gate.countDown();
            var a = one.get(20, TimeUnit.SECONDS);
            var b = two.get(20, TimeUnit.SECONDS);
            assertThat(List.of(a.getResponse().getStatus(), b.getResponse().getStatus())).containsExactlyInAnyOrder(200, 201);
            JsonNode child = json(a).path("session");
            long expired = child.path("sessionId").asLong();
            assertThat(json(b).path("session").path("sessionId").asLong()).isEqualTo(expired);
            assertThat(child.path("refId").asLong()).isEqualTo(run);
            assertThat(child.path("meta").path("placement").path("level").asInt()).isEqualTo(3);
            mvc.perform(nextRequest(learner, expired)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("ATTEMPT_NOT_OWNED"));
            mvc.perform(nextRequest(other, run)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("ATTEMPT_NOT_OWNED"));

            expire(expired);
            JsonNode replacement = next(run, 201).path("session");
            long replacementId = replacement.path("sessionId").asLong();
            assertThat(replacementId).isNotEqualTo(expired);
            JsonNode meta = replacement.path("meta").path("placement");
            assertThat(meta.path("block").asInt()).isEqualTo(2);
            assertThat(meta.path("level").asInt()).isEqualTo(3);
            assertThat(servedIds(replacementId)).doesNotContainAnyElementsOf(servedIds(expired));
            expire(replacementId);
            JsonNode conclusion = next(run, 200);
            assertThat(conclusion.path("concluded").asBoolean()).isTrue();
            assertThat(conclusion.path("session").isNull()).isTrue();
            var firstResult = placement.result(learner, run);
            assertThat(firstResult.itemCount()).isEqualTo(5);
            assertThat(firstResult.level()).isEqualTo(PlacementAlgorithm.PlacementLevel.HSK2);
            assertThat(firstResult.confidence()).isEqualTo(PlacementAlgorithm.Confidence.TENTATIVE);
            assertThat(next(run, 200)).isEqualTo(conclusion);
            assertThat(placement.result(learner, run).completedAt()).isEqualTo(firstResult.completedAt());
        } finally {
            pool.shutdownNow();
            cleanupPlacement();
            bank.cleanup();
        }
    }

    @Test
    @Transactional
    void FR021_E10_concludesWithoutEvidenceWhenFirstBlockExpiresAndBankRunsOut() throws Exception {
        var bank = new Bank();
        bank.complete();
        long run = json(start(learner, "{}", 201).andReturn()).path("runId").asLong();
        expire(run);
        jdbc.update("""
                UPDATE learning.questions SET status='ARCHIVED' WHERE id IN (
                    SELECT question_id FROM learning.question_knowledge_points
                    WHERE knowledge_point_id IN (SELECT id FROM learning.knowledge_points WHERE code LIKE '%2-973'))
                """);
        entities.clear();
        JsonNode step = next(run, 200);
        entities.clear();
        assertThat(step.path("concluded").asBoolean()).isTrue();
        var result = placement.result(learner, run);
        assertThat(result.level()).isEqualTo(PlacementAlgorithm.PlacementLevel.BELOW_1);
        assertThat(result.confidence()).isEqualTo(PlacementAlgorithm.Confidence.TENTATIVE);
        assertThat(result.itemCount()).isZero();
        assertThat(result.byLevel()).isEmpty();
        assertThat(result.wrongKids()).isEmpty();
        assertThat(result.domains()).hasSize(6).allSatisfy(domain -> {
            assertThat(domain.measured()).isFalse();
            assertThat(domain.percent()).isNull();
        });
        assertThat(placement.summary(learner).last().runId()).isEqualTo(run);
    }

    @Test
    @Transactional
    void FR022_AT21_recomputesThirtyItemsInSixDomainsUsingSnapshotAndThreeQueries() throws Exception {
        jdbc.update("""
                INSERT INTO learning.knowledge_points(code,title,domain,name_zh,hsk_level) VALUES
                    ('NP1-974','Ngữ pháp tự soạn','NP','样例',1), ('TV1-974','Từ vựng tự soạn','TV','样例',1)
                """);
        Long root = null;
        for (int block = 1; block <= 6; block++) {
            var items = mapper.createArrayNode();
            var kidOf = mapper.createObjectNode();
            for (int no = 1; no <= 5; no++) {
                items.addObject().put("no", no);
                kidOf.put(Integer.toString(no), block == 1 && no <= 2 ? "TV1-974" : "NP1-974");
            }
            var snapshot = mapper.createObjectNode();
            snapshot.set("items", items);
            var meta = snapshot.putObject("meta").putObject("placement");
            meta.put("block", block).put("level", 1).set("kidOf", kidOf);
            if (root == null) meta.putObject("conclusion").put("completedAt", "2026-11-04T03:00:00Z")
                    .put("bankExhausted", true);
            long id = jdbc.queryForObject("""
                    INSERT INTO learning.attempts(user_id,kind,ref_type,ref_id,status,served_at,submitted_at,score,max_score,served_items)
                    VALUES (?, 'EXAM', 'PLACEMENT', ?, 'SUBMITTED', now(), now(), 0, 5, ?::jsonb) RETURNING id
                    """, Long.class, learner, root, snapshot.toString());
            if (root == null) root = id;
            jdbc.update("""
                    INSERT INTO learning.attempt_items(attempt_id,order_index,item_type,skill,is_correct)
                    VALUES (?, 1, 'QUESTION_CHOICE', 'READING', true)
                    """, id);
        }
        long run = root;
        var stats = entityFactory.unwrap(SessionFactory.class).getStatistics();
        boolean enabled = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        try {
            long before = stats.getPrepareStatementCount();
            var result = placement.result(learner, run);
            assertThat(stats.getPrepareStatementCount() - before).isEqualTo(3);
            assertThat(result.itemCount()).isEqualTo(30);
            assertThat(result.byLevel()).containsExactly(new PlacementService.LevelScore(1, 6, 30, 20));
            assertThat(result.domains()).extracting(domain -> domain.domain().name())
                    .containsExactly("NA", "CH", "TV", "NP", "NL", "VB");
            assertThat(result.domains().get(2)).satisfies(domain -> {
                assertThat(domain.measured()).isTrue();
                assertThat(domain.tentative()).isTrue();
                assertThat(domain.percent()).isEqualTo(50);
            });
            assertThat(result.domains().stream().filter(domain -> domain.total() == 0).toList()).hasSize(4)
                    .allSatisfy(domain -> {
                        assertThat(domain.measured()).isFalse();
                        assertThat(domain.tentative()).isFalse();
                        assertThat(domain.percent()).isNull();
                    });
            assertThat(result.wrongKids()).containsExactly(
                    new PlacementService.WrongKid("NP1-974", "Ngữ pháp tự soạn", 23, 28),
                    new PlacementService.WrongKid("TV1-974", "Từ vựng tự soạn", 1, 2));
        } finally {
            stats.setStatisticsEnabled(enabled);
        }
        mvc.perform(getResult(learner, run)).andExpect(status().isOk())
                .andExpect(jsonPath("$.domains[0].percent").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(getResult(learner, Long.MAX_VALUE)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("ATTEMPT_NOT_OWNED"));
    }

    private void expire(long session) {
        jdbc.update("UPDATE learning.attempts SET expires_at=now()-interval '1 minute' WHERE id=?", session);
    }

    private JsonNode next(long run, int statusCode) throws Exception {
        return json(mvc.perform(nextRequest(learner, run)).andExpect(status().is(statusCode)).andReturn());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder nextRequest(long user, long run) {
        return post(RUNS + "/{id}/next", run).header("Authorization", bearer(user, "LEARNER"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder getResult(long user, long run) {
        return get(RUNS + "/{id}/result", run).header("Authorization", bearer(user, "LEARNER"));
    }

    private void cleanupPlacement() {
        jdbc.update("""
                DELETE FROM learning.attempt_items WHERE attempt_id IN (
                    SELECT id FROM learning.attempts WHERE user_id=? AND ref_type='PLACEMENT')
                """, learner);
        jdbc.update("DELETE FROM learning.attempts WHERE user_id=? AND ref_type='PLACEMENT'", learner);
        jdbc.update("DELETE FROM learning.study_events WHERE user_id=? AND ref_type='PLACEMENT'", learner);
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
