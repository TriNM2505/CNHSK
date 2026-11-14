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
    @Autowired private GameRoundApi rounds;
    @MockitoSpyBean private ContentLookup content;
    @MockitoSpyBean private GameStatsApi stats;
    @MockitoSpyBean private PracticeApi practice;
    @MockitoSpyBean private GameRegistry registry;
    private final List<Long> users = new ArrayList<>();
    private long owner;
    private long other;
    private long lexeme;

    @BeforeEach void fixture() {
        owner = user(); other = user();
        lexeme = jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,pinyin,pinyin_norm,meaning_vi,hsk_level) "
                + "VALUES ('WORD',?,'nǐ hǎo','ni3hao3','xin chào',1) RETURNING id", Long.class, "game-start-" + UUID.randomUUID());
        var card = new ContentLookup.LexemeCard(lexeme, "WORD", "你好", "nǐ hǎo", "ni3hao3", "xin chào",
                Set.of("xin chào"), null, null, null, 1);
        doReturn(List.of(card)).when(content).pool(any());
        clearInvocations(content, stats, practice);
    }

    @AfterEach void cleanup() {
        for (long id : users) {
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
                .header("X-XSRF-TOKEN", "game-start-csrf")).andExpect(status().isCreated());
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
        JsonNode result = created(owner, "CHAR_RAIN", "{\"hskLevel\":9}");
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
        verify(content, times(2)).pool(argThat(query -> query.preferLearnedBy().equals(owner) && query.hskLevels().equals(Set.of(9))));
        verify(stats, times(1)).countRecentGameRounds(owner, NOW.minusSeconds(3600));
    }

    @Test void AT06_onlyOwnSameGameActiveRoundIsAbandoned() throws Exception {
        long previous = created(owner, "CHAR_RAIN", null).path("roundId").asLong();
        long different = created(owner, "PINYIN_MATCH", "{}").path("roundId").asLong();
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
    private MockHttpServletRequestBuilder request(long user, String code) { return post(BASE + code + "/rounds").header("Authorization", bearer(user, "LEARNER")); }
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
        @Bean @Primary Clock gameStartClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        @Bean GameDefinition startCharRain() { return new GameDefinitionFixtures.Definition(GameCode.CHAR_RAIN); }
        @Bean GameDefinition startPinyinMatch() { return new GameDefinitionFixtures.Definition(GameCode.PINYIN_MATCH); }
        @Bean GameDefinition startRadicalMatch() { return new GameDefinitionFixtures.Definition(GameCode.RADICAL_MATCH); }
        @Bean GameDefinition startCharCatch() { return new GameDefinitionFixtures.Definition(GameCode.CHAR_CATCH); }
        @Bean GameDefinition startBrushStroke() { return new GameDefinitionFixtures.Definition(GameCode.BRUSH_STROKE); }
        @Bean GameDefinition startFlyingDart() { return new GameDefinitionFixtures.Definition(GameCode.FLYING_DART); }
    }
}
