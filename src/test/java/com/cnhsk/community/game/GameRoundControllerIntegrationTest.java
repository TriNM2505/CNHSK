package com.cnhsk.community.game;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.cnhsk.learning.api.ContentLookup;
import com.cnhsk.learning.api.GameCode;
import com.cnhsk.learning.api.GameRoundApi;
import com.cnhsk.learning.api.GameStatsApi;
import com.cnhsk.learning.api.PracticeApi;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Import(GameRoundControllerIntegrationTest.Fixtures.class)
class GameRoundControllerIntegrationTest extends AbstractIntegrationTest {
    private static final Instant NOW = Instant.parse("2035-11-15T01:00:00Z");
    private static final String BASE = "/api/community/games/";
    private static final String HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @MockitoSpyBean private GameRoundApi rounds;
    @Autowired private MutableGameClock gameClock;
    @MockitoSpyBean private GameRoundHandler gameHandler;
    @MockitoSpyBean private ContentLookup content;
    @MockitoSpyBean private GameStatsApi stats;
    @MockitoSpyBean private PracticeApi practice;
    @MockitoSpyBean private GameRegistry registry;
    private final List<Long> users = new ArrayList<>();
    private long owner;
    private long other;
    private long lexeme;

    @BeforeEach void fixture() {
        gameClock.set(NOW);
        owner = user(); other = user();
        lexeme = jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,pinyin,pinyin_norm,meaning_vi,hsk_level) "
                + "VALUES ('WORD',?,'nǐ hǎo','ni3hao3','xin chào',1) RETURNING id", Long.class, "game-start-" + UUID.randomUUID());
        var card = new ContentLookup.LexemeCard(lexeme, "WORD", "你好", "nǐ hǎo", "ni3hao3", "xin chào",
                Set.of("xin chào"), null, null, null, 1);
        doReturn(List.of(card)).when(content).pool(any());
        // Preserve the small snapshots used by these engine tests without registering duplicate game codes.
        for (GameCode code : List.of(GameCode.CHAR_RAIN, GameCode.PINYIN_MATCH)) {
            doReturn(java.util.Optional.of(new GameDefinitionFixtures.Definition(code))).when(registry).find(code);
        }
        clearInvocations(content, stats, practice);
    }

    @AfterEach void cleanup() {
        for (long id : users) {
            jdbc.update("DELETE FROM learning.study_events WHERE user_id=?", id);
            jdbc.update("DELETE FROM learning.user_progress WHERE user_id=?", id);
            jdbc.update("DELETE FROM learning.attempt_items WHERE attempt_id IN (SELECT id FROM learning.attempts WHERE user_id=?)", id);
            jdbc.update("DELETE FROM learning.attempts WHERE user_id=?", id);
            jdbc.update("DELETE FROM auth.users WHERE id=?", id);
        }
        jdbc.update("DELETE FROM learning.lexemes WHERE id=?", lexeme);
        users.clear();
    }

    @Test void AT04_authenticationRolesAndCsrfAreRequired() throws Exception {
        mvc.perform(post(BASE + "CHAR_RAIN/rounds")).andExpect(status().isUnauthorized());
        for (String role : List.of("TEACHER", "ADMIN", "CONTENT_EDITOR", "MODERATOR", "ACCOUNTANT")) {
            mvc.perform(post(BASE + "CHAR_RAIN/rounds").header("Authorization", bearer(owner, role)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        }
        Cookie access = new Cookie("access_token", token(owner, "LEARNER"));
        mvc.perform(post(BASE + "CHAR_RAIN/rounds").cookie(access)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        mvc.perform(post(BASE + "CHAR_RAIN/rounds").cookie(access, new Cookie("XSRF-TOKEN", "game-start-csrf"))
                .header("X-XSRF-TOKEN", "wrong")).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "CHAR_RAIN/rounds").cookie(access, new Cookie("XSRF-TOKEN", "game-start-csrf"))
                .header("X-XSRF-TOKEN", "game-start-csrf").contentType("application/json").content("{\"hskLevel\":1}"))
                .andExpect(status().isCreated());
    }

    @Test void AT04_invalidCodeAndBodyAreRejectedBeforeReadingStatistics() throws Exception {
        mvc.perform(request(owner, "SNAKE")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_GAME_CODE"));
        for (String body : List.of("null", "[]", "{", "{\"hskLevel\":null}", "{\"hskLevel\":0}",
                "{\"hskLevel\":10}", "{\"hskLevel\":1.5}", "{\"hskLevel\":\"1\"}",
                "{\"score\":999}", "{\"points\":999}", "{\"hskLevel\":1,\"extra\":true}")) {
            mvc.perform(request(owner, "CHAR_RAIN").contentType("application/json").content(body))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        }
        verifyNoInteractions(stats, practice);
    }

    @Test void AT08_snapshotHasAnswerKeysButPublic201NeverDoesAndTimesComeFromServer() throws Exception {
        JsonNode result = created(owner, "CHAR_RAIN", "{\"hskLevel\":3}");
        long id = result.path("roundId").asLong();
        assertThat(result.path("gameCode").asText()).isEqualTo("CHAR_RAIN");
        assertThat(result.path("serverTime").asText()).isEqualTo(NOW.toString());
        assertThat(result.path("startedAt").asText()).isEqualTo(NOW.toString());
        assertThat(result.path("deadline").asText()).isEqualTo(NOW.plusSeconds(33).toString());
        assertThat(result.path("timeLimitSeconds").asInt()).isEqualTo(30);
        assertThat(result.path("pointsPerCorrect").asInt()).isEqualTo(10);
        assertThat(result.path("uiConfig").path("fixture").asBoolean()).isTrue();
        assertThat(result.path("items")).hasSize(1);
        assertPublic(result);
        var row = jdbc.queryForMap("SELECT user_id,kind,ref_type,game_code,expires_at,served_items FROM learning.attempts WHERE id=?", id);
        assertThat(((Number) row.get("user_id")).longValue()).isEqualTo(owner);
        assertThat(row).containsEntry("kind", "GAME").containsEntry("ref_type", "GAME_ROUND").containsEntry("game_code", "CHAR_RAIN");
        assertThat(((Timestamp) row.get("expires_at")).toInstant()).isEqualTo(NOW.plusSeconds(48));
        var snapshot = mapper.readTree(row.get("served_items").toString());
        assertThat(snapshot.path("items").get(0).path("answer").asText()).isEqualTo("A");
        assertThat(snapshot.path("items").get(0).path("explanation").asText()).isEqualTo("Giải thích tự soạn.");
        assertThat(snapshot.path("meta").path("timeLimitSeconds").asInt()).isEqualTo(30);
        verify(content, times(2)).pool(argThat(query -> query.preferLearnedBy().equals(owner) && query.hskLevels().equals(Set.of(3))));
        verify(stats, times(1)).countRecentGameRounds(owner, NOW.minusSeconds(3600));
    }

    @Test void AT06_onlyOwnSameGameActiveRoundIsAbandoned() throws Exception {
        long previous = created(owner, "CHAR_RAIN", null).path("roundId").asLong();
        long different = created(owner, "PINYIN_MATCH", "{\"hskLevel\":2}").path("roundId").asLong();
        long foreign = created(other, "CHAR_RAIN", null).path("roundId").asLong();
        long next = created(owner, "CHAR_RAIN", null).path("roundId").asLong();
        assertThat(state(previous)).isEqualTo("ABANDONED");
        for (long id : List.of(different, foreign, next)) assertThat(state(id)).isEqualTo("IN_PROGRESS");
    }

    @Test void Q_T05_04_storedDeadlineAtEqualityAndGraceDoNotUseCurrentDefinitionOrEngineExpiry() throws Exception {
        long equality = attempt(owner, "CHAR_RAIN", "IN_PROGRESS", NOW.minusSeconds(33), 30);
        long grace = attempt(owner, "CHAR_RAIN", "IN_PROGRESS", NOW.minusSeconds(40), 30);
        long longer = attempt(owner, "CHAR_RAIN", "IN_PROGRESS", NOW.minusSeconds(40), 60);
        created(owner, "CHAR_RAIN", null);
        assertThat(state(equality)).isEqualTo("IN_PROGRESS"); assertThat(state(grace)).isEqualTo("IN_PROGRESS");
        assertThat(state(longer)).isEqualTo("ABANDONED");
    }

    @Test void Q_T05_04_corruptStoredSnapshotReturns500WithoutCreatingOrAbandoning() throws Exception {
        long id = attempt(owner, "CHAR_RAIN", "IN_PROGRESS", NOW, 30);
        jdbc.update("UPDATE learning.attempts SET served_items='{}'::jsonb WHERE id=?", id);
        mvc.perform(request(owner, "CHAR_RAIN")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error_code").value("INTERNAL_ERROR"));
        assertThat(state(id)).isEqualTo("IN_PROGRESS"); assertThat(count(owner)).isEqualTo(1);
    }

    @Test void Q_T05_02_failedNewStartKeepsTheOldAbandonCommittedAsApproved() throws Exception {
        long previous = created(owner, "CHAR_RAIN", null).path("roundId").asLong();
        doThrow(new IllegalStateException("Simulated start failure.")).when(practice).start(any());
        mvc.perform(request(owner, "CHAR_RAIN")).andExpect(status().isInternalServerError());
        assertThat(state(previous)).isEqualTo("ABANDONED"); assertThat(count(owner)).isEqualTo(1);
    }

    @Test void AT05_everyStateAndSuspiciousRoundCountsAndStrictBoundaryDoesNot() throws Exception {
        for (int index = 0; index < 30; index++) {
            attempt(owner, "CHAR_RAIN", List.of("SUBMITTED", "EXPIRED", "ABANDONED").get(index % 3), NOW.minusSeconds(3599), 30);
        }
        jdbc.update("UPDATE learning.attempts SET suspicious=true WHERE user_id=?", owner);
        mvc.perform(request(owner, "CHAR_RAIN")).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error_code").value("ROUND_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(1));
        assertThat(count(owner)).isEqualTo(30);
        jdbc.update("UPDATE learning.attempts SET served_at=? WHERE user_id=?", Timestamp.from(NOW.minusSeconds(3600)), owner);
        created(owner, "CHAR_RAIN", null);
        assertThat(count(owner)).isEqualTo(31);
    }

    @Test void AT05_twoRealParallelDifferentGamesCompeteForOnlyTheThirtiethSlot() throws Exception {
        for (int index = 0; index < 29; index++) attempt(owner, "CHAR_RAIN", "SUBMITTED", NOW.minusSeconds(10), 30);
        var results = parallel("CHAR_RAIN", "PINYIN_MATCH");
        assertThat(results).extracting(result -> result.getResponse().getStatus()).containsExactlyInAnyOrder(201, 429);
        assertThat(count(owner)).isEqualTo(30);
    }

    @Test void AT06_twoRealParallelSameGameStartsLeaveOnlyOneActiveRound() throws Exception {
        var results = parallel("CHAR_RAIN", "CHAR_RAIN");
        assertThat(results).allSatisfy(result -> assertThat(result.getResponse().getStatus()).isEqualTo(201));
        assertThat(jdbc.queryForList("SELECT status FROM learning.attempts WHERE user_id=? ORDER BY id", String.class, owner))
                .containsExactly("ABANDONED", "IN_PROGRESS");
    }

    @Test void AT07_missingDefinitionAndUnavailableContentReturn422WithoutCreatingRounds() throws Exception {
        doReturn(java.util.Optional.empty()).when(registry).find(GameCode.CHAR_RAIN);
        mvc.perform(request(owner, "CHAR_RAIN")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("GAME_UNAVAILABLE"));
        doReturn(List.of()).when(content).pool(any());
        mvc.perform(request(owner, "PINYIN_MATCH")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("GAME_UNAVAILABLE"));
        assertThat(count(owner)).isZero(); verifyNoInteractions(practice);
    }

    @Test void Q_T05_01_lifecycleEntryPointsCannotBeUsedWithoutATransaction() {
        assertThatThrownBy(() -> rounds.lockStart(owner)).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(() -> rounds.abandonActive(owner, GameCode.CHAR_RAIN, NOW))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test void NFR_P02_measureOnlyP95AndStatisticsCallsRemainOnePerStart() throws Exception {
        long[] times = new long[20];
        for (int index = 0; index < times.length; index++) {
            long start = System.nanoTime(); created(owner, "CHAR_RAIN", null); times[index] = System.nanoTime() - start;
        }
        java.util.Arrays.sort(times);
        LoggerFactory.getLogger(getClass()).info("NFR-P02 game start: samples=20, p95={} ms, reference=300 ms (measurement only)", times[18] / 1_000_000);
        verify(stats, times(20)).countRecentGameRounds(owner, NOW.minusSeconds(3600));
        assertThat(count(owner)).isEqualTo(20);
    }

    @Test void AT09_allFourOperationsRequireLearnerAndCookieWritesRequireCsrf() throws Exception {
        long id = fullRound(2);
        var paths = List.of(BASE + "rounds/" + id, BASE + "rounds/" + id + "/answers",
                BASE + "rounds/" + id + "/finish", BASE + "rounds/" + id + "/abandon");
        for (int index = 0; index < paths.size(); index++) {
            mvc.perform(index == 0 ? get(paths.get(index)) : post(paths.get(index))).andExpect(status().isUnauthorized());
            mvc.perform((index == 0 ? get(paths.get(index)) : post(paths.get(index)))
                    .header("Authorization", bearer(owner, "TEACHER"))).andExpect(status().isForbidden());
        }
        Cookie access = new Cookie("access_token", token(owner, "LEARNER"));
        mvc.perform(post(BASE + "rounds/" + id + "/answers").cookie(access).contentType("application/json")
                .content(answerBody(1, "A", 1000))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        mvc.perform(post(BASE + "rounds/" + id + "/answers").cookie(access, new Cookie("XSRF-TOKEN", "game-answer-fixture"))
                .header("X-XSRF-TOKEN", "game-answer-fixture").contentType("application/json").content(answerBody(1, "A", 1000)))
                .andExpect(status().isOk());
    }

    @Test void AT09_nonGameMissingMalformedAndOtherOwnersHaveDistinctContractErrors() throws Exception {
        long id = fullRound(1);
        for (String suffix : List.of("", "/answers", "/finish", "/abandon")) {
            var foreign = suffix.isEmpty() ? get(BASE + "rounds/" + id) : post(BASE + "rounds/" + id + suffix);
            if (suffix.equals("/answers")) foreign.contentType("application/json").content(answerBody(1, "A", 1000));
            mvc.perform(foreign.header("Authorization", bearer(other, "LEARNER"))).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("ROUND_NOT_OWNED"));
        }
        for (String bad : List.of("abc", "0", "-1", "9223372036854775808", "9223372036854775807")) {
            mvc.perform(get(BASE + "rounds/" + bad).header("Authorization", bearer(owner, "LEARNER")))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code").value("ROUND_NOT_FOUND"));
        }
        jdbc.update("UPDATE learning.attempts SET served_items='{}'::jsonb WHERE id=?", id);
        mvc.perform(get(BASE + "rounds/" + id).header("Authorization", bearer(other, "LEARNER")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("ROUND_NOT_OWNED"));

        jdbc.update("UPDATE learning.attempts SET ref_type='TOPIC_TEST' WHERE id=?", id);
        mvc.perform(get(BASE + "rounds/" + id).header("Authorization", bearer(owner, "LEARNER")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code").value("ROUND_NOT_FOUND"));
        jdbc.update("UPDATE learning.attempts SET ref_type='GAME_ROUND',kind='PRACTICE',game_code=null WHERE id=?", id);
        mvc.perform(post(BASE + "rounds/" + id + "/finish").header("Authorization", bearer(owner, "LEARNER")))
                .andExpect(status().isNotFound());
    }

    @Test void AT10_deadlineBoundaryAndDirectEnginePathUseTheSameLockedServerRule() throws Exception {
        long id = fullRound(2);
        gameClock.set(NOW.plusSeconds(35));
        answer(id, 1, "A", 1000).andExpect(status().isOk());
        gameClock.set(NOW.plusSeconds(35).plusNanos(1));
        answer(id, 2, "A", 1000).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("ROUND_TIME_OVER"));
        mvc.perform(post("/api/practice/sessions/" + id + "/answers").header("Authorization", bearer(owner, "LEARNER"))
                .contentType("application/json").content(answerBody(2, "A", 1000)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("ROUND_TIME_OVER"));
        assertThat(answerCount(id)).isEqualTo(1);
    }

    @Test void AT11_unknownFieldsAreRejectedAtEveryMutationWithoutChangingTheRound() throws Exception {
        long id = fullRound(2);
        for (String field : List.of("score", "points", "correct", "replayCount", "clientMeta")) {
            String body = answerBody(1, "A", 1000).replace("}", ",\"" + field + "\":999}");
            mvc.perform(post(BASE + "rounds/" + id + "/answers").header("Authorization", bearer(owner, "LEARNER"))
                    .contentType("application/json").content(body)).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        }
        for (String suffix : List.of("finish", "abandon")) {
            mvc.perform(post(BASE + "rounds/" + id + "/" + suffix).header("Authorization", bearer(owner, "LEARNER"))
                    .contentType("application/json").content("{\"score\":999999}"))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        }
        assertThat(answerCount(id)).isZero(); assertThat(state(id)).isEqualTo("IN_PROGRESS");
    }

    @Test void AT12_threeCorrectAnswersAndRestoreUseServerTotalsWithoutExposingUnansweredKeys() throws Exception {
        long id = fullRound(4);
        var pristine = roundView(id);
        assertPublic(pristine.path("items")); assertThat(pristine.path("answered")).isEmpty();
        assertThat(pristine.path("totalPoints").asLong()).isZero();
        for (int no = 1; no <= 3; no++) answer(id, no, "A", 1000).andExpect(status().isOk())
                .andExpect(jsonPath("$.pointsAwarded").value(10)).andExpect(jsonPath("$.totalPoints").value(no * 10))
                .andExpect(jsonPath("$.answeredCount").value(no)).andExpect(jsonPath("$.correctAnswer").value("A"));
        var view = roundView(id);
        assertPublic(view.path("items")); assertThat(view.path("answered")).hasSize(3);
        assertThat(view.path("totalPoints").asLong()).isEqualTo(30);
        assertThat(view.path("answered").findValuesAsText("correctAnswer")).containsOnly("A");
        assertThat(view.path("answered").findValues("no")).allSatisfy(value -> assertThat(value.asInt()).isLessThan(4));
    }

    @Test void AT13_repeatFinishAndExpiryAreMappedWithoutChangingEngineTransitions() throws Exception {
        long id = fullRound(1);
        finishRound(id).andExpect(status().isOk()).andExpect(jsonPath("$.accuracy").isEmpty())
                .andExpect(jsonPath("$.missed").value(1));
        finishRound(id).andExpect(status().isConflict()).andExpect(jsonPath("$.error_code").value("ROUND_ALREADY_SUBMITTED"));
        abandonRound(id).andExpect(status().isConflict()).andExpect(jsonPath("$.error_code").value("ROUND_ALREADY_SUBMITTED"));
        long expired = fullRound(1);
        gameClock.set(NOW.plusSeconds(49));
        finishRound(expired).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("ROUND_EXPIRED"));
        assertThat(state(expired)).isEqualTo("EXPIRED");
        abandonRound(expired).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("ROUND_EXPIRED"));
        assertThat(roundView(expired).path("status").asText()).isEqualTo("EXPIRED");
    }

    @Test void AT14_twentyRealParallelAnswersHaveOneWinnerAndCannotExceedTheServedMaximum() throws Exception {
        long id = fullRound(20);
        var executor = Executors.newFixedThreadPool(20);
        var ready = new CountDownLatch(20); var go = new CountDownLatch(1);
        try {
            var futures = java.util.stream.IntStream.range(0, 20).mapToObj(index -> executor.submit(() -> {
                ready.countDown(); if (!go.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Answer gate timed out.");
                return answer(id, 1, "A", 1000).andReturn().getResponse().getStatus();
            })).toList();
            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue(); go.countDown();
            var statuses = new ArrayList<Integer>();
            for (var future : futures) statuses.add(future.get(45, TimeUnit.SECONDS));
            assertThat(statuses.stream().filter(value -> value == 200).count()).isEqualTo(1);
            assertThat(statuses.stream().filter(value -> value == 409).count()).isEqualTo(19);
        } finally { go.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue(); }
        answer(id, 999, "A", 1000).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_code").value("ITEM_NOT_IN_ROUND"));
        answer(id, 2, "Z", 1000).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_code").value("OPTION_NOT_IN_ITEM"));
        assertThat(answerCount(id)).isEqualTo(1);
        finishRound(id).andExpect(status().isOk()).andExpect(jsonPath("$.points").value(10)).andExpect(jsonPath("$.maxPoints").value(200));
        var score = jdbc.queryForMap("SELECT score,max_score FROM learning.attempts WHERE id=?", id);
        assertThat(((Number) score.get("score")).longValue()).isEqualTo(1).isLessThanOrEqualTo(((Number) score.get("max_score")).longValue());
    }

    @Test void AT14_realCapFailureRollsBackFinishStatusScoreAndStudyEvent() throws Exception {
        long id = fullRound(2);
        answer(id, 1, "A", 1000).andExpect(status().isOk());
        doAnswer(invocation -> {
            com.cnhsk.learning.api.GameRoundHooks.Round round = invocation.getArgument(0);
            com.cnhsk.learning.api.GameRoundHooks.Finish facts = invocation.getArgument(1);
            return new GameRoundHandler(new GameRegistry(GameDefinitionFixtures.all()))
                    .onFinish(round, new com.cnhsk.learning.api.GameRoundHooks.Finish(3L, 2L, false, facts.submittedAt(), 3, 0, 0));
        }).when(gameHandler).onFinish(any(), any());
        finishRound(id).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.error_code").value("INTERNAL_ERROR"));
        assertThat(state(id)).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("SELECT score FROM learning.attempts WHERE id=?", Long.class, id)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.study_events WHERE user_id=?", Long.class, owner)).isZero();
        assertThat(answerCount(id)).isEqualTo(1);
    }

    @Test void AT15_resultUsesSnapshotPointsAndPriorBestExcludingTheFinishingRound() throws Exception {
        long prior = attempt(owner, "CHAR_RAIN", "SUBMITTED", NOW.minusSeconds(60), 30);
        jdbc.update("UPDATE learning.attempts SET score=8,max_score=20 WHERE id=?", prior);
        long id = fullRound(20);
        for (int no = 1; no <= 17; no++) answer(id, no, no <= 12 ? "A" : "B", 1000).andExpect(status().isOk());
        gameClock.set(NOW.plusSeconds(10));
        finishRound(id).andExpect(status().isOk()).andExpect(jsonPath("$.points").value(120))
                .andExpect(jsonPath("$.maxPoints").value(200)).andExpect(jsonPath("$.correct").value(12))
                .andExpect(jsonPath("$.wrong").value(5)).andExpect(jsonPath("$.missed").value(3))
                .andExpect(jsonPath("$.accuracy").value(70)).andExpect(jsonPath("$.durationMs").value(10000))
                .andExpect(jsonPath("$.bestPoints").value(120)).andExpect(jsonPath("$.newBest").value(true));
        assertThat(stats.gameStats(owner).get(GameCode.CHAR_RAIN).bestCorrect()).isEqualTo(12);
    }

    @Test void AT15_suspicionStillStoresPointsButCannotCreateABestAndThresholdIsInclusive() throws Exception {
        long prior = attempt(owner, "CHAR_RAIN", "SUBMITTED", NOW.minusSeconds(60), 30);
        jdbc.update("UPDATE learning.attempts SET score=8,max_score=20 WHERE id=?", prior);
        long id = fullRound(10);
        for (int no = 1; no <= 10; no++) answer(id, no, "A", no <= 3 ? 1 : 1000).andExpect(status().isOk());
        finishRound(id).andExpect(status().isOk()).andExpect(jsonPath("$.suspicious").value(true))
                .andExpect(jsonPath("$.points").value(100)).andExpect(jsonPath("$.bestPoints").value(80))
                .andExpect(jsonPath("$.newBest").value(false));
        assertThat(jdbc.queryForObject("SELECT score FROM learning.attempts WHERE id=?", Long.class, id)).isEqualTo(10L);
        assertThat(stats.gameStats(owner).get(GameCode.CHAR_RAIN).bestCorrect()).isEqualTo(8);
        assertThat(stats.gameStats(owner).get(GameCode.CHAR_RAIN).roundsPlayed()).isEqualTo(2);
    }

    @Test void GM8_gradeNewGameItemsWithoutExposingTheSnapshotAnswer() throws Exception {
        var trace = PracticeApi.ItemType.TRACE_CHAR;
        var served = List.of(
                gameItem(1, trace),
                gameItem(2, trace),
                gameItem(3, PracticeApi.ItemType.PINYIN_PAIR),
                gameItem(4, PracticeApi.ItemType.RADICAL_CHOICE),
                gameItem(5, PracticeApi.ItemType.CATCH_CHOICE));
        long id = practice.start(new PracticeApi.StartPracticeCommand(owner, PracticeApi.PracticeSource.GAME_ROUND,
                null, GameCode.CHAR_RAIN.name(), served, List.of(), java.time.Duration.ofSeconds(48),
                java.util.Map.of("timeLimitSeconds", 30, "pointsPerCorrect", 10, "uiConfig", java.util.Map.of())))
                .sessionId();

        JsonNode publicItems = roundView(id).path("items");
        assertThat(publicItems).hasSize(5);
        assertPublic(publicItems);
        assertThat(publicItems.get(0).path("type").asText()).isEqualTo("TRACE_CHAR");
        assertThat(publicItems.get(0).path("options").isMissingNode() || publicItems.get(0).path("options").isNull()).isTrue();
        assertThat(publicItems.toString()).doesNotContain("PASS");

        answer(id, 1, "PASS", 100).andExpect(status().isOk()).andExpect(jsonPath("$.correct").value(true));
        answer(id, 2, "pass", 100).andExpect(status().isOk()).andExpect(jsonPath("$.correct").value(false));
        for (int no = 3; no <= 5; no++) {
            answer(id, no, "A", 1000).andExpect(status().isOk()).andExpect(jsonPath("$.correct").value(true));
        }
        finishRound(id).andExpect(status().isOk()).andExpect(jsonPath("$.points").value(40))
                .andExpect(jsonPath("$.maxPoints").value(50)).andExpect(jsonPath("$.suspicious").value(true));
    }

    private static PracticeApi.ServedItem gameItem(int no, PracticeApi.ItemType type) {
        boolean trace = type == PracticeApi.ItemType.TRACE_CHAR;
        return new PracticeApi.ServedItem(no, type, PracticeApi.Skill.CHARACTER, null,
                new PracticeApi.Prompt("字", null, null, null, null, null, null, null, null),
                trace ? null : List.of(new PracticeApi.Option("A", "Đáp án mẫu"), new PracticeApi.Option("B", "Mục khác")),
                trace ? "PASS" : "A", null, null, new PracticeApi.Rules(300, 0, false));
    }

    @Test void AT16_abandonUsesTheExistingEngineAndNeverAppearsInStatistics() throws Exception {
        long id = fullRound(2);
        answer(id, 1, "A", 1000).andExpect(status().isOk());
        abandonRound(id).andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(state(id)).isEqualTo("ABANDONED"); assertThat(stats.gameStats(owner)).isEmpty();
        abandonRound(id).andExpect(status().isConflict()).andExpect(jsonPath("$.error_code").value("ROUND_ALREADY_SUBMITTED"));
    }

    @Test void NFR_P01_measureOnlyAnswerP95AndSnapshotReadsDoNotGrowWithQuestionCount() throws Exception {
        long id = fullRound(20);
        long[] samples = new long[20];
        for (int no = 1; no <= 20; no++) {
            long begin = System.nanoTime(); answer(id, no, "A", 1000).andExpect(status().isOk()); samples[no - 1] = System.nanoTime() - begin;
        }
        java.util.Arrays.sort(samples);
        LoggerFactory.getLogger(getClass()).info("NFR-P01 game answer: samples=20, p95={} ms, reference=120 ms (measurement only)", samples[18] / 1_000_000);
        assertThat(roundView(id).path("totalPoints").asLong()).isEqualTo(200);
        clearInvocations(rounds);
        roundView(id);
        verify(rounds, times(1)).findOwned(owner, id); verify(rounds, times(1)).totals(id);
        long larger = fullRound(200);
        clearInvocations(rounds);
        assertThat(roundView(larger).path("items")).hasSize(200);
        verify(rounds, times(1)).findOwned(owner, larger); verify(rounds, times(1)).totals(larger);
    }

    private long fullRound(int count) {
        var served = java.util.stream.IntStream.rangeClosed(1, count).mapToObj(no -> new PracticeApi.ServedItem(no,
                PracticeApi.ItemType.MEANING_CHOICE, PracticeApi.Skill.VOCAB, null,
                new PracticeApi.Prompt("Mục tự soạn", null, null, null, null, null, null, null, null),
                List.of(new PracticeApi.Option("A", "Một"), new PracticeApi.Option("B", "Hai")), "A", null,
                "Giải thích tự soạn", new PracticeApi.Rules(300, 0, false))).toList();
        return practice.start(new PracticeApi.StartPracticeCommand(owner, PracticeApi.PracticeSource.GAME_ROUND,
                null, "CHAR_RAIN", served, List.of(), java.time.Duration.ofSeconds(48),
                java.util.Map.of("timeLimitSeconds", 30, "pointsPerCorrect", 10, "uiConfig", java.util.Map.of()))).sessionId();
    }
    private String answerBody(int no, String answer, int duration) throws Exception {
        return mapper.writeValueAsString(java.util.Map.of("no", no, "answer", answer, "durationMs", duration));
    }
    private org.springframework.test.web.servlet.ResultActions answer(long id, int no, String value, int duration) throws Exception {
        return mvc.perform(post(BASE + "rounds/" + id + "/answers").header("Authorization", bearer(owner, "LEARNER"))
                .contentType("application/json").content(answerBody(no, value, duration)));
    }
    private org.springframework.test.web.servlet.ResultActions finishRound(long id) throws Exception {
        return mvc.perform(post(BASE + "rounds/" + id + "/finish").header("Authorization", bearer(owner, "LEARNER")));
    }
    private org.springframework.test.web.servlet.ResultActions abandonRound(long id) throws Exception {
        return mvc.perform(post(BASE + "rounds/" + id + "/abandon").header("Authorization", bearer(owner, "LEARNER")));
    }
    private JsonNode roundView(long id) throws Exception {
        return mapper.readTree(mvc.perform(get(BASE + "rounds/" + id).header("Authorization", bearer(owner, "LEARNER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private long answerCount(long id) {
        return jdbc.queryForObject("SELECT count(*) FROM learning.attempt_items WHERE attempt_id=?", Long.class, id);
    }

    private List<MvcResult> parallel(String first, String second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2); var go = new CountDownLatch(1);
        try {
            var futures = List.of(first, second).stream().map(code -> executor.submit(() -> {
                ready.countDown(); if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start gate timed out.");
                return mvc.perform(request(owner, code)).andReturn();
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); go.countDown();
            return List.of(futures.get(0).get(30, TimeUnit.SECONDS), futures.get(1).get(30, TimeUnit.SECONDS));
        } finally { go.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    private JsonNode created(long user, String code, String body) throws Exception {
        var request = request(user, code); if (body != null) request.contentType("application/json").content(body);
        return mapper.readTree(mvc.perform(request).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    /** Every round is played at a level; tests that probe the body replace this default. */
    private MockHttpServletRequestBuilder request(long user, String code) { return post(BASE + code + "/rounds").header("Authorization", bearer(user, "LEARNER")).contentType("application/json").content("{\"hskLevel\":1}"); }
    private String token(long user, String role) { return jwt.issueAccessToken(user, List.of(role), false, UUID.randomUUID()); }
    private String bearer(long user, String role) { return "Bearer " + token(user, role); }
    private long user() {
        long id = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,'Game start fixture') RETURNING id",
                Long.class, UUID.randomUUID() + "@example.test", HASH); users.add(id); return id;
    }
    private String state(long id) { return jdbc.queryForObject("SELECT status FROM learning.attempts WHERE id=?", String.class, id); }
    private long count(long user) { return jdbc.queryForObject("SELECT count(*) FROM learning.attempts WHERE user_id=?", Long.class, user); }
    private long attempt(long user, String code, String state, Instant served, int duration) {
        return jdbc.queryForObject("INSERT INTO learning.attempts(user_id,kind,ref_type,game_code,status,served_at,expires_at,submitted_at,served_items) "
                        + "VALUES (?,'GAME','GAME_ROUND',?,?,?,?,?,?::jsonb) RETURNING id", Long.class, user, code, state,
                Timestamp.from(served), Timestamp.from(served.plusSeconds(3L + duration + 15)),
                state.equals("SUBMITTED") ? Timestamp.from(served.plusSeconds(1)) : null,
                "{\"meta\":{\"timeLimitSeconds\":" + duration + "},\"items\":[]}");
    }
    private void assertPublic(JsonNode node) {
        if (node.isObject()) node.fields().forEachRemaining(entry -> {
            assertThat(entry.getKey()).isNotIn("answer", "accept", "correctAnswer", "explanation", "score", "userId");
            assertPublic(entry.getValue());
        }); else if (node.isArray()) node.forEach(this::assertPublic);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Fixtures {
        @Bean @Primary MutableGameClock gameStartClock() { return new MutableGameClock(); }
    }

    static class MutableGameClock extends Clock {
        private final java.util.concurrent.atomic.AtomicReference<Instant> value = new java.util.concurrent.atomic.AtomicReference<>(NOW);
        void set(Instant instant) { value.set(instant); }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return Clock.fixed(value.get(), zone); }
        @Override public Instant instant() { return value.get(); }
    }
}
