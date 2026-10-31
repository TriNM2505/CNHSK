package com.cnhsk.learning.grammar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeExtra;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.practice.entity.Attempt;
import com.cnhsk.learning.practice.entity.AttemptItem;
import com.cnhsk.learning.practice.entity.AttemptItemRepository;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GrammarLessonIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");
    private static final String HOUR = "2026103012";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private LexemeRepository lexemes;
    @Autowired private AttemptRepository attempts;
    @Autowired private AttemptItemRepository attemptItems;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @MockitoBean private Clock clock;
    private long owner;
    private long other;

    @BeforeAll
    void identitiesThroughAuthApi() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        owner = createUser();
        other = createUser();
    }

    @BeforeEach
    void fixedTime() {
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void FR009_AT05_recordsLessonReadAtStartAndServesChoicesWithoutAnswers() throws Exception {
        Lexeme point = grammar(explanation(100), 3);
        JsonNode session = start(owner, point, 12_000, 201);
        long id = session.path("sessionId").asLong();
        assertThat(session.path("source").asText()).isEqualTo("GRAMMAR_LESSON");
        assertThat(session.path("refId").asLong()).isEqualTo(point.getId());
        assertThat(session.path("feedback").asText()).isEqualTo("IMMEDIATE");
        assertThat(session.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(session.path("expiresAt").asText()).isEqualTo(NOW.plusSeconds(7200).toString());
        JsonNode items = session.path("items");
        assertThat(items).hasSize(4);
        assertThat(items.get(0).path("type").asText()).isEqualTo("LESSON_READ");
        assertThat(items.get(0).path("skill").asText()).isEqualTo("GRAMMAR");
        assertThat(items.get(0).path("prompt").path("text").asText()).isEqualTo(point.getText());
        for (int i = 1; i < items.size(); i++) {
            JsonNode item = items.get(i);
            assertThat(item.path("no").asInt()).isEqualTo(i + 1);
            assertThat(item.path("type").asText()).isEqualTo("GRAMMAR_CHOICE");
            assertThat(item.path("skill").asText()).isEqualTo("GRAMMAR");
            assertThat(item.path("prompt").path("sentence").asText()).startsWith("Câu ");
            assertThat(item.path("rules").path("minDurationMs").asInt()).isEqualTo(800);
            assertThat(item.path("options")).extracting(option -> option.path("key").asText())
                    .containsExactly("A", "B", "C", "D");
        }
        assertThat(session.path("answered")).hasSize(1);
        assertThat(session.path("answered").get(0).path("no").asInt()).isEqualTo(1);
        assertThat(session.path("answered").get(0).path("answer").asText()).isEqualTo("UNDERSTOOD");

        List<AttemptItem> recorded = attemptItems.findByAttemptIdOrderByOrderIndexAsc(id);
        assertThat(recorded).hasSize(1);
        AttemptItem read = recorded.get(0);
        assertThat(read.getOrderIndex()).isEqualTo(1);
        assertThat(read.getItemType()).isEqualTo("LESSON_READ");
        assertThat(read.getLexemeId()).isEqualTo(point.getId());
        assertThat(read.getAnswer()).isEqualTo("UNDERSTOOD");
        assertThat(read.getDurationMs()).isEqualTo(12_000);
        assertThat(read.getCorrect()).isNull();
        assertSafe(session);
        assertSafe(mapper.readTree(mvc.perform(get("/api/practice/sessions/{id}", id)
                        .header("Authorization", bearer(owner, "LEARNER"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()));
    }

    @ParameterizedTest
    @CsvSource({"2000,500,true", "4999,300,true", "5000,500,false", "2000,299,false", "0,100,false"})
    void FR010_AT06_flagsFastReadingOnlyForLongExplanations(int duration, int length, boolean flagged) throws Exception {
        Lexeme point = grammar(explanation(length), 1);
        JsonNode session = start(owner, point, duration, 201);
        assertThat(session.path("meta").has("readTooFast")).isEqualTo(flagged);
        if (flagged) assertThat(session.path("meta").path("readTooFast").asBoolean()).isTrue();
    }

    @Test
    void FR011_AT07_servesFiveRandomExercisesInRandomOrderFromTwelve() throws Exception {
        long learner = createUser();
        Lexeme point = grammar(explanation(10), 12);
        Set<String> seen = new HashSet<>();
        Set<String> firstServed = new HashSet<>();
        for (int run = 0; run < 100; run++) {
            JsonNode session = start(learner, point, 6_000, 201);
            JsonNode items = session.path("items");
            assertThat(items).hasSize(6);
            Set<String> stems = new HashSet<>();
            for (int i = 1; i < items.size(); i++) stems.add(items.get(i).path("prompt").path("sentence").asText());
            assertThat(stems).hasSize(5);
            seen.addAll(stems);
            firstServed.add(items.get(1).path("prompt").path("sentence").asText());
            abandon(session.path("sessionId").asLong());
        }
        assertThat(seen.size()).isGreaterThanOrEqualTo(10);
        assertThat(firstServed.size()).isGreaterThan(1);
    }

    @Test
    void FR012_AT08_reshuffledKeysStillPointToTheImportedCorrectOption() throws Exception {
        long learner = createUser();
        Lexeme point = grammar(explanation(10), 1);
        Set<String> correctKeys = new HashSet<>();
        for (int run = 0; run < 100; run++) {
            long id = start(learner, point, 6_000, 201).path("sessionId").asLong();
            JsonNode served = served(id).path("items").get(1);
            String key = served.path("answer").asText();
            assertThat(served.path("options")).extracting(option -> option.path("key").asText())
                    .containsExactly("A", "B", "C", "D");
            assertThat(served.path("options")).filteredOn(option -> option.path("key").asText().equals(key))
                    .singleElement().extracting(option -> option.path("text").asText()).isEqualTo("đúng-1");
            assertThat(served.path("explanation").asText()).isEqualTo("Lời giải bí mật 1");
            correctKeys.add(key);
            abandon(id);
        }
        assertThat(correctKeys).containsExactlyInAnyOrder("A", "B", "C", "D");
    }

    @Test
    void FR012_choicesAreGradedOnTheServerAndExplainedOnlyAfterAnswering() throws Exception {
        Lexeme point = grammar(explanation(10), 1);
        long id = start(owner, point, 6_000, 201).path("sessionId").asLong();
        String key = served(id).path("items").get(1).path("answer").asText();
        JsonNode result = answer(owner, id, 2, key);
        assertThat(result.path("correct").asBoolean()).isTrue();
        assertThat(result.path("correctAnswer").asText()).isEqualTo(key);
        assertThat(result.path("explanation").asText()).isEqualTo("Lời giải bí mật 1");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void FR013_AT09_lessonWithoutExercisesIsSubmittedImmediatelyAndMarksLearned(boolean emptyList) throws Exception {
        long learner = createUser();
        Lexeme point = grammar(explanation(10), 0);
        if (!emptyList) {
            LexemeExtra extra = point.getExtra();
            point.setExtra(new LexemeExtra(extra.examples(), null, extra.structure(), extra.explanation(),
                    extra.commonMistake(), null));
            point = lexemes.saveAndFlush(point);
        }
        JsonNode body = start(learner, point, 3_000, 201);
        assertThat(body.fieldNames()).toIterable().containsExactlyInAnyOrder("session", "finish");
        JsonNode session = body.path("session");
        JsonNode finish = body.path("finish");
        long id = session.path("sessionId").asLong();
        assertThat(session.path("status").asText()).isEqualTo("SUBMITTED");
        assertThat(session.path("items")).hasSize(1);
        assertThat(session.path("items").get(0).path("type").asText()).isEqualTo("LESSON_READ");
        assertThat(finish.path("sessionId").asLong()).isEqualTo(id);
        assertThat(finish.path("status").asText()).isEqualTo("SUBMITTED");
        assertThat(finish.path("score").isNull()).isTrue();
        assertThat(finish.path("maxScore").isNull()).isTrue();
        assertThat(finish.path("percent").isNull()).isTrue();

        Attempt attempt = attempts.findById(id).orElseThrow();
        assertThat(attempt.getStatus()).isEqualTo("SUBMITTED");
        assertThat(attempt.getScore()).isNull();
        assertThat(attempt.getMaxScore()).isNull();
        assertThat(attempt.getSubmittedAt()).isEqualTo(NOW);
        assertThat(attemptItems.findByAttemptIdOrderByOrderIndexAsc(id)).singleElement()
                .satisfies(item -> assertThat(item.getDurationMs()).isEqualTo(3_000));
        assertThat(redis.opsForValue().get(startKey(learner))).isEqualTo("1");

        JsonNode row = find(list(learner), point.getId());
        assertThat(row.path("learned").asBoolean()).isTrue();
        assertThat(row.path("bestPercent").isNull()).isTrue();
        assertThat(row.path("lastStudiedAt").asText()).isEqualTo(NOW.toString());
        assertThat(start(learner, point, 3_000, 201).path("session").path("sessionId").asLong()).isNotEqualTo(id);
    }

    @Test
    void FR014_AT10_resumesTheSameSessionIgnoringNewReadDurationWithoutUsingStartQuota() throws Exception {
        long learner = createUser();
        Lexeme point = grammar(explanation(500), 2);
        long id = start(learner, point, 8_000, 201).path("sessionId").asLong();
        String before = redis.opsForValue().get(startKey(learner));
        JsonNode resumed = start(learner, point, 1_000, 200);
        assertThat(resumed.path("sessionId").asLong()).isEqualTo(id);
        assertThat(resumed.path("meta").has("readTooFast")).isFalse();
        assertThat(attemptItems.findByAttemptIdOrderByOrderIndexAsc(id).get(0).getDurationMs()).isEqualTo(8_000);
        assertThat(redis.opsForValue().get(startKey(learner))).isEqualTo(before);
        assertThat(start(other, point, 8_000, 201).path("sessionId").asLong()).isNotEqualTo(id);

        when(clock.instant()).thenReturn(NOW.plusSeconds(7200));
        assertThat(start(learner, point, 8_000, 200).path("sessionId").asLong()).isEqualTo(id);
        when(clock.instant()).thenReturn(NOW.plusSeconds(7201));
        assertThat(start(learner, point, 8_000, 201).path("sessionId").asLong()).isNotEqualTo(id);
    }

    @Test
    void FR014_concurrentStartsForTheSameUserAndPointCreateExactlyOneSession() throws Exception {
        long learner = createUser();
        Lexeme point = grammar(explanation(10), 2);
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> concurrentStart(learner, point, ready, release));
            var second = pool.submit(() -> concurrentStart(learner, point, ready, release));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            MvcResult a = first.get(20, TimeUnit.SECONDS);
            MvcResult b = second.get(20, TimeUnit.SECONDS);
            assertThat(List.of(a.getResponse().getStatus(), b.getResponse().getStatus()))
                    .containsExactlyInAnyOrder(200, 201);
            assertThat(mapper.readTree(a.getResponse().getContentAsString()).path("sessionId"))
                    .isEqualTo(mapper.readTree(b.getResponse().getContentAsString()).path("sessionId"));
            assertThat(sessionCount(learner, point)).isEqualTo(1);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3_600_000})
    void FR009_readDurationBoundsAreAccepted(int duration) throws Exception {
        assertThat(start(owner, grammar(explanation(10), 1), duration, 201).path("sessionId").asLong()).isPositive();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"readDurationMs\":-1}", "{\"readDurationMs\":3600001}", "{}",
            "{\"readDurationMs\":null}", "{\"readDurationMs\":\"1000\"}", "{\"readDurationMs\":1.5}",
            "{\"readDurationMs\":99999999999}", "[]", "null", "not-json", "{\"readDurationMs\":1000} {}"})
    void FR009_invalidReadDurationReturns422WithoutCreatingSession(String body) throws Exception {
        Lexeme point = grammar(explanation(10), 1);
        mvc.perform(post("/api/grammar/{id}/lessons", point.getId()).header("Authorization", bearer(owner, "LEARNER"))
                        .header("X-Request-Id", "grammar-validation")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("readDurationMs"))
                .andExpect(jsonPath("$.request_id").value("grammar-validation"));
        assertThat(sessionCount(owner, point)).isZero();
    }

    @Test
    void FR009_missingBodyReturns422() throws Exception {
        mvc.perform(post("/api/grammar/{id}/lessons", grammar(explanation(10), 1).getId())
                        .header("Authorization", bearer(owner, "LEARNER")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("readDurationMs"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "999999999999999999999"})
    void FR009_invalidIdReturns422(String id) throws Exception {
        mvc.perform(post("/api/grammar/{id}/lessons", id).header("Authorization", bearer(owner, "LEARNER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"readDurationMs\":1000}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("id"));
    }

    @Test
    void FR009_nonGrammarOrMissingIdReturns404() throws Exception {
        Lexeme word = new Lexeme(LexemeKind.WORD, "词" + UUID.randomUUID(), "từ");
        word.setHskLevel((short) 1);
        lexemes.saveAndFlush(word);
        for (long id : List.of(word.getId(), Long.MAX_VALUE)) {
            mvc.perform(post("/api/grammar/{id}/lessons", id).header("Authorization", bearer(owner, "LEARNER"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"readDurationMs\":1000}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("GRAMMAR_POINT_NOT_FOUND"));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.attempts WHERE ref_type = 'GRAMMAR_LESSON' "
                + "AND ref_id = ?", Long.class, word.getId())).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void FR009_FR013_newLessonsUseTheStartQuotaIncludingLessonsWithoutExercises(int exercises) throws Exception {
        long limited = createUser();
        Lexeme point = grammar(explanation(10), exercises);
        redis.opsForValue().set(startKey(limited), "120", Duration.ofMinutes(5));
        JsonNode error = start(limited, point, 1_000, 429);
        assertThat(error.path("error_code").asText()).isEqualTo("RATE_LIMITED");
        assertThat(error.path("retryAfterSeconds").asLong()).isPositive();
        assertThat(sessionCount(limited, point)).isZero();
        assertThat(redis.opsForValue().get(startKey(limited))).isEqualTo("121");
    }

    @Test
    void FR009_onlyLearnersCanStartAndCookieWritesRequireCsrf() throws Exception {
        Lexeme point = grammar(explanation(10), 1);
        String path = "/api/grammar/" + point.getId() + "/lessons";
        String body = "{\"readDurationMs\":1000}";
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        for (String role : List.of("ADMIN", "TEACHER", "CONTENT_EDITOR", "MODERATOR", "ACCOUNTANT")) {
            mvc.perform(post(path).header("Authorization", bearer(owner, role))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        }
        Cookie auth = new Cookie("access_token", token(owner, "LEARNER"));
        mvc.perform(post(path).cookie(auth).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        Cookie csrf = mvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        mvc.perform(post(path).cookie(auth, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private long createUser() throws Exception {
        String password = "Aa1" + UUID.randomUUID();
        return mapper.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.test",
                                "password", password, "confirmPassword", password, "displayName", "Lesson test user",
                                "acceptTerms", true)))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).path("userId").asLong();
    }

    private Lexeme grammar(String explanation, int exerciseCount) {
        var exercises = new ArrayList<LexemeExtra.Exercise>();
        for (int i = 1; i <= exerciseCount; i++) {
            exercises.add(new LexemeExtra.Exercise(i, "Câu " + i + " ___", List.of(
                    new LexemeExtra.Option("A", "sai-a-" + i), new LexemeExtra.Option("B", "sai-b-" + i),
                    new LexemeExtra.Option("C", "đúng-" + i), new LexemeExtra.Option("D", "sai-d-" + i)),
                    "C", "Lời giải bí mật " + i));
        }
        Lexeme point = new Lexeme(LexemeKind.GRAMMAR, "是……的" + UUID.randomUUID(), "Nhấn mạnh thời gian");
        point.setHskLevel((short) 2);
        point.setExtra(new LexemeExtra(List.of(new LexemeExtra.Example("我是昨天来的。", "Wǒ shì zuótiān lái de.",
                "Tôi đến hôm qua.")), null, "S + 是 + … + 的", explanation, null, exercises));
        return lexemes.saveAndFlush(point);
    }

    private static String explanation(int length) {
        return "语".repeat(length);
    }

    private JsonNode start(long user, Lexeme point, int readDurationMs, int expectedStatus) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/grammar/{id}/lessons", point.getId())
                        .header("Authorization", bearer(user, "LEARNER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"readDurationMs\":" + readDurationMs + "}"))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString());
    }

    private MvcResult concurrentStart(long user, Lexeme point, CountDownLatch ready, CountDownLatch release)
            throws Exception {
        ready.countDown();
        assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
        return mvc.perform(post("/api/grammar/{id}/lessons", point.getId())
                .header("Authorization", bearer(user, "LEARNER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"readDurationMs\":6000}")).andReturn();
    }

    private JsonNode answer(long user, long id, int no, String value) throws Exception {
        var body = mapper.createObjectNode().put("no", no).put("answer", value).put("durationMs", 1_000)
                .put("skipped", false).put("audioFailed", false).put("replayCount", 0);
        return mapper.readTree(mvc.perform(post("/api/practice/sessions/{id}/answers", id)
                        .header("Authorization", bearer(user, "LEARNER")).contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode list(long user) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/grammar").header("Authorization", bearer(user, "LEARNER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static JsonNode find(JsonNode rows, long id) {
        for (JsonNode row : rows) if (row.path("id").asLong() == id) return row;
        throw new AssertionError("Grammar point missing from response: " + id);
    }

    private JsonNode served(long id) {
        return attempts.findById(id).orElseThrow().getServedItems();
    }

    private void abandon(long id) {
        jdbc.update("UPDATE learning.attempts SET status = 'ABANDONED' WHERE id = ?", id);
    }

    private long sessionCount(long user, Lexeme point) {
        return jdbc.queryForObject("SELECT count(*) FROM learning.attempts WHERE user_id = ? "
                + "AND ref_type = 'GRAMMAR_LESSON' AND ref_id = ?", Long.class, user, point.getId());
    }

    private static String startKey(long user) {
        return "practice:start:" + user + ":" + HOUR;
    }

    private static void assertSafe(JsonNode session) {
        assertThat(session.toString()).doesNotContain("answerKey", "Lời giải bí mật", "\"correctAnswer\"",
                "\"accept\"", "\"explanation\"");
        session.path("items").forEach(item -> assertThat(item.has("answer")).isFalse());
    }

    private String bearer(long user, String role) {
        return "Bearer " + token(user, role);
    }

    private String token(long user, String role) {
        return jwt.issueAccessToken(user, List.of(role), false, UUID.randomUUID());
    }
}
