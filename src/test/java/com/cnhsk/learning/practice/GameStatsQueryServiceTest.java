package com.cnhsk.learning.practice;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class GameStatsQueryServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-14T01:00:00Z");
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final GameStatsQueryService service = new GameStatsQueryService(jdbc);

    @Test
    void FR004_oneBoundQueryMapsBigintAggregatesAndLastSubmission() throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getString("game_code")).thenReturn("CHAR_RAIN");
        when(row.getLong("best_correct")).thenReturn(3_000_000_000L);
        when(row.getLong("rounds_played")).thenReturn(4_000_000_000L);
        when(row.getTimestamp("last_played_at")).thenReturn(Timestamp.from(NOW));
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenAnswer(call -> {
            assertThat((Map<String, Object>) call.getArgument(1)).containsOnlyKeys("userId").containsEntry("userId", 71L);
            RowMapper<?> mapper = call.getArgument(2);
            return List.of(mapper.mapRow(row, 0));
        });
        assertThat(service.gameStats(71)).containsExactly(
                new GameStatsQueryService.GameStatsRow("CHAR_RAIN", 3_000_000_000L, 4_000_000_000L, NOW));
        verify(jdbc, times(1)).query(anyString(), anyMap(), any(RowMapper.class));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void FR004_noRowsDoNotTriggerFallbackQueries() {
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of());
        assertThat(service.gameStats(71)).isEmpty();
        verify(jdbc, times(1)).query(anyString(), anyMap(), any(RowMapper.class));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void FR007_countAndOldestShareOneBoundQueryAndPreserveInstant() throws Exception {
        ResultSet row = mock(ResultSet.class);
        Instant since = NOW.minusSeconds(3600);
        when(row.getLong("rounds")).thenReturn(30L);
        when(row.getTimestamp("oldest_served_at")).thenReturn(Timestamp.from(NOW));
        when(jdbc.queryForObject(anyString(), anyMap(), any(RowMapper.class))).thenAnswer(call -> {
            assertThat((Map<String, Object>) call.getArgument(1)).containsOnlyKeys("userId", "since")
                    .containsEntry("userId", 71L).containsEntry("since", Timestamp.from(since));
            RowMapper<?> mapper = call.getArgument(2);
            return mapper.mapRow(row, 0);
        });
        assertThat(service.countRecentGameRounds(71, since))
                .isEqualTo(new GameStatsQueryService.RecentGameRoundsRow(30, NOW));
        verify(jdbc, times(1)).queryForObject(anyString(), anyMap(), any(RowMapper.class));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void FR007_nullMinimumTimestampIsPreservedForAnEmptyWindow() throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(jdbc.queryForObject(anyString(), anyMap(), any(RowMapper.class))).thenAnswer(call -> {
            RowMapper<?> mapper = call.getArgument(2);
            return mapper.mapRow(row, 0);
        });
        assertThat(service.countRecentGameRounds(71, NOW))
                .isEqualTo(new GameStatsQueryService.RecentGameRoundsRow(0, null));
    }
}
