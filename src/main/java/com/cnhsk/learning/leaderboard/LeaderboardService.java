package com.cnhsk.learning.leaderboard;

import java.util.Optional;

public interface LeaderboardService {
    Optional<Rank> rankOf(long userId, long topicId);

    record Rank(long rank, long total) {}
}
