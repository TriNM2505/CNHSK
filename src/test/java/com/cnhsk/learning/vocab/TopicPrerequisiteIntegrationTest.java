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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.cnhsk.learning.content.repository.TopicRepository;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
class TopicPrerequisiteIntegrationTest extends TopicPrerequisiteFixture {
    @Autowired private TopicRepository topicsRepository;
    @Autowired private TopicQueryService learner;
    @MockitoSpyBean private AuditService audit;
    @MockitoSpyBean private NamedParameterJdbcTemplate tracedJdbc;

    @Test void listingFiltersPaginatesAndLoadsPrerequisitesOutsideTheTargetFilter() throws Exception {
        long target = topic(1, 0);
        long higher = topic(9, 0);
        long second = topic(1, 0);
        jdbc.update("UPDATE learning.topics SET name=?,order_index=10 WHERE id=?", prefix + " Việt%_!", target);
        jdbc.update("UPDATE learning.topics SET name=?,order_index=5 WHERE id=?", prefix + " Việt%_! hai", second);
        rawPrerequisites(target, List.of(higher, Long.MAX_VALUE));
        JsonNode page = mapper.readTree(mvc.perform(get("/api/admin/topics").header("Authorization", editor())
                .param("hsk", "1").param("q", prefix.toLowerCase() + " việt%_!").param("size", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(page.path("totalElements").asLong()).isEqualTo(2);
        assertThat(page.path("items").get(0).path("id").asLong()).isEqualTo(second);
        JsonNode next = mapper.readTree(mvc.perform(get("/api/admin/topics").header("Authorization", editor())
                .param("hsk", "1").param("q", prefix + " Việt%_!").param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        JsonNode item = next.path("items").get(0);
        assertThat(item.path("id").asLong()).isEqualTo(target);
        assertThat(item.path("isRoot").asBoolean()).isFalse();
        assertThat(item.path("prereqs")).hasSize(1);
        assertThat(item.path("prereqs").get(0).path("hskLevel").asInt()).isEqualTo(9);
        assertThat(item.has("warnings")).isFalse();
        mvc.perform(get("/api/admin/topics").header("Authorization", editor()).param("q", prefix).param("page", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test void missingLegacyIdsAreEffectiveRootsAndSuccessfulWritesCleanTheTarget() throws Exception {
        long target = topic(1, 0);
        long prerequisite = topic(1, 0);
        rawPrerequisites(target, List.of(Long.MAX_VALUE));
        var page = service.list(null, prefix, 0, 50);
        var legacy = page.items().stream().filter(row -> row.id() == target).findFirst().orElseThrow();
        assertThat(legacy.isRoot()).isTrue();
        assertThat(legacy.prereqs()).isEmpty();
        assertThat(put(target, List.of(prerequisite), 200).path("isRoot").asBoolean()).isFalse();
        assertThat(prerequisites(target)).containsExactly(prerequisite);
        assertThat(put(target, List.of(), 200).path("isRoot").asBoolean()).isTrue();
        assertThat(prerequisites(target)).isEmpty();
    }

    @Test void validWritesPreserveOrderWarnAndAuditEvenWhenTheListDoesNotChange() throws Exception {
        long target = topic(1, 0);
        long high = topic(3, 0);
        long same = topic(1, 0);
        JsonNode result = put(target, List.of(high, same), 200);
        assertThat(result.path("warnings")).hasSize(1);
        assertThat(result.path("warnings").get(0).asText()).contains("HSK 3", "HSK 1");
        assertThat(result.path("prereqs").get(0).path("id").asLong()).isEqualTo(high);
        put(target, List.of(high, same), 200);
        assertThat(prerequisites(target)).containsExactly(high, same);
        var details = jdbc.queryForList("SELECT detail::text FROM shared.audit_logs WHERE actor_id=? AND target_type='TOPIC' AND target_id=? AND action='TOPIC_PREREQ_UPDATE' ORDER BY id", String.class, owner, target);
        assertThat(details).hasSize(2);
        assertThat(mapper.readTree(details.get(0)).path("before")).isEmpty();
        assertIds(mapper.readTree(details.get(0)).path("after"), List.of(high, same));
        assertIds(mapper.readTree(details.get(1)).path("before"), List.of(high, same));
        assertIds(mapper.readTree(details.get(1)).path("after"), List.of(high, same));
        assertThat(put(target, List.of(same), 200).path("warnings")).isEmpty();
    }

    @Test void nonexistentTargetsAndEveryNonexistentNewPrerequisiteReturn404WithoutWriting() throws Exception {
        long target = topic(1, 0);
        long known = topic(1, 0);
        put(Long.MAX_VALUE, List.of(), 404);
        for (long missing : new long[]{Long.MAX_VALUE, 0, -1, Long.MIN_VALUE}) {
            assertThat(put(target, List.of(known, missing), 404).path("error_code").asText()).isEqualTo("TOPIC_NOT_FOUND");
            assertThat(prerequisites(target)).isEmpty();
        }
        assertThat(audits()).isZero();
    }

    @Test void selfTwoAndThreeStepCyclesAreRejectedAndTheWholeGraphIsChecked() throws Exception {
        long first = topic(1, 0);
        long second = topic(1, 0);
        long third = topic(1, 0);
        assertIds(put(first, List.of(first), 422).path("details").path("path"), List.of(first, first));
        put(first, List.of(second), 200);
        assertThat(put(second, List.of(first), 422).path("error_code").asText()).isEqualTo("PREREQUISITE_CYCLE");
        put(second, List.of(third), 200);
        var cycle = put(third, List.of(first), 422).path("details").path("path");
        assertIds(cycle, List.of(first, second, third, first));
        assertThat(prerequisites(third)).isEmpty();
        assertThat(audits()).isEqualTo(2);
        rawPrerequisites(third, List.of(first));
        long disconnected = topic(1, 0);
        assertThat(put(disconnected, List.of(), 422).path("error_code").asText()).isEqualTo("PREREQUISITE_CYCLE");
        assertThat(audits()).isEqualTo(2);
    }

    @Test void auditFailureRollsBackThePrerequisiteUpdate() throws Exception {
        long target = topic(1, 0);
        long prerequisite = topic(1, 0);
        doThrow(new IllegalStateException("Simulated audit failure")).when(audit)
                .recordDetail(eq(owner), eq("TOPIC_PREREQ_UPDATE"), eq("TOPIC"), eq(target), anyMap());
        put(target, List.of(prerequisite), 500);
        assertThat(prerequisites(target)).isEmpty();
        assertThat(audits()).isZero();
    }

    @ParameterizedTest @CsvSource({"hsk,0", "hsk,10", "page,-1", "size,0", "size,51"})
    void queryBoundsAreValidatedByTheRealService(String parameter, String value) throws Exception {
        mvc.perform(get("/api/admin/topics").header("Authorization", editor()).param(parameter, value))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
    }

    @Test void overlyLongSearchIsRejectedAndEmptyResultKeepsPaginationMetadata() throws Exception {
        mvc.perform(get("/api/admin/topics").header("Authorization", editor()).param("q", "a".repeat(101)))
                .andExpect(status().isUnprocessableEntity());
        var result = service.list(null, prefix, 3, 7);
        assertThat(result.items()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.page()).isEqualTo(3);
        assertThat(result.size()).isEqualTo(7);
    }

    @Test void fiveAndFiftyTopicsUseTheSameThreeJdbcStatements() {
        long prerequisite = topic(1, 0);
        for (int i = 0; i < 50; i++) rawPrerequisites(topic(1, 0), List.of(prerequisite));
        for (int size : new int[]{5, 50}) {
            clearInvocations(tracedJdbc);
            assertThat(service.list(1, prefix, 0, size).items()).hasSize(size);
            long count = mockingDetails(tracedJdbc).getInvocations().stream()
                    .filter(call -> call.getMethod().getName().equals("query") || call.getMethod().getName().equals("queryForObject"))
                    .filter(call -> call.getMethod().getParameterCount() == 3
                            && call.getMethod().getParameterTypes()[1] == org.springframework.jdbc.core.namedparam.SqlParameterSource.class
                            && call.getMethod().getParameterTypes()[2] == org.springframework.jdbc.core.RowMapper.class).count();
            assertThat(count).isEqualTo(3);
        }
    }

    @Test void aRealPutLocksAndUnlocksLearningWithoutLosingProgressOrBlockingResume() throws Exception {
        long target = topic(1, 2);
        long prerequisite = topic(1, 0);
        long attempt = study.start(owner, target).session().sessionId();
        mvc.perform(post("/api/practice/sessions/{id}/answers", attempt).header("Authorization", learner())
                .contentType(MediaType.APPLICATION_JSON).content("{\"no\":1,\"answer\":\"KNOWN\",\"durationMs\":1500}"))
                .andExpect(status().isOk());
        long learned = learner.detail(owner, target).learnedWords();
        assertThat(learned).isPositive();
        put(target, List.of(prerequisite), 200);
        assertThat(learner.detail(owner, target).status()).isEqualTo("LOCKED");
        assertThat(learner.detail(owner, target).learnedWords()).isEqualTo(learned);
        mvc.perform(post("/api/topics/{id}/study-sessions", target).header("Authorization", learner()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value(attempt));
        mvc.perform(get("/api/practice/sessions/{id}", attempt).header("Authorization", learner())).andExpect(status().isOk());
        put(target, List.of(), 200);
        assertThat(learner.detail(owner, target).status()).isEqualTo("IN_PROGRESS");
        assertThat(learner.detail(owner, target).learnedWords()).isEqualTo(learned);
    }

    private static void assertIds(JsonNode node, List<Long> expected) {
        assertThat(node.isArray()).isTrue();
        node.forEach(value -> {
            assertThat(value.isIntegralNumber()).isTrue();
            assertThat(value.canConvertToLong()).isTrue();
        });
        var actual = java.util.stream.StreamSupport.stream(node.spliterator(), false).map(JsonNode::asLong).toList();
        assertThat(actual).containsExactlyElementsOf(expected);
    }

    @Test void importerStyleSaveOfAnOlderManagedTopicKeepsCommittedPrerequisites() {
        long target = topic(1, 0);
        long prerequisite = topic(1, 0);
        var pool = Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                var stale = topicsRepository.findById(target).orElseThrow();
                assertThat(stale.getPrereqIds()).isEmpty();
                try { pool.submit(() -> put(target, List.of(prerequisite), 200)).get(20, TimeUnit.SECONDS); }
                catch (Exception exception) { throw new IllegalStateException(exception); }
                assertThat(stale.getPrereqIds()).isEmpty();
                stale.setName(prefix + " imported");
                topicsRepository.saveAndFlush(stale);
            });
            assertThat(prerequisites(target)).containsExactly(prerequisite);
            assertThat(jdbc.queryForObject("SELECT name FROM learning.topics WHERE id=?", String.class, target)).isEqualTo(prefix + " imported");
            assertThat(audits()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
}

abstract class TopicPrerequisiteFixture extends AbstractIntegrationTest {
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected JwtService jwt;
    @Autowired protected PlatformTransactionManager transactions;
    @Autowired protected TopicPrerequisiteService service;
    @Autowired protected TopicStudyService study;
    protected final List<Long> topics = new ArrayList<>();
    protected final List<Long> words = new ArrayList<>();
    protected final String prefix = "PR-" + UUID.randomUUID();
    protected long owner;

    @BeforeEach void createActor() {
        owner = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,'Prerequisite fixture') RETURNING id",
                Long.class, UUID.randomUUID() + "@example.test", PASSWORD_HASH);
    }

    @AfterEach void cleanupOwnedRows() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("DELETE FROM learning.attempt_items WHERE attempt_id IN (SELECT id FROM learning.attempts WHERE user_id=?)", owner);
            jdbc.update("DELETE FROM learning.attempts WHERE user_id=?", owner);
            jdbc.update("DELETE FROM learning.study_events WHERE user_id=?", owner);
            jdbc.update("DELETE FROM learning.user_progress WHERE user_id=?", owner);
            jdbc.update("DELETE FROM shared.audit_logs WHERE actor_id=?", owner);
            var named = new NamedParameterJdbcTemplate(jdbc);
            if (!topics.isEmpty()) {
                named.update("DELETE FROM learning.topic_items WHERE topic_id IN (:ids)", Map.of("ids", topics));
                named.update("DELETE FROM learning.topics WHERE id IN (:ids)", Map.of("ids", topics));
            }
            if (!words.isEmpty()) named.update("DELETE FROM learning.lexemes WHERE id IN (:ids)", Map.of("ids", words));
            jdbc.update("DELETE FROM auth.users WHERE id=?", owner);
        });
    }

    protected long topic(int hsk, int wordCount) {
        long id = jdbc.queryForObject("INSERT INTO learning.topics(code,name,hsk_level) VALUES (?,?,?) RETURNING id",
                Long.class, "PR-" + UUID.randomUUID(), prefix + "-" + topics.size(), hsk);
        topics.add(id);
        for (int i = 0; i < wordCount; i++) {
            String text = "词" + UUID.randomUUID();
            long word = jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,pinyin,pinyin_norm,meaning_vi,meaning_norm,hsk_level) "
                    + "VALUES ('WORD',?,'cí','ci2',?,?,1) RETURNING id", Long.class, text, text, text);
            words.add(word);
            jdbc.update("INSERT INTO learning.topic_items(topic_id,lexeme_id,order_index) VALUES (?,?,?)", id, word, i);
        }
        return id;
    }

    protected void rawPrerequisites(long id, List<Long> ids) {
        String array = ids.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(",", "{", "}"));
        jdbc.update("UPDATE learning.topics SET prereq_ids=CAST(? AS bigint[]) WHERE id=?", array, id);
    }
    protected List<Long> prerequisites(long id) { return jdbc.queryForList("SELECT unnest(prereq_ids) FROM learning.topics WHERE id=?", Long.class, id); }
    protected long audits() { return jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs WHERE actor_id=? AND action='TOPIC_PREREQ_UPDATE'", Long.class, owner); }
    protected String editor() { return "Bearer " + jwt.issueAccessToken(owner, List.of("CONTENT_EDITOR"), true, UUID.randomUUID()); }
    protected String learner() { return "Bearer " + jwt.issueAccessToken(owner, List.of("LEARNER"), true, UUID.randomUUID()); }
    protected JsonNode put(long id, List<Long> ids, int expected) throws Exception {
        return mapper.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/topics/{id}/prerequisites", id).header("Authorization", editor())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("prereqIds", ids))))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsByteArray());
    }
}
