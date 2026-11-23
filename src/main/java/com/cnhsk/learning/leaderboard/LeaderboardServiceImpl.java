package com.cnhsk.learning.leaderboard;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.PublicProfile;
import com.cnhsk.learning.api.GameCode;
import com.cnhsk.learning.leaderboard.LeaderboardDtos.GameOption;
import com.cnhsk.learning.leaderboard.LeaderboardDtos.Leaderboard;
import com.cnhsk.learning.leaderboard.LeaderboardDtos.LeaderboardEntry;
import com.cnhsk.learning.leaderboard.LeaderboardDtos.TopicOption;
import com.cnhsk.learning.leaderboard.LeaderboardPeriods.Interval;
import com.cnhsk.learning.leaderboard.LeaderboardRepository.Row;
import com.cnhsk.learning.leaderboard.LeaderboardRepository.Snapshot;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LeaderboardServiceImpl implements LeaderboardService {
    private static final int TOP_SIZE = 20;
    private final LeaderboardRepository repository;
    private final LeaderboardPeriods periods;
    private final LeaderboardCatalogRepository catalog;
    private final LeaderboardCache cache;
    private final AuthApi auth;

    public LeaderboardServiceImpl(LeaderboardRepository repository, LeaderboardPeriods periods,
            LeaderboardCatalogRepository catalog, LeaderboardCache cache, AuthApi auth) {
        this.repository = repository;
        this.periods = periods;
        this.catalog = catalog;
        this.cache = cache;
        this.auth = auth;
    }

    @Override
    public Optional<Rank> rankOf(long userId, long topicId) {
        if (userId <= 0 || topicId <= 0) throw new IllegalArgumentException("IDs must be positive.");
        Snapshot snapshot = repository.topicRanking(topicId, periods.resolve("all"), auth.bannedUserIds(), userId, 1);
        return snapshot.myEntry().map(row -> new Rank(row.rank(), snapshot.total()));
    }

    public List<TopicOption> listTopics(Integer hsk) {
        if (hsk != null && (hsk < 1 || hsk > 9)) throw invalid("hsk", "Cấp HSK phải từ 1 đến 9.");
        return catalog.topics(hsk);
    }

    public List<GameOption> listGames() {
        return Arrays.stream(GameCode.values()).map(game -> new GameOption(game.name(), game.displayName())).toList();
    }

    public Leaderboard topicLeaderboard(long topicId, String period, Long viewerId) {
        if (topicId <= 0) throw invalid("topicId", "Mã chủ đề phải là số nguyên dương.");
        Interval interval = interval(period);
        if (!catalog.exists(topicId)) throw new ApiException(ErrorCode.TOPIC_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Không tìm thấy chủ đề.", Map.of());
        return leaderboard("topic", Long.toString(topicId), interval, viewerId,
                (banned, viewer) -> repository.topicRanking(topicId, interval, banned, viewer, TOP_SIZE));
    }

    public Leaderboard gameLeaderboard(String gameCode, String period, String hskLevel, Long viewerId) {
        GameCode game;
        try { game = GameCode.valueOf(gameCode == null ? "" : gameCode); }
        catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.INVALID_GAME_CODE, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Mã trò chơi không hợp lệ.", Map.of());
        }
        int level = gameLevel(hskLevel);
        Interval interval = interval(period);
        return leaderboard("game", game.name(), game.name() + ":hsk" + level, interval, viewerId,
                (banned, viewer) -> repository.gameRanking(game, level, interval, banned, viewer, TOP_SIZE));
    }

    /** Total game points across every game and level, for the community page; periods week, month or year. */
    public Leaderboard communityLeaderboard(String period, Long viewerId) {
        String selected = period == null ? "week" : period;
        if (!List.of("week", "month", "year").contains(selected)) {
            throw invalid("period", "Kỳ phải là week, month hoặc year.");
        }
        Interval interval = periods.resolve(selected);
        return leaderboard("community", "games", interval, viewerId,
                (banned, viewer) -> repository.communityRanking(interval, banned, viewer, TOP_SIZE));
    }

    /** Games have content for HSK 1–3; a ranking without a level shows HSK 1. */
    private static int gameLevel(String value) {
        if (value == null || value.isEmpty()) return 1;
        if (!value.matches("[1-3]")) throw invalid("hskLevel", "Cấp HSK phải là 1, 2 hoặc 3.");
        return Integer.parseInt(value);
    }

    private Leaderboard leaderboard(String type, String ref, Interval interval, Long viewerId, Ranking query) {
        return leaderboard(type, ref, ref, interval, viewerId, query);
    }

    private Leaderboard leaderboard(String type, String ref, String cacheRef, Interval interval, Long viewerId,
            Ranking query) {
        String key = LeaderboardCache.key(type, cacheRef, interval);
        var lookup = cache.read(key);
        List<Row> top;
        Optional<Row> mine;
        if (lookup.payload().isPresent()) {
            var payload = lookup.payload().get();
            top = payload.top();
            mine = viewerId == null ? Optional.empty() : query.read(payload.banned(), viewerId).myEntry();
        } else {
            Set<Long> banned = auth.bannedUserIds();
            Snapshot snapshot = query.read(banned, viewerId);
            top = snapshot.entries();
            mine = snapshot.myEntry();
            if (lookup.writable()) cache.write(key, top, snapshot.total(), banned);
        }
        var userIds = new HashSet<Long>();
        top.forEach(row -> userIds.add(row.userId()));
        mine.ifPresent(row -> userIds.add(row.userId()));
        Map<Long, PublicProfile> profiles = userIds.isEmpty() ? Map.of() : auth.publicProfiles(userIds);
        return new Leaderboard(type.toUpperCase(java.util.Locale.ROOT), ref, interval.period(), interval.start(), interval.end(),
                top.stream().map(row -> entry(row, profiles)).toList(), mine.map(row -> entry(row, profiles)).orElse(null));
    }

    private Interval interval(String period) {
        try { return periods.resolve(period); }
        catch (IllegalArgumentException exception) { throw invalid("period", "Kỳ phải là week, month, year hoặc all."); }
    }

    private static LeaderboardEntry entry(Row row, Map<Long, PublicProfile> profiles) {
        PublicProfile profile = profiles.get(row.userId());
        boolean missing = profile == null || profile.displayName() == null || profile.displayName().isBlank();
        return new LeaderboardEntry(row.rank(), row.userId(), missing ? "Người học" : profile.displayName(),
                missing ? null : profile.avatarUrl(), row.score(), row.durationMs());
    }

    public static ApiException invalid(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Dữ liệu không hợp lệ.",
                Map.of("details", List.of(Map.of("field", field, "message", message))));
    }

    @FunctionalInterface
    private interface Ranking { Snapshot read(Set<Long> banned, Long viewer); }
}
