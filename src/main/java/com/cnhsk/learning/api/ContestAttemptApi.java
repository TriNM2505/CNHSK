package com.cnhsk.learning.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Java boundary for contest attempts owned by learning; community supplies trusted caller identities. */
public interface ContestAttemptApi {

    ContestSession start(long userId, long contestId, long examId, Instant deadline);

    long countAttempts(long contestId);

    Instant submit(long userId, long contestId);

    Optional<MyContestAttempt> myAttempt(long userId, long contestId);

    RankingPage ranking(long contestId, int page, int size);

    Optional<Long> rankOf(long userId, long contestId);

    List<ContestParticipant> participants(long contestId);

    void setExcluded(long contestId, long userId, boolean excluded);

    Optional<ExamSummary> exam(long examId);

    record ContestSession(long sessionId, Instant deadline, boolean resumed,
            List<PracticeApi.ServedItemView> items) { }

    record MyContestAttempt(String status, Long score, Long maxScore, Integer durationMs,
            Instant submittedAt, boolean suspicious) { }

    record RankingRow(long rank, long userId, long score, long maxScore, Integer durationMs,
            Instant submittedAt) { }

    record RankingPage(List<RankingRow> items, int page, int size, long total) { }

    record ContestParticipant(long userId, String status, Long score, Long maxScore, Integer durationMs,
            Instant submittedAt, boolean suspicious) { }

    record ExamSummary(long id, String title, String status, int durationMinutes, int questionCount) { }
}
