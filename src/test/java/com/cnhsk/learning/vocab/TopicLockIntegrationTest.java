package com.cnhsk.learning.vocab;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.cnhsk.learning.vocab.TopicLockQuery.LockedTopicRef;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class TopicLockIntegrationTest extends AbstractIntegrationTest {
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TopicQueryService queries;
    @Autowired private TopicStudyService study;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private EntityManager entities;
    @Autowired private EntityManagerFactory factory;
    @MockitoSpyBean private TopicLockQuery locks;
    private final List<Long> users = new ArrayList<>();
    private final List<Long> topics = new ArrayList<>();
    private final List<Long> words = new ArrayList<>();
    private long owner;

    @BeforeEach void setup() { owner = user(); }
    @AfterEach void cleanup() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var named = new NamedParameterJdbcTemplate(jdbc);
            var args = Map.of("ids", users);
            named.update("DELETE FROM learning.attempt_items WHERE attempt_id IN (SELECT id FROM learning.attempts WHERE user_id IN (:ids))", args);
            named.update("DELETE FROM learning.attempts WHERE user_id IN (:ids)", args);
            named.update("DELETE FROM learning.study_events WHERE user_id IN (:ids)", args);
            named.update("DELETE FROM learning.user_progress WHERE user_id IN (:ids)", args);
            named.update("DELETE FROM shared.audit_logs WHERE actor_id IN (:ids)", args);
            if (!topics.isEmpty()) {
                named.update("DELETE FROM learning.topic_items WHERE topic_id IN (:ids)", Map.of("ids", topics));
                named.update("DELETE FROM learning.topics WHERE id IN (:ids)", Map.of("ids", topics));
            }
            if (!words.isEmpty()) named.update("DELETE FROM learning.lexemes WHERE id IN (:ids)", Map.of("ids", words));
            named.update("DELETE FROM auth.users WHERE id IN (:ids)", args);
        });
    }

    @ParameterizedTest
    @CsvSource({"89,false", "90,true", "100,true", "0,false"})
    void onlyThePassingBoundaryUnlocksDirectPrerequisites(int score, boolean open) {
        long prerequisite = topic(0);
        long target = topic(1);
        prerequisites(target, prerequisite);
        evidence(owner, prerequisite, "TOPIC_TEST", "SUBMITTED", score, 100);
        var detail = queries.detail(owner, target);
        assertThat(detail.status()).isEqualTo(open ? "NOT_STARTED" : "LOCKED");
        assertThat(detail.lockedBy()).hasSize(open ? 0 : 1);
        assertThat(detail.actions().canStudy()).isEqualTo(open);
    }

    @ParameterizedTest
    @CsvSource({"TOPIC_TEST,IN_PROGRESS,100", "TOPIC_TEST,ABANDONED,100", "TOPIC_TEST,EXPIRED,100",
            "TOPIC_TEST,SUBMITTED,0", "TOPIC_PRACTICE,SUBMITTED,100", "GRAMMAR_LESSON,SUBMITTED,100"})
    void incompleteZeroDenominatorAndOtherSourceEvidenceCannotUnlock(String source, String status, int maximum) {
        long prerequisite = topic(0);
        long target = topic(1);
        prerequisites(target, prerequisite);
        evidence(owner, prerequisite, source, status, maximum, maximum);
        assertThat(queries.detail(owner, target).status()).isEqualTo("LOCKED");
    }

    @Test
    void allPrerequisitesUseTheOwnersEvidenceAndDoNotRecursivelyInheritUiLocks() {
        long first = topic(1);
        long second = topic(1);
        long ancestor = topic(1);
        long target = topic(1);
        prerequisites(target, second, first, first, Long.MAX_VALUE);
        evidence(owner, first, "TOPIC_TEST", "SUBMITTED", 90, 100);
        evidence(user(), second, "TOPIC_TEST", "SUBMITTED", 100, 100);
        evidence(owner, target, "TOPIC_TEST", "SUBMITTED", 100, 100);
        var locked = queries.detail(owner, target);
        assertThat(locked.status()).isEqualTo("LOCKED");
        assertThat(locked.bestTestPercent()).isEqualTo(100);
        assertThat(locked.lockedBy()).containsExactly(new LockedTopicRef(second, name(second)));
        assertThat(locked.actions().canStudy()).isFalse();
        assertThat(locked.actions().canPracticeRecognition()).isFalse();
        assertThat(locked.actions().canPracticeListening()).isFalse();
        assertThat(locked.actions().canTakeTest()).isFalse();
        assertThat(locked.actions().testBlockedReason()).isEqualTo("TOPIC_LOCKED");
        assertThat(locked.actions().retryAvailableAt()).isNull();
        assertThat(locked.actions().requiredWords()).isEqualTo(1);
        evidence(owner, second, "TOPIC_TEST", "SUBMITTED", 90, 100);
        evidence(owner, second, "TOPIC_TEST", "SUBMITTED", 10, 100);
        prerequisites(second, ancestor);
        assertThat(queries.detail(owner, second).status()).isEqualTo("LOCKED");
        assertThat(queries.detail(owner, target).status()).isEqualTo("COMPLETED");
        assertThat(queries.detail(owner, target).lockedBy()).isEmpty();
        var blockedBy = queries.detail(user(), target).lockedBy();
        assertThat(blockedBy).extracting(LockedTopicRef::id).containsExactly(first, second);
    }

    @ParameterizedTest
    @ValueSource(strings = {"study-sessions", "practice-sessions", "tests"})
    void aBlockedNewStartReturnsThePrerequisitesAndCreatesNoAttempt(String suffix) throws Exception {
        long prerequisite = topic(0);
        long target = topic(1);
        prerequisites(target, prerequisite);
        evidence(user(), target, "TOPIC_STUDY", "IN_PROGRESS", 0, 0);
        for (String mode : suffix.equals("practice-sessions") ? List.of("RECOGNITION", "LISTENING") : List.of("RECOGNITION")) {
            var response = start(target, suffix, mode, 422);
            assertThat(response.path("error_code").asText()).isEqualTo("TOPIC_LOCKED");
            assertThat(response.path("details").path("lockedBy").get(0).path("id").asLong()).isEqualTo(prerequisite);
            assertThat(response.path("details").path("lockedBy").get(0).path("name").asText()).isEqualTo(name(prerequisite));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.attempts WHERE user_id=?", Long.class, owner)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOPIC_STUDY", "TOPIC_PRACTICE", "TOPIC_TEST"})
    void runningSessionsRemainReadableAnswerableAndSubmittableAfterTheTopicIsLocked(String source) throws Exception {
        long target = topic(5);
        learned(target, 4);
        String suffix = suffix(source);
        JsonNode session = start(target, suffix, "RECOGNITION", 201);
        long id = session.path("sessionId").asLong();
        String snapshot = jdbc.queryForObject("SELECT served_items::text FROM learning.attempts WHERE id=?", String.class, id);
        prerequisites(target, topic(0));
        assertThat(queries.detail(owner, target).status()).isEqualTo("LOCKED");
        clearInvocations(locks);
        if (!source.equals("TOPIC_PRACTICE")) assertThat(start(target, suffix, "RECOGNITION", 200).path("sessionId").asLong()).isEqualTo(id);
        mvc.perform(get("/api/practice/sessions/{id}", id).header("Authorization", bearer(owner))).andExpect(status().isOk());
        mvc.perform(get("/api/practice/sessions/active").param("source", source).param("refId", Long.toString(target))
                .header("Authorization", bearer(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value(id));
        verify(locks, never()).requireOpen(anyLong(), anyLong());
        mvc.perform(get("/api/practice/sessions/{id}", id).header("Authorization", bearer(user())))
                .andExpect(status().isForbidden());
        if (source.equals("TOPIC_PRACTICE")) start(target, suffix, "RECOGNITION", 422);
        JsonNode stored = mapper.readTree(snapshot).path("items");
        String answer = answer(stored.get(0));
        mvc.perform(post("/api/practice/sessions/{id}/answers", id).header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("no", 1, "answer", answer, "durationMs", 1500))))
                .andExpect(status().isOk());
        finish(id, stored);
        assertThat(jdbc.queryForObject("SELECT status FROM learning.attempts WHERE id=?", String.class, id)).isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject("SELECT served_items::text FROM learning.attempts WHERE id=?", String.class, id)).isEqualTo(snapshot);
        start(target, suffix, "RECOGNITION", 422);
    }

    @ParameterizedTest
    @ValueSource(strings = {"study-sessions", "tests"})
    void expiredSessionsCannotBypassThePrerequisiteGate(String suffix) throws Exception {
        long target = topic(5);
        learned(target, 4);
        long id = start(target, suffix, "RECOGNITION", 201).path("sessionId").asLong();
        jdbc.update("UPDATE learning.attempts SET expires_at=now()-interval '1 second' WHERE id=?", id);
        prerequisites(target, topic(0));
        assertThat(start(target, suffix, "RECOGNITION", 422).path("error_code").asText()).isEqualTo("TOPIC_LOCKED");
    }

    @Test
    void finishingAPrerequisiteTestUnlocksTheNextTopicWithoutRestartingOrLosingProgress() throws Exception {
        long prerequisite = topic(5);
        long target = topic(2);
        learned(prerequisite, 4);
        learned(target, 1);
        prerequisites(target, prerequisite);
        var before = queries.detail(owner, target);
        assertThat(before.status()).isEqualTo("LOCKED");
        long id = start(prerequisite, "tests", "RECOGNITION", 201).path("sessionId").asLong();
        JsonNode stored = mapper.readTree(jdbc.queryForObject("SELECT served_items::text FROM learning.attempts WHERE id=?", String.class, id)).path("items");
        finish(id, stored);
        var after = queries.detail(owner, target);
        assertThat(after.status()).isEqualTo("IN_PROGRESS");
        assertThat(after.learnedWords()).isEqualTo(before.learnedWords()).isEqualTo(1);
        assertThat(after.lockedBy()).isEmpty();
        start(target, "study-sessions", "RECOGNITION", 201);
    }

    @Test
    void fiveAndFiftyTopicsWithPrerequisitesStillUseExactlyOneListStatement() {
        long prerequisite = topic(1);
        var stats = factory.unwrap(SessionFactory.class).getStatistics();
        boolean enabled = stats.isStatisticsEnabled();
        try {
            stats.setStatisticsEnabled(true);
            for (int count : new int[]{5, 50}) {
                while (topics.size() < count + 1) prerequisites(topic(1), prerequisite);
                new TransactionTemplate(transactions).executeWithoutResult(status -> {
                    entities.flush(); entities.clear(); stats.clear();
                    long start = System.nanoTime();
                    var result = queries.list(owner, null);
                    long duration = System.nanoTime() - start;
                    assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
                    assertThat(result.stream().filter(row -> topics.contains(row.id()) && row.id() != prerequisite).toList())
                            .hasSize(count).allSatisfy(row -> assertThat(row.status()).isEqualTo("LOCKED"));
                    System.out.println("Topic locks: topics=" + count + ", queries=1, elapsed ms=" + duration / 1_000_000.0);
                });
            }
        } finally { stats.setStatisticsEnabled(enabled); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void prerequisiteWritersAndNewStartsSerializeThroughTheTopicRow(boolean writerFirst) throws Exception {
        long prerequisite = topic(0);
        long target = topic(1);
        Callable<?> writer = () -> { prerequisites(target, prerequisite); return null; };
        Callable<?> starter = () -> study.start(owner, target);
        assertThat(serialized(writerFirst ? writer : starter, writerFirst ? starter : writer))
                .containsExactly("OK", writerFirst ? "TOPIC_LOCKED" : "OK");
        assertThat(queries.detail(owner, target).status()).isEqualTo("LOCKED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.attempts WHERE user_id=? AND ref_id=?", Long.class, owner, target))
                .isEqualTo(writerFirst ? 0 : 1);
        if (!writerFirst) assertThat(study.start(owner, target).created()).isFalse();
    }

    private List<String> serialized(Callable<?> first, Callable<?> second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var firstWritten = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var secondEntered = new CountDownLatch(1);
        var pid = new AtomicInteger();
        try {
            var one = executor.submit(() -> outcome(() -> new TransactionTemplate(transactions).execute(status -> {
                invoke(first); firstWritten.countDown(); await(release); return null;
            })));
            assertThat(firstWritten.await(10, TimeUnit.SECONDS)).isTrue();
            var two = executor.submit(() -> outcome(() -> new TransactionTemplate(transactions).execute(status -> {
                pid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class)); secondEntered.countDown(); return invoke(second);
            })));
            assertThat(secondEntered.await(10, TimeUnit.SECONDS)).isTrue();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            boolean blocked;
            do {
                blocked = jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE pid=? AND NOT granted", Integer.class, pid.get()) > 0;
                if (!blocked) Thread.sleep(10);
            } while (!blocked && System.nanoTime() < deadline);
            assertThat(blocked).as("second transaction waits for the topic row").isTrue();
            release.countDown();
            return List.of(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS));
        } finally { release.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue(); }
    }

    private long user() {
        long id = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,'Topic lock fixture') RETURNING id",
                Long.class, UUID.randomUUID() + "@example.test", PASSWORD_HASH);
        users.add(id); return id;
    }
    private long topic(int count) {
        String code = "LOCK-" + UUID.randomUUID();
        long id = jdbc.queryForObject("INSERT INTO learning.topics(code,name,hsk_level) VALUES (?,?,1) RETURNING id", Long.class, code, code);
        topics.add(id);
        for (int index = 0; index < count; index++) {
            String text = "词" + UUID.randomUUID();
            long word = jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,pinyin,pinyin_norm,meaning_vi,meaning_norm,hsk_level,audio_url) "
                    + "VALUES ('WORD',?,'cí','ci2',?,?,1,'https://example.test/audio.mp3') RETURNING id", Long.class, text, text, text);
            words.add(word);
            jdbc.update("INSERT INTO learning.topic_items(topic_id,lexeme_id,order_index) VALUES (?,?,?)", id, word, index);
        }
        return id;
    }
    private String name(long id) { return jdbc.queryForObject("SELECT name FROM learning.topics WHERE id=?", String.class, id); }
    private void prerequisites(long id, long... ids) {
        jdbc.update("UPDATE learning.topics SET prereq_ids=CAST(? AS bigint[]) WHERE id=?",
                "{" + java.util.Arrays.stream(ids).mapToObj(Long::toString).collect(java.util.stream.Collectors.joining(",")) + "}", id);
    }
    private long evidence(long user, long topic, String source, String status, long score, long max) {
        return jdbc.queryForObject("INSERT INTO learning.attempts(user_id,kind,ref_type,ref_id,status,score,max_score,submitted_at,expires_at) "
                + "VALUES (?,?,?,?,?,?,?,CASE WHEN ?='SUBMITTED' THEN now() ELSE NULL END,now()+interval '1 day') RETURNING id",
                Long.class, user, source.equals("TOPIC_TEST") ? "TOPIC_TEST" : "PRACTICE", source, topic, status, score, max, status);
    }
    private void learned(long topic, int count) {
        long attempt = evidence(owner, topic, "TOPIC_STUDY", "SUBMITTED", 0, 0);
        var ids = jdbc.queryForList("SELECT lexeme_id FROM learning.topic_items WHERE topic_id=? ORDER BY order_index LIMIT ?", Long.class, topic, count);
        for (int i = 0; i < ids.size(); i++) jdbc.update("INSERT INTO learning.attempt_items(attempt_id,lexeme_id,item_type,skill,order_index,answer,duration_ms) "
                + "VALUES (?,?,'STUDY_CARD','VOCAB',?,'KNOWN',1500)", attempt, ids.get(i), i + 1);
    }
    private JsonNode start(long topic, String suffix, String mode, int expected) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/topics/{id}/" + suffix, topic).header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("mode", mode))))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsByteArray());
    }
    private void finish(long id, JsonNode stored) throws Exception {
        var answers = mapper.createArrayNode();
        String source = jdbc.queryForObject("SELECT ref_type FROM learning.attempts WHERE id=?", String.class, id);
        if (source.equals("TOPIC_TEST")) for (JsonNode item : stored)
            answers.addObject().put("no", item.path("no").asInt()).put("answer", answer(item)).put("durationMs", 1500);
        mvc.perform(post("/api/practice/sessions/{id}/finish", id).header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.createObjectNode().set("answers", answers).toString())).andExpect(status().isOk());
    }
    private String answer(JsonNode item) {
        if (item.path("type").asText().equals("STUDY_CARD")) return "KNOWN";
        return item.hasNonNull("answer") ? item.path("answer").asText() : item.path("accept").get(0).asText();
    }
    private String suffix(String source) { return switch (source) { case "TOPIC_STUDY" -> "study-sessions"; case "TOPIC_TEST" -> "tests"; default -> "practice-sessions"; }; }
    private String bearer(long user) { return "Bearer " + jwt.issueAccessToken(user, List.of("LEARNER"), false, UUID.randomUUID()); }
    private String outcome(Callable<?> action) throws Exception { try { action.call(); return "OK"; } catch (ApiException exception) { return exception.getCode().name(); } }
    private Object invoke(Callable<?> action) { try { return action.call(); } catch (RuntimeException exception) { throw exception; } catch (Exception exception) { throw new IllegalStateException(exception); } }
    private void await(CountDownLatch signal) { try { assertThat(signal.await(10, TimeUnit.SECONDS)).isTrue(); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); } }
}
