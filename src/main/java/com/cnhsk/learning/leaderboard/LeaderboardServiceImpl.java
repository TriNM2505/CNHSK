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

    public Leaderboard gameLeaderboard(String gameCode, String period, Long viewerId) {
        GameCode game;
        try { game = GameCode.valueOf(gameCode == null ? "" : gameCode); }
        catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.INVALID_GAME_CODE, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Mã trò chơi không hợp lệ.", Map.of());
        }
        Interval interval = interval(period);
        return leaderboard("game", game.name(), interval, viewerId,
                (banned, viewer) -> repository.gameRanking(game, interval, banned, viewer, TOP_SIZE));
    }

    private Leaderboard leaderboard(String type, String ref, Interval interval, Long viewerId, Ranking query) {
        String key = LeaderboardCache.key(type, ref, interval);
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
        return new Leaderboard(type.equals("topic") ? "TOPIC" : "GAME", ref, interval.period(), interval.start(), interval.end(),
                top.stream().map(row -> entry(row, profiles)).toList(), mine.map(row -> entry(row, profiles)).orElse(null));
    }

    private Interval interval(String period) {
        try { return periods.resolve(period); }
        catch (IllegalArgumentException exception) { throw invalid("period", "Kỳ phải là week, month hoặc all."); }
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
