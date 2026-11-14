package com.cnhsk.community.game;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.cnhsk.learning.api.ContentLookup;
import com.cnhsk.learning.api.GameCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Exercises the real cache, registry and definitions with controlled content and time, without Redis. */
class GameCatalogCacheIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-14T01:00:00Z");
    private static final ContentLookup.LexemeCard CARD = new ContentLookup.LexemeCard(1, "WORD", "你好", "nǐ hǎo",
            "ni3hao3", "xin chào", Set.of("xin chào"), null, null, null, 1);
    private final Clock clock = mock(Clock.class);
    private final ContentLookup content = mock(ContentLookup.class);
    private final AtomicReference<Instant> now = new AtomicReference<>(NOW);
    private List<GameDefinition> definitions;
    private GameCatalogCache cache;

    @BeforeEach void fixture() {
        when(clock.instant()).thenAnswer(call -> now.get());
        when(content.pool(any())).thenReturn(List.of(CARD));
        definitions = GameDefinitionFixtures.all().stream().map(definition -> spy(definition)).toList();
        cache = new GameCatalogCache(new GameRegistry(definitions), content, clock);
    }

    @Test void AT02_FR005_snapshotUsesEnumOrderGuestContextAndNoRoundOrUiData() {
        var result = cache.snapshot();
        assertThat(result).extracting(game -> game.code()).containsExactly(GameCode.values());
        assertThat(result).allSatisfy(game -> { assertThat(game.playable()).isTrue(); assertThat(game.unavailableReason()).isNull(); });
        for (var definition : definitions) {
            verify(definition).isAvailable(new GameContext(null, null, content));
            verify(definition, never()).buildRound(any()); verify(definition, never()).uiConfig();
        }
        verify(content, times(6)).pool(argThat(query -> query.preferLearnedBy() == null && query.hskLevels().isEmpty()));
        assertThatThrownBy(() -> result.clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void FR005_withinSixtySecondsNeitherMetadataNorAvailabilityIsRecomputed() {
        var initial = cache.snapshot();
        when(content.pool(any())).thenReturn(List.of());
        now.set(NOW.plusSeconds(59).plusNanos(999_999_999));
        assertThat(cache.snapshot()).isSameAs(initial);
        for (var definition : definitions) {
            verify(definition).isAvailable(any()); verify(definition).description(); verify(definition).timeLimitSeconds();
        }
        verify(content, times(6)).pool(any());
    }

    @Test void FR005_atExactlySixtySecondsTheWholeSnapshotIsReplacedAndOldResultStaysImmutable() {
        var initial = cache.snapshot();
        when(content.pool(any())).thenReturn(List.of());
        now.set(NOW.plusSeconds(60));
        var refreshed = cache.snapshot();
        assertThat(refreshed).isNotSameAs(initial).allSatisfy(game -> {
            assertThat(game.playable()).isFalse(); assertThat(game.unavailableReason()).isEqualTo("NOT_ENOUGH_CONTENT");
        });
        assertThat(initial).allSatisfy(game -> assertThat(game.playable()).isTrue());
        now.set(NOW.plusSeconds(119)); assertThat(cache.snapshot()).isSameAs(refreshed);
        when(content.pool(any())).thenReturn(List.of(CARD));
        now.set(NOW.plusSeconds(120));
        assertThat(cache.snapshot()).allSatisfy(game -> { assertThat(game.playable()).isTrue(); assertThat(game.unavailableReason()).isNull(); });
        for (var definition : definitions) verify(definition, times(3)).isAvailable(any());
    }

    @Test void FR005_parallelColdAndExpiredRequestsEachRebuildOnlyOnce() throws Exception {
        concurrentSnapshots();
        for (var definition : definitions) verify(definition).isAvailable(any());
        when(content.pool(any())).thenReturn(List.of()); now.set(NOW.plusSeconds(60));
        concurrentSnapshots();
        for (var definition : definitions) verify(definition, times(2)).isAvailable(any());
        verify(content, times(12)).pool(any());
    }

    @Test void FR005_failedRebuildDoesNotPublishAPartialSnapshotOrExtendTheExpiredOne() {
        var initial = cache.snapshot(); now.set(NOW.plusSeconds(60));
        when(content.pool(any())).thenThrow(new IllegalStateException("Fixture content unavailable"));
        assertThatThrownBy(cache::snapshot).isInstanceOf(IllegalStateException.class);
        doReturn(List.of()).when(content).pool(any());
        assertThat(cache.snapshot()).isNotSameAs(initial).allSatisfy(game -> assertThat(game.playable()).isFalse());
    }

    @Test void Q_T03_01_emptyRegistryCanBeCachedWithoutCallingContent() {
        var empty = new GameCatalogCache(new GameRegistry(List.of()), content, clock);
        assertThat(empty.snapshot()).isEmpty(); now.set(NOW.plusSeconds(60));
        assertThat(empty.snapshot()).isEmpty(); verifyNoInteractions(content);
    }

    private void concurrentSnapshots() throws Exception {
        int clients = 12;
        var executor = Executors.newFixedThreadPool(clients);
        var ready = new CountDownLatch(clients); var go = new CountDownLatch(1);
        try {
            var futures = java.util.stream.IntStream.range(0, clients).mapToObj(index -> executor.submit(() -> {
                ready.countDown(); if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Fixture start timed out");
                return cache.snapshot();
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); go.countDown();
            var first = futures.get(0).get(10, TimeUnit.SECONDS);
            for (var future : futures) assertThat(future.get(10, TimeUnit.SECONDS)).isSameAs(first);
        } finally { go.countDown(); executor.shutdownNow(); executor.awaitTermination(10, TimeUnit.SECONDS); }
    }
}
