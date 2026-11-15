package com.cnhsk.learning.api;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import com.cnhsk.learning.practice.PracticeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GameRoundApiTest {
    private static final Instant START = Instant.parse("2035-01-01T00:00:00Z");
    private final PracticeService practice = mock(PracticeService.class);
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final GameRoundApi api = new GameRoundApi(practice, jdbc, new ObjectMapper());

    @Test void AT06_onlyOwnInProgressSameGameRowsAreSelectedAndTheEnginePerformsAbandon() throws Exception {
        row("{\"meta\":{\"timeLimitSeconds\":30}}");
        api.abandonActive(7, GameCode.CHAR_RAIN, START.plusSeconds(32));
        verify(practice).abandon(7, 19);
        verify(jdbc).query(argThat(sql -> sql.contains("user_id = :user") && sql.contains("game_code = :code")
                        && sql.contains("kind = 'GAME'") && sql.contains("ref_type = 'GAME_ROUND'")
                        && sql.contains("status = 'IN_PROGRESS'") && !sql.contains("FOR UPDATE")),
                eq(java.util.Map.of("user", 7L, "code", "CHAR_RAIN")), any(RowMapper.class));
    }

    @Test void Q_T05_04_atDeadlineAndDuringGraceTheOldRoundIsNotAbandoned() throws Exception {
        row("{\"meta\":{\"timeLimitSeconds\":30}}");
        api.abandonActive(7, GameCode.CHAR_RAIN, START.plusSeconds(33));
        api.abandonActive(7, GameCode.CHAR_RAIN, START.plusSeconds(40));
        verifyNoInteractions(practice);
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"meta\":{}}", "{\"meta\":{\"timeLimitSeconds\":null}}",
            "{\"meta\":{\"timeLimitSeconds\":\"30\"}}", "{\"meta\":{\"timeLimitSeconds\":0}}",
            "{\"meta\":{\"timeLimitSeconds\":1.5}}", "{\"meta\":{\"timeLimitSeconds\":999999999999}}", "{"})
    void Q_T05_04_corruptSnapshotFailsWithoutGuessingOrAbandoning(String snapshot) throws Exception {
        row(snapshot);
        assertThatThrownBy(() -> api.abandonActive(7, GameCode.CHAR_RAIN, START))
                .isInstanceOf(IllegalStateException.class).hasMessageStartingWith("Invalid stored game");
        verifyNoInteractions(practice);
    }

    @Test void AT06_noActiveRoundDoesNothing() {
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of());
        api.abandonActive(7, GameCode.CHAR_RAIN, START);
        verifyNoInteractions(practice);
    }

    @Test void Q_T05_02_explicitAbandonDelegatesOwnershipAndStatusRulesToTheEngine() {
        api.abandon(7, 19); verify(practice).abandon(7, 19); verifyNoInteractions(jdbc);
    }

    @Test @SuppressWarnings({"unchecked", "rawtypes"})
    void serverSnapshotReadFiltersGameSourceAndReturnsKeysOnlyToTheJavaBoundary() throws Exception {
        var item = new PracticeApi.ServedItem(1, PracticeApi.ItemType.MEANING_CHOICE, PracticeApi.Skill.VOCAB, null,
                new PracticeApi.Prompt("Tự soạn", null, null, null, null, null, null, null, null),
                List.of(new PracticeApi.Option("A", "Một")), "A", null, "Giải thích", new PracticeApi.Rules(300, 0, false));
        var mapper = new ObjectMapper();
        var result = mock(ResultSet.class);
        when(result.getLong("id")).thenReturn(19L);
        when(result.getLong("user_id")).thenReturn(7L);
        when(result.getString("game_code")).thenReturn("CHAR_RAIN");
        when(result.getString("status")).thenReturn("IN_PROGRESS");
        when(result.getTimestamp("served_at")).thenReturn(Timestamp.from(START));
        when(result.getTimestamp("expires_at")).thenReturn(Timestamp.from(START.plusSeconds(48)));
        when(result.getString("served_items")).thenReturn(mapper.writeValueAsString(java.util.Map.of(
                "meta", java.util.Map.of("timeLimitSeconds", 30, "pointsPerCorrect", 10, "uiConfig", java.util.Map.of()), "items", List.of(item))));
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper reader = invocation.getArgument(2);
            return List.of(reader.mapRow(result, 0));
        });
        var round = api.find(19).orElseThrow();
        assertThat(round.gameCode()).isEqualTo(GameCode.CHAR_RAIN);
        assertThat(round.userId()).isEqualTo(7);
        assertThat(round.gradableCount()).isEqualTo(1);
        assertThat(round.items().get(0).answer()).isEqualTo("A");
        assertThat(round.deadline()).isEqualTo(START.plusSeconds(33));
        verify(jdbc).query(argThat(sql -> sql.contains("kind = 'GAME'") && sql.contains("ref_type = 'GAME_ROUND'")),
                eq(java.util.Map.of("id", 19L)), any(RowMapper.class));
    }

    @Test void previousBestExcludesThisRoundAndSuspiciousOrUnsubmittedRounds() {
        when(jdbc.queryForObject(anyString(), anyMap(), eq(Long.class))).thenReturn(8L);
        assertThat(api.bestCorrectBefore(7, GameCode.CHAR_RAIN, 19)).isEqualTo(8L);
        verify(jdbc).queryForObject(argThat(sql -> sql.contains("id <> :id") && sql.contains("suspicious = false")
                        && sql.contains("status = 'SUBMITTED'") && sql.contains("kind = 'GAME'")),
                eq(java.util.Map.of("user", 7L, "code", "CHAR_RAIN", "id", 19L)), eq(Long.class));
    }

    @Test void Q_T05_01_lockAndActiveSelectionRequireTheCallersTransaction() throws Exception {
        assertThat(GameRoundApi.class.getMethod("lockStart", long.class).getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.MANDATORY);
        assertThat(GameRoundApi.class.getMethod("abandonActive", long.class, GameCode.class, Instant.class)
                .getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.MANDATORY);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void row(String snapshot) throws Exception {
        var result = mock(ResultSet.class);
        when(result.getLong("id")).thenReturn(19L);
        when(result.getTimestamp("served_at")).thenReturn(Timestamp.from(START));
        when(result.getString("served_items")).thenReturn(snapshot);
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(2);
            return List.of(mapper.mapRow(result, 0));
        });
    }
}
