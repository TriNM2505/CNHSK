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

import com.cnhsk.learning.exam.PlacementFormScoring.Confidence;
import com.cnhsk.learning.exam.PlacementFormScoring.Level;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.web.servlet.ResultActions;

/** Placement on fixed forms with committed data: each test cleans the forms, questions and runs it made. */
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
    private long learner;
    private long other;
    private Forms forms;

    @BeforeAll
    void users() throws Exception {
        learner = user();
        other = user();
    }

    @AfterEach
    void cleanup() {
        cleanupPlacement();
        if (forms != null) forms.cleanup();
        forms = null;
    }

    @Test
    void withoutAPublishedFormThatCoversHskOneToThreeTheTestIsNotReady() throws Exception {
        forms = new Forms();
        forms.publish(10, 10, 0);
        start(learner, "{}", 422).andExpect(jsonPath("$.error_code").value("PLACEMENT_BANK_INSUFFICIENT"));
        start(learner, "{\"startLevel\":4}", 422).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
    }

    @Test
    void servesOneWholeFormWithoutAnswersAndResumesTheSameRun() throws Exception {
        forms = new Forms();
        String code = forms.publish(10, 10, 10);
        JsonNode created = json(start(learner, "{}", 201).andReturn());
        long run = created.path("runId").asLong();
        JsonNode session = created.path("session");
        assertThat(run).isEqualTo(session.path("sessionId").asLong());
        assertThat(session.path("items").size()).isEqualTo(30);
        JsonNode meta = session.path("meta").path("placement");
        assertThat(meta.path("form").asText()).isEqualTo(code);
        assertThat(meta.path("levelOf").size()).isEqualTo(30);
        assertThat(meta.path("kidOf").size()).isEqualTo(30);
        for (JsonNode item : session.path("items")) {
            for (String field : List.of("answer", "accept", "explanation", "is_correct", "isCorrect", "correctKey")) {
                assertThat(item.findValue(field)).as(field).isNull();
            }
        }
        start(learner, "{\"startLevel\":3}", 200).andExpect(jsonPath("$.runId").value(run));
        mvc.perform(post("/api/practice/sessions/{id}/finish", run).header("Authorization", bearer(learner, "LEARNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.extra.runId").value(run))
                .andExpect(jsonPath("$.extra.block").value(1));
    }

    @Test
    void submittingConcludesScoresEachPartAndOverwritesTheDeclaredLevel() throws Exception {
        forms = new Forms();
        forms.publish(10, 10, 10);
        jdbc.update("UPDATE auth.users SET hsk_level = 3 WHERE id = ?", learner);
        long run = json(start(learner, "{}", 201).andReturn()).path("runId").asLong();
        mvc.perform(get(RUNS + "/{id}/result", run).header("Authorization", bearer(learner, "LEARNER")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error_code").value("PLACEMENT_NOT_FINISHED"));
        finish(run, Map.of(1, 10, 2, 7, 3, 3));
        JsonNode step = json(mvc.perform(post(RUNS + "/{id}/next", run).header("Authorization", bearer(learner, "LEARNER")))
                .andExpect(status().isOk()).andReturn());
        assertThat(step.path("concluded").asBoolean()).isTrue();
        entities.clear();
        var result = placement.result(learner, run);
        var expected = expected(10, 7, 3);
        assertThat(result.level()).isEqualTo(expected.level());
        assertThat(result.confidence()).isEqualTo(Confidence.SURE);
        assertThat(result.itemCount()).isEqualTo(30);
        assertThat(result.byLevel()).extracting(PlacementService.LevelScore::correct).containsExactly(10, 7, 3);
        assertThat(result.wrongKids()).isNotEmpty();
        assertThat(jdbc.queryForObject("SELECT hsk_level FROM auth.users WHERE id = ?", Integer.class, learner))
                .isEqualTo(PlacementFormScoring.profileLevel(expected.level()));
        // The run keeps the estimated ability, which later calibrates each question's difficulty.
        assertThat(jdbc.queryForObject("""
                SELECT CAST(served_items->'meta'->'placement'->'conclusion'->>'ability' AS float8)
                FROM learning.attempts WHERE id = ?
                """, Double.class, run)).isEqualTo(expected.ability());
        // Wrong answers keep their question, so AI generation and the knowledge tree still see them.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM learning.attempt_items
                WHERE attempt_id = ? AND is_correct = false AND question_id IS NOT NULL
                """, Long.class, run)).isEqualTo(10L);
        mvc.perform(post(RUNS + "/{id}/next", run).header("Authorization", bearer(learner, "LEARNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.concluded").value(true));
        assertThat(placement.summary(learner).last().level()).isEqualTo(expected.level());
        mvc.perform(get(RUNS + "/{id}/result", run).header("Authorization", bearer(other, "LEARNER")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("ATTEMPT_NOT_OWNED"));
    }

    @Test
    void belowHskOneIsRecordedAsOneAndManySkippedAnswersAreTentative() throws Exception {
        forms = new Forms();
        forms.publish(10, 10, 10);
        long run = json(start(learner, "{}", 201).andReturn()).path("runId").asLong();
        finish(run, Map.of(1, 3, 2, 0, 3, 0));
        mvc.perform(post(RUNS + "/{id}/next", run).header("Authorization", bearer(learner, "LEARNER")))
                .andExpect(status().isOk());
        entities.clear();
        var result = placement.result(learner, run);
        assertThat(result.level()).isIn(Level.BELOW_1, Level.HSK1);
        assertThat(result.confidence()).isEqualTo(Confidence.TENTATIVE);
        assertThat(jdbc.queryForObject("SELECT hsk_level FROM auth.users WHERE id = ?", Integer.class, learner)).isEqualTo(1);
    }

    /** What the model concludes for a sheet of this test's questions: two options each, starting difficulty. */
    private PlacementFormScoring.Conclusion expected(int hsk1, int hsk2, int hsk3) {
        int[] right = {hsk1, hsk2, hsk3};
        List<PlacementFormScoring.Item> items = new ArrayList<>();
        for (int level = 1; level <= 3; level++) {
            for (int index = 0; index < 10; index++) {
                items.add(new PlacementFormScoring.Item(PlacementFormScoring.priorDifficulty(level, 2), 0.5,
                        index < right[level - 1], true));
            }
        }
        return PlacementFormScoring.decide(items, new PlacementConfig());
    }

    @Test
    void aRetakeRightAwayDrawsAnotherFormWheneverOneExists() throws Exception {
        forms = new Forms();
        Set<String> codes = new HashSet<>(List.of(forms.publish(10, 10, 10), forms.publish(10, 10, 10)));
        String previous = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            JsonNode session = json(start(learner, "{}", 201).andReturn()).path("session");
            String code = session.path("meta").path("placement").path("form").asText();
            assertThat(codes).contains(code);
            if (previous != null) assertThat(code).isNotEqualTo(previous);
            previous = code;
            long run = session.path("sessionId").asLong();
            finish(run, Map.of(1, 10, 2, 10, 3, 10));
            mvc.perform(post(RUNS + "/{id}/next", run).header("Authorization", bearer(learner, "LEARNER")))
                    .andExpect(status().isOk());
            entities.clear();
        }
        assertThat(placement.summary(learner).last().level()).isEqualTo(expected(10, 10, 10).level());
    }

    @Test
    void anExpiredFormIsServedAgainAndAWithdrawnFormConcludesTheRun() throws Exception {
        forms = new Forms();
        String code = forms.publish(10, 10, 10);
        long run = json(start(learner, "{}", 201).andReturn()).path("runId").asLong();
        expire(run);
        JsonNode again = json(mvc.perform(post(RUNS + "/{id}/next", run).header("Authorization", bearer(learner, "LEARNER")))
                .andExpect(status().isCreated()).andReturn()).path("session");
        assertThat(again.path("refId").asLong()).isEqualTo(run);
        assertThat(again.path("meta").path("placement").path("form").asText()).isEqualTo(code);
        long retry = again.path("sessionId").asLong();
        expire(retry);
        jdbc.update("UPDATE learning.exams SET status = 'ARCHIVED' WHERE code = ?", code);
        mvc.perform(post(RUNS + "/{id}/next", run).header("Authorization", bearer(learner, "LEARNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.concluded").value(true));
        entities.clear();
        var result = placement.result(learner, run);
        assertThat(result.level()).isEqualTo(Level.BELOW_1);
        assertThat(result.itemCount()).isZero();
    }

    @Test
    void aRunFromTheEarlierAdaptiveTestStillHasAResult() throws Exception {
        jdbc.update("""
                INSERT INTO learning.knowledge_points(code,title,domain,name_zh,hsk_level)
                VALUES ('NP1-975','Ngữ pháp tự soạn','NP','样例',1)
                """);
        var items = mapper.createArrayNode();
        var kidOf = mapper.createObjectNode();
        for (int no = 1; no <= 5; no++) {
            items.addObject().put("no", no);
            kidOf.put(Integer.toString(no), "NP1-975");
        }
        var snapshot = mapper.createObjectNode();
        snapshot.set("items", items);
        var meta = snapshot.putObject("meta").putObject("placement");
        meta.put("block", 1).put("level", 1).set("kidOf", kidOf);
        meta.putObject("conclusion").put("completedAt", "2026-11-04T03:00:00Z").put("bankExhausted", true);
        long run = jdbc.queryForObject("""
                INSERT INTO learning.attempts(user_id,kind,ref_type,status,served_at,submitted_at,score,max_score,served_items)
                VALUES (?, 'EXAM', 'PLACEMENT', 'SUBMITTED', now(), now(), 0, 5, ?::jsonb) RETURNING id
                """, Long.class, learner, snapshot.toString());
        var result = placement.result(learner, run);
        assertThat(result.level()).isEqualTo(Level.BELOW_1);
        assertThat(result.byLevel()).singleElement().satisfies(score -> assertThat(score.total()).isEqualTo(5));
        jdbc.update("DELETE FROM learning.knowledge_points WHERE code = 'NP1-975'");
    }

    @Test
    void parallelStartsCreateOneRun() throws Exception {
        forms = new Forms();
        forms.publish(10, 10, 10);
        var pool = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        try {
            Callable<MvcResult> call = () -> {
                assertThat(gate.await(10, TimeUnit.SECONDS)).isTrue();
                return mvc.perform(post(RUNS).header("Authorization", bearer(learner, "LEARNER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
            };
            var first = pool.submit(call);
            var second = pool.submit(call);
            gate.countDown();
            MvcResult a = first.get(20, TimeUnit.SECONDS);
            MvcResult b = second.get(20, TimeUnit.SECONDS);
            assertThat(List.of(a.getResponse().getStatus(), b.getResponse().getStatus())).containsExactlyInAnyOrder(200, 201);
            assertThat(json(a).path("runId").asLong()).isEqualTo(json(b).path("runId").asLong());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void onlyLearnersAndCookiePostsNeedCsrf() throws Exception {
        mvc.perform(get(SUMMARY)).andExpect(status().isUnauthorized());
        mvc.perform(get(SUMMARY).header("Authorization", bearer(learner, "CONTENT_EDITOR"))).andExpect(status().isForbidden());
        mvc.perform(post(RUNS).cookie(new Cookie("access_token", token(learner, "LEARNER"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
    }

    /** Answers {@code right.get(level)} questions of each level with the correct key and the rest wrongly. */
    private void finish(long run, Map<Integer, Integer> right) throws Exception {
        JsonNode levelOf = mapper.readTree(jdbc.queryForObject(
                "SELECT served_items->'meta'->'placement'->'levelOf' FROM learning.attempts WHERE id = ?", String.class, run));
        var given = new java.util.HashMap<Integer, Integer>();
        var answers = mapper.createArrayNode();
        levelOf.fields().forEachRemaining(entry -> {
            int level = entry.getValue().asInt();
            int used = given.merge(level, 1, Integer::sum);
            int target = right.getOrDefault(level, 0);
            int no = Integer.parseInt(entry.getKey());
            if (target == 0 && level != 1) return; // leave whole parts unanswered when nothing is right there
            answers.addObject().put("no", no).put("answer", used <= target ? "A" : "B").put("durationMs", 2000);
        });
        mvc.perform(post("/api/practice/sessions/{id}/finish", run).header("Authorization", bearer(learner, "LEARNER"))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.createObjectNode().set("answers", answers).toString()))
                .andExpect(status().isOk());
    }

    private void expire(long session) {
        jdbc.update("UPDATE learning.attempts SET expires_at = now() - interval '1 minute' WHERE id = ?", session);
        entities.clear();
    }

    private void cleanupPlacement() {
        for (long user : List.of(learner, other)) {
            jdbc.update("""
                    DELETE FROM learning.attempt_items WHERE attempt_id IN (
                        SELECT id FROM learning.attempts WHERE user_id=? AND ref_type='PLACEMENT')
                    """, user);
            jdbc.update("DELETE FROM learning.study_events WHERE user_id=? AND ref_type='PLACEMENT'", user);
            jdbc.update("DELETE FROM learning.attempts WHERE user_id=? AND ref_type='PLACEMENT' AND ref_id IS NOT NULL", user);
            jdbc.update("DELETE FROM learning.attempts WHERE user_id=? AND ref_type='PLACEMENT'", user);
        }
    }

    /** Published placement forms built from questions labelled with HSK 1–3 nodes in three domains. */
    private final class Forms {
        private final List<Long> questions = new ArrayList<>();
        private final List<Long> nodes = new ArrayList<>();
        private final List<Long> exams = new ArrayList<>();

        private String publish(int hsk1, int hsk2, int hsk3) {
            String code = "PLACEMENT-T" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            long exam = jdbc.queryForObject("""
                    INSERT INTO learning.exams(code,title,hsk_level,sections,total_score,status,published_at)
                    VALUES (?, 'Kiểm tra trình độ tự soạn', 3, '[]', 30, 'PUBLISHED', now()) RETURNING id
                    """, Long.class, code);
            exams.add(exam);
            int order = 0;
            int[] counts = {hsk1, hsk2, hsk3};
            for (int level = 1; level <= 3; level++) {
                for (int i = 0; i < counts[level - 1]; i++) {
                    long question = question(level, i);
                    jdbc.update("INSERT INTO learning.exam_questions(exam_id,question_id,section_index,order_index) VALUES (?,?,?,?)",
                            exam, question, level - 1, order++);
                }
            }
            return code;
        }

        private final Map<String, Long> nodeOf = new java.util.HashMap<>();

        private long question(int level, int index) {
            String domain = List.of("NP", "TV", "CH").get(index % 3);
            long node = nodeOf.computeIfAbsent(domain + level + "-976", kid -> {
                long id = jdbc.queryForObject("""
                        INSERT INTO learning.knowledge_points(code,title,domain,name_zh,hsk_level)
                        VALUES (?, 'Nút tự soạn', ?, '样例', ?) RETURNING id
                        """, Long.class, kid, domain, level);
                nodes.add(id);
                return id;
            });
            long question = jdbc.queryForObject("""
                    INSERT INTO learning.questions(code,qtype,hsk_level,stem,options,correct_key,explanation,status)
                    VALUES (?, 'MULTIPLE_CHOICE', ?, 'Câu tự soạn',
                        '[{"key":"A","text":"Một","is_correct":true},{"key":"B","text":"Hai","is_correct":false}]',
                        'A', 'Giải thích tự soạn', 'APPROVED') RETURNING id
                    """, Long.class, "TEST-" + UUID.randomUUID(), level);
            questions.add(question);
            jdbc.update("INSERT INTO learning.question_knowledge_points(question_id,knowledge_point_id,role) VALUES (?,?,'PRIMARY')",
                    question, node);
            return question;
        }

        private void cleanup() {
            for (long exam : exams) {
                jdbc.update("DELETE FROM learning.exam_questions WHERE exam_id=?", exam);
                jdbc.update("DELETE FROM learning.exams WHERE id=?", exam);
            }
            for (long question : questions) {
                jdbc.update("DELETE FROM learning.question_knowledge_points WHERE question_id=?", question);
                jdbc.update("DELETE FROM learning.questions WHERE id=?", question);
            }
            for (long node : nodes) jdbc.update("DELETE FROM learning.knowledge_points WHERE id=?", node);
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
