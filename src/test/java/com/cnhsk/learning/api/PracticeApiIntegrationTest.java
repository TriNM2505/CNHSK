package com.cnhsk.learning.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

import com.cnhsk.learning.api.PracticeApi.Answer;
import com.cnhsk.learning.api.PracticeApi.ItemType;
import com.cnhsk.learning.api.PracticeApi.PracticeSource;
import com.cnhsk.learning.api.PracticeApi.ServedItem;
import com.cnhsk.learning.api.PracticeApi.StartPracticeCommand;
import com.cnhsk.learning.practice.PracticeSourceHandler;
import com.cnhsk.learning.practice.entity.AttemptItemRepository;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.learning.practice.event.ItemGradedEvent;
import com.cnhsk.learning.practice.event.SessionFinishedEvent;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@AutoConfigureMockMvc
@Import(PracticeApiIntegrationTest.EventConfiguration.class)
class PracticeApiIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-30T23:30:00Z");
    @Autowired private PracticeApi api;
    @Autowired private AttemptRepository attempts;
    @Autowired private AttemptItemRepository items;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EventProbe probe;
    @MockitoBean private Clock clock;
    @MockitoBean(name = "stubPracticeSourceHandler") private PracticeSourceHandler handler;
    private long owner;

    @BeforeEach
    void setup() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        when(handler.source()).thenReturn(com.cnhsk.learning.practice.PracticeSource.TOPIC_TEST);
        when(handler.onFinish(org.mockito.ArgumentMatchers.any())).thenReturn(Map.of("sourceFinished", true));
        probe.clear();
        owner = createUser();
    }

    @ParameterizedTest
    @EnumSource(value = PracticeSource.class, names = {"TOPIC_STUDY", "TOPIC_PRACTICE", "TOPIC_TEST"})
    void FR012_AT01_startAndRestoreOnlyExposePublicDataAndBindTheCaller(PracticeSource source) {
        var view = start(source, 1);
        var stored = attempts.findById(view.sessionId()).orElseThrow();
        assertThat(stored.getUserId()).isEqualTo(owner);
        assertThat(stored.getServedItems().path("items").get(0).path("answer").asText()).isEqualTo("A");
        for (var response : List.of(view, api.view(owner, view.sessionId()))) {
            assertThat(response.source()).isEqualTo(source);
            assertThat(response.status()).isEqualTo("IN_PROGRESS");
            assertThat(response.expiresAt()).isEqualTo(NOW.plus(source == PracticeSource.TOPIC_TEST
                    ? Duration.ofHours(24) : Duration.ofHours(2)));
            JsonNode json = mapper.valueToTree(response);
            assertNoServerSecrets(json.path("items"));
            assertNoServerSecrets(json.path("meta"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"view", "answer", "finish"})
    void FR012_AT02_missingAndUnownedSessionsHaveIdenticalOwnershipErrors(String operation) throws Exception {
        long session = start(PracticeSource.TOPIC_TEST, 1).sessionId();
        long other = createUser();
        ApiException unowned = assertThrows(ApiException.class, () -> call(operation, other, session));
        ApiException missing = assertThrows(ApiException.class, () -> call(operation, other, Long.MAX_VALUE));
        assertThat(unowned.getStatus().value()).isEqualTo(403);
        assertThat(missing.getStatus()).isEqualTo(unowned.getStatus());
        assertThat(unowned.getCode()).isEqualTo(ErrorCode.ATTEMPT_NOT_OWNED);
        assertThat(missing.getCode()).isEqualTo(unowned.getCode());
        assertThat(missing.getMessage()).isEqualTo(unowned.getMessage());
        assertThat(api.view(owner, session).status()).isEqualTo("IN_PROGRESS");
        assertThat(items.findByAttemptIdOrderByOrderIndexAsc(session)).isEmpty();
        assertThat(studyEventCount()).isZero();
        assertThat(probe.graded).isEmpty();
        assertThat(probe.finished).isEmpty();
    }

    @Test
    void FR012_AT12_deferredEditsRestoreTheLatestAnswerWithoutFeedbackOrSecrets() {
        long session = start(PracticeSource.TOPIC_TEST, 1).sessionId();
        api.answer(owner, session, answer(1, "B"));
        JsonNode accepted = mapper.valueToTree(api.answer(owner, session, answer(1, "A")));
        assertThat(accepted.size()).isEqualTo(2);
        assertThat(accepted.path("accepted").asBoolean()).isTrue();
        JsonNode restored = mapper.valueToTree(api.view(owner, session));
        assertThat(restored.path("answered").get(0).path("answer").asText()).isEqualTo("A");
        assertNoServerSecrets(restored.path("items"));
        for (String field : List.of("correct", "correctAnswer", "accept", "explanation")) {
            assertThat(accepted.has(field)).as(field).isFalse();
            assertThat(restored.path("answered").get(0).has(field)).as(field).isFalse();
        }
        assertThat(attempts.findById(session).orElseThrow().getDraftAnswers().path("1").path("answer").asText())
                .isEqualTo("A");
        assertThat(items.findByAttemptIdOrderByOrderIndexAsc(session)).isEmpty();
        assertThat(probe.graded).isEmpty();
    }

    @Test
    void FR012_AT14_AT17_finishUsesServerScoresAndPublishesExactlyTheEnginesEvents() {
        long session = start(PracticeSource.TOPIC_TEST, 20).sessionId();
        var answers = IntStream.rangeClosed(1, 15).mapToObj(no -> answer(no, no <= 12 ? "A" : "B")).toList();
        var result = api.finish(owner, session, answers);
        assertThat(result.score()).isEqualTo(12);
        assertThat(result.maxScore()).isEqualTo(20);
        assertThat(result.percent()).isEqualTo(60);
        assertThat(result.status()).isEqualTo("SUBMITTED");
        assertThat(result.items()).hasSize(20);
        assertThat(result.extra()).containsEntry("sourceFinished", true);
        assertThat(items.findByAttemptIdOrderByOrderIndexAsc(session))
                .filteredOn(item -> Boolean.TRUE.equals(item.getMeta().get("missing"))).hasSize(5);
        assertThat(studyEventCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select local_date from learning.study_events where user_id = ?",
                LocalDate.class, owner)).isEqualTo(LocalDate.parse("2026-10-31"));
        assertThat(probe.graded).hasSize(20).allSatisfy(event -> assertThat(event.userId()).isEqualTo(owner));
        assertThat(probe.finished).hasSize(1);
        assertThat(probe.finished.get(0).score()).isEqualTo(result.score());
        assertThat(probe.allTransactional).isTrue();
        assertThat(api.view(owner, session).status()).isEqualTo("SUBMITTED");
    }

    @Test
    void FR012_AT01_recordPreRecordedEvidenceRemainsUnscoredAndOwnedByTheCaller() {
        var command = new StartPracticeCommand(owner, PracticeSource.TOPIC_STUDY, 1L, null,
                List.of(card(1)), List.of(answer(1, "KNOWN")), null, Map.of());
        var started = api.start(command);
        assertThat(started.answered()).hasSize(1);
        assertThat(started.answered().get(0).answer()).isEqualTo("KNOWN");
        assertThat(started.answered().get(0).correct()).isNull();
        var result = api.finish(owner, started.sessionId(), null);
        assertThat(result.score()).isNull();
        assertThat(result.maxScore()).isNull();
        assertThat(result.percent()).isNull();
        assertThat(probe.graded).hasSize(1);
        assertThat(probe.finished).hasSize(1);
    }

    @Test
    void FR012_immediateAnswerIsGradedByTheEngineAndCannotBeSubmittedTwice() {
        long session = start(PracticeSource.TOPIC_PRACTICE, 1).sessionId();
        var result = api.answer(owner, session, answer(1, "B"));
        assertThat(result.correct()).isFalse();
        assertThat(result.correctAnswer()).isEqualTo("A");
        assertThat(result.counted()).isTrue();
        assertThat(result.explanation()).isEqualTo("server explanation");
        assertThat(assertThrows(ApiException.class, () -> api.answer(owner, session, answer(1, "A"))).getCode())
                .isEqualTo(ErrorCode.ANSWER_ALREADY_SUBMITTED);
        var finished = api.finish(owner, session, List.of());
        assertThat(finished.score()).isZero();
        assertThat(finished.maxScore()).isEqualTo(1);
        assertThat(probe.graded).hasSize(1);
        assertThat(probe.finished).hasSize(1);
        assertThat(probe.allTransactional).isTrue();
    }

    @Test
    void FR012_AT17_listenerFailureRollsBackFinishAndKeepsThePreviousDraft() {
        long session = start(PracticeSource.TOPIC_TEST, 1).sessionId();
        api.answer(owner, session, answer(1, "A"));
        probe.rejectFinish = true;
        assertThrows(IllegalStateException.class, () -> api.finish(owner, session, List.of(answer(1, "B"))));
        var restored = api.view(owner, session);
        assertThat(restored.status()).isEqualTo("IN_PROGRESS");
        assertThat(restored.answered().get(0).answer()).isEqualTo("A");
        assertThat(attempts.findById(session).orElseThrow().getScore()).isNull();
        assertThat(items.findByAttemptIdOrderByOrderIndexAsc(session)).isEmpty();
        assertThat(studyEventCount()).isZero();
        probe.clear();
        assertThat(api.finish(owner, session, null).score()).isEqualTo(1);
        assertThat(probe.finished).hasSize(1);
    }

    @Test
    void FR012_expiryErrorRetainsTheEnginesCommitBeforeTheException() {
        long session = start(PracticeSource.TOPIC_TEST, 1).sessionId();
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofHours(25)));
        ApiException failure = assertThrows(ApiException.class, () -> api.answer(owner, session, answer(1, "A")));
        assertThat(failure.getCode()).isEqualTo(ErrorCode.SESSION_EXPIRED);
        assertThat(failure.getStatus().value()).isEqualTo(422);
        assertThat(attempts.findById(session).orElseThrow().getStatus()).isEqualTo("EXPIRED");
        assertThat(api.view(owner, session).status()).isEqualTo("EXPIRED");
    }

    @Test
    void FR012_gameInputPreservesServerLifetimeAndScoringPolicy() {
        var view = api.start(new StartPracticeCommand(owner, PracticeSource.GAME_ROUND, null, "test-game",
                List.of(choice(1)), null, Duration.ofSeconds(45), Map.of("roundSeconds", 30)));
        assertThat(view.expiresAt()).isEqualTo(NOW.plusSeconds(45));
        assertThat(attempts.findById(view.sessionId()).orElseThrow().getGameCode()).isEqualTo("test-game");
        var result = api.finish(owner, view.sessionId(), null);
        assertThat(result.score()).isZero();
        assertThat(result.maxScore()).isEqualTo(1);
    }

    private void call(String operation, long userId, long sessionId) {
        switch (operation) {
            case "view" -> api.view(userId, sessionId);
            case "answer" -> api.answer(userId, sessionId, answer(1, "A"));
            case "finish" -> api.finish(userId, sessionId, List.of(answer(1, "A")));
            default -> throw new AssertionError(operation);
        }
    }

    private PracticeApi.SessionView start(PracticeSource source, int count) {
        return api.start(new StartPracticeCommand(owner, source, 1L, null,
                IntStream.rangeClosed(1, count).mapToObj(no -> source == PracticeSource.TOPIC_STUDY
                        ? card(no) : choice(no)).toList(), null, null, Map.of("mixedDistractors", false)));
    }

    private static ServedItem choice(int no) {
        return new ServedItem(no, ItemType.MEANING_CHOICE, PracticeApi.Skill.VOCAB, null,
                new PracticeApi.Prompt("我", null, null, null, null, null, null, null, null),
                List.of(new PracticeApi.Option("A", "tôi"), new PracticeApi.Option("B", "bạn")),
                "A", null, "server explanation", new PracticeApi.Rules(800, 0, false));
    }

    private static ServedItem card(int no) {
        return new ServedItem(no, ItemType.STUDY_CARD, PracticeApi.Skill.VOCAB, null,
                new PracticeApi.Prompt("我", "wǒ", "ngã", "tôi", null, null, null, null, "CHAR"),
                null, "A", null, null, new PracticeApi.Rules(1000, 0, false));
    }

    private static Answer answer(int no, String value) {
        return new Answer(no, value, 1500, 0, false, false, Map.of());
    }

    private int studyEventCount() {
        return jdbc.queryForObject("select count(*) from learning.study_events where user_id = ?", Integer.class, owner);
    }

    private long createUser() throws Exception {
        String password = "Aa1" + UUID.randomUUID();
        return mapper.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.test",
                                "password", password, "confirmPassword", password,
                                "displayName", "Practice API learner", "acceptTerms", true))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("userId").asLong();
    }

    private static void assertNoServerSecrets(JsonNode node) {
        if (node.isObject()) {
            for (String field : List.of("answer", "accept", "correctAnswer", "explanation")) {
                assertThat(node.has(field)).as(field).isFalse();
            }
        }
        node.forEach(PracticeApiIntegrationTest::assertNoServerSecrets);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EventConfiguration {
        @Bean
        EventProbe practiceApiEventProbe() {
            return new EventProbe();
        }
    }

    static class EventProbe {
        final List<ItemGradedEvent> graded = new CopyOnWriteArrayList<>();
        final List<SessionFinishedEvent> finished = new CopyOnWriteArrayList<>();
        boolean rejectFinish;
        boolean allTransactional;

        void clear() {
            graded.clear();
            finished.clear();
            rejectFinish = false;
            allTransactional = true;
        }

        @EventListener
        void graded(ItemGradedEvent event) {
            allTransactional &= TransactionSynchronizationManager.isActualTransactionActive();
            graded.add(event);
        }

        @EventListener
        void finished(SessionFinishedEvent event) {
            allTransactional &= TransactionSynchronizationManager.isActualTransactionActive();
            if (rejectFinish) {
                throw new IllegalStateException("Test listener rejected finish");
            }
            finished.add(event);
        }
    }
}
