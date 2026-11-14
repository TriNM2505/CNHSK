package com.cnhsk.learning.api;

import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cnhsk.learning.practice.GameStatsQueryService;
import com.cnhsk.learning.practice.GameStatsQueryService.GameStatsRow;
import com.cnhsk.learning.practice.GameStatsQueryService.RecentGameRoundsRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameStatsApiTest {
    private static final Instant NOW = Instant.parse("2026-11-14T01:00:00Z");
    private final GameStatsQueryService queries = mock(GameStatsQueryService.class);
    private final GameStatsApi api = new GameStatsApi(queries);

    @ParameterizedTest
    @EnumSource(GameCode.class)
    void FR004_allSixSharedCodesMapWithoutChangingServerFacts(GameCode code) {
        when(queries.gameStats(71)).thenReturn(List.of(new GameStatsRow(code.name(), 8, 3, NOW)));
        assertThat(api.gameStats(71)).containsOnlyKeys(code)
                .containsEntry(code, new GameStatsApi.GameStats(8, 3, NOW));
        verify(queries).gameStats(71);
    }

    @Test
    void FR004_noSubmittedRoundsReturnAnEmptyMapAndTheMapCannotBeChanged() {
        when(queries.gameStats(71)).thenReturn(List.of());
        var result = api.gameStats(71);
        assertThat(result).isEmpty();
        assertThatThrownBy(() -> result.put(GameCode.CHAR_RAIN, new GameStatsApi.GameStats(1, 1, NOW)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void FR004_unknownStoredCodeIsSkippedWithOneFixedWarningAndKnownRowsStillWork() {
        String unknown = "UNKNOWN_GAME\nprivate-detail";
        when(queries.gameStats(71)).thenReturn(List.of(new GameStatsRow(unknown, 99, 1, NOW),
                new GameStatsRow("FLYING_DART", 7, 2, NOW)));
        Logger logger = (Logger) LoggerFactory.getLogger(GameStatsApi.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThat(api.gameStats(71)).containsOnlyKeys(GameCode.FLYING_DART)
                    .containsEntry(GameCode.FLYING_DART, new GameStatsApi.GameStats(7, 2, NOW));
            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).isEqualTo("Unknown stored game code in game statistics; row ignored.");
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getFormattedMessage()).doesNotContain(unknown, "private-detail");
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void FR007_recentRoundsPreserveTheCallerWindowAndBigintCount() {
        Instant since = NOW.minusSeconds(3600);
        when(queries.countRecentGameRounds(71, since)).thenReturn(new RecentGameRoundsRow(3_000_000_000L, NOW));
        assertThat(api.countRecentGameRounds(71, since)).isEqualTo(new GameStatsApi.RecentGameRounds(3_000_000_000L, NOW));
        verify(queries).countRecentGameRounds(71, since);
    }

    @Test
    void FR007_emptyWindowReturnsZeroAndNullOldestTime() {
        when(queries.countRecentGameRounds(71, NOW)).thenReturn(new RecentGameRoundsRow(0, null));
        assertThat(api.countRecentGameRounds(71, NOW)).isEqualTo(new GameStatsApi.RecentGameRounds(0, null));
    }

    @Test
    void AC03_boundaryOnlyExposesTheTwoReadMethodsAndApiDtos() {
        var methods = Arrays.stream(GameStatsApi.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers())).toList();
        assertThat(methods).extracting(java.lang.reflect.Method::getName)
                .containsExactlyInAnyOrder("gameStats", "countRecentGameRounds");
        for (var method : methods) {
            assertThat(method.getGenericReturnType().getTypeName()).doesNotContain("learning.practice", ".entity.");
            assertThat(method.getParameterTypes()[0]).isEqualTo(long.class);
        }
    }
}
