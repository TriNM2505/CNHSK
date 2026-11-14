package com.cnhsk.learning.practice;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import com.cnhsk.learning.api.GameCode;
import com.cnhsk.learning.api.GameStatsApi;
import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Transactional
class GameStatsIntegrationTest extends AbstractIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-14T01:00:00Z");
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NamedParameterJdbcTemplate named;
    @Autowired private GameStatsApi api;
    private long owner;
    private long other;

    @BeforeEach
    void fixture() {
        owner = user();
        other = user();
    }

    @Test
    void FR004_FR018_AT03_suspiciousSubmittedRoundCountsButCannotSetTheRecord() {
        game(owner, "CHAR_RAIN", "SUBMITTED", 5, false, NOW.minusSeconds(30));
        game(owner, "CHAR_RAIN", "SUBMITTED", 8, false, NOW.minusSeconds(20));
        game(owner, "CHAR_RAIN", "SUBMITTED", 9, true, NOW.minusSeconds(10));
        for (String status : new String[]{"IN_PROGRESS", "ABANDONED", "EXPIRED"}) {
            game(owner, "CHAR_RAIN", status, 99, false, NOW);
        }
        game(other, "CHAR_RAIN", "SUBMITTED", 100, false, NOW);
        attempt(owner, "PRACTICE", "CHAR_RAIN", "SUBMITTED", 101, false, NOW);
        assertThat(api.gameStats(owner)).containsOnlyKeys(GameCode.CHAR_RAIN).containsEntry(GameCode.CHAR_RAIN,
                new GameStatsApi.GameStats(8, 3, NOW));
        assertThat(api.gameStats(other).get(GameCode.CHAR_RAIN).bestCorrect()).isEqualTo(100);
    }

    @Test
    void FR004_allSixGamesHaveIndependentAggregatesAndZeroRemainsARealScore() {
        int score = 0;
        for (GameCode code : GameCode.values()) {
            game(owner, code.name(), "SUBMITTED", score++, false, NOW);
        }
        var stats = api.gameStats(owner);
        assertThat(stats.keySet()).containsExactly(GameCode.values());
        score = 0;
        for (GameCode code : GameCode.values()) {
            assertThat(stats.get(code)).isEqualTo(new GameStatsApi.GameStats(score++, 1, NOW.plusSeconds(10)));
        }
        assertThat(api.gameStats(other)).isEmpty();
    }

    @Test
    void FR018_onlySuspiciousRoundsHaveZeroRecordButKeepCountAndLastPlayedAt() {
        game(owner, "BRUSH_STROKE", "SUBMITTED", 90, true, NOW);
        assertThat(api.gameStats(owner)).containsEntry(GameCode.BRUSH_STROKE,
                new GameStatsApi.GameStats(0, 1, NOW.plusSeconds(10)));
    }

    @Test
    void FR004_unknownDatabaseCodeDoesNotBreakStatsAndStillCountsTowardTheRateWindow() {
        game(owner, "FUTURE_GAME", "SUBMITTED", 99, false, NOW);
        assertThat(api.gameStats(owner)).isEmpty();
        game(owner, "PINYIN_MATCH", "SUBMITTED", 3, false, NOW);
        assertThat(api.gameStats(owner)).containsOnlyKeys(GameCode.PINYIN_MATCH);
        assertThat(api.countRecentGameRounds(owner, NOW.minusSeconds(3600)))
                .isEqualTo(new GameStatsApi.RecentGameRounds(2, NOW));
    }

    @Test
    void FR007_exclusiveWindowCountsAllGameStatesAndSuspiciousRoundsButNoOtherUserOrKind() {
        Instant since = NOW.minusSeconds(3600);
        game(owner, "CHAR_RAIN", "SUBMITTED", 9, false, since.minusMillis(1));
        game(owner, "CHAR_RAIN", "SUBMITTED", 9, false, since);
        Instant first = since.plusMillis(1);
        game(owner, "CHAR_RAIN", "IN_PROGRESS", 0, false, first);
        game(owner, "PINYIN_MATCH", "SUBMITTED", 2, true, NOW.minusSeconds(30));
        game(owner, "RADICAL_MATCH", "ABANDONED", 0, false, NOW.minusSeconds(20));
        game(owner, "CHAR_CATCH", "EXPIRED", 0, true, NOW.minusSeconds(10));
        game(other, "CHAR_RAIN", "IN_PROGRESS", 0, false, NOW);
        attempt(owner, "PRACTICE", null, "IN_PROGRESS", 0, false, NOW);
        assertThat(api.countRecentGameRounds(owner, since)).isEqualTo(new GameStatsApi.RecentGameRounds(4, first));
        assertThat(api.countRecentGameRounds(owner, NOW)).isEqualTo(new GameStatsApi.RecentGameRounds(0, null));
        assertThat(api.countRecentGameRounds(Long.MAX_VALUE, since)).isEqualTo(new GameStatsApi.RecentGameRounds(0, null));
    }

    @Test
    void FR007_AT05_thirtyRoundsReturnTheExactOldestTimeNeededForRetryAfter() {
        for (int index = 0; index < 30; index++) {
            game(owner, GameCode.values()[index % 6].name(), index % 2 == 0 ? "ABANDONED" : "SUBMITTED",
                    0, index % 3 == 0, NOW.minusSeconds(3500 - index));
        }
        assertThat(api.countRecentGameRounds(owner, NOW.minusSeconds(3600)))
                .isEqualTo(new GameStatsApi.RecentGameRounds(30, NOW.minusSeconds(3500)));
        game(owner, "FLYING_DART", "IN_PROGRESS", 0, false, NOW);
        assertThat(api.countRecentGameRounds(owner, NOW.minusSeconds(3600)).count()).isEqualTo(31);
    }

    @Test
    void FR004_FR007_queryCountsStayOnePerOperationWhenTheNumberOfGamesAndRoundsGrows() {
        var counted = spy(named);
        var service = new GameStatsQueryService(counted);
        game(owner, "CHAR_RAIN", "SUBMITTED", 1, false, NOW);
        assertThat(service.gameStats(owner)).hasSize(1);
        assertThat(service.countRecentGameRounds(owner, NOW.minusSeconds(3600)).count()).isEqualTo(1);
        verify(counted, times(1)).query(anyString(), anyMap(), any(RowMapper.class));
        verify(counted, times(1)).queryForObject(anyString(), anyMap(), any(RowMapper.class));
        clearInvocations(counted);
        for (int index = 0; index < 120; index++) {
            game(owner, GameCode.values()[index % 6].name(), "SUBMITTED", index, false, NOW);
        }
        assertThat(service.gameStats(owner)).hasSize(6);
        assertThat(service.countRecentGameRounds(owner, NOW.minusSeconds(3600)).count()).isEqualTo(121);
        verify(counted, times(1)).query(anyString(), anyMap(), any(RowMapper.class));
        verify(counted, times(1)).queryForObject(anyString(), anyMap(), any(RowMapper.class));
    }

    private long user() {
        return jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,?) RETURNING id",
                Long.class, UUID.randomUUID() + "@example.test", PASSWORD_HASH, "Game stats fixture");
    }

    private void game(long userId, String code, String status, long score, boolean suspicious, Instant served) {
        attempt(userId, "GAME", code, status, score, suspicious, served);
    }

    private void attempt(long userId, String kind, String code, String status, long score, boolean suspicious, Instant served) {
        jdbc.update("INSERT INTO learning.attempts(user_id,kind,ref_type,game_code,status,served_at,submitted_at,score,suspicious) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)", userId, kind, kind.equals("GAME") ? "GAME_ROUND" : "TOPIC_PRACTICE",
                code, status, Timestamp.from(served), status.equals("SUBMITTED") ? Timestamp.from(served.plusSeconds(10)) : null,
                score, suspicious);
    }
}
